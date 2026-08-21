package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.AlarmScheduling

/**
 * Точка входа для будильников системы: отладочный показ экрана и подъём тревоги
 * после перезагрузки телефона.
 *
 * Оба идут через `AlarmManager`, а не напрямую, потому что только у бродкаста от
 * точного будильника есть право запустить службу переднего плана из фона.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(AlarmScheduling.EXTRA_RESUME, false)) {
            Log.i(TAG, "подъём тревоги после перезагрузки")
            AlarmController.onResumeAfterBoot(context)
            return
        }
        Log.i(TAG, "тестовый показ экрана")
        AlarmController.onTestFired(context)
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
