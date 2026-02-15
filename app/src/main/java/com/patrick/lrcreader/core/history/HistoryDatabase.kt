package com.patrick.lrcreader.core.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEvent::class],
    version = 1,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
