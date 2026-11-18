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
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Adapter for the Greenpacket D2-220G1 4G LTE Router (Vodafone Home Wireless CPE).
 *
 * Uses a dual-strategy approach:
 *  - Strategy A: JSON-RPC / CGI endpoints (ubus, LuCI, Greenpacket XML API, ZTE-style CGI)
 *  - Strategy B: WebView JS Bridge for non-standard firmware
 */
class GreenpacketD2220G1Adapter(
    private val okHttpClient: OkHttpClient
) : RouterAdapter {

    // ─── State ────────────────────────────────────────────────────────────────

    private var routerIp = ""
    private var routerPort = 80
    private var loginUsername = ""
    private var loginPassword = ""

    /** ubus session token (Strategy A / OpenWrt) */
    private var ubusSession = ""

    /** Detected firmware strategy */
    private var strategy = Strategy.UNKNOWN

    /** Cookies accumulated across requests */
    private var sessionCookies = ""

    private enum class Strategy {
        UNKNOWN,
        UBUS,       // OpenWrt ubus JSON-RPC
        LUCI,       // LuCI web UI
        XML_CGI,    // Greenpacket proprietary XML API (/xml_action.cgi)
        ZTE_CGI,    // ZTE-style CGI (/goform/...)
        REST_JSON   // /api/ REST
    }

    // ─── RouterAdapter identity ────────────────────────────────────────────────

    override val brand: RouterBrand = RouterBrand.GREENPACKET

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.SMS,
        RouterCapability.USSD,
        RouterCapability.LTE_SIGNAL,
        RouterCapability.BAND_LOCK,
        RouterCapability.WIFI_5GHZ,
        RouterCapability.REBOOT,
        RouterCapability.DNS_CHANGE,
        RouterCapability.MAC_FILTER
    )

    // ─── Preloaded APN profiles ───────────────────────────────────────────────

    private val preloadedApnProfiles: List<ApnProfile> = listOf(
        ApnProfile(name = "Vodafone Egypt", apn = "internet", username = "", password = ""),
        ApnProfile(name = "WE (Telecom Egypt)", apn = "data.te.eg", username = "", password = ""),
        ApnProfile(name = "Orange Egypt", apn = "orange.eg", username = "", password = ""),
        ApnProfile(name = "Etisalat/e&", apn = "etisalat", username = "", password = ""),
        ApnProfile(name = "Vodafone KSA", apn = "data.vodafone.com.sa", username = "", password = ""),
        ApnProfile(name = "STC", apn = "jawalnet.com.sa", username = "", password = "")
    )

    // ─── Supported LTE bands on D2-220G1 ─────────────────────────────────────

    private val supportedLteBands: List<LteBand> = listOf(
        LteBand.BAND_1, LteBand.BAND_3, LteBand.BAND_7, LteBand.BAND_8,
        LteBand.BAND_20, LteBand.BAND_28, LteBand.BAND_38, LteBand.BAND_40, LteBand.BAND_41
    )

    // ─── Crypto helpers ───────────────────────────────────────────────────────

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun base64(input: ByteArray): String =
        Base64.getEncoder().encodeToString(input)

    private fun md5Bytes(input: String): ByteArray =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))

    // ─── URL helper ───────────────────────────────────────────────────────────

    private fun baseUrl(): String =
        if (routerPort == 80) "http://$routerIp" else "http://$routerIp:$routerPort"

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private suspend fun get(path: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(baseUrl() + path)
            if (sessionCookies.isNotEmpty()) builder.addHeader("Cookie", sessionCookies)
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                captureSetCookie(resp)
                resp.body?.string() ?: ""
            }
        }

    private suspend fun postForm(path: String, formBody: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val fb = FormBody.Builder()
            formBody.forEach { (k, v) -> fb.add(k, v) }
            val request = Request.Builder()
                .url(baseUrl() + path)
                .post(fb.build())
                .apply { if (sessionCookies.isNotEmpty()) addHeader("Cookie", sessionCookies) }
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                captureSetCookie(resp)
                resp.body?.string() ?: ""
            }
        }

    private suspend fun postJson(path: String, json: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrl() + path)
                .post(body)
                .apply {
                    if (sessionCookies.isNotEmpty()) addHeader("Cookie", sessionCookies)
                    extraHeaders.forEach { (k, v) -> addHeader(k, v) }
                }
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                captureSetCookie(resp)
                resp.body?.string() ?: ""
            }
        }

    private suspend fun postXml(path: String, xml: String): String =
        withContext(Dispatchers.IO) {
            val body = xml.toRequestBody("text/xml".toMediaType())
            val request = Request.Builder()
                .url(baseUrl() + path)
                .post(body)
                .apply { if (sessionCookies.isNotEmpty()) addHeader("Cookie", sessionCookies) }
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                captureSetCookie(resp)
                resp.body?.string() ?: ""
            }
        }

    private fun captureSetCookie(response: okhttp3.Response) {
        val setCookieHeaders = response.headers.values("Set-Cookie")
        if (setCookieHeaders.isNotEmpty()) {
            val cookieParts = setCookieHeaders.map { it.substringBefore(";").trim() }
            sessionCookies = cookieParts.joinToString("; ")
        }
    }

    // ─── Fingerprinting ───────────────────────────────────────────────────────

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        // Try Greenpacket-specific XML endpoint
        try {
            val url = if (port == 80) "http://$ip" else "http://$ip:$port"
            val request = Request.Builder().url("$url/xml_action.cgi").build()
            val resp = okHttpClient.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (resp.code != 404 && (body.contains("greenpacket", ignoreCase = true) ||
                        body.contains("xml_action", ignoreCase = true) ||
                        resp.code in 200..302)) {
                return@withContext true
            }
        } catch (e: Exception) { /* try next */ }

        // Try ZTE CGI style (D2-220G1 may use this)
        try {
            val url = if (port == 80) "http://$ip" else "http://$ip:$port"
            val request = Request.Builder()
                .url("$url/goform/goform_get_cmd_process?isTest=false&cmd=modem_main_state")
                .build()
            val resp = okHttpClient.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.contains("modem_main_state") || body.contains("lte", ignoreCase = true)) {
                return@withContext true
            }
        } catch (e: Exception) { /* try next */ }

        // Try ubus
        try {
            val url = if (port == 80) "http://$ip" else "http://$ip:$port"
            val body = """{"jsonrpc":"2.0","id":1,"method":"call","params":["00000000000000000000000000000000","session","list",{}]}"""
            val reqBody = body.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$url/ubus").post(reqBody).build()
            val resp = okHttpClient.newCall(request).execute()
            val respBody = resp.body?.string() ?: ""
            resp.close()
            if (respBody.contains("jsonrpc")) return@withContext true
        } catch (e: Exception) { /* try next */ }

        // Try model page /
        try {
            val url = if (port == 80) "http://$ip" else "http://$ip:$port"
            val request = Request.Builder().url("$url/").build()
            val resp = okHttpClient.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            if (body.contains("D2-220", ignoreCase = true) ||
                body.contains("greenpacket", ignoreCase = true) ||
                body.contains("GP-", ignoreCase = true)) {
                return@withContext true
            }
        } catch (e: Exception) { /* ignore */ }

        false
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    override suspend fun login(
        ip: String, port: Int, username: String, password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        routerIp = ip
        routerPort = port
        loginUsername = username
        loginPassword = password
        sessionCookies = ""
        ubusSession = ""
        strategy = Strategy.UNKNOWN

        // Strategy 1: Try ubus OpenWrt login
        val ubusResult = tryUbusLogin(username, password)
        if (ubusResult.isSuccess) {
            strategy = Strategy.UBUS
            return@withContext ubusResult
        }

        // Strategy 2: Try ZTE CGI login (most likely for D2-220G1)
        val zteResult = tryZteCgiLogin(username, password)
        if (zteResult.isSuccess) {
            strategy = Strategy.ZTE_CGI
            return@withContext zteResult
        }

        // Strategy 3: Try Greenpacket XML CGI
        val xmlResult = tryXmlCgiLogin(username, password)
        if (xmlResult.isSuccess) {
            strategy = Strategy.XML_CGI
            return@withContext xmlResult
        }

        // Strategy 4: Try LuCI login
        val luciResult = tryLuciLogin(username, password)
        if (luciResult.isSuccess) {
            strategy = Strategy.LUCI
            return@withContext luciResult
        }

        // Strategy 5: Try REST API login
        val restResult = tryRestLogin(username, password)
        if (restResult.isSuccess) {
            strategy = Strategy.REST_JSON
            return@withContext restResult
        }

        Result.failure(Exception("All login strategies failed for $ip:$port"))
    }

    private suspend fun tryUbusLogin(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"jsonrpc":"2.0","id":1,"method":"call","params":["00000000000000000000000000000000","session","login",{"username":"$username","password":"$password"}]}"""
                val response = postJson("/ubus", body)
                val json = JSONObject(response)
                val result = json.optJSONArray("result")
                if (result != null && result.optInt(0) == 0) {
                    val sessionData = result.optJSONObject(1)
                    ubusSession = sessionData?.optString("ubus_rpc_session") ?: ""
                    if (ubusSession.isNotEmpty()) return@withContext Result.success(Unit)
                }
                Result.failure(Exception("ubus login failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun tryZteCgiLogin(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val passwordVariants = listOf(
                md5(password),
                base64(md5Bytes(password)),
                password,
                md5(username + md5(password)),
                sha256Hex(password)
            )
            for (pwdHash in passwordVariants) {
                try {
                    val response = postForm(
                        "/goform/goform_set_cmd_process",
                        mapOf(
                            "isTest" to "false",
                            "goformId" to "LOGIN",
                            "password" to pwdHash
                        )
                    )
                    if (response.contains("\"result\":\"0\"") || response.contains("\"result\": \"0\"")) {
                        return@withContext Result.success(Unit)
                    }
                } catch (e: Exception) { /* try next variant */ }
            }
            Result.failure(Exception("ZTE CGI login failed"))
        }

    private suspend fun tryXmlCgiLogin(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val xml = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <function>login</function>
    <username>$username</username>
    <password>${md5(password)}</password>
</request>"""
                val response = postXml("/xml_action.cgi", xml)
                if (response.contains("<result>success</result>", ignoreCase = true) ||
                    response.contains("<status>0</status>") ||
                    response.contains("OK", ignoreCase = true)) {
                    return@withContext Result.success(Unit)
                }
                Result.failure(Exception("XML CGI login failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun tryLuciLogin(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = postForm(
                    "/cgi-bin/luci",
                    mapOf("luci_username" to username, "luci_password" to password)
                )
                if (!response.contains("incorrect", ignoreCase = true) &&
                    sessionCookies.contains("sysauth")) {
                    return@withContext Result.success(Unit)
                }
                Result.failure(Exception("LuCI login failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun tryRestLogin(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"username":"$username","password":"${sha256Hex(password)}"}"""
                val response = postJson("/api/user/login", body)
                if (response.contains("\"code\":0") || response.contains("\"status\":\"ok\"")) {
                    return@withContext Result.success(Unit)
                }
                Result.failure(Exception("REST login failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Logout ───────────────────────────────────────────────────────────────

    override suspend fun logout() {
        try {
            when (strategy) {
                Strategy.UBUS -> ubusCall("session", "destroy", """{"ubus_rpc_session":"$ubusSession"}""")
                Strategy.ZTE_CGI -> postForm("/goform/goform_set_cmd_process", mapOf("goformId" to "LOGOUT", "isTest" to "false"))
                Strategy.XML_CGI -> postXml("/xml_action.cgi", "<request><function>logout</function></request>")
                Strategy.LUCI -> get("/cgi-bin/luci/logout")
                Strategy.REST_JSON -> postJson("/api/user/logout", "{}")
                else -> { /* no-op */ }
            }
        } catch (_: Exception) { }
        ubusSession = ""
        sessionCookies = ""
        strategy = Strategy.UNKNOWN
    }

    // ─── ubus helper ──────────────────────────────────────────────────────────

    private suspend fun ubusCall(obj: String, method: String, params: String = "{}"): JSONObject =
        withContext(Dispatchers.IO) {
            val body = """{"jsonrpc":"2.0","id":1,"method":"call","params":["$ubusSession","$obj","$method",$params]}"""
            val response = postJson("/ubus", body)
            val json = JSONObject(response)
            val result = json.optJSONArray("result")
            if (result != null && result.optInt(0) == 0) {
                result.optJSONObject(1) ?: JSONObject()
            } else {
                throw Exception("ubus call failed: $obj/$method — code ${result?.optInt(0)}")
            }
        }

    // ─── LTE Signal ───────────────────────────────────────────────────────────

    override suspend fun getLteSignal(): Result<LteSignalData> = withContext(Dispatchers.IO) {
        try {
            when (strategy) {
                Strategy.UBUS -> getLteSignalViaUbus()
                Strategy.ZTE_CGI, Strategy.UNKNOWN -> getLteSignalViaCgi()
                Strategy.XML_CGI -> getLteSignalViaXml()
                Strategy.REST_JSON -> getLteSignalViaRest()
                else -> getLteSignalViaCgi()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getLteSignalViaCgi(): Result<LteSignalData> {
        val response = get(
            "/goform/goform_get_cmd_process" +
                    "?isTest=false&cmd=modem_main_state,SINR,wan_connect_type,RSRP,RSRQ,RSSI," +
                    "PCI,cell_id,EARFCN,network_type,network_provider,bandwidth,lte_band"
        )
        return parseLteSignalFromJson(response)
    }

    private fun parseLteSignalFromJson(response: String): Result<LteSignalData> {
        return try {
            val json = JSONObject(response)
            val rsrp = json.optString("RSRP", "0").toIntOrNull() ?: 0
            val rsrq = json.optString("RSRQ", "0").toFloatOrNull() ?: 0f
            val sinr = json.optString("SINR", "0").toFloatOrNull() ?: 0f
            val rssi = json.optString("RSSI", "0").toIntOrNull() ?: 0
            val cellIdStr = json.optString("cell_id", "0")
            val cellId: Long = when {
                cellIdStr.startsWith("0x", ignoreCase = true) -> cellIdStr.substring(2).toLong(16)
                cellIdStr.startsWith("0X", ignoreCase = true) -> cellIdStr.substring(2).toLong(16)
                else -> cellIdStr.toLongOrNull() ?: 0L
            }
            val earfcn = json.optString("EARFCN", "0").toIntOrNull() ?: 0
            val pci = json.optString("PCI", "0").toIntOrNull() ?: 0
            val band = json.optString("lte_band", "0").toIntOrNull() ?: 0

            Result.success(
                LteSignalData(
                    rsrp = rsrp,
                    rsrq = rsrq,
                    sinr = sinr,
                    rssi = rssi,
                    pci = pci,
                    cellId = cellId,
                    earfcn = earfcn,
                    band = band
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getLteSignalViaUbus(): Result<LteSignalData> {
        return try {
            val wanStatus = ubusCall("network.interface.wan", "status", "{}")
            // Fallback to CGI as ubus may not expose LTE signal directly
            getLteSignalViaCgi()
        } catch (_: Exception) {
            getLteSignalViaCgi()
        }
    }

    private suspend fun getLteSignalViaXml(): Result<LteSignalData> {
        return try {
            val response = postXml(
                "/xml_action.cgi",
                "<request><function>get_lte_signal</function></request>"
            )
            val doc = Jsoup.parse(response, "", Parser.xmlParser())
            val rsrp = doc.selectFirst("rsrp,RSRP")?.text()?.toIntOrNull() ?: 0
            val rsrq = doc.selectFirst("rsrq,RSRQ")?.text()?.toFloatOrNull() ?: 0f
            val sinr = doc.selectFirst("sinr,SINR")?.text()?.toFloatOrNull() ?: 0f
            val rssi = doc.selectFirst("rssi,RSSI")?.text()?.toIntOrNull() ?: 0
            val cellId = doc.selectFirst("cell_id,cellId")?.text()?.toLongOrNull() ?: 0L
            val earfcn = doc.selectFirst("earfcn,EARFCN")?.text()?.toIntOrNull() ?: 0
            val pci = doc.selectFirst("pci,PCI")?.text()?.toIntOrNull() ?: 0
            val band = doc.selectFirst("band,lte_band")?.text()?.toIntOrNull() ?: 0
            Result.success(LteSignalData(rsrp = rsrp, rsrq = rsrq, sinr = sinr, rssi = rssi, pci = pci, cellId = cellId, earfcn = earfcn, band = band))
        } catch (e: Exception) {
            getLteSignalViaCgi()
        }
    }

    private suspend fun getLteSignalViaRest(): Result<LteSignalData> {
        return try {
            val response = get("/api/device/signal")
            parseLteSignalFromJson(response)
        } catch (e: Exception) {
            getLteSignalViaCgi()
        }
    }

    // ─── Connected Devices ────────────────────────────────────────────────────

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<ConnectedDevice>()
            var lastException: Exception? = null

            // Try ZTE CGI station_list
            try {
                val response = get("/goform/goform_get_cmd_process?isTest=false&cmd=station_list")
                val json = JSONObject(response)
                val list = json.optJSONArray("station_list")
                if (list != null && list.length() > 0) {
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        val mac = item.optString("mac_addr", "")
                        val ip = item.optString("ip_addr", "")
                        val hostname = item.optString("hostname", "")
                        val rssi = item.optString("rssi", "0").toIntOrNull() ?: 0
                        val bandStr = item.optString("type", "")
                        val band = when {
                            bandStr.contains("5G") -> WifiBand.BAND_5GHZ
                            bandStr.contains("2.4") -> WifiBand.BAND_2_4GHZ
                            else -> WifiBand.UNKNOWN
                        }
                        if (mac.isNotEmpty()) {
                            results.add(
                                ConnectedDevice(
                                    mac = mac, ip = ip, hostname = hostname,
                                    band = band, rssi = rssi
                                )
                            )
                        }
                    }
                    return@withContext Result.success(results)
                }
            } catch (e: Exception) { lastException = e }

            // Try REST API
            try {
                val response = get("/api/wlan/station-information")
                val json = JSONObject(response)
                val stations = json.optJSONArray("stations") ?: json.optJSONArray("Hosts")
                if (stations != null) {
                    for (i in 0 until stations.length()) {
                        val item = stations.getJSONObject(i)
                        val mac = item.optString("MacAddress", item.optString("mac", ""))
                        val ip = item.optString("IpAddress", item.optString("ip", ""))
                        val hostname = item.optString("HostName", item.optString("hostname", ""))
                        if (mac.isNotEmpty()) {
                            results.add(ConnectedDevice(mac = mac, ip = ip, hostname = hostname))
                        }
                    }
                    if (results.isNotEmpty()) return@withContext Result.success(results)
                }
            } catch (e: Exception) { lastException = e }

            // Try parsing DHCP clients HTML page
            try {
                val htmlPages = listOf("/dhcp_clients.asp", "/connected_device.asp", "/userRpm/AssignedIpAddrListRpm.htm")
                for (page in htmlPages) {
                    try {
                        val html = get(page)
                        val doc = Jsoup.parse(html)
                        val rows = doc.select("tr")
                        for (row in rows) {
                            val cells = row.select("td")
                            if (cells.size >= 2) {
                                val mac = cells.firstOrNull { isMacAddress(it.text()) }?.text() ?: continue
                                val ip = cells.firstOrNull { isIpAddress(it.text()) }?.text() ?: ""
                                results.add(ConnectedDevice(mac = mac, ip = ip))
                            }
                        }
                        if (results.isNotEmpty()) return@withContext Result.success(results)
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) { lastException = e }

            // Try ubus hostapd
            if (strategy == Strategy.UBUS) {
                try {
                    val clients24 = ubusCall("hostapd.wlan0", "get_clients", "{}")
                    val clientsMap = clients24.optJSONObject("clients")
                    if (clientsMap != null) {
                        for (mac in clientsMap.keys()) {
                            results.add(ConnectedDevice(mac = mac, ip = "", band = WifiBand.BAND_2_4GHZ))
                        }
                    }
                    if (results.isNotEmpty()) return@withContext Result.success(results)
                } catch (_: Exception) { }
            }

            if (results.isNotEmpty()) Result.success(results)
            else Result.failure(lastException ?: Exception("Could not retrieve connected devices"))
        }

    // ─── WiFi Settings ────────────────────────────────────────────────────────

    override suspend fun getWifiSettings(): Result<WifiSettings> = withContext(Dispatchers.IO) {
        try {
            val response = get(
                "/goform/goform_get_cmd_process?isTest=false&cmd=ssid,storagePwd,HideSSID,WifiEnable"
            )
            val json = JSONObject(response)
            Result.success(
                WifiSettings(
                    ssid2g = json.optString("ssid", ""),
                    password2g = json.optString("storagePwd", ""),
                    enabled2g = json.optString("WifiEnable", "1") == "1",
                    channel2g = 0,
                    security = "WPA2"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SET_WIFI_INFO",
                        "ssid" to settings.ssid2g,
                        "wifi_password" to settings.password2g,
                        "WifiEnable" to if (settings.enabled2g) "1" else "0"
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Device blocking ──────────────────────────────────────────────────────

    override suspend fun blockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postForm(
                "/goform/goform_set_cmd_process",
                mapOf("isTest" to "false", "goformId" to "BLACKLIST_ADD", "mac_addr" to mac)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postForm(
                "/goform/goform_set_cmd_process",
                mapOf("isTest" to "false", "goformId" to "BLACKLIST_REMOVE", "mac_addr" to mac)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Reboot ───────────────────────────────────────────────────────────────

    override suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (strategy) {
                Strategy.UBUS -> {
                    ubusCall("system", "reboot", "{}")
                    Result.success(Unit)
                }
                Strategy.ZTE_CGI, Strategy.UNKNOWN -> {
                    postForm(
                        "/goform/goform_set_cmd_process",
                        mapOf("isTest" to "false", "goformId" to "REBOOT")
                    )
                    Result.success(Unit)
                }
                Strategy.XML_CGI -> {
                    postXml("/xml_action.cgi", "<request><function>reboot</function></request>")
                    Result.success(Unit)
                }
                Strategy.REST_JSON -> {
                    postJson("/api/device/control", """{"Control":1}""")
                    Result.success(Unit)
                }
                else -> {
                    postForm(
                        "/goform/goform_set_cmd_process",
                        mapOf("isTest" to "false", "goformId" to "REBOOT")
                    )
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── SMS ──────────────────────────────────────────────────────────────────

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> = withContext(Dispatchers.IO) {
        try {
            // First get total count
            get("/goform/goform_get_cmd_process?isTest=false&cmd=sms_data_total")

            // Get actual messages
            val response = get(
                "/goform/goform_get_cmd_process?isTest=false" +
                        "&cmd=sms_page_num&pageIndex=0&perPage=500&mem_store=1&tags=10" +
                        "&order_by=order+by+id+desc"
            )
            val json = JSONObject(response)
            val messagesArray: JSONArray = json.optJSONArray("messages")
                ?: json.optJSONArray("sms_list")
                ?: return@withContext Result.success(emptyList())

            val messages = mutableListOf<SmsMessage>()
            for (i in 0 until messagesArray.length()) {
                val msg = messagesArray.getJSONObject(i)
                val id = msg.optString("id", "$i")
                val from = msg.optString("number", msg.optString("from", ""))
                val body = msg.optString("content", msg.optString("body", ""))
                val tag = msg.optString("tag", "1")
                val readStatus = msg.optString("read_status", "1")
                val timestamp = try {
                    // Try to parse date string "YYYY-MM-DD HH:MM:SS"
                    val dateStr = msg.optString("date", "")
                    if (dateStr.isNotEmpty()) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .parse(dateStr)?.time ?: System.currentTimeMillis()
                    } else System.currentTimeMillis()
                } catch (_: Exception) { System.currentTimeMillis() }

                messages.add(
                    SmsMessage(
                        id = id,
                        from = from,
                        body = body,
                        timestamp = timestamp,
                        isRead = readStatus == "1",
                        isInbox = tag == "1"
                    )
                )
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SEND_SMS",
                        "Number" to to,
                        "MessageBody" to body,
                        "encode_type" to "UNICODE",
                        "sms_time" to (System.currentTimeMillis() / 1000).toString()
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteSms(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postForm(
                "/goform/goform_set_cmd_process",
                mapOf(
                    "isTest" to "false",
                    "goformId" to "DELETE_SMS",
                    "msg_id" to id,
                    "storage_type" to "1"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── USSD ─────────────────────────────────────────────────────────────────

    override suspend fun sendUssd(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            postForm(
                "/goform/goform_set_cmd_process",
                mapOf(
                    "isTest" to "false",
                    "goformId" to "USSD_PROCESS",
                    "USSD_operator" to code
                )
            )
            // Poll for response (may need a delay)
            kotlinx.coroutines.delay(3000)
            val response = get("/goform/goform_get_cmd_process?isTest=false&cmd=ussd_data_info")
            val json = JSONObject(response)
            val ussdResponse = json.optString("ussd_data_info", json.optString("ussd_response", ""))
            Result.success(ussdResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Traffic ──────────────────────────────────────────────────────────────

    override suspend fun getTrafficStats(): Result<TrafficStats> = withContext(Dispatchers.IO) {
        try {
            val response = get("/goform/goform_get_cmd_process?isTest=false&cmd=web_data_information")
            val json = JSONObject(response)
            val rx = json.optString("current_download_data", json.optString("rx_bytes", "0"))
                .replace(",", "").toLongOrNull() ?: 0L
            val tx = json.optString("current_upload_data", json.optString("tx_bytes", "0"))
                .replace(",", "").toLongOrNull() ?: 0L
            Result.success(TrafficStats(rxBytes = rx, txBytes = tx))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── DSL Stats — not applicable ───────────────────────────────────────────

    override suspend fun getDslStats(): Result<DslStats> =
        Result.failure(Exception("DSL stats not applicable for Greenpacket D2-220G1 (4G LTE router)"))

    // ─── DNS ──────────────────────────────────────────────────────────────────

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "WAN_DNS_INFO",
                        "prefer_dns_manual" to "1",
                        "prefer_dns" to primary,
                        "standby_dns" to secondary
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Network mode ─────────────────────────────────────────────────────────

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val modeCode = when (mode) {
                    NetworkMode.LTE_ONLY -> "LTE_ONLY"
                    NetworkMode.WCDMA_ONLY -> "WCDMA_ONLY"
                    NetworkMode.GSM_ONLY -> "GSM_ONLY"
                    NetworkMode.LTE_WCDMA -> "LTE_WCDMA"
                    NetworkMode.WCDMA_GSM,
                    NetworkMode.NR_LTE,
                    NetworkMode.NR_ONLY,
                    NetworkMode.AUTO -> "AUTO"
                }
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SET_CONNECTION_MODE",
                        "conn_mode" to "auto_dial",
                        "net_select" to modeCode
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Band selection ───────────────────────────────────────────────────────

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Calculate bitmask — ZTE-style band bitmask
                val bandBitmask = bands.fold(0L) { acc, band ->
                    acc or when (band) {
                        LteBand.BAND_1 -> 0x1L
                        LteBand.BAND_3 -> 0x4L
                        LteBand.BAND_7 -> 0x40L
                        LteBand.BAND_8 -> 0x80L
                        LteBand.BAND_20 -> 0x80000L
                        LteBand.BAND_28 -> 0x8000000L
                        LteBand.BAND_38 -> 0x2000000000L
                        LteBand.BAND_40 -> 0x8000000000L
                        LteBand.BAND_41 -> 0x10000000000L
                    }
                }
                val bitmaskHex = "0x${bandBitmask.toString(16).uppercase()}"
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SET_LTE_BAND",
                        "lte_band" to bitmaskHex
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Backup / Restore — not reliably supported ────────────────────────────

    override suspend fun downloadBackup(): Result<ByteArray> =
        Result.failure(Exception("Configuration backup not supported on Greenpacket D2-220G1 firmware"))

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> =
        Result.failure(Exception("Configuration restore not supported on Greenpacket D2-220G1 firmware"))

    // ─── Security Info ────────────────────────────────────────────────────────

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = get(
                    "/goform/goform_get_cmd_process?isTest=false&cmd=firewall_switch,remote_mgt_enable,upnp_switch"
                )
                val json = JSONObject(response)
                Result.success(
                    RouterSecurityInfo(
                        defaultCredentialsChanged = true, // Can't easily detect this
                        remoteManagementEnabled = json.optString("remote_mgt_enable", "0") == "1",
                        uPnPEnabled = json.optString("upnp_switch", "0") == "1",
                        firewallEnabled = json.optString("firewall_switch", "1") == "1"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── MAC Filter ───────────────────────────────────────────────────────────

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val modeCode = when (mode) {
                    MacFilterMode.BLACKLIST -> "1"
                    MacFilterMode.WHITELIST -> "2"
                    MacFilterMode.DISABLED -> "0"
                }
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SET_MAC_FILTER_MODE",
                        "filter_mode" to modeCode,
                        "filter_mac_list" to macs.joinToString(";")
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── QoS — basic support ──────────────────────────────────────────────────

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> =
        Result.failure(Exception("QoS configuration not supported on Greenpacket D2-220G1 via current API"))

    // ─── Guest WiFi ───────────────────────────────────────────────────────────

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "SET_GUEST_WIFI",
                        "guest_ssid" to ssid,
                        "guest_wifi_password" to password,
                        "guest_wifi_enable" to if (enabled) "1" else "0"
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Port Forwarding — not supported on CPE ───────────────────────────────

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postForm(
                    "/goform/goform_set_cmd_process",
                    mapOf(
                        "isTest" to "false",
                        "goformId" to "ADD_PORT_MAP_CFG",
                        "nat_port_map_name" to rule.name,
                        "nat_port_map_protocol" to rule.protocol.toWireString(),
                        "nat_port_map_wan_start_port" to rule.externalPort.toString(),
                        "nat_port_map_wan_end_port" to rule.externalPort.toString(),
                        "nat_port_map_lan_host_ip" to rule.internalIp,
                        "nat_port_map_lan_start_port" to rule.internalPort.toString()
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
        withContext(Dispatchers.IO) {
            try {
                val response = get("/goform/goform_get_cmd_process?isTest=false&cmd=nat_port_mapping_list")
                val json = JSONObject(response)
                val list = json.optJSONArray("nat_port_mapping_list") ?: return@withContext Result.success(emptyList())
                val rules = mutableListOf<PortForwardRule>()
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    rules.add(
                        PortForwardRule(
                            name = item.optString("name", "Rule$i"),
                            protocol = PortProtocol.fromString(item.optString("protocol", "TCP")),
                            externalPort = item.optString("wan_start_port", "0").toIntOrNull() ?: 0,
                            internalIp = item.optString("lan_host_ip", ""),
                            internalPort = item.optString("lan_start_port", "0").toIntOrNull() ?: 0
                        )
                    )
                }
                Result.success(rules)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── APN Profiles ─────────────────────────────────────────────────────────

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
        Result.success(preloadedApnProfiles)

    override suspend fun setApn(apn: ApnProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            postForm(
                "/goform/goform_set_cmd_process",
                mapOf(
                    "isTest" to "false",
                    "goformId" to "SET_APN_INFO",
                    "apn_name" to apn.name,
                    "apn" to apn.apn,
                    "ppp_username" to apn.username,
                    "ppp_passwd" to apn.password,
                    "ppp_auth_mode" to "0"
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── WebView Bridge ───────────────────────────────────────────────────────

    /**
     * Provides JavaScript injection scripts for WebView-based fallback when
     * Strategy A (direct API) fails.
     */
    class GreenpacketWebViewBridge(private val routerIp: String) {

        /** JavaScript to inject into the router login page for auto-login */
        fun getLoginScript(username: String, password: String): String = """
            (function() {
                var user = document.querySelector('input[name="Username"],input[name="user"],#loginUser,#username,input[type="text"]');
                var pass = document.querySelector('input[name="Password"],input[name="pwd"],#loginPwd,#password,input[type="password"]');
                if(user) user.value = '${username.replace("'", "\\'")}';
                if(pass) pass.value = '${password.replace("'", "\\'")}';
                var btn = document.querySelector('input[type="submit"],button[type="submit"],.loginBtn,#loginBtn,.btn-primary');
                if(btn) btn.click();
                return JSON.stringify({userFound: !!user, passFound: !!pass, btnFound: !!btn});
            })();
        """.trimIndent()

        /** JavaScript to extract LTE signal data from router admin page DOM */
        fun getSignalExtractScript(): String = """
            (function() {
                var result = {};
                var rsrp = document.querySelector('#rsrp,#RSRP,[class*="rsrp"],[id*="signal_rsrp"],#signal_rsrp');
                var sinr = document.querySelector('#sinr,#SINR,[class*="sinr"]');
                var rsrq = document.querySelector('#rsrq,#RSRQ,[class*="rsrq"]');
                var rssi = document.querySelector('#rssi,#RSSI,[class*="rssi"]');
                var pci  = document.querySelector('#pci,#PCI,[class*="pci"]');
                var cellId = document.querySelector('#cell_id,#cellId,[class*="cell_id"]');
                var band = document.querySelector('#lte_band,#band,[class*="lte_band"]');
                if(rsrp) result.rsrp = rsrp.textContent.trim();
                if(sinr) result.sinr = sinr.textContent.trim();
                if(rsrq) result.rsrq = rsrq.textContent.trim();
                if(rssi) result.rssi = rssi.textContent.trim();
                if(pci)  result.pci  = pci.textContent.trim();
                if(cellId) result.cell_id = cellId.textContent.trim();
                if(band) result.band = band.textContent.trim();
                // Hook XMLHttpRequest to intercept API responses
                if(!window._nmHooked) {
                    window._nmHooked = true;
                    window._nmLastXhrResponse = '';
                    var origOpen = XMLHttpRequest.prototype.open;
                    var origSend = XMLHttpRequest.prototype.send;
                    XMLHttpRequest.prototype.open = function(m, url) {
                        this._nmUrl = url;
                        return origOpen.apply(this, arguments);
                    };
                    XMLHttpRequest.prototype.send = function() {
                        this.addEventListener('load', function() {
                            if(this._nmUrl && (this._nmUrl.includes('signal') || this._nmUrl.includes('RSRP'))) {
                                window._nmLastXhrResponse = this.responseText;
                            }
                        });
                        return origSend.apply(this, arguments);
                    };
                }
                if(window._nmLastXhrResponse) {
                    try {
                        var xhrData = JSON.parse(window._nmLastXhrResponse);
                        if(xhrData.RSRP) result.rsrp = xhrData.RSRP;
                        if(xhrData.SINR) result.sinr = xhrData.SINR;
                        if(xhrData.RSRQ) result.rsrq = xhrData.RSRQ;
                        if(xhrData.RSSI) result.rssi = xhrData.RSSI;
                    } catch(e) {}
                }
                return JSON.stringify(result);
            })();
        """.trimIndent()

        /** JavaScript to extract connected devices from router admin page */
        fun getDevicesExtractScript(): String = """
            (function() {
                var devices = [];
                var rows = document.querySelectorAll('table tr, .device-row, .client-row');
                rows.forEach(function(row) {
                    var cells = row.querySelectorAll('td');
                    if(cells.length >= 2) {
                        var macPattern = /([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}/;
                        var ipPattern = /\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/;
                        var text = row.innerText;
                        var mac = text.match(macPattern);
                        var ip = text.match(ipPattern);
                        if(mac) {
                            devices.push({mac: mac[0], ip: ip ? ip[0] : ''});
                        }
                    }
                });
                return JSON.stringify(devices);
            })();
        """.trimIndent()

        /** Inject mobile viewport CSS for better readability on Android WebView */
        fun getViewportInjectionScript(): String = """
            javascript:(function(){
                var existing = document.getElementById('nm-viewport');
                if(existing) return;
                var meta = document.createElement('meta');
                meta.id = 'nm-viewport';
                meta.name = 'viewport';
                meta.content = 'width=device-width, initial-scale=1, maximum-scale=1';
                document.head.appendChild(meta);
                var style = document.createElement('style');
                style.id = 'nm-style';
                style.textContent = 'body{max-width:100%!important;overflow-x:hidden!important;}' +
                    'table{width:100%!important;font-size:12px!important;word-break:break-all!important;}' +
                    'img{max-width:100%!important;height:auto!important;}' +
                    'input,select,button{font-size:14px!important;min-height:36px!important;}';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        /** Dark mode toggle CSS injection */
        fun getDarkModeToggleScript(): String = """
            javascript:(function(){
                var existing = document.getElementById('nm-dark');
                if(existing){existing.remove();return;}
                var style = document.createElement('style');
                style.id = 'nm-dark';
                style.textContent = 'html,body,div,td,th,tr,table,form,input,select,textarea,button,span,p,a{' +
                    'background-color:#1a1a2e!important;color:#e0e0e0!important;border-color:#444!important;}' +
                    'a{color:#4fc3f7!important;}' +
                    'input,select,textarea,button{background-color:#2d2d44!important;color:#e0e0e0!important;}';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        /** Admin page URL for this router */
        fun getAdminUrl(): String = "http://$routerIp"

        /** Signal page URL */
        fun getSignalPageUrl(): String = "http://$routerIp/status.html"

        /** Connected devices page URL */
        fun getDevicesPageUrl(): String = "http://$routerIp/connected_device.asp"
    }

    // ─── Utility helpers ──────────────────────────────────────────────────────

    private fun isMacAddress(text: String): Boolean =
        text.trim().matches(Regex("([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}"))

    private fun isIpAddress(text: String): Boolean =
        text.trim().matches(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"))
}
