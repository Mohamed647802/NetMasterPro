package com.netmaster.pro.presentation.dsl

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DslAnalyzerScreen(viewModel: DslAnalyzerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("محلل DSL/VDSL") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "تحديث")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading && state.stats.lineMode.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.lastUpdated > 0) {
                item {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(
                        "آخر تحديث: ${sdf.format(Date(state.lastUpdated))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            item { DslStatsCard(state.stats) }

            if (state.adviceCards.isNotEmpty()) {
                item { Text("التشخيص والنصائح", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.adviceCards) { advice -> DslAdviceCard(advice) }
            }

            if (state.stats.lineMode.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SignalCellularOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("لم يتم الاتصال بالراوتر أو بيانات DSL غير متاحة", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DslStatsCard(stats: DslStats) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("بيانات خط DSL", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (stats.lineMode.isNotEmpty()) {
                Text("النوع: ${stats.lineMode}", fontWeight = FontWeight.SemiBold)
            }

            DslMetricRow("SNR هبوط", stats.snrDownDb, "dB", minGood = 6.0, higherIsBetter = true)
            DslMetricRow("SNR صعود", stats.snrUpDb, "dB", minGood = 6.0, higherIsBetter = true)
            DslMetricRow("تخفيف الخط ↓", stats.attenuationDownDb, "dB", minGood = 40.0, higherIsBetter = false)
            DslMetricRow("تخفيف الخط ↑", stats.attenuationUpDb, "dB", minGood = 40.0, higherIsBetter = false)

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("السرعة النظرية ↓", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.attainableDownKbps / 1000} Mbps", fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("السرعة النظرية ↑", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.attainableUpKbps / 1000} Mbps", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("سرعة المزامنة ↓", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.syncDownKbps / 1000} Mbps", fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("سرعة المزامنة ↑", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.syncUpKbps / 1000} Mbps", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9800))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("أخطاء CRC ↓", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.crcErrorsDown}", fontWeight = FontWeight.SemiBold, color = if (stats.crcErrorsDown > 0) Color(0xFFE53935) else Color(0xFF4CAF50))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("أخطاء CRC ↑", style = MaterialTheme.typography.labelSmall)
                    Text("${stats.crcErrorsUp}", fontWeight = FontWeight.SemiBold, color = if (stats.crcErrorsUp > 0) Color(0xFFE53935) else Color(0xFF4CAF50))
                }
            }
        }
    }
}

@Composable
private fun DslMetricRow(label: String, value: Double, unit: String, minGood: Double, higherIsBetter: Boolean) {
    val color = when {
        higherIsBetter && value >= minGood -> Color(0xFF4CAF50)
        higherIsBetter && value >= minGood * 0.5 -> Color(0xFFFF9800)
        !higherIsBetter && value <= minGood -> Color(0xFF4CAF50)
        !higherIsBetter && value <= minGood * 1.5 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(6.dp))
            Text("%.1f $unit".format(value), fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DslAdviceCard(advice: DslAdvice) {
    val (bg, iconTint) = when (advice.severity) {
        Severity.OK -> MaterialTheme.colorScheme.secondaryContainer to Color(0xFF4CAF50)
        Severity.WARNING -> Color(0xFFFFF3E0) to Color(0xFFFF9800)
        Severity.CRITICAL -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(advice.icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(advice.messageAr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private val Int.sp @androidx.compose.runtime.Composable get() = androidx.compose.ui.unit.sp
