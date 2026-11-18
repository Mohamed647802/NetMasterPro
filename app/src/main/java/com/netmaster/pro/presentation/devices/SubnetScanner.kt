package com.netmaster.pro.presentation.devices

import android.content.Context
import com.netmaster.pro.data.model.ConnectedDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubnetScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Scans all hosts in the /24 subnet derived from [gatewayIp].
     * Uses 32 concurrent coroutines. Progress reported via [progressCallback] (0–100).
     * Returns list of reachable IP addresses.
     */
    suspend fun scan(
        gatewayIp: String,
        progressCallback: (Int) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        // Build base prefix: "192.168.1" from "192.168.1.1"
        val parts = gatewayIp.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val base = "${parts[0]}.${parts[1]}.${parts[2]}"

        val hosts = (1..254).map { i -> "$base.$i" }
        val reachable = mutableListOf<String>()
        val chunkSize = 32
        val chunks = hosts.chunked(chunkSize)
        var completed = 0

        chunks.forEach { chunk ->
            chunk.map { ip ->
                async {
                    try {
                        if (InetAddress.getByName(ip).isReachable(500)) {
                            synchronized(reachable) { reachable.add(ip) }
                        }
                    } catch (_: Exception) {}
                    synchronized(this) {
                        completed++
                        progressCallback((completed * 100) / hosts.size)
                    }
                }
            }.awaitAll()
        }

        reachable.sortedWith(compareBy { it.split(".").last().toIntOrNull() ?: 0 })
    }
}
