package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeedTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    /** Bufferbloat grade from A+ (best) to F (worst) */
    val bufferbloatGrade: BufferbloatGrade,
    val testType: SpeedTestType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BufferbloatGrade(val displayLabel: String) {
    A_PLUS("A+"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    F("F")
}

enum class SpeedTestType {
    /** Test between this device and the router (LAN throughput) */
    LAN_TO_ROUTER,
    /** Test between router and internet (WAN throughput via router's speedtest) */
    ROUTER_TO_INTERNET
}
