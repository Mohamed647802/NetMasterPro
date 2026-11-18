package com.netmaster.pro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ConnectedDeviceEntity::class,
        TrafficLogEntity::class,
        SignalSnapshotEntity::class,
        SpeedTestEntity::class,
        RouterProfileEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectedDeviceDao(): ConnectedDeviceDao
    abstract fun trafficLogDao(): TrafficLogDao
    abstract fun signalSnapshotDao(): SignalSnapshotDao
    abstract fun speedTestDao(): SpeedTestDao
    abstract fun routerProfileDao(): RouterProfileDao

    companion object {
        const val DATABASE_NAME = "netmaster_pro.db"
    }
}
