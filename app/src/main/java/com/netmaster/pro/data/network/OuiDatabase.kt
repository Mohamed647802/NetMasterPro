package com.netmaster.pro.data.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and queries the bundled OUI MAC vendor database from `assets/oui.txt`.
 *
 * File format: one entry per line, tab-separated:
 *   `MAC_PREFIX\tVENDOR_NAME`
 *
 * Example:
 *   `00:00:0C\tCisco Systems, Inc`
 *
 * The asset file `assets/oui.txt` must be bundled with the APK. This class only
 * reads it; the file itself is maintained separately.
 */
@Singleton
class OuiDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OuiDatabase"
        private const val OUI_ASSET = "oui.txt"
        private const val SEPARATOR = '\t'
    }

    /** Lazily loaded map: uppercase MAC prefix (e.g. "AA:BB:CC") -> vendor name */
    private val ouiMap: HashMap<String, String> by lazy {
        loadOuiMap()
    }

    /**
     * Looks up the vendor name for a given MAC address or prefix.
     *
     * @param macPrefix A MAC address string. The first 3 octets are used for the lookup,
     *                  e.g. "AA:BB:CC:DD:EE:FF" → looks up "AA:BB:CC".
     *                  You may also pass just the 3-octet prefix directly.
     * @return The vendor name, or null if not found in the database.
     */
    fun lookup(macPrefix: String): String? {
        val normalized = normalizeMacPrefix(macPrefix) ?: return null
        // Try 3-octet prefix first (most specific)
        return ouiMap[normalized]
            ?: ouiMap[normalized.substring(0, 5)] // 2-octet fallback (OUI-28/36 blocks)
    }

    private fun normalizeMacPrefix(mac: String): String? {
        // Strip any separators and uppercase
        val stripped = mac.uppercase()
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")

        if (stripped.length < 6) return null

        // Reconstruct as colon-separated 3-octet prefix
        return "${stripped.substring(0, 2)}:${stripped.substring(2, 4)}:${stripped.substring(4, 6)}"
    }

    private fun loadOuiMap(): HashMap<String, String> {
        val map = HashMap<String, String>(35_000) // OUI database has ~35k entries
        try {
            context.assets.open(OUI_ASSET).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    if (line.isBlank() || line.startsWith('#')) return@forEachLine
                    val tabIndex = line.indexOf(SEPARATOR)
                    if (tabIndex < 0) return@forEachLine
                    val prefix = line.substring(0, tabIndex).trim().uppercase()
                    val vendor = line.substring(tabIndex + 1).trim()
                    if (prefix.isNotEmpty() && vendor.isNotEmpty()) {
                        map[prefix] = vendor
                    }
                }
            }
            Log.i(TAG, "Loaded ${map.size} OUI entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OUI database from assets", e)
        }
        return map
    }

    /** Returns the number of loaded OUI entries (useful for diagnostics). */
    fun size(): Int = ouiMap.size
}
