package com.patrick.lrcreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------
// Onglets
// ---------------------------------------------------------------------
sealed class BottomTab(val id: String, val label: String) {
    @Composable
    abstract fun Icon()

    object QuickPlaylists : BottomTab("quick", "Playlists") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.PlaylistPlay,
                contentDescription = null
            )
        }
    }

    object Player : BottomTab("player", "Lecteur") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null
            )
        }
    }

    object Library : BottomTab("library", "Bibliothèque") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null
            )
        }
    }

    object AllPlaylists : BottomTab("all", "Toutes") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null
            )
        }
    }

    object More : BottomTab("more", "Plus") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null
            )
        }
    }
}

// ---------------------------------------------------------------------
// Barre du bas
// ---------------------------------------------------------------------
@Composable
fun BottomTabsBar(
    selected: BottomTab,
    onSelected: (BottomTab) -> Unit
) {
    val tabs = listOf(
        BottomTab.QuickPlaylists,
        BottomTab.Player,
        BottomTab.Library,
        BottomTab.AllPlaylists,
        BottomTab.More
    )

    NavigationBar(
        containerColor = Color.Black,
        contentColor = Color.White
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selected.id

            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelected(tab) },
                icon = { tab.Icon() },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White.copy(alpha = 1f),
                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                    indicatorColor = Color.Transparent,
                    selectedTextColor = Color.White,
                    unselectedTextColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}