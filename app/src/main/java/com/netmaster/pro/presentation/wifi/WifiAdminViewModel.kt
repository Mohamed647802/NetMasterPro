package com.netmaster.pro.presentation.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.MacFilterMode as DomainMacFilterMode
import com.netmaster.pro.domain.adapter.WifiSettings as DomainWifiSettings
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Local UI models ─────────────────────────────────────────────────────────

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

// ─── Mapping helpers ──────────────────────────────────────────────────────────

private fun DomainWifiSettings.toUi(): WifiSettings = WifiSettings(
    ssid24 = ssid2g,
    password24 = password2g,
    ssid5 = ssid5g,
    password5 = password5g,
    security = when {
        security.contains("WPA3", ignoreCase = true) && security.contains("WPA2", ignoreCase = true) ->
            WifiSecurity.WPA2_WPA3_SAE
        security.contains("WPA3", ignoreCase = true) -> WifiSecurity.WPA3_SAE
        else -> WifiSecurity.WPA2_PSK
    }
)

private fun WifiSettings.toDomain(): DomainWifiSettings = DomainWifiSettings(
    ssid2g = ssid24,
    password2g = password24,
    ssid5g = ssid5,
    password5g = password5,
    security = when (security) {
        WifiSecurity.WPA3_SAE -> "WPA3-SAE"
        WifiSecurity.WPA2_WPA3_SAE -> "WPA2-PSK/WPA3-SAE"
        else -> "WPA2-PSK"
    }
)

private fun MacFilterMode.toDomain(): DomainMacFilterMode = when (this) {
    MacFilterMode.BLACKLIST -> DomainMacFilterMode.BLACKLIST
    MacFilterMode.WHITELIST -> DomainMacFilterMode.WHITELIST
    MacFilterMode.OFF -> DomainMacFilterMode.DISABLED
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class WifiAdminViewModel @Inject constructor(
    private val routerRepository: RouterRepository
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
            routerRepository.getWifiSettings().onSuccess { domain ->
                _settings.value = domain.toUi()
            }
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
            val current = _settings.value
            routerRepository.setWifiSettings(current.toDomain()).fold(
                onSuccess = {
                    if (current.guestEnabled) {
                        routerRepository.setGuestWifi(true, current.guestSsid, current.guestPassword)
                    }
                    routerRepository.setMacFilter(current.macFilterMode.toDomain(), current.macFilterList)
                    _applyState.value = WifiApplyState.Success
                },
                onFailure = { _applyState.value = WifiApplyState.Error(it.message ?: "Failed") }
            )
        }
    }

    fun resetApplyState() {
        _applyState.value = WifiApplyState.Idle
    }
}
