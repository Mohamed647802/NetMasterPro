package com.netmaster.pro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GuestWifiDisableWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val routerRepository: RouterRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Disable guest Wi-Fi: set enabled=false, preserve any existing SSID/pass
            routerRepository.setGuestWifi(enabled = false, ssid = "", password = "")
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
