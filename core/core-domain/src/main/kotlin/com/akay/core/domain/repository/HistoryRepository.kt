package com.akay.core.domain.repository

import com.akay.core.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryItem>>
    fun getHistoryByDate(startDate: Long, endDate: Long): Flow<List<HistoryItem>>
    suspend fun getHistoryItem(id: String): HistoryItem?
    suspend fun addHistoryItem(item: HistoryItem)
    suspend fun updateHistoryItem(item: HistoryItem)
    suspend fun deleteHistoryItem(id: String)
    suspend fun deleteHistoryBefore(timestamp: Long)
    suspend fun deleteAllHistory()
    suspend fun searchHistory(query: String): Flow<List<HistoryItem>>
}
