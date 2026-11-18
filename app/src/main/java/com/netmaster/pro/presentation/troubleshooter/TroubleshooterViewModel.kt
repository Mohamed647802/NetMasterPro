package com.netmaster.pro.presentation.troubleshooter

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getSignalInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class StepStatus { PENDING, RUNNING, PASS, FAIL }

data class DiagStep(
    val id: Int,
    val nameAr: String,
    val descriptionAr: String,
    val status: StepStatus = StepStatus.PENDING,
    val failReasonAr: String = "",
    val fixActionAr: String = ""
)

data class TroubleshooterUiState(
    val steps: List<DiagStep> = defaultSteps(),
    val running: Boolean = false,
    val currentStepId: Int = -1,
    val failedAtStep: Int = -1,
    val allPassed: Boolean = false
)

private fun defaultSteps() = listOf(
    DiagStep(1, "اتصال Wi-Fi", "هل الهاتف متصل بشبكة Wi-Fi؟"),
    DiagStep(2, "DHCP / عنوان IP", "هل الهاتف حصل على عنوان IP صالح؟"),
    DiagStep(3, "الـ Gateway", "هل يمكن الوصول للبوابة الافتراضية (الراوتر)؟"),
    DiagStep(4, "مزامنة الراوتر", "هل الراوتر متصل بالإنترنت (4G/DSL)؟"),
    DiagStep(5, "DNS", "هل الـ DNS يعمل ويحل أسماء النطاقات؟"),
    DiagStep(6, "الوصول للإنترنت", "هل يمكن الوصول لـ HTTPS؟")
)

@HiltViewModel
class TroubleshooterViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TroubleshooterUiState())
    val uiState: StateFlow<TroubleshooterUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun startDiagnosis() {
        viewModelScope.launch {
            _uiState.update { it.copy(running = true, steps = defaultSteps(), allPassed = false, failedAtStep = -1) }

            var passed = runStep(1) { checkWifiConnected() }
            if (!passed) return@launch

            passed = runStep(2) { checkDhcpIp() }
            if (!passed) return@launch

            passed = runStep(3) { checkGatewayPing() }
            if (!passed) return@launch

            passed = runStep(4) { checkRouterWanSync() }
            // Don't stop — continue even if WAN sync unclear

            passed = runStep(5) { checkDnsResolution() }
            if (!passed) return@launch

            passed = runStep(6) { checkHttpsReachability() }

            _uiState.update { it.copy(running = false, allPassed = passed && it.failedAtStep < 0) }
        }
    }

    private suspend fun runStep(id: Int, check: suspend () -> Pair<Boolean, String>): Boolean {
        _uiState.update { state ->
            state.copy(
                currentStepId = id,
                steps = state.steps.map {
                    if (it.id == id) it.copy(status = StepStatus.RUNNING) else it
                }
            )
        }
        delay(500) // animation breathing room
        val (result, reason) = check()
        _uiState.update { state ->
            state.copy(
                steps = state.steps.map {
                    if (it.id == id) it.copy(
                        status = if (result) StepStatus.PASS else StepStatus.FAIL,
                        failReasonAr = reason
                    ) else it
                },
                failedAtStep = if (!result && state.failedAtStep < 0) id else state.failedAtStep
            )
        }
        return result
    }

    private suspend fun checkWifiConnected(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        val connected = info.networkId != -1 && info.ssid.isNotBlank() && info.ssid != "<unknown ssid>"
        connected to if (connected) "" else "الهاتف غير متصل بشبكة Wi-Fi — تأكد من تفعيل الواي-فاي وتوصله بالشبكة"
    }

    private suspend fun checkDhcpIp(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifiManager.dhcpInfo
        val ip = dhcp.ipAddress
        val valid = ip != 0
        valid to if (valid) "" else "لم يتم الحصول على عنوان IP من DHCP — جرب إعادة تشغيل الواي-فاي أو الراوتر"
    }

    private suspend fun checkGatewayPing(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
        val gateway = wifiManager.dhcpInfo.gateway
        if (gateway == 0) return@withContext false to "لا يوجد بوابة افتراضية — DHCP غير مكتمل"
        val gatewayIp = "%d.%d.%d.%d".format(
            gateway and 0xFF, (gateway shr 8) and 0xFF,
            (gateway shr 16) and 0xFF, (gateway shr 24) and 0xFF
        )
        try {
            val reachable = InetAddress.getByName(gatewayIp).isReachable(3000)
            reachable to if (reachable) "" else "لا يمكن الوصول للبوابة ($gatewayIp) — قد يكون الراوتر متوقفاً"
        } catch (e: Exception) {
            false to "فشل الوصول للبوابة: ${e.message}"
        }
    }

    private suspend fun checkRouterWanSync(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val signal = routerAdapter.getSignalInfo()
            val synced = signal.rssi > -120
            synced to if (synced) "" else "الراوتر غير متصل بالإنترنت (${signal.rssi} dBm)"
        } catch (e: Exception) {
            // Can't check — assume ok
            true to ""
        }
    }

    private suspend fun checkDnsResolution(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName("google.com")
            val resolved = addr.hostAddress?.isNotEmpty() == true
            resolved to if (resolved) "" else "فشل حل اسم النطاق — الـ DNS لا يعمل"
        } catch (e: Exception) {
            false to "فشل الـ DNS: تحقق من إعدادات DNS أو جرب 1.1.1.1"
        }
    }

    private suspend fun checkHttpsReachability(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://www.google.com").head().build()
            val resp = client.newCall(req).execute()
            val ok = resp.isSuccessful
            ok to if (ok) "" else "الموقع استجاب بكود ${resp.code} — قد يكون هناك مشكلة في الشبكة"
        } catch (e: Exception) {
            false to "فشل الوصول للإنترنت: ${e.message}"
        }
    }

    fun reset() {
        _uiState.update { TroubleshooterUiState() }
    }
}
