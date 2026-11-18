package com.netmaster.pro.presentation.parental

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject

class DnsBenchmark @Inject constructor() {

    data class DnsResult(
        val provider: String,
        val primary: String,
        val secondary: String,
        val latencyMs: Long,
        val failed: Boolean
    )

    private val providers = listOf(
        Triple("Cloudflare", "1.1.1.1", "1.0.0.1"),
        Triple("Cloudflare Family", "1.1.1.3", "1.0.0.3"),
        Triple("Google", "8.8.8.8", "8.8.4.4"),
        Triple("AdGuard", "94.140.14.14", "94.140.15.15"),
        Triple("OpenDNS", "208.67.222.222", "208.67.220.220"),
        Triple("OpenDNS Family", "208.67.222.123", "208.67.220.123"),
        Triple("Quad9", "9.9.9.9", "149.112.112.112"),
        Triple("Comodo", "8.26.56.26", "8.20.247.20"),
        Triple("CleanBrowsing", "185.228.168.9", "185.228.169.9"),
        Triple("Yandex Safe", "77.88.8.88", "77.88.8.2")
    )

    suspend fun benchmark(): List<DnsResult> = withContext(Dispatchers.IO) {
        providers.map { (name, primary, secondary) ->
            async {
                val latency = measureDnsLatency(primary)
                DnsResult(
                    provider = name,
                    primary = primary,
                    secondary = secondary,
                    latencyMs = latency,
                    failed = latency < 0
                )
            }
        }.awaitAll().sortedWith(
            compareBy({ it.failed }, { it.latencyMs })
        )
    }

    private fun measureDnsLatency(serverIp: String): Long {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            val query = buildDnsQuery("google.com")
            val serverAddr = InetAddress.getByName(serverIp)
            val sendPacket = DatagramPacket(query, query.size, serverAddr, 53)
            val receiveBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            val start = System.currentTimeMillis()
            socket.send(sendPacket)
            socket.receive(receivePacket)
            val elapsed = System.currentTimeMillis() - start
            socket.close()
            elapsed
        } catch (e: SocketTimeoutException) {
            -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val buf = mutableListOf<Byte>()
        // Transaction ID
        buf.add(0x12); buf.add(0x34)
        // Flags: standard query
        buf.add(0x01); buf.add(0x00)
        // Questions: 1
        buf.add(0x00); buf.add(0x01)
        // Answers, Authority, Additional: 0
        buf.add(0x00); buf.add(0x00)
        buf.add(0x00); buf.add(0x00)
        buf.add(0x00); buf.add(0x00)
        // QNAME
        for (label in domain.split(".")) {
            buf.add(label.length.toByte())
            for (c in label) buf.add(c.code.toByte())
        }
        buf.add(0x00) // end of QNAME
        // QTYPE: A (1)
        buf.add(0x00); buf.add(0x01)
        // QCLASS: IN (1)
        buf.add(0x00); buf.add(0x01)
        return buf.toByteArray()
    }
}
