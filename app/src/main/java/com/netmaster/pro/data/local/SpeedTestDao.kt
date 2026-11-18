package com.netmaster.pro.data.local

import androidx.room.*

@Dao
interface SpeedTestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: SpeedTestEntity): Long

    @Query("SELECT * FROM speed_tests ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getHistory(limit: Int = 50): List<SpeedTestEntity>

    @Query("SELECT * FROM speed_tests ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): SpeedTestEntity?

    @Query("SELECT AVG(download_mbps) FROM speed_tests WHERE timestamp >= :since")
    suspend fun getAverageDownload(since: Long): Double?

    @Query("SELECT AVG(upload_mbps) FROM speed_tests WHERE timestamp >= :since")
    suspend fun getAverageUpload(since: Long): Double?

    @Query("DELETE FROM speed_tests WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM speed_tests")
    suspend fun deleteAll()
}
