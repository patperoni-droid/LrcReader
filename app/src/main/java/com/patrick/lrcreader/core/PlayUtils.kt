package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlin.math.max
import com.patrick.lrcreader.core.readUsltFromUri
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.FillerSoundManager

/* -------------------------------------------------------------------------- */
/*  LECTURE AVEC FONDU + CALLBACK FIN NATURELLE                               */
/* -------------------------------------------------------------------------- */

fun hasReadAccess(ctx: Context, uri: Uri): Boolean {
    return try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { }
        true
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        true
    }
}

fun crossfadePlay(
    context: Context,
    mediaPlayer: MediaPlayer,
    uriString: String,
    playlistName: String?,
    playToken: Long,
    getCurrentToken: () -> Long,
    onLyricsLoaded: (String?) -> Unit,
    onStart: () -> Unit,
    onError: () -> Unit,
    onNaturalEnd: () -> Unit,
    fadeDurationMs: Long = 500
) {
    CoroutineScope(Dispatchers.Main).launch {
        // On s'assure que le son de remplissage s'arrête proprement
        FillerSoundManager.fadeOutAndStop(400)

        if (mediaPlayer.isPlaying) {
            fadeVolume(mediaPlayer, 1f, 0f, fadeDurationMs)
        } else {
            mediaPlayer.setVolume(0f, 0f)
        }

        try {
            val uri = uriString.trim().toUri()

            if (!hasReadAccess(context, uri)) {
                Toast.makeText(
                    context,
                    "Accès refusé au fichier. Réautorise le dossier ou rechoisis le titre.",
                    Toast.LENGTH_LONG
                ).show()
                onError()
                return@launch
            }

            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepare()

            val lrcText = readUsltFromUri(context, uri)
            onLyricsLoaded(lrcText)

            mediaPlayer.start()
            onStart()

            fadeVolume(mediaPlayer, 0f, 1f, fadeDurationMs)

            if (playlistName != null) {
                val thisToken = playToken
                val thisSong = uriString
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10_000)
                    if (getCurrentToken() == thisToken && mediaPlayer.isPlaying) {
                        PlaylistRepository.markSongPlayed(playlistName, thisSong)
                    }
                }
            }

            mediaPlayer.setOnCompletionListener {
                onNaturalEnd()
                // Relance automatique du filler si configuré
                FillerSoundManager.startIfConfigured(context)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Lecture impossible : ${e.message ?: "erreur inconnue"}",
                Toast.LENGTH_LONG
            ).show()
            onError()
        }
    }
}

suspend fun fadeVolume(
    player: MediaPlayer,
    from: Float,
    to: Float,
    durationMs: Long
) {
    val steps = 20
    val stepTime = durationMs / steps
    val delta = (to - from) / steps
    for (i in 0..steps) {
        val v = (from + delta * i).coerceIn(0f, 1f)
        player.setVolume(v, v)
        delay(stepTime)
    }
    player.setVolume(to, to)
}