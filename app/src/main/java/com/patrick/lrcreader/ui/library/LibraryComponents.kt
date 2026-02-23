package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.patrick.lrcreader.exo.R
import kotlin.math.roundToInt

@Composable
fun LibraryHeader(
    titleColor: Color,
    subtitleColor: Color,
    currentFolderName: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    actionsExpanded: Boolean,
    onActionsExpanded: (Boolean) -> Unit,
    onPickRoot: () -> Unit,
    onRescan: () -> Unit,
    onForgetRoot: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), color = titleColor, fontSize = 20.sp)
            Text(currentFolderName, color = subtitleColor, fontSize = 11.sp)
        }

        IconButton(onClick = { onActionsExpanded(true) }) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White)
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { onActionsExpanded(false) }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_choose_folder)) },
                onClick = { onActionsExpanded(false); onPickRoot() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_rescan)) },
                onClick = { onActionsExpanded(false); onRescan() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_forget_folder)) },
                onClick = { onActionsExpanded(false); onForgetRoot() }
            )
        }
    }
}

@Composable
fun LibraryBottomBar(
    bottomBarHeight: Dp,
    selectedCount: Int,
    onAssign: () -> Unit,
    onClear: () -> Unit
) {
    BottomAppBar(
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .height(bottomBarHeight)
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(28.dp)
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(selectedCount.toString(), color = Color.White, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onAssign) { Text(stringResource(R.string.library_bottom_assign), color = Color.White) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClear) { Text(stringResource(R.string.library_bottom_clear_selection), color = Color(0xFFB0B0B0)) }
    }
}

@Composable
fun LibraryLoadingOverlay(
    isLoading: Boolean,
    moveProgress: Float?,
    moveLabel: String?
) {
    if (!isLoading) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val p = moveProgress
            if (p == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.72f).height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    moveLabel ?: stringResource(R.string.library_moving),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            } else {
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.72f).height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                val pct = (p.coerceIn(0f, 1f) * 100f).roundToInt()
                Text(
                    (moveLabel ?: stringResource(R.string.library_copying)) + " $pct%",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }
    }
}
