package com.netmaster.pro.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedDeviceDao {

    @Query("SELECT * FROM connected_devices ORDER BY last_seen DESC")
    fun getAll(): Flow<List<ConnectedDeviceEntity>>

    @Query("SELECT * FROM connected_devices ORDER BY last_seen DESC")
    suspend fun getAllOnce(): List<ConnectedDeviceEntity>

    @Query("SELECT * FROM connected_devices WHERE mac = :mac LIMIT 1")
    suspend fun getByMac(mac: String): ConnectedDeviceEntity?

    @Query("SELECT * FROM connected_devices WHERE mac = :mac LIMIT 1")
    fun observeByMac(mac: String): Flow<ConnectedDeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: ConnectedDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<ConnectedDeviceEntity>)

    @Query("DELETE FROM connected_devices WHERE mac = :mac")
    suspend fun delete(mac: String)

    @Query("DELETE FROM connected_devices")
    suspend fun deleteAll()

    /** Returns devices where nickname is empty and category is UNKNOWN */
    @Query("SELECT * FROM connected_devices WHERE nickname = '' AND category = 'UNKNOWN'")
    suspend fun getUnknownDevices(): List<ConnectedDeviceEntity>

    /** Returns all historical snapshots for a device ordered by last_seen */
    @Query("SELECT * FROM connected_devices WHERE mac = :mac ORDER BY last_seen DESC")
    suspend fun getDeviceHistory(mac: String): List<ConnectedDeviceEntity>

    @Query("UPDATE connected_devices SET nickname = :nickname WHERE mac = :mac")
    suspend fun updateNickname(mac: String, nickname: String)

    @Query("UPDATE connected_devices SET category = :category WHERE mac = :mac")
    suspend fun updateCategory(mac: String, category: String)

    @Query("UPDATE connected_devices SET is_trusted = :trusted WHERE mac = :mac")
    suspend fun setTrusted(mac: String, trusted: Boolean)
}
