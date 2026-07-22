package com.akay.feature.browser.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.domain.model.Tab
import com.akay.core.domain.repository.TabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTab: Tab? = null,
    val isLoading: Boolean = false,
    val url: String = "",
    val displayUrl: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isIncognito: Boolean = false,
    val error: String? = null,
    val showTabSwitcher: Boolean = false,
    val showDownloadSheet: Boolean = false,
    val devConsoleVisible: Boolean = false,
    val pageHtml: String = "",
    val detectedMediaCount: Int = 0
)

sealed class BrowserUiEvent {
    data object NavigateBack : BrowserUiEvent()
    data class ShowSnackbar(val message: String) : BrowserUiEvent()
    data class NavigateToUrl(val url: String) : BrowserUiEvent()
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<BrowserUiEvent?>(null)
    val events: StateFlow<BrowserUiEvent?> = _events.asStateFlow()

    init {
        loadTabs()
    }

    private fun loadTabs() {
        viewModelScope.launch {
            tabRepository.getAllTabs().collect { tabs ->
                _uiState.value = _uiState.value.copy(tabs = tabs)
                if (tabs.isEmpty()) {
                    createNewTab()
                } else if (_uiState.value.activeTab == null) {
                    val activeTab = tabs.find { it.isActive } ?: tabs.first()
                    setActiveTab(activeTab)
                }
            }
        }
    }

    fun createNewTab(url: String = "about:blank", incognito: Boolean = false) {
        viewModelScope.launch {
            val tab = Tab(
                url = url,
                isIncognito = incognito,
                isActive = true
            )
            tabRepository.createTab(tab)
            tabRepository.setActiveTab(tab.id)
        }
    }

    fun setActiveTab(tab: Tab) {
        viewModelScope.launch {
            tabRepository.setActiveTab(tab.id)
            _uiState.value = _uiState.value.copy(
                activeTab = tab,
                url = tab.url,
                displayUrl = tab.url,
                title = tab.title,
                showTabSwitcher = false
            )
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(displayUrl = url)
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
        viewModelScope.launch {
            _uiState.value.activeTab?.let { tab ->
                tabRepository.updateTab(tab.copy(url = _uiState.value.displayUrl, title = title, lastAccessed = System.currentTimeMillis()))
            }
        }
    }

    fun updateProgress(progress: Int) {
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    fun navigateToUrl(url: String) {
        val processedUrl = when {
            url.isBlank() -> "about:blank"
            !url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:") -> {
                "https://www.google.com/search?q=$url"
            }
            else -> url
        }
        _uiState.value = _uiState.value.copy(url = processedUrl, displayUrl = processedUrl)
        viewModelScope.launch {
            _uiState.value.activeTab?.let { tab ->
                tabRepository.updateTab(tab.copy(url = processedUrl, lastAccessed = System.currentTimeMillis()))
            }
        }
    }

    fun toggleTabSwitcher() {
        _uiState.value = _uiState.value.copy(showTabSwitcher = !_uiState.value.showTabSwitcher)
    }

    fun toggleDevConsole() {
        _uiState.value = _uiState.value.copy(devConsoleVisible = !_uiState.value.devConsoleVisible)
    }

    fun updatePageHtml(html: String) {
        _uiState.value = _uiState.value.copy(pageHtml = html)
    }

    fun updateDetectedMediaCount(count: Int) {
        _uiState.value = _uiState.value.copy(detectedMediaCount = count)
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            tabRepository.deleteTab(tabId)
            if (_uiState.value.activeTab?.id == tabId) {
                val remainingTabs = _uiState.value.tabs.filter { it.id != tabId }
                if (remainingTabs.isNotEmpty()) {
                    setActiveTab(remainingTabs.first())
                } else {
                    createNewTab()
                }
            }
        }
    }

    fun toggleIncognito() {
        val newState = !_uiState.value.isIncognito
        _uiState.value = _uiState.value.copy(isIncognito = newState)
        createNewTab(incognito = newState)
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
