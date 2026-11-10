package com.akay.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akay.core.domain.model.FingerprintProfile
import com.akay.core.domain.model.ProxyListParser
import com.akay.core.domain.model.ProxyParseOutcome
import com.akay.core.domain.model.ProxyRotation
import com.akay.core.domain.model.ProxyRotationMode
import com.akay.core.domain.model.ProxyServer
import com.akay.core.domain.model.ProxyType
import com.akay.core.ui.theme.Primary
import com.akay.feature.settings.viewmodel.ProxySettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    onBack: () -> Unit,
    viewModel: ProxySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val warning by viewModel.warning.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val activeProxy by viewModel.activeProxy.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val importReport by viewModel.importReport.collectAsState()
    var editingProxy by remember { mutableStateOf<ProxyServer?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var newBypassRule by remember { mutableStateOf("") }
    val usableCount = ProxyRotation.eligible(uiState.proxies).size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proxy") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingProxy = null; showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add proxy") },
                containerColor = Primary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ProxyStatusCard(
                    enabled = uiState.enabled,
                    hasAnyProxy = uiState.proxies.isNotEmpty(),
                    usableCount = usableCount,
                    isConnected = isConnected,
                    activeProxy = activeProxy,
                    onToggle = { viewModel.setProxyEnabled(it) }
                )
            }
            if (!connectionError.isNullOrBlank()) {
                item {
                    StatusNote(
                        text = connectionError!!,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            if (!warning.isNullOrBlank()) {
                item {
                    StatusNote(
                        text = warning!!,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            val test = testState
            if (test != null) {
                item {
                    StatusNote(
                        text = test.message,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val report = importReport
            if (report != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(report.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            report.errors.take(8).forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (report.errors.size > 8) {
                                Text(
                                    "${report.errors.size - 8} more lines were skipped.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.dismissImportReport() }) { Text("Dismiss") }
                        }
                    }
                }
            }

            if (uiState.proxies.isEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No proxies yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Add one, or import a list. HTTP, HTTPS, SOCKS4, and SOCKS5 are supported. IPv6 hosts need brackets, like [2001:db8::1]:1080.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { showImportDialog = true }) { Text("Import list") }
                        }
                    }
                }
            }

            items(uiState.proxies, key = { it.id }) { proxy ->
                ProxyCard(
                    proxy = proxy,
                    isActive = activeProxy?.id == proxy.id && uiState.enabled,
                    testInProgress = testState?.proxyId == proxy.id && testState?.inProgress == true,
                    onToggle = { viewModel.toggleProxyEnabled(proxy.id, it) },
                    onEdit = { editingProxy = proxy; showAddDialog = true },
                    onDelete = { viewModel.deleteProxy(proxy.id) },
                    onTest = { viewModel.testProxy(proxy) }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Rotation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.proxies.isNotEmpty()) {
                        TextButton(onClick = { showImportDialog = true }) { Text("Import") }
                    }
                    FilledTonalButton(
                        onClick = { viewModel.rotateNow() },
                        enabled = uiState.enabled && usableCount > 1
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rotate now")
                    }
                }
                Column(Modifier.selectableGroup()) {
                    RotationOption(
                        title = "Manual",
                        subtitle = "Stay on the current proxy until you tap Rotate now",
                        selected = uiState.rotationMode == ProxyRotationMode.MANUAL.name,
                        onSelect = { viewModel.setRotationMode(ProxyRotationMode.MANUAL) }
                    )
                    RotationOption(
                        title = "Every navigation",
                        subtitle = "The next proxy is installed before a new page loads",
                        selected = uiState.rotationMode == ProxyRotationMode.PER_NAVIGATION.name,
                        onSelect = { viewModel.setRotationMode(ProxyRotationMode.PER_NAVIGATION) }
                    )
                    RotationOption(
                        title = "Timed",
                        subtitle = "Rotate every ${uiState.rotationIntervalMinutes} min, after the current page finishes loading",
                        selected = uiState.rotationMode == ProxyRotationMode.TIMED.name,
                        onSelect = { viewModel.setRotationMode(ProxyRotationMode.TIMED) }
                    )
                }
                if (uiState.rotationMode == ProxyRotationMode.TIMED.name) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                        listOf(5, 10, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = uiState.rotationIntervalMinutes == minutes,
                                onClick = { viewModel.setRotationInterval(minutes) },
                                label = { Text("${minutes}m") },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Bypass list",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "These hosts skip the proxy. <local> keeps private addresses on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newBypassRule,
                        onValueChange = { newBypassRule = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. *.bank.com") },
                        singleLine = true,
                        label = { Text("Host pattern") }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.addBypassRule(newBypassRule.trim())
                            newBypassRule = ""
                        },
                        enabled = newBypassRule.isNotBlank()
                    ) { Icon(Icons.Default.Add, contentDescription = "Add bypass rule") }
                }
                if (uiState.bypassRules.isEmpty()) {
                    Text(
                        "No bypass rules. Every request will try the proxy, including local addresses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.bypassRules.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(rule, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeBypassRule(rule) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove $rule")
                        }
                    }
                }
                Spacer(Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        ProxyEditDialog(
            existing = editingProxy,
            onDismiss = { showAddDialog = false },
            onSave = { id, label, type, host, port, username, password, enabled, timezone, locale, remoteDns ->
                viewModel.saveProxy(id, label, type, host, port, username, password, enabled, timezone, locale, remoteDns)
                showAddDialog = false
            }
        )
    }
    if (showImportDialog) {
        ImportProxiesDialog(
            onDismiss = { showImportDialog = false },
            onImport = {
                viewModel.importProxies(it)
                showImportDialog = false
            }
        )
    }
}

@Composable
private fun StatusNote(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container)
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = content)
    }
}

