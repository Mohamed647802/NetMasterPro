package com.netmaster.pro.data.local.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EncryptedPrefs"
        private const val PREFS_FILE = "netmaster_secure_prefs"
        private const val KEY_USERNAME_SUFFIX = "_username"
        private const val KEY_PASSWORD_SUFFIX = "_password"
    }

    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to null", e)
            null
        }
    }

    /**
     * Saves router admin credentials for a specific IP address.
     * Credentials are stored encrypted on-device.
     */
    fun saveCredentials(ip: String, username: String, password: String) {
        try {
            sharedPreferences?.edit()?.apply {
                putString(sanitizeKey(ip) + KEY_USERNAME_SUFFIX, username)
                putString(sanitizeKey(ip) + KEY_PASSWORD_SUFFIX, password)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save credentials for $ip", e)
        }
    }

    /**
     * Retrieves stored credentials for a specific IP address.
     * Returns null if no credentials are stored or decryption fails.
     */
    fun getCredentials(ip: String): Pair<String, String>? {
        return try {
            val prefs = sharedPreferences ?: return null
            val key = sanitizeKey(ip)
            val username = prefs.getString(key + KEY_USERNAME_SUFFIX, null) ?: return null
            val password = prefs.getString(key + KEY_PASSWORD_SUFFIX, null) ?: return null
            Pair(username, password)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get credentials for $ip", e)
            null
        }
    }

    /**
     * Removes stored credentials for a specific IP address.
     */
    fun clearCredentials(ip: String) {
        try {
            val key = sanitizeKey(ip)
            sharedPreferences?.edit()?.apply {
                remove(key + KEY_USERNAME_SUFFIX)
                remove(key + KEY_PASSWORD_SUFFIX)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credentials for $ip", e)
        }
    }

    /**
     * Clears all stored credentials.
     */
    fun clearAll() {
        try {
            sharedPreferences?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all credentials", e)
        }
    }

    /** Sanitizes an IP address string to be a safe SharedPreferences key */
    private fun sanitizeKey(ip: String): String = ip.replace('.', '_').replace(':', '_')
}
