package com.netmaster.pro.domain.repository

import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for connected device operations.
 * Aggregates data from router DHCP, local subnet scan, mDNS, and OUI lookup.
 */
interface DeviceRepository {

    /** Flow of all known connected devices, refreshed on each router poll. */
    fun observeDevices(): Flow<List<ConnectedDevice>>

    /** One-shot fetch of currently connected devices from the router. */
    suspend fun getConnectedDevices(): Result<List<ConnectedDevice>>

    /** Returns a specific device by MAC, or null if not found. */
    suspend fun getDeviceByMac(mac: String): ConnectedDevice?

    /** Persists user-assigned nickname for a device. */
    suspend fun setNickname(mac: String, nickname: String)

    /** Updates the device category for icon/filtering purposes. */
    suspend fun setCategory(mac: String, category: DeviceCategory)

    /** Marks a device as trusted or untrusted. */
    suspend fun setTrusted(mac: String, trusted: Boolean)

    /**
     * Blocks a device at the router level (adds to router's block list).
     * Also records the action locally.
     */
    suspend fun blockDevice(mac: String): Result<Unit>

    /** Unblocks a previously blocked device. */
    suspend fun unblockDevice(mac: String): Result<Unit>

    /**
     * Scans the local subnet for devices not yet reported by the router's DHCP list.
     * Uses ARP/ICMP ping sweep and mDNS discovery.
     */
    suspend fun scanSubnet(networkPrefix: String): List<ConnectedDevice>

    /**
     * Returns devices with unknown category (UNKNOWN) that need classification.
     */
    suspend fun getUnclassifiedDevices(): List<ConnectedDevice>

    /** Clears all locally-stored device history. */
    suspend fun clearHistory()
}
