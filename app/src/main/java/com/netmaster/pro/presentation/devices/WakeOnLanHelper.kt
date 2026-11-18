package com.netmaster.pro.presentation.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeOnLanHelper @Inject constructor() {

    /**
     * Sends a Wake-on-LAN magic packet to [macAddress] via UDP broadcast to [broadcastIp]:[port].
     *
     * Magic packet structure: 6 bytes of 0xFF followed by 16 repetitions of the 6-byte MAC address
     * = 6 + (16 × 6) = 102 bytes total.
     */
    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastIp: String,
        port: Int = 9
    ) = withContext(Dispatchers.IO) {
        val mac = macAddress.replace(":", "").replace("-", "")
        require(mac.length == 12) { "MAC address must be exactly 6 bytes: $macAddress" }

        val macBytes = ByteArray(6) { i ->
            mac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        // 6 × 0xFF header + 16 × 6-byte MAC = 102 bytes
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (repetition in 0 until 16) {
            for (byteIdx in 0 until 6) {
                packet[6 + repetition * 6 + byteIdx] = macBytes[byteIdx]
            }
        }

        DatagramSocket().use { socket ->
            socket.broadcast = true
            val address = InetAddress.getByName(broadcastIp)
            socket.send(DatagramPacket(packet, packet.size, address, port))
        }
    }
}
