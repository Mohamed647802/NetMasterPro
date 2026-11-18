package com.netmaster.pro.data.model

data class SpeedTestRecord(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Long = 0,
    val jitterMs: Long = 0
)
