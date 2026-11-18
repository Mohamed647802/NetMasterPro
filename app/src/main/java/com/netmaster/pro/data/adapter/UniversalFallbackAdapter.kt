package com.netmaster.pro.data.adapter

import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.SmsMessage
import com.netmaster.pro.data.model.TrafficStats
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
import okhttp3.OkHttpClient

/**
 * Universal fallback adapter — always last in priority, matches any router.
 * Returns not-supported errors for all operations.
 */
class UniversalFallbackAdapter(private val okHttpClient: OkHttpClient) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.UNKNOWN
    override val supportedCapabilities: Set<RouterCapability> = emptySet()

    override suspend fun fingerprint(ip: String, port: Int): Boolean = true // matches anything

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        return try {
            val scheme = if (port == 443) "https" else "http"
            val body = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$scheme://$ip:$port/login")
                .post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 302) Result.success(Unit)
                else Result.failure(Exception("Login failed: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() { /* no-op */ }

    override suspend fun getLteSignal(): Result<LteSignalData> =
        Result.failure(UnsupportedOperationException("LTE signal not available for unknown router"))

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        Result.failure(UnsupportedOperationException("Device list not available for unknown router"))

    override suspend fun blockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Block not available for unknown router"))

    override suspend fun unblockDevice(mac: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Unblock not available for unknown router"))

    override suspend fun getWifiSettings(): Result<WifiSettings> =
        Result.failure(UnsupportedOperationException("WiFi settings not available for unknown router"))

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        Result.failure(UnsupportedOperationException("WiFi settings not available for unknown router"))

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Guest WiFi not supported"))

    override suspend fun reboot(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Reboot not available for unknown router"))

    override suspend fun downloadBackup(): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("Backup not supported"))

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> =
        Result.failure(UnsupportedOperationException("Backup not supported"))

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> =
        Result.failure(UnsupportedOperationException("SMS not supported"))

    override suspend fun sendSms(to: String, body: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("SMS not supported"))

    override suspend fun deleteSms(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("SMS not supported"))

    override suspend fun sendUssd(code: String): Result<String> =
        Result.failure(UnsupportedOperationException("USSD not supported"))

    override suspend fun getTrafficStats(): Result<TrafficStats> =
        Result.failure(UnsupportedOperationException("Traffic stats not available"))

    override suspend fun getDslStats(): Result<DslStats> =
        Result.failure(UnsupportedOperationException("DSL stats not supported"))

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("DNS change not available"))

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        Result.failure(UnsupportedOperationException("Network mode not available"))

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
        Result.failure(UnsupportedOperationException("Band lock not supported"))

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> =
        Result.failure(UnsupportedOperationException("Security info not available"))

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("MAC filter not supported"))

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> =
        Result.failure(UnsupportedOperationException("QoS not supported"))

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        Result.failure(UnsupportedOperationException("Port forwarding not supported"))

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
        Result.failure(UnsupportedOperationException("Port forwarding not supported"))

    override suspend fun setApn(apn: ApnProfile): Result<Unit> =
        Result.failure(UnsupportedOperationException("APN not supported"))

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
        Result.failure(UnsupportedOperationException("APN not supported"))
}
