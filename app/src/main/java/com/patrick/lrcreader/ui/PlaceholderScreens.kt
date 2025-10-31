package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier
) {
    SimplePlaceholderScreen(
        modifier = modifier,
        title = "Playlists rapides",
        message = "Ici tu mettras les playlists prêtes pour le live 🎤"
    )
}

// utilitaire privé, réutilisable dans ce fichier seulement
@Composable
private fun SimplePlaceholderScreen(
    modifier: Modifier = Modifier,
    title: String,
    message: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text(text = message, color = Color.Gray, textAlign = TextAlign.Center)
    }
}