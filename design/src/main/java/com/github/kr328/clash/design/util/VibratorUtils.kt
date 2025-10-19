package com.github.kr328.clash.design.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibratorUtils {
    fun performHapticClick(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }

                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun performHapticHeavyClick(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                }

                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}

fun Context.performHapticClick() {
    VibratorUtils.performHapticClick(this)
}

fun Context.performHapticHeavyClick() {
    VibratorUtils.performHapticHeavyClick(this)
}