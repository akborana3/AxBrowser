package com.akay.core.data.repository

import com.akay.core.data.db.dao.HistoryDao
import com.akay.core.data.mapper.toDomain
import com.akay.core.data.mapper.toEntity
import com.akay.core.domain.model.HistoryItem
import com.akay.core.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<HistoryItem>> {
        return historyDao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHistoryByDate(startDate: Long, endDate: Long): Flow<List<HistoryItem>> {
        return historyDao.getHistoryByDate(startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getHistoryItem(id: String): HistoryItem? {
        return historyDao.getHistoryItem(id)?.toDomain()
    }

    override suspend fun addHistoryItem(item: HistoryItem) {
        historyDao.insertHistoryItem(item.toEntity())
    }

    override suspend fun updateHistoryItem(item: HistoryItem) {
        historyDao.updateHistoryItem(item.toEntity())
    }

    override suspend fun deleteHistoryItem(id: String) {
        historyDao.deleteHistoryItemById(id)
    }

    override suspend fun deleteHistoryBefore(timestamp: Long) {
        historyDao.deleteHistoryBefore(timestamp)
    }

    override suspend fun deleteAllHistory() {
        historyDao.deleteAllHistory()
    }

    override fun searchHistory(query: String): Flow<List<HistoryItem>> {
        return historyDao.searchHistory(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
