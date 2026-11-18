package com.netmaster.pro.presentation.wifi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netmaster.pro.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAdminScreen(
    viewModel: WifiAdminViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val applyState by viewModel.applyState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(applyState) {
        when (applyState) {
            is WifiApplyState.Success -> {
                snackbarHostState.showSnackbar("✓ Settings applied successfully")
                viewModel.resetApplyState()
            }
            is WifiApplyState.Error -> {
                snackbarHostState.showSnackbar((applyState as WifiApplyState.Error).message)
                viewModel.resetApplyState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wifi_admin_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2.4GHz Settings
            SectionCard(title = stringResource(R.string.wifi_band_24)) {
                SsidPasswordRow(
                    ssid = settings.ssid24,
                    password = settings.password24,
                    onSsidChange = { viewModel.updateSsid24(it) },
                    onPasswordChange = { viewModel.updatePassword24(it) },
                    ssidLabel = stringResource(R.string.wifi_ssid_24),
                    passwordLabel = stringResource(R.string.wifi_password_label)
                )
            }

            // 5GHz Settings
            SectionCard(title = stringResource(R.string.wifi_band_5)) {
                SsidPasswordRow(
                    ssid = settings.ssid5,
                    password = settings.password5,
                    onSsidChange = { viewModel.updateSsid5(it) },
                    onPasswordChange = { viewModel.updatePassword5(it) },
                    ssidLabel = stringResource(R.string.wifi_ssid_5),
                    passwordLabel = stringResource(R.string.wifi_password_label)
                )
            }

            // Security & General
            SectionCard(title = stringResource(R.string.wifi_security_section)) {
                // Security mode dropdown
                SecurityDropdown(
                    selected = settings.security,
                    onSelected = { viewModel.updateSecurity(it) }
                )
                Spacer(Modifier.height(8.dp))

                // Hide SSID toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.wifi_hide_ssid))
                    Switch(
                        checked = settings.hideSsid,
                        onCheckedChange = { viewModel.updateHideSsid(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Max clients slider
                Text(stringResource(R.string.wifi_max_clients, settings.maxClients), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.maxClients.toFloat(),
                    onValueChange = { viewModel.updateMaxClients(it.toInt()) },
                    valueRange = 1f..32f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // TX Power
                Text(stringResource(R.string.wifi_tx_power), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxPower.entries.forEach { power ->
                        FilterChip(
                            selected = settings.txPower == power,
                            onClick = { viewModel.updateTxPower(power) },
                            label = {
                                Text(
                                    when (power) {
                                        TxPower.LOW -> stringResource(R.string.wifi_tx_low)
                                        TxPower.MEDIUM -> stringResource(R.string.wifi_tx_medium)
                                        TxPower.HIGH -> stringResource(R.string.wifi_tx_high)
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // MAC Filter
            SectionCard(title = stringResource(R.string.wifi_mac_filter_title)) {
                MacFilterSection(
                    mode = settings.macFilterMode,
                    macList = settings.macFilterList,
                    onModeChange = { viewModel.updateMacFilterMode(it) },
                    onAddMac = { viewModel.addMacToFilter(it) },
                    onRemoveMac = { viewModel.removeMacFromFilter(it) }
                )
            }

            // Guest Wi-Fi
            SectionCard(title = stringResource(R.string.wifi_guest_title)) {
                GuestWifiSection(
                    enabled = settings.guestEnabled,
                    ssid = settings.guestSsid,
                    password = settings.guestPassword,
                    autoOff = settings.guestAutoOff,
                    onEnabledChange = { viewModel.updateGuestEnabled(it) },
                    onSsidChange = { viewModel.updateGuestSsid(it) },
                    onPasswordChange = { viewModel.updateGuestPassword(it) },
                    onAutoOffChange = { viewModel.updateGuestAutoOff(it) }
                )
            }

            // Router Reboot section
            RouterRebootSection()

            // Apply button
            Button(
                onClick = { viewModel.applySettings() },
                enabled = applyState !is WifiApplyState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (applyState is WifiApplyState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.wifi_apply_button))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Section Card ────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Divider()
            content()
        }
    }
}

// ─── SSID + Password Row ─────────────────────────────────────────────────────

@Composable
private fun SsidPasswordRow(
    ssid: String,
    password: String,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    ssidLabel: String,
    passwordLabel: String
) {
    var showPassword by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = ssid,
        onValueChange = onSsidChange,
        label = { Text(ssidLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(passwordLabel) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    WifiPasswordStrengthIndicator(password = password)
}

// ─── Security Dropdown ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityDropdown(
    selected: WifiSecurity,
    onSelected: (WifiSecurity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.toLabel(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.wifi_security_mode)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WifiSecurity.entries.forEach { sec ->
                DropdownMenuItem(
                    text = { Text(sec.toLabel()) },
                    onClick = {
                        onSelected(sec)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun WifiSecurity.toLabel(): String = when (this) {
    WifiSecurity.WPA2_PSK -> "WPA2-PSK"
    WifiSecurity.WPA2_WPA3_SAE -> "WPA2/WPA3-SAE"
    WifiSecurity.WPA3_SAE -> "WPA3-SAE"
}

// ─── MAC Filter Section ──────────────────────────────────────────────────────

@Composable
private fun MacFilterSection(
    mode: MacFilterMode,
    macList: List<String>,
    onModeChange: (MacFilterMode) -> Unit,
    onAddMac: (String) -> Unit,
    onRemoveMac: (String) -> Unit
) {
    var newMac by remember { mutableStateOf("") }

    // Mode toggle chips
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacFilterMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onModeChange(m) },
                label = {
                    Text(
                        when (m) {
                            MacFilterMode.OFF -> stringResource(R.string.wifi_mac_filter_off)
                            MacFilterMode.BLACKLIST -> stringResource(R.string.wifi_mac_filter_blacklist)
                            MacFilterMode.WHITELIST -> stringResource(R.string.wifi_mac_filter_whitelist)
                        },
                        fontSize = 12.sp
                    )
                }
            )
        }
    }

    AnimatedVisibility(mode != MacFilterMode.OFF) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            macList.forEach { mac ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mac, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { onRemoveMac(mac) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMac,
                    onValueChange = { newMac = it },
                    label = { Text(stringResource(R.string.wifi_mac_add_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newMac.isNotBlank()) {
                            onAddMac(newMac.trim())
                            newMac = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        }
    }
}

// ─── Guest Wi-Fi Section ─────────────────────────────────────────────────────

@Composable
private fun GuestWifiSection(
    enabled: Boolean,
    ssid: String,
    password: String,
    autoOff: GuestAutoOffTimer,
    onEnabledChange: (Boolean) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAutoOffChange: (GuestAutoOffTimer) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.wifi_guest_enable))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    AnimatedVisibility(enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                label = { Text(stringResource(R.string.wifi_guest_ssid)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var showGuestPwd by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.wifi_guest_password)) },
                singleLine = true,
                visualTransformation = if (showGuestPwd) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showGuestPwd = !showGuestPwd }) {
                        Icon(if (showGuestPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Auto-off timer chips
            Text(stringResource(R.string.wifi_guest_auto_off), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GuestAutoOffTimer.entries.forEach { timer ->
                    FilterChip(
                        selected = autoOff == timer,
                        onClick = { onAutoOffChange(timer) },
                        label = {
                            Text(
                                when (timer) {
                                    GuestAutoOffTimer.MIN_30 -> "30m"
                                    GuestAutoOffTimer.HOUR_1 -> "1h"
                                    GuestAutoOffTimer.HOUR_2 -> "2h"
                                    GuestAutoOffTimer.HOUR_4 -> "4h"
                                    GuestAutoOffTimer.CUSTOM -> stringResource(R.string.wifi_guest_timer_custom)
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }
    }
}