@Composable
private fun ProxyStatusCard(
    enabled: Boolean,
    hasAnyProxy: Boolean,
    usableCount: Int,
    isConnected: Boolean,
    activeProxy: ProxyServer?,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                tint = if (enabled && isConnected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Use proxy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    statusLine(enabled, usableCount, isConnected, activeProxy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = hasAnyProxy || !enabled,
                modifier = Modifier.semantics { contentDescription = "Use proxy" }
            )
        }
    }
}

private fun statusLine(enabled: Boolean, usableCount: Int, isConnected: Boolean, active: ProxyServer?): String {
    if (!enabled) return "Off. Pages and downloads use this device's network."
    if (usableCount == 0) return "On, but no proxy has a valid host and port."
    if (isConnected && active != null) return "Applied · ${active.label} · ${active.host}:${active.port}"
    return "On. Waiting for WebView to apply a proxy."
}

@Composable
private fun ProxyCard(
    proxy: ProxyServer,
    isActive: Boolean,
    testInProgress: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(alpha = 0.15f)) {
                            Text(
                                proxy.type.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            proxy.label.ifBlank { proxy.host },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        buildString {
                            append("${proxy.host}:${proxy.port}")
                            if (!proxy.username.isNullOrBlank()) append(" · login saved")
                            if (proxy.remoteDns) append(" · remote DNS")
                            proxy.timezone?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (proxy.usesSocks && proxy.requiresAuth) {
                        Text(
                            "WebView can't send this SOCKS login. Downloads can.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = proxy.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = "Enable ${proxy.label}" }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTest, enabled = !testInProgress) {
                    Text(if (testInProgress) "Testing…" else "Test")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${proxy.label}") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${proxy.label}", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RotationOption(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportProxiesDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val preview = remember(text) { ProxyListParser.parse(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import proxies") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "One proxy per line. Blank lines and # comments are ignored. Example: socks5://user:pass@1.2.3.4:1080",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text("Proxy list") },
                    minLines = 4
                )
                Text(
                    if (text.isBlank()) "Nothing entered yet."
                    else "${preview.proxies.size} valid, ${preview.errors.size} skipped.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview.errors.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(enabled = preview.proxies.isNotEmpty(), onClick = { onImport(text) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProxyEditDialog(
    existing: ProxyServer?,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        label: String,
        type: ProxyType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        enabled: Boolean,
        timezone: String?,
        locale: String?,
        remoteDns: Boolean
    ) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: ProxyType.HTTP) }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it > 0 }?.toString() ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var timezone by remember { mutableStateOf(existing?.timezone ?: "") }
    var locale by remember { mutableStateOf(existing?.locale ?: "") }
    var remoteDns by remember { mutableStateOf(existing?.remoteDns ?: false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val hostProblem = hostProblem(host)
    val portNumber = port.toIntOrNull()
    val portProblem = when {
        port.isBlank() -> "Port is required"
        portNumber == null || portNumber !in 1..65535 -> "Use a port from 1 to 65535"
        else -> null
    }
    val zoneProblem = if (timezone.isBlank() || FingerprintProfile.normalizeTimeZone(timezone) != null) null
        else "Use an IANA zone such as America/New_York"
    val localeProblem = if (locale.isBlank() || FingerprintProfile.normalizeLocale(locale) != null) null
        else "Use a tag such as en-US"
    val canSave = hostProblem == null && portProblem == null && zoneProblem == null && localeProblem == null && host.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add proxy" else "Edit proxy") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedTextField(
                        value = type.name, onValueChange = {}, readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose proxy type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { typeMenuExpanded = true }
                    )
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        ProxyType.values().forEach { t ->
                            DropdownMenuItem(text = { Text(t.name) }, onClick = { type = t; typeMenuExpanded = false })
                        }
                    }
                }
                Row {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { value ->
                            val parsed = if (value.contains("://") || value.contains("@")) ProxyListParser.parseEntry(value.trim()) else null
                            if (parsed is ProxyParseOutcome.Ok) {
                                host = parsed.proxy.host
                                port = parsed.proxy.port.toString()
                                type = parsed.proxy.type
                                remoteDns = parsed.proxy.remoteDns
                                parsed.proxy.username?.let { username = it }
                                parsed.proxy.password?.let { password = it }
                            } else {
                                host = value
                            }
                        },
                        label = { Text("Host") },
                        singleLine = true,
                        isError = host.isNotBlank() && hostProblem != null,
                        supportingText = hostProblem?.let { { Text(it) } },
                        modifier = Modifier.weight(2f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        isError = portProblem != null && port.isNotBlank(),
                        supportingText = if (port.isNotBlank()) portProblem?.let { { Text(it) } } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Leave blank to store the proxy without a password.") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timezone, onValueChange = { timezone = it },
                    label = { Text("Timezone (optional)") }, singleLine = true,
                    isError = zoneProblem != null,
                    supportingText = { Text(zoneProblem ?: "Used for the page clock while this proxy is active.") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = locale, onValueChange = { locale = it },
                    label = { Text("Locale (optional)") }, singleLine = true,
                    isError = localeProblem != null,
                    supportingText = { Text(localeProblem ?: "Example: en-US. Leave blank to keep the device language.") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == ProxyType.SOCKS5) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Remote DNS", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Downloads ask the proxy to resolve hostnames. WebView still resolves them on the device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = remoteDns, onCheckedChange = { remoteDns = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        existing?.id,
                        label.ifBlank { host.trim() },
                        type,
                        host.trim(),
                        portNumber ?: 0,
                        username.ifBlank { null },
                        password.ifBlank { null },
                        existing?.enabled ?: true,
                        timezone.trim().ifBlank { null },
                        locale.trim().ifBlank { null },
                        remoteDns && type == ProxyType.SOCKS5
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun hostProblem(host: String): String? {
    val value = host.trim()
    if (value.isEmpty()) return "Host is required"
    if (value.any { it.isWhitespace() }) return "Host can't contain spaces"
    if (value.contains("://") || value.contains("/")) return "Use a hostname or IP, or paste a full proxy URL"
    val inner = value.removePrefix("[").removeSuffix("]")
    val ipv6 = inner.count { it == ':' } >= 2
    if (!ipv6 && value.contains(":")) return "Put the port in the port field"
    return null
}
