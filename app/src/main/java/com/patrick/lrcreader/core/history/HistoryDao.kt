package com.patrick.lrcreader.core.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: HistoryEvent)

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    fun observeLatest(): Flow<List<HistoryEvent>>

    @Query("SELECT * FROM history_events WHERE source = :source ORDER BY timestamp DESC")
    fun observeBySource(source: String): Flow<List<HistoryEvent>>

    @Query("DELETE FROM history_events")
    suspend fun clearAll()
}
