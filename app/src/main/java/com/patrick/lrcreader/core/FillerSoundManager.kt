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
 * - enchaîne avec crossfade
 */
object FillerSoundManager {

    private var player: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var currentVolume: Float = DEFAULT_VOLUME

    // playlist dossier
    private var folderPlaylist: List<Uri> = emptyList()
    private var folderIndex: Int = 0

    private const val DEFAULT_VOLUME = 0.25f
    private const val CROSSFADE_MS = 1500L

    fun startIfConfigured(context: Context) {
        // on récupère le volume choisi par l’utilisateur
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        // 1) dossier configuré ?
        val folderUri = FillerSoundPrefs.getFillerFolder(context)
        if (folderUri != null) {
            val list = buildPlaylistFromFolder(context, folderUri)
            if (list.isEmpty()) {
                FillerSoundPrefs.clear(context)
                Toast.makeText(context, "Dossier vide ou inaccessible", Toast.LENGTH_SHORT).show()
                return
            }

            folderPlaylist = list

            // 👇 départ aléatoire
            folderIndex = if (list.size == 1) 0 else Random.nextInt(list.size)

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
        }
    }

    /** bouton on/off */
    fun toggle(context: Context) {
        if (isPlaying()) {
            fadeOutAndStop(200)
        } else {
            startIfConfigured(context)
        }
    }

    /** bouton "suivant" dans l’UI */
    fun next(context: Context) {
        if (folderPlaylist.isNotEmpty()) {
            playNextInFolder(context)
        } else {
            // mode fichier → on relance
            startIfConfigured(context)
        }
    }

    fun previous(context: Context) {
        if (folderPlaylist.isEmpty()) {
            startIfConfigured(context)
            return
        }
        if (folderPlaylist.size == 1) {
            startIfConfigured(context)
            return
        }
        folderIndex = (folderIndex - 1 + folderPlaylist.size) % folderPlaylist.size
        try {
            startFromFolderIndex(context, folderIndex)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPlaying(): Boolean = player != null

    // ───────────────── fichier unique ─────────────────
    private fun startFromSingleFile(context: Context, uri: Uri) {
        stopNow()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = true
        mp.prepare()
        mp.setVolume(currentVolume, currentVolume)
        mp.start()

        player = mp
        folderPlaylist = emptyList()
    }

    // ───────────────── dossier ─────────────────
    private fun buildPlaylistFromFolder(context: Context, folderUri: Uri): List<Uri> {
        val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return doc.listFiles()
            .filter { it.isFile }
            .filter { f ->
                val name = f.name ?: return@filter false
                name.endsWith(".mp3", ignoreCase = true) ||
                        name.endsWith(".wav", ignoreCase = true)
            }
            .sortedBy { it.name ?: "" }
            .map { it.uri }
    }

    private fun startFromFolderIndex(context: Context, index: Int) {
        if (folderPlaylist.isEmpty()) return
        val uri = folderPlaylist[index]

        stopNext()  // au cas où

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = false
        mp.setOnCompletionListener {
            playNextInFolder(context)
        }
        mp.prepare()
        mp.setVolume(currentVolume, currentVolume)
        mp.start()

        // on remplace l’actuel
        stopCurrentOnly()
        player = mp
    }

    private fun playNextInFolder(context: Context) {
        if (folderPlaylist.isEmpty()) {
            stopNow()
            return
        }

        // on avance dans la liste (boucle)
        folderIndex = (folderIndex + 1) % folderPlaylist.size
        val nextUri = folderPlaylist[folderIndex]

        val oldPlayer = player ?: return

        stopNext()
        try {
            val newPlayer = MediaPlayer()
            newPlayer.setDataSource(context, nextUri)
            newPlayer.isLooping = false
            newPlayer.prepare()
            // on démarre à 0 de volume
            newPlayer.setVolume(0f, 0f)
            newPlayer.start()
            nextPlayer = newPlayer

            // crossfade
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
                // fin du fondu → on libère l’ancien
                try {
                    oldPlayer.stop()
                } catch (_: Exception) {}
                oldPlayer.release()

                // le newPlayer devient notre player
                player = newPlayer
                nextPlayer = null

                // quand celui-ci finit, on enchaîne de nouveau
                newPlayer.setOnCompletionListener {
                    playNextInFolder(context)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ───────────────── volume & stop ─────────────────
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        currentVolume = v
        player?.setVolume(v, v)
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
        try {
            p.stop()
        } catch (_: Exception) {}
        p.release()
        player = null
    }

    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null

        // stop player courant
        player?.let { mp ->
            try {
                mp.stop()
            } catch (_: Exception) {}
            mp.release()
        }
        player = null

        // stop éventuel nextPlayer
        stopNext()
    }

    private fun stopNext() {
        nextPlayer?.let { mp ->
            try {
                mp.stop()
            } catch (_: Exception) {}
            mp.release()
        }
        nextPlayer = null
    }
}