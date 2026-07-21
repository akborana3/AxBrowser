package com.akay.feature.bookmarks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.domain.model.Bookmark
import com.akay.core.domain.model.BookmarkFolder
import com.akay.core.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val folders: List<BookmarkFolder> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            bookmarkRepository.getAllBookmarks().collect { bookmarks ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
            }
        }
        viewModelScope.launch {
            bookmarkRepository.getAllFolders().collect { folders ->
                _uiState.value = _uiState.value.copy(folders = folders)
            }
        }
    }

    fun addBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(bookmark)
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun searchBookmarks(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            loadBookmarks()
        } else {
            viewModelScope.launch {
                bookmarkRepository.searchBookmarks(query).collect { bookmarks ->
                    _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
                }
            }
        }
    }
}
