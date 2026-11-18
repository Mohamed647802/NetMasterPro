package com.netmaster.pro.data.adapter

import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.SmsMessage
import com.netmaster.pro.data.model.TrafficStats
import com.netmaster.pro.data.model.WifiBand
import com.netmaster.pro.domain.adapter.ApnProfile
import com.netmaster.pro.domain.adapter.DslStats
import com.netmaster.pro.domain.adapter.LteBand
import com.netmaster.pro.domain.adapter.MacFilterMode
import com.netmaster.pro.domain.adapter.NetworkMode
import com.netmaster.pro.domain.adapter.PortForwardRule
import com.netmaster.pro.domain.adapter.PortProtocol
import com.netmaster.pro.domain.adapter.QosRule
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.RouterSecurityInfo
import com.netmaster.pro.domain.adapter.WifiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Adapter for standard OpenWrt routers using ubus JSON-RPC and LuCI.
 *
 * Authentication: POSTs to /ubus with the null-session token to log in,
 * then uses the returned 32-char hex session token for all subsequent calls.
 */
class OpenWrtUbusAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.OPENWRT

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.REBOOT,
        RouterCapability.WIFI_5GHZ,
        RouterCapability.GUEST_WIFI,
        RouterCapability.PORT_FORWARDING,
        RouterCapability.MAC_FILTER,
        RouterCapability.DNS_CHANGE,
        RouterCapability.CONFIG_BACKUP,
        RouterCapability.UPNP
    )

    private var routerIp: String = ""
    private var routerPort: Int = 80
    private var sessionToken: String = ""
    private val requestIdCounter = AtomicInteger(1)

    companion object {
        private const val NULL_SESSION = "00000000000000000000000000000000"
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun ubusUrl() = "http://$routerIp:$routerPort/ubus"
    private fun baseUrl() = "http://$routerIp:$routerPort"
    private fun nextId() = requestIdCounter.getAndIncrement()

    private fun buildClient(): OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun ubusRaw(body: String): JSONObject = withContext(Dispatchers.IO) {
        val mediaType = "application/json".toMediaType()
        val requestBody = body.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(ubusUrl())
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        val resp = buildClient().newCall(request).execute()
        val respBody = resp.body?.string() ?: "{}"
        JSONObject(respBody)
    }

    /**
     * Execute a ubus RPC call. Returns the result[1] data object on success,
     * or throws if result[0] != 0.
     */
    private suspend fun ubusCall(
        session: String,
        ubusObject: String,
        method: String,
        params: JSONObject = JSONObject()
    ): JSONObject {
        val id = nextId()
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "call")
            put("params", JSONArray().apply {
                put(session)
                put(ubusObject)
                put(method)
                put(params)
            })
        }.toString()
        val resp = ubusRaw(body)
        val result = resp.optJSONArray("result")
            ?: throw Exception("ubus: no result array in response for $ubusObject.$method")
        val code = result.optInt(0, -1)
        if (code != 0) {
            throw Exception("ubus call $ubusObject.$method returned error code $code")
        }
        return result.optJSONObject(1) ?: JSONObject()
    }

    private suspend fun ubusCallString(
        session: String,
        ubusObject: String,
        method: String,
        params: JSONObject = JSONObject()
    ): String? {
        val id = nextId()
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "call")
            put("params", JSONArray().apply {
                put(session)
                put(ubusObject)
                put(method)
                put(params)
            })
        }.toString()
        val resp = ubusRaw(body)
        val result = resp.optJSONArray("result") ?: return null
        return result.optJSONObject(1)?.toString()
    }

    // ─── fingerprint ──────────────────────────────────────────────────────────

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Probe 1: ubus session list
            val testSession = NULL_SESSION
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "call")
                put("params", JSONArray().apply {
                    put(testSession)
                    put("session")
                    put("list")
                    put(JSONObject())
                })
            }.toString()
            val mediaType = "application/json".toMediaType()
            val requestBody = body.toRequestBody(mediaType)
            val client = buildClient()
            val request = Request.Builder()
                .url("http://$ip:$port/ubus")
                .post(requestBody)
                .build()
            val resp = client.newCall(request).execute()
            val respBody = resp.body?.string() ?: ""
            if (respBody.contains("\"result\"") && resp.code == 200) return@withContext true

            // Probe 2: LuCI redirect
            val luciReq = Request.Builder().url("http://$ip:$port/cgi-bin/luci").build()
            val luciResp = client.newBuilder().followRedirects(false).build().newCall(luciReq).execute()
            if (luciResp.code == 302 || luciResp.code == 301) {
                val location = luciResp.header("Location") ?: ""
                if (location.contains("luci") || location.contains("login")) return@withContext true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // ─── login ────────────────────────────────────────────────────────────────

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            routerIp = ip
            routerPort = port
            try {
                val params = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                val id = nextId()
                val body = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", "call")
                    put("params", JSONArray().apply {
                        put(NULL_SESSION)
                        put("session")
                        put("login")
                        put(params)
                    })
                }.toString()
                val resp = ubusRaw(body)
                val result = resp.optJSONArray("result")
                    ?: return@withContext Result.failure(Exception("No result in ubus login response"))
                val code = result.optInt(0, -1)
                if (code != 0) {
                    return@withContext Result.failure(Exception("ubus login failed with code $code — wrong password?"))
                }
                val data = result.optJSONObject(1)
                    ?: return@withContext Result.failure(Exception("ubus login: no session data"))
                val token = data.optString("ubus_rpc_session", "")
                if (token.isEmpty() || token == NULL_SESSION) {
                    return@withContext Result.failure(Exception("ubus login: empty session token"))
                }
                sessionToken = token
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── logout ───────────────────────────────────────────────────────────────

    override suspend fun logout() {
        try {
            if (sessionToken.isNotEmpty()) {
                ubusCall(
                    sessionToken,
                    "session",
                    "destroy",
                    JSONObject().apply { put("ubus_rpc_session", sessionToken) }
                )
            }
        } catch (_: Exception) {}
        sessionToken = ""
    }

    // ─── LTE signal (not supported) ───────────────────────────────────────────

    override suspend fun getLteSignal(): Result<LteSignalData> =
        Result.failure(UnsupportedOperationException(
            "LTE signal not available via OpenWrt ubus — this adapter targets Wi-Fi OpenWrt routers"
        ))

    // ─── Connected devices ────────────────────────────────────────────────────

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                val deviceMap = mutableMapOf<String, ConnectedDevice>()

                // Source 1: DHCP leases from /tmp/dhcp.leases
                try {
                    val fileData = ubusCall(
                        sessionToken, "file", "read",
                        JSONObject().apply { put("path", "/tmp/dhcp.leases") }
                    )
                    val content = fileData.optString("data", "")
                    // Format: epoch mac ip hostname *
                    content.lines().filter { it.isNotBlank() }.forEach { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val mac = parts[1].uppercase()
                            val ip = parts[2]
                            val hostname = if (parts[3] == "*") "" else parts[3]
                            deviceMap[mac] = ConnectedDevice(
                                mac = mac,
                                ip = ip,
                                hostname = hostname,
                                band = WifiBand.UNKNOWN,
                                category = DeviceCategory.UNKNOWN
                            )
                        }
                    }
                } catch (_: Exception) {}

                // Source 2: Wi-Fi associated clients (wlan0 = 2.4GHz, wlan1 = 5GHz)
                val wirelessInterfaces = listOf("wlan0" to WifiBand.BAND_2_4GHZ, "wlan1" to WifiBand.BAND_5GHZ)
                wirelessInterfaces.forEach { (iface, band) ->
                    try {
                        val assoc = ubusCall(
                            sessionToken, "iwinfo", "assoclist",
                            JSONObject().apply { put("device", iface) }
                        )
                        val results = assoc.optJSONArray("results") ?: return@forEach
                        for (i in 0 until results.length()) {
                            val entry = results.getJSONObject(i)
                            val mac = entry.optString("bssid", "").uppercase()
                            if (mac.isEmpty()) continue
                            val signal = entry.optInt("signal", 0)
                            val existing = deviceMap[mac]
                            if (existing != null) {
                                deviceMap[mac] = existing.copy(band = band, rssi = signal)
                            } else {
                                deviceMap[mac] = ConnectedDevice(
                                    mac = mac,
                                    ip = "",
                                    band = band,
                                    rssi = signal,
                                    category = DeviceCategory.UNKNOWN
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Source 3: Try hostapd if iwinfo not available
                if (deviceMap.values.all { it.band == WifiBand.UNKNOWN }) {
                    try {
                        val clients = ubusCall(sessionToken, "hostapd.wlan0", "get_clients", JSONObject())
                        val clientsObj = clients.optJSONObject("clients") ?: JSONObject()
                        clientsObj.keys().forEach { mac ->
                            val ucMac = mac.uppercase()
                            val existing = deviceMap[ucMac]
                            if (existing != null) {
                                deviceMap[ucMac] = existing.copy(band = WifiBand.BAND_2_4GHZ)
                            } else {
                                deviceMap[ucMac] = ConnectedDevice(
                                    mac = ucMac,
                                    ip = "",
                                    band = WifiBand.BAND_2_4GHZ,
                                    category = DeviceCategory.UNKNOWN
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                Result.success(deviceMap.values.toList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── WiFi settings ────────────────────────────────────────────────────────

    override suspend fun getWifiSettings(): Result<WifiSettings> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            val uciData = ubusCall(
                sessionToken, "uci", "get",
                JSONObject().apply { put("config", "wireless") }
            )
            val values = uciData.optJSONObject("values") ?: JSONObject()
            // Find the first wifi-iface section
            var ssid = ""
            var key = ""
            var disabled = false
            var channel = 0
            var encryption = "psk2"

            values.keys().forEach { sectionKey ->
                val section = values.optJSONObject(sectionKey) ?: return@forEach
                val type = section.optString(".type", "")
                when (type) {
                    "wifi-iface" -> {
                        if (ssid.isEmpty()) {
                            ssid = section.optString("ssid", "")
                            key = section.optString("key", "")
                            disabled = section.optString("disabled", "0") == "1"
                            encryption = section.optString("encryption", "psk2")
                        }
                    }
                    "wifi-device" -> {
                        if (channel == 0) channel = section.optString("channel", "0").toIntOrNull() ?: 0
                    }
                }
            }
            Result.success(
                WifiSettings(
                    ssid2g = ssid,
                    password2g = key,
                    enabled2g = !disabled,
                    channel2g = channel,
                    security = encryption
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                // Find default radio0 section and default_radio0 iface
                ubusCall(
                    sessionToken, "uci", "set",
                    JSONObject().apply {
                        put("config", "wireless")
                        put("section", "default_radio0")
                        put("values", JSONObject().apply {
                            put("ssid", settings.ssid2g)
                            put("key", settings.password2g)
                            put("disabled", if (settings.enabled2g) "0" else "1")
                            put("encryption", settings.security)
                        })
                    }
                )
                // Also set channel on the device section
                if (settings.channel2g > 0) {
                    ubusCall(
                        sessionToken, "uci", "set",
                        JSONObject().apply {
                            put("config", "wireless")
                            put("section", "radio0")
                            put("values", JSONObject().apply { put("channel", settings.channel2g.toString()) })
                        }
                    )
                }
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "wireless") })
                ubusCall(sessionToken, "network", "reload", JSONObject())
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Block / Unblock ──────────────────────────────────────────────────────

    override suspend fun blockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // Add MAC to wireless access-list deny entry
            ubusCall(
                sessionToken, "uci", "add",
                JSONObject().apply {
                    put("config", "wireless")
                    put("type", "wifi-iface") // We'll use mac-filter approach via nftables/iptables
                    put("values", JSONObject().apply {
                        put("macaddr", mac.uppercase())
                        put("macpolicy", "deny")
                    })
                }
            )
            ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "wireless") })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // List sections and find the blocked MAC entry to delete
            val uciData = ubusCall(
                sessionToken, "uci", "get",
                JSONObject().apply { put("config", "wireless") }
            )
            val values = uciData.optJSONObject("values") ?: JSONObject()
            val targetSection = values.keys().asSequence().firstOrNull { key ->
                val sec = values.optJSONObject(key)
                sec?.optString("macaddr", "").equals(mac, ignoreCase = true)
            }
            if (targetSection != null) {
                ubusCall(
                    sessionToken, "uci", "delete",
                    JSONObject().apply {
                        put("config", "wireless")
                        put("section", targetSection)
                    }
                )
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "wireless") })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Reboot ───────────────────────────────────────────────────────────────

    override suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            ubusCall(sessionToken, "system", "reboot", JSONObject())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── SMS / USSD (not supported) ───────────────────────────────────────────

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> =
        Result.failure(UnsupportedOperationException("SMS not available on OpenWrt WiFi routers"))

    override suspend fun sendSms(to: String, body: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("SMS not available on OpenWrt WiFi routers"))

    override suspend fun deleteSms(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("SMS not available on OpenWrt WiFi routers"))

    override suspend fun sendUssd(code: String): Result<String> =
        Result.failure(UnsupportedOperationException("USSD not available on OpenWrt WiFi routers"))

    // ─── Traffic stats ────────────────────────────────────────────────────────

    override suspend fun getTrafficStats(): Result<TrafficStats> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // Try luci-rpc first
            var rxBytes = 0L
            var txBytes = 0L
            try {
                val devData = ubusCall(sessionToken, "luci-rpc", "getNetworkDevices", JSONObject())
                devData.keys().forEach { dev ->
                    val devObj = devData.optJSONObject(dev) ?: return@forEach
                    val stats = devObj.optJSONObject("stats") ?: return@forEach
                    // Skip loopback
                    if (dev == "lo") return@forEach
                    rxBytes += stats.optLong("rx_bytes", 0L)
                    txBytes += stats.optLong("tx_bytes", 0L)
                }
            } catch (_: Exception) {
                // Fallback: read /proc/net/dev
                val fileData = ubusCall(
                    sessionToken, "file", "read",
                    JSONObject().apply { put("path", "/proc/net/dev") }
                )
                val procDev = fileData.optString("data", "")
                procDev.lines().drop(2).filter { it.isNotBlank() }.forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    val iface = parts.getOrNull(0)?.trimEnd(':') ?: return@forEach
                    if (iface == "lo") return@forEach
                    rxBytes += parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    txBytes += parts.getOrNull(9)?.toLongOrNull() ?: 0L
                }
            }
            Result.success(TrafficStats(rxBytes = rxBytes, txBytes = txBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── DSL stats (not supported) ────────────────────────────────────────────

    override suspend fun getDslStats(): Result<DslStats> =
        Result.failure(UnsupportedOperationException("DSL stats not supported — OpenWrt ubus adapter targets Wi-Fi/wired routers"))

    // ─── DNS ──────────────────────────────────────────────────────────────────

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                ubusCall(
                    sessionToken, "uci", "set",
                    JSONObject().apply {
                        put("config", "dhcp")
                        put("section", "@dnsmasq[0]")
                        put("values", JSONObject().apply {
                            put("server", JSONArray().apply {
                                put(primary)
                                put(secondary)
                            })
                        })
                    }
                )
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "dhcp") })
                // Restart dnsmasq
                try {
                    ubusCall(
                        sessionToken, "file", "exec",
                        JSONObject().apply {
                            put("command", "/etc/init.d/dnsmasq")
                            put("params", JSONArray().apply { put("restart") })
                        }
                    )
                } catch (_: Exception) {}
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Network mode / Band selection (not supported) ───────────────────────

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        Result.failure(UnsupportedOperationException("Network mode selection not supported by OpenWrt ubus adapter"))

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
        Result.failure(UnsupportedOperationException("LTE band selection not supported by OpenWrt ubus adapter"))

    // ─── Backup / Restore ─────────────────────────────────────────────────────

    override suspend fun downloadBackup(): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // LuCI backup endpoint requires sysauth cookie — derive it from ubus session
            val url = "${baseUrl()}/cgi-bin/luci/admin/system/flashops/backup"
            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", "sysauth=$sessionToken")
                .build()
            val response = buildClient().newCall(request).execute()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isNotEmpty()) Result.success(bytes)
            else Result.failure(Exception("Empty backup response from LuCI"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            val url = "${baseUrl()}/cgi-bin/luci/admin/system/flashops/restore"
            val mediaType = "application/x-tar".toMediaType()
            val requestBody = data.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Cookie", "sysauth=$sessionToken")
                .build()
            val response = buildClient().newCall(request).execute()
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception("Restore failed, HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Security info ────────────────────────────────────────────────────────

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // Check which services are enabled
            val openPorts = mutableListOf<Int>()
            var remoteManagementEnabled = false
            var upnpEnabled = false

            // Check /etc/config/uhttpd for open ports
            try {
                val uhttpdData = ubusCall(
                    sessionToken, "uci", "get",
                    JSONObject().apply { put("config", "uhttpd") }
                )
                val values = uhttpdData.optJSONObject("values") ?: JSONObject()
                values.keys().forEach { k ->
                    val section = values.optJSONObject(k) ?: return@forEach
                    val listenHttp = section.optString("listen_http", "")
                    if (listenHttp.isNotEmpty()) {
                        listenHttp.split(" ").forEach { addr ->
                            val port = addr.substringAfterLast(":").toIntOrNull() ?: 80
                            openPorts.add(port)
                            if (port != 80 && port != 443) remoteManagementEnabled = true
                        }
                    }
                }
            } catch (_: Exception) {}

            // Check UPnP
            try {
                val upnpData = ubusCall(
                    sessionToken, "uci", "get",
                    JSONObject().apply { put("config", "upnpd") }
                )
                val values = upnpData.optJSONObject("values") ?: JSONObject()
                values.keys().forEach { k ->
                    val section = values.optJSONObject(k) ?: return@forEach
                    if (section.optString("enabled", "0") == "1") upnpEnabled = true
                }
            } catch (_: Exception) {}

            Result.success(
                RouterSecurityInfo(
                    defaultCredentialsChanged = true, // assume true if we're logged in
                    remoteManagementEnabled = remoteManagementEnabled,
                    uPnPEnabled = upnpEnabled,
                    firewallEnabled = true // OpenWrt has firewall by default
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── MAC filter ───────────────────────────────────────────────────────────

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                // Set macpolicy on the wifi-iface section
                val policyStr = when (mode) {
                    MacFilterMode.WHITELIST -> "allow"
                    MacFilterMode.BLACKLIST -> "deny"
                    MacFilterMode.DISABLED -> "none"
                }
                ubusCall(
                    sessionToken, "uci", "set",
                    JSONObject().apply {
                        put("config", "wireless")
                        put("section", "default_radio0")
                        put("values", JSONObject().apply {
                            put("macpolicy", policyStr)
                            put("maclist", JSONArray(macs))
                        })
                    }
                )
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "wireless") })
                ubusCall(sessionToken, "network", "reload", JSONObject())
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── QoS ──────────────────────────────────────────────────────────────────

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = withContext(Dispatchers.IO) {
        if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
        try {
            // OpenWrt QoS via SQM or qos-scripts — basic approach via UCI
            rules.forEach { rule ->
                ubusCall(
                    sessionToken, "uci", "add",
                    JSONObject().apply {
                        put("config", "qos")
                        put("type", "classify")
                        put("values", JSONObject().apply {
                            put("target", "Priority")
                            put("proto", "any")
                            put("srchost", rule.deviceMac)
                            put("uploadkbps", rule.uploadLimitKbps)
                            put("downloadkbps", rule.downloadLimitKbps)
                        })
                    }
                )
            }
            ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "qos") })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Guest WiFi ───────────────────────────────────────────────────────────

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                // Try to find or create a guest interface on radio0
                ubusCall(
                    sessionToken, "uci", "set",
                    JSONObject().apply {
                        put("config", "wireless")
                        put("section", "guest_radio0")
                        put("values", JSONObject().apply {
                            put(".type", "wifi-iface")
                            put("device", "radio0")
                            put("mode", "ap")
                            put("ssid", ssid)
                            put("encryption", if (password.isEmpty()) "none" else "psk2")
                            put("key", password)
                            put("disabled", if (enabled) "0" else "1")
                            put("network", "guest")
                            put("isolate", "1") // Client isolation for guest network
                        })
                    }
                )
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "wireless") })
                ubusCall(sessionToken, "network", "reload", JSONObject())
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Port forwarding ──────────────────────────────────────────────────────

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                ubusCall(
                    sessionToken, "uci", "add",
                    JSONObject().apply {
                        put("config", "firewall")
                        put("type", "redirect")
                        put("values", JSONObject().apply {
                            put("name", rule.name)
                            put("proto", rule.protocol.toWireString(lowercase = true))
                            put("src", "wan")
                            put("src_dport", rule.externalPort.toString())
                            put("dest", "lan")
                            put("dest_ip", rule.internalIp)
                            put("dest_port", rule.internalPort.toString())
                            put("target", "DNAT")
                        })
                    }
                )
                ubusCall(sessionToken, "uci", "commit", JSONObject().apply { put("config", "firewall") })
                // Reload firewall
                try {
                    ubusCall(
                        sessionToken, "file", "exec",
                        JSONObject().apply {
                            put("command", "/etc/init.d/firewall")
                            put("params", JSONArray().apply { put("reload") })
                        }
                    )
                } catch (_: Exception) {}
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
        withContext(Dispatchers.IO) {
            if (sessionToken.isEmpty()) return@withContext Result.failure(Exception("Not logged in"))
            try {
                val firewallData = ubusCall(
                    sessionToken, "uci", "get",
                    JSONObject().apply { put("config", "firewall") }
                )
                val values = firewallData.optJSONObject("values") ?: JSONObject()
                val rules = mutableListOf<PortForwardRule>()
                values.keys().forEach { sectionKey ->
                    val section = values.optJSONObject(sectionKey) ?: return@forEach
                    if (section.optString(".type", "") != "redirect") return@forEach
                    val name = section.optString("name", sectionKey)
                    val proto = section.optString("proto", "tcp").uppercase()
                    val srcDport = section.optString("src_dport", "0").toIntOrNull() ?: 0
                    val destIp = section.optString("dest_ip", "")
                    val destPort = section.optString("dest_port", "0").toIntOrNull() ?: 0
                    rules.add(
                        PortForwardRule(
                            name = name,
                            protocol = PortProtocol.fromString(proto),
                            externalPort = srcDport,
                            internalIp = destIp,
                            internalPort = destPort
                        )
                    )
                }
                Result.success(rules)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── APN (not supported) ──────────────────────────────────────────────────

    override suspend fun setApn(apn: ApnProfile): Result<Unit> =
        Result.failure(UnsupportedOperationException("APN not supported by OpenWrt ubus adapter — this targets Wi-Fi routers"))

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
        Result.failure(UnsupportedOperationException("APN not supported by OpenWrt ubus adapter — this targets Wi-Fi routers"))
}
