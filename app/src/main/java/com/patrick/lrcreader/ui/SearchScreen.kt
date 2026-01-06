package com.patrick.lrcreader.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LibraryIndexCache

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    indexAll: List<LibraryIndexCache.CachedEntry>,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    // null => recherche globale (comme avant)
    // non null => recherche limitée (ex: playlist)
    restrictToUriStrings: Set<String>? = null
) {
    // look "analogique"
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)

    // ✅ liste globale des fichiers (pas les dossiers) + restriction optionnelle
    val allAudio = remember(indexAll, restrictToUriStrings) {
        indexAll
            .asSequence()
            .filter { !it.isDirectory }
            .filter { ce -> restrictToUriStrings?.contains(ce.uriString) ?: true }
            .map {
                LibraryEntry(
                    uri = Uri.parse(it.uriString),
                    name = it.name,
                    isDirectory = false
                )
            }
            .toList()
    }

    var q by remember { mutableStateOf("") }

    val results = remember(q, allAudio) {
        if (q.isBlank()) emptyList()
        else allAudio.filter { it.name.contains(q, ignoreCase = true) }
            .take(300)
    }



    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Recherche", color = titleColor, fontSize = 20.sp)
                Text("Tape un nom de morceau", color = subtitleColor, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher…") },
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        if (q.isBlank()) {
            Text("Commence à taper…", color = subtitleColor)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 70.dp)
            ) {
                items(results, key = { it.uri.toString() }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(cardBg, RoundedCornerShape(10.dp))
                            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                            .clickable { onPlay(entry.uri.toString()) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("▶", color = accent, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}