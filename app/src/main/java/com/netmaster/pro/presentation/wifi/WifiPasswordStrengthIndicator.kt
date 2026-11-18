package com.netmaster.pro.presentation.wifi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netmaster.pro.R

enum class PasswordStrength { WEAK, FAIR, GOOD, STRONG }

@Composable
fun WifiPasswordStrengthIndicator(
    password: String,
    modifier: Modifier = Modifier
) {
    val strength = remember(password) {
        when {
            password.length < 8 -> PasswordStrength.WEAK
            password.length < 12 && !password.any { it.isDigit() } -> PasswordStrength.FAIR
            password.length >= 12 && password.any { it.isUpperCase() } && password.any { it.isDigit() } -> PasswordStrength.STRONG
            else -> PasswordStrength.GOOD
        }
    }

    val progress = when (strength) {
        PasswordStrength.WEAK -> 0.25f
        PasswordStrength.FAIR -> 0.5f
        PasswordStrength.GOOD -> 0.75f
        PasswordStrength.STRONG -> 1f
    }

    val targetColor = when (strength) {
        PasswordStrength.WEAK -> Color(0xFFE53935)
        PasswordStrength.FAIR -> Color(0xFFFB8C00)
        PasswordStrength.GOOD -> Color(0xFFFDD835)
        PasswordStrength.STRONG -> Color(0xFF43A047)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "password_strength_color"
    )

    val label = when (strength) {
        PasswordStrength.WEAK -> stringResource(R.string.wifi_password_weak)
        PasswordStrength.FAIR -> stringResource(R.string.wifi_password_fair)
        PasswordStrength.GOOD -> stringResource(R.string.wifi_password_good)
        PasswordStrength.STRONG -> stringResource(R.string.wifi_password_strong)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = animatedColor
        )
    }
}
