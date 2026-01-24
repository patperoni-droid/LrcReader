@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.patrick.lrcreader.core

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.EmbeddedLyricsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var lastEndListener: Player.Listener? = null

fun exoCrossfadePlay(
    context: Context,
    exoPlayer: ExoPlayer,
    embeddedLyricsListener: EmbeddedLyricsListener,
    uriString: String,
    playlistName: String?,
    playToken: Long,
    getCurrentToken: () -> Long,
    onLyricsLoaded: (String?) -> Unit,
    onStart: () -> Unit,
    onError: () -> Unit,
    onNaturalEnd: () -> Unit = {},
    fadeDurationMs: Long = 1000L
) {
    CoroutineScope(Dispatchers.Main).launch {

        if (getCurrentToken() != playToken) return@launch

        val wasPlaying = exoPlayer.isPlaying
        if (wasPlaying) {
            val steps = 24
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                val curved = 1f - (t * t)
                AudioEngine.setFadeMultiplier(curved)
                delay(stepDelay)
            }
            // ✅ IMPORTANT : on reste à 0 (silence) tant que l'ancien player n'est pas stoppé
            AudioEngine.setFadeMultiplier(0f)
        }

        if (getCurrentToken() != playToken) {
            // ✅ sécurité : éviter de laisser le bus muet si on a été interrompu
            AudioEngine.setFadeMultiplier(1f)
            return@launch
        }

        // ✅ on nettoie l'UI paroles dès qu'on bascule de morceau
        onLyricsLoaded(null)

        runCatching { embeddedLyricsListener.reset() }
        runCatching { exoPlayer.removeListener(embeddedLyricsListener) }
        exoPlayer.addListener(embeddedLyricsListener)

        lastEndListener?.let { old -> runCatching { exoPlayer.removeListener(old) } }

        val endListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (getCurrentToken() != playToken) return
                if (state == Player.STATE_ENDED) onNaturalEnd()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (getCurrentToken() != playToken) return
                onError()
            }
        }

        lastEndListener = endListener
        exoPlayer.addListener(endListener)

        // ✅ stop réel de l'ancien titre AVANT de remettre le volume "normal"
        runCatching { exoPlayer.stop() }
        runCatching { exoPlayer.clearMediaItems() }

        // ✅ maintenant seulement, on remet le bus à 1 pour le nouveau titre
        AudioEngine.setFadeMultiplier(1f)

        val playableUriString = withContext(Dispatchers.IO) {
            resolvePlayableUriString(context, uriString)
        }

        if (playableUriString == null) {
            Log.e("PlayUriCheck", "UNRESOLVABLE uri=$uriString")
            onError()
            return@launch
        }

        if (playableUriString != uriString) {
            Log.w("PlayUriCheck", "AUTO-MIGRATE old=$uriString -> new=$playableUriString")
            PlaylistRepository.replaceSongUriEverywhere(oldUri = uriString, newUri = playableUriString)
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(playableUriString))
        exoPlayer.prepare()

        AudioEngine.reapplyMixNow()
        AudioEngine.debugVolumeTag("after prepare")

        PlaybackCoordinator.requestStartPlayer()
        exoPlayer.play()
        onStart()

        val lyrics = embeddedLyricsListener.lyrics.filterNotNull().firstOrNull()
        if (getCurrentToken() != playToken) return@launch
        onLyricsLoaded(lyrics)
    }
}

