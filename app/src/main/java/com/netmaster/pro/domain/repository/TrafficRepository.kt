package com.netmaster.pro.domain.repository

import com.netmaster.pro.data.model.QuotaInfo
import com.netmaster.pro.data.model.TrafficStats
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for traffic statistics and quota management.
 */
interface TrafficRepository {

    /** Flow of live traffic stats, polling the router at regular intervals. */
    fun observeTrafficStats(): Flow<TrafficStats>

    /** One-shot fetch of current traffic counters from the router. */
    suspend fun getCurrentStats(): Result<TrafficStats>

    /**
     * Returns aggregated traffic usage since [sinceTimestamp] (epoch millis).
     * Computed from locally stored [TrafficLogEntity] records.
     */
    suspend fun getUsageSince(sinceTimestamp: Long): TrafficStats

    /**
     * Returns the daily traffic allowance in bytes based on [QuotaInfo].
     * Calculated as: bundleSizeBytes / daysInBillingCycle.
     */
    suspend fun getDailyAllowanceBytes(): Long

    /** Saves or updates the user's quota configuration. */
    suspend fun saveQuotaInfo(quotaInfo: QuotaInfo)

    /** Returns the current quota configuration, or null if not set. */
    suspend fun getQuotaInfo(): QuotaInfo?

    /** Observes quota info changes in real-time. */
    fun observeQuotaInfo(): Flow<QuotaInfo?>

    /**
     * Computes what percentage of the monthly quota has been used.
     * Returns 0.0 if no quota is configured.
     */
    suspend fun getQuotaUsedPercent(): Double

    /** Purges traffic log entries older than [olderThanMs] epoch millis. */
    suspend fun pruneOldLogs(olderThanMs: Long)

    /** Resets usage counters for a new billing cycle. */
    suspend fun resetCycle()
}
