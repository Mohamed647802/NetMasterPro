package com.netmaster.pro.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.netmaster.pro.navigation.Screen

data class FeatureItem(
    val emoji: String,
    val label: String,
    val route: String
)

private val featureItems = listOf(
    FeatureItem("📡", "إشارة 4G",        Screen.Signal.route),
    FeatureItem("📱", "الرسائل",          Screen.Sms.route),
    FeatureItem("🖥️", "الأجهزة المتصلة", Screen.Devices.route),
    FeatureItem("📶", "Wi-Fi",            Screen.WifiAdmin.route),
    FeatureItem("👨‍👩‍👧", "الرقابة الأبوية", Screen.Parental.route),
    FeatureItem("📊", "محلل Wi-Fi",       Screen.WifiAnalyzer.route),
    FeatureItem("⚡", "اختبار السرعة",    Screen.SpeedTest.route),
    FeatureItem("🛡️", "الأمان",           Screen.Security.route),
    FeatureItem("🔧", "أدوات الشبكة",    Screen.NetworkTools.route),
    FeatureItem("📋", "تقرير",            Screen.Report.route),
    FeatureItem("⚙️", "الإعدادات",        Screen.Settings.route),
    FeatureItem("🗺️", "خريطة Wi-Fi",     Screen.WifiHeatmap.route),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetMaster Pro", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.scanForRouters() },
                icon    = { Icon(Icons.Default.Search, contentDescription = null) },
                text    = { Text("فحص الشبكة") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Intruder alert banner ──────────────────────────────────────────
            if (state.hasIntruders) {
                IntruderAlertBanner(
                    onClick = { navController.navigate(Screen.Devices.route) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Connection status card ─────────────────────────────────────────
            ConnectionStatusCard(state = state)
            Spacer(Modifier.height(12.dp))

            // ── Quick stats row ───────────────────────────────────────────────
            QuickStatsRow(state = state)
            Spacer(Modifier.height(16.dp))

            // ── Feature grid ──────────────────────────────────────────────────
            Text(
                text  = "الميزات",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns             = GridCells.Fixed(3),
                contentPadding      = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(featureItems) { item ->
                    FeatureCard(item = item, onClick = { navController.navigate(item.route) })
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: HomeUiState) {
    val statusColor = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED    -> Color(0xFF00C853)
        ConnectionStatus.CONNECTING   -> Color(0xFFFFAB00)
        ConnectionStatus.DISCONNECTED -> Color(0xFFD50000)
        ConnectionStatus.NO_ROUTER    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    val statusLabel = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED    -> "متصل"
        ConnectionStatus.CONNECTING   -> "جارٍ الاتصال..."
        ConnectionStatus.DISCONNECTED -> "غير متصل"
        ConnectionStatus.NO_ROUTER    -> "لا يوجد راوتر"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Router,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "الراوتر",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text       = statusLabel,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color     = MaterialTheme.colorScheme.primary
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape),
                    color    = statusColor,
                    content  = {}
                )
            }
        }
    }
}

@Composable
private fun QuickStatsRow(state: HomeUiState) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(
            modifier = Modifier.weight(1f),
            label    = "الأجهزة",
            value    = state.connectedDevices.size.toString()
        )
        StatChip(
            modifier = Modifier.weight(1f),
            label    = "الإشارة",
            value    = state.lteSignal?.rsrp?.let { "${it} dBm" } ?: "—"
        )
        StatChip(
            modifier = Modifier.weight(1f),
            label    = "الحصة",
            value    = if (state.quotaPercent > 0) "${100 - state.quotaPercent}% متبقٍ" else "—"
        )
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier  = modifier,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureCard(item: FeatureItem, onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = item.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text      = item.label,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface,
                maxLines  = 2
            )
        }
    }
}

@Composable
private fun IntruderAlertBanner(onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Default.Warning,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onErrorContainer,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "⚠️ تم اكتشاف جهاز غير معروف على شبكتك! اضغط للمراجعة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
