package com.netmaster.pro.presentation.parental

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalScreen(
    viewModel: ParentalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الأجهزة", "DNS", "الحجب", "النوم")

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("الرقابة الأبوية") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> DevicesTab(state, viewModel)
                1 -> DnsTab(state, viewModel)
                2 -> BlocklistTab(state, viewModel)
                3 -> BedtimeTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun DevicesTab(state: ParentalUiState, viewModel: ParentalViewModel) {
    var showDurationDialog by remember { mutableStateOf<String?>(null) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الأجهزة المتصلة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        items(state.devices, key = { it.mac }) { device ->
            DeviceCard(
                device = device,
                onPause = { showDurationDialog = device.mac },
                onResume = { viewModel.resumeDevice(device.mac) }
            )
        }
    }

    showDurationDialog?.let { mac ->
        DurationPickerDialog(
            onDismiss = { showDurationDialog = null },
            onConfirm = { duration, custom ->
                viewModel.pauseDevice(mac, duration, custom)
                showDurationDialog = null
            }
        )
    }
}

@Composable
private fun DeviceCard(
    device: PausedDevice,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isPaused)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.isPaused) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Text(device.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (device.isPaused) {
                    Text(
                        "⏸ إنترنت موقف",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (device.isPaused) {
                TextButton(onClick = onResume) { Text("استئناف") }
            } else {
                FilledTonalButton(onClick = onPause) {
                    Icon(Icons.Default.PauseCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إيقاف")
                }
            }
        }
    }
}

@Composable
private fun DurationPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (PauseDuration, Int) -> Unit
) {
    var selected by remember { mutableStateOf(PauseDuration.HOUR_1) }
    var customMinutes by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مدة الإيقاف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PauseDuration.entries.forEach { dur ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = dur }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = selected == dur, onClick = { selected = dur })
                        Spacer(Modifier.width(8.dp))
                        Text(dur.label)
                    }
                }
                if (selected == PauseDuration.CUSTOM) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("الدقائق") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected, customMinutes.toIntOrNull() ?: 60) }) {
                Text("تطبيق")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun DnsTab(state: ParentalUiState, viewModel: ParentalViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("DNS Shield", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        items(viewModel.dnsPresets) { preset ->
            DnsPresetCard(
                preset = preset,
                isSelected = state.selectedDnsPreset == preset.name,
                onClick = { viewModel.selectDnsPreset(preset.name) }
            )
        }
        item {
            AnimatedVisibility(state.selectedDnsPreset == "Custom") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.customDnsPrimary,
                        onValueChange = viewModel::updateCustomDnsPrimary,
                        label = { Text("DNS الأساسي") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = state.customDnsSecondary,
                        onValueChange = viewModel::updateCustomDnsSecondary,
                        label = { Text("DNS الاحتياطي") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::applyDns, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("تطبيق DNS")
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("DNS Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::runDnsBenchmark,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.benchmarkRunning
            ) {
                if (state.benchmarkRunning) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("جارٍ الاختبار...")
                } else {
                    Icon(Icons.Default.Speed, null)
                    Spacer(Modifier.width(8.dp))
                    Text("اختبار جميع DNS")
                }
            }
        }
        if (state.benchmarkResults.isNotEmpty()) {
            item {
                Text("النتائج (أسرع → أبطأ):", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
            }
            items(state.benchmarkResults) { result ->
                BenchmarkResultRow(result, isFirst = state.benchmarkResults.firstOrNull { !it.failed } == result)
            }
            item {
                val fastest = state.benchmarkResults.firstOrNull { !it.failed }
                if (fastest != null) {
                    FilledTonalButton(onClick = viewModel::applyFastestDns, modifier = Modifier.fillMaxWidth()) {
                        Text("تطبيق الأسرع: ${fastest.provider} (${fastest.latencyMs}ms)")
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsPresetCard(preset: DnsPreset, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name, fontWeight = FontWeight.Bold)
                Text(preset.descriptionAr, style = MaterialTheme.typography.bodySmall)
                if (preset.primary.isNotEmpty()) {
                    Text(
                        "${preset.primary} / ${preset.secondary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BenchmarkResultRow(result: DnsBenchmark.DnsResult, isFirst: Boolean) {
    val latencyColor = when {
        result.failed -> MaterialTheme.colorScheme.error
        result.latencyMs < 50 -> Color(0xFF4CAF50)
        result.latencyMs < 150 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFirst) Text("🏆 ", fontSize = 16.sp)
        else Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(result.provider, fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal)
            Text(result.primary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            if (result.failed) "فشل" else "${result.latencyMs}ms",
            color = latencyColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BlocklistTab(state: ParentalUiState, viewModel: ParentalViewModel) {
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("قائمة الحجب", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.newBlockedDomain,
                    onValueChange = viewModel::updateNewBlockedDomain,
                    label = { Text("نطاق للحجب") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.addBlockedDomain()
                        focusManager.clearFocus()
                    }),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    viewModel.addBlockedDomain()
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Default.Add, "إضافة")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.blockedDomains.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد نطاقات محجوبة", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(state.blockedDomains, key = { it }) { domain ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(domain, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeBlockedDomain(domain) }) {
                            Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BedtimeTab(state: ParentalUiState, viewModel: ParentalViewModel) {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("جدول وقت النوم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "يتم إيقاف الإنترنت عن جميع الأجهزة في الوقت المحدد تلقائياً",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("تفعيل الجدولة", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(
                    checked = state.bedtimeEnabled,
                    onCheckedChange = viewModel::setBedtimeEnabled
                )
            }
        }
        item {
            AnimatedVisibility(state.bedtimeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("وقت الإيقاف", fontWeight = FontWeight.SemiBold)
                            Text(state.bedtimeStart.format(fmt), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            TimeSlider(
                                time = state.bedtimeStart,
                                onTimeChange = viewModel::setBedtimeStart
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("وقت الاستئناف", fontWeight = FontWeight.SemiBold)
                            Text(state.bedtimeEnd.format(fmt), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            TimeSlider(
                                time = state.bedtimeEnd,
                                onTimeChange = viewModel::setBedtimeEnd
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "الإنترنت سيتوقف من ${state.bedtimeStart.format(fmt)} حتى ${state.bedtimeEnd.format(fmt)} يومياً",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlider(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    var hour by remember(time) { mutableIntStateOf(time.hour) }
    var minute by remember(time) { mutableIntStateOf(time.minute) }

    Column {
        Text("الساعة: $hour", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = hour.toFloat(),
            onValueChange = {
                hour = it.toInt()
                onTimeChange(LocalTime.of(hour, minute))
            },
            valueRange = 0f..23f,
            steps = 22
        )
        Text("الدقائق: ${minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = minute.toFloat(),
            onValueChange = {
                minute = it.toInt()
                onTimeChange(LocalTime.of(hour, minute))
            },
            valueRange = 0f..59f,
            steps = 58
        )
    }
}
