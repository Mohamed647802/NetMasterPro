package com.netmaster.pro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

// ── App theme modes ──────────────────────────────────────────────────────────
enum class AppTheme { SYSTEM, LIGHT, DARK, NEON_CYBER }

val LocalAppTheme = staticCompositionLocalOf { AppTheme.SYSTEM }

// ── Color schemes ─────────────────────────────────────────────────────────────
private val NeonCyberDarkScheme = darkColorScheme(
    primary            = NeonCyberPrimary,
    onPrimary          = NeonCyberOnPrimary,
    primaryContainer   = NeonCyberPrimaryContainer,
    secondary          = NeonCyberSecondary,
    background         = NeonCyberBackground,
    surface            = NeonCyberSurface,
    surfaceVariant     = NeonCyberSurfaceVariant,
    onBackground       = NeonCyberOnBackground,
    onSurface          = NeonCyberOnSurface,
    error              = NeonCyberError,
    tertiary           = NeonCyberTertiary
)

private val CyberLightScheme = lightColorScheme(
    primary            = CyberLightPrimary,
    onPrimary          = CyberLightOnPrimary,
    primaryContainer   = CyberLightPrimaryContainer,
    secondary          = CyberLightSecondary,
    background         = CyberLightBackground,
    surface            = CyberLightSurface,
    surfaceVariant     = CyberLightSurfaceVariant,
    onBackground       = CyberLightOnBackground,
    onSurface          = CyberLightOnSurface,
    error              = CyberLightError,
    tertiary           = CyberLightTertiary
)

private val CyberDarkScheme = darkColorScheme(
    primary            = CyberDarkPrimary,
    onPrimary          = CyberDarkOnPrimary,
    primaryContainer   = CyberDarkPrimaryContainer,
    secondary          = CyberDarkSecondary,
    background         = CyberDarkBackground,
    surface            = CyberDarkSurface,
    surfaceVariant     = CyberDarkSurfaceVariant,
    onBackground       = CyberDarkOnBackground,
    onSurface          = CyberDarkOnSurface,
    error              = CyberDarkError,
    tertiary           = CyberDarkTertiary
)

@Composable
fun NetMasterTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (appTheme) {
        AppTheme.NEON_CYBER -> NeonCyberDarkScheme
        AppTheme.LIGHT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            CyberLightScheme
        }
        AppTheme.DARK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            CyberDarkScheme
        }
        AppTheme.SYSTEM -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && systemDark ->
                dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !systemDark ->
                dynamicLightColorScheme(context)
            systemDark -> CyberDarkScheme
            else -> CyberLightScheme
        }
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = NetMasterTypography,
            shapes      = NetMasterShapes,
            content     = content
        )
    }
}
