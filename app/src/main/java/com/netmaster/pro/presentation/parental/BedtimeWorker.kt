package com.netmaster.pro.presentation.parental

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.pauseAllDevices
import com.netmaster.pro.domain.adapter.resumeAllDevices
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

@HiltWorker
class BedtimeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val routerAdapter: RouterAdapter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val startHour = inputData.getInt("start_hour", 22)
            val startMinute = inputData.getInt("start_minute", 0)
            val endHour = inputData.getInt("end_hour", 7)
            val endMinute = inputData.getInt("end_minute", 0)
            val now = LocalTime.now()
            val start = LocalTime.of(startHour, startMinute)
            val end = LocalTime.of(endHour, endMinute)

            val shouldPause = if (start.isBefore(end)) {
                now.isAfter(start) && now.isBefore(end)
            } else {
                now.isAfter(start) || now.isBefore(end)
            }

            if (shouldPause) {
                routerAdapter.pauseAllDevices()
            } else {
                routerAdapter.resumeAllDevices()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
