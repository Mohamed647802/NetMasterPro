package com.netmaster.pro.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.local.security.EncryptedPreferencesManager
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterDevice
import com.netmaster.pro.domain.adapter.fingerprint.GatewayDetector
import com.netmaster.pro.domain.adapter.fingerprint.RouterFingerprinter
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouterSetupUiState(
    val ip: String                       = "",
    val port: String                     = "80",
    val username: String                 = "admin",
    val password: String                 = "",
    val showPassword: Boolean            = false,
    val isLoading: Boolean               = false,
    val isTesting: Boolean               = false,
    val detectedBrand: RouterBrand?      = null,
    val detectedModel: String            = "",
    val testResult: TestResult?          = null,
    val biometricEnabled: Boolean        = false,
    val error: String?                   = null,
    val saved: Boolean                   = false
)

sealed class TestResult {
    object Success : TestResult()
    data class Failure(val reason: String) : TestResult()
}

@HiltViewModel
class RouterSetupViewModel @Inject constructor(
    private val routerRepository: RouterRepository,
    private val gatewayDetector: GatewayDetector,
    private val routerFingerprinter: RouterFingerprinter,
    private val encryptedPrefs: EncryptedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouterSetupUiState())
    val uiState: StateFlow<RouterSetupUiState> = _uiState.asStateFlow()

    fun onIpChange(ip: String)           { _uiState.value = _uiState.value.copy(ip = ip) }
    fun onPortChange(port: String)       { _uiState.value = _uiState.value.copy(port = port) }
    fun onUsernameChange(u: String)      { _uiState.value = _uiState.value.copy(username = u) }
    fun onPasswordChange(p: String)      { _uiState.value = _uiState.value.copy(password = p) }
    fun toggleShowPassword()             { _uiState.value = _uiState.value.copy(showPassword = !_uiState.value.showPassword) }
    fun onBiometricToggle(enabled: Boolean) { _uiState.value = _uiState.value.copy(biometricEnabled = enabled) }

    fun autoDetect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val gateway = gatewayDetector.getDefaultGateway()
                if (gateway != null) {
                    val routerDevice = routerFingerprinter.detect(gateway)
                    _uiState.value = _uiState.value.copy(
                        ip            = gateway,
                        detectedBrand = routerDevice.brand,
                        detectedModel = routerDevice.model,
                        isLoading     = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error     = "تعذّر اكتشاف الراوتر تلقائيًا. أدخل العنوان يدويًا."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = e.message ?: "خطأ في الاكتشاف التلقائي"
                )
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isTesting = true, testResult = null)
            try {
                val port = state.port.toIntOrNull() ?: 80
                val result = routerRepository.login(
                    ip       = state.ip,
                    port     = port,
                    username = state.username,
                    password = state.password
                )
                if (result.isSuccess) {
                    val routerDevice = routerFingerprinter.detect(state.ip)
                    _uiState.value = _uiState.value.copy(
                        isTesting     = false,
                        detectedBrand = routerDevice.brand,
                        detectedModel = routerDevice.model,
                        testResult    = TestResult.Success
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isTesting  = false,
                        testResult = TestResult.Failure(result.exceptionOrNull()?.message ?: "فشل الاتصال")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTesting  = false,
                    testResult = TestResult.Failure(e.message ?: "خطأ")
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            encryptedPrefs.saveCredentials(state.ip, state.username, state.password)
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }
}
