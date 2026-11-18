package com.netmaster.pro.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.netmaster.pro.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.showResetDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissResetDialog,
            title    = { Text("إعادة ضبط التطبيق") },
            text     = { Text("سيتم حذف جميع الإعدادات وبيانات الراوتر المحفوظة. هل أنت متأكد؟") },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetApp(); viewModel.dismissResetDialog() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("إعادة ضبط") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissResetDialog) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Language ──────────────────────────────────────────────────
            item {
                SettingsSectionTitle("اللغة / Language")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.language == AppLanguage.ARABIC,
                        onClick  = { viewModel.setLanguage(AppLanguage.ARABIC) },
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("العربية") }
                    SegmentedButton(
                        selected = state.language == AppLanguage.ENGLISH,
                        onClick  = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("English") }
                }
            }

            // ── Theme ─────────────────────────────────────────────────────
            item {
                SettingsSectionTitle("المظهر / Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppTheme.SYSTEM to "نظام",
                        AppTheme.LIGHT  to "فاتح",
                        AppTheme.DARK   to "داكن",
                        AppTheme.NEON_CYBER to "نيون"
                    ).forEach { (theme, label) ->
                        FilterChip(
                            selected = state.theme == theme,
                            onClick  = { viewModel.setTheme(theme) },
                            label    = { Text(label) }
                        )
                    }
                }
            }

            // ── Biometric lock ────────────────────────────────────────────
            item {
                SettingsSectionTitle("الأمان / Security")
                SettingsToggleRow(
                    label   = "قفل البصمة",
                    subtext = "يتطلب المصادقة عند فتح التطبيق",
                    checked = state.biometricEnabled && state.biometricAvailable,
                    enabled = state.biometricAvailable,
                    onToggle = viewModel::toggleBiometric
                )
                if (!state.biometricAvailable) {
                    Text(
                        "البصمة غير متاحة على هذا الجهاز",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }

            // ── Router Credentials ────────────────────────────────────────
            item {
                SettingsSectionTitle("بيانات الراوتر المحفوظة")
                if (state.savedRouterIps.isEmpty()) {
                    Text(
                        "لا توجد راوترات محفوظة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    state.savedRouterIps.forEach { ip ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(ip, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { viewModel.deleteRouter(ip) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // ── Notifications ─────────────────────────────────────────────
            item {
                SettingsSectionTitle("الإشعارات / Notifications")
                SettingsToggleRow("تنبيهات الأجهزة المجهولة", checked = state.intruderAlertsEnabled,
                    onToggle = viewModel::toggleIntruderAlerts)
                SettingsToggleRow("تحذير الحصة 50%", checked = state.quotaAlert50,
                    onToggle = viewModel::toggleQuotaAlert50)
                SettingsToggleRow("تحذير الحصة 80%", checked = state.quotaAlert80,
                    onToggle = viewModel::toggleQuotaAlert80)
                SettingsToggleRow("تحذير الحصة 95%", checked = state.quotaAlert95,
                    onToggle = viewModel::toggleQuotaAlert95)
            }

            // ── Scan Interval ─────────────────────────────────────────────
            item {
                SettingsSectionTitle("فترة الفحص التلقائي")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScanInterval.MIN_15  to "15 دقيقة",
                        ScanInterval.MIN_30  to "30 دقيقة",
                        ScanInterval.HOUR_1  to "ساعة",
                        ScanInterval.OFF     to "إيقاف"
                    ).forEach { (interval, label) ->
                        FilterChip(
                            selected = state.scanInterval == interval,
                            onClick  = { viewModel.setScanInterval(interval) },
                            label    = { Text(label) }
                        )
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────
            item {
                SettingsSectionTitle("حول التطبيق / About")
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("NetMaster Pro", fontWeight = FontWeight.Bold)
                            Text("الإصدار ${state.appVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ── Reset ─────────────────────────────────────────────────────
            item {
                OutlinedButton(
                    onClick  = viewModel::showResetDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("إعادة ضبط التطبيق")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    subtext: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtext.isNotBlank()) {
                Text(subtext, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}
