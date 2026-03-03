package com.patrick.lrcreader.ui

import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.core.search.SearchEngine
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    indexAll: List<LibraryIndexCache.CachedEntry>,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    // null => recherche globale (comme avant)
    // non null => recherche limitée (ex: playlist)
    restrictToUriStrings: Set<String>? = null,
    searchModeLabel: String = "GLOBAL",
    searchPlaylistName: String? = null
) {
    val context = LocalContext.current
    val aliasVersion = TitleAliasesStore.version.intValue

    // look "analogique"
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)

    val indexedItems = remember(indexAll, aliasVersion) {
        indexAll
            .asSequence()
            .filter { !it.isDirectory }
            .map {
                val alias = TitleAliasesStore.getTitleForTrack(context, it.uriString)
                    ?: PlaylistRepository.getAnyCustomTitleForUri(it.uriString)
                SearchEngine.index(
                    id = it.uriString,
                    displayTitle = alias ?: it.name,
                    fallbackName = it.name
                )
            }
            .toList()
    }
    val searchPool = remember(indexedItems, restrictToUriStrings) {
        SearchEngine.restrictToIds(indexedItems, restrictToUriStrings)
    }

    var q by remember { mutableStateOf("") }

    val results = remember(q, searchPool) {
        SearchEngine.filter(searchPool, q)
            .take(300)
    }
    val normalizedQuery = remember(q) { SearchEngine.normalize(q) }

    LaunchedEffect(normalizedQuery, searchPool.size, results.size, searchModeLabel, searchPlaylistName) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "SEARCH_PROOF",
                "mode=$searchModeLabel query='$normalizedQuery' playlist=${searchPlaylistName ?: "-"} itemsBefore=${searchPool.size} itemsAfter=${results.size}"
            )
        }
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
                Text(stringResource(R.string.search_title), color = titleColor, fontSize = 20.sp)
                Text(stringResource(R.string.search_hint), color = subtitleColor, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.common_search_placeholder)) },
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        if (q.isBlank()) {
            Text(stringResource(R.string.search_start_typing), color = subtitleColor)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 70.dp)
            ) {
                items(results, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(cardBg, RoundedCornerShape(10.dp))
                            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                            .clickable { onPlay(entry.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.displayTitle,
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
