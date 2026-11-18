package com.netmaster.pro.service

import com.netmaster.pro.data.model.ConnectedDevice

/**
 * Thin port used by [IntruderDetectionService] to fetch devices.
 * Bound in DI to [com.netmaster.pro.data.repository.DeviceRepositoryImpl].
 * This interface returns a plain list (not Result) to simplify service logic.
 */
interface DeviceRepositoryProvider {
    suspend fun getConnectedDevices(): List<ConnectedDevice>
}
