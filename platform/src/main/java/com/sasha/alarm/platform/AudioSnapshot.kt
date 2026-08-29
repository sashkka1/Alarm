package com.sasha.alarm.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.sasha.alarm.core.LogValue

/**
 * Состояние звука в момент, когда тревога поднимается.
 *
 * Отвечает на единственный вопрос, но самый неприятный из возможных: **почему утром
 * было тихо**. Тишина вместо будильника — худший отказ приложения (P0 №7), и разбирать
 * её постфактум без этих четырёх чисел невозможно: системную громкость могли выкрутить
 * в ноль вечером, «Не беспокоить» мог остаться включённым с ночи, наушники — лежать
 * подключёнными на столе.
 *
 * Снимок делается **до** того, как мы поднимаем громкость сами: важно, каким состояние
 * было, а не каким мы его сделали.
 */
object AudioSnapshot {

    fun take(context: Context): Map<String, LogValue> {
        val out = LinkedHashMap<String, LogValue>()

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null) {
            runCatching {
                out["alarmVolume"] = LogValue.of(audio.getStreamVolume(AudioManager.STREAM_ALARM).toLong())
                out["maxVolume"] = LogValue.of(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).toLong())
            }
            runCatching {
                out["ringerMode"] = LogValue.of(
                    when (audio.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                        else -> "normal"
                    }
                )
            }
        }

        runCatching {
            val notifications = context.getSystemService(NotificationManager::class.java)
            val filter = notifications?.currentInterruptionFilter
            out["dnd"] = LogValue.of(
                filter != null &&
                    filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            )
        }

        runCatching { out["headphones"] = LogValue.of(AudioOutputs.headphonesConnected(context)) }

        return out
    }
}