private fun resolvePlayableUriString(context: Context, uriString: String): String? {

    fun canOpen(u: String): Boolean {
        return runCatching {
            val uri = Uri.parse(u)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: error("openFileDescriptor returned null")
            true
        }.getOrElse { e ->
            Log.e("PlayUriCheck", "CANNOT_READ uri=$u type=${e.javaClass.simpleName} msg=${e.message}")
            false
        }
    }

    // 1) direct
    if (canOpen(uriString)) return uriString

    // 2) nom cible
    val wantedFileName: String? = runCatching {
        val u = Uri.parse(uriString)
        val docId = DocumentsContract.getDocumentId(u) // primary:Music/AMERICANO.mp3
        docId.substringAfterLast('/')
    }.getOrNull()

    if (wantedFileName.isNullOrBlank()) {
        Log.e("PlayUriCheck", "fallback: cannot extract wantedFileName from uri=$uriString")
        return null
    }

    Log.w("PlayUriCheck", "fallback wantedFileName=$wantedFileName from uri=$uriString")

    // 3) SAF scan (cursor) + dump des enfants si ça ne matche pas
    val rootTreeUri = BackupFolderPrefs.get(context) ?: LibrarySnapshot.rootFolderUri
    if (rootTreeUri != null) {

        val hasPerm = context.contentResolver.persistedUriPermissions.any { p ->
            p.uri == rootTreeUri && p.isReadPermission
        }
        Log.w("PlayUriCheck", "fallback SAF: rootTreeUri=$rootTreeUri hasPerm=$hasPerm")

        // 🔥 dump rapide des 40 premiers noms du root (pour arrêter les suppositions)
        dumpRootChildrenNames(context, rootTreeUri, limit = 40)

        val found = findInTreeByDisplayName(context, rootTreeUri, wantedFileName)
        Log.w("PlayUriCheck", "fallback SAF scan foundUri=$found for name=$wantedFileName")
        if (!found.isNullOrBlank() && canOpen(found)) return found

    } else {
        Log.e("PlayUriCheck", "fallback SAF: no rootTreeUri (BackupFolderPrefs + LibrarySnapshot null)")
    }

    // 4) MediaStore (dernier recours)
    val mediaStoreUri = findInMediaStoreByDisplayName(context, wantedFileName)
    Log.w("PlayUriCheck", "fallback MediaStore foundUri=$mediaStoreUri for name=$wantedFileName")
    if (!mediaStoreUri.isNullOrBlank() && canOpen(mediaStoreUri)) return mediaStoreUri

    return null
}

private fun dumpRootChildrenNames(context: Context, treeUri: Uri, limit: Int) {
    val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )

    runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)
    }.onFailure { e ->
        Log.e("PlayUriCheck", "dumpRootChildrenNames query failed type=${e.javaClass.simpleName} msg=${e.message}")
    }.getOrNull()?.use { c ->
        val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

        var count = 0
        while (c.moveToNext() && count < limit) {
            val name = c.getString(nameCol) ?: ""
            val mime = c.getString(mimeCol) ?: ""
            val tag = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) "[DIR]" else "[FILE]"
            Log.w("PlayUriCheck", "SAF_ROOT_CHILD $tag $name")
            count++
        }
    }
}

private fun findInTreeByDisplayName(context: Context, treeUri: Uri, wantedName: String): String? {

    val wantedNorm = normName(wantedName)
    val wantedNoExt = normNoExt(wantedName)

    val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return null

    val stack = ArrayDeque<String>()
    stack.add(rootDocId)

    var candidatesLogged = 0

    while (stack.isNotEmpty()) {
        val parentDocId = stack.removeLast()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val cursor = runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        }.getOrNull() ?: continue

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (c.moveToNext()) {
                val docId = c.getString(idCol)
                val displayName = c.getString(nameCol) ?: ""
                val mime = c.getString(mimeCol) ?: ""

                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR

                val dnNorm = normName(displayName)
                val dnNoExt = normNoExt(displayName)

                // log “candidats” (contient une partie du nom recherché)
                if (!isDir && candidatesLogged < 20) {
                    val key = wantedNoExt.take(6)
                    if (dnNoExt.contains(key)) {
                        Log.w("PlayUriCheck", "SAF_CANDIDATE name=$displayName docId=$docId")
                        candidatesLogged++
                    }
                }

                val match =
                    (dnNorm == wantedNorm) ||
                            (dnNoExt == wantedNoExt) ||
                            (dnNorm.contains(wantedNoExt) || wantedNoExt.contains(dnNorm))

                if (match && !isDir) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    return docUri.toString()
                }

                if (isDir) stack.add(docId)
            }
        }
    }

    return null
}

private fun normName(s: String): String {
    return s.trim()
        .lowercase()
        .replace('\u00A0', ' ')          // NBSP -> espace normal
        .replace(Regex("\\s+"), " ")     // espaces multiples -> 1
}

private fun normNoExt(s: String): String {
    val base = s.substringBeforeLast('.', s)
    return normName(base)
}

private fun findInMediaStoreByDisplayName(context: Context, displayName: String): String? {
    return runCatching {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(displayName)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            if (c.moveToFirst()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                return@runCatching uri.toString()
            }
        }
        null
    }.getOrElse { e ->
        Log.e("PlayUriCheck", "MediaStore query failed type=${e.javaClass.simpleName} msg=${e.message}")
        null
    }
}