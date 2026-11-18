package com.netmaster.pro.presentation.troubleshooter

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshooterScreen(viewModel: TroubleshooterViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لماذا الإنترنت فاصل؟") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "تشخيص تلقائي خطوة بخطوة لمشاكل الإنترنت",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::startDiagnosis,
                        modifier = Modifier.weight(1f),
                        enabled = !state.running
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ التشخيص...")
                        } else {
                            Icon(Icons.Default.BugReport, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ابدأ التشخيص")
                        }
                    }
                    if (!state.running && (state.allPassed || state.failedAtStep >= 0)) {
                        OutlinedButton(onClick = viewModel::reset) {
                            Icon(Icons.Default.Refresh, null)
                        }
                    }
                }
            }

            items(state.steps) { step ->
                DiagStepCard(step, isCurrent = step.id == state.currentStepId)
            }

            if (state.allPassed) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎉", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("كل شيء يعمل!", fontWeight = FontWeight.Bold)
                                Text("جميع الاختبارات نجحت — الإنترنت يعمل بشكل صحيح", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagStepCard(step: DiagStep, isCurrent: Boolean) {
    val (bg, iconContent) = when (step.status) {
        StepStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant to @Composable {
            Text("${step.id}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        }
        StepStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer to @Composable {
            val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rot"
            )
            Icon(Icons.Default.HourglassTop, null, Modifier.size(20.dp).rotate(rotation), tint = MaterialTheme.colorScheme.primary)
        }
        StepStatus.PASS -> MaterialTheme.colorScheme.secondaryContainer to @Composable {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = Color(0xFF4CAF50))
        }
        StepStatus.FAIL -> MaterialTheme.colorScheme.errorContainer to @Composable {
            Icon(Icons.Default.Cancel, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = CircleShape,
                color = when (step.status) {
                    StepStatus.PASS -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    StepStatus.FAIL -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    StepStatus.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    iconContent()
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.nameAr, fontWeight = FontWeight.SemiBold)
                Text(step.descriptionAr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                AnimatedVisibility(step.status == StepStatus.FAIL && step.failReasonAr.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text("❌ ${step.failReasonAr}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            val statusText = when (step.status) {
                StepStatus.PENDING -> "⏳"
                StepStatus.RUNNING -> ""
                StepStatus.PASS -> "✅"
                StepStatus.FAIL -> "❌"
            }
            if (statusText.isNotEmpty()) Text(statusText, fontSize = 18.sp)
        }
    }
}
