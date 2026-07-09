package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SequentialNavigation(
    modifier: Modifier = Modifier
) {
    val shellShape = RoundedCornerShape(16.dp)
    val controlShape = RoundedCornerShape(7.dp)
    val shellBorder = Color.White.copy(alpha = 0.22f)
    val controlBorder = Color.White.copy(alpha = 0.22f)
    val controlBackground = Color.White.copy(alpha = 0.10f)
    val disabledTint = Color.White.copy(alpha = 0.38f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color(0xFF0C0C0C), shellShape)
            .border(1.dp, shellBorder, shellShape)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SequentialNavigationButton(
            imageVector = Icons.Filled.KeyboardArrowDown,
            background = controlBackground,
            border = controlBorder,
            tint = disabledTint,
            shape = controlShape
        )

        SequentialNavigationButton(
            imageVector = Icons.Filled.KeyboardArrowUp,
            background = controlBackground,
            border = controlBorder,
            tint = disabledTint,
            shape = controlShape
        )
    }
}

@Composable
private fun SequentialNavigationButton(
    imageVector: ImageVector,
    background: Color,
    border: Color,
    tint: Color,
    shape: RoundedCornerShape
) {
    Box(
        modifier = Modifier
            .width(144.dp)
            .fillMaxHeight()
            .padding(vertical = 10.dp)
            .background(background, shape)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(34.dp)
        )
    }
}
