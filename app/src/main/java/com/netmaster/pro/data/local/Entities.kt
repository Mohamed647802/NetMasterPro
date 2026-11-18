package com.netmaster.pro.data.local

import androidx.room.TypeConverter
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.WifiBand
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─── Room Entities ──────────────────────────────────────────────────────────

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey val mac: String,
    @ColumnInfo(name = "ip") val ip: String,
    @ColumnInfo(name = "hostname") val hostname: String = "",
    @ColumnInfo(name = "vendor") val vendor: String = "",
    @ColumnInfo(name = "nickname") val nickname: String = "",
    @ColumnInfo(name = "category") val category: String = DeviceCategory.UNKNOWN.name,
    @ColumnInfo(name = "is_trusted") val isTrusted: Boolean = false,
    @ColumnInfo(name = "band") val band: String = WifiBand.UNKNOWN.name,
    @ColumnInfo(name = "rssi") val rssi: Int = 0,
    @ColumnInfo(name = "lease_time") val leaseTime: Int = -1,
    @ColumnInfo(name = "first_seen") val firstSeen: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_seen") val lastSeen: Long = System.currentTimeMillis()
)

@Entity(tableName = "traffic_logs")
data class TrafficLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "rx_bytes") val rxBytes: Long,
    @ColumnInfo(name = "tx_bytes") val txBytes: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "signal_snapshots")
data class SignalSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rsrp") val rsrp: Int,
    @ColumnInfo(name = "rsrq") val rsrq: Float,
    @ColumnInfo(name = "sinr") val sinr: Float,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "pci") val pci: Int,
    @ColumnInfo(name = "cell_id") val cellId: Long,
    @ColumnInfo(name = "enb_id") val eNbId: Long,
    @ColumnInfo(name = "earfcn") val earfcn: Int,
    @ColumnInfo(name = "band") val band: Int,
    @ColumnInfo(name = "ca_active") val caActive: Boolean = false,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "speed_tests")
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "download_mbps") val downloadMbps: Double,
    @ColumnInfo(name = "upload_mbps") val uploadMbps: Double,
    @ColumnInfo(name = "ping_ms") val pingMs: Double,
    @ColumnInfo(name = "jitter_ms") val jitterMs: Double,
    @ColumnInfo(name = "bufferbloat_grade") val bufferbloatGrade: String,
    @ColumnInfo(name = "test_type") val testType: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "router_profiles")
data class RouterProfileEntity(
    @PrimaryKey val ip: String,
    @ColumnInfo(name = "port") val port: Int = 80,
    @ColumnInfo(name = "brand") val brand: String = RouterBrand.UNKNOWN.name,
    @ColumnInfo(name = "model") val model: String = "",
    @ColumnInfo(name = "firmware_version") val firmwareVersion: String = "",
    @ColumnInfo(name = "mac_address") val macAddress: String = "",
    @ColumnInfo(name = "capabilities") val capabilities: String = "[]", // JSON array
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "last_connected") val lastConnected: Long = System.currentTimeMillis()
)

// ─── Type Converters ─────────────────────────────────────────────────────────

class AppTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: String): List<String> =
        json.decodeFromString(value)

    @TypeConverter
    fun toStringList(list: List<String>): String =
        json.encodeToString(list)

    @TypeConverter
    fun fromIntList(value: String): List<Int> =
        json.decodeFromString(value)

    @TypeConverter
    fun toIntList(list: List<Int>): String =
        json.encodeToString(list)
}
