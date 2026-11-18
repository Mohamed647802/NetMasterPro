package com.netmaster.pro.domain.adapter

import com.netmaster.pro.data.model.*

/**
 * Core pluggable interface that every router brand adapter must implement.
 *
 * Each adapter encapsulates the HTTP/scraping logic for one router brand/family.
 * The [RouterAdapterRegistry] selects the matching adapter at runtime by calling
 * [fingerprint] on each registered adapter in priority order.
 */
interface RouterAdapter {
    /** The brand this adapter handles */
    val brand: RouterBrand

    /** Set of features this adapter is known to support for its target devices */
    val supportedCapabilities: Set<RouterCapability>

    /**
     * Returns true if this adapter recognises the router at [ip]:[port].
     * Must be a lightweight probe — a single HTTP request is ideal.
     */
    suspend fun fingerprint(ip: String, port: Int): Boolean

    /** Authenticates against the router. Returns [Result.success] on success. */
    suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit>

    /** Ends the current session (best-effort). */
    suspend fun logout()

    // ─── Signal & Network ───────────────────────────────────────────────────

    suspend fun getLteSignal(): Result<LteSignalData>

    // ─── Device Management ──────────────────────────────────────────────────

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
