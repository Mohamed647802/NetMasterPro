package com.netmaster.pro.data.adapter

import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.DeviceCategory
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.SmsMessage
import com.netmaster.pro.data.model.TrafficStats
import com.netmaster.pro.data.model.WifiBand
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Adapter for MikroTik RouterOS 7.1+ routers using the REST API.
 *
 * Authentication: HTTP Basic Auth sent with every request (stateless).
 * Base URL: http://{ip}:{port}/rest
 *
 * Supported models: hAP, RB4011, CCR series, any RouterOS 7.1+ device.
 */
class MikroTikAdapter(private val okHttpClient: OkHttpClient) : RouterAdapter {

    override val brand: RouterBrand = RouterBrand.MIKROTIK

    override val supportedCapabilities: Set<RouterCapability> = setOf(
        RouterCapability.REBOOT,
        RouterCapability.PORT_FORWARDING,
        RouterCapability.DNS_CHANGE,
        RouterCapability.CONFIG_BACKUP,
        RouterCapability.MAC_FILTER,
        RouterCapability.QOS,
        RouterCapability.WIFI_5GHZ
    )

    private var routerIp = ""
    private var routerPort = 80
    private var loginUsername = ""
    private var loginPassword = ""

    private val baseUrl get() = "http://$routerIp:$routerPort/rest"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------------------
    // Helper: execute authenticated GET
    // ---------------------------------------------------------------------------
    private fun authenticatedClient(): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val withAuth = original.newBuilder()
                    .header("Authorization", Credentials.basic(loginUsername, loginPassword))
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(withAuth)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun restGet(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl$path").get().build()
            val response = authenticatedClient().newCall(request).execute()
            val body = response.use { it.body?.string() ?: "" }
            if (response.code == 401) {
                return@withContext Result.failure(Exception("Unauthorized: check MikroTik credentials"))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restPost(path: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = jsonBody.toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl$path").post(body).build()
            val response = authenticatedClient().newCall(request).execute()
            val responseBody = response.use { it.body?.string() ?: "" }
            if (response.code == 401) {
                return@withContext Result.failure(Exception("Unauthorized: check MikroTik credentials"))
            }
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restPatch(path: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = jsonBody.toRequestBody(jsonMediaType)
            val request = Request.Builder().url("$baseUrl$path").patch(body).build()
            val response = authenticatedClient().newCall(request).execute()
            val responseBody = response.use { it.body?.string() ?: "" }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restDelete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl$path").delete().build()
            val response = authenticatedClient().newCall(request).execute()
            response.use { it.body?.string() }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------
    // RouterAdapter implementation
    // ---------------------------------------------------------------------------

    override suspend fun fingerprint(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:$port/rest/system/resource"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic("admin", ""))
                .header("Accept", "application/json")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.use { it.body?.string() ?: "" }
            // MikroTik REST returns JSON object with "platform", "version", "board-name"
            response.code == 200 && (
                body.contains("\"platform\"") ||
                body.contains("\"version\"") ||
                body.contains("\"board-name\"") ||
                body.contains("MikroTik")
            )
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun login(ip: String, port: Int, username: String, password: String): Result<Unit> {
        routerIp = ip
        routerPort = port
        loginUsername = username
        loginPassword = password

        // Verify credentials by calling system/resource
        return when (val result = restGet("/system/resource")) {
            is Result.Success -> Result.success(Unit)
            is Result.Failure -> {
                val msg = result.exception?.message ?: "Login failed"
                if (msg.contains("Unauthorized")) {
                    Result.failure(Exception("Invalid MikroTik credentials for $ip"))
                } else {
                    Result.failure(Exception(msg))
                }
            }
        }
    }

    override suspend fun logout() {
        // Stateless Basic Auth — just clear stored state
        routerIp = ""
        loginUsername = ""
        loginPassword = ""
    }

    override suspend fun getLteSignal(): Result<LteSignalData> {
        return Result.failure(Exception("LTE signal monitoring not supported by MikroTik REST adapter. MikroTik is a wired/wireless router, not a 4G LTE CPE."))
    }

    override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        try {
            // Source 1: Wireless registration table (clients connected to wlan interfaces)
            val wirelessMap = mutableMapOf<String, Pair<WifiBand, Int>>() // mac → (band, rssi)
            restGet("/interface/wireless/registration-table").getOrNull()?.let { body ->
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val entry = arr.getJSONObject(i)
                    val mac = entry.optString("mac-address", "").uppercase()
                    val iface = entry.optString("interface", "")
                    val signalStr = entry.optString("signal-strength", "0dBm")
                        .replace("dBm", "").trim()
                    val rssi = signalStr.toIntOrNull() ?: 0
                    val band = when {
                        iface.endsWith("1") -> WifiBand.BAND_2_4GHZ
                        iface.endsWith("2") -> WifiBand.BAND_5GHZ
                        else -> WifiBand.UNKNOWN
                    }
                    if (mac.isNotEmpty()) wirelessMap[mac] = Pair(band, rssi)
                }
            }

            // Source 2: DHCP leases (IP address and hostname info)
            val leaseBody = restGet("/ip/dhcp-server/lease").getOrNull() ?: "[]"
            val leaseArr = JSONArray(leaseBody)

            val devices = mutableListOf<ConnectedDevice>()
            val now = System.currentTimeMillis()

            for (i in 0 until leaseArr.length()) {
                val lease = leaseArr.getJSONObject(i)
                val mac = lease.optString("mac-address", "").uppercase()
                val ip = lease.optString("address", "")
                val hostname = lease.optString("host-name", "")
                val status = lease.optString("status", "")
                val expiresAfter = lease.optString("expires-after", "0:00:00")

                if (mac.isEmpty() || ip.isEmpty()) continue

                // Parse expires-after "HH:MM:SS" to seconds
                val leaseSeconds = parseTimeToSeconds(expiresAfter)

                val (band, rssi) = wirelessMap[mac] ?: Pair(WifiBand.WIRED, 0)

                devices.add(
                    ConnectedDevice(
                        mac = mac,
                        ip = ip,
                        hostname = hostname,
                        vendor = "",
                        nickname = "",
                        category = DeviceCategory.UNKNOWN,
                        isTrusted = false,
                        band = if (wirelessMap.containsKey(mac)) band else WifiBand.WIRED,
                        rssi = rssi,
                        leaseTime = leaseSeconds,
                        firstSeen = now,
                        lastSeen = now
                    )
                )
            }

            // Add wireless clients that don't have DHCP leases
            for ((mac, bandRssi) in wirelessMap) {
                if (devices.none { it.mac == mac }) {
                    devices.add(
                        ConnectedDevice(
                            mac = mac,
                            ip = "",
                            hostname = "",
                            vendor = "",
                            nickname = "",
                            category = DeviceCategory.UNKNOWN,
                            isTrusted = false,
                            band = bandRssi.first,
                            rssi = bandRssi.second,
                            leaseTime = -1,
                            firstSeen = now,
                            lastSeen = now
                        )
                    )
                }
            }

            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTimeToSeconds(time: String): Int {
        return try {
            val parts = time.split(":")
            if (parts.size == 3) {
                parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            } else -1
        } catch (e: Exception) {
            -1
        }
    }

    override suspend fun getWifiSettings(): Result<WifiSettings> {
        return try {
            val body = restGet("/interface/wireless").getOrThrow()
            val arr = JSONArray(body)
            if (arr.length() == 0) return Result.failure(Exception("No wireless interfaces found"))
            val wlan = arr.getJSONObject(0)
            Result.success(
                WifiSettings(
                    ssid = wlan.optString("ssid", ""),
                    password = "",
                    band = wlan.optString("band", "2ghz-b/g/n"),
                    channel = wlan.optString("channel", "0").toIntOrNull() ?: 0,
                    isEnabled = !wlan.optBoolean("disabled", false),
                    securityMode = wlan.optString("security-profile", "default")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> {
        return try {
            val body = restGet("/interface/wireless").getOrThrow()
            val arr = JSONArray(body)
            if (arr.length() == 0) return Result.failure(Exception("No wireless interfaces found"))
            val wlan = arr.getJSONObject(0)
            val id = wlan.optString(".id", "")
            val patchJson = JSONObject().apply {
                put("ssid", settings.ssid)
                put("disabled", !settings.isEnabled)
            }.toString()
            restPatch("/interface/wireless/$id", patchJson).map { Unit }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockDevice(mac: String): Result<Unit> {
        return restPost(
            "/interface/wireless/access-list",
            JSONObject().apply {
                put("mac-address", mac.uppercase())
                put("authentication", false)
                put("forwarding", false)
                put("comment", "Blocked by NetMasterPro")
            }.toString()
        ).map { Unit }
    }

    override suspend fun unblockDevice(mac: String): Result<Unit> {
        return try {
            val body = restGet("/interface/wireless/access-list").getOrThrow()
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("mac-address", "").equals(mac, ignoreCase = true)) {
                    val id = entry.optString(".id", "")
                    if (id.isNotEmpty()) {
                        return restDelete("/interface/wireless/access-list/$id")
                    }
                }
            }
            Result.failure(Exception("No access-list entry found for MAC: $mac"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reboot(): Result<Unit> {
        return restPost("/system/reboot", "{}").map { Unit }
    }

    override suspend fun getSmsMessages(): Result<List<SmsMessage>> {
        return Result.failure(Exception("SMS not supported by MikroTik REST adapter. MikroTik is not a cellular router."))
    }

    override suspend fun sendSms(to: String, body: String): Result<Unit> {
        return Result.failure(Exception("SMS not supported by MikroTik REST adapter."))
    }

    override suspend fun deleteSms(id: String): Result<Unit> {
        return Result.failure(Exception("SMS not supported by MikroTik REST adapter."))
    }

    override suspend fun sendUssd(code: String): Result<String> {
        return Result.failure(Exception("USSD not supported by MikroTik REST adapter."))
    }

    override suspend fun getTrafficStats(): Result<TrafficStats> {
        return try {
            val body = restGet("/interface").getOrThrow()
            val arr = JSONArray(body)
            // Find WAN interface: prefer pppoe-out or ether1
            var wanIface: JSONObject? = null
            for (i in 0 until arr.length()) {
                val iface = arr.getJSONObject(i)
                val name = iface.optString("name", "")
                val type = iface.optString("type", "")
                if (type == "pppoe-out" || name == "pppoe-out1" || name == "ether1") {
                    wanIface = iface
                    if (type == "pppoe-out") break
                }
            }
            if (wanIface == null && arr.length() > 0) {
                wanIface = arr.getJSONObject(0)
            }
            val rxBytes = wanIface?.optString("rx-byte", "0")?.toLongOrNull() ?: 0L
            val txBytes = wanIface?.optString("tx-byte", "0")?.toLongOrNull() ?: 0L
            Result.success(TrafficStats(rxBytes = rxBytes, txBytes = txBytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDslStats(): Result<DslStats> {
        return Result.failure(Exception("DSL stats not supported by MikroTik REST adapter."))
    }

    override suspend fun setDns(primary: String, secondary: String): Result<Unit> {
        val servers = if (secondary.isNotEmpty()) "$primary,$secondary" else primary
        return restPatch(
            "/ip/dns",
            JSONObject().apply {
                put("servers", servers)
                put("allow-remote-requests", "yes")
            }.toString()
        ).map { Unit }
    }

    override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> {
        return Result.failure(Exception("Network mode not applicable to MikroTik routers. MikroTik is not a cellular modem."))
    }

    override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> {
        return Result.failure(Exception("LTE band selection not supported by MikroTik REST adapter."))
    }

    override suspend fun downloadBackup(): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create backup file
            restPost("/system/backup", JSONObject().apply {
                put("name", "netmaster-backup")
                put("dont-encrypt", "yes")
            }.toString())

            // Brief pause for backup to complete
            Thread.sleep(2000)

            // Step 2: Get file list to find the backup
            restGet("/file").getOrNull()?.let { filesBody ->
                val filesArr = JSONArray(filesBody)
                for (i in 0 until filesArr.length()) {
                    val file = filesArr.getJSONObject(i)
                    val name = file.optString("name", "")
                    if (name.contains("netmaster-backup") && name.endsWith(".backup")) {
                        // Step 3: Download the file content via /file endpoint
                        val fileBody = restGet("/file/$name").getOrNull()
                        if (fileBody != null) {
                            return@withContext Result.success(fileBody.toByteArray())
                        }
                    }
                }
            }

            Result.failure(Exception("Backup file not found after creation"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(data: ByteArray): Result<Unit> {
        return Result.failure(Exception("Backup restore not supported via REST API. Use WinBox or SSH to restore backups."))
    }

    override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> {
        return try {
            // Get enabled services
            val servicesBody = restGet("/ip/service").getOrNull() ?: "[]"
            val servicesArr = JSONArray(servicesBody)
            val openPorts = mutableListOf<Int>()
            var remoteManagementEnabled = false
            var adminPasswordChanged = false

            for (i in 0 until servicesArr.length()) {
                val svc = servicesArr.getJSONObject(i)
                val name = svc.optString("name", "")
                val disabled = svc.optBoolean("disabled", true)
                val port = svc.optString("port", "0").toIntOrNull() ?: 0
                if (!disabled) {
                    openPorts.add(port)
                    if (name == "www" || name == "www-ssl" || name == "api" || name == "api-ssl") {
                        remoteManagementEnabled = true
                    }
                }
            }

            // Check firewall rules count
            val firewallBody = restGet("/ip/firewall/filter").getOrNull() ?: "[]"
            val firewallArr = JSONArray(firewallBody)
            val firewallEnabled = firewallArr.length() > 0

            // Check if admin password changed (heuristic: if telnet/ftp disabled, more secure)
            val telnetEnabled = openPorts.contains(23)
            adminPasswordChanged = !telnetEnabled

            Result.success(
                RouterSecurityInfo(
                    adminPasswordChanged = adminPasswordChanged,
                    remoteManagementEnabled = remoteManagementEnabled,
                    upnpEnabled = false, // MikroTik UPnP requires explicit config
                    firewallEnabled = firewallEnabled,
                    openPorts = openPorts
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get existing access list entries and clear them
            val existingBody = restGet("/interface/wireless/access-list").getOrNull() ?: "[]"
            val existingArr = JSONArray(existingBody)
            for (i in 0 until existingArr.length()) {
                val entry = existingArr.getJSONObject(i)
                val id = entry.optString(".id", "")
                if (id.isNotEmpty()) {
                    restDelete("/interface/wireless/access-list/$id")
                }
            }

            when (mode) {
                MacFilterMode.DISABLED -> {
                    // Already cleared, nothing more to do
                }
                MacFilterMode.BLACKLIST -> {
                    for (mac in macs) {
                        restPost(
                            "/interface/wireless/access-list",
                            JSONObject().apply {
                                put("mac-address", mac.uppercase())
                                put("authentication", false)
                                put("forwarding", false)
                                put("comment", "Blacklisted by NetMasterPro")
                            }.toString()
                        )
                    }
                }
                MacFilterMode.WHITELIST -> {
                    for (mac in macs) {
                        restPost(
                            "/interface/wireless/access-list",
                            JSONObject().apply {
                                put("mac-address", mac.uppercase())
                                put("authentication", true)
                                put("forwarding", true)
                                put("comment", "Whitelisted by NetMasterPro")
                            }.toString()
                        )
                    }
                    // Set default policy to deny unknown MACs
                    val wlanBody = restGet("/interface/wireless").getOrNull() ?: "[]"
                    val wlanArr = JSONArray(wlanBody)
                    for (i in 0 until wlanArr.length()) {
                        val wlan = wlanArr.getJSONObject(i)
                        val id = wlan.optString(".id", "")
                        if (id.isNotEmpty()) {
                            restPatch(
                                "/interface/wireless/$id",
                                JSONObject().apply {
                                    put("default-authentication", false)
                                }.toString()
                            )
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setQos(rules: List<QosRule>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (rule in rules) {
                val uploadLimit = "${rule.uploadKbps}k"
                val downloadLimit = "${rule.downloadKbps}k"
                restPost(
                    "/queue/simple",
                    JSONObject().apply {
                        put("name", "netmaster-qos-${rule.mac.replace(":", "")}")
                        put("target", "${rule.mac}/32")
                        put("max-limit", "$uploadLimit/$downloadLimit")
                        put("priority", "${rule.priority}")
                        put("comment", "NetMasterPro QoS")
                    }.toString()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setGuestWifi(enabled: Boolean, ssid: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if guest interface already exists
            val wlanBody = restGet("/interface/wireless").getOrNull() ?: "[]"
            val wlanArr = JSONArray(wlanBody)
            var guestId = ""
            for (i in 0 until wlanArr.length()) {
                val wlan = wlanArr.getJSONObject(i)
                if (wlan.optString("comment", "").contains("guest", ignoreCase = true) ||
                    wlan.optString("name", "").contains("guest", ignoreCase = true)) {
                    guestId = wlan.optString(".id", "")
                    break
                }
            }

            if (!enabled) {
                if (guestId.isNotEmpty()) {
                    restPatch("/interface/wireless/$guestId", JSONObject().apply {
                        put("disabled", true)
                    }.toString())
                }
                return@withContext Result.success(Unit)
            }

            if (guestId.isEmpty()) {
                // Create security profile for guest
                restPost(
                    "/interface/wireless/security-profiles",
                    JSONObject().apply {
                        put("name", "netmaster-guest-profile")
                        put("mode", "dynamic-keys")
                        put("authentication-types", "wpa2-psk")
                        put("wpa2-pre-shared-key", password)
                        put("comment", "NetMasterPro guest profile")
                    }.toString()
                )

                // Find master wlan interface
                val masterName = if (wlanArr.length() > 0) wlanArr.getJSONObject(0).optString("name", "wlan1") else "wlan1"

                // Create virtual AP
                restPost(
                    "/interface/wireless",
                    JSONObject().apply {
                        put("name", "wlan-guest")
                        put("master-interface", masterName)
                        put("ssid", ssid)
                        put("mode", "ap-bridge")
                        put("security-profile", "netmaster-guest-profile")
                        put("disabled", false)
                        put("comment", "NetMasterPro guest network")
                    }.toString()
                )
            } else {
                // Update existing guest interface
                restPatch("/interface/wireless/$guestId", JSONObject().apply {
                    put("ssid", ssid)
                    put("disabled", false)
                }.toString())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> {
        return restPost(
            "/ip/firewall/nat",
            JSONObject().apply {
                put("chain", "dstnat")
                put("protocol", rule.protocol.lowercase())
                put("dst-port", rule.externalPort.toString())
                put("action", "dst-nat")
                put("to-addresses", rule.internalIp)
                put("to-ports", rule.internalPort.toString())
                put("comment", rule.name)
            }.toString()
        ).map { Unit }
    }

    override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> {
        return try {
            val body = restGet("/ip/firewall/nat").getOrThrow()
            val arr = JSONArray(body)
            val rules = mutableListOf<PortForwardRule>()
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("chain", "") != "dstnat") continue
                rules.add(
                    PortForwardRule(
                        name = entry.optString("comment", "Rule ${i + 1}"),
                        protocol = entry.optString("protocol", "tcp"),
                        externalPort = entry.optString("dst-port", "0").toIntOrNull() ?: 0,
                        internalIp = entry.optString("to-addresses", ""),
                        internalPort = entry.optString("to-ports", "0").toIntOrNull() ?: 0
                    )
                )
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setApn(apn: ApnProfile): Result<Unit> {
        return Result.failure(Exception("APN configuration not supported by MikroTik REST adapter. MikroTik is not a cellular modem."))
    }

    override suspend fun getApnProfiles(): Result<List<ApnProfile>> {
        return Result.failure(Exception("APN profiles not supported by MikroTik REST adapter."))
    }

    // ---------------------------------------------------------------------------
    // Helper extension (avoids Result.getOrThrow unavailability issues)
    // ---------------------------------------------------------------------------
    private fun <T> Result<T>.getOrThrow(): T {
        return if (isSuccess) getOrNull()!! else throw (exceptionOrNull() ?: Exception("Unknown error"))
    }

    private fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
        return if (isSuccess) Result.success(transform(getOrNull()!!)) else Result.failure(exceptionOrNull()!!)
    }
}
