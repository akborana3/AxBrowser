package com.akay.feature.browser.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.core.ui.components.AxProgressBar
import com.akay.core.ui.theme.Primary
import com.akay.feature.browser.devconsole.DevConsolePanel
import com.akay.feature.browser.devconsole.NetworkInterceptor
import com.akay.feature.browser.devconsole.NetworkRequest
import com.akay.feature.browser.viewmodel.BrowserViewModel
import com.akay.feature.downloads.ui.DetectedMediaUi
import com.akay.feature.downloads.ui.MediaBottomSheet

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditingUrl by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastNavigatedUrl by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMediaSheet by remember { mutableStateOf(false) }
    var detectedMedia by remember { mutableStateOf<List<DetectedMediaUi>>(emptyList()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isEditingUrl) {
                            OutlinedTextField(
                                value = uiState.displayUrl,
                                onValueChange = { viewModel.updateUrl(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search or enter URL") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        viewModel.navigateToUrl(uiState.displayUrl)
                                        isEditingUrl = false
                                        keyboardController?.hide()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        } else {
                            Column {
                                Text(
                                    text = uiState.title.ifEmpty { "New Tab" },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1
                                )
                                Text(
                                    text = uiState.displayUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleTabSwitcher() }) {
                            Icon(Icons.Default.Menu, contentDescription = "Tabs")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        IconButton(onClick = { webView?.goForward() }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                        }
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { viewModel.toggleDevConsole() }) {
                            Icon(Icons.Default.BugReport, contentDescription = "Dev Console")
                        }
                        IconButton(onClick = {
                            isEditingUrl = !isEditingUrl
                        }) {
                            Icon(
                                if (isEditingUrl) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isEditingUrl) "Close" else "Search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                AxProgressBar(
                    progress = uiState.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        settings.userAgentString = settings.userAgentString.replace(
                            "Mobile",
                            "Desktop"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                request?.let {
                                    val netReq = NetworkRequest(
                                        url = it.url.toString(),
                                        method = it.method ?: "GET",
                                        requestHeaders = it.requestHeaders ?: emptyMap()
                                    )
                                    NetworkInterceptor.onRequest(netReq)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                url?.let { viewModel.updateUrl(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                viewModel.updateProgress(100)
                                view?.title?.let { viewModel.updateTitle(it) }

                                val js = view?.context?.assets?.open("js/media_scanner.js")?.bufferedReader()?.readText()
                                js?.let {
                                    view.evaluateJavascript(it) { result ->
                                        if (!result.isNullOrEmpty() && result != "null") {
                                            try {
                                                val cleaned = result.removeSurrounding("\"")
                                                    .replace("\\\"", "\"")
                                                    .replace("\\n", "\n")
                                                    .replace("\\u003C", "<")
                                                    .replace("\\/", "/")
                                                val mediaItems = parseMediaJson(cleaned)
                                                if (mediaItems.isNotEmpty()) {
                                                    detectedMedia = mediaItems
                                                    viewModel.updateDetectedMediaCount(mediaItems.size)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                viewModel.updateTitle("Error")
                            }
                        }

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.updateProgress(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { viewModel.updateTitle(it) }
                            }
                        }

                        webView = this
                        loadUrl(uiState.url)
                    }
                },
                update = { webView ->
                    if (uiState.url.isNotEmpty() && uiState.url != lastNavigatedUrl) {
                        lastNavigatedUrl = uiState.url
                        webView.loadUrl(uiState.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Tab Switcher Overlay
            AnimatedVisibility(
                visible = uiState.showTabSwitcher,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                TabSwitcherOverlay(
                    tabs = uiState.tabs,
                    activeTabId = uiState.activeTab?.id,
                    onTabClick = { viewModel.setActiveTab(it) },
                    onCloseTab = { viewModel.closeTab(it) },
                    onNewTab = { viewModel.createNewTab() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Floating Buttons (only when tab switcher is hidden)
            if (!uiState.showTabSwitcher) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Floating Download Button - appears when media is detected
                    AnimatedVisibility(
                        visible = uiState.detectedMediaCount > 0,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { showMediaSheet = true },
                            containerColor = Primary,
                            contentColor = Color.White,
                            icon = { Icon(Icons.Default.Download, contentDescription = null) },
                            text = {
                                Text(
                                    text = if (uiState.detectedMediaCount == 1) "1 media" else "${uiState.detectedMediaCount} media"
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // New Tab FAB
                    FloatingActionButton(
                        onClick = { viewModel.createNewTab() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Dev Console Panel
        DevConsolePanel(
            isVisible = uiState.devConsoleVisible,
            currentPageUrl = uiState.displayUrl,
            currentPageHtml = uiState.pageHtml,
            onDismiss = { viewModel.toggleDevConsole() }
        )

        // Media Bottom Sheet
        if (showMediaSheet && detectedMedia.isNotEmpty()) {
            MediaBottomSheet(
                detectedUrls = detectedMedia,
                onDownloadDirect = { url, filename ->
                    viewModel.updateDetectedMediaCount(0)
                    showMediaSheet = false
                },
                onDownloadWithYtDlp = { url ->
                    viewModel.updateDetectedMediaCount(0)
                    showMediaSheet = false
                },
                onDismiss = { showMediaSheet = false }
            )
        }
    }
}

private fun parseMediaJson(json: String): List<DetectedMediaUi> {
    val items = mutableListOf<DetectedMediaUi>()
    try {
        val cleaned = json.trim()
        if (!cleaned.startsWith("[")) return emptyList()

        var i = 0
        while (i < cleaned.length) {
            val urlStart = cleaned.indexOf("\"url\"", i)
            if (urlStart == -1) break
            val urlColon = cleaned.indexOf(":", urlStart)
            val urlQuote1 = cleaned.indexOf("\"", urlColon + 1)
            val urlQuote2 = cleaned.indexOf("\"", urlQuote1 + 1)
            val url = cleaned.substring(urlQuote1 + 1, urlQuote2)

            val typeStart = cleaned.indexOf("\"type\"", urlQuote2)
            val typeColon = cleaned.indexOf(":", typeStart)
            val typeQuote1 = cleaned.indexOf("\"", typeColon + 1)
            val typeQuote2 = cleaned.indexOf("\"", typeQuote1 + 1)
            val type = cleaned.substring(typeQuote1 + 1, typeQuote2)

            val isVideo = type == "video" || type == "source"
            val filename = url.substringAfterLast("/").substringBefore("?").ifBlank { "media_${System.currentTimeMillis()}" }

            items.add(DetectedMediaUi(
                id = "${url}_${System.currentTimeMillis()}",
                url = url,
                filename = filename,
                mimeType = null,
                isVideo = isVideo
            ))

            i = typeQuote2 + 1
        }
    } catch (_: Exception) {}
    return items
}
