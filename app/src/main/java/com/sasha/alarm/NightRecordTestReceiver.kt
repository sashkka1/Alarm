package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Запуск и остановка записи ночи по кабелю — чтобы её можно было проверить.
 *
 * Пара к `TestTriggerReceiver`: то же назначение и та же причина. Кнопка на экране
 * нажимается только пальцем, а `adb shell input` Xiaomi не отдаёт (`INJECT_EVENTS` за
 * Mi-аккаунтом). Без этого приёмника проверить, что микрофон действительно пишет звук в
 * файл, нельзя вовсе — оставалось бы верить на слово.
 *
 * ⚠️ **Приёмник открыт наружу, но закрыт разрешением `android.permission.DUMP`.** Оно есть
 * у оболочки adb и **нет** у обычных приложений, поэтому включить микрофон чужой программе
 * этот путь не даёт. Открывать запись звука кому попало нельзя ни ради какого удобства.
 *
 * ```
 * adb shell am broadcast -a com.sasha.alarm.NIGHT_RECORD -n com.sasha.alarm/.NightRecordTestReceiver
 * adb shell am broadcast -a com.sasha.alarm.NIGHT_RECORD --es action stop -n com.sasha.alarm/.NightRecordTestReceiver
 * ```
 */
class NightRecordTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val stop = intent?.getStringExtra(EXTRA_ACTION) == "stop"
        Log.i(TAG, if (stop) "остановить запись (кабель)" else "начать запись (кабель)")
        if (stop) {
            NightRecordingService.stop(app, "manual")
        } else {
            NightRecordingService.start(app)
        }
    }

    private companion object {
        const val TAG = "NightRecordTest"
        const val EXTRA_ACTION = "action"
    }
}
