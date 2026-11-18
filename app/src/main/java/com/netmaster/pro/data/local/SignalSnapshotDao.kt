package com.netmaster.pro.data.local

import androidx.room.*

@Dao
interface SignalSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SignalSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<SignalSnapshotEntity>)

    @Query("SELECT * FROM signal_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 1): List<SignalSnapshotEntity>

    @Query(
        """
        SELECT * FROM signal_snapshots 
        WHERE timestamp BETWEEN :from AND :to 
        ORDER BY timestamp ASC
        """
    )
    suspend fun getHistory(from: Long, to: Long): List<SignalSnapshotEntity>

    @Query(
        """
        SELECT AVG(rsrp) FROM signal_snapshots 
        WHERE timestamp BETWEEN :from AND :to
        """
    )
    suspend fun getAverageRsrp(from: Long, to: Long): Double?

    @Query(
        """
        SELECT AVG(sinr) FROM signal_snapshots 
        WHERE timestamp BETWEEN :from AND :to
        """
    )
    suspend fun getAverageSinr(from: Long, to: Long): Double?

    @Query("DELETE FROM signal_snapshots WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM signal_snapshots")
    suspend fun deleteAll()
}
