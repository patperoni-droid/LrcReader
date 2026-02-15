package com.patrick.lrcreader.core.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["source"])
    ]
)
data class HistoryEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val source: String,
    val title: String,
    val artist: String?,
    val mediaUri: String
)
