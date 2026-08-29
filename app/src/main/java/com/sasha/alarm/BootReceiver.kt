package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.LogEvent
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner
import com.sasha.alarm.platform.EventLog
import java.util.TimeZone

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

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // ⚠️ Время берём не «сейчас», а настоящий момент загрузки: HyperOS
                // доставляет BOOT_COMPLETED повторно — проверено на телефоне 2026-08-25,
                // где на одну загрузку пришло две записи с разницей в семь минут.
                // С честным временем повторы дают побайтово одинаковую строку, и
                // приёмник на компьютере отбрасывает их сам, без особого правила.
                val bootedAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
                EventLog(app).write(
                    LogEvent(
                        at = bootedAt,
                        tzOffsetMinutes = TimeZone.getDefault().getOffset(bootedAt) / 60_000,
                        type = EventType.PHONE_BOOT,
                    )
                )
            }

            Intent.ACTION_SHUTDOWN ->
                // Выключение телефона перестало быть выходом (см. выше), но знать о нём
                // надо: без этой отметки ночь с выключенным телефоном выглядит в журнале
                // просто как дыра.
                EventLog(app).write(EventType.PHONE_SHUTDOWN)
        }

        // Выключение телефона расписание не трогает: восстанавливать нечего, мы уходим.
        if (intent.action == Intent.ACTION_SHUTDOWN) return

        DeviceOwner.applyPermanentPolicies(app)

        // Таймеры не переживают перезагрузку и обновление приложения — ежедневную
        // передачу журнала надо ставить заново вместе со всем остальным.
        SyncReceiver.schedule(app)

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
