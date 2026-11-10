package com.akay.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akay.core.data.datastore.AxPreferences
import com.akay.core.data.proxy.OkHttpProxies
import com.akay.core.data.proxy.ProxyManager
import com.akay.core.domain.model.ProxyListParser
import com.akay.core.domain.model.ProxyRotation
import com.akay.core.domain.model.ProxyRotationMode
import com.akay.core.domain.model.ProxyServer
import com.akay.core.domain.model.ProxyType
import com.akay.core.domain.repository.ProxyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ProxyTestUi(val proxyId: String, val inProgress: Boolean, val message: String)

data class ProxyImportReport(val summary: String, val errors: List<String>)

data class ProxySettingsUiState(
    val proxies: List<ProxyServer> = emptyList(),
    val enabled: Boolean = false,
    val rotationMode: String = ProxyRotationMode.MANUAL.name,
    val rotationIntervalMinutes: Int = 10,
    val bypassRules: List<String> = listOf("<local>"),
    val activeIndex: Int = 0
)

@HiltViewModel
class ProxySettingsViewModel @Inject constructor(
    private val proxyRepository: ProxyRepository,
    private val preferences: AxPreferences,
    private val proxyManager: ProxyManager
) : ViewModel() {

    val connectionError: StateFlow<String?> = proxyManager.lastError
    val warning: StateFlow<String?> = proxyManager.warning
    val isConnected: StateFlow<Boolean> = proxyManager.isApplied
    val activeProxy: StateFlow<ProxyServer?> = proxyManager.activeProxy

    private val _testState = MutableStateFlow<ProxyTestUi?>(null)
    val testState: StateFlow<ProxyTestUi?> = _testState.asStateFlow()

    private val _importReport = MutableStateFlow<ProxyImportReport?>(null)
    val importReport: StateFlow<ProxyImportReport?> = _importReport.asStateFlow()

    val uiState: StateFlow<ProxySettingsUiState> = combine(
        proxyRepository.observeAll(),
        preferences.proxyEnabled,
        preferences.proxyRotationMode,
        preferences.proxyRotationIntervalMinutes,
        preferences.proxyBypassRulesJson,
        preferences.proxyActiveIndex
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val proxies = values[0] as List<ProxyServer>
        val enabled = values[1] as Boolean
        val rotationMode = values[2] as String
        val intervalMin = values[3] as Int
        val bypassJson = values[4] as String
        val activeIndex = values[5] as Int
        val bypassRules = runCatching {
            val arr = JSONArray(bypassJson)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(listOf("<local>"))
        ProxySettingsUiState(proxies, enabled, rotationMode, intervalMin, bypassRules, activeIndex)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxySettingsUiState())

    fun setProxyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setProxyEnabled(enabled) }
    }

    fun setRotationMode(mode: ProxyRotationMode) {
        viewModelScope.launch { preferences.setProxyRotationMode(mode.name) }
    }

    fun setRotationInterval(minutes: Int) {
        viewModelScope.launch { preferences.setProxyRotationIntervalMinutes(minutes) }
    }

    fun rotateNow() {
        proxyManager.rotateNext()
    }

    fun saveProxy(
        id: String?,
        label: String,
        type: ProxyType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        enabled: Boolean,
        timezone: String? = null,
        locale: String? = null,
        remoteDns: Boolean = false
    ) {
        viewModelScope.launch {
            proxyRepository.save(
                id, label, type, host, port, username, password, enabled,
                timezone = timezone?.trim()?.ifBlank { null },
                locale = locale?.trim()?.ifBlank { null },
                remoteDns = remoteDns
            )
        }
    }

    fun importProxies(text: String) {
        viewModelScope.launch {
            val parsed = ProxyListParser.parse(text)
            if (parsed.proxies.isEmpty() && parsed.errors.isEmpty()) {
                _importReport.value = ProxyImportReport("Nothing to import. Add one proxy per line.", emptyList())
                return@launch
            }
            val existing = proxyRepository.getAllOnce()
            val seen = existing.map { Triple(it.host.lowercase(), it.port, it.type) }.toMutableSet()
            var saved = 0
            for (proxy in parsed.proxies) {
                val key = Triple(proxy.host.lowercase(), proxy.port, proxy.type)
                if (!seen.add(key)) continue
                val match = existing.firstOrNull {
                    it.host.equals(proxy.host, ignoreCase = true) && it.port == proxy.port && it.type == proxy.type
                }
                proxyRepository.save(
                    id = match?.id,
                    label = match?.label?.takeIf { it.isNotBlank() } ?: proxy.label,
                    type = proxy.type,
                    host = proxy.host,
                    port = proxy.port,
                    username = proxy.username,
                    password = proxy.password,
                    enabled = true,
                    timezone = match?.timezone,
                    locale = match?.locale,
                    remoteDns = proxy.remoteDns
                )
                saved++
            }
            val noun = if (saved == 1) "proxy" else "proxies"
            _importReport.value = ProxyImportReport(
                summary = "Imported $saved $noun.",
                errors = parsed.errors.map { "Line ${it.lineNumber}: ${it.message}" }
            )
        }
    }

    fun dismissImportReport() {
        _importReport.value = null
    }

    fun testProxy(proxy: ProxyServer) {
        if (_testState.value?.inProgress == true) return
        viewModelScope.launch {
            _testState.value = ProxyTestUi(proxy.id, inProgress = true, message = "Checking ${proxy.label}…")
            val message = withContext(Dispatchers.IO) { probe(proxy) }
            _testState.value = ProxyTestUi(proxy.id, inProgress = false, message = message)
        }
    }

    private fun probe(proxy: ProxyServer): String {
        if (!ProxyRotation.isEligible(proxy)) {
            return "This proxy needs a host and a port from 1 to 65535."
        }
        val open = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(proxy.host, proxy.port), 8_000)
            }
        }
        if (open.isFailure) {
            val reason = open.exceptionOrNull()?.message ?: "connection failed"
            return "Couldn't open ${proxy.host}:${proxy.port}. $reason"
        }
        if (proxy.usesSocks && proxyManager.activeProxy.value?.id != proxy.id) {
            return "Port is open. Make this the active proxy to check the exit IP — SOCKS logins use the proxy that's already applied."
        }
        return runCatching {
            val client = OkHttpProxies.apply(
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .callTimeout(12, TimeUnit.SECONDS),
                { proxy }
            ).build()
            val response = client.newCall(
                Request.Builder()
                    .url("https://api.ipify.org?format=text")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            ).execute()
            response.use {
                val body = it.body?.string()?.trim().orEmpty().take(64)
                if (!it.isSuccessful) "Proxy answered HTTP ${it.code}."
                else if (body.isBlank()) "Connected, but the IP check came back empty."
                else "Exit IP $body"
            }
        }.getOrElse { error ->
            "Port is open, but the IP check failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun deleteProxy(id: String) {
        viewModelScope.launch { proxyRepository.delete(id) }
    }

    fun toggleProxyEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { proxyRepository.setEnabled(id, enabled) }
    }

    fun addBypassRule(rule: String) {
        if (rule.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.bypassRules
            if (rule in current) return@launch
            val updated = current + rule
            preferences.setProxyBypassRulesJson(JSONArray(updated).toString())
        }
    }

    fun removeBypassRule(rule: String) {
        viewModelScope.launch {
            val updated = uiState.value.bypassRules.filterNot { it == rule }
            preferences.setProxyBypassRulesJson(JSONArray(updated).toString())
        }
    }
}
