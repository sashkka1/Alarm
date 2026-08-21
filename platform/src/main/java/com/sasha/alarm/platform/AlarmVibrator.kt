package com.sasha.alarm.platform

import android.media.AudioAttributes
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

/**
 * Вибрация на время звонка. Повторяется, пока будильник не выключат.
 *
 * Вибрация помечена как **будильничная**: у системы для звонков, уведомлений и
 * будильников разные тумблеры, и без такой пометки наша вибрация попала бы под
 * общий выключатель уведомлений. С пометкой она подчиняется настройке будильника —
 * той же, что и у штатных будильников телефона.
 *
 * ⚠️ Полностью выключенную в системе вибрацию перебить нечем: API, который бы
 * заставил мотор работать вопреки настройке пользователя, в Android не существует.
 */
class AlarmVibrator(context: Context) {

    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    fun start() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) {
            Log.i(TAG, "вибромотора нет")
            return
        }
        val effect = VibrationEffect.createWaveform(PATTERN, REPEAT_FROM)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "вибрация не запустилась", e)
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "вибрация не остановилась", e)
        }
    }

    private companion object {
        const val TAG = "AlarmVibrator"

        /** пауза, толчок, пауза, толчок — и по кругу */
        val PATTERN = longArrayOf(0L, 600L, 500L, 600L, 1_200L)
        const val REPEAT_FROM = 0
    }
}
