package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QuotaInfo(
    /** Total bundle size in gigabytes */
    val bundleSizeGB: Double,
    /** Day of month when bundle renews (1-31) */
    val renewalDay: Int,
    /** Epoch millis of the billing cycle start */
    val startDate: Long,
    /** Bytes received since cycle start */
    val currentRxBytes: Long = 0L,
    /** Bytes transmitted since cycle start */
    val currentTxBytes: Long = 0L,
    /** Percentage thresholds that trigger alerts */
    val alertThresholds: List<Int> = listOf(50, 80, 95)
) {
    val totalUsedBytes: Long get() = currentRxBytes + currentTxBytes
    val bundleSizeBytes: Long get() = (bundleSizeGB * 1_073_741_824).toLong()
    val usedPercent: Double
        get() = if (bundleSizeBytes > 0) totalUsedBytes.toDouble() / bundleSizeBytes * 100.0 else 0.0
    val remainingBytes: Long get() = (bundleSizeBytes - totalUsedBytes).coerceAtLeast(0L)
}
