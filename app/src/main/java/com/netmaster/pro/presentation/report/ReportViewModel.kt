package com.netmaster.pro.presentation.report

import android.app.Application
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getSignalInfo
import com.netmaster.pro.domain.adapter.getConnectedDevicesList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ReportData(
    val routerModel: String = "",
    val routerIp: String = "",
    val ssid: String = "",
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Long = 0,
    val connectedDevices: Int = 0,
    val signalStrength: Int = 0,
    val securityScore: Int = 0,
    val generatedAt: Long = System.currentTimeMillis()
)

data class ReportUiState(
    val reportData: ReportData = ReportData(),
    val loading: Boolean = false,
    val pdfReady: Boolean = false,
    val pdfPath: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun collectData() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val signal = routerAdapter.getSignalInfo()
                val devices = routerAdapter.getConnectedDevicesList()
                _uiState.update {
                    it.copy(
                        loading = false,
                        reportData = ReportData(
                            routerModel = "Greenpacket D2-220G1",
                            routerIp = "192.168.1.1",
                            ssid = "",
                            downloadMbps = 0.0,
                            uploadMbps = 0.0,
                            pingMs = 0,
                            connectedDevices = devices.size,
                            signalStrength = signal.rssi,
                            securityScore = 0
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "فشل جمع البيانات: ${e.message}") }
            }
        }
    }

    fun generatePdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val data = _uiState.value.reportData
                val pdfDoc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
                val page = pdfDoc.startPage(pageInfo)
                drawReportPage(page.canvas, data)
                pdfDoc.finishPage(page)

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val filename = "NetMaster_Report_${sdf.format(Date())}.pdf"
                val file = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), filename)
                FileOutputStream(file).use { pdfDoc.writeTo(it) }
                pdfDoc.close()

                _uiState.update { it.copy(loading = false, pdfReady = true, pdfPath = file.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "فشل توليد PDF: ${e.message}") }
            }
        }
    }

    fun sharePdf() {
        val path = _uiState.value.pdfPath
        if (path.isBlank()) return
        val file = File(path)
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        getApplication<Application>().startActivity(Intent.createChooser(intent, "مشاركة التقرير").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun drawReportPage(canvas: Canvas, data: ReportData) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val titlePaint = Paint().apply { textSize = 24f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 13f }
        val smallPaint = Paint().apply { textSize = 11f; color = android.graphics.Color.GRAY }

        var y = 60f
        canvas.drawText("NetMaster Pro - Diagnostic Report", 40f, y, titlePaint); y += 30f
        canvas.drawText(sdf.format(Date(data.generatedAt)), 40f, y, smallPaint); y += 40f

        canvas.drawText("Router Information", 40f, y, headerPaint); y += 20f
        canvas.drawText("Model: ${data.routerModel}", 40f, y, bodyPaint); y += 18f
        canvas.drawText("IP: ${data.routerIp}", 40f, y, bodyPaint); y += 18f
        canvas.drawText("SSID: ${data.ssid.ifBlank { "N/A" }}", 40f, y, bodyPaint); y += 30f

        canvas.drawText("Speed Test Results", 40f, y, headerPaint); y += 20f
        canvas.drawText("Download: ${"%.1f".format(data.downloadMbps)} Mbps", 40f, y, bodyPaint); y += 18f
        canvas.drawText("Upload: ${"%.1f".format(data.uploadMbps)} Mbps", 40f, y, bodyPaint); y += 18f
        canvas.drawText("Ping: ${data.pingMs} ms", 40f, y, bodyPaint); y += 30f

        canvas.drawText("Network Status", 40f, y, headerPaint); y += 20f
        canvas.drawText("Connected Devices: ${data.connectedDevices}", 40f, y, bodyPaint); y += 18f
        canvas.drawText("Signal Strength: ${data.signalStrength} dBm", 40f, y, bodyPaint); y += 18f
        canvas.drawText("Security Score: ${data.securityScore}/100", 40f, y, bodyPaint); y += 30f

        canvas.drawText("Generated by NetMaster Pro", 40f, 820f, smallPaint)
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
