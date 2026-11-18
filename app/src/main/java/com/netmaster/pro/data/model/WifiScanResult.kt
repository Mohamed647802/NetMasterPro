package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WifiScanResult(
    /** Network name (may be empty for hidden networks) */
    val ssid: String,
    /** Basic Service Set Identifier — the AP MAC address */
    val bssid: String,
    /** Center frequency in MHz */
    val frequency: Int,
    /** Wi-Fi channel derived from frequency */
    val channel: Int = frequencyToChannel(frequency),
    /** Signal level in dBm (negative) */
    val level: Int,
    /** Wi-Fi generation standard */
    val standard: WifiStandard = WifiStandard.WIFI4,
    /** Channel width in MHz */
    val channelWidth: ChannelWidth = ChannelWidth.WIDTH_20,
    /** Security capabilities string from scan results, e.g. "[WPA2-PSK-CCMP]" */
    val capabilities: String = ""
) {
    val isSecure: Boolean
        get() = capabilities.contains("WPA") || capabilities.contains("WEP")

    val isOpenNetwork: Boolean
        get() = !isSecure
}

enum class WifiStandard(val displayName: String) {
    WIFI4("Wi-Fi 4 (802.11n)"),
    WIFI5("Wi-Fi 5 (802.11ac)"),
    WIFI6("Wi-Fi 6 (802.11ax)"),
    WIFI7("Wi-Fi 7 (802.11be)"),
    LEGACY("Legacy (802.11a/b/g)")
}

enum class ChannelWidth(val mhz: Int) {
    WIDTH_20(20),
    WIDTH_40(40),
    WIDTH_80(80),
    WIDTH_160(160),
    WIDTH_320(320)
}

private fun frequencyToChannel(frequency: Int): Int = when {
    frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
    frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
    frequency in 5955..7115 -> (frequency - 5955) / 5 + 1
    else -> 0
}
