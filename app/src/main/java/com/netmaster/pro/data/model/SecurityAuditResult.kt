package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurityAuditResult(
    /** Overall security score from 0 (worst) to 100 (best) */
    val score: Int,
    val risks: List<SecurityRisk>
) {
    val criticalCount: Int get() = risks.count { it.severity == RiskSeverity.CRITICAL }
    val highCount: Int get() = risks.count { it.severity == RiskSeverity.HIGH }
    val unfixedCount: Int get() = risks.count { !it.isFixed }
}

@Serializable
data class SecurityRisk(
    val title: String,
    val description: String,
    val severity: RiskSeverity,
    val isFixed: Boolean = false
)

enum class RiskSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
