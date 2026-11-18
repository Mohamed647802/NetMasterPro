package com.netmaster.pro.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.netmaster.pro.data.local.security.BiometricAuthManager
import com.netmaster.pro.navigation.NetMasterNavHost
import com.netmaster.pro.ui.theme.AppTheme
import com.netmaster.pro.ui.theme.NetMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled gracefully at feature level */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        requestRuntimePermissions()

        setContent {
            NetMasterTheme(appTheme = AppTheme.SYSTEM) {
                val navController = rememberNavController()
                NetMasterNavHost(navController)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            add(Manifest.permission.CAMERA)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
