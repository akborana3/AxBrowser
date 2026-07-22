package com.akay.feature.browser.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.core.ui.components.AxProgressBar
import com.akay.core.ui.theme.Primary
import com.akay.feature.browser.devconsole.DevConsolePanel
import com.akay.feature.browser.devconsole.NetworkInterceptor
import com.akay.feature.browser.viewmodel.BrowserViewModel
import com.akay.feature.browser.webview.AxWebChromeClient
import com.akay.feature.browser.webview.AxWebViewClient
import com.akay.feature.downloads.ui.DetectedMediaUi
import com.akay.feature.downloads.ui.MediaBottomSheet
import com.akay.feature.downloads.viewmodel.DownloadViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    downloadViewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditingUrl by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastNavigatedUrl by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMediaSheet by remember { mutableStateOf(false) }
    var showPasteLinkDialog by remember { mutableStateOf(false) }
    var pasteUrl by remember { mutableStateOf("") }

    val networkMedia by NetworkInterceptor.detectedMedia.collectAsState()
    val erudaEnabled by viewModel.erudaEnabled.collectAsState(initial = false)
    val mediaCount = uiState.detectedMediaCount + networkMedia.size

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
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (uiState.displayUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = if (uiState.displayUrl.startsWith("https"))
                                            Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = prettifyUrl(uiState.displayUrl),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                        IconButton(onClick = { isEditingUrl = !isEditingUrl }) {
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
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        webViewClient = AxWebViewClient(
                            context = ctx,
                            onPageStarted = { url -> viewModel.updateUrl(url) },
                            onPageFinished = { url, title ->
                                viewModel.updateProgress(100)
                                title?.let { viewModel.updateTitle(it) }
                                if (url.isNotBlank() && url != "about:blank") {
                                    viewModel.recordHistory(url, title ?: url)
                                }
                                if (erudaEnabled) {
                                    val js = runCatching {
                                        ctx.assets.open("js/eruda_init.js").bufferedReader().readText()
                                    }.getOrNull()
                                    js?.let { evaluateJavascript(it, null) }
                                }
                                val scanJs = runCatching {
                                    ctx.assets.open("js/media_scanner.js").bufferedReader().readText()
                                }.getOrNull()
                                scanJs?.let { script ->
                                    evaluateJavascript(script) { result ->
                                        if (!result.isNullOrEmpty() && result != "null") {
                                            parseAndReportDomMedia(result)
                                        }
                                    }
                                }
                            },
                            onError = { viewModel.updateTitle("Error") },
                            onMediaDetected = { url, mime ->
                                NetworkInterceptor.onRequest(
                                    com.akay.feature.browser.devconsole.NetworkRequest(url = url, mimeType = mime)
                                )
                            }
                        )

                        webChromeClient = AxWebChromeClient(
                            onProgressChange = { viewModel.updateProgress(it) },
                            onTitleChange = { viewModel.updateTitle(it) },
                            onUrlChange = { }
                        )

                        webView = this
                        loadUrl(uiState.url)
                    }
                },
                update = { wv ->
                    if (uiState.url.isNotEmpty() && uiState.url != lastNavigatedUrl) {
                        lastNavigatedUrl = uiState.url
                        wv.loadUrl(uiState.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

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

            if (!uiState.showTabSwitcher) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = mediaCount > 0,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { showMediaSheet = true },
                            containerColor = Primary,
                            contentColor = Color.White,
                            icon = { Icon(Icons.Default.Download, contentDescription = null) },
                            text = {
                                Text(text = if (mediaCount == 1) "1 media" else "$mediaCount media")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SmallFloatingActionButton(
                        onClick = { showPasteLinkDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Paste Link", tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FloatingActionButton(
                        onClick = { viewModel.createNewTab() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        DevConsolePanel(
            isVisible = uiState.devConsoleVisible,
            currentPageUrl = uiState.displayUrl,
            currentPageHtml = uiState.pageHtml,
            onDismiss = { viewModel.toggleDevConsole() }
        )

        if (showMediaSheet) {
            val allMedia = networkMedia.map {
                DetectedMediaUi(it.id, it.url, it.filename, it.mimeType, it.isVideo)
            }
            if (allMedia.isNotEmpty()) {
                MediaBottomSheet(
                    detectedUrls = allMedia,
                    onDownloadDirect = { url, filename ->
                        showMediaSheet = false
                        downloadViewModel.enqueue(url = url, filename = filename, useYtDlp = false)
                    },
                    onDownloadWithYtDlp = { url ->
                        showMediaSheet = false
                        val fname = url.substringAfterLast("/").substringBefore("?")
                            .ifBlank { "video_${System.currentTimeMillis()}.mp4" }
                        downloadViewModel.enqueue(url = url, filename = fname, useYtDlp = true)
                    },
                    onDismiss = { showMediaSheet = false }
                )
            }
        }

        if (showPasteLinkDialog) {
            PasteLinkDialog(
                url = pasteUrl,
                onUrlChange = { pasteUrl = it },
                onConfirm = {
                    showPasteLinkDialog = false
                    if (pasteUrl.isNotBlank()) {
                        viewModel.navigateToUrl(pasteUrl)
                        pasteUrl = ""
                    }
                },
                onDismiss = { showPasteLinkDialog = false }
            )
        }
    }
}

@Composable
fun PasteLinkDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Link, contentDescription = null) },
        title = { Text("Paste Link to Download") },
        text = {
            Column {
                Text(
                    "Paste a video or file URL to download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = url.isNotBlank()) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun prettifyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .let { if (it.length > 45) it.take(42) + "..." else it }

private fun parseAndReportDomMedia(json: String) {
    try {
        val cleaned = json.trim().removeSurrounding("\"")
            .replace("\\\"", "\"").replace("\\n", "\n").replace("\\/", "/")
            .replace("\\u003C", "<")
        if (!cleaned.startsWith("[")) return
        var i = 0
        while (i < cleaned.length) {
            val urlStart = cleaned.indexOf("\"url\"", i).takeIf { it >= 0 } ?: break
            val u1 = cleaned.indexOf("\"", cleaned.indexOf(":", urlStart) + 1) + 1
            val u2 = cleaned.indexOf("\"", u1)
            val url = cleaned.substring(u1, u2)
            val typeStart = cleaned.indexOf("\"type\"", u2).takeIf { it >= 0 } ?: break
            val t1 = cleaned.indexOf("\"", cleaned.indexOf(":", typeStart) + 1) + 1
            val t2 = cleaned.indexOf("\"", t1)
            val type = cleaned.substring(t1, t2)
            if (url.startsWith("http")) NetworkInterceptor.addDomMedia(url, type)
            i = t2 + 1
        }
    } catch (_: Exception) {}
}
