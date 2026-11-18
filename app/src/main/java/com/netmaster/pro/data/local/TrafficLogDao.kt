package com.netmaster.pro.data.local

import androidx.room.*

@Dao
interface TrafficLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TrafficLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<TrafficLogEntity>)

    @Query("SELECT * FROM traffic_logs WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getLogsForSession(sessionId: String): List<TrafficLogEntity>

    @Query("DELETE FROM traffic_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COALESCE(SUM(rx_bytes), 0) FROM traffic_logs WHERE timestamp >= :timestamp")
    suspend fun getTotalRxBytesSince(timestamp: Long): Long

    @Query("SELECT COALESCE(SUM(tx_bytes), 0) FROM traffic_logs WHERE timestamp >= :timestamp")
    suspend fun getTotalTxBytesSince(timestamp: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(rx_bytes), 0) + COALESCE(SUM(tx_bytes), 0) 
        FROM traffic_logs 
        WHERE timestamp >= :timestamp
        """
    )
    suspend fun getTotalBytesSince(timestamp: Long): Long

    @Query("SELECT * FROM traffic_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 100): List<TrafficLogEntity>

    @Query("DELETE FROM traffic_logs")
    suspend fun deleteAll()
}
