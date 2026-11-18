package com.netmaster.pro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.notification.IntruderNotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IntruderDetectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val notificationHelper: IntruderNotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = deviceRepository.getConnectedDevices()
            result.fold(
                onSuccess = { devices ->
                    val unknownDevices: List<ConnectedDevice> = devices.filter { !it.isTrusted }
                    if (unknownDevices.isNotEmpty()) {
                        notificationHelper.showIntruderAlert(unknownDevices)
                    }
                    Result.success()
                },
                onFailure = { Result.retry() }
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
