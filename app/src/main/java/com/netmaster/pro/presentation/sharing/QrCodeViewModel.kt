package com.netmaster.pro.presentation.sharing

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class QrCodeUiState(
    val ssid: String = "",
    val password: String = "",
    val security: String = "WPA",
    val hidden: Boolean = false,
    val colorTheme: QrColorTheme = QrColorTheme.DEFAULT,
    val qrBitmap: Bitmap? = null,
    val generating: Boolean = false,
    val nfcWriting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class QrColorTheme(val fg: Int, val bg: Int, val label: String) {
    DEFAULT(Color.BLACK, Color.WHITE, "كلاسيكي"),
    DARK(Color.WHITE, Color.parseColor("#1A1A2E"), "داكن"),
    BRAND(Color.parseColor("#00FFCC"), Color.parseColor("#0D1117"), "نيون سايبر"),
    BLUE(Color.WHITE, Color.parseColor("#1565C0"), "أزرق")
}

@HiltViewModel
class QrCodeViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QrCodeUiState())
    val uiState: StateFlow<QrCodeUiState> = _uiState.asStateFlow()

    fun updateSsid(value: String) = _uiState.update { it.copy(ssid = value) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }
    fun updateSecurity(value: String) = _uiState.update { it.copy(security = value) }
    fun updateHidden(value: Boolean) = _uiState.update { it.copy(hidden = value) }
    fun updateColorTheme(theme: QrColorTheme) = _uiState.update { it.copy(colorTheme = theme) }

    fun generateQrCode() {
        val state = _uiState.value
        if (state.ssid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "أدخل اسم الشبكة") }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(generating = true, qrBitmap = null) }
            val wifiString = buildWifiString(state)
            val bitmap = generateQrBitmap(wifiString, state.colorTheme, 512)
            _uiState.update { it.copy(generating = false, qrBitmap = bitmap) }
        }
    }

    private fun buildWifiString(state: QrCodeUiState): String {
        val sec = if (state.security.equals("open", true)) "nopass" else state.security
        val hidden = if (state.hidden) ";H:true;" else ";;"
        return "WIFI:T:${sec};S:${escapeWifi(state.ssid)};P:${escapeWifi(state.password)}$hidden"
    }

    private fun escapeWifi(s: String): String = s
        .replace("\\", "\\\\").replace(";", "\\;")
        .replace(",", "\\,").replace("\"", "\\\"")
        .replace(":", "\\:")

    private fun generateQrBitmap(content: String, theme: QrColorTheme, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) theme.fg else theme.bg)
            }
        }
        return bitmap
    }

    fun shareQrCode() {
        val bitmap = _uiState.value.qrBitmap ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(getApplication<Application>().cacheDir, "qrcode_wifi.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                getApplication<Application>().startActivity(Intent.createChooser(intent, "مشاركة QR Code").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    fun writeToNfc(tag: Tag) {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(nfcWriting = true) }
            try {
                val wifiString = buildWifiString(state)
                val record = NdefRecord.createTextRecord("en", wifiString)
                val message = NdefMessage(arrayOf(record))
                val ndef = Ndef.get(tag)
                ndef?.connect()
                ndef?.writeNdefMessage(message)
                ndef?.close()
                _uiState.update { it.copy(nfcWriting = false, successMessage = "تم الكتابة على NFC بنجاح ✓") }
            } catch (e: Exception) {
                _uiState.update { it.copy(nfcWriting = false, errorMessage = "فشل الكتابة على NFC: ${e.message}") }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, successMessage = null) }
}
