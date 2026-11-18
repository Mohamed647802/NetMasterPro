package com.netmaster.pro.presentation.quota

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaScreen(viewModel: QuotaViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("حارس الباقة") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = viewModel::loadTrafficStats) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "تحديث")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { QuotaArcMeter(state) }
            item { DailyStatsCard(state) }
            item { BundleSetupCard(state, viewModel) }
            item { AlertsCard(state, viewModel) }
        }
    }
}

@Composable
private fun QuotaArcMeter(state: QuotaUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.usedPercent,
        animationSpec = tween(1000), label = "quota_arc"
    )
    val arcColor = when {
        state.usedPercent >= 0.95f -> Color(0xFFE53935)
        state.usedPercent >= 0.80f -> Color(0xFFFF9800)
        state.usedPercent >= 0.50f -> Color(0xFFFFEB3B)
        else -> Color(0xFF4CAF50)
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 20.dp.toPx()
                    val startAngle = 135f
                    val sweep = 270f
                    drawArc(Color.LightGray.copy(alpha = 0.3f), startAngle, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(arcColor, startAngle, sweep * animatedProgress, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(state.usedGb), fontWeight = FontWeight.Bold, fontSize = 32.sp)
                    Text("GB مستخدم", style = MaterialTheme.typography.labelMedium)
                    Text("من ${"%.0f".format(state.bundleSizeGb)} GB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${"%.1f".format(state.usedPercent * 100)}% مستخدم — ${"%.1f".format(state.remainingGb)} GB متبقي",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DailyStatsCard(state: QuotaUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إحصائيات يومية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("📅 يوم التجديد", "${state.renewalDay} من الشهر")
                StatItem("⏳ أيام متبقية", "${state.daysUntilRenewal} يوم")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("📊 المسموح يومياً", "${"%.2f".format(state.dailyAllowanceGb)} GB/يوم")
                StatItem("⌛ أيام حتى نفاد الباقة", "${"%.0f".format(state.estimatedDaysLeft)} يوم")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BundleSetupCard(state: QuotaUiState, viewModel: QuotaViewModel) {
    var bundleText by remember(state.bundleSizeGb) { mutableStateOf("%.0f".format(state.bundleSizeGb)) }
    var renewalText by remember(state.renewalDay) { mutableStateOf("${state.renewalDay}") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إعداد الباقة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = bundleText,
                onValueChange = {
                    bundleText = it
                    it.toDoubleOrNull()?.let { gb -> viewModel.setBundleSize(gb) }
                },
                label = { Text("حجم الباقة (GB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = renewalText,
                onValueChange = {
                    renewalText = it
                    it.toIntOrNull()?.let { day -> viewModel.setRenewalDay(day) }
                },
                label = { Text("يوم التجديد (1-28)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun AlertsCard(state: QuotaUiState, viewModel: QuotaViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("عتبات التنبيه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            AlertRow("تنبيه عند 50%", state.alertAt50, viewModel::setAlert50)
            AlertRow("تنبيه عند 80%", state.alertAt80, viewModel::setAlert80)
            AlertRow("تنبيه عند 95%", state.alertAt95, viewModel::setAlert95)
        }
    }
}

@Composable
private fun AlertRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
