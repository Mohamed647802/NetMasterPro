package com.netmaster.pro.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.notification.IntruderNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanState { IDLE, SCANNING, DONE, ERROR }

enum class DeviceFilter { ALL, TRUSTED, STRANGER, BLOCKED }

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val wakeOnLanHelper: WakeOnLanHelper,
    private val subnetScanner: SubnetScanner,
    private val notificationHelper: IntruderNotificationHelper
) : ViewModel() {

    private val _allDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    private val _filterState = MutableStateFlow(DeviceFilter.ALL)
    val filterState: StateFlow<DeviceFilter> = _filterState.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    val selectedDevice: StateFlow<ConnectedDevice?> = _selectedDevice.asStateFlow()

    private val _deviceHistory = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val deviceHistory: StateFlow<List<ConnectedDevice>> = _deviceHistory.asStateFlow()

    private val _wolStatus = MutableStateFlow<String?>(null)
    val wolStatus: StateFlow<String?> = _wolStatus.asStateFlow()

    val devices: StateFlow<List<ConnectedDevice>>
        get() = _allDevices

    private var autoScanJob: Job? = null
    private val _knownMacs = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            deviceRepository.observeDevices().collect { devices ->
                // Check for strangers
                devices.filter { !it.isTrusted && it.mac !in _knownMacs }.forEach { stranger ->
                    notificationHelper.showIntruderAlert(listOf(stranger))
                }
                _knownMacs.addAll(devices.map { it.mac })
                _allDevices.value = devices
            }
        }
        refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _scanState.value = ScanState.SCANNING
            deviceRepository.getConnectedDevices().fold(
                onSuccess = {
                    _allDevices.value = it
                    _scanState.value = ScanState.DONE
                },
                onFailure = { _scanState.value = ScanState.ERROR }
            )
        }
    }

    fun startAutoScan(intervalSeconds: Int = 30) {
        autoScanJob?.cancel()
        autoScanJob = viewModelScope.launch {
            while (true) {
                refreshDevices()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stopAutoScan() {
        autoScanJob?.cancel()
        autoScanJob = null
    }

    fun updateDevice(mac: String, nickname: String, category: DeviceCategory, isTrusted: Boolean) {
        viewModelScope.launch {
            deviceRepository.setNickname(mac, nickname)
            deviceRepository.setCategory(mac, category)
            deviceRepository.setTrusted(mac, isTrusted)
            refreshDevices()
        }
    }

    fun blockDevice(mac: String) {
        viewModelScope.launch {
            deviceRepository.blockDevice(mac)
            refreshDevices()
        }
    }

    fun unblockDevice(mac: String) {
        viewModelScope.launch {
            deviceRepository.unblockDevice(mac)
            refreshDevices()
        }
    }

    fun wakeOnLan(mac: String, broadcastIp: String) {
        viewModelScope.launch {
            _wolStatus.value = null
            try {
                wakeOnLanHelper.sendMagicPacket(mac, broadcastIp)
                _wolStatus.value = "WoL packet sent ✓"
            } catch (e: Exception) {
                _wolStatus.value = "Failed: ${e.message}"
            }
        }
    }

    fun setFilter(filter: DeviceFilter) {
        _filterState.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDevice(device: ConnectedDevice?) {
        _selectedDevice.value = device
        if (device != null) {
            viewModelScope.launch {
                // Use the local DB history via getDeviceByMac or scan subnet
                val history = deviceRepository.getConnectedDevices().getOrDefault(emptyList())
                    .filter { it.mac == device.mac }
                _deviceHistory.value = history
            }
        }
    }

    fun clearWolStatus() {
        _wolStatus.value = null
    }

    fun filteredDevices(filter: DeviceFilter, query: String): List<ConnectedDevice> {
        val base = _allDevices.value
        val filtered = when (filter) {
            DeviceFilter.ALL -> base
            DeviceFilter.TRUSTED -> base.filter { it.isTrusted }
            DeviceFilter.STRANGER -> base.filter { !it.isTrusted }
            DeviceFilter.BLOCKED -> emptyList()
        }
        return if (query.isBlank()) filtered
        else filtered.filter { d ->
            d.nickname.contains(query, ignoreCase = true) ||
                d.mac.contains(query, ignoreCase = true) ||
                d.ip.contains(query, ignoreCase = true) ||
                d.hostname.contains(query, ignoreCase = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScan()
    }
}
