package com.netmaster.pro.presentation.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(Unit) { viewModel.collectData() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("تقرير التشخيص") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    if (state.pdfReady) {
                        IconButton(onClick = viewModel::sharePdf) { Icon(Icons.Default.Share, "مشاركة") }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("معاينة التقرير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Text(
                    "تاريخ التوليد: ${sdf.format(Date(state.reportData.generatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Report Preview Card
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReportSection("معلومات الراوتر") {
                            ReportRow("الموديل", state.reportData.routerModel.ifBlank { "غير معروف" })
                            ReportRow("العنوان IP", state.reportData.routerIp)
                        }
                        ReportSection("نتائج اختبار السرعة") {
                            ReportRow("التنزيل", "${"%.1f".format(state.reportData.downloadMbps)} Mbps")
                            ReportRow("الرفع", "${"%.1f".format(state.reportData.uploadMbps)} Mbps")
                            ReportRow("Ping", "${state.reportData.pingMs} ms")
                        }
                        ReportSection("حالة الشبكة") {
                            ReportRow("الأجهزة المتصلة", "${state.reportData.connectedDevices}")
                            ReportRow("قوة الإشارة", "${state.reportData.signalStrength} dBm")
                            val scoreColor = when {
                                state.reportData.securityScore >= 80 -> Color(0xFF4CAF50)
                                state.reportData.securityScore >= 60 -> Color(0xFFFF9800)
                                else -> Color(0xFFE53935)
                            }
                            ReportRow("نقاط الأمان", "${state.reportData.securityScore}/100", valueColor = scoreColor)
                        }
                    }
                }
            }

            // Actions
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::collectData,
                        modifier = Modifier.weight(1f),
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("تحديث البيانات")
                    }
                    Button(
                        onClick = viewModel::generatePdf,
                        modifier = Modifier.weight(1f),
                        enabled = !state.loading
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, null)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("توليد PDF")
                    }
                }
            }

            if (state.pdfReady) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("تم توليد PDF بنجاح", fontWeight = FontWeight.Bold)
                                Text(state.pdfPath.substringAfterLast("/"), style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(onClick = viewModel::sharePdf) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(4.dp))
                                Text("مشاركة")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        content()
    }
}

@Composable
private fun ReportRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}
