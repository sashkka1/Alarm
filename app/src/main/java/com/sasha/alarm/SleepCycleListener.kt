package com.sasha.alarm

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sasha.alarm.core.ForeignAlarmRule
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.ForeignAudioWatcher

/**
 * Зацеп за Sleep Cycle.
 *
 * Ловит **звонок** и гасит его сам, не дожидаясь владельца (решение владельца
 * 2026-08-19: вручную выключать Sleep Cycle каждое утро неудобно). Как только услышали
 * звонок — просим [AlarmGuardAccessibilityService] смахнуть «Стоп» за владельца
 * (единственный доступный с телефона способ: `force-stop` обычному приложению не дан,
 * ввод по кабелю Xiaomi блокирует, а жест служба выполняет системно). Sleep Cycle
 * завершает сессию, снимает уведомление — и это снятие поднимает наш экран прежним путём.
 *
 * Что подтверждено дампами телефона владельца (2026-08-14):
 *  - на всё время отслеживания сна висит одно уведомление `id=105`;
 *  - в момент звонка оно **обновляется**, текст становится «Выключить будильник…»;
 *  - при выключении Sleep Cycle завершает сессию и **снимает уведомление целиком**.
 *
 * Снятие уведомления после звонка — наш сигнал поднять экран (сработает и если владелец
 * выключит Sleep Cycle раньше нашего жеста). Решение принимает [ForeignAlarmRule] в ядре.
 */
class SleepCycleListener : NotificationListenerService() {

    private var audioWatcher: ForeignAudioWatcher? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != TARGET_PACKAGE) return

        val ringing = looksLikeRinging(sbn)
        val store = AlarmStateStore(applicationContext)
        val now = AndroidClock.nowMillis()

        store.update { state ->
            if (!state.masterEnabled) return@update state
            val updated = ForeignAlarmRule.onNotification(
                ringing = ringing,
                nowMillis = now,
                since = state.foreignRingingSinceMillis,
            )
            if (updated != state.foreignRingingSinceMillis) {
                Log.i(TAG, if (updated != null) "Sleep Cycle зазвонил" else "Sleep Cycle больше не звонит")
            }
            if (updated != null) requestForeignDismiss()
            state.copy(foreignRingingSinceMillis = updated)
        }
    }

    /**
     * Просим службу спецвозможностей смахнуть «Стоп» Sleep Cycle за владельца.
     *
     * Раньше наш экран поднимался только ПОСЛЕ того, как владелец выключал Sleep Cycle
     * сам. Теперь мы делаем это выключение сами, как только услышали звонок: служба
     * смахивает его экран, Sleep Cycle завершает сессию и снимает уведомление — а это
     * снятие и поднимает наш экран прежним путём ([onNotificationRemoved]).
     */
    private fun requestForeignDismiss() {
        // Наша тревога уже идёт — значит чужой уже погашен, смахивать нечего.
        if (AlarmRuntime.alarmActive) return
        if (!AlarmRuntime.foreignDismissRequested) {
            Log.i(TAG, "прошу смахнуть Sleep Cycle")
        }
        AlarmRuntime.foreignDismissRequested = true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != TARGET_PACKAGE) return

        val store = AlarmStateStore(applicationContext)
        val now = AndroidClock.nowMillis()
        val state = store.read()
        if (!state.masterEnabled) return
        val since = state.foreignRingingSinceMillis
        // Звонок кончился — смахивать больше нечего, гасим просьбу к службе жестов.
        AlarmRuntime.foreignDismissRequested = false

        if (!ForeignAlarmRule.isDismissal(since, now)) {
            // Обычная остановка отслеживания сна: будильник не звонил, будить не за чем.
            Log.i(TAG, "уведомление Sleep Cycle пропало, но звонка не было — пропускаю")
            return
        }

        Log.i(TAG, "Sleep Cycle выключен владельцем — поднимаю наш экран")
        store.update { it.copy(foreignRingingSinceMillis = null) }
        AlarmController.onForeignAlarmDismissed(applicationContext)
    }

    override fun onListenerConnected() {
        Log.i(TAG, "слушатель уведомлений подключён")
        audioWatcher = ForeignAudioWatcher(applicationContext, ::onAlarmAudio).also { it.start() }
    }

    override fun onListenerDisconnected() {
        audioWatcher?.stop()
        audioWatcher = null
    }

    /**
     * Звук по каналу будильника — второй признак того, что чужой будильник звонит,
     * не зависящий от текста уведомления.
     *
     * Только «зазвонил»: замолкший звук выключением не считаем — Sleep Cycle
     * повторяет цикл каждые пять минут и между повторами тоже молчит.
     */
    private fun onAlarmAudio(playing: Boolean) {
        if (!playing) return

        val store = AlarmStateStore(applicationContext)
        val now = AndroidClock.nowMillis()
        store.update { state ->
            if (!state.masterEnabled || AlarmRuntime.alarmActive) {
                // Зацеп выключен, либо это играет наш собственный будильник, а не чужой.
                // Признак «наш звонит» берём из памяти, а не с диска: незакрытый `run`
                // на диске отключал бы зацеп молча (баг 2026-08-19).
                state
            } else {
                val updated = ForeignAlarmRule.onNotification(true, now, state.foreignRingingSinceMillis)
                if (updated != state.foreignRingingSinceMillis) {
                    Log.i(TAG, "чужой будильник зазвонил (услышал по звуку)")
                }
                if (updated != null) requestForeignDismiss()
                state.copy(foreignRingingSinceMillis = updated)
            }
        }
    }

    /**
     * Текст уведомления в момент звонка — «Выключить будильник Sleep Cycle.».
     * Смотрим и основной текст, и бегущую строку: приложение кладёт его в оба поля.
     */
    private fun looksLikeRinging(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val ticker = sbn.notification?.tickerText?.toString().orEmpty()
        return text.contains(RING_MARKER, ignoreCase = true) ||
            ticker.contains(RING_MARKER, ignoreCase = true)
    }

    private companion object {
        const val TAG = "SleepCycleListener"
        const val TARGET_PACKAGE = "com.northcube.sleepcycle"

        /** Кусок текста, в который Sleep Cycle переписывает уведомление на время звонка. */
        const val RING_MARKER = "Выключить будильник"
    }
}
