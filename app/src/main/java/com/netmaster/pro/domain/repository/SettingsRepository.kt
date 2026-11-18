package com.netmaster.pro.domain.repository

import com.netmaster.pro.data.model.RouterBrand
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persistent app preferences.
 * Backed by Jetpack DataStore<Preferences>.
 */
interface SettingsRepository {

    // ─── Router ─────────────────────────────────────────────────────────────

    suspend fun getRouterIp(): String?
    suspend fun setRouterIp(ip: String)

    suspend fun getRouterPort(): Int
    suspend fun setRouterPort(port: Int)

    suspend fun getRouterBrand(): RouterBrand
    suspend fun setRouterBrand(brand: RouterBrand)

    // ─── Appearance ─────────────────────────────────────────────────────────

    suspend fun getLanguage(): String
    suspend fun setLanguage(languageCode: String)

    suspend fun getTheme(): AppTheme
    suspend fun setTheme(theme: AppTheme)

    // ─── Quota ──────────────────────────────────────────────────────────────

    suspend fun getQuotaBundleGb(): Double
    suspend fun setQuotaBundleGb(gb: Double)

    suspend fun getQuotaRenewalDay(): Int
    suspend fun setQuotaRenewalDay(day: Int)

    // ─── Notifications ───────────────────────────────────────────────────────

    suspend fun isNotificationEnabled(): Boolean
    suspend fun setNotificationEnabled(enabled: Boolean)

    // ─── Security ───────────────────────────────────────────────────────────

    suspend fun isBiometricEnabled(): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)

    // ─── Polling ────────────────────────────────────────────────────────────

    suspend fun getSignalPollIntervalSeconds(): Int
    suspend fun setSignalPollIntervalSeconds(seconds: Int)

    suspend fun getTrafficPollIntervalSeconds(): Int
    suspend fun setTrafficPollIntervalSeconds(seconds: Int)

    // ─── Reactive Flows ─────────────────────────────────────────────────────

    fun observeRouterIp(): Flow<String?>
    fun observeTheme(): Flow<AppTheme>
    fun observeNotificationEnabled(): Flow<Boolean>
    fun observeBiometricEnabled(): Flow<Boolean>

    // ─── Misc ───────────────────────────────────────────────────────────────

    suspend fun clearAll()
}

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}
