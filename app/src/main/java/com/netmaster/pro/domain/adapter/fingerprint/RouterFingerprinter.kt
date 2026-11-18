package com.netmaster.pro.domain.adapter.fingerprint

import android.util.Log
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.RouterDevice
import com.netmaster.pro.data.network.OkHttpClientProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes a router's HTTP interface to determine its brand and capabilities.
 *
 * Priority order (highest to lowest confidence):
 * 1. Greenpacket — unique URLs like /goform/ or /cgi-bin/GetLteInfoNew.cgi
 * 2. Huawei — /api/webserver/SesTokInfo
 * 3. ZTE — /goform/goform_get_cmd_process
 * 4. TP-Link — /webpages/index.html or stok= pattern
 * 5. D-Link — /ui/login.html
 * 6. MikroTik — /webfig/
 * 7. OpenWrt — /cgi-bin/luci/
 * 8. ASUS — /appGet.cgi
 * 9. Generic fallback
 */
@Singleton
class RouterFingerprinter @Inject constructor(
    private val okHttpClientProvider: OkHttpClientProvider,
    private val gatewayDetector: GatewayDetector
) {
    companion object {
        private const val TAG = "RouterFingerprinter"
        private const val PROBE_TIMEOUT_MS = 5_000L
        private val PROBE_PORTS = listOf(80, 8080, 443)
    }

    /**
     * Attempts to detect the router brand at the given IP (or the default gateway if null).
     * Probes all candidate ports in parallel and returns the best match.
     *
     * @param ip Optional IP override; uses the system's default gateway if null.
     * @return [RouterDevice] with detected brand and capabilities, or brand=UNKNOWN on failure.
     */
    suspend fun detect(ip: String? = null): RouterDevice {
        val targetIp = ip ?: gatewayDetector.getDefaultGateway()
        if (targetIp == null) {
            Log.w(TAG, "No gateway detected and no IP provided")
            return RouterDevice(ip = "0.0.0.0", brand = RouterBrand.UNKNOWN)
        }

        Log.d(TAG, "Fingerprinting router at $targetIp")

        // Try ports in parallel, return the first successful match
        return coroutineScope {
            PROBE_PORTS.map { port ->
                async { probePort(targetIp, port) }
            }.map { it.await() }
        }.firstOrNull { it.brand != RouterBrand.UNKNOWN }
            ?: RouterDevice(ip = targetIp, port = 80, brand = RouterBrand.UNKNOWN)
    }

    private suspend fun probePort(ip: String, port: Int): RouterDevice {
        val scheme = if (port == 443) "https" else "http"
        val base = "$scheme://$ip:$port"

        // 1. Greenpacket — unique CGI endpoints
        if (probe("$base/goform/GetLteInfoNew") ||
            probe("$base/cgi-bin/GetLteInfoNew.cgi") ||
            probe("$base/goform/goform_get_cmd_process?cmd=modem_main_state") ||
            containsText("$base/", "GreenPacket")
        ) {
            Log.i(TAG, "Detected Greenpacket at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.GREENPACKET,
                capabilities = setOf(
                    RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
                    RouterCapability.BAND_LOCK, RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP
                )
            )
        }

        // 2. Huawei — session token API
        if (probe("$base/api/webserver/SesTokInfo") ||
            probe("$base/api/device/basic_information") ||
            containsText("$base/", "Huawei")
        ) {
            Log.i(TAG, "Detected Huawei at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.HUAWEI,
                capabilities = setOf(
                    RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
                    RouterCapability.BAND_LOCK, RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
                    RouterCapability.CONFIG_BACKUP, RouterCapability.DNS_CHANGE
                )
            )
        }

        // 3. ZTE — goform endpoint
        if (probe("$base/goform/goform_get_cmd_process") ||
            containsText("$base/", "ZTE")
        ) {
            Log.i(TAG, "Detected ZTE at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.ZTE,
                capabilities = setOf(
                    RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
                    RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP
                )
            )
        }

        // 4. TP-Link — tplink-specific webpages or stok pattern
        if (probe("$base/webpages/index.html") ||
            probe("$base/index.htm") &&
            containsText("$base/", "TP-LINK") ||
            containsText("$base/", "tplink") ||
            containsText("$base/", "TL-")
        ) {
            Log.i(TAG, "Detected TP-Link at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.TPLINK,
                capabilities = setOf(
                    RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
                    RouterCapability.CONFIG_BACKUP, RouterCapability.DNS_CHANGE,
                    RouterCapability.PORT_FORWARDING, RouterCapability.MAC_FILTER,
                    RouterCapability.GUEST_WIFI, RouterCapability.QOS
                )
            )
        }

        // 5. D-Link
        if (probe("$base/ui/login.html") ||
            containsText("$base/", "D-Link") ||
            containsText("$base/", "dlink")
        ) {
            Log.i(TAG, "Detected D-Link at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.DLINK,
                capabilities = setOf(
                    RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
                    RouterCapability.CONFIG_BACKUP, RouterCapability.PORT_FORWARDING
                )
            )
        }

        // 6. MikroTik — Winbox / WebFig
        if (probe("$base/webfig/") ||
            probe("$base/winbox/") ||
            containsText("$base/", "MikroTik") ||
            containsText("$base/", "RouterOS")
        ) {
            Log.i(TAG, "Detected MikroTik at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.MIKROTIK,
                capabilities = setOf(
                    RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP,
                    RouterCapability.PORT_FORWARDING, RouterCapability.QOS,
                    RouterCapability.DNS_CHANGE, RouterCapability.MAC_FILTER,
                    RouterCapability.UPNP, RouterCapability.DSL_STATS
                )
            )
        }

        // 7. OpenWrt — LuCI web interface
        if (probe("$base/cgi-bin/luci/") ||
            containsText("$base/", "OpenWrt") ||
            containsText("$base/cgi-bin/luci/", "LuCI")
        ) {
            Log.i(TAG, "Detected OpenWrt at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.OPENWRT,
                capabilities = setOf(
                    RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
                    RouterCapability.CONFIG_BACKUP, RouterCapability.PORT_FORWARDING,
                    RouterCapability.QOS, RouterCapability.DNS_CHANGE,
                    RouterCapability.MAC_FILTER, RouterCapability.UPNP
                )
            )
        }

        // 8. ASUS
        if (probe("$base/appGet.cgi") ||
            containsText("$base/", "ASUS") ||
            containsText("$base/", "asuswrt")
        ) {
            Log.i(TAG, "Detected ASUS at $ip:$port")
            return RouterDevice(
                ip = ip, port = port, brand = RouterBrand.ASUS,
                capabilities = setOf(
                    RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
                    RouterCapability.CONFIG_BACKUP, RouterCapability.PORT_FORWARDING,
                    RouterCapability.QOS, RouterCapability.DNS_CHANGE,
                    RouterCapability.MAC_FILTER, RouterCapability.GUEST_WIFI,
                    RouterCapability.PARENTAL_CONTROLS, RouterCapability.UPNP
                )
            )
        }

        return RouterDevice(ip = ip, port = port, brand = RouterBrand.UNKNOWN)
    }

    /** Returns true if a GET to [url] returns any 2xx or 3xx status */
    private suspend fun probe(url: String): Boolean {
        return try {
            withTimeout(PROBE_TIMEOUT_MS) {
                val request = Request.Builder().url(url).get()
                    .header("User-Agent", "NetMasterPro/1.0")
                    .build()
                okHttpClientProvider.client.newCall(request).execute().use { response ->
                    response.code in 200..399
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "Probe failed for $url: ${e.message}")
            false
        }
    }

    /** Returns true if a GET to [url] succeeds and body contains [text] (case-insensitive) */
    private suspend fun containsText(url: String, text: String): Boolean {
        return try {
            withTimeout(PROBE_TIMEOUT_MS) {
                val request = Request.Builder().url(url).get()
                    .header("User-Agent", "NetMasterPro/1.0")
                    .build()
                okHttpClientProvider.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withTimeout false
                    val body = response.body?.string() ?: return@withTimeout false
                    body.contains(text, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "containsText failed for $url: ${e.message}")
            false
        }
    }
}
