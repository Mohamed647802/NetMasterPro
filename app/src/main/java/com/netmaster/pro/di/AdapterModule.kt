package com.netmaster.pro.di

import android.util.Log
import com.netmaster.pro.data.local.security.EncryptedPreferencesManager
import com.netmaster.pro.data.model.*
import com.netmaster.pro.data.network.OkHttpClientProvider
import com.netmaster.pro.domain.adapter.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

// ─── Adapter Registry ────────────────────────────────────────────────────────

/**
 * Selects the correct [RouterAdapter] for a given IP:port by calling
 * [RouterAdapter.fingerprint] on each registered adapter in priority order.
 */
class RouterAdapterRegistry(
    private val adapters: List<RouterAdapter>
) {
    companion object {
        private const val TAG = "RouterAdapterRegistry"
    }

    suspend fun detect(ip: String, port: Int): RouterAdapter? {
        for (adapter in adapters) {
            try {
                if (adapter.fingerprint(ip, port)) {
                    Log.i(TAG, "Adapter ${adapter.brand} matched $ip:$port")
                    return adapter
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fingerprint probe error for ${adapter.brand}: ${e.message}")
            }
        }
        Log.w(TAG, "No adapter matched $ip:$port — using UniversalFallback")
        return adapters.firstOrNull { it.brand == RouterBrand.UNKNOWN }
    }

    fun adapterFor(brand: RouterBrand): RouterAdapter? =
        adapters.firstOrNull { it.brand == brand }
}

// ─── Stub Base Adapter ───────────────────────────────────────────────────────

/**
 * Base class with Not-Implemented stubs for capabilities a specific adapter
 * doesn't support. Adapters override only the methods they implement.
 */
abstract class BaseRouterAdapter(
    protected val okHttpClient: OkHttpClient
) : RouterAdapter {

    protected fun notSupported(feature: String): Nothing =
        throw UnsupportedOperationException("$feature is not supported by ${brand.name} adapter")

    override suspend fun logout() { /* no-op by default */ }
    override suspend fun getDslStats(): Result<DslStats> = Result.failure(UnsupportedOperationException("DSL stats not supported"))
    override suspend fun getSmsMessages(): Result<List<SmsMessage>> = Result.failure(UnsupportedOperationException("SMS not supported"))
    override suspend fun sendSms(to: String, body: String): Result<Unit> = Result.failure(UnsupportedOperationException("SMS not supported"))
    override suspend fun deleteSms(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("SMS not supported"))
    override suspend fun sendUssd(code: String): Result<String> = Result.failure(UnsupportedOperationException("USSD not supported"))
    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> = Result.failure(UnsupportedOperationException("Guest WiFi not supported"))
    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = Result.failure(UnsupportedOperationException("QoS not supported"))
    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> = Result.failure(UnsupportedOperationException("Port forwarding not supported"))
    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> = Result.failure(UnsupportedOperationException("Port forwarding not supported"))
    override suspend fun setApn(apn: ApnProfile): Result<Unit> = Result.failure(UnsupportedOperationException("APN not supported"))
    override suspend fun getApnProfiles(): Result<List<ApnProfile>> = Result.failure(UnsupportedOperationException("APN not supported"))
    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> = Result.failure(UnsupportedOperationException("Band lock not supported"))
    override suspend fun downloadBackup(): Result<ByteArray> = Result.failure(UnsupportedOperationException("Backup not supported"))
    override suspend fun restoreBackup(data: ByteArray): Result<Unit> = Result.failure(UnsupportedOperationException("Backup not supported"))
    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> = Result.failure(UnsupportedOperationException("Security info not supported"))
    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> = Result.failure(UnsupportedOperationException("MAC filter not supported"))
    override suspend fun getLteSignal(): Result<LteSignalData> = Result.failure(UnsupportedOperationException("LTE signal not supported"))
    override suspend fun getDslStats2(): Result<DslStats> = getDslStats()
}

// Alias to avoid duplicate method issue
private suspend fun BaseRouterAdapter.getDslStats2(): Result<DslStats> = getDslStats()

// ─── Greenpacket D2220G-1 Adapter ────────────────────────────────────────────

class GreenpacketD2220G1Adapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.GREENPACKET
    override val supportedCapabilities = setOf(
        RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
        RouterCapability.BAND_LOCK, RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP,
        RouterCapability.DNS_CHANGE, RouterCapability.MAC_FILTER, RouterCapability.WIFI_5GHZ
    )

    private var baseUrl: String = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/goform/GetLteInfoNew"
            val request = okhttp3.Request.Builder().url(url).get()
                .header("User-Agent", "NetMasterPro/1.0").build()
            okHttpClient.newCall(request).execute().use { it.code in 200..399 }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/Login")
                .post(body)
                .header("Referer", "$baseUrl/index.html")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 302) Result.success(Unit)
                else Result.failure(Exception("Login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLteSignal(): Result<LteSignalData> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetLteInfoNew")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                // Parse JSON response — Greenpacket returns JSON with rsrp, rsrq, sinr, etc.
                val json = org.json.JSONObject(body)
                Result.success(
                    LteSignalData(
                        rsrp = json.optInt("rsrp", 0),
                        rsrq = json.optDouble("rsrq", 0.0).toFloat(),
                        sinr = json.optDouble("sinr", 0.0).toFloat(),
                        rssi = json.optInt("rssi", 0),
                        pci = json.optInt("pci", 0),
                        cellId = json.optLong("cell_id", 0L),
                        earfcn = json.optInt("earfcn", 0),
                        band = json.optInt("band", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetDhcpClientInfo")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val devices = mutableListOf<ConnectedDevice>()
                val json = org.json.JSONObject(body)
                val list = json.optJSONArray("dhcp_list") ?: return Result.success(emptyList())
                for (i in 0 until list.length()) {
                    val entry = list.getJSONObject(i)
                    devices.add(
                        ConnectedDevice(
                            mac = entry.optString("mac", ""),
                            ip = entry.optString("ip", ""),
                            hostname = entry.optString("hostname", ""),
                            leaseTime = entry.optInt("lease_time", -1)
                        )
                    )
                }
                Result.success(devices)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder().add("goformId", "REBOOT_DEVICE").build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Reboot failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetWifiConfig")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val json = org.json.JSONObject(body)
                Result.success(
                    WifiSettings(
                        ssid2g = json.optString("ssid_2g", ""),
                        password2g = json.optString("wifi_key_2g", ""),
                        enabled2g = json.optInt("wifi_enable_2g", 1) == 1,
                        ssid5g = json.optString("ssid_5g", ""),
                        password5g = json.optString("wifi_key_5g", ""),
                        enabled5g = json.optInt("wifi_enable_5g", 1) == 1
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "SET_WIFI_CONFIG")
                .add("ssid_2g", settings.ssid2g)
                .add("wifi_key_2g", settings.password2g)
                .add("wifi_enable_2g", if (settings.enabled2g) "1" else "0")
                .add("ssid_5g", settings.ssid5g)
                .add("wifi_key_5g", settings.password5g)
                .add("wifi_enable_5g", if (settings.enabled5g) "1" else "0")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set WiFi failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockDevice(mac: String): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "ADD_MAC_FILTER")
                .add("mac", mac)
                .add("filter_mode", "black")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Block device failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "DEL_MAC_FILTER")
                .add("mac", mac)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Unblock device failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetSms?pageIndex=1&dataPerPage=20&mem_store=1&tags=1&order_by=order+by+id+desc")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val json = org.json.JSONObject(body)
                val list = json.optJSONArray("messages") ?: return Result.success(emptyList())
                val messages = mutableListOf<SmsMessage>()
                for (i in 0 until list.length()) {
                    val msg = list.getJSONObject(i)
                    messages.add(
                        SmsMessage(
                            id = msg.optString("id", ""),
                            from = msg.optString("number", ""),
                            body = msg.optString("content", ""),
                            timestamp = msg.optLong("date", 0L),
                            isRead = msg.optInt("tag", 0) == 1,
                            isInbox = msg.optInt("mem_store", 1) == 1
                        )
                    )
                }
                Result.success(messages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> {
        return try {
            val formBody = okhttp3.FormBody.Builder()
                .add("goformId", "SEND_SMS")
                .add("Number", to)
                .add("MessageBody", body)
                .add("encode_type", "UNICODE")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(formBody).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Send SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSms(id: String): Result<Unit> {
        return try {
            val formBody = okhttp3.FormBody.Builder()
                .add("goformId", "DELETE_SMS")
                .add("msg_id", id)
                .add("mem_store", "1")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(formBody).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Delete SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendUssd(code: String): Result<String> {
        return try {
            val formBody = okhttp3.FormBody.Builder()
                .add("goformId", "USSD_PROCESS")
                .add("USSD_operator", code)
                .add("action", "initiate")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(formBody).build()
            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(respBody)
                    Result.success(json.optString("USSD_recv_content", respBody))
                } else {
                    Result.failure(Exception("USSD failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetWanFlowStatistic")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val json = org.json.JSONObject(body)
                Result.success(
                    TrafficStats(
                        rxBytes = json.optLong("rx_bytes", 0L),
                        txBytes = json.optLong("tx_bytes", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "SET_WAN_DNS")
                .add("manual_dns_1", primary)
                .add("manual_dns_2", secondary)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set DNS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> {
        return try {
            val modeStr = when (mode) {
                NetworkMode.LTE_ONLY -> "03"
                NetworkMode.WCDMA_ONLY -> "02"
                NetworkMode.GSM_ONLY -> "01"
                NetworkMode.AUTO -> "00"
                else -> "00"
            }
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "SET_BEARER_PREFERENCE")
                .add("BearerPreference", modeStr)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set network mode failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> {
        return try {
            val bandMask = bands.fold(0L) { acc, band ->
                acc or (1L shl (band.ordinal))
            }.toString(16)
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "SET_LTE_BAND")
                .add("lte_band_mask", bandMask)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set band failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadBackup(): Result<ByteArray> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/GetBackupCfg")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: return Result.failure(Exception("Empty backup"))
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> {
        return try {
            val modeStr = when (mode) {
                MacFilterMode.BLACKLIST -> "black"
                MacFilterMode.WHITELIST -> "white"
                MacFilterMode.DISABLED -> "disabled"
            }
            val body = okhttp3.FormBody.Builder()
                .add("goformId", "SET_MAC_FILTER")
                .add("filter_mode", modeStr)
                .add("mac_list", macs.joinToString(";"))
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set MAC filter failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ─── Huawei Adapter ──────────────────────────────────────────────────────────

class HuaweiAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.HUAWEI
    override val supportedCapabilities = setOf(
        RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
        RouterCapability.BAND_LOCK, RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT,
        RouterCapability.CONFIG_BACKUP, RouterCapability.DNS_CHANGE
    )

    private var baseUrl = ""
    private var sessionToken = ""
    private var requestToken = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/api/webserver/SesTokInfo"
            val request = okhttp3.Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun refreshTokens(ip: String, port: Int) {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/webserver/SesTokInfo")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val xml = response.body?.string() ?: return
                sessionToken = Regex("<SesInfo>(.*?)</SesInfo>").find(xml)?.groupValues?.get(1) ?: ""
                requestToken = Regex("<TokInfo>(.*?)</TokInfo>").find(xml)?.groupValues?.get(1) ?: ""
            }
        } catch (e: Exception) {
            Log.e("HuaweiAdapter", "Token refresh failed", e)
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        return try {
            refreshTokens(ip, port)
            val hashedPass = hashPassword(username, password, requestToken)
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><Username>$username</Username><Password>$hashedPass</Password><password_type>4</password_type></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/user/login")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hashPassword(username: String, password: String, token: String): String {
        // Huawei uses SHA256(username + SHA256(password) + token) base64 encoded
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
        val passHash = sha256.digest(password.toByteArray()).toHexString()
        val combined = username + passHash + token
        val finalHash = sha256.digest(combined.toByteArray())
        return android.util.Base64.encodeToString(finalHash, android.util.Base64.NO_WRAP)
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    override suspend fun getLteSignal(): Result<LteSignalData> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/device/signal")
                .get()
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val xml = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                fun extractXml(tag: String): String =
                    Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1)?.trim() ?: "0"
                Result.success(
                    LteSignalData(
                        rsrp = extractXml("rsrp").trimEnd('d','B','m').toIntOrNull() ?: 0,
                        rsrq = extractXml("rsrq").trimEnd('d','B').toFloatOrNull() ?: 0f,
                        sinr = extractXml("sinr").toFloatOrNull() ?: 0f,
                        rssi = extractXml("rssi").trimEnd('d','B','m').toIntOrNull() ?: 0,
                        band = extractXml("band").toIntOrNull() ?: 0,
                        cellId = extractXml("cell_id").toLongOrNull() ?: 0L,
                        pci = extractXml("pci").toIntOrNull() ?: 0
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/wlan/host-list")
                .get()
                .header("Cookie", sessionToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val xml = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val hosts = Regex("<Host>(.*?)</Host>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                val devices = hosts.map { match ->
                    val block = match.groupValues[1]
                    fun field(tag: String) = Regex("<$tag>(.*?)</$tag>").find(block)?.groupValues?.get(1) ?: ""
                    ConnectedDevice(
                        mac = field("MacAddress"),
                        ip = field("IpAddress"),
                        hostname = field("HostName")
                    )
                }.toList()
                Result.success(devices)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><Control>1</Control></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/device/control")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Reboot failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/wlan/basic-settings")
                .get().header("Cookie", sessionToken).build()
            okHttpClient.newCall(request).execute().use { response ->
                val xml = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                fun field(tag: String) = Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1) ?: ""
                Result.success(
                    WifiSettings(
                        ssid2g = field("WifiSsid"),
                        password2g = field("WifiKey"),
                        enabled2g = field("WifiEnable") == "1"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><WifiSsid>${settings.ssid2g}</WifiSsid><WifiKey>${settings.password2g}</WifiKey><WifiEnable>${if (settings.enabled2g) 1 else 0}</WifiEnable></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/wlan/basic-settings")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set WiFi failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Block device not available for Huawei adapter"))

    override suspend fun unblockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Unblock device not available for Huawei adapter"))

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><PageIndex>1</PageIndex><ReadCount>20</ReadCount><BoxType>1</BoxType><SortType>0</SortType><Ascending>0</Ascending><UnreadPreferred>1</UnreadPreferred></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/sms/sms-list")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val respXml = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val messages = Regex("<Message>(.*?)</Message>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(respXml).map { match ->
                        val block = match.groupValues[1]
                        fun field(tag: String) = Regex("<$tag>(.*?)</$tag>").find(block)?.groupValues?.get(1) ?: ""
                        SmsMessage(
                            id = field("Index"),
                            from = field("Phone"),
                            body = field("Content"),
                            timestamp = System.currentTimeMillis(),
                            isRead = field("Smstat") == "1",
                            isInbox = field("BoxType") == "1"
                        )
                    }.toList()
                Result.success(messages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/monitoring/traffic-statistics")
                .get().header("Cookie", sessionToken).build()
            okHttpClient.newCall(request).execute().use { response ->
                val xml = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                fun field(tag: String) = Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                Result.success(TrafficStats(rxBytes = field("TotalDownload"), txBytes = field("TotalUpload")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><DnsStatus>1</DnsStatus><PrimaryDns>$primary</PrimaryDns><SecondaryDns>$secondary</SecondaryDns></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/dhcp/settings")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Set DNS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendUssd(code: String): Result<String> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><content>$code</content><codeType>CodeType</codeType><timeout></timeout></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/ussd/send")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("USSD send failed"))
            }
            // Poll for response
            kotlinx.coroutines.delay(3000)
            val getRequest = okhttp3.Request.Builder()
                .url("$baseUrl/api/ussd/get")
                .get().header("Cookie", sessionToken).build()
            okHttpClient.newCall(getRequest).execute().use { response ->
                val xml2 = response.body?.string() ?: ""
                val content = Regex("<content>(.*?)</content>").find(xml2)?.groupValues?.get(1) ?: ""
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun sendSms(to: String, body: String): Result<Unit> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><Index>-1</Index><Phones><Phone>$to</Phone></Phones><Sca></Sca><Content>$body</Content><Length>-1</Length><Reserved>1</Reserved><Date>-1</Date></request>"""
            val reqBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/sms/send-sms")
                .post(reqBody)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Send SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun deleteSms(id: String): Result<Unit> {
        return try {
            val xml = """<?xml version="1.0" encoding="UTF-8"?><request><Index>$id</Index></request>"""
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/xml"), xml)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/sms/delete-sms")
                .post(body)
                .header("Cookie", sessionToken)
                .header("__RequestVerificationToken", requestToken)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Delete SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ─── ZTE Adapter ─────────────────────────────────────────────────────────────

class ZteAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.ZTE
    override val supportedCapabilities = setOf(
        RouterCapability.SMS, RouterCapability.USSD, RouterCapability.LTE_SIGNAL,
        RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP, RouterCapability.DNS_CHANGE
    )

    private var baseUrl = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/goform/goform_get_cmd_process?cmd=modem_main_state"
            val request = okhttp3.Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        return try {
            val hashedPass = android.util.Base64.encodeToString(
                password.toByteArray(), android.util.Base64.NO_WRAP
            )
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "LOGIN")
                .add("password", hashedPass)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun query(vararg cmds: String): org.json.JSONObject {
        val cmdStr = cmds.joinToString(",")
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=$cmdStr&multi_data=1")
            .get().header("Referer", "$baseUrl/index.html").build()
        return okHttpClient.newCall(request).execute().use { response ->
            org.json.JSONObject(response.body?.string() ?: "{}")
        }
    }

    override suspend fun getLteSignal(): Result<LteSignalData> {
        return try {
            val json = query("rsrp", "rsrq", "sinr", "rssi", "pci", "cell_id", "EARFCN", "lte_band")
            Result.success(
                LteSignalData(
                    rsrp = json.optString("rsrp", "0").filter { it.isDigit() || it == '-' }.toIntOrNull() ?: 0,
                    rsrq = json.optString("rsrq", "0").filter { it.isDigit() || it == '-' || it == '.' }.toFloatOrNull() ?: 0f,
                    sinr = json.optString("sinr", "0").toFloatOrNull() ?: 0f,
                    rssi = json.optString("rssi", "0").filter { it.isDigit() || it == '-' }.toIntOrNull() ?: 0,
                    pci = json.optString("pci", "0").toIntOrNull() ?: 0,
                    cellId = json.optString("cell_id", "0").toLongOrNull() ?: 0L,
                    earfcn = json.optString("EARFCN", "0").toIntOrNull() ?: 0,
                    band = json.optString("lte_band", "0").toIntOrNull() ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val json = query("lan_ipaddr", "dhcpinfo_list")
            val list = json.optJSONArray("dhcpinfo_list") ?: return Result.success(emptyList())
            val devices = (0 until list.length()).map { i ->
                val entry = list.getJSONObject(i)
                ConnectedDevice(
                    mac = entry.optString("mac_addr", ""),
                    ip = entry.optString("ip_addr", ""),
                    hostname = entry.optString("host_name", "")
                )
            }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "REBOOT_DEVICE")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Reboot failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> {
        return try {
            val json = query("ssid", "wifi_key", "wifi_enable", "ssid_5g", "wifi_key_5g", "wifi_enable_5g")
            Result.success(
                WifiSettings(
                    ssid2g = json.optString("ssid", ""),
                    password2g = json.optString("wifi_key", ""),
                    enabled2g = json.optString("wifi_enable", "1") == "1",
                    ssid5g = json.optString("ssid_5g", ""),
                    password5g = json.optString("wifi_key_5g", ""),
                    enabled5g = json.optString("wifi_enable_5g", "1") == "1"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "SAVE_WIFI_MULTI_SSID_SETTINGS")
                .add("ssid", settings.ssid2g)
                .add("wifi_key", settings.password2g)
                .add("wifi_enable", if (settings.enabled2g) "1" else "0")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Set WiFi failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Block not supported on ZTE"))

    override suspend fun unblockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Unblock not supported on ZTE"))

    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val json = query("realtime_rx_bytes", "realtime_tx_bytes")
            Result.success(
                TrafficStats(
                    rxBytes = json.optString("realtime_rx_bytes", "0").toLongOrNull() ?: 0L,
                    txBytes = json.optString("realtime_tx_bytes", "0").toLongOrNull() ?: 0L
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "SET_WAN_CONNECTION")
                .add("MANUAL_DNS_ENABLE", "1")
                .add("MANUAL_DNS_1", primary)
                .add("MANUAL_DNS_2", secondary)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Set DNS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> {
        return try {
            val json = query("sms_data_total", "sms_received_total")
            // ZTE SMS list
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=sms_data_total&page=0&data_per_page=20&mem_store=1&tags=1&order_by=order%20by%20id%20desc")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val jsonResp = org.json.JSONObject(body)
                val list = jsonResp.optJSONArray("messages") ?: return Result.success(emptyList())
                val messages = (0 until list.length()).map { i ->
                    val msg = list.getJSONObject(i)
                    SmsMessage(
                        id = msg.optString("id", ""),
                        from = msg.optString("number", ""),
                        body = msg.optString("content", ""),
                        timestamp = System.currentTimeMillis(),
                        isRead = msg.optString("tag", "0") == "1"
                    )
                }
                Result.success(messages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> {
        return try {
            val formBody = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "SEND_SMS")
                .add("Number", to)
                .add("MessageBody", body)
                .add("encode_type", "UNICODE")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(formBody).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Send SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSms(id: String): Result<Unit> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "DELETE_SMS")
                .add("msg_id", id)
                .add("mem_store", "1")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("ZTE Delete SMS failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendUssd(code: String): Result<String> {
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("isTest", "false")
                .add("goformId", "USSD_PROCESS")
                .add("USSD_operator", code)
                .add("action", "initiate")
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/goform/goform_set_cmd_process")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(respBody)
                    Result.success(json.optString("USSD_recv_content", respBody))
                } else {
                    Result.failure(Exception("ZTE USSD failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ─── TP-Link Adapter ─────────────────────────────────────────────────────────

class TpLinkAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.TPLINK
    override val supportedCapabilities = setOf(
        RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP,
        RouterCapability.DNS_CHANGE, RouterCapability.PORT_FORWARDING,
        RouterCapability.MAC_FILTER, RouterCapability.GUEST_WIFI, RouterCapability.QOS
    )

    private var baseUrl = ""
    private var stok = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/webpages/index.html"
            val request = okhttp3.Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                response.isSuccessful && (body.contains("TP-LINK", ignoreCase = true) || body.contains("tplink", ignoreCase = true))
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        return try {
            val passwordMd5 = java.security.MessageDigest.getInstance("MD5")
                .digest(password.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .uppercase()
            val body = """{"method":"do","login":{"username":"$username","password":"$passwordMd5"}}"""
            val reqBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), body)
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/")
                .post(reqBody).build()
            okHttpClient.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                val json = org.json.JSONObject(respBody)
                stok = json.optString("stok", "")
                if (stok.isNotEmpty()) Result.success(Unit)
                else Result.failure(Exception("TP-Link Login failed: no stok"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun tpRequest(payload: String): org.json.JSONObject {
        val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), payload)
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/stok=$stok/ds")
            .post(body).build()
        return okHttpClient.newCall(request).execute().use { response ->
            org.json.JSONObject(response.body?.string() ?: "{}")
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val payload = """{"method":"get","hosts_info":{"table":"online_host"}}"""
            val json = tpRequest(payload)
            val hosts = json.optJSONObject("hosts_info")?.optJSONArray("online_host")
            val devices = if (hosts != null) {
                (0 until hosts.length()).map { i ->
                    val h = hosts.getJSONObject(i)
                    ConnectedDevice(
                        mac = h.optString("mac", ""),
                        ip = h.optString("ip", ""),
                        hostname = h.optString("hostname", "")
                    )
                }
            } else emptyList()
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            tpRequest("""{"method":"do","system":{"reboot":null}}""")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> {
        return try {
            val json = tpRequest("""{"method":"get","wireless":{"name":["wlan_host_2g","wlan_host_5g"]}}""")
            val w2g = json.optJSONObject("wireless")?.optJSONObject("wlan_host_2g")
            val w5g = json.optJSONObject("wireless")?.optJSONObject("wlan_host_5g")
            Result.success(
                WifiSettings(
                    ssid2g = w2g?.optString("ssid", "") ?: "",
                    password2g = w2g?.optString("key", "") ?: "",
                    enabled2g = w2g?.optString("enable", "on") == "on",
                    ssid5g = w5g?.optString("ssid", "") ?: "",
                    password5g = w5g?.optString("key", "") ?: "",
                    enabled5g = w5g?.optString("enable", "on") == "on"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> {
        return try {
            val payload = """{"method":"set","wireless":{"wlan_host_2g":{"ssid":"${settings.ssid2g}","key":"${settings.password2g}","enable":"${if (settings.enabled2g) "on" else "off"}"}}}"""
            tpRequest(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockDevice(mac: String): Result<Unit> {
        return try {
            val payload = """{"method":"do","qos":{"add_black_list":{"mac":"$mac"}}}"""
            tpRequest(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> {
        return try {
            val payload = """{"method":"do","qos":{"del_black_list":{"mac":"$mac"}}}"""
            tpRequest(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val json = tpRequest("""{"method":"get","wan_ipv4_status":{"name":"status"}}""")
            val wan = json.optJSONObject("wan_ipv4_status")?.optJSONObject("status")
            Result.success(
                TrafficStats(
                    rxBytes = wan?.optLong("rx_bytes", 0L) ?: 0L,
                    txBytes = wan?.optLong("tx_bytes", 0L) ?: 0L
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> {
        return try {
            val payload = """{"method":"set","dns":{"dns1":"$primary","dns2":"$secondary"}}"""
            tpRequest(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ─── OpenWrt Adapter ─────────────────────────────────────────────────────────

class OpenWrtAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.OPENWRT
    override val supportedCapabilities = setOf(
        RouterCapability.WIFI_5GHZ, RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP,
        RouterCapability.PORT_FORWARDING, RouterCapability.QOS, RouterCapability.DNS_CHANGE,
        RouterCapability.MAC_FILTER, RouterCapability.UPNP
    )

    private var baseUrl = ""
    private var authToken = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/cgi-bin/luci/"
            val request = okhttp3.Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                response.isSuccessful && (body.contains("LuCI", ignoreCase = true) || body.contains("OpenWrt", ignoreCase = true))
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("luci_username", username)
                .add("luci_password", password)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/cgi-bin/luci/")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                // LuCI sets a session cookie
                if (response.isSuccessful || response.code == 302) {
                    // Extract token from response URL or cookie
                    val location = response.header("Location") ?: ""
                    authToken = Regex("/cgi-bin/luci/;stok=([a-f0-9]+)").find(location)?.groupValues?.get(1) ?: ""
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("OpenWrt Login failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun rpcCall(method: String, params: Any): org.json.JSONObject {
        val payload = """{"method":"$method","params":$params}"""
        val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), payload)
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/cgi-bin/luci/rpc/sys?auth=$authToken")
            .post(body).build()
        return okHttpClient.newCall(request).execute().use { response ->
            org.json.JSONObject(response.body?.string() ?: "{}")
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            // Use the DHCP leases file via exec RPC
            val json = rpcCall("exec", """["cat /tmp/dhcp.leases"]""")
            val output = json.optString("result", "")
            val devices = output.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.trim().split(" ")
                if (parts.size >= 4) {
                    ConnectedDevice(
                        mac = parts[1],
                        ip = parts[2],
                        hostname = parts[3]
                    )
                } else null
            }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            rpcCall("exec", """["reboot"]""")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> = Result.failure(UnsupportedOperationException("Use uci for OpenWrt wifi settings"))
    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> = Result.failure(UnsupportedOperationException("Use uci for OpenWrt wifi settings"))
    override suspend fun blockDevice(mac: String): Result<Unit> = Result.failure(UnsupportedOperationException("Block via iptables rules on OpenWrt"))
    override suspend fun unblockDevice(mac: String): Result<Unit> = Result.failure(UnsupportedOperationException("Unblock via iptables rules on OpenWrt"))
    override suspend fun getTrafficStats(): Result<TrafficStats> = Result.failure(UnsupportedOperationException("Use luci-statistics on OpenWrt"))
    override suspend fun setDns(primary: String, secondary: String): Result<Unit> = Result.failure(UnsupportedOperationException("Use uci for OpenWrt DNS settings"))
}

// ─── MikroTik Adapter ────────────────────────────────────────────────────────

class MikroTikAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.MIKROTIK
    override val supportedCapabilities = setOf(
        RouterCapability.REBOOT, RouterCapability.CONFIG_BACKUP,
        RouterCapability.PORT_FORWARDING, RouterCapability.QOS,
        RouterCapability.DNS_CHANGE, RouterCapability.MAC_FILTER,
        RouterCapability.UPNP, RouterCapability.DSL_STATS
    )

    private var baseUrl = ""

    override suspend fun fingerprint(ip: String, port: Int): Boolean {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val url = "$scheme://$ip:$port/webfig/"
            val request = okhttp3.Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                response.isSuccessful && (body.contains("MikroTik", ignoreCase = true) || body.contains("RouterOS", ignoreCase = true))
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        val scheme = if (port == 443) "https" else "http"
        baseUrl = "$scheme://$ip:$port"
        return try {
            // MikroTik REST API (RouterOS 7+)
            val credential = android.util.Base64.encodeToString(
                "$username:$password".toByteArray(), android.util.Base64.NO_WRAP
            )
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/rest/system/resource")
                .get()
                .header("Authorization", "Basic $credential")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("MikroTik Login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return try {
            val body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), "{}")
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/rest/system/reboot")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("MikroTik Reboot failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/rest/ip/dhcp-server/lease")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val array = org.json.JSONArray(body)
                val devices = (0 until array.length()).map { i ->
                    val entry = array.getJSONObject(i)
                    ConnectedDevice(
                        mac = entry.optString("mac-address", ""),
                        ip = entry.optString("address", ""),
                        hostname = entry.optString("host-name", "")
                    )
                }
                Result.success(devices)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> = Result.failure(UnsupportedOperationException("MikroTik WiFi via REST API"))
    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> = Result.failure(UnsupportedOperationException("MikroTik WiFi via REST API"))
    override suspend fun blockDevice(mac: String): Result<Unit> = Result.failure(UnsupportedOperationException("Block via firewall filter on MikroTik"))
    override suspend fun unblockDevice(mac: String): Result<Unit> = Result.failure(UnsupportedOperationException("Unblock via firewall filter on MikroTik"))
    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/rest/interface")
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                val array = org.json.JSONArray(body)
                var rxBytes = 0L
                var txBytes = 0L
                for (i in 0 until array.length()) {
                    val iface = array.getJSONObject(i)
                    if (iface.optString("type") == "ether") {
                        rxBytes += iface.optString("rx-byte", "0").toLongOrNull() ?: 0L
                        txBytes += iface.optString("tx-byte", "0").toLongOrNull() ?: 0L
                    }
                }
                Result.success(TrafficStats(rxBytes = rxBytes, txBytes = txBytes))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun setDns(primary: String, secondary: String): Result<Unit> = Result.failure(UnsupportedOperationException("DNS via REST API"))
}

// ─── Universal Fallback Adapter ───────────────────────────────────────────────

class UniversalFallbackAdapter(okHttpClient: OkHttpClient) : BaseRouterAdapter(okHttpClient) {
    override val brand = RouterBrand.UNKNOWN
    override val supportedCapabilities: Set<RouterCapability> = emptySet()

    override suspend fun fingerprint(ip: String, port: Int): Boolean = true // matches anything

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        // Generic form-based login attempt
        val scheme = if (port == 443) "https" else "http"
        return try {
            val body = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$scheme://$ip:$port/login")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 302) Result.success(Unit)
                else Result.failure(Exception("Fallback login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        Result.failure(UnsupportedOperationException("Universal fallback: device list not available"))

    override suspend fun reboot(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: reboot not available"))

    override suspend fun getWifiSettings(): Result<WifiSettings> =
        Result.failure(UnsupportedOperationException("Universal fallback: WiFi settings not available"))

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: WiFi settings not available"))

    override suspend fun blockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: block not available"))

    override suspend fun unblockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: unblock not available"))

    override suspend fun getTrafficStats(): Result<TrafficStats> =
        Result.failure(UnsupportedOperationException("Universal fallback: traffic stats not available"))

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: DNS change not available"))

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        Result.failure(UnsupportedOperationException("Universal fallback: network mode not available"))
}

// ─── Hilt Module ─────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object AdapterModule {

    @Provides
    @Singleton
    fun provideGreenpacketAdapter(okHttpClient: OkHttpClient): GreenpacketD2220G1Adapter =
        GreenpacketD2220G1Adapter(okHttpClient)

    @Provides
    @Singleton
    fun provideHuaweiAdapter(okHttpClient: OkHttpClient): HuaweiAdapter =
        HuaweiAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideZteAdapter(okHttpClient: OkHttpClient): ZteAdapter =
        ZteAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideTpLinkAdapter(okHttpClient: OkHttpClient): TpLinkAdapter =
        TpLinkAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenWrtAdapter(okHttpClient: OkHttpClient): OpenWrtAdapter =
        OpenWrtAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideMikroTikAdapter(okHttpClient: OkHttpClient): MikroTikAdapter =
        MikroTikAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideUniversalFallbackAdapter(okHttpClient: OkHttpClient): UniversalFallbackAdapter =
        UniversalFallbackAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideRouterAdapters(
        greenpacket: GreenpacketD2220G1Adapter,
        huawei: HuaweiAdapter,
        zte: ZteAdapter,
        tpLink: TpLinkAdapter,
        openWrt: OpenWrtAdapter,
        mikrotik: MikroTikAdapter,
        fallback: UniversalFallbackAdapter
    ): List<@JvmSuppressWildcards RouterAdapter> = listOf(
        // Priority order — most specific first, fallback last
        greenpacket,
        huawei,
        zte,
        tpLink,
        openWrt,
        mikrotik,
        fallback
    )

    @Provides
    @Singleton
    fun provideRouterAdapterRegistry(
        adapters: List<@JvmSuppressWildcards RouterAdapter>
    ): RouterAdapterRegistry = RouterAdapterRegistry(adapters)
}
