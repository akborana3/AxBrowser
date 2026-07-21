package com.akay.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.akay.core.data.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY last_visited DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE last_visited BETWEEN :startDate AND :endDate ORDER BY last_visited DESC")
    fun getHistoryByDate(startDate: Long, endDate: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistoryItem(id: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryEntity)

    @Update
    suspend fun updateHistoryItem(item: HistoryEntity)

    @Delete
    suspend fun deleteHistoryItem(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryItemById(id: String)

    @Query("DELETE FROM history WHERE last_visited < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAllHistory()

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY last_visited DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>
}
