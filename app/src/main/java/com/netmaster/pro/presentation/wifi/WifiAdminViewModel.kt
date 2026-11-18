package com.netmaster.pro.presentation.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Domain models ───────────────────────────────────────────────────────────

enum class WifiSecurity { WPA2_PSK, WPA2_WPA3_SAE, WPA3_SAE }
enum class TxPower { LOW, MEDIUM, HIGH }
enum class MacFilterMode { OFF, BLACKLIST, WHITELIST }
enum class GuestAutoOffTimer { MIN_30, HOUR_1, HOUR_2, HOUR_4, CUSTOM }

data class WifiSettings(
    val ssid24: String = "",
    val password24: String = "",
    val ssid5: String = "",
    val password5: String = "",
    val security: WifiSecurity = WifiSecurity.WPA2_PSK,
    val hideSsid: Boolean = false,
    val maxClients: Int = 32,
    val txPower: TxPower = TxPower.HIGH,
    val macFilterMode: MacFilterMode = MacFilterMode.OFF,
    val macFilterList: List<String> = emptyList(),
    val guestEnabled: Boolean = false,
    val guestSsid: String = "",
    val guestPassword: String = "",
    val guestAutoOff: GuestAutoOffTimer = GuestAutoOffTimer.HOUR_1
)

sealed class WifiApplyState {
    object Idle : WifiApplyState()
    object Loading : WifiApplyState()
    object Success : WifiApplyState()
    data class Error(val message: String) : WifiApplyState()
}

// Repository interface (provided by data layer)
interface WifiRepository {
    suspend fun getSettings(): Result<WifiSettings>
    suspend fun applySettings(settings: WifiSettings): Result<Unit>
}

// ─── ViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class WifiAdminViewModel @Inject constructor(
    private val wifiRepository: WifiRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(WifiSettings())
    val settings: StateFlow<WifiSettings> = _settings.asStateFlow()

    private val _applyState = MutableStateFlow<WifiApplyState>(WifiApplyState.Idle)
    val applyState: StateFlow<WifiApplyState> = _applyState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            wifiRepository.getSettings().onSuccess { _settings.value = it }
            _isLoading.value = false
        }
    }

    fun updateSsid24(ssid: String) {
        _settings.value = _settings.value.copy(ssid24 = ssid)
    }

    fun updatePassword24(pwd: String) {
        _settings.value = _settings.value.copy(password24 = pwd)
    }

    fun updateSsid5(ssid: String) {
        _settings.value = _settings.value.copy(ssid5 = ssid)
    }

    fun updatePassword5(pwd: String) {
        _settings.value = _settings.value.copy(password5 = pwd)
    }

    fun updateSecurity(sec: WifiSecurity) {
        _settings.value = _settings.value.copy(security = sec)
    }

    fun updateHideSsid(hide: Boolean) {
        _settings.value = _settings.value.copy(hideSsid = hide)
    }

    fun updateMaxClients(max: Int) {
        _settings.value = _settings.value.copy(maxClients = max)
    }

    fun updateTxPower(power: TxPower) {
        _settings.value = _settings.value.copy(txPower = power)
    }

    fun updateMacFilterMode(mode: MacFilterMode) {
        _settings.value = _settings.value.copy(macFilterMode = mode)
    }

    fun addMacToFilter(mac: String) {
        val current = _settings.value.macFilterList.toMutableList()
        if (mac !in current) {
            current.add(mac)
            _settings.value = _settings.value.copy(macFilterList = current)
        }
    }

    fun removeMacFromFilter(mac: String) {
        val current = _settings.value.macFilterList.toMutableList()
        current.remove(mac)
        _settings.value = _settings.value.copy(macFilterList = current)
    }

    fun updateGuestEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(guestEnabled = enabled)
    }

    fun updateGuestSsid(ssid: String) {
        _settings.value = _settings.value.copy(guestSsid = ssid)
    }

    fun updateGuestPassword(pwd: String) {
        _settings.value = _settings.value.copy(guestPassword = pwd)
    }

    fun updateGuestAutoOff(timer: GuestAutoOffTimer) {
        _settings.value = _settings.value.copy(guestAutoOff = timer)
    }

    fun applySettings() {
        viewModelScope.launch {
            _applyState.value = WifiApplyState.Loading
            wifiRepository.applySettings(_settings.value).fold(
                onSuccess = { _applyState.value = WifiApplyState.Success },
                onFailure = { _applyState.value = WifiApplyState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun resetApplyState() {
        _applyState.value = WifiApplyState.Idle
    }
}
