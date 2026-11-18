package com.netmaster.pro.presentation.analyzer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.setWifiChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

data class WifiInfo(
    val ssid: String = "",
    val bssid: String = "",
    val linkSpeed: Int = 0,
    val frequency: Int = 0,
    val standard: String = "",
    val channelWidth: String = "",
    val channel: Int = 0,
    val rssi: Int = 0,
    val localIp: String = "",
    val gateway: String = "",
    val dns1: String = "",
    val dns2: String = ""
)

data class NeighborAp(
    val ssid: String,
    val bssid: String,
    val frequency: Int,
    val channel: Int,
    val rssi: Int,
    val capabilities: String,
    val standard: String
)

data class RoomSignal(
    val zone: Int,         // 0..24 for 5x5 grid
    val rssi: Int,
    val lteRssi: Int
)

data class WifiAnalyzerUiState(
    val wifiInfo: WifiInfo = WifiInfo(),
    val neighbors: List<NeighborAp> = emptyList(),
    val bestChannel2G: Int = 1,
    val bestChannel5G: Int = 36,
    val roomSignals: Map<Int, RoomSignal> = emptyMap(),
    val scanInProgress: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WifiAnalyzerViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _uiState = MutableStateFlow(WifiAnalyzerUiState())
    val uiState: StateFlow<WifiAnalyzerUiState> = _uiState.asStateFlow()

    private var scanReceiver: BroadcastReceiver? = null

    init {
        refreshCurrentWifiInfo()
    }

    fun refreshCurrentWifiInfo() {
        viewModelScope.launch {
            val info = wifiManager.connectionInfo
            val dhcp = wifiManager.dhcpInfo
            val ipInt = dhcp.ipAddress
            val localIp = formatIp(ipInt)
            val gateway = formatIp(dhcp.gateway)
            val dns1 = formatIp(dhcp.dns1)
            val dns2 = formatIp(dhcp.dns2)

            @Suppress("DEPRECATION")
            val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
            val freq = info.frequency
            val channel = frequencyToChannel(freq)
            val capabilities = ""
            val standard = detectWifiStandard(capabilities, freq)

            _uiState.update {
                it.copy(
                    wifiInfo = WifiInfo(
                        ssid = ssid,
                        bssid = info.bssid ?: "",
                        linkSpeed = info.linkSpeed,
                        frequency = freq,
                        standard = standard,
                        channelWidth = channelBandwidthLabel(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 1 else 0),
                        channel = channel,
                        rssi = info.rssi,
                        localIp = localIp,
                        gateway = gateway,
                        dns1 = dns1,
                        dns2 = dns2
                    )
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    fun startScan(context: Context) {
        if (_uiState.value.scanInProgress) return
        _uiState.update { it.copy(scanInProgress = true) }

        if (scanReceiver != null) {
            try { context.unregisterReceiver(scanReceiver) } catch (_: Exception) {}
        }

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try { ctx?.unregisterReceiver(this) } catch (_: Exception) {}
                scanReceiver = null

                val results = wifiManager.scanResults ?: emptyList()
                val neighbors = results.map { r ->
                    val freq = r.frequency
                    val ch = frequencyToChannel(freq)
                    val cap = r.capabilities ?: ""
                    NeighborAp(
                        ssid = r.SSID ?: "",
                        bssid = r.BSSID ?: "",
                        frequency = freq,
                        channel = ch,
                        rssi = r.level,
                        capabilities = cap,
                        standard = detectWifiStandard(cap, freq)
                    )
                }.sortedByDescending { it.rssi }

                val best2G = bestChannel2GHz(neighbors.filter { it.frequency < 3000 })
                val best5G = bestChannel5GHz(neighbors)

                _uiState.update {
                    it.copy(
                        neighbors = neighbors,
                        bestChannel2G = best2G,
                        bestChannel5G = best5G,
                        scanInProgress = false
                    )
                }
            }
        }
        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        @Suppress("DEPRECATION")
        val started = wifiManager.startScan()
        if (!started) {
            // API 28+ returns false but results may still update
            _uiState.update { it.copy(scanInProgress = false) }
        }
    }

    fun recordRoomSignal(zone: Int) {
        viewModelScope.launch {
            val info = wifiManager.connectionInfo
            val rssi = info.rssi
            _uiState.update { state ->
                state.copy(
                    roomSignals = state.roomSignals + (zone to RoomSignal(zone, rssi, -100))
                )
            }
        }
    }

    fun applyBestChannel(is2GHz: Boolean) {
        viewModelScope.launch {
            val channel = if (is2GHz) _uiState.value.bestChannel2G else _uiState.value.bestChannel5G
            try {
                routerAdapter.setWifiChannel(channel, is2GHz)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "فشل تطبيق القناة: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        scanReceiver?.let {
            try { getApplication<Application>().unregisterReceiver(it) } catch (_: Exception) {}
        }
    }

    private fun formatIp(ipInt: Int): String {
        val bytes = ByteArray(4) { i -> ((ipInt shr (i * 8)) and 0xFF).toByte() }
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }
}
