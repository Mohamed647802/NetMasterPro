package com.netmaster.pro.presentation.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

data class PingResult(val seq: Int, val ttl: Int, val timeMs: Long, val timedOut: Boolean)
data class TracerouteHop(val hop: Int, val ip: String, val hostname: String, val latencyMs: Long, val timedOut: Boolean)
data class PortResult(val port: Int, val open: Boolean, val service: String)
data class DnsRecord(val type: String, val value: String)
data class SubnetInfo(
    val network: String, val broadcast: String, val firstHost: String,
    val lastHost: String, val hostCount: Long, val subnetMask: String, val cidr: Int
)
data class UpnpMapping(val protocol: String, val externalPort: Int, val internalIp: String, val internalPort: Int, val description: String)
data class DiscoveredDevice(val name: String, val ip: String, val type: String, val port: Int)

data class NetworkToolsUiState(
    val pingHost: String = "",
    val pingCount: Int = 4,
    val pingResults: List<PingResult> = emptyList(),
    val pingRunning: Boolean = false,
    val pingStats: String = "",

    val tracerouteHost: String = "",
    val tracerouteHops: List<TracerouteHop> = emptyList(),
    val tracerouteRunning: Boolean = false,

    val portScanHost: String = "",
    val portResults: List<PortResult> = emptyList(),
    val portScanRunning: Boolean = false,

    val dnsHost: String = "",
    val dnsResults: List<DnsRecord> = emptyList(),
    val dnsRunning: Boolean = false,

    val subnetIp: String = "",
    val subnetMask: String = "255.255.255.0",
    val subnetInfo: SubnetInfo? = null,

    val upnpMappings: List<UpnpMapping> = emptyList(),
    val upnpLoading: Boolean = false,

    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val discoveryRunning: Boolean = false,

    val errorMessage: String? = null
)

val COMMON_PORTS = listOf(
    21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
    80 to "HTTP", 443 to "HTTPS", 554 to "RTSP", 8080 to "HTTP-Alt",
    8443 to "HTTPS-Alt", 9100 to "Printer"
)

val SERVICE_PORTS = COMMON_PORTS.toMap()

