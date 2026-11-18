package com.netmaster.pro.data.adapter

import com.netmaster.pro.data.model.*
import com.netmaster.pro.domain.adapter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * RouterAdapter implementation for Huawei 4G CPE routers:
 * B310, B315, B525, B612, B618, B628 (LTE CPEs)
 * HG630, HG633, DG8045 (VDSL routers)
 *
 * Uses Huawei's proprietary XML-based REST API at http://{ip}/api/
 */
class HuaweiAdapter(private val okHttpClient: OkHttpClient) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.HUAWEI

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.LTE_SIGNAL,
        RouterCapability.SMS,
        RouterCapability.REBOOT,
        RouterCapability.WIFI_5GHZ,
        RouterCapability.GUEST_WIFI,
        RouterCapability.MAC_FILTER,
        RouterCapability.PORT_FORWARDING,
        RouterCapability.DNS_CHANGE,
        RouterCapability.CONFIG_BACKUP,
        RouterCapability.DSL_STATS,
        RouterCapability.BAND_LOCK,
        RouterCapability.QOS
    )

    private var routerIp: String = ""
    private var routerPort: Int = 80
    private var sessionToken: String = ""
    private var cookies: String = ""

    private val XML_CONTENT_TYPE = "application/xml".toMediaType()
    private val XML_CONTENT_TYPE_STR = "application/xml"

    private fun baseUrl(): String = "http://$routerIp:$routerPort"

    // ─────────────────────────── Hashing helpers ─────────────────────────────

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun md5Base64(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val md5Bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(md5Bytes)
    }

    // ─────────────────────────── HTTP helpers ────────────────────────────────

    private fun buildClient(): OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun xmlRequestBuilder(path: String): Request.Builder {
        val builder = Request.Builder()
            .url("${baseUrl()}$path")
            .header("Content-Type", XML_CONTENT_TYPE_STR)
            .header("Accept", "application/xml, text/html, */*")
            .header("X-Requested-With", "XMLHttpRequest")
        if (sessionToken.isNotEmpty()) {
            builder.header("__RequestVerificationToken", sessionToken)
        }
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies)
        }
        return builder
    }

    private suspend fun getXml(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = xmlRequestBuilder(path).get().build()
            val response = buildClient().newCall(request).execute()
            // Capture new session token if present
            val newToken = response.header("__RequestVerificationToken")
            if (!newToken.isNullOrEmpty()) sessionToken = newToken
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) Result.success(body)
            else Result.failure(Exception("HTTP ${response.code}: $body"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun postXml(path: String, xmlBody: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = xmlBody.toRequestBody(XML_CONTENT_TYPE)
            val request = xmlRequestBuilder(path).post(body).build()
            val response = buildClient().newCall(request).execute()
            val newToken = response.header("__RequestVerificationToken")
            if (!newToken.isNullOrEmpty()) sessionToken = newToken
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) Result.success(responseBody)
            else Result.failure(Exception("HTTP ${response.code}: $responseBody"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseXmlText(xml: String, tag: String): String {
        return try {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            doc.selectFirst(tag)?.text()?.trim() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun isXmlSuccess(xml: String): Boolean {
        val code = parseXmlText(xml, "code")
        return code == "0" || code.isEmpty()
    }

    // ─────────────────────────── Fingerprint ─────────────────────────────────

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = buildClient()
            val request = Request.Builder()
                .url("http://$ip:$port/api/device/information")
                .header("Accept", "application/xml")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            // Huawei returns XML with <response> containing <DeviceName>
            body.contains("<DeviceName>", ignoreCase = true) ||
                body.contains("<Manufacturer>HUAWEI", ignoreCase = true) ||
                response.header("Server")?.contains("mini_httpd", ignoreCase = true) == true
        } catch (e: Exception) { false }
    }

    // ─────────────────────────── Login ───────────────────────────────────────

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            routerIp = ip
            routerPort = port

            // Step 1: Get the initial session token from the homepage
            try {
                val initReq = Request.Builder()
                    .url("${baseUrl()}/")
                    .get()
                    .build()
                val initResp = buildClient().newCall(initReq).execute()
                val initToken = initResp.header("__RequestVerificationToken")
                if (!initToken.isNullOrEmpty()) sessionToken = initToken
                // Also capture cookies
                val setCookies = initResp.headers("Set-Cookie")
                cookies = setCookies.joinToString("; ") { it.substringBefore(";") }
            } catch (e: Exception) { /* continue without initial token */ }

            // Try SHA256 first (newer firmware), then MD5+Base64 (older)
            val passwordHashes = listOf(
                sha256Hex(password),          // newer firmware
                md5Base64(password),           // older firmware
                md5Hex(password),              // some variants
                Base64.getEncoder().encodeToString(password.toByteArray()) // base64 plain
            )

            for (hash in passwordHashes) {
                val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Username>$username</Username>
    <Password>$hash</Password>
    <password_type>4</password_type>
</request>"""
                val result = postXml("/api/user/login", xmlBody)
                if (result.isSuccess) {
                    val body = result.getOrDefault("")
                    if (isXmlSuccess(body) || body.contains("OK", ignoreCase = true)) {
                        return@withContext Result.success(Unit)
                    }
                }
            }
            Result.failure(Exception("Login failed — invalid credentials or unsupported firmware"))
        }

    override suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        try {
            postXml("/api/user/logout", "<request><Logout>1</Logout></request>")
        } catch (e: Exception) { /* ignore */ }
        sessionToken = ""
        cookies = ""
    }

    // ─────────────────────────── LTE Signal ──────────────────────────────────

    override suspend fun getLteSignal(): Result<LteSignalData> = withContext(Dispatchers.IO) {
        val result = getXml("/api/device/signal")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            val rsrp = doc.selectFirst("Rsrp")?.text()?.trim()?.toIntOrNull()
                ?: doc.selectFirst("rsrp")?.text()?.trim()?.toIntOrNull() ?: 0
            val rsrq = doc.selectFirst("Rsrq")?.text()?.trim()?.toFloatOrNull()
                ?: doc.selectFirst("rsrq")?.text()?.trim()?.toFloatOrNull() ?: 0f
            val sinr = doc.selectFirst("Sinr")?.text()?.trim()?.toFloatOrNull()
                ?: doc.selectFirst("sinr")?.text()?.trim()?.toFloatOrNull() ?: 0f
            val rssi = doc.selectFirst("Rssi")?.text()?.trim()?.toIntOrNull()
                ?: doc.selectFirst("rssi")?.text()?.trim()?.toIntOrNull() ?: 0
            val pci = doc.selectFirst("pci")?.text()?.trim()?.toIntOrNull()
                ?: doc.selectFirst("Pci")?.text()?.trim()?.toIntOrNull() ?: 0

            val cellIdStr = doc.selectFirst("cell_id")?.text()?.trim()
                ?: doc.selectFirst("CellID")?.text()?.trim() ?: "0"
            val cellId = when {
                cellIdStr.startsWith("0x", ignoreCase = true) ->
                    cellIdStr.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
                else -> cellIdStr.toLongOrNull() ?: 0L
            }

            val earfcn = doc.selectFirst("earfcn")?.text()?.trim()?.toIntOrNull()
                ?: doc.selectFirst("Earfcn")?.text()?.trim()?.toIntOrNull() ?: 0
            val band = doc.selectFirst("band")?.text()?.trim()?.toIntOrNull()
                ?: doc.selectFirst("Band")?.text()?.trim()
                    ?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0

            // Check for Carrier Aggregation
            val caStr = doc.selectFirst("CurrentDlBandwidth")?.text()?.trim() ?: ""
            val caActive = caStr.contains("+") || doc.selectFirst("ca_state")?.text() == "1"

            Result.success(LteSignalData(
                rsrp = rsrp,
                rsrq = rsrq,
                sinr = sinr,
                rssi = rssi,
                pci = pci,
                cellId = cellId,
                earfcn = earfcn,
                band = band,
                caActive = caActive
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse signal data: ${e.message}"))
        }
    }

    // ─────────────────────────── Connected Devices ───────────────────────────

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        val result = getXml("/api/wlan/host-list")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val hosts = doc.select("Host")
            val devices = hosts.mapNotNull { host ->
                val mac = host.selectFirst("MacAddress")?.text()?.trim() ?: return@mapNotNull null
                val ip = host.selectFirst("IpAddress")?.text()?.trim() ?: ""
                val hostname = host.selectFirst("HostName")?.text()?.trim() ?: ""
                val bandStr = host.selectFirst("AssociatedSsid")?.text()?.trim()
                    ?: host.selectFirst("BandWidth")?.text()?.trim() ?: ""
                val rssi = host.selectFirst("Rssi")?.text()?.trim()?.toIntOrNull() ?: 0
                val interfaceType = host.selectFirst("InterfaceType")?.text()?.trim() ?: "wifi"

                val band = when {
                    interfaceType.equals("ethernet", ignoreCase = true) -> WifiBand.WIRED
                    bandStr.contains("5G", ignoreCase = true) -> WifiBand.BAND_5GHZ
                    bandStr.contains("2.4", ignoreCase = true) -> WifiBand.BAND_2_4GHZ
                    else -> WifiBand.UNKNOWN
                }

                ConnectedDevice(
                    mac = mac,
                    ip = ip,
                    hostname = hostname,
                    band = band,
                    rssi = rssi
                )
            }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse device list: ${e.message}"))
        }
    }

    // ─────────────────────────── Wi-Fi Settings ──────────────────────────────

    override suspend fun getWifiSettings(): Result<WifiSettings> = withContext(Dispatchers.IO) {
        val result = getXml("/api/wlan/basic-settings")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val ssid = doc.selectFirst("WifiSsid")?.text()?.trim()
                ?: doc.selectFirst("Ssid")?.text()?.trim() ?: ""
            val password = doc.selectFirst("WifiWpaPreSharedKey")?.text()?.trim()
                ?: doc.selectFirst("WifiKey")?.text()?.trim() ?: ""
            val channel = doc.selectFirst("WifiChannel")?.text()?.trim()?.toIntOrNull() ?: 0
            val enabled = doc.selectFirst("WifiEnable")?.text()?.trim() == "1"
            val security = doc.selectFirst("WifiWpaEncryptionModes")?.text()?.trim() ?: "WPA2PSK"

            Result.success(WifiSettings(
                ssid = ssid,
                password = password,
                band = "2.4GHz",
                channel = channel,
                isEnabled = enabled,
                securityMode = security
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse WiFi settings: ${e.message}"))
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val enabledVal = if (settings.isEnabled) "1" else "0"
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <WifiSsid>${settings.ssid}</WifiSsid>
    <WifiChannel>${settings.channel}</WifiChannel>
    <WifiHide>0</WifiHide>
    <WifiCountry>US</WifiCountry>
    <WifiEnable>${enabledVal}</WifiEnable>
    <WifiWpaEncryptionModes>${settings.securityMode}</WifiWpaEncryptionModes>
    <WifiWpaPreSharedKey>${settings.password}</WifiWpaPreSharedKey>
</request>"""
        val result = postXml("/api/wlan/basic-settings", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to update WiFi settings"))
    }

    // ─────────────────────────── Device Block / Unblock ──────────────────────

    override suspend fun blockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Ssids>
        <Ssid>ssid0</Ssid>
    </Ssids>
    <MacAddress>${mac.uppercase()}</MacAddress>
    <Blocked>1</Blocked>
</request>"""
        val result = postXml("/api/wlan/mac-filter", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to block device $mac"))
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <MacAddress>${mac.uppercase()}</MacAddress>
    <Blocked>0</Blocked>
</request>"""
        val result = postXml("/api/wlan/mac-filter", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to unblock device $mac"))
    }

    // ─────────────────────────── Reboot ──────────────────────────────────────

    override suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request><Control>1</Control></request>"""
        val result = postXml("/api/device/control", xmlBody)
        // Reboot may cut connection immediately — treat connection error as success
        if (result.isSuccess || result.exceptionOrNull()?.message?.contains("closed", ignoreCase = true) == true) {
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Reboot failed"))
        }
    }

    // ─────────────────────────── SMS ─────────────────────────────────────────

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> = withContext(Dispatchers.IO) {
        // First fetch inbox
        val inboxResult = getXml("/api/sms/sms-list")
        if (inboxResult.isFailure) {
            // Try alternative endpoint
            val altResult = getXml("/api/sms/sms-list?pageindex=1&readcount=20&boxtype=1&password=")
            if (altResult.isFailure) return@withContext Result.failure(inboxResult.exceptionOrNull()!!)
        }

        try {
            val xml = inboxResult.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val messages = doc.select("Message").map { msg ->
                val id = msg.selectFirst("Index")?.text()?.trim() ?: ""
                val from = msg.selectFirst("Phone")?.text()?.trim() ?: ""
                val body = msg.selectFirst("Content")?.text()?.trim() ?: ""
                val dateStr = msg.selectFirst("Date")?.text()?.trim() ?: ""
                val timestamp = try {
                    // Huawei date format: "2024-01-15 10:30:00"
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) { System.currentTimeMillis() }
                val isRead = msg.selectFirst("Status")?.text()?.trim() == "1"
                SmsMessage(id = id, from = from, body = body, timestamp = timestamp, isRead = isRead)
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse SMS list: ${e.message}"))
        }
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Index>-1</Index>
    <Phones><Phone>$to</Phone></Phones>
    <Sca></Sca>
    <Content>$body</Content>
    <Length>${body.length}</Length>
    <Reserved>1</Reserved>
    <Date>$timestamp</Date>
</request>"""
        val result = postXml("/api/sms/send-sms", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to send SMS"))
    }

    override suspend fun deleteSms(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Index>$id</Index>
</request>"""
        val result = postXml("/api/sms/delete-sms", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to delete SMS $id"))
    }

    override suspend fun sendUssd(code: String): Result<String> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <content>$code</content>
    <codeType>CodeType</codeType>
    <timeout></timeout>
</request>"""
        val result = postXml("/api/ussd/send", xmlBody)
        if (result.isFailure) return@withContext Result.failure(
            Exception("USSD not supported or failed: ${result.exceptionOrNull()?.message}")
        )
        try {
            val xml = result.getOrDefault("")
            val response = parseXmlText(xml, "content")
            if (response.isNotEmpty()) Result.success(response)
            else Result.failure(Exception("Empty USSD response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────── Traffic Stats ───────────────────────────────

    override suspend fun getTrafficStats(): Result<TrafficStats> = withContext(Dispatchers.IO) {
        val result = getXml("/api/monitoring/traffic-statistics")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val rxBytes = doc.selectFirst("CurrentDownload")?.text()?.trim()?.toLongOrNull()
                ?: doc.selectFirst("TotalDownload")?.text()?.trim()?.toLongOrNull() ?: 0L
            val txBytes = doc.selectFirst("CurrentUpload")?.text()?.trim()?.toLongOrNull()
                ?: doc.selectFirst("TotalUpload")?.text()?.trim()?.toLongOrNull() ?: 0L
            Result.success(TrafficStats(rxBytes = rxBytes, txBytes = txBytes))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse traffic stats: ${e.message}"))
        }
    }

    // ─────────────────────────── DSL Stats ───────────────────────────────────

    override suspend fun getDslStats(): Result<DslStats> = withContext(Dispatchers.IO) {
        // Try DSL diagnose endpoint (HG series)
        val result = getXml("/api/device/DSLDiagnose")
        val xml = if (result.isSuccess) result.getOrDefault("") else {
            // Try HTML fallback
            val htmlResult = getXml("/dslStats.html")
            htmlResult.getOrDefault("")
        }

        if (xml.isEmpty()) {
            return@withContext Result.failure(Exception("DSL stats not available on this Huawei model"))
        }

        try {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val downstream = doc.selectFirst("DownstreamMaxRate")?.text()?.toDoubleOrNull()
                ?: doc.selectFirst("ActualDataRateDownstream")?.text()?.toDoubleOrNull() ?: 0.0
            val upstream = doc.selectFirst("UpstreamMaxRate")?.text()?.toDoubleOrNull()
                ?: doc.selectFirst("ActualDataRateUpstream")?.text()?.toDoubleOrNull() ?: 0.0
            val snrDown = doc.selectFirst("DownstreamNoiseMargin")?.text()?.toDoubleOrNull() ?: 0.0
            val snrUp = doc.selectFirst("UpstreamNoiseMargin")?.text()?.toDoubleOrNull() ?: 0.0
            val attDown = doc.selectFirst("DownstreamAttenuation")?.text()?.toDoubleOrNull() ?: 0.0
            val attUp = doc.selectFirst("UpstreamAttenuation")?.text()?.toDoubleOrNull() ?: 0.0

            Result.success(DslStats(
                downstream = downstream,
                upstream = upstream,
                snrDown = snrDown,
                snrUp = snrUp,
                attenuationDown = attDown,
                attenuationUp = attUp
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse DSL stats: ${e.message}"))
        }
    }

    // ─────────────────────────── DNS ─────────────────────────────────────────

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <DnsStatus>1</DnsStatus>
    <PrimaryDns>$primary</PrimaryDns>
    <SecondaryDns>$secondary</SecondaryDns>
</request>"""
        // Try multiple possible endpoints
        var result = postXml("/api/dhcp/settings", xmlBody)
        if (result.isFailure || !isXmlSuccess(result.getOrDefault(""))) {
            result = postXml("/api/wlan/dns-config", xmlBody)
        }
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to set DNS servers"))
    }

    // ─────────────────────────── Network Mode ────────────────────────────────

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> = withContext(Dispatchers.IO) {
        val modeCode = when (mode) {
            NetworkMode.LTE_ONLY -> "03"
            NetworkMode.WCDMA_ONLY -> "02"
            NetworkMode.GSM_ONLY -> "01"
            NetworkMode.AUTO -> "00"
        }
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <NetworkMode>$modeCode</NetworkMode>
    <NetworkBand>3FFFFFFF</NetworkBand>
    <LTEBand>7FFFFFFFFFFFFFFF</LTEBand>
</request>"""
        val result = postXml("/api/net/net-mode", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to set network mode"))
    }

    // ─────────────────────────── Band Selection ──────────────────────────────

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> = withContext(Dispatchers.IO) {
        // Huawei uses hex bitmask for LTE bands
        // Bit positions: B1=bit0, B3=bit2, B7=bit6, B8=bit7, B20=bit19, B28=bit27, B38=bit37, B40=bit39, B41=bit40
        val bandBitMap = mapOf(
            LteBand.B1 to 0x1L,
            LteBand.B3 to 0x4L,
            LteBand.B7 to 0x40L,
            LteBand.B8 to 0x80L,
            LteBand.B20 to 0x80000L,
            LteBand.B28 to 0x8000000L,
            LteBand.B38 to 0x2000000000L,
            LteBand.B40 to 0x8000000000L,
            LteBand.B41 to 0x10000000000L
        )
        val lteBandMask = bands.fold(0L) { acc, band -> acc or (bandBitMap[band] ?: 0L) }
        val lteBandHex = "%016X".format(lteBandMask)

        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <NetworkMode>03</NetworkMode>
    <NetworkBand>3FFFFFFF</NetworkBand>
    <LTEBand>$lteBandHex</LTEBand>
</request>"""
        val result = postXml("/api/net/net-mode", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to set band selection"))
    }

    // ─────────────────────────── Backup / Restore ────────────────────────────

    override suspend fun downloadBackup(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = xmlRequestBuilder("/api/config/backup").get().build()
            val response = buildClient().newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                Result.success(bytes)
            } else {
                Result.failure(Exception("Backup download failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mediaType = "application/octet-stream".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("config", "backup.bin",
                    data.toRequestBody(mediaType))
                .build()
            val request = xmlRequestBuilder("/api/config/restore")
                .post(requestBody)
                .build()
            val response = buildClient().newCall(request).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Restore failed: HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─────────────────────────── Security Info ───────────────────────────────

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> = withContext(Dispatchers.IO) {
        try {
            val deviceInfo = getXml("/api/device/information").getOrDefault("")
            val securityInfo = getXml("/api/security/firewall-switch").getOrDefault("")

            val doc1 = Jsoup.parse(deviceInfo, "", Parser.xmlParser())
            val doc2 = Jsoup.parse(securityInfo, "", Parser.xmlParser())

            val firewallEnabled = doc2.selectFirst("FirewallSwitch")?.text()?.trim() == "1"
            val remoteManagement = doc2.selectFirst("RemoteAccess")?.text()?.trim() == "1"

            // Try to get open port info
            val upnpXml = getXml("/api/security/upnp-settings").getOrDefault("")
            val upnpDoc = Jsoup.parse(upnpXml, "", Parser.xmlParser())
            val upnpEnabled = upnpDoc.selectFirst("UPnPEnable")?.text()?.trim() == "1"

            Result.success(RouterSecurityInfo(
                adminPasswordChanged = true, // Cannot determine from API; assume changed
                remoteManagementEnabled = remoteManagement,
                upnpEnabled = upnpEnabled,
                firewallEnabled = firewallEnabled,
                openPorts = emptyList()
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get security info: ${e.message}"))
        }
    }

    // ─────────────────────────── MAC Filter ──────────────────────────────────

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            val modeCode = when (mode) {
                MacFilterMode.BLACKLIST -> "1"
                MacFilterMode.WHITELIST -> "2"
                MacFilterMode.DISABLED -> "0"
            }
            val macEntries = macs.joinToString("") { mac ->
                "<Ssid><MacAddress>${mac.uppercase()}</MacAddress></Ssid>"
            }
            val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Ssids>$macEntries</Ssids>
    <MacFilterStatus>$modeCode</MacFilterStatus>
</request>"""
            val result = postXml("/api/wlan/mac-filter", xmlBody)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
            else Result.failure(Exception("Failed to set MAC filter"))
        }

    // ─────────────────────────── QoS ─────────────────────────────────────────

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = withContext(Dispatchers.IO) {
        val ruleEntries = rules.joinToString("") { rule ->
            """<QosRule>
    <Mac>${rule.mac}</Mac>
    <DownRate>${rule.downloadKbps}</DownRate>
    <UpRate>${rule.uploadKbps}</UpRate>
    <Priority>${rule.priority}</Priority>
</QosRule>"""
        }
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <QosRules>$ruleEntries</QosRules>
</request>"""
        val result = postXml("/api/security/qos", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to set QoS rules"))
    }

    // ─────────────────────────── Guest WiFi ──────────────────────────────────

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val enabledVal = if (enabled) "1" else "0"
            val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Ssid index="2">
        <WifiSsid>$ssid</WifiSsid>
        <WifiEnable>$enabledVal</WifiEnable>
        <WifiWpaPreSharedKey>$password</WifiWpaPreSharedKey>
        <WifiWpaEncryptionModes>AESEncryption</WifiWpaEncryptionModes>
        <WifiAuthMode>WPA2PSK</WifiAuthMode>
    </Ssid>
</request>"""
            val result = postXml("/api/wlan/multi-basic-settings", xmlBody)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
            else Result.failure(Exception("Failed to configure guest WiFi"))
        }

    // ─────────────────────────── Port Forwarding ─────────────────────────────

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Portmap>
        <Description>${rule.name}</Description>
        <WanPort>${rule.externalPort}</WanPort>
        <LanIp>${rule.internalIp}</LanIp>
        <LanPort>${rule.internalPort}</LanPort>
        <Protocol>${rule.protocol.uppercase()}</Protocol>
    </Portmap>
</request>"""
            val result = postXml("/api/security/nat", xmlBody)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
            else Result.failure(Exception("Failed to add port forwarding rule"))
        }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> = withContext(Dispatchers.IO) {
        val result = getXml("/api/security/nat")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val rules = doc.select("Portmap").map { pm ->
                PortForwardRule(
                    name = pm.selectFirst("Description")?.text()?.trim() ?: "",
                    protocol = pm.selectFirst("Protocol")?.text()?.trim() ?: "TCP",
                    externalPort = pm.selectFirst("WanPort")?.text()?.trim()?.toIntOrNull() ?: 0,
                    internalIp = pm.selectFirst("LanIp")?.text()?.trim() ?: "",
                    internalPort = pm.selectFirst("LanPort")?.text()?.trim()?.toIntOrNull() ?: 0
                )
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse port forwarding rules: ${e.message}"))
        }
    }

    // ─────────────────────────── APN Profiles ────────────────────────────────

    override suspend fun setApn(apn: ApnProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val xmlBody = """<?xml version="1.0" encoding="UTF-8"?>
<request>
    <Apn>${apn.apn}</Apn>
    <Username>${apn.username}</Username>
    <Password>${apn.password}</Password>
    <AuthType>0</AuthType>
    <Pdptype>IP</Pdptype>
    <Roam>0</Roam>
</request>"""
        val result = postXml("/api/dialup/profiles", xmlBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isXmlSuccess(result.getOrDefault(""))) Result.success(Unit)
        else Result.failure(Exception("Failed to set APN profile"))
    }

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> = withContext(Dispatchers.IO) {
        val result = getXml("/api/dialup/profiles")
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val xml = result.getOrDefault("")
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val profiles = doc.select("Profile").map { p ->
                ApnProfile(
                    name = p.selectFirst("ProfileName")?.text()?.trim() ?: "",
                    apn = p.selectFirst("Apn")?.text()?.trim() ?: "",
                    username = p.selectFirst("Username")?.text()?.trim() ?: "",
                    password = p.selectFirst("Password")?.text()?.trim() ?: ""
                )
            }
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse APN profiles: ${e.message}"))
        }
    }
}
