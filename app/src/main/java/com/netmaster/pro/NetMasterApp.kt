package com.netmaster.pro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import com.netmaster.pro.notification.IntruderNotificationHelper
import javax.inject.Inject

@HiltAndroidApp
class NetMasterApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: IntruderNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
