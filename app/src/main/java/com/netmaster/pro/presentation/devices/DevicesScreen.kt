package com.netmaster.pro.presentation.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material.icons.filled.LocalPrintshop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netmaster.pro.R
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.data.model.WifiBand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val allDevices by viewModel.devices.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val deviceHistory by viewModel.deviceHistory.collectAsState()
    val wolStatus by viewModel.wolStatus.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val displayedDevices by remember(allDevices, filterState, searchQuery) {
        derivedStateOf { viewModel.filteredDevices(filterState, searchQuery) }
    }

    val strangerCount by remember(displayedDevices) {
        derivedStateOf { displayedDevices.count { !it.isTrusted } }
    }

    LaunchedEffect(wolStatus) {
        wolStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearWolStatus()
        }
    }

    // Device detail bottom sheet
    if (selectedDevice != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DeviceDetailSheet(
            device = selectedDevice!!,
            history = deviceHistory,
            sheetState = sheetState,
            onDismiss = { viewModel.selectDevice(null) },
            onUpdateDevice = { mac, nick, cat, trusted ->
                viewModel.updateDevice(mac, nick, cat, trusted)
                viewModel.selectDevice(null)
            },
            onBlock = { mac ->
                viewModel.blockDevice(mac)
                viewModel.selectDevice(null)
            },
            onUnblock = { mac ->
                viewModel.unblockDevice(mac)
                viewModel.selectDevice(null)
            },
            onWakeOnLan = { mac ->
                viewModel.wakeOnLan(mac, "255.255.255.255")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_screen_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshDevices() }) {
                Icon(Icons.Default.WifiFind, contentDescription = stringResource(R.string.devices_scan))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = scanState == ScanState.SCANNING,
            onRefresh = { viewModel.refreshDevices() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Scan progress bar
                if (scanState == ScanState.SCANNING) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = { scanProgress / 100f }
                    )
                }

                // Stranger alert banner
                if (strangerCount > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeviceUnknown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.devices_stranger_alert, strangerCount),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.devices_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // Filter chips
                val filters = listOf(
                    DeviceFilter.ALL to stringResource(R.string.devices_filter_all),
                    DeviceFilter.TRUSTED to stringResource(R.string.devices_filter_trusted),
                    DeviceFilter.STRANGER to stringResource(R.string.devices_filter_strangers),
                    DeviceFilter.BLOCKED to stringResource(R.string.devices_filter_blocked)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { (filter, label) ->
                        FilterChip(
                            selected = filterState == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(label) }
                        )
                    }
                }

                // Device list
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
                ) {
                    items(displayedDevices, key = { it.mac }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { viewModel.selectDevice(device) },
                            onTrustToggle = {
                                viewModel.updateDevice(
                                    device.mac,
                                    device.nickname,
                                    device.category,
                                    !device.isTrusted
                                )
                            },
                            onBlock = { viewModel.blockDevice(device.mac) }
                        )
                    }

                    if (displayedDevices.isEmpty() && scanState != ScanState.SCANNING) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.devices_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Device Card ────────────────────────────────────────────────────────────

@Composable
fun DeviceCard(
    device: ConnectedDevice,
    onClick: () -> Unit,
    onTrustToggle: () -> Unit,
    onBlock: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isTrusted)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Icon(
                imageVector = device.category.toIcon(),
                contentDescription = device.category.name,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.nickname.ifBlank { device.hostname.ifBlank { device.vendor.ifBlank { device.mac } } },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    // Band badge
                    Badge(
                        containerColor = if (device.band == WifiBand.BAND_5GHZ)
                            MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary
                    ) {
                        Text(device.band.toBandLabel(), fontSize = 9.sp)
                    }
                }
                Text(
                    text = "${device.ip} · ${device.mac}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // RSSI signal bars
                RssiSignalBars(rssi = device.rssi)
            }

            // Trust toggle
            IconButton(onClick = onTrustToggle) {
                Icon(
                    imageVector = if (device.isTrusted) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.devices_trust_toggle),
                    tint = if (device.isTrusted) Color(0xFFFDD835) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Block button
            IconButton(onClick = onBlock) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = stringResource(R.string.devices_block),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun RssiSignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        rssi >= -85 -> 1
        else -> 0
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            val active = i <= bars
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .then(
                        Modifier.then(
                            if (active)
                                Modifier
                            else
                                Modifier
                        )
                    )
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
            )
        }
    }
}

// ─── Device Detail Bottom Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: ConnectedDevice,
    history: List<ConnectedDevice>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onUpdateDevice: (String, String, DeviceCategory, Boolean) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
    onWakeOnLan: (String) -> Unit
) {
    var editNickname by remember(device) { mutableStateOf(device.nickname) }
    var editCategory by remember(device) { mutableStateOf(device.category) }
    var editTrusted by remember(device) { mutableStateOf(device.isTrusted) }
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = device.category.toIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(device.mac, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${device.ip} · ${device.vendor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Divider()

            // Edit nickname
            OutlinedTextField(
                value = editNickname,
                onValueChange = { editNickname = it },
                label = { Text(stringResource(R.string.devices_detail_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category picker
            Text(stringResource(R.string.devices_detail_category), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            val categories = DeviceCategory.entries.chunked(4)
            categories.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cat ->
                        val selected = editCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { editCategory = cat },
                            label = {
                                Icon(cat.toIcon(), contentDescription = cat.name, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }

            // Trust toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.devices_detail_trusted))
                Switch(checked = editTrusted, onCheckedChange = { editTrusted = it })
            }

            // Connection history timeline
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.devices_detail_history), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                history.take(5).forEach { h ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(dateFormat.format(Date(h.lastSeen)), style = MaterialTheme.typography.labelSmall)
                        Text(h.ip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Divider()

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WoL
                Button(
                    onClick = { onWakeOnLan(device.mac) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.devices_wake_on_lan), fontSize = 12.sp)
                }
                // Block / Unblock
                if (device.isTrusted) {
                    TextButton(onClick = { onBlock(device.mac) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.devices_block), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = { onUnblock(device.mac) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.devices_unblock), fontSize = 12.sp)
                    }
                }
            }

            // Save
            Button(
                onClick = { onUpdateDevice(device.mac, editNickname, editCategory, editTrusted) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Helper extensions ───────────────────────────────────────────────────────

fun DeviceCategory.toIcon(): ImageVector = when (this) {
    DeviceCategory.PHONE -> Icons.Default.PhoneAndroid
    DeviceCategory.TABLET -> Icons.Default.Tablet
    DeviceCategory.TV -> Icons.Default.Tv
    DeviceCategory.CONSOLE -> Icons.Default.Gamepad
    DeviceCategory.PC -> Icons.Default.Computer
    DeviceCategory.LAPTOP -> Icons.Default.LaptopMac
    DeviceCategory.IOT -> Icons.Default.LightbulbCircle
    DeviceCategory.CAMERA -> Icons.Default.Videocam
    DeviceCategory.ROUTER -> Icons.Default.Router
    DeviceCategory.PRINTER -> Icons.Default.LocalPrintshop
    DeviceCategory.SPEAKER -> Icons.Default.SpeakerGroup
    DeviceCategory.UNKNOWN -> Icons.Default.DeviceUnknown
}

fun WifiBand.toBandLabel(): String = when (this) {
    WifiBand.BAND_2_4GHZ -> "2.4G"
    WifiBand.BAND_5GHZ -> "5G"
    WifiBand.BAND_6GHZ -> "6G"
    WifiBand.WIRED -> "LAN"
    WifiBand.UNKNOWN -> "?"
}
