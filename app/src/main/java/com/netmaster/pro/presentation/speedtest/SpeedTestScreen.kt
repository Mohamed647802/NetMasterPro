package com.netmaster.pro.presentation.speedtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel = hiltViewModel(),
    gatewayIp: String = "192.168.1.1"
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("اختبار السرعة", "البيانات المحفوظة", "ألعاب")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اختبار السرعة") },
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
                0 -> SpeedTestMainTab(state, onStart = { viewModel.startTest(gatewayIp) })
                1 -> HistoryTab(state)
                2 -> GamePingTab(state)
            }
        }
    }
}

@Composable
private fun SpeedTestMainTab(state: SpeedTestUiState, onStart: () -> Unit) {
    val isRunning = state.phase != SpeedTestPhase.IDLE && state.phase != SpeedTestPhase.DONE

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Animated speedometer meters
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedometerMeter(
                    label = "LAN",
                    value = state.lanDownloadMbps,
                    unit = "Mbps",
                    isActive = state.phase == SpeedTestPhase.LAN_TEST,
                    maxValue = 1000.0,
                    color = MaterialTheme.colorScheme.primary
                )
                SpeedometerMeter(
                    label = "WAN ↓",
                    value = state.wanDownloadMbps,
                    unit = "Mbps",
                    isActive = state.phase == SpeedTestPhase.WAN_DOWNLOAD,
                    maxValue = 500.0,
                    color = Color(0xFF4CAF50)
                )
                SpeedometerMeter(
                    label = "WAN ↑",
                    value = state.wanUploadMbps,
                    unit = "Mbps",
                    isActive = state.phase == SpeedTestPhase.WAN_UPLOAD,
                    maxValue = 200.0,
                    color = Color(0xFFFF9800)
                )
            }
        }
        item {
            Button(
                onClick = onStart,
                enabled = !isRunning,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Speed, null, Modifier.size(32.dp))
                    Text("ابدأ", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isRunning) {
            item {
                val phaseLabel = when (state.phase) {
                    SpeedTestPhase.LAN_TEST -> "قياس سرعة الشبكة المحلية..."
                    SpeedTestPhase.WAN_DOWNLOAD -> "قياس سرعة التنزيل..."
                    SpeedTestPhase.WAN_UPLOAD -> "قياس سرعة الرفع..."
                    SpeedTestPhase.PING -> "قياس زمن الاستجابة..."
                    SpeedTestPhase.GAME_PING -> "اختبار خوادم الألعاب..."
                    else -> ""
                }
                Text(phaseLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.phase == SpeedTestPhase.DONE) {
            item {
                ResultsGrid(state)
            }
            if (state.diagnosisMessage.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null)
                            Spacer(Modifier.width(12.dp))
                            Text(state.diagnosisMessage, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedometerMeter(
    label: String,
    value: Double,
    unit: String,
    isActive: Boolean,
    maxValue: Double,
    color: Color
) {
    val animatedValue by animateFloatAsState(
        targetValue = (value / maxValue).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(500), label = "speedometer"
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = if (isActive) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(90.dp)) {
                val stroke = 10.dp.toPx()
                val startAngle = 135f
                val sweepMax = 270f
                // Background arc
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepMax,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                // Value arc
                drawArc(
                    color = color.copy(alpha = if (isActive) alpha else 1f),
                    startAngle = startAngle,
                    sweepAngle = sweepMax * animatedValue,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (value > 0) "%.1f".format(value) else "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(unit, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ResultsGrid(state: SpeedTestUiState) {
    val gradeColor = when (state.bufferbloatGrade) {
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFF9800)
        "D" -> Color(0xFFFF5722)
        else -> Color(0xFFE53935)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCard("↓ تنزيل", "%.1f".format(state.wanDownloadMbps), "Mbps", Modifier.weight(1f), Color(0xFF4CAF50))
            ResultCard("↑ رفع", "%.1f".format(state.wanUploadMbps), "Mbps", Modifier.weight(1f), Color(0xFFFF9800))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCard("⏱ Ping", "${state.pingMs}", "ms", Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            ResultCard("〰 Jitter", "${state.jitterMs}", "ms", Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            ResultCard("Bufferbloat", state.bufferbloatGrade, "", Modifier.weight(1f), gradeColor)
        }
    }
}

@Composable
private fun ResultCard(label: String, value: String, unit: String, modifier: Modifier, color: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
            if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryTab(state: SpeedTestUiState) {
    if (state.history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد اختبارات سابقة", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("آخر الاختبارات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(state.history.take(10)) { record ->
            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sdf.format(Date(record.timestamp)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Text("↓ ${"%.1f".format(record.downloadMbps)} / ↑ ${"%.1f".format(record.uploadMbps)} Mbps", fontWeight = FontWeight.SemiBold)
                    }
                    Text("${record.pingMs}ms", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GamePingTab(state: SpeedTestUiState) {
    if (state.gamePingResults.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("قم بتشغيل اختبار السرعة لرؤية نتائج الألعاب", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("مصفوفة ping الألعاب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(state.gamePingResults) { result ->
            val statusColor = when (result.status) {
                "ممتاز" -> Color(0xFF4CAF50)
                "جيد" -> Color(0xFF8BC34A)
                "متوسط" -> Color(0xFFFF9800)
                else -> Color(0xFFE53935)
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.name, fontWeight = FontWeight.SemiBold)
                        Text(result.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${result.pingMs}ms", fontWeight = FontWeight.Bold, color = statusColor)
                        Text("فقدان ${result.packetLoss}%", style = MaterialTheme.typography.labelSmall)
                        Text(result.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
            }
        }
    }
}
