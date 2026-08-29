package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Крайний срок записи ночи — девять утра.
 *
 * Обычно ночь заканчивает звонок будильника, и до этого приёмника дело не доходит. Он
 * существует ровно для случаев, когда звонка не было вовсе: отслеживание сна не
 * запустилось, будильник выключен, запись нажали днём.
 *
 * ⚠️ Без него забытая запись слушала бы комнату вторые сутки и съела бы память телефона
 * гигабайтами — при том, что владелец о ней уже не помнит.
 */
class NightDeadlineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "крайний срок записи")
        NightRecordingService.stop(context.applicationContext, "deadline")
    }

    private companion object {
        const val TAG = "NightDeadline"
    }
}
