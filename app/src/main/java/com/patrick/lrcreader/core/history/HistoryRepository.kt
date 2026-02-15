package com.patrick.lrcreader.core.history

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class HistoryRepository private constructor(
    private val dao: HistoryDao
) {
    suspend fun logPlay(
        source: PlaySource,
        title: String,
        artist: String?,
        uri: String
    ) {
        dao.insert(
            HistoryEvent(
                timestamp = System.currentTimeMillis(),
                source = source.name,
                title = title.ifBlank { UNTITLED_FALLBACK },
                artist = artist?.takeIf { it.isNotBlank() },
                mediaUri = uri
            )
        )
    }

    fun observe(source: PlaySource?): Flow<List<HistoryEvent>> {
        return if (source == null) {
            dao.observeLatest()
        } else {
            dao.observeBySource(source.name)
        }
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    companion object {
        private const val DB_NAME = "history.db"
        const val UNTITLED_FALLBACK = "(Sans titre)"

        @Volatile
        private var INSTANCE: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = Room.databaseBuilder(
                        context.applicationContext,
                        HistoryDatabase::class.java,
                        DB_NAME
                    ).build()
                    HistoryRepository(db.historyDao()).also { INSTANCE = it }
                }
            }
        }
    }
}
