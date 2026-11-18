package com.netmaster.pro.presentation.security

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    viewModel: SecurityAuditViewModel = hiltViewModel(),
    routerIp: String = "192.168.1.1"
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("فحص الأمان") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SecurityScoreMeter(state) }
            item {
                Button(
                    onClick = { viewModel.startAudit(routerIp) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.scanning
                ) {
                    if (state.scanning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ الفحص...")
                    } else {
                        Icon(Icons.Default.Security, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ابدأ فحص الأمان")
                    }
                }
            }
            item {
                Text("نتائج الفحص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.checks) { check ->
                SecurityCheckCard(check)
            }
        }
    }
}

@Composable
private fun SecurityScoreMeter(state: SecurityAuditUiState) {
    val animScore by animateIntAsState(
        targetValue = state.score,
        animationSpec = tween(1000), label = "score"
    )
    val animProgress by animateFloatAsState(
        targetValue = state.score / 100f,
        animationSpec = tween(1000), label = "progress"
    )
    val scoreColor = when {
        state.score >= 80 -> Color(0xFF4CAF50)
        state.score >= 60 -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    drawArc(
                        Color.LightGray.copy(alpha = 0.3f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        scoreColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animProgress,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$animScore", fontWeight = FontWeight.Bold, fontSize = 36.sp, color = scoreColor)
                    Text("/100", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(state.gradeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = scoreColor)
        }
    }
}

@Composable
private fun SecurityCheckCard(check: SecurityCheck) {
    val (bg, icon, iconTint) = when {
        check.isChecking -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Default.HourglassTop, MaterialTheme.colorScheme.primary)
        check.passed == null -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Default.HelpOutline, MaterialTheme.colorScheme.outline)
        check.passed == true -> Triple(MaterialTheme.colorScheme.secondaryContainer, Icons.Default.CheckCircle, Color(0xFF4CAF50))
        else -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Default.Cancel, MaterialTheme.colorScheme.error)
    }
    val severityLabel = when (check.severity) {
        CheckSeverity.CRITICAL -> "حرج"
        CheckSeverity.HIGH -> "عالي"
        CheckSeverity.MEDIUM -> "متوسط"
        CheckSeverity.LOW -> "منخفض"
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (check.isChecking) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(check.nameAr, fontWeight = FontWeight.Bold)
                    Text(check.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                val badgeColor = when (check.severity) {
                    CheckSeverity.CRITICAL -> Color(0xFFE53935)
                    CheckSeverity.HIGH -> Color(0xFFFF5722)
                    CheckSeverity.MEDIUM -> Color(0xFFFF9800)
                    CheckSeverity.LOW -> Color(0xFF8BC34A)
                }
                Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        severityLabel,
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (check.passed == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚡ الإصلاح: ${check.fixInstructionAr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "النقاط المفقودة: ${check.pointsLost}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
