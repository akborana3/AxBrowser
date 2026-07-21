package com.akay.feature.browser.webview

import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class AxWebChromeClient(
    private val onProgressChange: (Int) -> Unit,
    private val onTitleChange: (String) -> Unit,
    private val onUrlChange: (String) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChange(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleChange(it) }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                // Log errors in debug builds
            }
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.deny()
    }
}
