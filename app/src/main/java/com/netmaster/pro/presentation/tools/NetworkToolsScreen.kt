package com.netmaster.pro.presentation.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkToolsScreen(viewModel: NetworkToolsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ping", "Traceroute", "Port Scan", "DNS", "Subnet", "UPnP")
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("أدوات الشبكة") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> PingTab(state, viewModel)
                1 -> TracerouteTab(state, viewModel)
                2 -> PortScanTab(state, viewModel)
                3 -> DnsTab(state, viewModel)
                4 -> SubnetTab(state, viewModel)
                5 -> UpnpTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun PingTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    val focusManager = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.pingHost,
                    onValueChange = viewModel::updatePingHost,
                    label = { Text("الهدف (IP أو نطاق)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                OutlinedTextField(
                    value = "${state.pingCount}",
                    onValueChange = { it.toIntOrNull()?.let { c -> viewModel.updatePingCount(c.coerceIn(1, 50)) } },
                    label = { Text("عدد") },
                    modifier = Modifier.width(70.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::startPing, enabled = !state.pingRunning, modifier = Modifier.fillMaxWidth()) {
                if (state.pingRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.NetworkPing, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.pingRunning) "جارٍ..." else "Ping")
            }
        }
        if (state.pingStats.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(state.pingStats, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        item { Text("النتائج:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
        items(state.pingResults) { r ->
            val color = if (r.timedOut) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("seq=${r.seq}", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(if (r.timedOut) "انتهت المهلة" else "ttl=${r.ttl}  time=${r.timeMs}ms", color = color, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TracerouteTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    val focusManager = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = state.tracerouteHost,
                onValueChange = viewModel::updateTracerouteHost,
                label = { Text("الهدف") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::startTraceroute, enabled = !state.tracerouteRunning, modifier = Modifier.fillMaxWidth()) {
                if (state.tracerouteRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.Route, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.tracerouteRunning) "جارٍ..." else "Traceroute")
            }
        }
        items(state.tracerouteHops) { hop ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${hop.hop}", Modifier.width(24.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hop.timedOut) "* * *" else hop.ip,
                    Modifier.weight(1f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (!hop.timedOut) Text("${hop.latencyMs}ms", fontSize = 12.sp, color = Color(0xFF4CAF50))
            }
        }
    }
}

@Composable
private fun PortScanTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    val focusManager = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = state.portScanHost,
                onValueChange = viewModel::updatePortScanHost,
                label = { Text("IP الهدف") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::startPortScan, enabled = !state.portScanRunning, modifier = Modifier.fillMaxWidth()) {
                if (state.portScanRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.Scanner, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.portScanRunning) "جارٍ المسح..." else "فحص المنافذ")
            }
        }
        if (state.portResults.isNotEmpty()) {
            item {
                Text("المنافذ الشائعة:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                // Grid of ports
                val chunked = state.portResults.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunked.forEach { rowPorts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowPorts.forEach { port ->
                                val color = if (port.open) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant
                                val textColor = if (port.open) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${port.port}", fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
                                        Text(port.service, color = textColor.copy(alpha = 0.8f), fontSize = 10.sp)
                                        Text(if (port.open) "مفتوح" else "مغلق", color = textColor, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (rowPorts.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    val focusManager = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = state.dnsHost,
                onValueChange = viewModel::updateDnsHost,
                label = { Text("النطاق") },
                placeholder = { Text("google.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.startDnsLookup(); focusManager.clearFocus() })
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::startDnsLookup, enabled = !state.dnsRunning, modifier = Modifier.fillMaxWidth()) {
                if (state.dnsRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.Dns, null)
                Spacer(Modifier.width(8.dp))
                Text("استعلام DNS")
            }
        }
        items(state.dnsResults) { rec ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(rec.type, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(rec.value, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SubnetTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("حاسبة الشبكة الفرعية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.subnetIp,
                onValueChange = viewModel::updateSubnetIp,
                label = { Text("عنوان IP") },
                placeholder = { Text("192.168.1.0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.subnetMask,
                onValueChange = viewModel::updateSubnetMask,
                label = { Text("قناع الشبكة") },
                placeholder = { Text("255.255.255.0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::calculateSubnet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Calculate, null)
                Spacer(Modifier.width(8.dp))
                Text("احسب")
            }
        }
        state.subnetInfo?.let { info ->
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubnetRow("الشبكة", "${info.network}/${info.cidr}")
                        SubnetRow("قناع الشبكة", info.subnetMask)
                        SubnetRow("البث", info.broadcast)
                        SubnetRow("أول عنوان", info.firstHost)
                        SubnetRow("آخر عنوان", info.lastHost)
                        SubnetRow("عدد المضيفين", "${info.hostCount}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun UpnpTab(state: NetworkToolsUiState, viewModel: NetworkToolsViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("UPnP Port Mappings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("قائمة إعادة توجيه المنافذ عبر UPnP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (state.upnpMappings.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد تعيينات UPnP — الوظيفة قيد التطوير", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(state.upnpMappings) { mapping ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(mapping.description, fontWeight = FontWeight.SemiBold)
                        Text("${mapping.protocol} :${mapping.externalPort} → ${mapping.internalIp}:${mapping.internalPort}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
