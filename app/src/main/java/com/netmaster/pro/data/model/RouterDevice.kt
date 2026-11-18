package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RouterDevice(
    val ip: String,
    val port: Int = 80,
    val brand: RouterBrand,
    val model: String = "",
    val firmwareVersion: String = "",
    val macAddress: String = "",
    val capabilities: Set<RouterCapability> = emptySet()
)

enum class RouterBrand {
    GREENPACKET,
    HUAWEI,
    ZTE,
    TPLINK,
    DLINK,
    ASUS,
    MIKROTIK,
    OPENWRT,
    UNKNOWN
}

enum class RouterCapability {
    SMS,
    USSD,
    LTE_SIGNAL,
    BAND_LOCK,
    WIFI_5GHZ,
    GUEST_WIFI,
    QOS,
    PARENTAL_CONTROLS,
    REBOOT,
    CONFIG_BACKUP,
    DSL_STATS,
    PORT_FORWARDING,
    UPNP,
    DNS_CHANGE,
    MAC_FILTER
}
