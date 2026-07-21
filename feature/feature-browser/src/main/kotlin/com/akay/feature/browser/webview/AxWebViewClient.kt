package com.akay.feature.browser.webview

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class AxWebViewClient(
    private val context: Context,
    private val onUrlChange: (String) -> Unit,
    private val onTitleChange: (String) -> Unit,
    private val onProgressChange: (Int) -> Unit,
    private val onError: (String) -> Unit,
    private val adBlockerEnabled: Boolean = true,
    private val httpsUpgradeEnabled: Boolean = true
) : WebViewClient() {

    private val blockedDomains = mutableSetOf<String>()

    fun setBlockedDomains(domains: Set<String>) {
        blockedDomains.clear()
        blockedDomains.addAll(domains)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        if (httpsUpgradeEnabled && url.startsWith("http://")) {
            val httpsUrl = url.replaceFirst("http://", "https://")
            view?.loadUrl(httpsUrl)
            return true
        }

        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        if (adBlockerEnabled && isBlocked(url)) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("".toByteArray())
            )
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onUrlChange(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onProgressChange(100)
    }

    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        onError(description ?: "Unknown error occurred")
    }

    private fun isBlocked(url: String): Boolean {
        try {
            val host = java.net.URI(url).host ?: return false
            return blockedDomains.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (e: Exception) {
            return false
        }
    }
}
