package com.sasha.alarm

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sasha.alarm.core.DeskClockAlarm
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.ForeignAlarmRule
import com.sasha.alarm.core.LogValue
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.EventLog
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
 *
 * **Второй источник — стандартный будильник, часы Xiaomi** (2026-09-30, [DeskClockAlarm]).
 * Та же схема, но гасим не жестом, а кнопкой «Стоп» его же уведомления: зазвонил — нажали,
 * часы сняли уведомление — поднимаем свой экран.
 *
 * ⚠️ Имя класса не менять: доступ к уведомлениям выдан владельцем именно этому компоненту,
 * и переименование молча отбирает его — зацеп умрёт без единой ошибки.
 */
class SleepCycleListener : NotificationListenerService() {

    private var audioWatcher: ForeignAudioWatcher? = null

    /** Ключ уведомления, которым часы звонят сейчас; null — не звонят. */
    private var deskClockRingKey: String? = null

    /** Когда звонок часов последний раз кончился — чтобы узнать хвост его звука. */
    private var deskClockRingEndedAt: Long? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == DeskClockAlarm.PACKAGE) {
            onDeskClockPosted(sbn)
            return
        }
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
                if (updated != null) {
                    EventLog(applicationContext).write(
                        EventType.FOREIGN_RING,
                        "source" to LogValue.of("notification"),
                    )
                }
            }
            if (updated != null) requestForeignDismiss(state.challengePassedAtMillis, now)
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
    private fun requestForeignDismiss(challengePassedAt: Long?, nowMillis: Long) {
        // Наша тревога уже идёт — значит чужой уже погашен, смахивать нечего.
        if (AlarmRuntime.alarmActive) return
        // Испытание пройдено недавно — второй будильник звонит сам (владелец, 2026-09-30).
        if (!ForeignAlarmRule.silencesForeign(ourAlarmActive = false, challengePassedAt, nowMillis)) {
            Log.i(TAG, "испытание пройдено недавно — Sleep Cycle звонит сам")
            return
        }
        if (!AlarmRuntime.foreignDismissRequested) {
            Log.i(TAG, "прошу смахнуть Sleep Cycle")
        }
        AlarmRuntime.foreignDismissRequested = true
    }

    /**
     * Часы Xiaomi прислали уведомление. Звонок будильника — жмём его «Стоп» сразу
     * (решение владельца 2026-09-30); таймер и прочее пропускаем.
     */
    private fun onDeskClockPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val foreground = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        if (!DeskClockAlarm.isRinging(notification.channelId, foreground)) return

        // Ключ запоминаем и при выключенном тумблере: по нему узнаём звук часов.
        val firstPost = deskClockRingKey == null
        deskClockRingKey = sbn.key
        val state = AlarmStateStore(applicationContext).read()
        if (!state.masterEnabled) return

        if (firstPost) {
            Log.i(TAG, "стандартный будильник зазвонил")
            EventLog(applicationContext).write(
                EventType.FOREIGN_RING,
                "source" to LogValue.of(SOURCE_DESK_CLOCK),
            )
        }
        val now = AndroidClock.nowMillis()
        if (!ForeignAlarmRule.silencesForeign(AlarmRuntime.alarmActive, state.challengePassedAtMillis, now)) {
            // Испытание пройдено недавно — второй будильник звонит сам (владелец, 2026-09-30).
            if (firstPost) Log.i(TAG, "испытание пройдено недавно — часы звонят сами")
            return
        }
        pressDeskClockStop(notification)
    }

    /**
     * Нажимаем «Стоп» в уведомлении часов — то же, что владелец пальцем.
     *
     * Кнопки нет или она уже недействительна — ничего страшного: выключит владелец
     * руками, и наш экран поднимется от снятия уведомления тем же путём.
     */
    private fun pressDeskClockStop(notification: Notification) {
        val actions = notification.actions.orEmpty()
        val index = DeskClockAlarm.stopActionIndex(actions.map { it.title?.toString() })
        val intent = index?.let { actions[it].actionIntent }
        if (intent == null) {
            Log.w(TAG, "у звонка часов нет кнопки «Стоп» — ждём, пока выключат руками")
            return
        }
        try {
            intent.send()
            Log.i(TAG, "нажал «Стоп» стандартного будильника")
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "кнопка «Стоп» часов уже недействительна", e)
        }
    }

    /** Звонок часов кончился — кто бы его ни выключил, поднимаем свой экран. */
    private fun onDeskClockRemoved(sbn: StatusBarNotification) {
        if (sbn.key != deskClockRingKey) return
        val now = AndroidClock.nowMillis()
        deskClockRingKey = null
        deskClockRingEndedAt = now

        val store = AlarmStateStore(applicationContext)
        if (!store.read().masterEnabled) return
        EventLog(applicationContext).write(
            EventType.FOREIGN_DISMISSED,
            "source" to LogValue.of(SOURCE_DESK_CLOCK),
            "ringingMs" to LogValue.of((now - sbn.postTime).coerceAtLeast(0L)),
        )
        // Отметку «чужой звонил» мог поставить звук часов, пока мы его не узнали.
        // Оставленная, она подняла бы второй экран, когда владелец остановит
        // отслеживание Sleep Cycle в ближайший час.
        store.update { it.copy(foreignRingingSinceMillis = null) }

        Log.i(TAG, "стандартный будильник выключен")
        AlarmController.onForeignAlarmDismissed(applicationContext, "зацеп за стандартный будильник")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == DeskClockAlarm.PACKAGE) {
            onDeskClockRemoved(sbn)
            return
        }
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
        EventLog(applicationContext).write(
            EventType.FOREIGN_DISMISSED,
            // Сколько он звонил до того, как погас — по этому числу видно, смахнула ли
            // его наша служба жестов или владелец дотянулся сам.
            "ringingMs" to LogValue.of(since?.let { (now - it).coerceAtLeast(0L) } ?: 0L),
        )
        store.update { it.copy(foreignRingingSinceMillis = null) }
        AlarmController.onForeignAlarmDismissed(applicationContext, "зацеп за Sleep Cycle")
    }

    override fun onListenerConnected() {
        Log.i(TAG, "слушатель уведомлений подключён")
        AlarmRuntime.hookConnected = true
        // Зацеп ожил сам (или его поднял сторож) — закрыть отрезок «не работает» сразу,
        // не дожидаясь следующей проверки: уведомление о поломке не должно висеть зря.
        HookWatch.onConnected(applicationContext)
        audioWatcher = ForeignAudioWatcher(applicationContext, ::onAlarmAudio).also { it.start() }
        // Слушатель мог переподключиться посреди звонка часов — о висящем уведомлении
        // система второй раз не сообщит, поэтому смотрим на него сами.
        try {
            activeNotifications.orEmpty()
                .filter { it.packageName == DeskClockAlarm.PACKAGE }
                .forEach(::onDeskClockPosted)
        } catch (e: SecurityException) {
            Log.w(TAG, "не удалось прочитать висящие уведомления", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "слушатель уведомлений отключён")
        AlarmRuntime.hookConnected = false
        audioWatcher?.stop()
        audioWatcher = null
    }

    override fun onDestroy() {
        AlarmRuntime.hookConnected = false
        audioWatcher?.stop()
        audioWatcher = null
        super.onDestroy()
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
        // Это звонят часы: их ведёт своё уведомление, а Sleep Cycle тут ни при чём.
        if (DeskClockAlarm.isItsAudio(deskClockRingKey != null, deskClockRingEndedAt, AndroidClock.nowMillis())) return

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
                    EventLog(applicationContext).write(
                        EventType.FOREIGN_RING,
                        "source" to LogValue.of("audio"),
                    )
                }
                if (updated != null) requestForeignDismiss(state.challengePassedAtMillis, now)
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

        /** Источник в журнале для звонка стандартного будильника. */
        const val SOURCE_DESK_CLOCK = "deskclock"

        /** Кусок текста, в который Sleep Cycle переписывает уведомление на время звонка. */
        const val RING_MARKER = "Выключить будильник"
    }
}
