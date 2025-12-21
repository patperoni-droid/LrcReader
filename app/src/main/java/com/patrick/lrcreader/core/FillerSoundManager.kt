package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Fond sonore :
 * - dossier ou fichier unique
 * - si dossier → démarre sur une piste aléatoire
 * - enchaîne avec crossfade quand un titre se termine tout seul
 * - se désactive automatiquement si un titre principal joue
 *
 * ✅ Optimisation :
 * - Si un dossier est configuré, on essaye d’abord de construire la playlist
 *   depuis l’index JSON de la Bibliothèque (LibraryIndexCache) → instantané.
 * - Fallback sur DocumentFile si l’index n’existe pas / ne contient pas ce sous-arbre.
 *
 * ✅ IMPORTANT (pour éviter les demandes d'autorisations répétées) :
 * - On NE reconstruit PAS des Uri avec DocumentsContract.
 * - On réutilise les Uri EXACTES qui viennent de l’index Bibliothèque.
 */
object FillerSoundManager {

    private var player: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var currentVolume: Float = DEFAULT_VOLUME

    // playlist dossier
    private var folderPlaylist: List<Uri> = emptyList()
    private var folderIndex: Int = 0

    // on mémorise le dossier pour lequel la playlist a été construite
    private var currentFolderUri: Uri? = null

    // indique que le prochain démarrage auto doit avancer d'un morceau
    private var advanceOnNextStart: Boolean = false

    private const val DEFAULT_VOLUME = 0.25f
    private const val CROSSFADE_MS = 1500L

    /**
     * Appelé depuis le PlayerScreen quand on appuie sur PAUSE
     * sur le titre principal : on veut que le fond sonore démarre
     * sur le *morceau suivant* (pas toujours le même).
     */
    fun startFromPlayerPause(context: Context) {
        advanceOnNextStart = true
        startIfConfigured(context)
    }

    /** Démarre le fond sonore automatiquement (fin de morceau, etc.) */
    fun startIfConfigured(context: Context) {
        // ne rien faire si le mode filler est désactivé
        if (!FillerSoundPrefs.isEnabled(context)) {
            fadeOutAndStop(0)
            return
        }

        // sécurité anti-conflit : ne pas lancer automatiquement si un titre principal joue
        if (PlaybackCoordinator.isMainPlaying) {
            fadeOutAndStop(0)
            return
        }

        internalStart(context)
    }

    /**
     * Démarrage demandé explicitement depuis l’écran “Fond sonore”.
     * Ici on NE bloque PAS sur isMainPlaying (l’utilisateur sait ce qu’il fait).
     */
    fun startFromUi(context: Context) {
        if (!FillerSoundPrefs.isEnabled(context)) {
            FillerSoundPrefs.setEnabled(context, true)
        }
        internalStart(context)
    }

    /** Implémentation commune du démarrage (dossier ou fichier) */
    private fun internalStart(context: Context) {
        android.util.Log.e("FILLER_DBG", "internalStart() CALLED")
        // volume utilisateur (0..1)
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        // 1) dossier configuré ?
        val folderUri = FillerSoundPrefs.getFillerFolder(context)
        if (folderUri != null) {

            // si on joue déjà ce dossier → réutiliser la playlist existante
            val built: List<Uri> = if (
                folderPlaylist.isNotEmpty() &&
                currentFolderUri != null &&
                currentFolderUri == folderUri
            ) {
                folderPlaylist
            } else {
                // sinon on rebâtit la playlist une seule fois
                val fresh = buildPlaylistFromFolderOptimized(context, folderUri)
                if (fresh.isEmpty()) {
                    FillerSoundPrefs.clear(context)
                    advanceOnNextStart = false
                    Toast.makeText(context, "Dossier vide ou inaccessible", Toast.LENGTH_SHORT).show()
                    return
                }
                folderPlaylist = fresh
                currentFolderUri = folderUri
                folderIndex = if (fresh.size == 1) 0 else Random.nextInt(fresh.size)
                fresh
            }

            // si le démarrage vient du Player (pause), on avance d’un morceau
            if (built.isNotEmpty() && advanceOnNextStart) {
                if (built.size > 1) {
                    folderIndex = (folderIndex + 1) % built.size
                }
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

        // 2) sinon fichier unique
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

    /** bouton on/off */
    fun toggle(context: Context) {
        if (isPlaying()) fadeOutAndStop(200)
        else startIfConfigured(context)
    }

    /** bouton suivant (manuel, sans crossfade) */
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

    /** bouton précédent (manuel, sans crossfade) */
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

    // ───────────── fichier unique ─────────────
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

    // ───────────── dossier (index bibliothèque + fallback) ─────────────

    private fun isAudioName(name: String): Boolean {
        return name.endsWith(".mp3", true) || name.endsWith(".wav", true)
    }

    /**
     * ✅ 1) Essaye l’index Bibliothèque (instantané) en réutilisant les Uri EXACTES du cache
     * ✅ 2) Fallback DocumentFile si nécessaire
     */
    private fun buildPlaylistFromFolderOptimized(
        context: Context,
        folderUri: Uri
    ): List<Uri> {
        val all = LibraryIndexCache.load(context) ?: return emptyList()

        val rootKey = folderUri.toString()

        // parent -> enfants
        val childrenByParent: Map<String?, List<LibraryIndexCache.CachedEntry>> =
            all.groupBy { it.parentUriString }

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

        // tri stable
        return out.sortedBy { it.first.lowercase() }.map { it.second }
    }

    /**
     * Lance directement l’index demandé.
     * Coupe l’ancien player. Crossfade réservé à l’auto-suivant.
     */
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

    /**
     * Auto-suivant : CROSSFADE entre l’ancien et le nouveau.
     */
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

    // ───────────── volume & stop ─────────────

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

/* -----------------------------------------------------------
   Utilitaire volume système — aucune coloration du signal
   ----------------------------------------------------------- */
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
            // silencieux
        }
    }
}