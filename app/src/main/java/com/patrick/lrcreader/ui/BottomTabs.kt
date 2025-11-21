package com.patrick.lrcreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------
// Onglets
// ---------------------------------------------------------------------
sealed class BottomTab(val id: String, val label: String) {
    @Composable
    abstract fun Icon()

    // Accueil
    object Home : BottomTab("home", "Accueil") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = null
            )
        }
    }

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

    // Onglet DJ : texte "DJ" bien visible
    object Dj : BottomTab("dj", "DJ") {
        @Composable
        override fun Icon() {
            Text(
                text = "DJ",
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }

    // Écran accordeur (pas forcément présent dans la barre du bas)
    object Tuner : BottomTab("tuner", "Accordeur") {
        @Composable
        override fun Icon() {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
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
    // On NE met PAS Tuner ici : il est accessible via l’accueil
    val tabs = listOf(
        BottomTab.Home,
        BottomTab.QuickPlaylists,
        BottomTab.Player,
        BottomTab.Library,
        BottomTab.AllPlaylists,
        BottomTab.More,
        BottomTab.Dj
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
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}