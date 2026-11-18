package com.netmaster.pro.presentation.analyzer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAnalyzerScreen(viewModel: WifiAnalyzerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("معلومات", "الشبكات", "القنوات", "خريطة الإشارة")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("محلل Wi-Fi") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    if (state.scanInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            viewModel.refreshCurrentWifiInfo()
                            viewModel.startScan(context)
                        }) {
                            Icon(Icons.Default.Refresh, "مسح")
                        }
                    }
                }
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
                0 -> WifiInfoTab(state)
                1 -> NeighborListTab(state)
                2 -> ChannelChartTab(state, viewModel)
                3 -> HeatmapTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun WifiInfoTab(state: WifiAnalyzerUiState) {
    val info = state.wifiInfo
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("اتصالك الحالي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("📶 SSID", info.ssid.ifBlank { "غير متصل" })
                    InfoRow("🔗 BSSID", info.bssid.ifBlank { "—" })
                    InfoRow("⚡ السرعة", "${info.linkSpeed} Mbps")
                    InfoRow("📡 التردد", "${info.frequency} MHz")
                    InfoRow("🏷️ المعيار", info.standard)
                    InfoRow("📏 عرض القناة", info.channelWidth)
                    InfoRow("🔢 القناة", "${info.channel}")
                    InfoRow("📊 الإشارة", "${info.rssi} dBm (${signalStrengthLabel(info.rssi)})")
                    HorizontalDivider()
                    InfoRow("🏠 IP المحلي", info.localIp)
                    InfoRow("🚪 البوابة", info.gateway)
                    InfoRow("🌐 DNS1", info.dns1)
                    InfoRow("🌐 DNS2", info.dns2)
                }
            }
        }
        item {
            SignalMeter(rssi = info.rssi)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SignalMeter(rssi: Int) {
    val strength = ((rssi + 100).coerceIn(0, 60)).toFloat() / 60f
    val color = when {
        rssi >= -60 -> Color(0xFF4CAF50)
        rssi >= -70 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("قوة الإشارة", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { strength },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = color
            )
            Spacer(Modifier.height(4.dp))
            Text("$rssi dBm — ${signalStrengthLabel(rssi)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NeighborListTab(state: WifiAnalyzerUiState) {
    if (state.neighbors.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.WifiFind, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("اضغط على تحديث لفحص الشبكات المجاورة", color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${state.neighbors.size} شبكة مجاورة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(state.neighbors) { ap ->
            NeighborApCard(ap)
        }
    }
}

@Composable
private fun NeighborApCard(ap: NeighborAp) {
    val barColor = when {
        ap.rssi >= -60 -> Color(0xFF4CAF50)
        ap.rssi >= -70 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
    val strength = ((ap.rssi + 100).coerceIn(0, 60)).toFloat() / 60f

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ap.ssid.ifBlank { "(مخفي)" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("قناة ${ap.channel} · ${ap.frequency} MHz · ${ap.standard}", style = MaterialTheme.typography.labelSmall)
                Text(ap.bssid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(80.dp)) {
                Text("${ap.rssi} dBm", fontWeight = FontWeight.Bold, color = barColor, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { strength },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = barColor
                )
            }
        }
    }
}

@Composable
private fun ChannelChartTab(state: WifiAnalyzerUiState, viewModel: WifiAnalyzerViewModel) {
    var show2GHz by remember { mutableStateOf(true) }
    val neighbors2G = state.neighbors.filter { it.frequency < 3000 }
    val neighbors5G = state.neighbors.filter { it.frequency in 5000..6000 }
    val currentChannel = state.wifiInfo.channel

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                FilterChip(
                    selected = show2GHz,
                    onClick = { show2GHz = true },
                    label = { Text("2.4 GHz") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !show2GHz,
                    onClick = { show2GHz = false },
                    label = { Text("5 GHz") }
                )
            }
        }
        item {
            val activeNeighbors = if (show2GHz) neighbors2G else neighbors5G
            val bestChannel = if (show2GHz) state.bestChannel2G else state.bestChannel5G
            Card(Modifier.fillMaxWidth().height(220.dp)) {
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    if (activeNeighbors.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد شبكات — قم بالمسح أولاً", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        ChannelChart(
                            neighbors = activeNeighbors,
                            currentChannel = currentChannel,
                            is2GHz = show2GHz
                        )
                    }
                }
            }
        }
        item {
            val bestChannel = if (show2GHz) state.bestChannel2G else state.bestChannel5G
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Text("أفضل قناة: $bestChannel", fontWeight = FontWeight.Bold, Modifier.weight(1f))
                    FilledTonalButton(onClick = { viewModel.applyBestChannel(show2GHz) }) {
                        Text("تطبيق")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChart(neighbors: List<NeighborAp>, currentChannel: Int, is2GHz: Boolean) {
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFFF5722), Color(0xFF607D8B)
    )
    val channelRange = if (is2GHz) 1..14 else listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
    val channelList = if (is2GHz) (1..14).toList() else channelRange as List<Int>

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val channelCount = channelList.size
        val colW = w / channelCount

        // Draw channel labels
        for ((i, ch) in channelList.withIndex()) {
            val x = i * colW + colW / 2
            if (ch == currentChannel) {
                drawRect(
                    color = Color(0x330000FF),
                    topLeft = Offset(i * colW, 0f),
                    size = Size(colW, h)
                )
            }
        }

        // Draw AP bell curves
        neighbors.forEachIndexed { idx, ap ->
            val apColor = colors[idx % colors.size]
            val chIdx = channelList.indexOf(ap.channel).let { if (it < 0) return@forEachIndexed else it }
            val apX = chIdx * colW + colW / 2
            val normalizedSignal = ((ap.rssi + 100).coerceIn(0, 60)).toFloat() / 60f
            val peakH = h * normalizedSignal * 0.9f
            val spreadChannels = if (is2GHz) 2.5f else 1.5f
            val spread = spreadChannels * colW

            val path = Path()
            path.moveTo(apX - spread * 2, h)
            for (xOffset in -100..100) {
                val xf = apX + xOffset * (spread * 2 / 100f)
                val t = xOffset / 100f
                val yf = h - peakH * Math.exp((-2 * t * t).toDouble()).toFloat()
                if (xOffset == -100) path.moveTo(xf, yf)
                else path.lineTo(xf, yf)
            }
            path.lineTo(apX + spread * 2, h)
            path.close()
            drawPath(path, color = apColor.copy(alpha = 0.3f))
            drawPath(path, color = apColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        // Draw baseline
        drawLine(Color.Gray, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
    }
}

@Composable
private fun HeatmapTab(state: WifiAnalyzerUiState, viewModel: WifiAnalyzerViewModel) {
    var selectedZone by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("خريطة الإشارة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("اضغط على كل غرفة لتسجيل قوة الإشارة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    for (row in 0..4) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0..4) {
                                val zone = row * 5 + col
                                val signal = state.roomSignals[zone]
                                val bgColor = when {
                                    signal == null -> MaterialTheme.colorScheme.surfaceVariant
                                    signal.rssi >= -60 -> Color(0xFF4CAF50)
                                    signal.rssi >= -70 -> Color(0xFFFF9800)
                                    else -> Color(0xFFE53935)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor)
                                        .clickable {
                                            selectedZone = zone
                                            viewModel.recordRoomSignal(zone)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (signal != null) {
                                        Text(
                                            "${signal.rssi}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text("غ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(Color(0xFF4CAF50), "قوية (> -60)")
                LegendItem(Color(0xFFFF9800), "متوسطة")
                LegendItem(Color(0xFFE53935), "ضعيفة (< -70)")
            }
        }
        if (state.roomSignals.isNotEmpty()) {
            val weakZones = state.roomSignals.values.count { it.rssi < -70 }
            if (weakZones > 0) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "يوجد $weakZones ${if (weakZones == 1) "غرفة" else "غرف"} بإشارة ضعيفة. ينصح بوضع مكرر Wi-Fi في المنتصف.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
