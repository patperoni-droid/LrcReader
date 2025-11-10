// app/src/main/java/com/patrick/lrcreader/ui/DjScreen.kt
package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DjScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onPlayTrack: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // zone header provisoire
        Text(
            text = "DJ (brouillon)",
            color = Color.White,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Timeline DJ à mettre ici",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "On mettra la liste de titres ici",
            color = Color.Gray,
            fontSize = 13.sp
        )
    }
}