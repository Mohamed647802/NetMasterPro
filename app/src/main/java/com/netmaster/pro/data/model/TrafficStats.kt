package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TrafficStats(
    /** Bytes received since last counter reset */
    val rxBytes: Long,
    /** Bytes transmitted since last counter reset */
    val txBytes: Long,
    /** Epoch millis when this snapshot was taken */
    val timestamp: Long = System.currentTimeMillis(),
    /** Session identifier linking multiple snapshots into one measurement session */
    val sessionId: String = ""
) {
    val totalBytes: Long get() = rxBytes + txBytes

    val rxMbps: Double get() = rxBytes / 1_048_576.0
    val txMbps: Double get() = txBytes / 1_048_576.0
}
