package com.netmaster.pro.presentation.signal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netmaster.pro.R
import com.netmaster.pro.data.model.LteSignalData
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalScreen(
    viewModel: SignalViewModel = hiltViewModel()
) {
    val signalState by viewModel.signalState.collectAsState()
    val isPolling by viewModel.isPolling.collectAsState()
    val placementAssistMode by viewModel.placementAssistMode.collectAsState()
    val selectedBands by viewModel.selectedBands.collectAsState()
    val networkMode by viewModel.networkMode.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startSignalPolling()
        onDispose { viewModel.stopSignalPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.signal_screen_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    if (isPolling) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { viewModel.refreshOnce() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.togglePlacementAssist() },
                containerColor = if (placementAssistMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = if (placementAssistMode) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
                    contentDescription = stringResource(R.string.signal_placement_assistant)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedContent(targetState = signalState, label = "signal_state") { state ->
                when (state) {
                    is SignalUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is SignalUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    is SignalUiState.NotSupported -> {
                        Card {
                            Text(
                                text = stringResource(R.string.signal_not_supported),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    is SignalUiState.Success -> {
                        SignalSuccessContent(
                            signal = state.signal,
                            advice = state.advice,
                            placementAssistMode = placementAssistMode,
                            networkMode = networkMode,
                            selectedBands = selectedBands,
                            onNetworkModeSelected = { viewModel.setNetworkMode(it) },
                            onBandToggled = { band ->
                                val current = selectedBands.toMutableSet()
                                if (band in current) current.remove(band) else current.add(band)
                                viewModel.setBandSelection(current.toList())
                            },
                            onLockBands = { viewModel.lockBands(selectedBands.toList()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalSuccessContent(
    signal: LteSignalData,
    advice: String,
    placementAssistMode: Boolean,
    networkMode: NetworkMode,
    selectedBands: Set<LteBand>,
    onNetworkModeSelected: (NetworkMode) -> Unit,
    onBandToggled: (LteBand) -> Unit,
    onLockBands: () -> Unit
) {
    // Gauge row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SignalGauge(
            label = "RSRP",
            unit = "dBm",
            value = signal.rsrp.toFloat(),
            min = -140f,
            max = -44f,
            colorZones = listOf(
                GaugeZone(-140f, -100f, Color(0xFFE53935)),
                GaugeZone(-100f, -90f, Color(0xFFFB8C00)),
                GaugeZone(-90f, -80f, Color(0xFFFDD835)),
                GaugeZone(-80f, -44f, Color(0xFF43A047))
            ),
            modifier = Modifier.weight(1f)
        )
        SignalGauge(
            label = "RSRQ",
            unit = "dB",
            value = signal.rsrq,
            min = -20f,
            max = -3f,
            colorZones = listOf(
                GaugeZone(-20f, -15f, Color(0xFFE53935)),
                GaugeZone(-15f, -10f, Color(0xFFFB8C00)),
                GaugeZone(-10f, -6f, Color(0xFFFDD835)),
                GaugeZone(-6f, -3f, Color(0xFF43A047))
            ),
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SignalGauge(
            label = "SINR",
            unit = "dB",
            value = signal.sinr,
            min = -20f,
            max = 30f,
            colorZones = listOf(
                GaugeZone(-20f, 0f, Color(0xFFE53935)),
                GaugeZone(0f, 10f, Color(0xFFFB8C00)),
                GaugeZone(10f, 20f, Color(0xFFFDD835)),
                GaugeZone(20f, 30f, Color(0xFF43A047))
            ),
            modifier = Modifier.weight(1f)
        )
        SignalGauge(
            label = "RSSI",
            unit = "dBm",
            value = signal.rssi.toFloat(),
            min = -113f,
            max = -51f,
            colorZones = listOf(
                GaugeZone(-113f, -90f, Color(0xFFE53935)),
                GaugeZone(-90f, -75f, Color(0xFFFB8C00)),
                GaugeZone(-75f, -65f, Color(0xFFFDD835)),
                GaugeZone(-65f, -51f, Color(0xFF43A047))
            ),
            modifier = Modifier.weight(1f)
        )
    }

    // Info Grid
    SignalInfoGrid(signal = signal)

    // Advice card
    AdviceCard(advice = advice)

    // Placement assist banner
    if (placementAssistMode) {
        PlacementAssistBanner()
    }

    // Network Mode Selector
    NetworkModeSelector(
        currentMode = networkMode,
        onModeSelected = onNetworkModeSelected
    )

    // LTE Band Selector
    LteBandSelector(
        selectedBands = selectedBands,
        onBandToggled = onBandToggled,
        onLockBands = onLockBands
    )
}

// ─── Gauge ──────────────────────────────────────────────────────────────────

data class GaugeZone(val from: Float, val to: Float, val color: Color)

@Composable
fun SignalGauge(
    label: String,
    unit: String,
    value: Float,
    min: Float,
    max: Float,
    colorZones: List<GaugeZone>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 600),
        label = "gauge_$label"
    )

    val sweepAngle = 240f // arc spans 240 degrees
    val startAngle = 150f

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = strokeWidth.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                // Background arc
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )

                // Color zones
                colorZones.forEach { zone ->
                    val zoneStart = startAngle + ((zone.from - min) / (max - min)) * sweepAngle
                    val zoneSweep = ((zone.to - zone.from) / (max - min)) * sweepAngle
                    drawArc(
                        color = zone.color.copy(alpha = 0.4f),
                        startAngle = zoneStart,
                        sweepAngle = zoneSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokePx * 0.6f, cap = StrokeCap.Butt)
                    )
                }

                // Value arc
                val valueSweep = ((animatedValue - min) / (max - min)).coerceIn(0f, 1f) * sweepAngle
                val valueColor = colorZones.lastOrNull { zone ->
                    animatedValue >= zone.from
                }?.color ?: colorZones.firstOrNull()?.color ?: Color.Gray
                drawArc(
                    color = valueColor,
                    startAngle = startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )

                // Needle dot
                val needleAngleRad = Math.toRadians((startAngle + valueSweep).toDouble())
                val radius = diameter / 2f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val needleX = centerX + radius * cos(needleAngleRad).toFloat()
                val needleY = centerY + radius * sin(needleAngleRad).toFloat()
                drawCircle(color = valueColor, radius = strokePx * 0.8f, center = Offset(needleX, needleY))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f".format(animatedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Info Grid ──────────────────────────────────────────────────────────────

@Composable
private fun SignalInfoGrid(signal: LteSignalData) {
    val items = listOf(
        stringResource(R.string.signal_pci) to signal.pci.toString(),
        stringResource(R.string.signal_cell_id) to signal.cellId.toString(),
        stringResource(R.string.signal_enb_id) to signal.eNbId.toString(),
        stringResource(R.string.signal_earfcn) to signal.earfcn.toString(),
        stringResource(R.string.signal_band) to "B${signal.band}",
        stringResource(R.string.signal_ca_status) to if (signal.caActive)
            stringResource(R.string.signal_ca_active, signal.caComponentCount) else
            stringResource(R.string.signal_ca_inactive)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(180.dp)
    ) {
        items(items) { (label, value) ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─── Advice Card ────────────────────────────────────────────────────────────

@Composable
private fun AdviceCard(advice: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.signal_advice_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = advice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Start
            )
        }
    }
}

// ─── Placement Assist Banner ─────────────────────────────────────────────────

@Composable
private fun PlacementAssistBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val midY = size.height / 2f
                val amplitude = size.height * 0.4f
                val barCount = 20
                val barWidth = size.width / (barCount * 2f)
                for (i in 0 until barCount) {
                    val x = (i * 2 + 1) * barWidth
                    val barPhase = phase + i * 0.4f
                    val barHeight = amplitude * (0.3f + 0.7f * kotlin.math.abs(sin(barPhase.toDouble()).toFloat()))
                    drawRoundRect(
                        color = Color(0xFF1565C0),
                        topLeft = Offset(x - barWidth * 0.4f, midY - barHeight),
                        size = Size(barWidth * 0.8f, barHeight * 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.4f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.signal_placement_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─── Network Mode Selector ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkModeSelector(
    currentMode: NetworkMode,
    onModeSelected: (NetworkMode) -> Unit
) {
    val modes = listOf(
        NetworkMode.FOUR_G_ONLY to stringResource(R.string.network_mode_4g_only),
        NetworkMode.FOUR_G_THREE_G to stringResource(R.string.network_mode_4g_3g),
        NetworkMode.AUTO to stringResource(R.string.network_mode_auto)
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.network_mode_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentMode == mode,
                        onClick = { onModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

// ─── LTE Band Selector ───────────────────────────────────────────────────────

@Composable
private fun LteBandSelector(
    selectedBands: Set<LteBand>,
    onBandToggled: (LteBand) -> Unit,
    onLockBands: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.band_selector_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            // Wrap chips in a flow-like row arrangement
            val bandRows = LteBand.entries.chunked(4)
            bandRows.forEach { rowBands ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    rowBands.forEach { band ->
                        FilterChip(
                            selected = band in selectedBands,
                            onClick = { onBandToggled(band) },
                            label = { Text(band.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onLockBands,
                enabled = selectedBands.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.band_lock_button))
            }
        }
    }
}
