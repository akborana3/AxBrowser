package com.akay.feature.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.domain.model.HistoryItem
import com.akay.core.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val history: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyRepository.getAllHistory().collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyRepository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.deleteAllHistory()
        }
    }

    fun searchHistory(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadHistory()
        } else {
            viewModelScope.launch {
                historyRepository.searchHistory(query).collect { history ->
                    _uiState.value = _uiState.value.copy(history = history)
                }
            }
        }
    }
}
