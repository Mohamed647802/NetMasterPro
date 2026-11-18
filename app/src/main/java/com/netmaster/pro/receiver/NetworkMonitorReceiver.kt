package com.netmaster.pro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.netmaster.pro.worker.IntruderDetectionWorker
import com.netmaster.pro.worker.QuotaAlertWorker
import java.util.concurrent.TimeUnit

class NetworkMonitorReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkMonitorReceiver"
        const val WORK_INTRUDER = "intruder_detection_periodic"
        const val WORK_QUOTA    = "quota_alert_periodic"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                scheduleWorkers(context)
            }
        }
    }

    private fun scheduleWorkers(context: Context) {
        val wm = WorkManager.getInstance(context)

        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Intruder detection — every 15 minutes
        val intruderRequest = PeriodicWorkRequestBuilder<IntruderDetectionWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_INTRUDER,
            ExistingPeriodicWorkPolicy.KEEP,
            intruderRequest
        )

        // Quota check — every 1 hour
        val quotaRequest = PeriodicWorkRequestBuilder<QuotaAlertWorker>(
            1, TimeUnit.HOURS
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_QUOTA,
            ExistingPeriodicWorkPolicy.KEEP,
            quotaRequest
        )

        Log.d(TAG, "WorkManager jobs scheduled")
    }
}
