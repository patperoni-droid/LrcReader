package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.MidiCueDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LyricsEditorScreen(
    modifier: Modifier = Modifier,
    initialText: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit
) {
    var textState by remember {
        mutableStateOf(TextFieldValue(initialText))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Fermer l’édition",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Éditer les paroles",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // ZONE TEXTE MULTILIGNE
        OutlinedTextField(
            value = textState,
            onValueChange = { newValue ->
                textState = newValue
                onTextChange(newValue.text)
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 16.sp
            ),
            placeholder = {
                Text(
                    "Tape ou colle ici les paroles…",
                    color = Color.Gray
                )
            },
            singleLine = false,
            maxLines = Int.MAX_VALUE
        )
    }
}