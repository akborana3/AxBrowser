package com.akay.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.data.datastore.AxPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val searchEngine: String = "https://www.google.com/search?q=",
    val homepage: String = "about:blank",
    val isDarkMode: Boolean = true,
    val isAdBlockerEnabled: Boolean = true,
    val isTrackerBlockerEnabled: Boolean = true,
    val isHttpsUpgrade: Boolean = true,
    val isJavascriptEnabled: Boolean = true,
    val maxConcurrentDownloads: Int = 3,
    val isDesktopMode: Boolean = false,
    val fontSize: Int = 100,
    val clearCacheOnExit: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AxPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferences.searchEngine.collect { _uiState.value = _uiState.value.copy(searchEngine = it) }
        }
        viewModelScope.launch {
            preferences.isDarkMode.collect { _uiState.value = _uiState.value.copy(isDarkMode = it) }
        }
        viewModelScope.launch {
            preferences.isAdBlockerEnabled.collect { _uiState.value = _uiState.value.copy(isAdBlockerEnabled = it) }
        }
        viewModelScope.launch {
            preferences.isTrackerBlockerEnabled.collect { _uiState.value = _uiState.value.copy(isTrackerBlockerEnabled = it) }
        }
        viewModelScope.launch {
            preferences.isHttpsUpgrade.collect { _uiState.value = _uiState.value.copy(isHttpsUpgrade = it) }
        }
        viewModelScope.launch {
            preferences.isJavascriptEnabled.collect { _uiState.value = _uiState.value.copy(isJavascriptEnabled = it) }
        }
        viewModelScope.launch {
            preferences.maxConcurrentDownloads.collect { _uiState.value = _uiState.value.copy(maxConcurrentDownloads = it) }
        }
        viewModelScope.launch {
            preferences.isDesktopMode.collect { _uiState.value = _uiState.value.copy(isDesktopMode = it) }
        }
        viewModelScope.launch {
            preferences.fontSize.collect { _uiState.value = _uiState.value.copy(fontSize = it) }
        }
        viewModelScope.launch {
            preferences.clearCacheOnExit.collect { _uiState.value = _uiState.value.copy(clearCacheOnExit = it) }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkMode(enabled) }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAdBlockerEnabled(enabled) }
    }

    fun setTrackerBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTrackerBlockerEnabled(enabled) }
    }

    fun setHttpsUpgrade(enabled: Boolean) {
        viewModelScope.launch { preferences.setHttpsUpgrade(enabled) }
    }

    fun setJavascriptEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setJavascriptEnabled(enabled) }
    }

    fun setDesktopMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDesktopMode(enabled) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { preferences.setFontSize(size) }
    }

    fun setClearCacheOnExit(enabled: Boolean) {
        viewModelScope.launch { preferences.setClearCacheOnExit(enabled) }
    }
}
