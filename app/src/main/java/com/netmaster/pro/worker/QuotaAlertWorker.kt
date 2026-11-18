package com.netmaster.pro.worker

import android.content.Context
import android.net.TrafficStats
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netmaster.pro.notification.IntruderNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class QuotaAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: IntruderNotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private val THRESHOLDS = listOf(50, 80, 95)
        private const val PREFS_NAME   = "quota_worker_prefs"
        private const val KEY_BUNDLE_GB = "bundle_size_gb"
        private const val KEY_ALERTED  = "alerted_thresholds"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val bundleSizeGB = prefs.getFloat(KEY_BUNDLE_GB, 0f).toDouble()
            if (bundleSizeGB <= 0.0) return@withContext Result.success()

            val bundleSizeBytes = (bundleSizeGB * 1_073_741_824).toLong()
            val rxBytes = TrafficStats.getMobileRxBytes()
            val txBytes = TrafficStats.getMobileTxBytes()
            if (rxBytes == TrafficStats.UNSUPPORTED.toLong() ||
                txBytes == TrafficStats.UNSUPPORTED.toLong()
            ) {
                return@withContext Result.success()
            }

            val totalUsed = rxBytes + txBytes
            val percentUsed = if (bundleSizeBytes > 0)
                (totalUsed.toDouble() / bundleSizeBytes * 100.0).toInt()
            else 0
            val remainingGB = ((bundleSizeBytes - totalUsed).coerceAtLeast(0L))
                .toDouble() / 1_073_741_824.0

            val alertedSet = prefs.getStringSet(KEY_ALERTED, emptySet())!!.toMutableSet()

            for (threshold in THRESHOLDS.sortedDescending()) {
                val key = threshold.toString()
                if (percentUsed >= threshold && key !in alertedSet) {
                    notificationHelper.showQuotaAlert(percentUsed, remainingGB)
                    alertedSet.add(key)
                    prefs.edit().putStringSet(KEY_ALERTED, alertedSet).apply()
                    break
                }
            }

            // Reset alerts if usage drops below 50% (new billing cycle)
            if (percentUsed < 50 && alertedSet.isNotEmpty()) {
                prefs.edit().putStringSet(KEY_ALERTED, emptySet()).apply()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
