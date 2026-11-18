package com.netmaster.pro.presentation.parental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getConnectedDevicesList
import com.netmaster.pro.domain.adapter.pauseDevice
import com.netmaster.pro.domain.adapter.resumeDevice
import com.netmaster.pro.domain.adapter.pauseAllDevices
import com.netmaster.pro.domain.adapter.resumeAllDevices
import com.netmaster.pro.domain.adapter.getBlockedDomains
import com.netmaster.pro.domain.adapter.setBlockedDomains
import com.netmaster.pro.domain.adapter.setDnsServers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PausedDevice(
    val mac: String,
    val name: String,
    val ip: String,
    val isPaused: Boolean,
    val pauseUntil: Long = 0L
)

enum class PauseDuration(val label: String, val minutes: Int) {
    MIN_30("30 دقيقة", 30),
    HOUR_1("ساعة", 60),
    HOUR_2("ساعتان", 120),
    CUSTOM("مخصص", -1),
    FOREVER("حتى أقول", Int.MAX_VALUE)
}

data class DnsPreset(
    val name: String,
    val emoji: String,
    val descriptionAr: String,
    val primary: String,
    val secondary: String
)

data class ParentalUiState(
    val devices: List<PausedDevice> = emptyList(),
    val selectedDnsPreset: String = "Google",
    val customDnsPrimary: String = "",
    val customDnsSecondary: String = "",
    val benchmarkRunning: Boolean = false,
    val benchmarkResults: List<DnsBenchmark.DnsResult> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val newBlockedDomain: String = "",
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: LocalTime = LocalTime.of(22, 0),
    val bedtimeEnd: LocalTime = LocalTime.of(7, 0),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ParentalViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter,
    private val dnsBenchmark: DnsBenchmark
) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    val dnsPresets = listOf(
        DnsPreset("Family", "🛡️", "حجب المحتوى الإباحي", "1.1.1.3", "1.0.0.3"),
        DnsPreset("Ad Block", "🚫", "حجب الإعلانات", "94.140.14.14", "94.140.15.15"),
        DnsPreset("Gaming", "🎮", "تطبيق أسرع DNS للألعاب", "1.1.1.1", "1.0.0.1"),
        DnsPreset("Google", "🌐", "DNS عام", "8.8.8.8", "8.8.4.4"),
        DnsPreset("Custom", "⚙️", "إدخال مخصص", "", "")
    )

    private val _uiState = MutableStateFlow(ParentalUiState())
    val uiState: StateFlow<ParentalUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
        loadBlockedDomains()
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val connectedDevices = routerAdapter.getConnectedDevicesList()
                val pausedDevices = connectedDevices.map { device ->
                    PausedDevice(
                        mac = device.mac,
                        name = device.hostname.ifBlank { device.ip },
                        ip = device.ip,
                        isPaused = false
                    )
                }
                _uiState.update { it.copy(devices = pausedDevices, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل الأجهزة: ${e.message}") }
            }
        }
    }

    fun pauseDevice(mac: String, duration: PauseDuration, customMinutes: Int = 0) {
        viewModelScope.launch {
            val minutes = if (duration == PauseDuration.CUSTOM) customMinutes else duration.minutes
            val until = if (minutes == Int.MAX_VALUE) Long.MAX_VALUE
                        else System.currentTimeMillis() + minutes * 60_000L
            try {
                routerAdapter.pauseDevice(mac, minutes)
                _uiState.update { state ->
                    state.copy(
                        devices = state.devices.map { d ->
                            if (d.mac == mac) d.copy(isPaused = true, pauseUntil = until) else d
                        },
                        successMessage = "تم إيقاف الإنترنت عن الجهاز"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل إيقاف الجهاز: ${e.message}") }
            }
        }
    }

    fun resumeDevice(mac: String) {
        viewModelScope.launch {
            try {
                routerAdapter.resumeDevice(mac)
                _uiState.update { state ->
                    state.copy(
                        devices = state.devices.map { d ->
                            if (d.mac == mac) d.copy(isPaused = false, pauseUntil = 0L) else d
                        },
                        successMessage = "تم استئناف الإنترنت للجهاز"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل استئناف الجهاز: ${e.message}") }
            }
        }
    }

    fun selectDnsPreset(presetName: String) {
        _uiState.update { it.copy(selectedDnsPreset = presetName) }
    }

    fun applyDns() {
        viewModelScope.launch {
            val state = _uiState.value
            val preset = dnsPresets.find { it.name == state.selectedDnsPreset }
            val primary = if (preset?.name == "Custom") state.customDnsPrimary else preset?.primary ?: return@launch
            val secondary = if (preset.name == "Custom") state.customDnsSecondary else preset.secondary
            try {
                routerAdapter.setDnsServers(primary, secondary)
                _uiState.update { it.copy(successMessage = "تم تطبيق DNS بنجاح") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تطبيق DNS: ${e.message}") }
            }
        }
    }

    fun runDnsBenchmark() {
        viewModelScope.launch {
            _uiState.update { it.copy(benchmarkRunning = true, benchmarkResults = emptyList()) }
            try {
                val results = dnsBenchmark.benchmark()
                _uiState.update { it.copy(benchmarkRunning = false, benchmarkResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(benchmarkRunning = false, errorMessage = "فشل اختبار DNS: ${e.message}") }
            }
        }
    }

    fun applyFastestDns() {
        val fastest = _uiState.value.benchmarkResults.firstOrNull { !it.failed } ?: return
        viewModelScope.launch {
            try {
                routerAdapter.setDnsServers(fastest.primary, fastest.secondary)
                _uiState.update { it.copy(successMessage = "تم تطبيق أسرع DNS: ${fastest.provider} (${fastest.latencyMs}ms)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تطبيق DNS: ${e.message}") }
            }
        }
    }

    fun updateCustomDnsPrimary(value: String) = _uiState.update { it.copy(customDnsPrimary = value) }
    fun updateCustomDnsSecondary(value: String) = _uiState.update { it.copy(customDnsSecondary = value) }
    fun updateNewBlockedDomain(value: String) = _uiState.update { it.copy(newBlockedDomain = value) }

    fun addBlockedDomain() {
        val domain = _uiState.value.newBlockedDomain.trim()
        if (domain.isBlank()) return
        _uiState.update { state ->
            state.copy(
                blockedDomains = (state.blockedDomains + domain).distinct(),
                newBlockedDomain = ""
            )
        }
        syncBlockedDomains()
    }

    fun removeBlockedDomain(domain: String) {
        _uiState.update { state ->
            state.copy(blockedDomains = state.blockedDomains.filter { it != domain })
        }
        syncBlockedDomains()
    }

    private fun syncBlockedDomains() {
        viewModelScope.launch {
            try {
                routerAdapter.setBlockedDomains(_uiState.value.blockedDomains)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل مزامنة قائمة الحجب: ${e.message}") }
            }
        }
    }

    private fun loadBlockedDomains() {
        viewModelScope.launch {
            try {
                val domains = routerAdapter.getBlockedDomains()
                _uiState.update { it.copy(blockedDomains = domains) }
            } catch (_: Exception) {}
        }
    }

    fun setBedtimeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bedtimeEnabled = enabled) }
        if (enabled) scheduleBedtimeWork() else cancelBedtimeWork()
    }

    fun setBedtimeStart(time: LocalTime) = _uiState.update { it.copy(bedtimeStart = time) }
    fun setBedtimeEnd(time: LocalTime) = _uiState.update { it.copy(bedtimeEnd = time) }

    private fun scheduleBedtimeWork() {
        val state = _uiState.value
        val data = Data.Builder()
            .putInt("start_hour", state.bedtimeStart.hour)
            .putInt("start_minute", state.bedtimeStart.minute)
            .putInt("end_hour", state.bedtimeEnd.hour)
            .putInt("end_minute", state.bedtimeEnd.minute)
            .build()
        val request = PeriodicWorkRequestBuilder<BedtimeWorker>(1, TimeUnit.HOURS)
            .setInputData(data)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "bedtime_schedule",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelBedtimeWork() {
        workManager.cancelUniqueWork("bedtime_schedule")
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
