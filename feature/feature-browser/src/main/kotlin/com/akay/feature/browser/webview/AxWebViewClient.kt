package com.akay.feature.browser.webview

import android.content.Context
import android.graphics.Bitmap
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.akay.core.data.proxy.OkHttpProxies
import com.akay.core.domain.model.ProxyAddresses
import com.akay.core.domain.model.ProxyServer
import com.akay.feature.browser.adblock.AdBlockEngine
import com.akay.feature.browser.devconsole.NetworkInterceptor
import com.akay.feature.browser.devconsole.NetworkRequest
import com.akay.feature.browser.devconsole.RuleAction
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class AxWebViewClient(
    private val context: Context,
    private val onPageStarted: (url: String) -> Unit,
    private val onPageFinished: (url: String, title: String?) -> Unit,
    private val onError: (String) -> Unit,
    private val adBlockerEnabled: () -> Boolean = { true },
    private val httpsUpgradeEnabled: () -> Boolean = { true },
    private val onMediaDetected: (url: String, mimeType: String?) -> Unit = { _, _ -> },
    private val activeProxy: () -> ProxyServer? = { null },
    private val onMainFrameFailed: () -> Unit = {},
    /**
     * When this returns true it has taken the navigation and will call [proceed]
     * itself after the proxy rotation is installed. Used so per-navigation
     * rotation doesn't race the request it's supposed to cover.
     */
    private val deferForRotation: (proceed: () -> Unit) -> Boolean = { false }
) : WebViewClient() {

    @Volatile
    private var programmaticUrl: String? = null

    /** Address-bar headers. Re-applied when a rotation cancels and replays a navigation. */
    @Volatile
    var extraHeaders: Map<String, String> = emptyMap()

    /** The next main-frame load of [url] is ours (reload after a script change). Don't rotate for it. */
    fun markProgrammaticLoad(url: String?) {
        programmaticUrl = url
    }

    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        val proxy = activeProxy()
        val proxyUsername = proxy?.username
        // Only answer when the challenge names the proxy. A null host used to
        // send the proxy password to whatever site asked for a login.
        // SOCKS authentication is not an HTTP 407 and can't be answered here.
        if (handler != null && proxy != null && !proxy.usesSocks && !proxyUsername.isNullOrBlank() &&
            ProxyAddresses.matchesChallenge(proxy.host, host)
        ) {
            handler.proceed(proxyUsername, proxy.password.orEmpty())
            return
        }
        super.onReceivedHttpAuthRequest(view, handler, host, realm)
    }

    private val blockedDomains = mutableSetOf<String>()

    // shouldInterceptRequest runs on a background thread, so WebView methods
    // (including view.getUrl()) must never be called from it directly - doing
    // so throws a fatal "WebView methods must be called on the same thread"
    // RuntimeException on modern WebView builds. We track the current page's
    // origin here instead, updated only from the UI-thread callbacks below.
    @Volatile
    private var currentPageOrigin: String? = null

    private fun originOf(url: String?): String? =
        if (url.isNullOrBlank()) null else runCatching {
            java.net.URI(url).let { if (it.scheme != null && it.host != null) "${it.scheme}://${it.host}" else null }
        }.getOrNull()

    private val interceptorClient by lazy {
        OkHttpProxies.apply(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true),
            activeProxy
        ).build()
    }

    private val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".avi", ".mov", ".m3u8", ".mpd", ".ts", ".flv")
    private val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac", ".opus")

    fun setBlockedDomains(domains: Set<String>) {
        blockedDomains.clear()
        blockedDomains.addAll(domains)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url.toString()

        val netReq = NetworkRequest(
            url = url,
            method = req.method ?: "GET",
            requestHeaders = req.requestHeaders ?: emptyMap()
        )
        NetworkInterceptor.onRequest(netReq)

        // User-defined interceptor rules always apply (independent of ad blocker).
        val rule = NetworkInterceptor.matchRule(url)
        if (rule != null) {
            when (rule.action) {
                RuleAction.BLOCK -> {
                    NetworkInterceptor.markBlocked(url)
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                }
                RuleAction.REDIRECT -> {
                    val target = rule.value.trim()
                    if (target.isNotEmpty()) fetchModified(target, req.requestHeaders, null)?.let { return it }
                }
                RuleAction.ADD_HEADER -> {
                    val idx = rule.value.indexOf(':')
                    if (idx > 0) {
                        val hName = rule.value.substring(0, idx).trim()
                        val hVal = rule.value.substring(idx + 1).trim()
                        fetchModified(url, req.requestHeaders, hName to hVal)?.let { return it }
                    }
                }
            }
        }

        if (adBlockerEnabled() && (AdBlockEngine.shouldBlock(url, currentPageOrigin) || isBlocked(url))) {
            AdBlockEngine.onBlocked()
            NetworkInterceptor.markBlocked(url)
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
        }

        val lower = url.lowercase()
        val isVideo = videoExtensions.any { lower.contains(it) }
        val isAudio = audioExtensions.any { lower.contains(it) }
        if (isVideo || isAudio) {
            val mime = guessMime(lower)
            onMediaDetected(url, mime)
        }

        val accept = req.requestHeaders?.get("Accept") ?: ""
        if (accept.contains("video/") || accept.contains("audio/")) {
            onMediaDetected(url, null)
        }

        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val req = request ?: return false
        val raw = req.url?.toString() ?: return false
        val target = if (httpsUpgradeEnabled() && raw.startsWith("http://")) {
            raw.replaceFirst("http://", "https://")
        } else {
            raw
        }
        if (!req.isForMainFrame) return loadReplacement(view, raw, target)
        if (programmaticUrl != null && (raw == programmaticUrl || target == programmaticUrl)) {
            programmaticUrl = null
            return loadReplacement(view, raw, target)
        }
        val method = req.method ?: "GET"
        val canRotate = !req.isRedirect &&
            (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)) &&
            (target.startsWith("https://") || target.startsWith("http://"))
        if (canRotate && view != null && deferForRotation {
                programmaticUrl = target
                val headers = extraHeaders
                view.post {
                    if (headers.isEmpty()) view.loadUrl(target) else view.loadUrl(target, headers)
                }
            }
        ) {
            return true
        }
        return loadReplacement(view, raw, target)
    }

    private fun loadReplacement(view: WebView?, raw: String, target: String): Boolean {
        if (target == raw) return false
        view?.loadUrl(target)
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentPageOrigin = originOf(url)
        url?.let { onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        currentPageOrigin = originOf(url)
        onPageFinished(url ?: "", view?.title)
    }

    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        onMainFrameFailed()
        onError(description ?: "Unknown error")
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        if (!request.isForMainFrame) return
        onMainFrameFailed()
        onError(error.description?.toString() ?: "Unknown error")
    }

    /**
     * Fetches [url] with OkHttp, optionally injecting one extra header, and
     * returns it as a WebResourceResponse so the interceptor can redirect or
     * modify a request. Returns null on any failure so the page still loads.
     */
    private fun fetchModified(
        url: String,
        originalHeaders: Map<String, String>?,
        extraHeader: Pair<String, String>?
    ): WebResourceResponse? = try {
        val builder = Request.Builder().url(url).get()
        originalHeaders?.forEach { (k, v) ->
            if (!k.equals("Accept-Encoding", ignoreCase = true)) runCatching { builder.header(k, v) }
        }
        extraHeader?.let { builder.header(it.first, it.second) }
        val response = interceptorClient.newCall(builder.build()).execute()
        val body = response.body ?: return null
        val contentType = response.header("Content-Type") ?: "application/octet-stream"
        val mime = contentType.substringBefore(";").trim().ifEmpty { "application/octet-stream" }
        val charset = contentType.substringAfter("charset=", "UTF-8").substringBefore(";").trim().ifEmpty { "UTF-8" }
        NetworkInterceptor.markBlocked(url) // mark as intercepted in the log
        WebResourceResponse(mime, charset, body.byteStream())
    } catch (_: Exception) {
        null
    }

    private fun isBlocked(url: String): Boolean {
        if (blockedDomains.isEmpty()) return false
        return try {
            val host = java.net.URI(url).host ?: return false
            blockedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
        } catch (e: Exception) { false }
    }

    private fun guessMime(url: String): String? = when {
        url.contains(".mp4") -> "video/mp4"
        url.contains(".webm") -> "video/webm"
        url.contains(".mkv") -> "video/x-matroska"
        url.contains(".m3u8") -> "application/x-mpegurl"
        url.contains(".mpd") -> "application/dash+xml"
        url.contains(".mp3") -> "audio/mpeg"
        url.contains(".m4a") -> "audio/mp4"
        url.contains(".aac") -> "audio/aac"
        url.contains(".ogg") -> "audio/ogg"
        else -> null
    }
}
