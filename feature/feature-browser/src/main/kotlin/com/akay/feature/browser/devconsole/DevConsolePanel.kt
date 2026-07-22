package com.akay.feature.browser.devconsole

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevConsolePanel(
    isVisible: Boolean,
    currentPageUrl: String,
    currentPageHtml: String = "",
    onDismiss: () -> Unit
) {
    val requests by NetworkInterceptor.requests.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRequest by remember { mutableStateOf<NetworkRequest?>(null) }
    val tabs = listOf("Network (${requests.size})", "Console", "Elements", "Info")

    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Dev Console", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (selectedTab == 0) {
                        IconButton(onClick = { NetworkInterceptor.clear() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                            Text(title, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                when (selectedTab) {
                    0 -> NetworkTab(requests = requests, onSelectRequest = { selectedRequest = it })
                    1 -> ConsoleTab()
                    2 -> ElementsTab(html = currentPageHtml)
                    3 -> InfoTab(url = currentPageUrl, requestCount = requests.size)
                }
            }

            selectedRequest?.let { req ->
                RequestDetailSheet(request = req, onDismiss = { selectedRequest = null })
            }
        }
    }
}

@Composable
fun NetworkTab(requests: List<NetworkRequest>, onSelectRequest: (NetworkRequest) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(requests.reversed(), key = { it.id }) { req ->
            NetworkRequestRow(request = req, onClick = { onSelectRequest(req) })
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
fun NetworkRequestRow(request: NetworkRequest, onClick: () -> Unit) {
    val statusColor = when {
        request.isBlocked -> Color(0xFFFF5252)
        request.responseStatus == null -> Color(0xFF9E9E9E)
        request.responseStatus in 200..299 -> Color(0xFF4CAF50)
        request.responseStatus in 300..399 -> Color(0xFF2196F3)
        request.responseStatus in 400..499 -> Color(0xFFFF9800)
        else -> Color(0xFFFF5252)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(statusColor, shape = MaterialTheme.shapes.small))
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.url.removePrefix("https://").removePrefix("http://").let {
                    if (it.length > 60) it.take(57) + "..." else it
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = request.method,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (request.mimeType != null) {
                    Text(request.mimeType, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (request.durationMs > 0) {
                    Text("${request.durationMs}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (request.isBlocked) {
                    Text("BLOCKED", fontSize = 10.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (request.responseStatus != null) {
            Text(
                text = "${request.responseStatus}",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailSheet(request: NetworkRequest, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("Request Detail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            SelectionContainer {
                Text(request.url, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))

            Text("Request Headers", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            request.requestHeaders.forEach { (k, v) ->
                HeaderRow(name = k, value = v)
            }

            if (request.responseHeaders.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Response Headers", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                request.responseHeaders.forEach { (k, v) ->
                    HeaderRow(name = k, value = v)
                }
            }
        }
    }
}

@Composable
fun HeaderRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(140.dp)
        )
        SelectionContainer {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ConsoleTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(0.5f))
            Spacer(Modifier.height(8.dp))
            Text("Eruda console is active on the page.", style = MaterialTheme.typography.bodySmall)
            Text("Tap the Eruda icon on the webpage to open it.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ElementsTab(html: String) {
    if (html.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Page source will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val scroll = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            SelectionContainer {
                Text(html, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun InfoTab(url: String, requestCount: Int) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoRow("Current URL", url)
        InfoRow("Total Requests", requestCount.toString())
        InfoRow("Eruda", "Injected when Dev Mode is ON")
        InfoRow("Network Interception", "Active via WebViewClient")
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp), fontSize = 13.sp)
        SelectionContainer {
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
