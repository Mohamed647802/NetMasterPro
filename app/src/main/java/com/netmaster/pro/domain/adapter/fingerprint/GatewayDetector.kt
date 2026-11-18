package com.netmaster.pro.domain.adapter.fingerprint

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the default gateway IP address using the Android [ConnectivityManager].
 *
 * This relies on [android.net.LinkProperties.getRoutes] to find the default route
 * and extract its gateway address. No internet permission is required beyond
 * ACCESS_NETWORK_STATE.
 */
@Singleton
class GatewayDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the IPv4 host address of the active network's default gateway,
     * or null if no active network or gateway is present.
     */
    fun getDefaultGateway(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        return props.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress
    }

    /**
     * Returns all gateway addresses found across all active routes,
     * filtered to IPv4 addresses only. Useful when there are multiple gateways.
     */
    fun getAllGateways(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return emptyList()
        val props = cm.getLinkProperties(network) ?: return emptyList()
        return props.routes
            .mapNotNull { it.gateway?.hostAddress }
            .filter { it.contains('.') } // IPv4 only
            .distinct()
    }
}
