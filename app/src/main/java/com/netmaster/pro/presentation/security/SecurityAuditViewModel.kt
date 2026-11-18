package com.netmaster.pro.presentation.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getWirelessInfo
import com.netmaster.pro.domain.adapter.getUpnpMappings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SecurityCheck(
    val id: String,
    val name: String,
    val nameAr: String,
    val severity: CheckSeverity,
    val passed: Boolean?,         // null = not checked yet, true = passed, false = failed
    val pointsLost: Int,
    val fixInstructionAr: String,
    val isChecking: Boolean = false
)

enum class CheckSeverity { CRITICAL, HIGH, MEDIUM, LOW }

data class SecurityAuditUiState(
    val checks: List<SecurityCheck> = defaultChecks(),
    val score: Int = 100,
    val scanning: Boolean = false,
    val errorMessage: String? = null
) {
    val scoreColor get() = when {
        score >= 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        score >= 60 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> androidx.compose.ui.graphics.Color(0xFFE53935)
    }
    val gradeLabel get() = when {
        score >= 90 -> "A — ممتاز"
        score >= 80 -> "B — جيد جداً"
        score >= 70 -> "C — جيد"
        score >= 60 -> "D — مقبول"
        else -> "F — ضعيف"
    }
}

private fun defaultChecks() = listOf(
    SecurityCheck("wps", "WPS Enabled", "WPS مفعّل", CheckSeverity.HIGH, null, 20, "أوقف WPS من إعدادات الواي-فاي في الراوتر"),
    SecurityCheck("encryption", "WEP/Open Encryption", "تشفير WEP أو مفتوح", CheckSeverity.CRITICAL, null, 30, "غيّر التشفير إلى WPA3 أو WPA2"),
    SecurityCheck("default_pass", "Default Password", "كلمة مرور افتراضية", CheckSeverity.CRITICAL, null, 25, "غيّر كلمة مرور الراوتر إلى كلمة قوية وفريدة"),
    SecurityCheck("wan_admin", "WAN Admin Port Open", "منفذ الإدارة مفتوح من الإنترنت", CheckSeverity.HIGH, null, 15, "أوقف الوصول عن بُعد لواجهة الراوتر"),
    SecurityCheck("upnp", "UPnP with Open Ports", "UPnP يفتح منافذ تلقائياً", CheckSeverity.MEDIUM, null, 10, "أوقف UPnP إن لم تحتجه"),
    SecurityCheck("dns_hijack", "DNS Hijacking", "اختطاف DNS", CheckSeverity.HIGH, null, 20, "غيّر DNS إلى 1.1.1.1 أو 8.8.8.8")
)

@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SecurityAuditUiState())
    val uiState: StateFlow<SecurityAuditUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun startAudit(routerIp: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, checks = defaultChecks()) }

            val checkJobs = listOf(
                async { checkWps() },
                async { checkEncryption() },
                async { checkDefaultPassword(routerIp) },
                async { checkWanAdminPort(routerIp) },
                async { checkUpnp() },
                async { checkDnsHijacking() }
            )
            checkJobs.awaitAll()

            val finalChecks = _uiState.value.checks
            val score = 100 - finalChecks.filter { it.passed == false }.sumOf { it.pointsLost }
            _uiState.update { it.copy(score = score.coerceAtLeast(0), scanning = false) }
        }
    }

    private suspend fun checkWps() {
        setChecking("wps")
        val passed = try {
            val info = routerAdapter.getWirelessInfo()
            !info.wpsEnabled
        } catch (e: Exception) { true } // assume pass if can't check
        updateCheck("wps", passed)
    }

    private suspend fun checkEncryption() {
        setChecking("encryption")
        val passed = try {
            val info = routerAdapter.getWirelessInfo()
            info.security.uppercase().let { sec ->
                !sec.contains("WEP") && !sec.contains("NONE") && !sec.contains("OPEN")
            }
        } catch (e: Exception) { true }
        updateCheck("encryption", passed)
    }

    private suspend fun checkDefaultPassword(routerIp: String) {
        setChecking("default_pass")
        val commonPasswords = listOf(
            "admin", "password", "1234", "12345", "123456",
            "admin123", "root", "", "0000", "vodafone", "huawei",
            "greenpacket", "support", "user", "guest"
        )
        val passed = withContext(Dispatchers.IO) {
            var allFailed = true
            for (pass in commonPasswords) {
                try {
                    val result = routerAdapter.login(routerIp, 80, "admin", pass)
                    if (result.isSuccess) {
                        allFailed = false
                        break
                    }
                } catch (_: Exception) {}
            }
            allFailed // passed = true means NOT using default password
        }
        updateCheck("default_pass", passed)
    }

    private suspend fun checkWanAdminPort(routerIp: String) {
        setChecking("wan_admin")
        val passed = withContext(Dispatchers.IO) {
            var open = false
            for (port in listOf(8080, 8443, 80, 443)) {
                try {
                    val externalUrl = "https://api.ipify.org" // get public IP
                    val req = Request.Builder().url(externalUrl).build()
                    val publicIp = client.newCall(req).execute().use { it.body?.string()?.trim() ?: "" }
                    if (publicIp.isNotEmpty()) {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(publicIp, port), 3000)
                                open = true
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            !open
        }
        updateCheck("wan_admin", passed)
    }

    private suspend fun checkUpnp() {
        setChecking("upnp")
        val passed = try {
            val mappings = routerAdapter.getUpnpMappings()
            mappings.isEmpty() // no open UPnP ports = safer
        } catch (e: Exception) { true }
        updateCheck("upnp", passed)
    }

    private suspend fun checkDnsHijacking() {
        setChecking("dns_hijack")
        val passed = withContext(Dispatchers.IO) {
            try {
                // Resolve google.com via current DNS; if answer is unexpected, hijack detected
                val addr = java.net.InetAddress.getByName("google.com")
                val ip = addr.hostAddress ?: ""
                // Google's IPs are in 142.250.x.x, 172.217.x.x, 216.58.x.x ranges
                val isGoogle = ip.startsWith("142.250.") || ip.startsWith("172.217.") ||
                               ip.startsWith("216.58.") || ip.startsWith("142.251.") ||
                               ip.startsWith("74.125.")
                isGoogle
            } catch (e: Exception) { true }
        }
        updateCheck("dns_hijack", passed)
    }

    private fun setChecking(id: String) {
        _uiState.update { state ->
            state.copy(checks = state.checks.map {
                if (it.id == id) it.copy(isChecking = true) else it
            })
        }
    }

    private fun updateCheck(id: String, passed: Boolean) {
        _uiState.update { state ->
            state.copy(checks = state.checks.map {
                if (it.id == id) it.copy(passed = passed, isChecking = false) else it
            })
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
