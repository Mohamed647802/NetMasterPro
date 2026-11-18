package com.netmaster.pro.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: RouterProfileEntity)

    @Query("SELECT * FROM router_profiles WHERE ip = :ip LIMIT 1")
    suspend fun getByIp(ip: String): RouterProfileEntity?

    @Query("SELECT * FROM router_profiles ORDER BY last_connected DESC")
    suspend fun getAll(): List<RouterProfileEntity>

    @Query("SELECT * FROM router_profiles ORDER BY last_connected DESC")
    fun observeAll(): Flow<List<RouterProfileEntity>>

    @Query("DELETE FROM router_profiles WHERE ip = :ip")
    suspend fun delete(ip: String)

    @Query("SELECT * FROM router_profiles WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): RouterProfileEntity?

    @Transaction
    suspend fun setDefault(ip: String) {
        clearDefault()
        markDefault(ip)
    }

    @Query("UPDATE router_profiles SET is_default = 0")
    suspend fun clearDefault()

    @Query("UPDATE router_profiles SET is_default = 1 WHERE ip = :ip")
    suspend fun markDefault(ip: String)

    @Query("UPDATE router_profiles SET last_connected = :timestamp WHERE ip = :ip")
    suspend fun updateLastConnected(ip: String, timestamp: Long = System.currentTimeMillis())
}
