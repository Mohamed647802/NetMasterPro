package com.netmaster.pro.presentation.signal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.local.SignalSnapshotDao
import com.netmaster.pro.data.local.SignalSnapshotEntity
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.domain.adapter.LteBand
import com.netmaster.pro.domain.adapter.NetworkMode
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SignalUiState {
    object Loading : SignalUiState()
    data class Success(val signal: LteSignalData, val advice: String) : SignalUiState()
    data class Error(val message: String) : SignalUiState()
    object NotSupported : SignalUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class SignalViewModel @Inject constructor(
    private val routerRepository: RouterRepository,
    private val signalSnapshotDao: SignalSnapshotDao,
    private val audioManager: AudioPlacementAssistant
) : ViewModel() {

    private val _signalState = MutableStateFlow<SignalUiState>(SignalUiState.Loading)
    val signalState: StateFlow<SignalUiState> = _signalState.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _placementAssistMode = MutableStateFlow(false)
    val placementAssistMode: StateFlow<Boolean> = _placementAssistMode.asStateFlow()

    private val _selectedBands = MutableStateFlow<Set<LteBand>>(emptySet())
    val selectedBands: StateFlow<Set<LteBand>> = _selectedBands.asStateFlow()

    private val _networkMode = MutableStateFlow(NetworkMode.LTE_ONLY)
    val networkMode: StateFlow<NetworkMode> = _networkMode.asStateFlow()

    private var pollingJob: Job? = null

    init {
        refreshOnce()
    }

    fun startSignalPolling() {
        if (_isPolling.value) return
        _isPolling.value = true
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchAndUpdateSignal()
                delay(2_000L)
            }
        }
    }

    fun stopSignalPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
    }

    fun togglePlacementAssist() {
        val newState = !_placementAssistMode.value
        _placementAssistMode.value = newState
        if (!newState) {
            audioManager.stop()
        } else {
            val current = (_signalState.value as? SignalUiState.Success)?.signal
            if (current != null) {
                audioManager.start(current.rsrp, current.sinr)
            }
        }
    }

    fun refreshOnce() {
        viewModelScope.launch { fetchAndUpdateSignal() }
    }

    fun setNetworkMode(mode: NetworkMode) {
        _networkMode.value = mode
        viewModelScope.launch {
            routerRepository.setNetworkMode(mode)
        }
    }

    fun setBandSelection(bands: List<LteBand>) {
        _selectedBands.value = bands.toSet()
    }

    fun lockBands(bands: List<LteBand>) {
        viewModelScope.launch {
            routerRepository.setBandSelection(bands)
        }
    }

    private suspend fun fetchAndUpdateSignal() {
        routerRepository.getLteSignal().fold(
            onSuccess = { signal ->
                val advice = buildAdvice(signal)
                _signalState.value = SignalUiState.Success(signal, advice)
                // Persist snapshot
                signalSnapshotDao.insertSnapshot(signal.toEntity())
                // Update audio if placement assist is active
                if (_placementAssistMode.value) {
                    audioManager.updateSignal(signal.rsrp, signal.sinr)
                }
            },
            onFailure = { error ->
                _signalState.value = SignalUiState.Error(error.message ?: "Unknown error")
            }
        )
    }

    private fun buildAdvice(signal: LteSignalData): String {
        val interferenceWarning = if (signal.sinr < 0f) {
            "\nالتشويش مرتفع — ابتعد عن الأجهزة الكهربائية"
        } else ""
        val base = when {
            signal.rsrp > -80 -> "إشارة ممتازة 🟢 — الموقع مثالي"
            signal.rsrp >= -90 -> "إشارة جيدة 🟡 — يمكن التحسين قليلاً"
            signal.rsrp >= -100 -> "إشارة متوسطة 🟠 — جرّب نافذة أخرى"
            else -> "إشارة ضعيفة 🔴 — حرّك الراوتر باتجاه نافذة أو سطح مرتفع"
        }
        return base + interferenceWarning
    }

    override fun onCleared() {
        super.onCleared()
        stopSignalPolling()
        audioManager.stop()
    }
}

private fun LteSignalData.toEntity() = SignalSnapshotEntity(
    rsrp = rsrp,
    rsrq = rsrq,
    sinr = sinr,
    rssi = rssi,
    pci = pci,
    cellId = cellId,
    eNbId = eNbId,
    earfcn = earfcn,
    band = band,
    caActive = caActive,
    timestamp = timestamp
)
