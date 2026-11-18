package com.netmaster.pro.presentation.sharing

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ExtractedRouterInfo(
    val ip: String = "",
    val ssid: String = "",
    val wifiPassword: String = "",
    val adminPassword: String = "",
    val pin: String = ""
)

data class OcrUiState(
    val scanning: Boolean = false,
    val extractedText: String = "",
    val extractedInfo: ExtractedRouterInfo = ExtractedRouterInfo(),
    val highlightRects: List<Rect> = emptyList(),
    val appliedCredentials: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OcrScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun processImage(image: InputImage) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true) }
            try {
                val result = recognizer.process(image).await()
                val fullText = result.text
                val extracted = extractRouterInfo(fullText)
                val rects = result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { it.boundingBox }
                }
                _uiState.update {
                    it.copy(
                        scanning = false,
                        extractedText = fullText,
                        extractedInfo = extracted,
                        highlightRects = rects
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(scanning = false, errorMessage = "فشل التعرف على النص: ${e.message}") }
            }
        }
    }

    private fun extractRouterInfo(text: String): ExtractedRouterInfo {
        val ipRegex = Regex("""\b(\d{1,3}\.){3}\d{1,3}\b""")
        val ip = ipRegex.find(text)?.value ?: ""

        val ssidRegex = Regex("""(?:SSID|Network Name|WiFi Name)\s*[:=]\s*([^\n\r,]+)""", RegexOption.IGNORE_CASE)
        val ssid = ssidRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""

        val wifiKeyRegex = Regex("""(?:WiFi Key|WPA Key|Password|KEY|WPA2 Key|Wi-Fi Password)\s*[:=]\s*([^\n\r,\s]+)""", RegexOption.IGNORE_CASE)
        val wifiPassword = wifiKeyRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""

        val adminPassRegex = Regex("""(?:Admin Password|Admin Pass|GUI Password|Web Password)\s*[:=]\s*([^\n\r,\s]+)""", RegexOption.IGNORE_CASE)
        val adminPassword = adminPassRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""

        val pinRegex = Regex("""(?:PIN|WPS PIN)\s*[:=]\s*(\d{6,8})""", RegexOption.IGNORE_CASE)
        val pin = pinRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""

        return ExtractedRouterInfo(ip, ssid, wifiPassword, adminPassword, pin)
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun reset() = _uiState.update { OcrUiState() }
}
