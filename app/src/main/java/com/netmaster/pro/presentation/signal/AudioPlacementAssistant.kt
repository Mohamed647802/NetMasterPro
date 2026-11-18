package com.netmaster.pro.presentation.signal

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlacementAssistant @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var toneGenerator: ToneGenerator? = null
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var isRunning = false

    /**
     * Maps RSRP (-140 to -44 dBm) linearly to a volume (10–100) and tone frequency.
     * Better signal = higher volume and shorter tone interval (more urgent beeping).
     */
    fun start(rsrp: Int, sinr: Float) {
        if (isRunning) return
        isRunning = true
        playToneForSignal(rsrp, sinr)
    }

    fun stop() {
        isRunning = false
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
        vibrator.cancel()
    }

    fun updateSignal(rsrp: Int, sinr: Float) {
        if (!isRunning) return
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
        playToneForSignal(rsrp, sinr)
    }

    private fun playToneForSignal(rsrp: Int, sinr: Float) {
        // Clamp rsrp to range -140..-44
        val clampedRsrp = rsrp.coerceIn(-140, -44)
        // Map -140..-44 → 0..1
        val normalized = (clampedRsrp + 140).toFloat() / 96f
        // Map to volume 10..100
        val volume = (10 + (normalized * 90)).toInt().coerceIn(10, 100)

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
            // Use DTMF tones based on signal quality
            val toneType = when {
                rsrp >= -80 -> ToneGenerator.TONE_DTMF_1   // Excellent
                rsrp >= -90 -> ToneGenerator.TONE_DTMF_2   // Good
                rsrp >= -100 -> ToneGenerator.TONE_DTMF_3  // Fair
                else -> ToneGenerator.TONE_DTMF_0           // Poor
            }
            // Duration inversely proportional to quality (better = shorter beep = faster feedback)
            val duration = when {
                rsrp >= -80 -> 150
                rsrp >= -90 -> 250
                rsrp >= -100 -> 400
                else -> 600
            }
            toneGenerator?.startTone(toneType, duration)
        } catch (e: Exception) {
            // ToneGenerator can fail if audio focus is denied — silently ignore
        }

        // Vibration: longer/stronger pattern = stronger signal
        val vibrationMs = when {
            rsrp >= -80 -> 100L
            rsrp >= -90 -> 200L
            rsrp >= -100 -> 350L
            else -> 500L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    vibrationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(vibrationMs)
        }
    }
}
