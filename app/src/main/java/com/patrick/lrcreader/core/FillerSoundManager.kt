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

/**
 * Gère le fond sonore
 * - si un dossier est enregistré → on lit tous les mp3/wav dedans, un par un, et on reboucle
 * - sinon on lit le fichier unique en boucle
 * - volume réglable
 * - toggle possible
 * - protégé si le fichier/dossier n’est plus accessible
 */
object FillerSoundManager {

    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var currentVolume: Float = DEFAULT_VOLUME

    // pour le mode dossier
    private var folderPlaylist: List<Uri> = emptyList()
    private var folderIndex: Int = 0

    /**
     * Démarre le fond sonore selon ce qu’il y a en prefs.
     */
    fun startIfConfigured(context: Context) {
        // on récupère volume en premier
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        // 1) d’abord on regarde si un dossier est enregistré
        val folderUri = FillerSoundPrefs.getFillerFolder(context)
        if (folderUri != null) {
            val list = buildPlaylistFromFolder(context, folderUri)
            if (list.isEmpty()) {
                // dossier vide → on efface le dossier
                FillerSoundPrefs.clear(context)
                Toast.makeText(context, "Dossier vide ou inaccessible", Toast.LENGTH_SHORT).show()
                return
            }
            folderPlaylist = list
            folderIndex = 0
            try {
                startFromFolderIndex(context, folderIndex)
            } catch (e: Exception) {
                e.printStackTrace()
                FillerSoundPrefs.clear(context)
                Toast.makeText(context, "Impossible de lire le dossier", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2) sinon on tombe sur le fichier unique
        val fileUri = FillerSoundPrefs.getFillerUri(context) ?: return
        try {
            startFromSingleFile(context, fileUri)
        } catch (e: Exception) {
            e.printStackTrace()
            FillerSoundPrefs.clear(context)
            Toast.makeText(context, "Impossible de lire le fond sonore. Rechoisis le fichier.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Pour l’UI : si ça joue → stop, sinon → démarre selon prefs.
     */
    fun toggle(context: Context) {
        if (isPlaying()) {
            fadeOutAndStop(200)
        } else {
            startIfConfigured(context)
        }
    }

    fun isPlaying(): Boolean = player != null

    // ---------- lecture fichier unique ----------
    private fun startFromSingleFile(context: Context, uri: Uri) {
        stopNow()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = true          // fichier seul → on boucle
        mp.prepare()
        mp.setVolume(currentVolume, currentVolume)
        mp.start()

        player = mp
        // on est dans le mode fichier, on vide la playlist
        folderPlaylist = emptyList()
    }

    // ---------- lecture dossier ----------
    private fun buildPlaylistFromFolder(context: Context, folderUri: Uri): List<Uri> {
        val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return doc.listFiles()
            .filter { it.isFile }
            .filter { f ->
                val name = f.name ?: return@filter false
                name.endsWith(".mp3", true) || name.endsWith(".wav", true)
            }
            .sortedBy { it.name ?: "" }
            .map { it.uri }
    }

    private fun startFromFolderIndex(context: Context, index: Int) {
        if (folderPlaylist.isEmpty()) return
        val uri = folderPlaylist[index]

        stopNow()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = false  // on veut la fin pour passer au suivant
        mp.setOnCompletionListener {
            // quand ce titre est fini → on lance le suivant
            playNextInFolder(context)
        }
        mp.prepare()
        mp.setVolume(currentVolume, currentVolume)
        mp.start()

        player = mp
    }

    private fun playNextInFolder(context: Context) {
        if (folderPlaylist.isEmpty()) {
            stopNow()
            return
        }
        folderIndex = (folderIndex + 1) % folderPlaylist.size   // 👉 reboucle
        try {
            startFromFolderIndex(context, folderIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            // si un fichier du dossier disparaît, on essaie le suivant
            folderPlaylist = folderPlaylist.filterIndexed { i, _ -> i != folderIndex }
            if (folderPlaylist.isNotEmpty()) {
                folderIndex %= folderPlaylist.size
                startFromFolderIndex(context, folderIndex)
            } else {
                stopNow()
            }
        }
    }

    // ---------- volume ----------
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        currentVolume = v
        player?.setVolume(v, v)
    }

    // ---------- stop ----------
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

    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        player = null
    }

    private const val DEFAULT_VOLUME = 0.25f
}