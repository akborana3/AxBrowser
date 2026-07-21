package com.akay.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.core.ui.theme.Primary
import com.akay.feature.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Appearance") {
                SettingsSwitchItem(
                    title = "Dark Mode",
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
                SettingsSwitchItem(
                    title = "Desktop Mode",
                    checked = uiState.isDesktopMode,
                    onCheckedChange = { viewModel.setDesktopMode(it) }
                )
            }

            SettingsSection(title = "Privacy & Security") {
                SettingsSwitchItem(
                    title = "Ad Blocker",
                    checked = uiState.isAdBlockerEnabled,
                    onCheckedChange = { viewModel.setAdBlockerEnabled(it) }
                )
                SettingsSwitchItem(
                    title = "Tracker Blocker",
                    checked = uiState.isTrackerBlockerEnabled,
                    onCheckedChange = { viewModel.setTrackerBlockerEnabled(it) }
                )
                SettingsSwitchItem(
                    title = "HTTPS Upgrade",
                    checked = uiState.isHttpsUpgrade,
                    onCheckedChange = { viewModel.setHttpsUpgrade(it) }
                )
                SettingsSwitchItem(
                    title = "JavaScript",
                    checked = uiState.isJavascriptEnabled,
                    onCheckedChange = { viewModel.setJavascriptEnabled(it) }
                )
            }

            SettingsSection(title = "Downloads") {
                Text(
                    text = "Max concurrent downloads: ${uiState.maxConcurrentDownloads}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            SettingsSection(title = "General") {
                SettingsSwitchItem(
                    title = "Clear cache on exit",
                    checked = uiState.clearCacheOnExit,
                    onCheckedChange = { viewModel.setClearCacheOnExit(it) }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.5f)
            )
        )
    }
}
