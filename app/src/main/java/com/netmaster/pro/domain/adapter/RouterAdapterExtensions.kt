package com.netmaster.pro.domain.adapter

import com.netmaster.pro.data.model.*

/**
 * Extension functions that unwrap Result<T> from RouterAdapter
 * and bridge field name differences for presentation layer use.
 */

suspend fun RouterAdapter.getConnectedDevicesList(): List<ConnectedDevice> =
    getConnectedDevices().getOrDefault(emptyList())

suspend fun RouterAdapter.getTrafficStatsData(): TrafficStats =
    getTrafficStats().getOrDefault(TrafficStats(rxBytes = 0L, txBytes = 0L))

suspend fun RouterAdapter.getSignalInfo(): LteSignalData =
    getLteSignal().getOrDefault(LteSignalData())

suspend fun RouterAdapter.getWirelessInfo(): WirelessInfoCompat {
    val security = getWifiSettings().getOrDefault(WifiSettings())
    val secInfo = getSecurityInfo().getOrDefault(RouterSecurityInfo())
    return WirelessInfoCompat(
        security = security.security,
        wpsEnabled = secInfo.wpsEnabled
    )
}

suspend fun RouterAdapter.getDslStatsData(): DslStatsCompat {
    val stats = getDslStats().getOrDefault(DslStats())
    return DslStatsCompat(
        snrDownDb = stats.downstreamNoiseMargin.toDouble(),
        snrUpDb = stats.upstreamNoiseMargin.toDouble(),
        attenuationDownDb = stats.downstreamAttenuation.toDouble(),
        attenuationUpDb = stats.upstreamAttenuation.toDouble(),
        attainableDownKbps = stats.downstreamRate.toLong(),
        attainableUpKbps = stats.upstreamRate.toLong(),
        crcErrorsDown = 0L,
        crcErrorsUp = 0L,
        lineMode = stats.lineStatus,
        syncDownKbps = stats.downstreamRate.toLong(),
        syncUpKbps = stats.upstreamRate.toLong()
    )
}

suspend fun RouterAdapter.getUpnpMappings(): List<PortForwardRule> =
    getPortForwardRules().getOrDefault(emptyList())

suspend fun RouterAdapter.setDnsServers(primary: String, secondary: String) =
    setDns(primary, secondary).getOrDefault(Unit)

suspend fun RouterAdapter.setWifiChannel(channel: Int, is2GHz: Boolean) {
    val current = getWifiSettings().getOrDefault(WifiSettings())
    val updated = if (is2GHz) current.copy(channel2g = channel) else current.copy(channel5g = channel)
    setWifiSettings(updated)
}

suspend fun RouterAdapter.pauseDevice(mac: String, minutes: Int) = blockDevice(mac).getOrDefault(Unit)
suspend fun RouterAdapter.resumeDevice(mac: String) = unblockDevice(mac).getOrDefault(Unit)
suspend fun RouterAdapter.pauseAllDevices() { /* no-op — requires per-device block */ }
suspend fun RouterAdapter.resumeAllDevices() { /* no-op */ }
suspend fun RouterAdapter.getBlockedDomains(): List<String> = emptyList()
suspend fun RouterAdapter.setBlockedDomains(domains: List<String>) { /* DNS-based blocking */ }

/** Compat model for wireless info */
data class WirelessInfoCompat(
    val security: String,
    val wpsEnabled: Boolean
)

/** Compat model for DSL stats matching what DslAnalyzerViewModel expects */
data class DslStatsCompat(
    val snrDownDb: Double,
    val snrUpDb: Double,
    val attenuationDownDb: Double,
    val attenuationUpDb: Double,
    val attainableDownKbps: Long,
    val attainableUpKbps: Long,
    val crcErrorsDown: Long,
    val crcErrorsUp: Long,
    val lineMode: String,
    val syncDownKbps: Long,
    val syncUpKbps: Long
)
