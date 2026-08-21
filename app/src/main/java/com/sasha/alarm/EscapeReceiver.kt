package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.DeviceOwner

/**
 * ПУТЬ К ОТСТУПЛЕНИЮ ЧЕРЕЗ КАБЕЛЬ.
 *
 * Один из двух выходов, оставленных сознательно. Снимает всё разом: жёсткую
 * блокировку, права владельца устройства, запрет на удаление и текущую тревогу.
 * После этого приложение — обычное, `adb uninstall com.sasha.alarm` работает.
 *
 * Вызывается с компьютера одной командой:
 * ```
 * adb shell am broadcast -n com.sasha.alarm/.EscapeReceiver
 * ```
 *
 * ⚠️ Никаких ключей и подтверждений здесь нет намеренно (решение владельца
 * 2026-08-14): кабель — запасной путь, и он обязан срабатывать мгновенно, без
 * возни. Подключённый к телефону компьютер и так означает полный доступ, так что
 * ключ ничего не защищал бы, а вот помешать в неудачный момент мог бы.
 */
class EscapeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Log.i(TAG, "снимаю блокировку по команде с кабеля")

        val released = DeviceOwner.release(app)

        AlarmStateStore(app).update { it.copy(run = null, foreignRingingSinceMillis = null) }
        AlarmScheduling.cancelGuard(app, GuardReceiver::class.java)
        app.stopService(Intent(app, AlarmService::class.java))
        app.sendBroadcast(Intent(AlarmActivity.ACTION_DISMISS).setPackage(app.packageName))

        Log.i(TAG, "готово, права владельца сняты: $released")
    }

    companion object {
        const val ACTION = "com.sasha.alarm.ESCAPE"
        private const val TAG = "EscapeReceiver"
    }
}
