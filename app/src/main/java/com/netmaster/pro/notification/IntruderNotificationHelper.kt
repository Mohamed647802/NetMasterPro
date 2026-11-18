package com.netmaster.pro.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.netmaster.pro.R
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntruderNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_INTRUDER   = "intruder_alert"
        const val CHANNEL_ID_QUOTA      = "quota_alert"
        const val CHANNEL_ID_MONITORING = "network_monitoring"

        private const val NOTIF_ID_INTRUDER   = 1001
        private const val NOTIF_ID_QUOTA      = 1002
        const val NOTIF_ID_MONITORING         = 1003
    }

    fun createChannels() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intruderChannel = NotificationChannel(
            CHANNEL_ID_INTRUDER,
            "تنبيهات الأجهزة المجهولة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات عند اكتشاف جهاز غير معروف على الشبكة"
            enableVibration(true)
            enableLights(true)
        }

        val quotaChannel = NotificationChannel(
            CHANNEL_ID_QUOTA,
            "تنبيهات الحصة",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "إشعارات عند بلوغ حد استهلاك البيانات"
        }

        val monitoringChannel = NotificationChannel(
            CHANNEL_ID_MONITORING,
            "مراقبة الشبكة",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعار مستمر يدل على أن مراقبة الشبكة نشطة"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(
            listOf(intruderChannel, quotaChannel, monitoringChannel)
        )
    }

    fun showIntruderAlert(devices: List<ConnectedDevice>) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "devices")
        }
        val launchPending = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INTRUDER)
            .setSmallIcon(R.drawable.ic_notification_intruder)
            .setContentTitle("⚠️ جهاز غير معروف على شبكتك")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(launchPending)
            .addAction(
                R.drawable.ic_notification_intruder,
                "عرض الأجهزة",
                launchPending
            )

        if (devices.size == 1) {
            val d = devices.first()
            val name = d.nickname.ifBlank { d.vendor.ifBlank { d.mac } }
            builder
                .setContentText("$name (${d.ip})")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("الجهاز: $name\nMAC: ${d.mac}\nالعنوان: ${d.ip}")
                )
        } else {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle("${devices.size} أجهزة غير معروفة")
            devices.take(5).forEach { d ->
                val name = d.nickname.ifBlank { d.vendor.ifBlank { d.mac } }
                style.addLine("$name — ${d.ip}")
            }
            builder
                .setContentText("${devices.size} أجهزة غير معروفة على شبكتك")
                .setStyle(style)
                .setNumber(devices.size)
        }

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID_INTRUDER, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    fun showQuotaAlert(percentUsed: Int, remainingGB: Double) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "quota")
        }
        val pending = PendingIntent.getActivity(
            context, 1, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = when {
            percentUsed >= 95 -> "🔴"
            percentUsed >= 80 -> "🟠"
            else              -> "🟡"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_QUOTA)
            .setSmallIcon(R.drawable.ic_notification_quota)
            .setContentTitle("$emoji استهلاك الحصة: $percentUsed%")
            .setContentText("المتبقي: %.2f GB".format(remainingGB))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("لقد استهلكت $percentUsed% من حصتك الشهرية.\nالمتبقي: %.2f GB".format(remainingGB))
            )
            .setPriority(
                if (percentUsed >= 95) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_QUOTA, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    fun showMonitoringNotification(): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 2, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_MONITORING)
            .setSmallIcon(R.drawable.ic_notification_monitoring)
            .setContentTitle("مراقبة الشبكة نشطة")
            .setContentText("NetMaster Pro يراقب الأجهزة المتصلة")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()
    }
}
