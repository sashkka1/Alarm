package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner

/**
 * Загрузка телефона, обновление приложения, перевод часов.
 *
 * ⚠️ **Выключение телефона больше НЕ является выходом** (решение владельца
 * 2026-08-14). Раньше тревога при загрузке стиралась; теперь состояние лежит на
 * диске, и если будильник не был выключен по-настоящему, экран поднимается снова —
 * сразу после того, как телефон разблокировали.
 *
 * ⚠️ Отсчёт бэкапа при этом **начинается заново с полного таймаута**: иначе
 * перезагрузками можно было бы выждать дедлайн, пока телефон выключен и тихо.
 * Единственный оставшийся путь к отступлению — команда `alarm-off` по кабелю.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "восстановление после ${intent.action}")
        val app = context.applicationContext

        DeviceOwner.applyPermanentPolicies(app)

        val state = AlarmStateStore(app).read()
        if (state.run == null) {
            DeviceOwner.releaseHardLock(app)
            return
        }

        // Просроченность здесь не проверяем намеренно: отсчёт начнётся заново с полного
        // таймаута, иначе перезагрузками можно было бы выждать дедлайн в тишине.
        Log.i(TAG, "тревога не была выключена — поднимаю заново с полным таймаутом")
        AlarmScheduling.scheduleResume(
            context = app,
            triggerAtMillis = AndroidClock.nowMillis() + state.resumeDelaySeconds * 1_000L,
            receiver = AlarmReceiver::class.java,
        )
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
