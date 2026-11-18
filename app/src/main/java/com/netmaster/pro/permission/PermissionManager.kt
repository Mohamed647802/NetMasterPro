package com.netmaster.pro.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionState(
    val locationGranted: Boolean       = false,
    val cameraGranted: Boolean         = false,
    val nfcGranted: Boolean            = false,
    val notificationsGranted: Boolean  = false,
    val nearbyWifiGranted: Boolean     = false
)

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _permissionState = MutableStateFlow(queryPermissions())
    val permissionState: Flow<PermissionState> = _permissionState.asStateFlow()

    fun refresh() {
        _permissionState.value = queryPermissions()
    }

    fun isLocationGranted(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun isCameraGranted(): Boolean =
        isGranted(Manifest.permission.CAMERA)

    fun isNfcGranted(): Boolean =
        isGranted(Manifest.permission.NFC)

    fun isNotificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        else true  // Not required below API 33

    fun isNearbyWifiGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
        else true  // Not required below API 33

    private fun queryPermissions() = PermissionState(
        locationGranted      = isLocationGranted(),
        cameraGranted        = isCameraGranted(),
        nfcGranted           = isNfcGranted(),
        notificationsGranted = isNotificationsGranted(),
        nearbyWifiGranted    = isNearbyWifiGranted()
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    companion object {
        /** All permissions that the app may request at runtime. */
        fun requiredPermissions(): Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }
}
