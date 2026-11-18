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

/** Alias: handles BOOT_COMPLETED to restart WorkManager jobs. */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot received — rescheduling workers")
        scheduleWorkers(context)
    }

    private fun scheduleWorkers(context: Context) {
        val wm = WorkManager.getInstance(context)

        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val intruderRequest = PeriodicWorkRequestBuilder<IntruderDetectionWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            NetworkMonitorReceiver.WORK_INTRUDER,
            ExistingPeriodicWorkPolicy.KEEP,
            intruderRequest
        )

        val quotaRequest = PeriodicWorkRequestBuilder<QuotaAlertWorker>(
            1, TimeUnit.HOURS
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            NetworkMonitorReceiver.WORK_QUOTA,
            ExistingPeriodicWorkPolicy.KEEP,
            quotaRequest
        )

        Log.d(TAG, "Workers rescheduled after boot")
    }
}
