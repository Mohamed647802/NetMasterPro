package com.netmaster.pro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.domain.repository.AppTheme
import com.netmaster.pro.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    // ─── Preference Keys ─────────────────────────────────────────────────────

    private object Keys {
        val ROUTER_IP = stringPreferencesKey("router_ip")
        val ROUTER_PORT = intPreferencesKey("router_port")
        val ROUTER_BRAND = stringPreferencesKey("router_brand")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val QUOTA_BUNDLE_GB = doublePreferencesKey("quota_bundle_gb")
        val QUOTA_RENEWAL_DAY = intPreferencesKey("quota_renewal_day")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SIGNAL_POLL_INTERVAL_S = intPreferencesKey("signal_poll_interval_s")
        val TRAFFIC_POLL_INTERVAL_S = intPreferencesKey("traffic_poll_interval_s")
    }

    // ─── Defaults ────────────────────────────────────────────────────────────

    private object Defaults {
        const val ROUTER_PORT = 80
        const val LANGUAGE = "en"
        val THEME = AppTheme.SYSTEM
        val BRAND = RouterBrand.UNKNOWN
        const val QUOTA_BUNDLE_GB = 0.0
        const val QUOTA_RENEWAL_DAY = 1
        const val NOTIFICATION_ENABLED = true
        const val BIOMETRIC_ENABLED = false
        const val SIGNAL_POLL_INTERVAL_S = 5
        const val TRAFFIC_POLL_INTERVAL_S = 10
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private val safePrefs: Flow<Preferences>
        get() = dataStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    private suspend fun <T> get(key: Preferences.Key<T>, default: T): T =
        safePrefs.map { it[key] ?: default }.first()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    // ─── Router ─────────────────────────────────────────────────────────────

    override suspend fun getRouterIp(): String? = safePrefs.map { it[Keys.ROUTER_IP] }.first()
    override suspend fun setRouterIp(ip: String) = set(Keys.ROUTER_IP, ip)

    override suspend fun getRouterPort(): Int = get(Keys.ROUTER_PORT, Defaults.ROUTER_PORT)
    override suspend fun setRouterPort(port: Int) = set(Keys.ROUTER_PORT, port)

    override suspend fun getRouterBrand(): RouterBrand {
        val name = get(Keys.ROUTER_BRAND, Defaults.BRAND.name)
        return RouterBrand.entries.firstOrNull { it.name == name } ?: RouterBrand.UNKNOWN
    }
    override suspend fun setRouterBrand(brand: RouterBrand) = set(Keys.ROUTER_BRAND, brand.name)

    // ─── Appearance ─────────────────────────────────────────────────────────

    override suspend fun getLanguage(): String = get(Keys.LANGUAGE, Defaults.LANGUAGE)
    override suspend fun setLanguage(languageCode: String) = set(Keys.LANGUAGE, languageCode)

    override suspend fun getTheme(): AppTheme {
        val name = get(Keys.THEME, Defaults.THEME.name)
        return AppTheme.entries.firstOrNull { it.name == name } ?: AppTheme.SYSTEM
    }
    override suspend fun setTheme(theme: AppTheme) = set(Keys.THEME, theme.name)

    // ─── Quota ──────────────────────────────────────────────────────────────

    override suspend fun getQuotaBundleGb(): Double = get(Keys.QUOTA_BUNDLE_GB, Defaults.QUOTA_BUNDLE_GB)
    override suspend fun setQuotaBundleGb(gb: Double) = set(Keys.QUOTA_BUNDLE_GB, gb)

    override suspend fun getQuotaRenewalDay(): Int = get(Keys.QUOTA_RENEWAL_DAY, Defaults.QUOTA_RENEWAL_DAY)
    override suspend fun setQuotaRenewalDay(day: Int) = set(Keys.QUOTA_RENEWAL_DAY, day)

    // ─── Notifications ───────────────────────────────────────────────────────

    override suspend fun isNotificationEnabled(): Boolean =
        get(Keys.NOTIFICATION_ENABLED, Defaults.NOTIFICATION_ENABLED)
    override suspend fun setNotificationEnabled(enabled: Boolean) =
        set(Keys.NOTIFICATION_ENABLED, enabled)

    // ─── Security ───────────────────────────────────────────────────────────

    override suspend fun isBiometricEnabled(): Boolean =
        get(Keys.BIOMETRIC_ENABLED, Defaults.BIOMETRIC_ENABLED)
    override suspend fun setBiometricEnabled(enabled: Boolean) =
        set(Keys.BIOMETRIC_ENABLED, enabled)

    // ─── Polling ────────────────────────────────────────────────────────────

    override suspend fun getSignalPollIntervalSeconds(): Int =
        get(Keys.SIGNAL_POLL_INTERVAL_S, Defaults.SIGNAL_POLL_INTERVAL_S)
    override suspend fun setSignalPollIntervalSeconds(seconds: Int) =
        set(Keys.SIGNAL_POLL_INTERVAL_S, seconds)

    override suspend fun getTrafficPollIntervalSeconds(): Int =
        get(Keys.TRAFFIC_POLL_INTERVAL_S, Defaults.TRAFFIC_POLL_INTERVAL_S)
    override suspend fun setTrafficPollIntervalSeconds(seconds: Int) =
        set(Keys.TRAFFIC_POLL_INTERVAL_S, seconds)

    // ─── Reactive Flows ─────────────────────────────────────────────────────

    override fun observeRouterIp(): Flow<String?> =
        safePrefs.map { it[Keys.ROUTER_IP] }

    override fun observeTheme(): Flow<AppTheme> =
        safePrefs.map { prefs ->
            val name = prefs[Keys.THEME] ?: AppTheme.SYSTEM.name
            AppTheme.entries.firstOrNull { it.name == name } ?: AppTheme.SYSTEM
        }

    override fun observeNotificationEnabled(): Flow<Boolean> =
        safePrefs.map { it[Keys.NOTIFICATION_ENABLED] ?: Defaults.NOTIFICATION_ENABLED }

    override fun observeBiometricEnabled(): Flow<Boolean> =
        safePrefs.map { it[Keys.BIOMETRIC_ENABLED] ?: Defaults.BIOMETRIC_ENABLED }

    // ─── Misc ───────────────────────────────────────────────────────────────

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
