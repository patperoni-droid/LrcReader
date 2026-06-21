package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

@Composable
fun SmpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    placeholderColor: Color = Color(0x99FFFFFF),
    textColor: Color = Color.White,
    leadingIconTint: Color = Color(0xFF80CBC4),
    containerColor: Color = Color.White.copy(alpha = 0.07f),
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val safeTextStyle = textStyle.copy(
        color = textColor,
        lineHeight = if (textStyle.lineHeight.isUnspecified) 20.sp else textStyle.lineHeight
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(containerColor, shape),
        singleLine = true,
        textStyle = safeTextStyle,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(leadingIconTint),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = leadingIconTint,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = placeholderColor,
                            style = safeTextStyle.copy(color = placeholderColor)
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Box(
                        modifier = Modifier.width(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        trailingIcon()
                    }
                }
            }
        }
    )
}
