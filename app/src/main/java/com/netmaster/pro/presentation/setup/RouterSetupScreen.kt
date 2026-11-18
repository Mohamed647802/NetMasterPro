package com.netmaster.pro.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterSetupScreen(
    navController: NavController,
    viewModel: RouterSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعداد الراوتر") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Auto-detect button ──────────────────────────────────────────
            FilledTonalButton(
                onClick  = { viewModel.autoDetect() },
                modifier = Modifier.fillMaxWidth(),
                enabled  = !state.isLoading && !state.isTesting
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Default.Search, contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp))
                }
                Text("اكتشاف تلقائي")
            }

            // ── Manual IP input ─────────────────────────────────────────────
            OutlinedTextField(
                value         = state.ip,
                onValueChange = viewModel::onIpChange,
                label         = { Text("عنوان IP للراوتر") },
                placeholder   = { Text("مثال: 192.168.1.1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = state.port,
                onValueChange = viewModel::onPortChange,
                label         = { Text("المنفذ (Port)") },
                placeholder   = { Text("80") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            // ── Credentials ─────────────────────────────────────────────────
            OutlinedTextField(
                value         = state.username,
                onValueChange = viewModel::onUsernameChange,
                label         = { Text("اسم المستخدم") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = state.password,
                onValueChange = viewModel::onPasswordChange,
                label         = { Text("كلمة المرور") },
                visualTransformation = if (state.showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = viewModel::toggleShowPassword) {
                        Icon(
                            imageVector = if (state.showPassword) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = if (state.showPassword) "إخفاء" else "إظهار"
                        )
                    }
                },
                modifier  = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ── Test connection ─────────────────────────────────────────────
            OutlinedButton(
                onClick  = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled  = state.ip.isNotBlank() && !state.isTesting && !state.isLoading
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("اختبار الاتصال")
            }

            // ── Test result ─────────────────────────────────────────────────
            state.testResult?.let { result ->
                when (result) {
                    is TestResult.Success -> Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853))
                            Text(
                                "✅ الاتصال ناجح!",
                                modifier = Modifier.padding(start = 8.dp),
                                color = Color(0xFF00C853)
                            )
                        }
                    }
                    is TestResult.Failure -> Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "❌ ${result.reason}",
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── Detected router info ────────────────────────────────────────
            state.detectedBrand?.let { brand ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "الراوتر المكتشف",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "🏭 الماركة: ${brand.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (state.detectedModel.isNotBlank()) {
                            Text(
                                "📡 الموديل: ${state.detectedModel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── Biometric lock option ───────────────────────────────────────
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp))
                        Text("قفل بصمة الإصبع")
                    }
                    Switch(
                        checked         = state.biometricEnabled,
                        onCheckedChange = viewModel::onBiometricToggle
                    )
                }
            }

            // ── Error ───────────────────────────────────────────────────────
            state.error?.let { error ->
                Text(
                    text  = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Save button ─────────────────────────────────────────────────
            Button(
                onClick  = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled  = state.ip.isNotBlank() && !state.isLoading
            ) {
                Text("حفظ الإعدادات")
            }
        }
    }
}
