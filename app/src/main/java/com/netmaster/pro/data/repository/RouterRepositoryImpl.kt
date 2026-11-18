package com.netmaster.pro.data.repository

import android.util.Log
import com.netmaster.pro.data.local.*
import com.netmaster.pro.data.model.*
import com.netmaster.pro.domain.adapter.*
import com.netmaster.pro.domain.repository.RouterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouterRepositoryImpl @Inject constructor(
    private val signalSnapshotDao: SignalSnapshotDao,
    private val routerProfileDao: RouterProfileDao,
    private val adapters: List<@JvmSuppressWildcards RouterAdapter>
) : RouterRepository {

    companion object {
        private const val TAG = "RouterRepositoryImpl"
    }

    @Volatile
    private var currentAdapter: RouterAdapter? = null

    private val _lteSignalFlow = MutableStateFlow<LteSignalData>(LteSignalData())

    override val activeBrand: RouterBrand
        get() = currentAdapter?.brand ?: RouterBrand.UNKNOWN

    override fun switchAdapter(adapter: RouterAdapter) {
        Log.i(TAG, "Switching adapter to ${adapter.brand}")
        currentAdapter = adapter
    }

    private fun requireAdapter(): RouterAdapter =
        currentAdapter ?: throw IllegalStateException("No router adapter selected. Call login() first.")

    override suspend fun fingerprint(ip: String, port: Int): RouterBrand {
        for (adapter in adapters) {
            try {
                if (adapter.fingerprint(ip, port)) {
                    Log.i(TAG, "Fingerprinted $ip:$port as ${adapter.brand}")
                    currentAdapter = adapter
                    return adapter.brand
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fingerprint probe failed for ${adapter.brand}: ${e.message}")
            }
        }
        return RouterBrand.UNKNOWN
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        return try {
            val adapter = requireAdapter()
            val result = adapter.login(ip, port, username, password)
            if (result.isSuccess) {
                // Persist profile
                routerProfileDao.save(
                    RouterProfileEntity(
                        ip = ip, port = port,
                        brand = adapter.brand.name,
                        capabilities = adapter.supportedCapabilities.joinToString(",") { it.name }
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        try {
            currentAdapter?.logout()
        } catch (e: Exception) {
            Log.w(TAG, "Logout error (ignored)", e)
        }
    }

    override suspend fun getLteSignal(): Result<LteSignalData> {
        return try {
            val result = requireAdapter().getLteSignal()
            result.onSuccess { signal ->
                _lteSignalFlow.value = signal
                // Persist snapshot
                signalSnapshotDao.insertSnapshot(
                    SignalSnapshotEntity(
                        rsrp = signal.rsrp,
                        rsrq = signal.rsrq,
                        sinr = signal.sinr,
                        rssi = signal.rssi,
                        pci = signal.pci,
                        cellId = signal.cellId,
                        eNbId = signal.eNbId,
                        earfcn = signal.earfcn,
                        band = signal.band,
                        caActive = signal.caActive,
                        timestamp = signal.timestamp
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getLteSignal failed", e)
            Result.failure(e)
        }
    }

    override fun observeLteSignal(): Flow<LteSignalData> = _lteSignalFlow.asStateFlow()

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
        safeAdapterCall { it.getConnectedDevices() }

    override suspend fun blockDevice(mac: String): Result<Unit> =
        safeAdapterCall { it.blockDevice(mac) }

    override suspend fun unblockDevice(mac: String): Result<Unit> =
        safeAdapterCall { it.unblockDevice(mac) }

    override suspend fun getWifiSettings(): Result<WifiSettings> =
        safeAdapterCall { it.getWifiSettings() }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
        safeAdapterCall { it.setWifiSettings(settings) }

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> =
        safeAdapterCall { it.setGuestWifi(enabled, ssid, password) }

    override suspend fun reboot(): Result<Unit> =
        safeAdapterCall { it.reboot() }

    override suspend fun downloadBackup(): Result<ByteArray> =
        safeAdapterCall { it.downloadBackup() }

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> =
        safeAdapterCall { it.restoreBackup(data) }

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> =
        safeAdapterCall { it.getSmsMessages() }

    override suspend fun sendSms(to: String, body: String): Result<Unit> =
        safeAdapterCall { it.sendSms(to, body) }

    override suspend fun deleteSms(id: String): Result<Unit> =
        safeAdapterCall { it.deleteSms(id) }

    override suspend fun sendUssd(code: String): Result<String> =
        safeAdapterCall { it.sendUssd(code) }

    override suspend fun getTrafficStats(): Result<TrafficStats> =
        safeAdapterCall { it.getTrafficStats() }

    override suspend fun getDslStats(): Result<DslStats> =
        safeAdapterCall { it.getDslStats() }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
        safeAdapterCall { it.setDns(primary, secondary) }

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
        safeAdapterCall { it.setNetworkMode(mode) }

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
        safeAdapterCall { it.setBandSelection(bands) }

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> =
        safeAdapterCall { it.getSecurityInfo() }

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
        safeAdapterCall { it.setMacFilter(mode, macs) }

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> =
        safeAdapterCall { it.setQos(rules) }

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
        safeAdapterCall { it.addPortForwarding(rule) }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
        safeAdapterCall { it.getPortForwardRules() }

    override suspend fun setApn(apn: ApnProfile): Result<Unit> =
        safeAdapterCall { it.setApn(apn) }

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
        safeAdapterCall { it.getApnProfiles() }

    private suspend fun <T> safeAdapterCall(block: suspend (RouterAdapter) -> Result<T>): Result<T> {
        return try {
            block(requireAdapter())
        } catch (e: Exception) {
            Log.e(TAG, "Adapter call failed", e)
            Result.failure(e)
        }
    }
}
