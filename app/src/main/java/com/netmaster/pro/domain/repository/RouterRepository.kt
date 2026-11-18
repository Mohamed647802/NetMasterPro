package com.netmaster.pro.domain.repository

import com.netmaster.pro.data.model.*
import com.netmaster.pro.domain.adapter.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all router operations.
 * Implementations delegate to the currently active [RouterAdapter].
 */
interface RouterRepository {

    /** The currently active adapter brand */
    val activeBrand: RouterBrand

    /** Switches the active adapter used for all subsequent operations */
    fun switchAdapter(adapter: RouterAdapter)

    // ─── Connection ─────────────────────────────────────────────────────────

    suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun fingerprint(ip: String, port: Int): RouterBrand

    // ─── Signal ─────────────────────────────────────────────────────────────

    suspend fun getLteSignal(): Result<LteSignalData>
    fun observeLteSignal(): Flow<LteSignalData>

    // ─── Devices ────────────────────────────────────────────────────────────

    suspend fun getConnectedDevices(): Result<List<ConnectedDevice>>
    suspend fun blockDevice(mac: String): Result<Unit>
    suspend fun unblockDevice(mac: String): Result<Unit>

    // ─── Wi-Fi ──────────────────────────────────────────────────────────────

    suspend fun getWifiSettings(): Result<WifiSettings>
    suspend fun setWifiSettings(settings: WifiSettings): Result<Unit>
    suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit>

    // ─── System ─────────────────────────────────────────────────────────────

    suspend fun reboot(): Result<Unit>
    suspend fun downloadBackup(): Result<ByteArray>
    suspend fun restoreBackup(data: ByteArray): Result<Unit>

    // ─── SMS / USSD ─────────────────────────────────────────────────────────

    suspend fun getSmsMessages(): Result<List<SmsMessage>>
    suspend fun sendSms(to: String, body: String): Result<Unit>
    suspend fun deleteSms(id: String): Result<Unit>
    suspend fun sendUssd(code: String): Result<String>

    // ─── Traffic / Stats ────────────────────────────────────────────────────

    suspend fun getTrafficStats(): Result<TrafficStats>
    suspend fun getDslStats(): Result<DslStats>

    // ─── Network Config ─────────────────────────────────────────────────────

    suspend fun setDns(primary: String, secondary: String): Result<Unit>
    suspend fun setNetworkMode(mode: NetworkMode): Result<Unit>
    suspend fun setBandSelection(bands: List<LteBand>): Result<Unit>

    // ─── Security ───────────────────────────────────────────────────────────

    suspend fun getSecurityInfo(): Result<RouterSecurityInfo>
    suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit>

    // ─── QoS / Routing ──────────────────────────────────────────────────────

    suspend fun setQos(rules: List<QosRule>): Result<Unit>
    suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit>
    suspend fun getPortForwardRules(): Result<List<PortForwardRule>>

    // ─── APN ────────────────────────────────────────────────────────────────

    suspend fun setApn(apn: ApnProfile): Result<Unit>
    suspend fun getApnProfiles(): Result<List<ApnProfile>>
}
