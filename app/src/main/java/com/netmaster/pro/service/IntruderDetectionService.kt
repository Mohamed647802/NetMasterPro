package com.netmaster.pro.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.notification.IntruderNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntruderDetectionService : Service() {

    @Inject
    lateinit var notificationHelper: IntruderNotificationHelper

    @Inject
    lateinit var deviceRepository: DeviceRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** MACs we have already alerted for in this session. */
    private val alertedMacs = mutableSetOf<String>()

    companion object {
        private const val TAG               = "IntruderService"
        private const val POLL_INTERVAL_MS  = 30_000L
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            IntruderNotificationHelper.NOTIF_ID_MONITORING,
            notificationHelper.showMonitoringNotification()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        serviceScope.launch {
            while (isActive) {
                try {
                    checkIntruders()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    private suspend fun checkIntruders() {
        val result = deviceRepository.getConnectedDevices()
        result.onSuccess { devices ->
            val newIntruders: List<ConnectedDevice> = devices.filter { device ->
                !device.isTrusted && device.mac !in alertedMacs
            }
            if (newIntruders.isNotEmpty()) {
                newIntruders.forEach { alertedMacs.add(it.mac) }
                notificationHelper.showIntruderAlert(newIntruders)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