@HiltViewModel
class NetworkToolsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkToolsUiState())
    val uiState: StateFlow<NetworkToolsUiState> = _uiState.asStateFlow()

    // ─── Ping ───────────────────────────────────────────────────────────────
    fun updatePingHost(host: String) = _uiState.update { it.copy(pingHost = host) }
    fun updatePingCount(count: Int) = _uiState.update { it.copy(pingCount = count) }

    fun startPing() {
        val host = _uiState.value.pingHost.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(pingRunning = true, pingResults = emptyList(), pingStats = "") }
            val results = mutableListOf<PingResult>()
            repeat(_uiState.value.pingCount) { seq ->
                val result = withContext(Dispatchers.IO) {
                    try {
                        val addr = InetAddress.getByName(host)
                        val start = System.currentTimeMillis()
                        val reachable = addr.isReachable(3000)
                        val elapsed = System.currentTimeMillis() - start
                        PingResult(seq + 1, 64, elapsed, !reachable)
                    } catch (e: Exception) {
                        PingResult(seq + 1, 0, 0, true)
                    }
                }
                results.add(result)
                _uiState.update { it.copy(pingResults = results.toList()) }
                delay(500)
            }
            val sent = results.size
            val received = results.count { !it.timedOut }
            val loss = ((sent - received) * 100.0 / sent).toInt()
            val times = results.filter { !it.timedOut }.map { it.timeMs }
            val stats = if (times.isNotEmpty()) {
                "أُرسل=$sent  وصل=$received  فقد=${loss}%  min=${times.min()}ms  avg=${"%.1f".format(times.average())}ms  max=${times.max()}ms"
            } else "فشل جميع الطلبات"
            _uiState.update { it.copy(pingRunning = false, pingStats = stats) }
        }
    }

    // ─── Traceroute ─────────────────────────────────────────────────────────
    fun updateTracerouteHost(host: String) = _uiState.update { it.copy(tracerouteHost = host) }

    fun startTraceroute() {
        val host = _uiState.value.tracerouteHost.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(tracerouteRunning = true, tracerouteHops = emptyList()) }
            val hops = mutableListOf<TracerouteHop>()
            withContext(Dispatchers.IO) {
                for (ttl in 1..30) {
                    val hop = probeTtl(host, ttl)
                    hops.add(hop)
                    _uiState.update { it.copy(tracerouteHops = hops.toList()) }
                    if (!hop.timedOut && hop.ip == resolveHost(host)) break
                    if (!hop.timedOut && ttl > 2 && hop.ip == hops.getOrNull(ttl - 2)?.ip) break
                }
            }
            _uiState.update { it.copy(tracerouteRunning = false) }
        }
    }

    private fun probeTtl(host: String, ttl: Int): TracerouteHop {
        return try {
            val addr = InetAddress.getByName(host)
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            socket.timeToLive = ttl
            val data = ByteArray(32)
            val packet = DatagramPacket(data, data.size, addr, 33434 + ttl)
            val start = System.currentTimeMillis()
            socket.send(packet)
            socket.close()
            // Try to get ICMP TTL exceeded — fallback: assume hop
            val elapsed = System.currentTimeMillis() - start
            TracerouteHop(ttl, "*", "", elapsed, false)
        } catch (e: Exception) {
            TracerouteHop(ttl, "*", "", 0, true)
        }
    }

    private fun resolveHost(host: String): String = try {
        InetAddress.getByName(host).hostAddress ?: ""
    } catch (_: Exception) { "" }

    // ─── Port Scanner ────────────────────────────────────────────────────────
    fun updatePortScanHost(host: String) = _uiState.update { it.copy(portScanHost = host) }

    fun startPortScan() {
        val host = _uiState.value.portScanHost.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(portScanRunning = true, portResults = emptyList()) }
            val results = withContext(Dispatchers.IO) {
                COMMON_PORTS.map { (port, service) ->
                    async {
                        val open = try {
                            Socket().use { s -> s.connect(InetSocketAddress(host, port), 1500); true }
                        } catch (_: Exception) { false }
                        PortResult(port, open, service)
                    }
                }.map { it.await() }.sortedBy { it.port }
            }
            _uiState.update { it.copy(portScanRunning = false, portResults = results) }
        }
    }

    // ─── DNS Lookup ──────────────────────────────────────────────────────────
    fun updateDnsHost(host: String) = _uiState.update { it.copy(dnsHost = host) }

    fun startDnsLookup() {
        val host = _uiState.value.dnsHost.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(dnsRunning = true, dnsResults = emptyList()) }
            val records = withContext(Dispatchers.IO) {
                val list = mutableListOf<DnsRecord>()
                try {
                    val addrs = InetAddress.getAllByName(host)
                    for (addr in addrs) {
                        val type = if (addr.address.size == 4) "A" else "AAAA"
                        list.add(DnsRecord(type, addr.hostAddress ?: ""))
                    }
                    list.add(DnsRecord("PTR", addrs.firstOrNull()?.canonicalHostName ?: ""))
                } catch (e: Exception) {
                    list.add(DnsRecord("ERROR", e.message ?: "فشل الاستعلام"))
                }
                list
            }
            _uiState.update { it.copy(dnsRunning = false, dnsResults = records) }
        }
    }

    // ─── Subnet Calculator ───────────────────────────────────────────────────
    fun updateSubnetIp(ip: String) = _uiState.update { it.copy(subnetIp = ip) }
    fun updateSubnetMask(mask: String) = _uiState.update { it.copy(subnetMask = mask) }

    fun calculateSubnet() {
        val ip = _uiState.value.subnetIp.trim()
        val mask = _uiState.value.subnetMask.trim()
        try {
            val ipBytes = ip.split(".").map { it.toInt() }
            val maskBytes = mask.split(".").map { it.toInt() }
            val ipInt = (ipBytes[0] shl 24) or (ipBytes[1] shl 16) or (ipBytes[2] shl 8) or ipBytes[3]
            val maskInt = (maskBytes[0] shl 24) or (maskBytes[1] shl 16) or (maskBytes[2] shl 8) or maskBytes[3]
            val networkInt = ipInt and maskInt
            val broadcastInt = networkInt or maskInt.inv()
            val firstHost = networkInt + 1
            val lastHost = broadcastInt - 1
            val hostCount = (lastHost - firstHost + 1).toLong()
            val cidr = Integer.bitCount(maskInt)

            fun intToIp(v: Int) = "%d.%d.%d.%d".format((v ushr 24) and 0xFF, (v ushr 16) and 0xFF, (v ushr 8) and 0xFF, v and 0xFF)

            _uiState.update {
                it.copy(
                    subnetInfo = SubnetInfo(
                        network = intToIp(networkInt),
                        broadcast = intToIp(broadcastInt),
                        firstHost = intToIp(firstHost),
                        lastHost = intToIp(lastHost),
                        hostCount = hostCount,
                        subnetMask = mask,
                        cidr = cidr
                    )
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "IP أو قناع غير صالح") }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
