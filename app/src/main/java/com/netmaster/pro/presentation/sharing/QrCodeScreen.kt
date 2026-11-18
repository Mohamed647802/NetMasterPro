package com.netmaster.pro.presentation.sharing

import android.nfc.NfcAdapter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(viewModel: QrCodeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPassword by remember { mutableStateOf(false) }
    val hasNfc = NfcAdapter.getDefaultAdapter(context) != null

    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("مشاركة Wi-Fi بـ QR") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Network info inputs
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("بيانات الشبكة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.ssid,
                            onValueChange = viewModel::updateSsid,
                            label = { Text("اسم الشبكة (SSID)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Wifi, null) }
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::updatePassword,
                            label = { Text("كلمة المرور") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WPA", "WPA2", "WPA3", "Open").forEach { sec ->
                                FilterChip(
                                    selected = state.security == sec,
                                    onClick = { viewModel.updateSecurity(sec) },
                                    label = { Text(sec, fontSize = 11.sp) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("شبكة مخفية", Modifier.weight(1f))
                            Switch(checked = state.hidden, onCheckedChange = viewModel::updateHidden)
                        }
                    }
                }
            }

            // Color theme
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("النمط اللوني", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QrColorTheme.entries.forEach { theme ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(theme.bg))
                                        .border(
                                            width = if (state.colorTheme == theme) 3.dp else 1.dp,
                                            color = if (state.colorTheme == theme) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.updateColorTheme(theme) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("QR", color = Color(theme.fg), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Generate button
            item {
                Button(
                    onClick = viewModel::generateQrCode,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.generating
                ) {
                    if (state.generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Default.QrCode2, null)
                    Spacer(Modifier.width(8.dp))
                    Text("توليد QR Code")
                }
            }

            // QR Code display
            state.qrBitmap?.let { bitmap ->
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.ssid, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = viewModel::shareQrCode, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Share, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("مشاركة")
                                }
                                if (hasNfc) {
                                    OutlinedButton(
                                        onClick = { /* NFC requires foreground dispatch activity setup */ },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Nfc, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("NFC")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
