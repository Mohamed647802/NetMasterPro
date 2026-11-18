package com.netmaster.pro.presentation.qos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.QosRule
import com.netmaster.pro.domain.adapter.QosPriority
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceQosEntry(
    val mac: String,
    val ip: String,
    val name: String,
    val uploadKbps: Float   = 0f,
    val downloadKbps: Float = 0f,
    val priority: Int       = 2
)

data class QosUiState(
    val entries: List<DeviceQosEntry> = emptyList(),
    val selectedProfile: Int          = -1,
    val isLoading: Boolean            = false,
    val isApplying: Boolean           = false,
    val error: String?                = null
)

@HiltViewModel
class QosViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QosUiState())
    val uiState: StateFlow<QosUiState> = _uiState.asStateFlow()

    init { loadDevices() }

    private fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            deviceRepository.getConnectedDevices().onSuccess { devices ->
                val entries = devices.map { d ->
                    DeviceQosEntry(
                        mac  = d.mac,
                        ip   = d.ip,
                        name = d.nickname.ifBlank { d.vendor.ifBlank { d.hostname } }
                    )
                }
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }.onFailure {
                _uiState.update { s -> s.copy(isLoading = false, error = it.message) }
            }
        }
    }

    fun setUpload(mac: String, kbps: Float) {
        _uiState.update { state ->
            state.copy(entries = state.entries.map {
                if (it.mac == mac) it.copy(uploadKbps = kbps) else it
            })
        }
    }

    fun setDownload(mac: String, kbps: Float) {
        _uiState.update { state ->
            state.copy(entries = state.entries.map {
                if (it.mac == mac) it.copy(downloadKbps = kbps) else it
            })
        }
    }

    fun applyProfile(profile: Int) {
        _uiState.update { state ->
            val updated = state.entries.map { e ->
                when (profile) {
                    0 -> e.copy(uploadKbps = 2048f, downloadKbps = 2048f, priority = 1) // Gaming
                    1 -> e.copy(uploadKbps = 0f,    downloadKbps = 5120f, priority = 2) // Streaming
                    2 -> e.copy(uploadKbps = 512f,  downloadKbps = 1024f, priority = 3) // Data saver
                    else -> e
                }
            }
            state.copy(entries = updated, selectedProfile = profile)
        }
    }

    fun applyQos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            val rules = _uiState.value.entries.map { e ->
                val prio = when (e.priority) {
                    1    -> QosPriority.HIGH
                    3    -> QosPriority.LOW
                    else -> QosPriority.NORMAL
                }
                QosRule(
                    deviceMac        = e.mac,
                    downloadLimitKbps = e.downloadKbps.toInt(),
                    uploadLimitKbps   = e.uploadKbps.toInt(),
                    priority          = prio
                )
            }
            routerRepository.setQos(rules)
            _uiState.update { it.copy(isApplying = false) }
        }
    }
}
