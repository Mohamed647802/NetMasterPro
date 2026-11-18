package com.netmaster.pro.domain.adapter

import com.netmaster.pro.data.model.*
import kotlinx.serialization.Serializable

// ─── Supporting Data Classes ─────────────────────────────────────────────────

@Serializable
data class WifiSettings(
    val ssid2g: String = "",
    val password2g: String = "",
    val enabled2g: Boolean = true,
    val channel2g: Int = 6,
    val ssid5g: String = "",
    val password5g: String = "",
    val enabled5g: Boolean = true,
    val channel5g: Int = 36,
    val security: String = "WPA2-PSK",
    val bandSteering: Boolean = false
)

@Serializable
data class DslStats(
    val downstreamRate: Int = 0,       // kbps
    val upstreamRate: Int = 0,         // kbps
    val downstreamAttenuation: Float = 0f, // dB
    val upstreamAttenuation: Float = 0f,   // dB
    val downstreamNoiseMargin: Float = 0f, // dB
    val upstreamNoiseMargin: Float = 0f,   // dB
    val lineStatus: String = "",
    val uptime: Long = 0L              // seconds
)

enum class NetworkMode(val displayName: String) {
    AUTO("Auto (LTE preferred)"),
    LTE_ONLY("4G LTE Only"),
    WCDMA_ONLY("3G WCDMA Only"),
    GSM_ONLY("2G GSM Only"),
    LTE_WCDMA("LTE + WCDMA"),
    WCDMA_GSM("WCDMA + GSM"),
    NR_LTE("5G + 4G LTE"),
    NR_ONLY("5G NR Only")
}

enum class LteBand(val frequency: String, val mhz: Int) {
    BAND_1("2100 MHz", 2100),
    BAND_3("1800 MHz", 1800),
    BAND_7("2600 MHz", 2600),
    BAND_8("900 MHz", 900),
    BAND_20("800 MHz", 800),
    BAND_28("700 MHz", 700),
    BAND_38("2600 MHz TDD", 2600),
    BAND_40("2300 MHz TDD", 2300),
    BAND_41("2500 MHz TDD", 2500)
}

enum class MacFilterMode {
    BLACKLIST,
    WHITELIST,
    DISABLED
}

@Serializable
data class QosRule(
    val id: String = "",
    val deviceMac: String,
    val downloadLimitKbps: Int = 0,    // 0 = unlimited
    val uploadLimitKbps: Int = 0,      // 0 = unlimited
    val priority: QosPriority = QosPriority.NORMAL,
    val enabled: Boolean = true
)

enum class QosPriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST
}

@Serializable
data class PortForwardRule(
    val id: String = "",
    val name: String,
    val externalPort: Int,
    val internalPort: Int,
    val internalIp: String,
    val protocol: PortProtocol = PortProtocol.TCP_UDP,
    val enabled: Boolean = true
)

enum class PortProtocol {
    TCP, UDP, TCP_UDP
}

@Serializable
data class ApnProfile(
    val id: String = "",
    val name: String,
    val apn: String,
    val username: String = "",
    val password: String = "",
    val mcc: String = "",
    val mnc: String = "",
    val type: String = "default",
    val protocol: String = "IPv4v6",
    val isActive: Boolean = false
)

@Serializable
data class RouterSecurityInfo(
    val defaultCredentialsChanged: Boolean = false,
    val firewallEnabled: Boolean = false,
    val remoteManagementEnabled: Boolean = false,
    val uPnPEnabled: Boolean = false,
    val wpsEnabled: Boolean = false,
    val telnetEnabled: Boolean = false,
    val sshEnabled: Boolean = false,
    val firmwareVersion: String = "",
    val lastFirmwareCheck: Long = 0L
)
