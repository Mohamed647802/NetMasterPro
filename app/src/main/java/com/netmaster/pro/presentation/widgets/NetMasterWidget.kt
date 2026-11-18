package com.netmaster.pro.presentation.widgets

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.material3.ColorProviders
import com.netmaster.pro.MainActivity

class NetMasterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Collect data
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "غير متصل"
        val rssi = wifiInfo.rssi
        val signalStrength = when {
            rssi >= -60 -> "●●●●●"
            rssi >= -70 -> "●●●●○"
            rssi >= -80 -> "●●●○○"
            rssi >= -90 -> "●●○○○"
            else -> "●○○○○"
        }

        provideContent {
            WidgetContent(
                ssid = ssid,
                signalBars = signalStrength,
                rssi = rssi,
                connectedDevices = 0,  // updated via WorkManager
                remainingGb = 0.0
            )
        }
    }
}

@Composable
private fun WidgetContent(
    ssid: String,
    signalBars: String,
    rssi: Int,
    connectedDevices: Int,
    remainingGb: Double
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        // Header
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                "🌐 NetMaster",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.height(8.dp))

        // Signal row
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                signalBars,
                style = TextStyle(
                    color = when {
                        rssi >= -60 -> ColorProvider(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        rssi >= -70 -> ColorProvider(androidx.compose.ui.graphics.Color(0xFFFF9800))
                        else -> ColorProvider(androidx.compose.ui.graphics.Color(0xFFE53935))
                    },
                    fontSize = 12.sp
                )
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                ssid,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(4.dp))

        // Devices row
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                "📱 أجهزة متصلة: $connectedDevices",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp)
            )
        }
        Spacer(GlanceModifier.height(4.dp))

        // Quota row
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                "📊 الباقة المتبقية: ${"%.1f".format(remainingGb)} GB",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp)
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        // Quick action buttons
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                "🔲 QR",
                modifier = GlanceModifier
                    .clickable(actionRunCallback<OpenQrAction>())
                    .padding(4.dp),
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.primary)
            )
            Spacer(GlanceModifier.width(12.dp))
            Text(
                "🔄 إعادة",
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RebootAction>())
                    .padding(4.dp),
                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.error)
            )
        }
    }
}

class OpenQrAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_OPEN_QR"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class RebootAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_REBOOT_ROUTER"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class NetMasterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NetMasterWidget()
}
