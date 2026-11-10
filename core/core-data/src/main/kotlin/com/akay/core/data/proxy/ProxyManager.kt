package com.akay.core.data.proxy

import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.akay.core.data.datastore.AxPreferences
import com.akay.core.data.datastore.ProxyPointer
import com.akay.core.domain.model.ProxyRotation
import com.akay.core.domain.model.ProxyRotationMode
import com.akay.core.domain.model.ProxyServer
import com.akay.core.domain.repository.ProxyRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies one proxy at a time to the process-wide WebView [ProxyController].
 * The active entry is the saved proxy id when that proxy is still usable,
 * otherwise the saved index. Rotation writes that pair; this collector is the
 * only place that talks to WebView, so a rotate during page load can't apply
 * a stale snapshot over a newer one.
 */
@Singleton
class ProxyManager @Inject constructor(
    private val preferences: AxPreferences,
    private val proxyRepository: ProxyRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val immediateExecutor = Executor { it.run() }
    private val applyMutex = Mutex()
    private val rotationMutex = Mutex()

    private val _activeProxy = MutableStateFlow<ProxyServer?>(null)
    val activeProxy: StateFlow<ProxyServer?> = _activeProxy.asStateFlow()

    private val _isApplied = MutableStateFlow(false)
    val isApplied: StateFlow<Boolean> = _isApplied.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    /** False until the first apply attempt after a WebView exists (or it times out). */
    private val _proxyReady = MutableStateFlow(false)
    val proxyReady: StateFlow<Boolean> = _proxyReady.asStateFlow()

    private var rotationJob: kotlinx.coroutines.Job? = null
    private var timerSignature: String? = null
    private var started = false

    @Volatile
    private var webViewReady = false
    private val firstState = CompletableDeferred<Unit>()
    private var latest: Snapshot? = null
    private var applied: Snapshot? = null

    @Volatile
    private var proxyFeatureEnabled = false
    @Volatile
    private var rotationModeName: String = ProxyRotationMode.MANUAL.name
    @Volatile
    private var eligibleCount = 0

    @Volatile
    private var pageLoading = false
    @Volatile
    private var deferredTimedRotation = false
    private var watchdogToken = 0

    fun start() {
        if (started) return
        started = true
        OkHttpProxies.installSocksAuthenticator { _activeProxy.value }
        scope.launch {
            combine(
                combine(
                    preferences.proxyEnabled,
                    proxyRepository.observeAll(),
                    preferences.proxyBypassRulesJson,
                    preferences.proxyPointer,
                    preferences.proxyRotationMode
                ) { enabled, proxies, bypassJson, pointer, mode ->
                    ProxyInputs(enabled, proxies, bypassJson, pointer, mode)
                },
                preferences.proxyRotationIntervalMinutes
            ) { inputs, minutes ->
                Snapshot(
                    enabled = inputs.enabled,
                    proxies = inputs.proxies,
                    bypassJson = inputs.bypassJson,
                    index = inputs.pointer.index,
                    activeId = inputs.pointer.id,
                    mode = inputs.mode,
                    intervalMinutes = minutes
                )
            }
                .catch { e -> Log.w(TAG, "Proxy state flow failed", e) }
                .collect { snap ->
                    latest = snap
                    proxyFeatureEnabled = snap.enabled
                    rotationModeName = snap.mode
                    eligibleCount = ProxyRotation.eligible(snap.proxies).size
                    if (!firstState.isCompleted) firstState.complete(Unit)
                    if (webViewReady) drain()
                    syncTimer(snap)
                }
        }
    }

    /** Call once a WebView exists. Pages should wait for [proxyReady] before the first load. */
    fun markWebViewReady() {
        if (webViewReady) return
        webViewReady = true
        scope.launch {
            withTimeoutOrNull(2_500) {
                firstState.await()
                drain()
            }
            _proxyReady.value = true
        }
    }

    fun onMainFrameLoadStarted() {
        pageLoading = true
        armWatchdog()
    }

    fun onMainFrameLoadFinished() {
        if (!pageLoading && !deferredTimedRotation) return
        pageLoading = false
        watchdogToken++
        if (deferredTimedRotation) {
            deferredTimedRotation = false
            scope.launch { rotateNextLocked() }
        }
    }

    /**
     * Per-navigation rotation has to finish before the navigation starts, or the
     * document loads on the previous exit IP. Returns false when this navigation
     * should proceed immediately (manual mode, one proxy, proxy off).
     */
    fun interceptMainFrameNavigation(proceed: () -> Unit): Boolean {
        if (!proxyFeatureEnabled || rotationModeName != ProxyRotationMode.PER_NAVIGATION.name) return false
        if (eligibleCount <= 1 || !webViewReady) return false
        pageLoading = true
        armWatchdog()
        scope.launch {
            runCatching { rotateAndAwait() }
            proceed()
        }
        return true
    }

    fun rotateNext() {
        scope.launch { runCatching { rotateNextLocked() } }
    }

    suspend fun rotateAndAwait(): ProxyServer? {
        val targetId = rotateNextLocked() ?: return null
        withTimeoutOrNull(2_000) { activeProxy.first { it?.id == targetId } }
        return activeProxy.value
    }

    private fun armWatchdog() {
        val token = ++watchdogToken
        scope.launch {
            delay(20_000)
            if (token == watchdogToken && pageLoading) onMainFrameLoadFinished()
        }
    }

    private suspend fun rotateNextLocked(): String? = rotationMutex.withLock {
        val eligible = ProxyRotation.eligible(
            runCatching { proxyRepository.getAllOnce() }.getOrDefault(emptyList())
        )
        if (eligible.isEmpty()) return null
        val pointer = runCatching { preferences.proxyPointer.first() }.getOrDefault(ProxyPointer(0, ""))
        val current = ProxyRotation.indexOf(eligible, pointer.index, pointer.id)
        val next = if (eligible.size == 1) current else ProxyRotation.nextIndex(current, eligible.size)
        val chosen = eligible[next]
        if (pointer.index != next || pointer.id != chosen.id) {
            runCatching { preferences.setActiveProxy(next, chosen.id) }
        }
        chosen.id
    }

    private suspend fun drain() {
        applyMutex.withLock {
            while (true) {
                val snap = latest ?: return@withLock
                if (snap == applied) return@withLock
                applySnapshot(snap)
                applied = snap
            }
        }
    }

    private suspend fun applySnapshot(snap: Snapshot) {
        if (!isSupported()) {
            _isApplied.value = false
            _activeProxy.value = null
            _warning.value = null
            if (snap.enabled) {
                _lastError.value = "This WebView doesn't support a proxy override, so traffic stays on the device network."
            }
            return
        }
        val eligible = ProxyRotation.eligible(snap.proxies)
        if (!snap.enabled || eligible.isEmpty()) {
            _warning.value = null
            _lastError.value = if (snap.enabled) {
                "Proxy is on, but every entry is disabled or missing a valid host and port."
            } else {
                null
            }
            clearOverride()
            return
        }
        val start = ProxyRotation.indexOf(eligible, snap.index, snap.activeId)
        val selected = eligible[start]
        var appliedProxy: ProxyServer? = null
        for (candidate in ProxyRotation.failoverOrder(eligible, start)) {
            if (tryApply(candidate, snap.bypassJson)) {
                appliedProxy = candidate
                break
            }
        }
        val chosen = appliedProxy
        if (chosen == null) {
            _isApplied.value = false
            _activeProxy.value = null
            _warning.value = null
            if (_lastError.value.isNullOrBlank()) {
                _lastError.value = "Every proxy failed to apply."
            }
            clearOverride()
            return
        }
        if (chosen.id != selected.id || snap.index != start || snap.activeId != selected.id) {
            val healedIndex = eligible.indexOfFirst { it.id == chosen.id }.coerceAtLeast(0)
            if (snap.index != healedIndex || snap.activeId != chosen.id) {
                runCatching { preferences.setActiveProxy(healedIndex, chosen.id) }
            }
        }
    }

    private suspend fun tryApply(proxy: ProxyServer, bypassJson: String): Boolean {
        val confirmed = CompletableDeferred<Unit>()
        val submitted = runCatching {
            val builder = ProxyConfig.Builder()
            builder.addProxyRule(proxy.toProxyRule())
            bypassRules(bypassJson).forEach { rule ->
                if (rule.isNotBlank()) runCatching { builder.addBypassRule(rule) }
            }
            ProxyController.getInstance().setProxyOverride(builder.build(), immediateExecutor) {
                publishApplied(proxy)
                if (!confirmed.isCompleted) confirmed.complete(Unit)
            }
            true
        }.getOrElse { e ->
            Log.w(TAG, "Failed to apply proxy ${proxy.label}", e)
            _lastError.value = e.message ?: "Couldn't apply ${proxy.label}. Check the host and port."
            false
        }
        if (!submitted) return false
        // The listener runs once Chromium has installed the override. If it never
        // does, still treat the submit as success so navigation isn't stuck and
        // we don't fail over onto a second proxy that was actually accepted.
        if (withTimeoutOrNull(1_500) { confirmed.await() } == null) publishApplied(proxy)
        return true
    }

    private fun publishApplied(proxy: ProxyServer) {
        _activeProxy.value = proxy
        _isApplied.value = true
        _lastError.value = null
        _warning.value = socksWarning(proxy)
    }

    private suspend fun clearOverride() {
        if (!isSupported()) {
            _activeProxy.value = null
            _isApplied.value = false
            return
        }
        val confirmed = CompletableDeferred<Unit>()
        val submitted = runCatching {
            ProxyController.getInstance().clearProxyOverride(immediateExecutor) {
                _activeProxy.value = null
                _isApplied.value = false
                if (!confirmed.isCompleted) confirmed.complete(Unit)
            }
            true
        }.getOrElse { e ->
            Log.w(TAG, "Failed to clear proxy override", e)
            _lastError.value = e.message ?: "Couldn't turn the proxy off."
            false
        }
        if (submitted) withTimeoutOrNull(1_500) { confirmed.await() }
    }

    private fun socksWarning(proxy: ProxyServer): String? {
        if (!proxy.usesSocks || !proxy.requiresAuth) return null
        return "WebView can't send a SOCKS username. Pages through ${proxy.label} may fail; downloads still use the saved login."
    }

    private fun bypassRules(bypassJson: String): List<String> {
        if (bypassJson.isBlank()) return listOf("<local>")
        return runCatching {
            val arr = JSONArray(bypassJson)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(listOf("<local>"))
    }

    private fun isSupported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }.getOrDefault(false)

    private fun syncTimer(snap: Snapshot) {
        val count = ProxyRotation.eligible(snap.proxies).size
        val minutes = snap.intervalMinutes.coerceIn(1, 24 * 60)
        val signature = "${snap.enabled}|${snap.mode}|$minutes|$count"
        if (signature == timerSignature) return
        timerSignature = signature
        rotationJob?.cancel()
        if (!snap.enabled || snap.mode != ProxyRotationMode.TIMED.name || count <= 1) return
        rotationJob = scope.launch {
            while (isActive) {
                delay(minutes * 60_000L)
                if (pageLoading) deferredTimedRotation = true else rotateNextLocked()
            }
        }
    }

    private data class ProxyInputs(
        val enabled: Boolean,
        val proxies: List<ProxyServer>,
        val bypassJson: String,
        val pointer: ProxyPointer,
        val mode: String
    )

    private data class Snapshot(
        val enabled: Boolean,
        val proxies: List<ProxyServer>,
        val bypassJson: String,
        val index: Int,
        val activeId: String,
        val mode: String,
        val intervalMinutes: Int
    )

    private companion object {
        const val TAG = "ProxyManager"
    }
}
