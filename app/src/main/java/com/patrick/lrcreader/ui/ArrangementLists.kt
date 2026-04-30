package com.patrick.lrcreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R

data class ArrangementListItem(
    val id: String,
    val title: String,
    val isActive: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArrangementListCard(
    modifier: Modifier,
    title: String,
    emptyLabel: String,
    items: List<ArrangementListItem>,
    onItemClick: (String) -> Unit,
    onItemAdd: ((String) -> Unit)?,
    onItemDelete: ((String) -> Unit)?,
    onItemLongClick: ((String) -> Unit)?
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyLabel,
                        color = Color(0xFF78909C),
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onItemClick(item.id) },
                                    onLongClick = if (onItemLongClick != null) {
                                        { onItemLongClick(item.id) }
                                    } else {
                                        null
                                    }
                                )
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                color = if (item.isActive) Color(0xFF66BB6A) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (onItemAdd != null) {
                                IconButton(
                                    onClick = { onItemAdd(item.id) },
                                    modifier = Modifier.width(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.timeline_palette_add_action),
                                        tint = Color(0xFFCFD8DC)
                                    )
                                }
                            }
                            if (onItemDelete != null) {
                                IconButton(
                                    onClick = { onItemDelete(item.id) },
                                    modifier = Modifier.width(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.library_delete_action),
                                        tint = Color(0xFFCFD8DC)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
