package com.netmaster.pro.data.repository

import android.util.Log
import com.netmaster.pro.data.local.ConnectedDeviceDao
import com.netmaster.pro.data.local.ConnectedDeviceEntity
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.data.model.WifiBand
import com.netmaster.pro.data.network.OuiDatabase
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.domain.repository.RouterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val connectedDeviceDao: ConnectedDeviceDao,
    private val routerRepository: RouterRepository,
    private val ouiDatabase: OuiDatabase
) : DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepositoryImpl"
    }

    override fun observeDevices(): Flow<List<ConnectedDevice>> {
        return connectedDeviceDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val result = routerRepository.getConnectedDevices()
            result.onSuccess { devices ->
                // Merge with existing local data (preserve nicknames/categories)
                devices.forEach { device ->
                    val existing = connectedDeviceDao.getByMac(device.mac)
                    val entity = device.toEntity().copy(
                        nickname = existing?.nickname ?: "",
                        category = existing?.category ?: DeviceCategory.UNKNOWN.name,
                        isTrusted = existing?.isTrusted ?: false,
                        vendor = ouiDatabase.lookup(device.mac) ?: device.vendor,
                        firstSeen = existing?.firstSeen ?: System.currentTimeMillis()
                    )
                    connectedDeviceDao.upsert(entity)
                }
            }
            // Return enriched list from local DB for consistency
            val localDevices = connectedDeviceDao.getAllOnce().map { it.toDomain() }
            Result.success(localDevices)
        } catch (e: Exception) {
            Log.e(TAG, "getConnectedDevices failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getDeviceByMac(mac: String): ConnectedDevice? {
        return connectedDeviceDao.getByMac(mac)?.toDomain()
    }

    override suspend fun setNickname(mac: String, nickname: String) {
        connectedDeviceDao.updateNickname(mac, nickname)
    }

    override suspend fun setCategory(mac: String, category: DeviceCategory) {
        connectedDeviceDao.updateCategory(mac, category.name)
    }

    override suspend fun setTrusted(mac: String, trusted: Boolean) {
        connectedDeviceDao.setTrusted(mac, trusted)
    }

    override suspend fun blockDevice(mac: String): Result<Unit> {
        return routerRepository.blockDevice(mac)
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> {
        return routerRepository.unblockDevice(mac)
    }

    override suspend fun scanSubnet(networkPrefix: String): List<ConnectedDevice> {
        // Simplified subnet scan: ping sweep of .1 to .254
        // A full implementation would use ARP or ICMP via native socket,
        // but that requires root or CAP_NET_RAW. Here we return existing DB records
        // as a base and let the router DHCP list be the primary source.
        Log.d(TAG, "Subnet scan for $networkPrefix (using local DB)")
        return connectedDeviceDao.getAllOnce().map { it.toDomain() }
    }

    override suspend fun getUnclassifiedDevices(): List<ConnectedDevice> {
        return connectedDeviceDao.getUnknownDevices().map { it.toDomain() }
    }

    override suspend fun clearHistory() {
        connectedDeviceDao.deleteAll()
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    private fun ConnectedDeviceEntity.toDomain() = ConnectedDevice(
        mac = mac,
        ip = ip,
        hostname = hostname,
        vendor = vendor,
        nickname = nickname,
        category = DeviceCategory.entries.firstOrNull { it.name == category } ?: DeviceCategory.UNKNOWN,
        isTrusted = isTrusted,
        band = WifiBand.entries.firstOrNull { it.name == band } ?: WifiBand.UNKNOWN,
        rssi = rssi,
        leaseTime = leaseTime,
        firstSeen = firstSeen,
        lastSeen = lastSeen
    )

    private fun ConnectedDevice.toEntity() = ConnectedDeviceEntity(
        mac = mac,
        ip = ip,
        hostname = hostname,
        vendor = vendor,
        nickname = nickname,
        category = category.name,
        isTrusted = isTrusted,
        band = band.name,
        rssi = rssi,
        leaseTime = leaseTime,
        firstSeen = firstSeen,
        lastSeen = lastSeen
    )
}
