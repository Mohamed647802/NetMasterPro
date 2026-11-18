package com.netmaster.pro.data.local.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor() {

    companion object {
        private const val TAG = "BiometricAuth"
        private const val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    /**
     * Returns true if the device supports and has enrolled biometric / device credential
     * authentication that meets our security requirements.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "Device has no biometric hardware")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware is currently unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometrics enrolled on this device")
                false
            }
            else -> false
        }
    }

    /**
     * Presents the system biometric prompt. The activity must be a [FragmentActivity].
     *
     * @param activity The host activity (must be in resumed state).
     * @param title    Title shown in the biometric dialog.
     * @param subtitle Subtitle shown in the biometric dialog.
     * @param onSuccess Called on the main thread when authentication succeeds.
     * @param onError   Called on the main thread when authentication fails or is cancelled.
     *                  Receives (errorCode, errString) for display to the user.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Confirm your identity to access router settings",
        onSuccess: () -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Biometric authentication error $errorCode: $errString")
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed (bad biometric)")
                // onAuthenticationFailed means the biometric didn't match — the prompt
                // handles retries automatically, so we don't invoke onError here.
            }
        }

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
