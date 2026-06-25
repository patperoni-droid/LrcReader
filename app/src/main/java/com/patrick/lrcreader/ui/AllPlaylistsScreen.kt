package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AllPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (String) -> Unit
) {
    // écoute les changements du repo
    val version by PlaylistRepository.version
    val playlists = remember(version) { PlaylistRepository.getPlaylists() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✅ cache durée par titre (uriString -> durée ms)
    // => évite de relire 200 fois les mêmes mp3 (super important)
    val durationCache = remember { mutableStateMapOf<String, Long>() }

    // dialog création
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // dialog renommage
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    // dialog suppression
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    // résultat import playlist
    var playlistImportResultMessage by remember { mutableStateOf<String?>(null) }
    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rawJson = context.contentResolver.openInputStream(pickedUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    importPlaylistFile(context.applicationContext, rawJson)
                }.getOrElse {
                    PlaylistFileImportResult(
                        importedPlaylistCount = 0,
                        foundCount = 0,
                        missingCount = 0,
                        failed = true
                    )
                }
            }
            playlistImportResultMessage = formatPlaylistImportResultMessage(context, result)
        }
    }

    // Palette console
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )
    val accent = Color(0xFFFFC107)
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ---------- HEADER ----------
            Text(
                text = stringResource(R.string.all_playlists_header_title),
                color = accent,
                fontSize = 18.sp,
                letterSpacing = 3.sp
            )
            Text(
                text = stringResource(R.string.all_playlists_header_subtitle),
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // ---- bouton "nouvelle liste" ----
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = onBg
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.9f),
                            accent.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.all_playlists_new_playlist_button),
                    color = onBg,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    importPlaylistLauncher.launch(
                        arrayOf(
                            "application/json",
                            "text/json",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = onBg
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.9f),
                            accent.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Text(
                    stringResource(R.string.more_item_import_playlist),
                    color = onBg,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.all_playlists_empty),
                    color = sub,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlists) { name ->
                        PlaylistRow(
                            name = name,
                            repoVersion = version,
                            context = context,
                            durationCache = durationCache,
                            onClick = { onPlaylistClick(name) },
                            onRename = {
                                renameTarget = name
                                renameText = name
                            },
                            onDelete = {
                                deleteTarget = name
                            }
                        )
                    }
                }
            }
        }

        // ---- dialog création ----
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text(stringResource(R.string.all_playlists_create_title), color = onBg) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.all_playlists_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clean = newName.trim()
                        if (clean.isNotEmpty()) {
                            PlaylistRepository.addPlaylist(clean)
                            newName = ""
                        }
                        showCreateDialog = false
                    }) {
                        Text(stringResource(R.string.common_ok), color = onBg)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(stringResource(R.string.common_cancel), color = sub)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }

        // ---- dialog renommage ----
        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.all_playlists_rename_title), color = onBg) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.library_new_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val oldName = renameTarget ?: return@TextButton
                        val ok = PlaylistRepository.renamePlaylist(oldName, renameText.trim())
                        if (ok) {
                            renameTarget = null
                        }
                        // si le nom existe déjà, on ne ferme pas → l’utilisateur corrige
                    }) {
                        Text(stringResource(R.string.common_ok), color = onBg)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(stringResource(R.string.common_cancel), color = sub)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }

        // ---- dialog suppression ----
        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.all_playlists_delete_title), color = onBg) },
                text = {
                    Text(
                        text = stringResource(R.string.all_playlists_delete_confirm, deleteTarget ?: ""),
                        color = onBg,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = deleteTarget ?: return@TextButton
                        PlaylistRepository.deletePlaylist(target)
                        deleteTarget = null
                    }) {
                        Text(stringResource(R.string.common_erase), color = Color(0xFFFF8A80))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.common_cancel), color = sub)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }

        playlistImportResultMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { playlistImportResultMessage = null },
                title = {
                    Text(
                        text = stringResource(R.string.more_item_import_playlist),
                        color = onBg
                    )
                },
                text = {
                    Text(
                        text = message,
                        color = onBg,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { playlistImportResultMessage = null }) {
                        Text(stringResource(R.string.common_close), color = onBg)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    repoVersion: Int,
    context: Context,
    durationCache: MutableMap<String, Long>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    // ✅ durée calculée (ms)
    var totalMs by remember { mutableLongStateOf(-1L) } // -1 = loading
    var titleCount by remember { mutableStateOf(0) }    // pour le mode pro

    LaunchedEffect(name, repoVersion) {
        totalMs = -1L

        val songs = PlaylistRepository.getSongsFor(name)
        titleCount = songs.size // on affiche "X titres" (prompteur inclus)

        val sum = withContext(Dispatchers.IO) {
            var acc = 0L
            for (uriString in songs) {
                // prompteur => durée 0
                if (uriString.startsWith("prompter://")) continue

                val cached = durationCache[uriString]
                val d = if (cached != null) {
                    cached
                } else {
                    val dur = getAudioDurationMs(context, uriString) ?: 0L
                    durationCache[uriString] = dur
                    dur
                }
                acc += d
            }
            acc
        }

        totalMs = sum
    }

    val cardColor = Color(0xFF1B1B1B)
    val borderColor = Color(0x33FFFFFF)

    // ✅ MODE PRO : "18 titres • 42:18"
    val proLine = when {
        totalMs < 0 -> stringResource(R.string.all_playlists_meta_loading, titleCount)
        else -> stringResource(R.string.all_playlists_meta_ready, titleCount, formatDuration(totalMs))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    color = Color(0xFFFFF8E1),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = proLine,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.common_cd_options),
                        tint = Color(0xFFFFF8E1)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename), color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_erase), color = Color(0xFFFF8A80)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Helpers durée audio (SAFE + IO)
// ─────────────────────────────────────────────

private fun getAudioDurationMs(context: Context, uriString: String): Long? {
    return runCatching {
        val uri = Uri.parse(uriString)
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        mmr.release()
        durStr?.toLongOrNull()
    }.getOrNull()
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}
