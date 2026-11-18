package com.netmaster.pro.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.local.security.BiometricAuthManager
import com.netmaster.pro.data.local.security.EncryptedPreferencesManager
import com.netmaster.pro.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppLanguage { ARABIC, ENGLISH }
enum class ScanInterval { MIN_15, MIN_30, HOUR_1, OFF }

data class SettingsUiState(
    val language: AppLanguage          = AppLanguage.ARABIC,
    val theme: AppTheme                = AppTheme.SYSTEM,
    val biometricEnabled: Boolean      = false,
    val biometricAvailable: Boolean    = false,
    val intruderAlertsEnabled: Boolean = true,
    val quotaAlert50: Boolean          = true,
    val quotaAlert80: Boolean          = true,
    val quotaAlert95: Boolean          = true,
    val scanInterval: ScanInterval     = ScanInterval.MIN_15,
    val savedRouterIps: List<String>   = emptyList(),
    val showResetDialog: Boolean       = false,
    val appVersion: String             = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val encryptedPrefs: EncryptedPreferencesManager,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        _uiState.value = _uiState.value.copy(language = lang)
    }

    fun setTheme(theme: AppTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    fun toggleBiometric(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
    }

    fun toggleIntruderAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(intruderAlertsEnabled = enabled)
    }

    fun toggleQuotaAlert50(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(quotaAlert50 = enabled)
    }

    fun toggleQuotaAlert80(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(quotaAlert80 = enabled)
    }

    fun toggleQuotaAlert95(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(quotaAlert95 = enabled)
    }

    fun setScanInterval(interval: ScanInterval) {
        _uiState.value = _uiState.value.copy(scanInterval = interval)
    }

    fun showResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = true)
    }

    fun dismissResetDialog() {
        _uiState.value = _uiState.value.copy(showResetDialog = false)
    }

    fun resetApp() {
        viewModelScope.launch {
            encryptedPrefs.clearAll()
            _uiState.value = SettingsUiState()
        }
    }

    fun deleteRouter(ip: String) {
        viewModelScope.launch {
            encryptedPrefs.clearCredentials(ip)
            val updated = _uiState.value.savedRouterIps.filter { it != ip }
            _uiState.value = _uiState.value.copy(savedRouterIps = updated)
        }
    }
}
