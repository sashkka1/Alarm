package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * Проверка зацепа по таймеру — раз в 15 минут ([HookWatch]).
 *
 * ⚠️ Открыт наружу, но закрыт разрешением `android.permission.DUMP` — как
 * `StepsDumpReceiver`: оно есть у оболочки adb и нет у обычных приложений. Так
 * проверку можно дёрнуть по кабелю, не дожидаясь таймера:
 * `adb shell am broadcast -n com.sasha.alarm/.HookCheckReceiver`.
 */
class HookCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // 🔴 Следующая проверка ставится первой строкой: путь назад кладётся ДО работы.
        HookWatch.schedule(app)

        // Переподключение ждёт до восьми секунд — не на главном потоке и не без goAsync.
        // ⚠️ Простой поток, а не пул: пул на каждую проверку раз в 15 минут оставлял бы
        // по висящему потоку, и за сутки их набиралась бы сотня.
        val pending = goAsync()
        thread(name = "hook-check") {
            try {
                HookWatch.run(app)
            } catch (e: Exception) {
                Log.w(TAG, "проверка зацепа не прошла", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "HookCheckReceiver"
    }
}
