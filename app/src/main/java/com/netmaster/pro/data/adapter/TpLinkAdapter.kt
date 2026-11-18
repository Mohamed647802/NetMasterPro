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
import com.netmaster.pro.domain.adapter.QosRule
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.RouterSecurityInfo
import com.netmaster.pro.domain.adapter.WifiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Adapter for TP-Link Archer series routers (AX / older TL- models).
 *
 * Supports three login strategies:
 *  1. JSON stok (Archer AX / BE newer firmware)
 *  2. RPM cookie (older Archer C/AC)
 *  3. OpenWrt LuCI cookie (Deco / OpenWrt-based TP-Link)
 */
class TpLinkAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.TPLINK

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.REBOOT,
        RouterCapability.WIFI_5GHZ,
        RouterCapability.GUEST_WIFI,
        RouterCapability.QOS,
        RouterCapability.PORT_FORWARDING,
        RouterCapability.MAC_FILTER,
        RouterCapability.DNS_CHANGE,
        RouterCapability.CONFIG_BACKUP
    )

    private var routerIp: String = ""
    private var routerPort: Int = 80
    private var stok: String = ""
    private var sysAuthCookie: String = ""
    private var loginStrategy: LoginStrategy = LoginStrategy.NONE

    private enum class LoginStrategy { NONE, STOK_JSON, RPM_COOKIE, LUCI_COOKIE }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun baseUrl() = "http://$routerIp:$routerPort"

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun stokUrl(path: String) =
        "${baseUrl()}/cgi-bin/luci/;stok=$stok/$path"

    private fun buildJsonClient(): OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private suspend fun httpGet(url: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            if (sysAuthCookie.isNotEmpty()) builder.addHeader("Cookie", sysAuthCookie)
            val response = buildJsonClient().newCall(builder.build()).execute()
            response.body?.string() ?: ""
        }

    private suspend fun httpPost(
        url: String,
        body: String,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val mediaType = contentType.toMediaType()
        val requestBody = body.toRequestBody(mediaType)
        val builder = Request.Builder().url(url).post(requestBody)
        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        if (sysAuthCookie.isNotEmpty()) builder.addHeader("Cookie", sysAuthCookie)
        val response = buildJsonClient().newCall(builder.build()).execute()
        Pair(response.code, response.body?.string() ?: "")
    }

    private suspend fun httpPostForm(
        url: String,
        fields: Map<String, String>
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
        fields.forEach { (k, v) -> formBuilder.add(k, v) }
        val builder = Request.Builder().url(url).post(formBuilder.build())
        if (sysAuthCookie.isNotEmpty()) builder.addHeader("Cookie", sysAuthCookie)
        val response = buildJsonClient().newCall(builder.build()).execute()
        val setCookie = response.headers("Set-Cookie").joinToString("; ")
        Pair(response.code, response.body?.string() ?: "")
    }

    // ─── fingerprint ──────────────────────────────────────────────────────────

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$port/"
            val request = Request.Builder().url(url).build()
            val response = buildJsonClient().newCall(request).execute()
            val body = response.body?.string() ?: ""
            val server = response.header("Server") ?: ""
            if (body.contains("TP-LINK", ignoreCase = true) ||
                body.contains("TP_LINK", ignoreCase = true) ||
                body.contains("tplink", ignoreCase = true) ||
                server.contains("tplink", ignoreCase = true)
            ) return@withContext true

            // Check stok JSON endpoint
            val jsonBody = """{"method":"do","login":{"password":"test"}}"""
            val (code, resp) = httpPost("http://$ip:$port/", jsonBody)
            if (resp.contains("\"stok\"") || resp.contains("error_code")) return@withContext true

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

            // Strategy 1: JSON stok (newer Archer AX/BE)
            try {
                val md5Pass = md5Hex(password)
                val jsonBody = """{"method":"do","login":{"password":"$md5Pass"}}"""
                val (code, resp) = httpPost("${baseUrl()}/", jsonBody)
                if (code == 200 && resp.contains("\"stok\"")) {
                    val json = JSONObject(resp)
                    val token = json.optString("stok", "")
                    if (token.isNotEmpty()) {
                        stok = token
                        loginStrategy = LoginStrategy.STOK_JSON
                        return@withContext Result.success(Unit)
                    }
                }
            } catch (e: Exception) { /* try next */ }

            // Strategy 2: RPM cookie (older TL- models)
            try {
                val client = buildJsonClient()
                val loginUrl = "${baseUrl()}/userRpm/LoginRpm.htm?Save=Save&referer="
                val formBuilder = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .add("Save", "Save")
                val request = Request.Builder().url(loginUrl).post(formBuilder.build()).build()
                val response = client.newCall(request).execute()
                val cookie = response.headers("Set-Cookie").joinToString("; ")
                val body2 = response.body?.string() ?: ""
                if (response.code == 200 || response.code == 302) {
                    if (cookie.isNotEmpty() || body2.contains("LogoutRpm")) {
                        sysAuthCookie = cookie
                        loginStrategy = LoginStrategy.RPM_COOKIE
                        return@withContext Result.success(Unit)
                    }
                }
            } catch (e: Exception) { /* try next */ }

            // Strategy 3: LuCI cookie (OpenWrt-based Deco / TP-Link)
            try {
                val client = buildJsonClient()
                val luciUrl = "${baseUrl()}/cgi-bin/luci/"
                val formBuilder = FormBody.Builder()
                    .add("luci_username", username)
                    .add("luci_password", password)
                val request = Request.Builder().url(luciUrl).post(formBuilder.build()).build()
                val response = client.newCall(request).execute()
                val cookie = response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("sysauth") } ?: ""
                if (cookie.isNotEmpty()) {
                    sysAuthCookie = cookie.substringBefore(";")
                    loginStrategy = LoginStrategy.LUCI_COOKIE
                    return@withContext Result.success(Unit)
                }
            } catch (e: Exception) { /* all strategies failed */ }

            Result.failure(Exception("Login failed: all TP-Link login strategies exhausted for $ip:$port"))
        }

    // ─── logout ───────────────────────────────────────────────────────────────

    override suspend fun logout() {
        try {
            when (loginStrategy) {
                LoginStrategy.STOK_JSON ->
                    httpPost(stokUrl("admin/logout"), """{"logout":null}""")
                LoginStrategy.LUCI_COOKIE ->
                    httpGet("${baseUrl()}/cgi-bin/luci/admin/logout")
                else -> Unit
            }
        } catch (_: Exception) {}
        stok = ""
        sysAuthCookie = ""
        loginStrategy = LoginStrategy.NONE
    }

    // ─── LTE signal (not supported) ───────────────────────────────────────────

    override suspend fun getLteSignal(): Result<LteSignalData> =
        Result.failure(UnsupportedOperationException("TP-Link routers do not expose LTE signal data via this adapter"))

    // ─── Connected devices ────────────────────────────────────────────────────

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        withContext(Dispatchers.IO) {
            try {
                when (loginStrategy) {
                    LoginStrategy.STOK_JSON -> {
                        val body = """{"hosts":{"table":"host_info"}}"""
                        val (_, resp) = httpPost(stokUrl("api/hosts/list"), body)
                        parseStokDeviceList(resp)
                    }
                    LoginStrategy.LUCI_COOKIE -> {
                        val resp = httpGet("${baseUrl()}/cgi-bin/luci/admin/status/overview")
                        parseHtmlDeviceTable(resp)
                    }
                    else -> Result.failure(Exception("Not logged in"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseStokDeviceList(json: String): Result<List<ConnectedDevice>> {
        return try {
            val root = JSONObject(json)
            val hosts = root.optJSONObject("hosts")?.optJSONArray("host_info")
                ?: root.optJSONArray("host_info")
                ?: JSONArray()
            val devices = mutableListOf<ConnectedDevice>()
            for (i in 0 until hosts.length()) {
                val h = hosts.getJSONObject(i)
                val mac = h.optString("mac_addr", h.optString("mac", ""))
                val ip = h.optString("ip_addr", h.optString("ip", ""))
                val hostname = h.optString("hostname", h.optString("name", ""))
                val type = h.optString("type", "wireless")
                val band = when {
                    type == "wire" || type == "wired" -> WifiBand.WIRED
                    h.optString("band", "").contains("5") -> WifiBand.BAND_5GHZ
                    else -> WifiBand.BAND_2_4GHZ
                }
                if (mac.isNotEmpty() && ip.isNotEmpty()) {
                    devices.add(
                        ConnectedDevice(
                            mac = mac.uppercase(),
                            ip = ip,
                            hostname = hostname,
                            band = band,
                            category = DeviceCategory.UNKNOWN
                        )
                    )
                }
            }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHtmlDeviceTable(html: String): Result<List<ConnectedDevice>> {
        // Basic fallback: extract MAC/IP patterns from HTML
        val devices = mutableListOf<ConnectedDevice>()
        val macRegex = Regex("([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}")
        val ipRegex = Regex("\\b(192\\.168|10\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[01]))\\.\\d{1,3}\\.\\d{1,3}\\b")
        val macs = macRegex.findAll(html).map { it.value.uppercase() }.toList()
        val ips = ipRegex.findAll(html).map { it.value }.toList()
        for (i in macs.indices) {
            devices.add(
                ConnectedDevice(
                    mac = macs[i],
                    ip = ips.getOrElse(i) { "" },
                    band = WifiBand.UNKNOWN,
                    category = DeviceCategory.UNKNOWN
                )
            )
        }
        return Result.success(devices)
    }

    // ─── WiFi settings ────────────────────────────────────────────────────────

    override suspend fun getWifiSettings(): Result<WifiSettings> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("WiFi settings require stok login"))
            val body = """{"wireless":{"name":"wlan_host"}}"""
            val (_, resp) = httpPost(stokUrl("api/wireless/wlan_host"), body)
            val json = JSONObject(resp)
            val wireless = json.optJSONObject("wireless") ?: json
            Result.success(
                WifiSettings(
                    ssid = wireless.optString("ssid", ""),
                    password = wireless.optString("psk", ""),
                    band = wireless.optString("band", "2.4G"),
                    channel = wireless.optInt("channel", 0),
                    isEnabled = wireless.optInt("enable", 1) == 1,
                    securityMode = wireless.optString("security", "WPA2")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("WiFi settings require stok login"))
                val body = JSONObject().apply {
                    put("wireless", JSONObject().apply {
                        put("ssid", settings.ssid)
                        put("psk", settings.password)
                        put("enable", if (settings.isEnabled) 1 else 0)
                        put("security", settings.securityMode)
                        put("channel", settings.channel)
                    })
                }.toString()
                val (code, _) = httpPost(stokUrl("api/wireless/wlan_host"), body)
                if (code == 200) Result.success(Unit)
                else Result.failure(Exception("Failed to set WiFi settings, HTTP $code"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Block / Unblock ──────────────────────────────────────────────────────

    override suspend fun blockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("Device blocking requires stok login"))
            val body = """{"mac_filter":{"add":{"mac":"$mac","enable":1}}}"""
            val (code, _) = httpPost(stokUrl("api/wireless/mac_filter"), body)
            if (code == 200) Result.success(Unit)
            else Result.failure(Exception("Block device failed, HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("Device unblocking requires stok login"))
            val body = """{"mac_filter":{"delete":{"mac":"$mac"}}}"""
            val (code, _) = httpPost(stokUrl("api/wireless/mac_filter"), body)
            if (code == 200) Result.success(Unit)
            else Result.failure(Exception("Unblock device failed, HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Reboot ───────────────────────────────────────────────────────────────

    override suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (loginStrategy) {
                LoginStrategy.STOK_JSON -> {
                    val (code, _) = httpPost(stokUrl("api/admin/reboot"), """{"reboot":null}""")
                    if (code == 200) Result.success(Unit)
                    else Result.failure(Exception("Reboot failed, HTTP $code"))
                }
                LoginStrategy.LUCI_COOKIE -> {
                    httpPostForm("${baseUrl()}/cgi-bin/luci/admin/system/reboot", mapOf("action" to "reboot"))
                    Result.success(Unit)
                }
                else -> Result.failure(Exception("Not logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── SMS / USSD (not supported) ───────────────────────────────────────────

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> =
        Result.failure(UnsupportedOperationException("TP-Link routers do not support SMS"))

    override suspend fun sendSms(to: String, body: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("TP-Link routers do not support SMS"))

    override suspend fun deleteSms(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("TP-Link routers do not support SMS"))

    override suspend fun sendUssd(code: String): Result<String> =
        Result.failure(UnsupportedOperationException("TP-Link routers do not support USSD"))

    // ─── Traffic stats ────────────────────────────────────────────────────────

    override suspend fun getTrafficStats(): Result<TrafficStats> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("Traffic stats require stok login"))
            val body = """{"network":{"name":"traffic"}}"""
            val (_, resp) = httpPost(stokUrl("api/network/traffic"), body)
            val json = JSONObject(resp)
            val traffic = json.optJSONObject("network") ?: json
            Result.success(
                TrafficStats(
                    rxBytes = traffic.optLong("rx_bytes", 0L),
                    txBytes = traffic.optLong("tx_bytes", 0L)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── DSL stats (not supported) ────────────────────────────────────────────

    override suspend fun getDslStats(): Result<DslStats> =
        Result.failure(UnsupportedOperationException("DSL stats not available on this TP-Link model"))

    // ─── DNS ──────────────────────────────────────────────────────────────────

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("DNS change requires stok login"))
                val body = """{"dns":{"primary":"$primary","secondary":"$secondary"}}"""
                val (code, _) = httpPost(stokUrl("api/network/dns"), body)
                if (code == 200) Result.success(Unit)
                else Result.failure(Exception("setDns failed, HTTP $code"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Network mode / Band selection (not supported) ───────────────────────

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        Result.failure(UnsupportedOperationException("Network mode selection not supported on TP-Link WiFi routers"))

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
        Result.failure(UnsupportedOperationException("LTE band selection not supported on TP-Link WiFi routers"))

    // ─── Backup / Restore ─────────────────────────────────────────────────────

    override suspend fun downloadBackup(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (stok.isEmpty())
                return@withContext Result.failure(Exception("Not logged in with stok"))
            val url = stokUrl("api/admin/settings?form=backup")
            val request = Request.Builder().url(url).build()
            val response = buildJsonClient().newCall(request).execute()
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isNotEmpty()) Result.success(bytes)
            else Result.failure(Exception("Empty backup response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (stok.isEmpty())
                return@withContext Result.failure(Exception("Not logged in with stok"))
            val url = stokUrl("api/admin/settings?form=backup")
            val mediaType = "application/octet-stream".toMediaType()
            val requestBody = data.toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = buildJsonClient().newCall(request).execute()
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception("Restore failed, HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Security info ────────────────────────────────────────────────────────

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("Security info requires stok login"))
            val body = """{"firewall":{"name":"security"}}"""
            val (_, resp) = httpPost(stokUrl("api/firewall/security"), body)
            val json = JSONObject(resp)
            val firewall = json.optJSONObject("firewall") ?: json
            Result.success(
                RouterSecurityInfo(
                    adminPasswordChanged = true, // assume changed if logged in
                    remoteManagementEnabled = firewall.optBoolean("remote_mgmt", false),
                    upnpEnabled = firewall.optBoolean("upnp", false),
                    firewallEnabled = firewall.optBoolean("enable", true),
                    openPorts = emptyList()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── MAC filter ───────────────────────────────────────────────────────────

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("MAC filter requires stok login"))
                val modeStr = when (mode) {
                    MacFilterMode.BLACKLIST -> "black"
                    MacFilterMode.WHITELIST -> "white"
                    MacFilterMode.DISABLED -> "off"
                }
                val macArray = JSONArray(macs)
                val body = JSONObject().apply {
                    put("mac_filter", JSONObject().apply {
                        put("mode", modeStr)
                        put("macs", macArray)
                    })
                }.toString()
                val (code, _) = httpPost(stokUrl("api/wireless/mac_filter"), body)
                if (code == 200) Result.success(Unit)
                else Result.failure(Exception("setMacFilter failed, HTTP $code"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── QoS ──────────────────────────────────────────────────────────────────

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (loginStrategy != LoginStrategy.STOK_JSON)
                return@withContext Result.failure(Exception("QoS requires stok login"))
            val rulesArray = JSONArray()
            rules.forEach { rule ->
                rulesArray.put(JSONObject().apply {
                    put("mac", rule.mac)
                    put("upload_kbps", rule.uploadKbps)
                    put("download_kbps", rule.downloadKbps)
                    put("priority", rule.priority)
                })
            }
            val body = JSONObject().apply { put("qos", JSONObject().apply { put("rules", rulesArray) }) }.toString()
            val (code, _) = httpPost(stokUrl("api/qos"), body)
            if (code == 200) Result.success(Unit)
            else Result.failure(Exception("setQos failed, HTTP $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Guest WiFi ───────────────────────────────────────────────────────────

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("Guest WiFi requires stok login"))
                val body = JSONObject().apply {
                    put("wireless", JSONObject().apply {
                        put("enable", if (enabled) 1 else 0)
                        put("ssid", ssid)
                        put("psk", password)
                        put("security", "WPA2")
                    })
                }.toString()
                val (code, _) = httpPost(stokUrl("api/wireless/wlan_guest"), body)
                if (code == 200) Result.success(Unit)
                else Result.failure(Exception("setGuestWifi failed, HTTP $code"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Port forwarding ──────────────────────────────────────────────────────

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("Port forwarding requires stok login"))
                val body = JSONObject().apply {
                    put("nat", JSONObject().apply {
                        put("name", rule.name)
                        put("protocol", rule.protocol.lowercase())
                        put("external_port", rule.externalPort)
                        put("internal_ip", rule.internalIp)
                        put("internal_port", rule.internalPort)
                        put("enable", 1)
                    })
                }.toString()
                val (code, _) = httpPost(stokUrl("api/firewall/nat"), body)
                if (code == 200) Result.success(Unit)
                else Result.failure(Exception("addPortForwarding failed, HTTP $code"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
        withContext(Dispatchers.IO) {
            try {
                if (loginStrategy != LoginStrategy.STOK_JSON)
                    return@withContext Result.failure(Exception("Port forwarding requires stok login"))
                val body = """{"nat":{"table":"nat_info"}}"""
                val (_, resp) = httpPost(stokUrl("api/firewall/nat"), body)
                val root = JSONObject(resp)
                val natArray = root.optJSONObject("nat")?.optJSONArray("nat_info") ?: JSONArray()
                val rules = mutableListOf<PortForwardRule>()
                for (i in 0 until natArray.length()) {
                    val entry = natArray.getJSONObject(i)
                    rules.add(
                        PortForwardRule(
                            name = entry.optString("name", "Rule $i"),
                            protocol = entry.optString("protocol", "tcp").uppercase(),
                            externalPort = entry.optInt("external_port", 0),
                            internalIp = entry.optString("internal_ip", ""),
                            internalPort = entry.optInt("internal_port", 0)
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
        Result.failure(UnsupportedOperationException("APN configuration not supported on TP-Link WiFi routers"))

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
        Result.failure(UnsupportedOperationException("APN configuration not supported on TP-Link WiFi routers"))
}
