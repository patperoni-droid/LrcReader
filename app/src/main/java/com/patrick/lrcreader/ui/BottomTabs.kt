package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.exo.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource


// ---------------------------------------------------------------------
// Onglets (labels = stringResource)
// ---------------------------------------------------------------------
sealed class BottomTab(val id: String, val labelRes: Int) {
    @Composable abstract fun Icon()

    object Home : BottomTab("home", R.string.tab_home) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Home, contentDescription = null)
    }

    object QuickPlaylists : BottomTab("quick", R.string.tab_playlists) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
    }

    object Player : BottomTab("player", R.string.tab_player) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.MusicNote, contentDescription = null)
    }

    object Filler : BottomTab("filler", R.string.tab_filler) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Waves, contentDescription = null)
    }

    object Dj : BottomTab("dj", R.string.tab_dj) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Headset, contentDescription = null)
    }

    // 🔍 Action (overlay)
    object Search : BottomTab("search", R.string.tab_search) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Search, contentDescription = null)
    }

    // ⋮ Menu
    object More : BottomTab("more", R.string.tab_more) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.MoreVert, contentDescription = null)
    }

    // Accessibles via menu
    object Library : BottomTab("library", R.string.tab_library) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Folder, contentDescription = null)
    }

    object AllPlaylists : BottomTab("all", R.string.tab_all_playlists) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.Menu, contentDescription = null)
    }

    object Tuner : BottomTab("tuner", R.string.tab_tuner) {
        @Composable override fun Icon() =
            Icon(Icons.Filled.GraphicEq, contentDescription = null)
    }
}

// ---------------------------------------------------------------------
// Barre du bas
// ---------------------------------------------------------------------
@Composable
fun BottomTabsBar(
    selected: BottomTab,
    showMainBusTab: Boolean,
    showDjTab: Boolean,
    activeAudioSource: PlaybackCoordinator.Source,
    onSelected: (BottomTab) -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayerReselect: () -> Unit
) {
    val tabs = buildList {
        if (EditionConfig.isPro && showMainBusTab) {
            add(BottomTab.Home)
        }
        add(BottomTab.QuickPlaylists)
        add(BottomTab.Player)
        add(BottomTab.Filler)
        if (showDjTab) {
            add(BottomTab.Dj)
        }
        add(BottomTab.Library)
        add(BottomTab.Search)
        add(BottomTab.More)
    }

    NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selected.id
            val isActiveAudioSource = when (tab) {
                BottomTab.Player -> activeAudioSource == PlaybackCoordinator.Source.Player
                BottomTab.Filler -> activeAudioSource == PlaybackCoordinator.Source.Filler
                BottomTab.Dj -> activeAudioSource == PlaybackCoordinator.Source.Dj
                else -> false
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    when (tab) {
                        is BottomTab.Search -> onSearchClick()
                        is BottomTab.More -> onMoreClick()
                        is BottomTab.Player ->
                            if (isSelected) onPlayerReselect() else onSelected(tab)
                        else -> onSelected(tab)
                    }
                },
                alwaysShowLabel = false,
                icon = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        tab.Icon()
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        if (isActiveAudioSource) Color(0xFFFFC107) else Color.White,
                    unselectedIconColor =
                        if (isActiveAudioSource) Color(0xFFFFC107) else Color.White.copy(alpha = 0.4f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
