package com.netmaster.pro.data.adapter

import com.netmaster.pro.domain.adapter.RouterAdapter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry that holds all router adapters in priority order.
 * Call [autoDetect] to probe a router IP and return the best matching adapter.
 */
@Singleton
class RouterAdapterRegistry @Inject constructor(
    private val greenpacketAdapter: GreenpacketD2220G1Adapter,
    private val huaweiAdapter: HuaweiAdapter,
    private val zteAdapter: ZteAdapter,
    private val tpLinkAdapter: TpLinkAdapter,
    private val openWrtAdapter: OpenWrtUbusAdapter,
    private val mikrotikAdapter: MikroTikAdapter,
    private val universalAdapter: UniversalFallbackAdapter
) {
    private val adapters: List<RouterAdapter> = listOf(
        greenpacketAdapter,  // Priority 1 — flagship
        huaweiAdapter,       // Priority 2 — Huawei CPEs
        zteAdapter,          // Priority 3 — ZTE ZXHN / MF series
        tpLinkAdapter,       // Priority 4 — TP-Link Archer
        openWrtAdapter,      // Priority 5 — OpenWrt ubus
        mikrotikAdapter,     // Priority 6 — MikroTik REST
        universalAdapter     // Priority 7 — last resort
    )

    var activeAdapter: RouterAdapter = universalAdapter
        private set

    /**
     * Probes [ip]:[port] with each adapter's fingerprint() in priority order.
     * Returns the first adapter that claims the device (or the universal fallback).
     */
    suspend fun autoDetect(ip: String, port: Int): RouterAdapter {
        for (adapter in adapters) {
            try {
                if (adapter.fingerprint(ip, port)) {
                    activeAdapter = adapter
                    return adapter
                }
            } catch (_: Exception) {
                // Try next adapter
            }
        }
        activeAdapter = universalAdapter
        return universalAdapter
    }

    /** Manually override the active adapter (e.g. user selected brand explicitly). */
    fun setAdapter(adapter: RouterAdapter) {
        activeAdapter = adapter
    }

    /** Returns all registered adapters (useful for UI brand selection). */
    fun allAdapters(): List<RouterAdapter> = adapters
}
