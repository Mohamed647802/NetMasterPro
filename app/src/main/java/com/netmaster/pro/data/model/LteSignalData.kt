package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LteSignalData(
    /** Reference Signal Received Power in dBm (typical: -140 to -44) */
    val rsrp: Int = 0,
    /** Reference Signal Received Quality in dB (typical: -19.5 to -3) */
    val rsrq: Float = 0f,
    /** Signal to Interference + Noise Ratio in dB */
    val sinr: Float = 0f,
    /** Received Signal Strength Indicator in dBm */
    val rssi: Int = 0,
    /** Physical Cell Identity (0-503) */
    val pci: Int = 0,
    /** Global Cell ID (28-bit) */
    val cellId: Long = 0L,
    /** eNodeB ID — auto-calculated as cellId / 256 */
    val eNbId: Long = cellId / 256,
    /** E-UTRA Absolute Radio Frequency Channel Number */
    val earfcn: Int = 0,
    /** LTE band number (e.g. 3 = Band 3 at 1800 MHz) */
    val band: Int = 0,
    /** Whether Carrier Aggregation is active */
    val caActive: Boolean = false,
    /** Number of component carriers when CA is active */
    val caComponentCount: Int = 0,
    /** Secondary cell EARFCN list when CA is active */
    val caSecondaryEarfcns: List<Int> = emptyList(),
    /** System timestamp when this reading was taken */
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Signal quality category based on RSRP */
    val signalQuality: SignalQuality
        get() = when {
            rsrp >= -80 -> SignalQuality.EXCELLENT
            rsrp >= -90 -> SignalQuality.GOOD
            rsrp >= -100 -> SignalQuality.FAIR
            rsrp >= -110 -> SignalQuality.POOR
            else -> SignalQuality.NO_SIGNAL
        }
}

enum class SignalQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    NO_SIGNAL
}
