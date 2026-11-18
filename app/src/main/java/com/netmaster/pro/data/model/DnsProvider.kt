package com.netmaster.pro.data.model

/**
 * Well-known DNS provider configurations.
 * [primary] and [secondary] are IPv4 addresses.
 */
sealed class DnsProvider(
    val displayName: String,
    val primary: String,
    val secondary: String
) {
    object CloudflareFamily : DnsProvider(
        displayName = "Cloudflare Family",
        primary = "1.1.1.3",
        secondary = "1.0.0.3"
    )

    object OpenDnsFamily : DnsProvider(
        displayName = "OpenDNS Family Shield",
        primary = "208.67.222.123",
        secondary = "208.67.220.123"
    )

    object AdGuard : DnsProvider(
        displayName = "AdGuard DNS",
        primary = "94.140.14.14",
        secondary = "94.140.15.15"
    )

    object Google : DnsProvider(
        displayName = "Google Public DNS",
        primary = "8.8.8.8",
        secondary = "8.8.4.4"
    )

    /** User-supplied custom DNS servers */
    data class Custom(
        val customPrimary: String,
        val customSecondary: String
    ) : DnsProvider(
        displayName = "Custom",
        primary = customPrimary,
        secondary = customSecondary
    )

    companion object {
        val presets: List<DnsProvider> = listOf(CloudflareFamily, OpenDnsFamily, AdGuard, Google)

        fun fromAddresses(primary: String, secondary: String): DnsProvider {
            return presets.firstOrNull { it.primary == primary && it.secondary == secondary }
                ?: Custom(primary, secondary)
        }
    }
}
