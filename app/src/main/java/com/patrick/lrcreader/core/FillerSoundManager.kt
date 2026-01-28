package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object FillerSoundManager {

    private var player: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var currentVolume: Float = DEFAULT_VOLUME

    private var folderPlaylist: List<Uri> = emptyList()
    private var folderIndex: Int = 0
    private var currentFolderUri: Uri? = null
    private var advanceOnNextStart: Boolean = false

    private const val DEFAULT_VOLUME = 0.25f
    private const val CROSSFADE_MS = 1500L

    fun startFromPlayerPause(context: Context) {
        advanceOnNextStart = true
        startIfConfigured(context)
    }

    fun startIfConfigured(context: Context) {
        if (!FillerSoundPrefs.isEnabled(context)) {
            fadeOutAndStop(0)
            return
        }
        if (PlaybackCoordinator.isMainPlaying) {
            fadeOutAndStop(0)
            return
        }
        internalStart(context)
    }

    fun startFromUi(context: Context) {
        if (!FillerSoundPrefs.isEnabled(context)) {
            FillerSoundPrefs.setEnabled(context, true)
        }
        internalStart(context)
    }

    private fun internalStart(context: Context) {
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        val folderUriRaw = FillerSoundPrefs.getFillerFolder(context)
        if (folderUriRaw != null) {

            // ✅ normalise en DOCUMENT Uri (cohérent avec le cache DJ)
            val folderUri = normalizeToDocumentUri(context, folderUriRaw)

            if (folderUri == null) {
                Toast.makeText(context, "Dossier fond sonore invalide (hors DJ ?)", Toast.LENGTH_SHORT).show()
                return
            }

            val built: List<Uri> = if (
                folderPlaylist.isNotEmpty() &&
                currentFolderUri != null &&
                currentFolderUri == folderUri
            ) {
                folderPlaylist
            } else {
                val fresh = buildPlaylistFromFolderOptimized(context, folderUri)
                if (fresh.isEmpty()) {
                    advanceOnNextStart = false
                    Toast.makeText(context, "Aucun MP3/WAV trouvé dans ce dossier", Toast.LENGTH_SHORT).show()
                    return
                }
                folderPlaylist = fresh
                currentFolderUri = folderUri
                folderIndex = if (fresh.size == 1) 0 else Random.nextInt(fresh.size)
                fresh
            }

            if (built.isNotEmpty() && advanceOnNextStart) {
                if (built.size > 1) folderIndex = (folderIndex + 1) % built.size
                advanceOnNextStart = false
            }

            try {
                startFromFolderIndex(context, folderIndex)
            } catch (e: Exception) {
                e.printStackTrace()
                FillerSoundPrefs.clear(context)
                Toast.makeText(context, "Impossible de lire le dossier", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val fileUri = FillerSoundPrefs.getFillerUri(context) ?: return
        try {
            startFromSingleFile(context, fileUri)
        } catch (e: Exception) {
            e.printStackTrace()
            FillerSoundPrefs.clear(context)
            Toast.makeText(context, "Impossible de lire le fond sonore.", Toast.LENGTH_SHORT).show()
        } finally {
            advanceOnNextStart = false
        }
    }

    fun toggle(context: Context) {
        if (isPlaying()) fadeOutAndStop(200)
        else startIfConfigured(context)
    }

    fun next(context: Context) {
        if (folderPlaylist.isEmpty()) {
            startIfConfigured(context)
            return
        }
        if (folderPlaylist.size == 1) {
            try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
            return
        }
        folderIndex = (folderIndex + 1) % folderPlaylist.size
        try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
    }

    fun previous(context: Context) {
        if (folderPlaylist.isEmpty()) {
            startIfConfigured(context)
            return
        }
        if (folderPlaylist.size == 1) {
            try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
            return
        }
        folderIndex = (folderIndex - 1 + folderPlaylist.size) % folderPlaylist.size
        try { startFromFolderIndex(context, folderIndex) } catch (_: Exception) {}
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    private fun startFromSingleFile(context: Context, uri: Uri) {
        stopNow()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = true

        mp.setOnPreparedListener { prepared ->
            prepared.setVolume(currentVolume, currentVolume)
            prepared.start()
        }
        mp.prepareAsync()

        player = mp
        folderPlaylist = emptyList()
        currentFolderUri = null
    }

    private fun isAudioName(name: String): Boolean {
        return name.endsWith(".mp3", true) || name.endsWith(".wav", true)
    }

    /**
     * ✅ Normalise un Uri en DOCUMENT Uri.
     * - si on reçoit un TREE Uri → converti en DOCUMENT Uri racine
     * - si c'est déjà un DOCUMENT Uri → retourne tel quel
     */
    private fun normalizeToDocumentUri(context: Context, anyUri: Uri): Uri? {
        return runCatching {
            val seg = anyUri.pathSegments ?: emptyList()

            // ✅ Compatible API 23 : un TREE Uri a typiquement "tree" comme 1er segment
            val isTreeLike = seg.isNotEmpty() && seg[0] == "tree"

            if (isTreeLike) {
                val treeId = DocumentsContract.getTreeDocumentId(anyUri)
                DocumentsContract.buildDocumentUriUsingTree(anyUri, treeId)
            } else {
                anyUri
            }
        }.getOrNull()
    }

    /**
     * ✅ Fallback SAF fiable :
     * on part du TREE DJ (permission), puis on descend jusqu'au dossier choisi (documentId),
     * puis on scanne.
     */
    private fun buildPlaylistFromFolderFallbackDjTree(context: Context, folderDocUri: Uri): List<Uri> {
        val djTree = DjFolderPrefs.get(context) ?: return emptyList()
        val djRootTreeDoc = DocumentFile.fromTreeUri(context, djTree) ?: return emptyList()

        val targetDocId = runCatching { DocumentsContract.getDocumentId(folderDocUri) }.getOrNull()
            ?: return emptyList()

        fun findByDocId(root: DocumentFile): DocumentFile? {
            val rootId = runCatching { DocumentsContract.getDocumentId(root.uri) }.getOrNull()
            if (rootId == targetDocId) return root

            val queue = ArrayDeque<DocumentFile>()
            queue.addLast(root)
            while (queue.isNotEmpty()) {
                val dir = queue.removeFirst()
                dir.listFiles().forEach { f ->
                    if (f.isDirectory) {
                        val id = runCatching { DocumentsContract.getDocumentId(f.uri) }.getOrNull()
                        if (id == targetDocId) return f
                        queue.addLast(f)
                    }
                }
            }
            return null
        }

        val startDir = findByDocId(djRootTreeDoc) ?: return emptyList()

        val out = ArrayList<Pair<String, Uri>>(256)
        fun walk(dir: DocumentFile) {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) walk(f)
                else {
                    val name = f.name ?: ""
                    if (name.isNotBlank() && isAudioName(name)) {
                        out.add(name to f.uri)
                    }
                }
            }
        }

        walk(startDir)
        return out.sortedBy { it.first.lowercase() }.map { it.second }
    }

    private fun buildPlaylistFromFolderOptimized(
        context: Context,
        folderDocUri: Uri
    ): List<Uri> {
        val djTree = DjFolderPrefs.get(context) ?: return emptyList()

        // ✅ règle : folder doit être dans DJ (comparaison docId)
        val isInsideDj = runCatching {
            val djTreeId = DocumentsContract.getTreeDocumentId(djTree)         // ex: primary:.../DJ
            val folderDocId = DocumentsContract.getDocumentId(folderDocUri)    // ex: primary:.../DJ/...
            folderDocId == djTreeId || folderDocId.startsWith("$djTreeId/")
        }.getOrDefault(false)

        if (!isInsideDj) return emptyList()

        val all = DjIndexCache.load(context).orEmpty()
        if (all.isEmpty()) {
            // ✅ fallback propre (via TREE DJ)
            return buildPlaylistFromFolderFallbackDjTree(context, folderDocUri)
        }

        val rootKey = folderDocUri.toString()
        val childrenByParent = all.groupBy { it.parentUriString }

        val queue = ArrayDeque<String>()
        queue.addLast(rootKey)

        val out = ArrayList<Pair<String, Uri>>(256)

        while (queue.isNotEmpty()) {
            val parentKey = queue.removeFirst()
            val kids = childrenByParent[parentKey].orEmpty()

            for (e in kids) {
                if (e.isDirectory) {
                    queue.addLast(e.uriString)
                } else {
                    val name = e.name
                    if (name.isNotBlank() && isAudioName(name)) {
                        out.add(name to Uri.parse(e.uriString))
                    }
                }
            }
        }

        if (out.isEmpty()) {
            // ✅ si le cache DJ ne contient pas ce sous-arbre -> fallback TREE DJ
            return buildPlaylistFromFolderFallbackDjTree(context, folderDocUri)
        }

        return out.sortedBy { it.first.lowercase() }.map { it.second }
    }

    private fun startFromFolderIndex(context: Context, index: Int) {
        if (folderPlaylist.isEmpty()) return
        val uri = folderPlaylist[index]

        stopNext()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = false
        mp.setOnCompletionListener { playNextInFolder(context) }

        mp.setOnPreparedListener { prepared ->
            prepared.setVolume(currentVolume, currentVolume)
            prepared.start()
        }
        mp.prepareAsync()

        stopCurrentOnly()
        player = mp
    }

    private fun playNextInFolder(context: Context) {
        if (folderPlaylist.isEmpty()) { stopNow(); return }

        folderIndex = (folderIndex + 1) % folderPlaylist.size
        val nextUri = folderPlaylist[folderIndex]
        val oldPlayer = player ?: return

        stopNext()
        try {
            val newPlayer = MediaPlayer()
            newPlayer.setDataSource(context, nextUri)
            newPlayer.isLooping = false
            newPlayer.prepare()
            newPlayer.setVolume(0f, 0f)
            newPlayer.start()
            nextPlayer = newPlayer

            fadeJob?.cancel()
            fadeJob = CoroutineScope(Dispatchers.Main).launch {
                val steps = 20
                val stepTime = CROSSFADE_MS / steps
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val volOut = currentVolume * (1f - t)
                    val volIn = currentVolume * t
                    oldPlayer.setVolume(volOut, volOut)
                    newPlayer.setVolume(volIn, volIn)
                    delay(stepTime)
                }
                try { oldPlayer.stop() } catch (_: Exception) {}
                oldPlayer.release()
                player = newPlayer
                nextPlayer = null
                newPlayer.setOnCompletionListener { playNextInFolder(context) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        currentVolume = v

        fadeJob?.cancel()
        fadeJob = null

        player?.setVolume(v, v)
        nextPlayer?.setVolume(v, v)
    }

    fun setVolumeWithSystem(context: Context, volume: Float, systemFloorPercent: Float = 0.6f) {
        val old = currentVolume
        setVolume(volume)
        if (volume > old) {
            SystemVolumeHelper.setMusicVolumeFloor(context, systemFloorPercent)
        }
    }

    fun fadeOutAndStop(durationMs: Long = 200) {
        val p = player ?: return
        fadeJob?.cancel()
        fadeJob = CoroutineScope(Dispatchers.Main).launch {
            val startVol = currentVolume
            val steps = 16
            val stepTime = durationMs / steps
            for (i in 0..steps) {
                val factor = 1f - i / steps.toFloat()
                val v = (startVol * factor).coerceIn(0f, 1f)
                p.setVolume(v, v)
                delay(stepTime)
            }
            stopNow()
        }
    }

    private fun stopCurrentOnly() {
        val p = player ?: return
        try { p.stop() } catch (_: Exception) {}
        p.release()
        player = null
    }

    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        player = null
        stopNext()
    }

    private fun stopNext() {
        nextPlayer?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        nextPlayer = null
    }
}

private object SystemVolumeHelper {
    fun setMusicVolumeFloor(context: Context, floorPercent: Float) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val floor = (max * floorPercent.coerceIn(0f, 1f)).toInt()
            if (cur < floor) {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, floor, 0)
            }
        } catch (_: Exception) {
        }
    }
}