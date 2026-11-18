package com.netmaster.pro.data.repository

import android.util.Log
import com.netmaster.pro.data.local.TrafficLogDao
import com.netmaster.pro.data.local.TrafficLogEntity
import com.netmaster.pro.data.model.QuotaInfo
import com.netmaster.pro.data.model.TrafficStats
import com.netmaster.pro.domain.repository.RouterRepository
import com.netmaster.pro.domain.repository.SettingsRepository
import com.netmaster.pro.domain.repository.TrafficRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficRepositoryImpl @Inject constructor(
    private val trafficLogDao: TrafficLogDao,
    private val routerRepository: RouterRepository,
    private val settingsRepository: SettingsRepository
) : TrafficRepository {

    companion object {
        private const val TAG = "TrafficRepositoryImpl"
        private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
    }

    private val sessionId = UUID.randomUUID().toString()

    override fun observeTrafficStats(): Flow<TrafficStats> = flow {
        while (true) {
            try {
                val pollInterval = settingsRepository.getTrafficPollIntervalSeconds() * 1000L
                val result = routerRepository.getTrafficStats()
                result.onSuccess { stats ->
                    emit(stats)
                    // Persist to local log
                    trafficLogDao.insertLog(
                        TrafficLogEntity(
                            sessionId = sessionId,
                            rxBytes = stats.rxBytes,
                            txBytes = stats.txBytes,
                            timestamp = stats.timestamp
                        )
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Traffic poll failed: ${e.message}")
                }
                delay(pollInterval.coerceAtLeast(1000L))
            } catch (e: Exception) {
                Log.e(TAG, "observeTrafficStats error", e)
                delay(DEFAULT_POLL_INTERVAL_MS)
            }
        }
    }.distinctUntilChanged()

    override suspend fun getCurrentStats(): Result<TrafficStats> {
        return try {
            routerRepository.getTrafficStats()
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentStats failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getUsageSince(sinceTimestamp: Long): TrafficStats {
        val rxBytes = trafficLogDao.getTotalRxBytesSince(sinceTimestamp)
        val txBytes = trafficLogDao.getTotalTxBytesSince(sinceTimestamp)
        return TrafficStats(
            rxBytes = rxBytes,
            txBytes = txBytes,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
    }

    override suspend fun getDailyAllowanceBytes(): Long {
        val quota = getQuotaInfo() ?: return 0L
        val bundleBytes = (quota.bundleSizeGB * 1_073_741_824).toLong()
        val daysInCycle = getDaysInBillingCycle(quota)
        return if (daysInCycle > 0) bundleBytes / daysInCycle else 0L
    }

    override suspend fun saveQuotaInfo(quotaInfo: QuotaInfo) {
        settingsRepository.setQuotaBundleGb(quotaInfo.bundleSizeGB)
        settingsRepository.setQuotaRenewalDay(quotaInfo.renewalDay)
    }

    override suspend fun getQuotaInfo(): QuotaInfo? {
        val bundleGb = settingsRepository.getQuotaBundleGb()
        val renewalDay = settingsRepository.getQuotaRenewalDay()
        if (bundleGb <= 0.0) return null

        val cycleStart = getCycleStartMillis(renewalDay)
        val usage = getUsageSince(cycleStart)

        return QuotaInfo(
            bundleSizeGB = bundleGb,
            renewalDay = renewalDay,
            startDate = cycleStart,
            currentRxBytes = usage.rxBytes,
            currentTxBytes = usage.txBytes
        )
    }

    override fun observeQuotaInfo(): Flow<QuotaInfo?> = flow {
        while (true) {
            emit(getQuotaInfo())
            delay(60_000L) // refresh every minute
        }
    }

    override suspend fun getQuotaUsedPercent(): Double {
        val quota = getQuotaInfo() ?: return 0.0
        return quota.usedPercent
    }

    override suspend fun pruneOldLogs(olderThanMs: Long) {
        trafficLogDao.deleteOlderThan(olderThanMs)
    }

    override suspend fun resetCycle() {
        val cycleStart = getCycleStartMillis(settingsRepository.getQuotaRenewalDay())
        trafficLogDao.deleteOlderThan(cycleStart)
    }

    private fun getCycleStartMillis(renewalDay: Int): Long {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        if (today < renewalDay) {
            cal.add(Calendar.MONTH, -1)
        }
        cal.set(Calendar.DAY_OF_MONTH, renewalDay)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getDaysInBillingCycle(quota: QuotaInfo): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = quota.startDate
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
