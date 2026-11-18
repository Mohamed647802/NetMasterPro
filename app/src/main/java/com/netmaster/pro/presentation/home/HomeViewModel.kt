package com.netmaster.pro.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.data.model.RouterDevice
import com.netmaster.pro.data.local.security.EncryptedPreferencesManager
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean                  = false,
    val connectionStatus: ConnectionStatus  = ConnectionStatus.DISCONNECTED,
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val lteSignal: LteSignalData?           = null,
    val quotaPercent: Int                   = 0,
    val hasIntruders: Boolean               = false,
    val error: String?                      = null
)

enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, NO_ROUTER }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val routerRepository: RouterRepository,
    private val deviceRepository: DeviceRepository,
    private val encryptedPrefs: EncryptedPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(30_000L)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Try to get signal — success means router is reachable
                val signalResult = routerRepository.getLteSignal()
                val signal = signalResult.getOrNull()
                val status = if (signalResult.isSuccess) ConnectionStatus.CONNECTED
                             else ConnectionStatus.NO_ROUTER

                val devices = if (signalResult.isSuccess) {
                    deviceRepository.getConnectedDevices().getOrDefault(emptyList())
                } else emptyList()

                val hasIntruders = devices.any { !it.isTrusted }

                _uiState.value = HomeUiState(
                    isLoading        = false,
                    connectionStatus = status,
                    connectedDevices = devices,
                    lteSignal        = signal,
                    hasIntruders     = hasIntruders
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading        = false,
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    error            = e.message
                )
            }
        }
    }

    fun scanForRouters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading        = true,
                connectionStatus = ConnectionStatus.CONNECTING
            )
            refresh()
        }
    }
}
