package com.netmaster.pro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduledRebootWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val routerRepository: RouterRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            routerRepository.reboot()
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
