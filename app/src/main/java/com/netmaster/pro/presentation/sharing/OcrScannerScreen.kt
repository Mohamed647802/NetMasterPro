package com.netmaster.pro.presentation.sharing

import android.Manifest
import android.graphics.Rect
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(
    viewModel: OcrScannerViewModel = hiltViewModel(),
    onCredentialsExtracted: (ip: String, password: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("مسح لصاقة الراوتر") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    if (state.extractedInfo.ip.isNotEmpty() || state.extractedInfo.wifiPassword.isNotEmpty()) {
                        TextButton(onClick = {
                            onCredentialsExtracted(state.extractedInfo.ip, state.extractedInfo.adminPassword)
                        }) { Text("تطبيق") }
                    }
                    IconButton(onClick = viewModel::reset) { Icon(Icons.Default.Refresh, "إعادة") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!cameraPermission.status.isGranted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("يتطلب إذن الكاميرا", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("منح الإذن") }
                    }
                }
                return@Scaffold
            }

            // Camera preview
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                CameraPreview(onImageCaptured = { proxy ->
                    proxy.use {
                        val image = InputImage.fromMediaImage(proxy.image!!, proxy.imageInfo.rotationDegrees)
                        viewModel.processImage(image)
                    }
                })
                // Overlay highlights
                if (state.highlightRects.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        state.highlightRects.forEach { rect ->
                            drawRect(
                                color = Color(0xFF00FFCC),
                                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                                size = androidx.compose.ui.geometry.Size(rect.width().toFloat(), rect.height().toFloat()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
                // Scanning indicator
                if (state.scanning) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF00FFCC))
                                Spacer(Modifier.width(6.dp))
                                Text("جارٍ التعرف...", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Extracted info
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("المعلومات المستخرجة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                val info = state.extractedInfo
                if (info.ip.isEmpty() && info.ssid.isEmpty() && info.wifiPassword.isEmpty() && info.adminPassword.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DocumentScanner, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(8.dp))
                                Text("وجّه الكاميرا نحو لصاقة الراوتر", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                } else {
                    if (info.ip.isNotEmpty()) item { InfoExtractedRow("IP", info.ip) }
                    if (info.ssid.isNotEmpty()) item { InfoExtractedRow("SSID", info.ssid) }
                    if (info.wifiPassword.isNotEmpty()) item { InfoExtractedRow("كلمة Wi-Fi", info.wifiPassword, sensitive = true) }
                    if (info.adminPassword.isNotEmpty()) item { InfoExtractedRow("كلمة المرور الإدارية", info.adminPassword, sensitive = true) }
                    if (info.pin.isNotEmpty()) item { InfoExtractedRow("WPS PIN", info.pin) }

                    if (info.ip.isNotEmpty() || info.wifiPassword.isNotEmpty()) {
                        item {
                            Button(
                                onClick = { onCredentialsExtracted(info.ip, info.adminPassword) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoFixHigh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("تطبيق تلقائي للبيانات المستخرجة")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoExtractedRow(label: String, value: String, sensitive: Boolean = false) {
    var revealed by remember { mutableStateOf(!sensitive) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    if (sensitive && !revealed) "●".repeat(value.length.coerceAtMost(16)) else value,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            if (sensitive) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onImageCaptured: (ImageProxy) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { proxy ->
                            if (proxy.image != null) onImageCaptured(proxy)
                            else proxy.close()
                        }
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
