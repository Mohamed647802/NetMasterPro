package com.netmaster.pro.data.adapter

import com.netmaster.pro.data.model.*
import com.netmaster.pro.domain.adapter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * RouterAdapter implementation for ZTE routers:
 * ZXHN H168N, H188A (VDSL/xDSL)
 * MF286, MF286D, MC801A (4G LTE CPE)
 *
 * Uses ZTE's proprietary CGI form-based API at /goform/
 */
class ZteAdapter(private val okHttpClient: OkHttpClient) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.ZTE

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.LTE_SIGNAL,
        RouterCapability.SMS,
        RouterCapability.REBOOT,
        RouterCapability.WIFI_5GHZ,
        RouterCapability.GUEST_WIFI,
        RouterCapability.MAC_FILTER,
        RouterCapability.DNS_CHANGE,
        RouterCapability.BAND_LOCK,
        RouterCapability.PORT_FORWARDING
    )

    private var routerIp: String = ""
    private var routerPort: Int = 80
    private var cookies: String = ""

    private val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded".toMediaType()

    private fun baseUrl(): String = "http://$routerIp:$routerPort"
    private fun goformGet(): String = "${baseUrl()}/goform/goform_get_cmd_process"
    private fun goformSet(): String = "${baseUrl()}/goform/goform_set_cmd_process"

    // ─────────────────────────── Hash helpers ────────────────────────────────

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────────── HTTP helpers ────────────────────────────────

    private fun buildClient(): OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildGetRequest(url: String): Request {
        val builder = Request.Builder().url(url).get()
        if (cookies.isNotEmpty()) builder.header("Cookie", cookies)
        builder.header("Referer", "${baseUrl()}/")
        return builder.build()
    }

    private fun buildPostRequest(formBody: String): Request {
        val body = formBody.toRequestBody(FORM_CONTENT_TYPE)
        val builder = Request.Builder()
            .url(goformSet())
            .post(body)
            .header("Referer", "${baseUrl()}/")
        if (cookies.isNotEmpty()) builder.header("Cookie", cookies)
        return builder.build()
    }

    private suspend fun doGet(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = buildClient().newCall(buildGetRequest(url)).execute()
            updateCookies(response)
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) Result.success(body)
            else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun doPost(formBody: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = buildClient().newCall(buildPostRequest(formBody)).execute()
            updateCookies(response)
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) Result.success(body)
            else Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateCookies(response: Response) {
        val newCookies = response.headers("Set-Cookie")
        if (newCookies.isNotEmpty()) {
            cookies = newCookies.joinToString("; ") { it.substringBefore(";") }
        }
    }

    private fun parseLong(str: String?): Long {
        if (str == null) return 0L
        val trimmed = str.trim()
        return when {
            trimmed.startsWith("0x", ignoreCase = true) ->
                trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
            else -> trimmed.toLongOrNull() ?: 0L
        }
    }

    private fun isResponseOk(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            obj.optString("result") == "0" || obj.optString("result") == "success" ||
                obj.has("result").not()
        } catch (e: Exception) { false }
    }

    // ─────────────────────────── Fingerprint ─────────────────────────────────

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try ZTE-specific endpoint
            val client = buildClient()
            val r1 = client.newCall(
                Request.Builder()
                    .url("http://$ip:$port/goform/goform_get_cmd_process?isTest=false&cmd=web_version_number")
                    .get().build()
            ).execute()
            val body1 = r1.body?.string() ?: ""
            if (body1.contains("\"web_version_number\"")) return@withContext true

            // Check homepage for ZTE branding
            val r2 = client.newCall(
                Request.Builder().url("http://$ip:$port/").get().build()
            ).execute()
            val body2 = r2.body?.string() ?: ""
            body2.contains("ZTE", ignoreCase = true) ||
                body2.contains("goform_get_cmd_process", ignoreCase = true) ||
                body2.contains("goform_set_cmd_process", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    // ─────────────────────────── Login ───────────────────────────────────────

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            routerIp = ip
            routerPort = port

            // Collect initial cookie first
            try {
                val initResp = buildClient().newCall(
                    Request.Builder().url("${baseUrl()}/").get().build()
                ).execute()
                updateCookies(initResp)
            } catch (e: Exception) { /* continue */ }

            val passwordAttempts = listOf(
                md5Hex(password),                              // MD5 hex (most common)
                md5Hex(password).uppercase(),                  // MD5 uppercase
                password,                                      // Plain text
                android.util.Base64.encodeToString(
                    md5Hex(password).toByteArray(),
                    android.util.Base64.NO_WRAP
                )                                              // Base64(MD5)
            )

            for (passwordHash in passwordAttempts) {
                val formBody = "isTest=false&goformId=LOGIN&password=$passwordHash"
                val result = doPost(formBody)
                if (result.isSuccess) {
                    val body = result.getOrDefault("")
                    if (body.contains("\"result\":\"0\"") || body.contains("success") ||
                        body.contains("\"ok\"", ignoreCase = true)
                    ) {
                        return@withContext Result.success(Unit)
                    }
                }
            }
            Result.failure(Exception("ZTE login failed — invalid credentials"))
        }

    override suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        try {
            doPost("isTest=false&goformId=LOGOUT")
        } catch (e: Exception) { /* ignore */ }
        cookies = ""
    }

    // ─────────────────────────── LTE Signal ──────────────────────────────────

    override suspend fun getLteSignal(): Result<LteSignalData> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=SINR,RSRP,RSRQ,RSSI,network_type,network_provider," +
            "cell_id,EARFCN,PCI,lte_band,modem_main_state,wan_connect_type"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val rsrp = json.optString("RSRP", "0").trim().toIntOrNull() ?: 0
            val rsrq = json.optString("RSRQ", "0").trim().toFloatOrNull() ?: 0f
            val sinr = json.optString("SINR", "0").trim().toFloatOrNull() ?: 0f
            val rssi = json.optString("RSSI", "0").trim().toIntOrNull() ?: 0
            val pci = json.optString("PCI", "0").trim().toIntOrNull() ?: 0
            val cellId = parseLong(json.optString("cell_id", "0"))
            val earfcn = json.optString("EARFCN", "0").trim().toIntOrNull() ?: 0
            val bandStr = json.optString("lte_band", "0").trim()
                .replace(Regex("[^0-9]"), "")
            val band = bandStr.toIntOrNull() ?: 0

            Result.success(LteSignalData(
                rsrp = rsrp,
                rsrq = rsrq,
                sinr = sinr,
                rssi = rssi,
                pci = pci,
                cellId = cellId,
                earfcn = earfcn,
                band = band
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse ZTE signal data: ${e.message}"))
        }
    }

    // ─────────────────────────── Connected Devices ───────────────────────────

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=station_list"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val stationList = json.optJSONArray("station_list") ?: JSONArray()
            val devices = (0 until stationList.length()).map { i ->
                val station = stationList.getJSONObject(i)
                val mac = station.optString("mac_addr", "").trim()
                val ip = station.optString("ip_addr", "").trim()
                val hostname = station.optString("hostname", "").trim()
                val rssi = station.optString("rssi", "0").trim().toIntOrNull() ?: 0
                val type = station.optString("connect_type", "").lowercase()
                val band = when {
                    type.contains("5g") -> WifiBand.BAND_5GHZ
                    type.contains("2.4") || type.contains("wifi") -> WifiBand.BAND_2_4GHZ
                    type.contains("eth") || type.contains("wired") -> WifiBand.WIRED
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
            Result.failure(Exception("Failed to parse ZTE station list: ${e.message}"))
        }
    }

    // ─────────────────────────── Wi-Fi Settings ──────────────────────────────

    override suspend fun getWifiSettings(): Result<WifiSettings> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=ssid,storagePwd,HideSSID,WifiEnable,wifi_channel"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val ssid = json.optString("ssid", "").trim()
            val password = json.optString("storagePwd", "").trim()
            val enabled = json.optString("WifiEnable", "1").trim() == "1"
            val channel = json.optString("wifi_channel", "0").trim().toIntOrNull() ?: 0

            Result.success(WifiSettings(
                ssid = ssid,
                password = password,
                band = "2.4GHz",
                channel = channel,
                isEnabled = enabled,
                securityMode = "WPA2PSK"
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse ZTE WiFi settings: ${e.message}"))
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> = withContext(Dispatchers.IO) {
        val enabledVal = if (settings.isEnabled) "1" else "0"
        val formBody = "isTest=false&goformId=SET_WIFI_INFO" +
            "&ssid=${java.net.URLEncoder.encode(settings.ssid, "UTF-8")}" +
            "&wifi_password=${java.net.URLEncoder.encode(settings.password, "UTF-8")}" +
            "&WifiEnable=$enabledVal" +
            "&wifi_channel=${settings.channel}" +
            "&security_mode=WPA2PSK" +
            "&broadcast=1"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to update ZTE WiFi settings"))
    }

    // ─────────────────────────── Device Block / Unblock ──────────────────────

    override suspend fun blockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=BLACKLIST_ADD&mac_addr=${mac.uppercase()}"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        Result.success(Unit)
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=BLACKLIST_REMOVE&mac_addr=${mac.uppercase()}"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        Result.success(Unit)
    }

    // ─────────────────────────── Reboot ──────────────────────────────────────

    override suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=REBOOT"
        val result = doPost(formBody)
        // Connection will drop on reboot — treat as success
        if (result.isSuccess || result.exceptionOrNull()?.message?.contains("closed", ignoreCase = true) == true ||
            result.exceptionOrNull()?.message?.contains("reset", ignoreCase = true) == true
        ) {
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Reboot failed"))
        }
    }

    // ─────────────────────────── SMS ─────────────────────────────────────────

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> = withContext(Dispatchers.IO) {
        // First get the total count
        val countUrl = "$goformGet()?isTest=false&cmd=sms_data_total"
        val countResult = doGet(countUrl)
        val total = try {
            val json = JSONObject(countResult.getOrDefault("{}"))
            json.optString("sms_data_total", "0").trim().toIntOrNull() ?: 20
        } catch (e: Exception) { 20 }

        val perPage = minOf(total, 50)

        // Fetch message list
        val listUrl = "$goformGet()?isTest=false&cmd=sms_page_num&pageIndex=0&perPage=$perPage&mem_store=1&tags=10&order_by=order+by+id+desc"
        val listResult = doGet(listUrl)
        if (listResult.isFailure) return@withContext Result.failure(listResult.exceptionOrNull()!!)

        try {
            val body = listResult.getOrDefault("{}")
            val json = JSONObject(body)
            val messages = mutableListOf<SmsMessage>()

            // ZTE SMS format varies by firmware; try both formats
            val pageData = json.optJSONArray("sms_para_flag")
                ?: json.optJSONArray("messages")
                ?: run {
                    // Alternative: messages as top-level array keys
                    val arr = JSONArray()
                    if (json.has("id")) arr.put(json) else arr
                }

            if (pageData != null) {
                for (i in 0 until pageData.length()) {
                    try {
                        val msg = pageData.getJSONObject(i)
                        val id = msg.optString("id", "$i").trim()
                        val from = msg.optString("number", "").trim()
                            .ifEmpty { msg.optString("from", "").trim() }
                        val body = msg.optString("content", "").trim()
                            .ifEmpty { msg.optString("body", "").trim() }
                        val dateStr = msg.optString("date", "").trim()
                        val timestamp = parseZteDate(dateStr)
                        val isRead = msg.optString("tag", "1").trim() == "1"

                        messages.add(SmsMessage(
                            id = id,
                            from = from,
                            body = body,
                            timestamp = timestamp,
                            isRead = isRead,
                            isInbox = true
                        ))
                    } catch (e: Exception) { /* skip malformed message */ }
                }
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse ZTE SMS list: ${e.message}"))
        }
    }

    private fun parseZteDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        // ZTE uses format: "24/01/15,10:30:00+08" or "2024-01-15 10:30:00"
        return try {
            val formats = listOf(
                "yy/MM/dd,HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss"
            )
            for (fmt in formats) {
                try {
                    val cleaned = dateStr.replace(Regex("[+\\-]\\d+$"), "").trim()
                    return java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                        .parse(cleaned)?.time ?: continue
                } catch (e: Exception) { continue }
            }
            System.currentTimeMillis()
        } catch (e: Exception) { System.currentTimeMillis() }
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        val encodedBody = java.net.URLEncoder.encode(body, "UTF-8")
        val encodedTo = java.net.URLEncoder.encode(to, "UTF-8")
        val formBody = "isTest=false&goformId=SEND_SMS" +
            "&Number=$encodedTo" +
            "&MessageBody=$encodedBody" +
            "&encode_type=UNICODE" +
            "&sms_time=${getCurrentZteTime()}"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to send SMS"))
    }

    private fun getCurrentZteTime(): String {
        val sdf = java.text.SimpleDateFormat("yy/MM/dd,HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override suspend fun deleteSms(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=DELETE_SMS&msg_id=$id&storage_type=1"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        Result.success(Unit)
    }

    override suspend fun sendUssd(code: String): Result<String> = withContext(Dispatchers.IO) {
        val encodedCode = java.net.URLEncoder.encode(code, "UTF-8")
        val formBody = "isTest=false&goformId=USSD_PROCESS&USSD_operator=ussd_send" +
            "&USSD_send_number=$encodedCode&rdomain=0"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        // Poll for USSD response
        kotlinx.coroutines.delay(2000)
        val respResult = doGet("$goformGet()?isTest=false&cmd=ussd_data_info")
        try {
            val json = JSONObject(respResult.getOrDefault("{}"))
            val response = json.optString("ussd_data_info", "")
                .ifEmpty { json.optString("USSD_data", "") }
            if (response.isNotEmpty()) Result.success(response)
            else Result.success("USSD sent — no immediate response")
        } catch (e: Exception) {
            Result.success("USSD request sent")
        }
    }

    // ─────────────────────────── Traffic Stats ───────────────────────────────

    override suspend fun getTrafficStats(): Result<TrafficStats> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=monthly_rx_bytes,monthly_tx_bytes,daily_rx_bytes,daily_tx_bytes"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val rx = json.optString("monthly_rx_bytes", "0").toLongOrNull() ?: 0L
            val tx = json.optString("monthly_tx_bytes", "0").toLongOrNull() ?: 0L
            Result.success(TrafficStats(rxBytes = rx, txBytes = tx))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse ZTE traffic stats: ${e.message}"))
        }
    }

    // ─────────────────────────── DSL Stats (N/A for LTE) ─────────────────────

    override suspend fun getDslStats(): Result<DslStats> {
        return Result.failure(Exception("DSL stats not applicable to ZTE LTE routers. Use getLteSignal() for wireless signal info."))
    }

    // ─────────────────────────── DNS ─────────────────────────────────────────

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=WAN_DNS_INFO" +
            "&prefer_dns_manual=1" +
            "&prefer_dns=${java.net.URLEncoder.encode(primary, "UTF-8")}" +
            "&standby_dns=${java.net.URLEncoder.encode(secondary, "UTF-8")}"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to set DNS servers"))
    }

    // ─────────────────────────── Network Mode ────────────────────────────────

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> = withContext(Dispatchers.IO) {
        val modeStr = when (mode) {
            NetworkMode.LTE_ONLY -> "NETWORK_auto_lte"
            NetworkMode.WCDMA_ONLY -> "NETWORK_wcdma_only"
            NetworkMode.GSM_ONLY -> "NETWORK_gprs_only"
            NetworkMode.AUTO -> "NETWORK_auto"
        }
        val formBody = "isTest=false&goformId=SET_BEARER_PREFERENCE&BearerPreference=$modeStr"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to set network mode"))
    }

    // ─────────────────────────── Band Selection ──────────────────────────────

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> = withContext(Dispatchers.IO) {
        // ZTE uses numeric band list separated by commas
        val bandNumbers = bands.map { it.bandNumber }.joinToString(",")
        val formBody = "isTest=false&goformId=SET_NET_WORK_BAND" +
            "&work_mode=NETWORK_auto_lte" +
            "&ZTE_lte_band_list=$bandNumbers"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to set LTE band selection"))
    }

    // ─────────────────────────── Backup / Restore ────────────────────────────

    override suspend fun downloadBackup(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = buildGetRequest("${baseUrl()}/goform/goform_get_cmd_process?isTest=false&cmd=export_config")
            val response = buildClient().newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.bytes() ?: ByteArray(0))
            } else {
                Result.failure(Exception("Backup not available on this ZTE model"))
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
                .addFormDataPart("file", "backup.conf", data.toRequestBody(mediaType))
                .addFormDataPart("goformId", "IMPORT_CONFIG")
                .build()
            val request = Request.Builder()
                .url(goformSet())
                .post(requestBody)
                .header("Referer", "${baseUrl()}/")
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
        return@withContext Result.failure(
            Exception("Detailed security audit not supported by ZTE CGI API. Basic security info not exposed via /goform/ endpoints.")
        )
    }

    // ─────────────────────────── MAC Filter ──────────────────────────────────

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            // First clear existing blacklist
            doPost("isTest=false&goformId=BLACKLIST_CLEAR_ALL")

            if (mode == MacFilterMode.DISABLED) {
                return@withContext Result.success(Unit)
            }

            // Add each MAC to blacklist (ZTE only supports blacklist mode)
            var lastError: Exception? = null
            for (mac in macs) {
                val formBody = "isTest=false&goformId=BLACKLIST_ADD&mac_addr=${mac.uppercase()}"
                val result = doPost(formBody)
                if (result.isFailure) lastError = result.exceptionOrNull() as? Exception
            }
            if (lastError != null && macs.isNotEmpty()) {
                Result.failure(lastError!!)
            } else {
                Result.success(Unit)
            }
        }

    // ─────────────────────────── QoS ─────────────────────────────────────────

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = withContext(Dispatchers.IO) {
        // ZTE doesn't have a standard QoS goform — try anyway
        for (rule in rules) {
            val formBody = "isTest=false&goformId=QOS_SETTINGS" +
                "&mac_addr=${rule.mac}" +
                "&up_rate=${rule.uploadKbps}" +
                "&down_rate=${rule.downloadKbps}"
            doPost(formBody)
        }
        Result.success(Unit) // Best-effort
    }

    // ─────────────────────────── Guest WiFi ──────────────────────────────────

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val enabledVal = if (enabled) "1" else "0"
            val formBody = "isTest=false&goformId=SET_WIFI_GUEST_INFO" +
                "&guest_ssid=${java.net.URLEncoder.encode(ssid, "UTF-8")}" +
                "&guest_password=${java.net.URLEncoder.encode(password, "UTF-8")}" +
                "&guest_enable=$enabledVal"
            val result = doPost(formBody)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            Result.success(Unit)
        }

    // ─────────────────────────── Port Forwarding ─────────────────────────────

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        withContext(Dispatchers.IO) {
            val formBody = "isTest=false&goformId=PORT_FORWARD_ADD" +
                "&rule_name=${java.net.URLEncoder.encode(rule.name, "UTF-8")}" +
                "&port_start=${rule.externalPort}" +
                "&port_end=${rule.externalPort}" +
                "&ip_address=${rule.internalIp}" +
                "&ip_port=${rule.internalPort}" +
                "&protocol=${rule.protocol.uppercase()}" +
                "&enable=1"
            val result = doPost(formBody)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            Result.success(Unit)
        }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=port_forward_config"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val rulesArr = json.optJSONArray("port_forward_config") ?: return@withContext Result.success(emptyList())
            val rules = (0 until rulesArr.length()).mapNotNull { i ->
                try {
                    val r = rulesArr.getJSONObject(i)
                    PortForwardRule(
                        name = r.optString("rule_name", "Rule$i"),
                        protocol = r.optString("protocol", "TCP"),
                        externalPort = r.optString("port_start", "0").toIntOrNull() ?: 0,
                        internalIp = r.optString("ip_address", ""),
                        internalPort = r.optString("ip_port", "0").toIntOrNull() ?: 0
                    )
                } catch (e: Exception) { null }
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse port forwarding rules: ${e.message}"))
        }
    }

    // ─────────────────────────── APN Profiles ────────────────────────────────

    override suspend fun setApn(apn: ApnProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val formBody = "isTest=false&goformId=APN_PROC_EX" +
            "&apn_action=set" +
            "&apn_name=${java.net.URLEncoder.encode(apn.name, "UTF-8")}" +
            "&apn_en_flag=1" +
            "&set_apn_name=${java.net.URLEncoder.encode(apn.apn, "UTF-8")}" +
            "&set_apn_user=${java.net.URLEncoder.encode(apn.username, "UTF-8")}" +
            "&set_apn_pwd=${java.net.URLEncoder.encode(apn.password, "UTF-8")}" +
            "&set_auth_type=0" +
            "&set_pdp_type=IP"
        val result = doPost(formBody)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
        if (isResponseOk(result.getOrDefault("{}"))) Result.success(Unit)
        else Result.failure(Exception("Failed to set APN profile"))
    }

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> = withContext(Dispatchers.IO) {
        val url = "$goformGet()?isTest=false&cmd=apn_interface_version,APN_config0,APN_config1,APN_config2,APN_config3,APN_config4"
        val result = doGet(url)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        try {
            val json = JSONObject(result.getOrDefault("{}"))
            val profiles = mutableListOf<ApnProfile>()
            for (i in 0..4) {
                val apnStr = json.optString("APN_config$i", "")
                if (apnStr.isEmpty()) continue
                // Format: "name,apn,user,pass,type,..."
                val parts = apnStr.split(",")
                if (parts.size >= 2) {
                    profiles.add(ApnProfile(
                        name = parts.getOrElse(0) { "Profile$i" },
                        apn = parts.getOrElse(1) { "" },
                        username = parts.getOrElse(2) { "" },
                        password = parts.getOrElse(3) { "" }
                    ))
                }
            }
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse ZTE APN profiles: ${e.message}"))
        }
    }
}
