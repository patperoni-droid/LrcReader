package com.patrick.lrcreader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

sealed class BottomTab(val id: String, val label: String) {
    @Composable
    abstract fun Icon()

    object QuickPlaylists : BottomTab("quick", "Playlists") {
        @Composable
        override fun Icon() {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.PlaylistPlay,
                contentDescription = null
            )
        }
    }

    object Player : BottomTab("player", "Lecteur") {
        @Composable
        override fun Icon() {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null
            )
        }
    }

    object Library : BottomTab("library", "Bibliothèque") {
        @Composable
        override fun Icon() {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.LibraryMusic,
                contentDescription = null
            )
        }
    }

    object AllPlaylists : BottomTab("all", "Toutes") {
        @Composable
        override fun Icon() {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null
            )
        }
    }

    // 👇 NOUVEL ONGLET "Plus"
    object More : BottomTab("more", "Plus") {
        @Composable
        override fun Icon() {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null
            )
        }
    }
}

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
        BottomTab.More            // 👈 ajouté à la fin
    )

    NavigationBar(
        containerColor = Color.Black,
        contentColor = Color.White
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.id == selected.id,
                onClick = { onSelected(tab) },
                icon = { tab.Icon() },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(0xFF1DB954),
                    unselectedIconColor = Color(0xFFAAAAAA),
                    unselectedTextColor = Color(0xFF888888)
                )
            )
        }
    }
}