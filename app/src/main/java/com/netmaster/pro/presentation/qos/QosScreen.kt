package com.netmaster.pro.presentation.qos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QosScreen(
    navController: NavController,
    viewModel: QosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النطاق الترددي وجودة الخدمة") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Profile chips ──────────────────────────────────────────────
            Text("إعدادات مسبقة", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🎮 ألعاب", "📺 بث فيديو", "💾 توفير البيانات").forEachIndexed { i, label ->
                    FilterChip(
                        selected = state.selectedProfile == i,
                        onClick  = { viewModel.applyProfile(i) },
                        label    = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries) { entry ->
                        DeviceQosCard(
                            entry      = entry,
                            onUpload   = { viewModel.setUpload(entry.mac, it) },
                            onDownload = { viewModel.setDownload(entry.mac, it) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick  = { viewModel.applyQos() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !state.isApplying
                ) {
                    if (state.isApplying) CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text("تطبيق الإعدادات")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceQosCard(
    entry: DeviceQosEntry,
    onUpload: (Float) -> Unit,
    onDownload: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text       = entry.name.ifBlank { entry.mac },
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = "${entry.ip} • ${entry.mac}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            Text(
                "⬆ رفع: ${if (entry.uploadKbps == 0f) "بلا حدود" else "${entry.uploadKbps.toInt()} كيلوبت/ث"}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value        = entry.uploadKbps,
                onValueChange = onUpload,
                valueRange   = 0f..10240f,
                steps        = 20,
                modifier     = Modifier.fillMaxWidth()
            )

            Text(
                "⬇ تنزيل: ${if (entry.downloadKbps == 0f) "بلا حدود" else "${entry.downloadKbps.toInt()} كيلوبت/ث"}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value        = entry.downloadKbps,
                onValueChange = onDownload,
                valueRange   = 0f..10240f,
                steps        = 20,
                modifier     = Modifier.fillMaxWidth()
            )
        }
    }
}
