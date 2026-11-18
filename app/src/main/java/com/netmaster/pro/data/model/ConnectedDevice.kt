package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectedDevice(
    /** MAC address in colon-separated format, e.g. "AA:BB:CC:DD:EE:FF" */
    val mac: String,
    /** Current IP address assigned by DHCP */
    val ip: String,
    /** mDNS / NetBIOS hostname if resolved */
    val hostname: String = "",
    /** OUI vendor name looked up from mac prefix */
    val vendor: String = "",
    /** User-assigned friendly name */
    val nickname: String = "",
    /** Device category for icon / filtering */
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    /** Whether this device is in the user's trusted list */
    val isTrusted: Boolean = false,
    /** Wi-Fi band the device is connected on */
    val band: WifiBand = WifiBand.UNKNOWN,
    /** Wi-Fi RSSI in dBm (negative value) */
    val rssi: Int = 0,
    /** DHCP lease remaining time in seconds (-1 if unknown) */
    val leaseTime: Int = -1,
    /** Epoch millis when the device was first seen */
    val firstSeen: Long = System.currentTimeMillis(),
    /** Epoch millis when the device was last seen active */
    val lastSeen: Long = System.currentTimeMillis()
)

enum class WifiBand {
    BAND_2_4GHZ,
    BAND_5GHZ,
    BAND_6GHZ,
    WIRED,
    UNKNOWN
}
