package com.netmaster.pro.presentation.widgets

import android.app.PendingIntent
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.netmaster.pro.ui.MainActivity

class NetworkStatusTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val rssi = wifiInfo.rssi
        val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
        val connected = wifiInfo.networkId != -1 && ssid.isNotBlank()

        tile.apply {
            state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (connected) ssid else "لا اتصال"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when {
                    !connected -> "Wi-Fi غير متصل"
                    rssi >= -60 -> "إشارة ممتازة"
                    rssi >= -70 -> "إشارة جيدة"
                    rssi >= -80 -> "إشارة ضعيفة"
                    else -> "إشارة سيئة"
                }
            }
            updateTile()
        }
    }
}
