package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Запуск проверочной тревоги с компьютера — пара к `alarm-off`.
 *
 * Нужен, чтобы проверять сценарии, где до телефона дотянуться некогда: поднял
 * тревогу командой, выключил телефон, включил обратно и смотришь, вернулась ли она.
 *
 * ⚠️ Тревогу не запускает напрямую, а **ставит будильник** через несколько секунд.
 * Иначе не работает: службе переднего плана запрещено стартовать из фонового
 * бродкаста, а вот у бродкаста от точного будильника такое право есть. Заодно
 * появляется фора, чтобы успеть заблокировать телефон.
 */
class TestTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val delay = intent.getIntExtra(EXTRA_DELAY_SECONDS, DEFAULT_DELAY_SECONDS)
            .coerceIn(1, 600)
        Log.i(TAG, "проверочная тревога через $delay с")
        AlarmController.scheduleTest(context, delay * 1000L)
    }

    companion object {
        const val EXTRA_DELAY_SECONDS = "seconds"

        /**
         * Почти сразу. Совсем без паузы нельзя: тревогу поднимает будильник, а не сам
         * бродкаст, и системе нужно мгновение, чтобы его отработать.
         */
        const val DEFAULT_DELAY_SECONDS = 3
        private const val TAG = "TestTriggerReceiver"
    }
}
