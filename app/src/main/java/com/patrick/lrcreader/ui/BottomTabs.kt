package com.patrick.lrcreader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------
// Onglets
// ---------------------------------------------------------------------
sealed class BottomTab(val id: String, val label: String) {
    @Composable abstract fun Icon()

    object Home : BottomTab("home", "Accueil") {
        @Composable override fun Icon() = Icon(Icons.Filled.Home, contentDescription = null)
    }

    object QuickPlaylists : BottomTab("quick", "Playlists") {
        @Composable override fun Icon() = Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
    }

    object Player : BottomTab("player", "Lecteur") {
        @Composable override fun Icon() = Icon(Icons.Filled.MusicNote, contentDescription = null)
    }

    object Filler : BottomTab("filler", "Fond") {
        @Composable override fun Icon() = Icon(Icons.Filled.Waves, contentDescription = null)
    }

    object Dj : BottomTab("dj", "DJ") {
        @Composable
        override fun Icon() {
            Icon(imageVector = Icons.Filled.Headset, contentDescription = null)
        }
    }

    // ✅ Loupe = action (overlay), pas un “écran onglet”
    object Search : BottomTab("search", "Recherche") {
        @Composable override fun Icon() = Icon(Icons.Filled.Search, contentDescription = null)
    }

    // ✅ ⋮ = action (menu), pas un “écran onglet”
    object More : BottomTab("more", "Plus") {
        @Composable override fun Icon() = Icon(Icons.Filled.MoreVert, contentDescription = null)
    }

    // Reste accessible via ⋮
    object Library : BottomTab("library", "Bibliothèque") {
        @Composable override fun Icon() = Icon(Icons.Filled.Menu, contentDescription = null)
    }

    object AllPlaylists : BottomTab("all", "Toutes") {
        @Composable override fun Icon() = Icon(Icons.Filled.Menu, contentDescription = null)
    }

    object Tuner : BottomTab("tuner", "Accordeur") {
        @Composable override fun Icon() = Icon(Icons.Filled.GraphicEq, contentDescription = null)
    }
}

// ---------------------------------------------------------------------
// Barre du bas
// ---------------------------------------------------------------------
@Composable
fun BottomTabsBar(
    selected: BottomTab,
    onSelected: (BottomTab) -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayerReselect: () -> Unit // ✅ NEW
) {
    val tabs = listOf(
        BottomTab.Home,
        BottomTab.QuickPlaylists,
        BottomTab.Player,
        BottomTab.Filler,
        BottomTab.Dj,
        BottomTab.Search,
        BottomTab.More
    )

    NavigationBar(containerColor = Color.Black, contentColor = Color.White) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selected.id

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    when (tab) {
                        is BottomTab.Search -> onSearchClick()
                        is BottomTab.More -> onMoreClick()

                        // ✅ Si on reclique sur Lecteur alors qu'il est déjà sélectionné,
                        // on déclenche un événement spécial (retour depuis Track Console).
                        is BottomTab.Player -> {
                            if (isSelected) onPlayerReselect() else onSelected(tab)
                        }

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
                    ) { tab.Icon() }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}