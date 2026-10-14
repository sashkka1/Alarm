package com.sasha.alarm

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.FailSafe
import com.sasha.alarm.core.ForeignAlarmRule
import com.sasha.alarm.core.LogValue
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AudioSnapshot
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner
import com.sasha.alarm.platform.EventLog

/**
 * Композиционный корень приложения: связывает правила из `:core` с адаптерами `:platform`.
 *
 * Сам решений не принимает — все они в ядре. Здесь только «кого позвать и в каком порядке».
 */
object AlarmController {

    private const val TAG = "AlarmController"

    fun store(context: Context) = AlarmStateStore(context.applicationContext)

    fun state(context: Context): AlarmState = store(context).read()

    /**
     * Тревога начинается.
     *
     * Порядок важен: сторож ставится ПЕРЕД показом экрана (P0 №5) — если что-то
     * упадёт дальше по цепочке, снятие блока уже запланировано.
     */
    private fun startRun(context: Context, reason: String, source: String, preview: Boolean) {
        val app = context.applicationContext
        val now = AndroidClock.nowMillis()
        val state = store(app).update {
            it.copy(run = FailSafe.runFor(now, it.failSafeMinutes, preview))
        }
        val run = state.run ?: return

        // ⚠️ Журнал пишется здесь, а не в сервисе. Сервис на одну тревогу стартует
        // дважды (его поднимают и мы, и полноэкранное уведомление), и в журнал попадали
        // два подъёма на одно утро — видно на телефоне 2026-08-25. Здесь тревога
        // рождается ровно один раз.
        val log = EventLog(app)
        log.write(
            EventType.ALARM_SHOWN,
            "source" to LogValue.of(source),
            "challenge" to LogValue.of(state.challenge.name.lowercase()),
            "preview" to LogValue.of(preview),
        )
        // Снимок звука — **до** того, как мы сами поднимем громкость: важно, каким
        // состояние было. Без этих чисел утро «будильник не сработал» не разобрать.
        log.write(EventType.AUDIO_STATE, AudioSnapshot.take(app))

        AlarmScheduling.scheduleGuard(
            context = app,
            triggerAtMillis = run.deadlineMillis,
            timeoutMillis = FailSafe.timeoutMillis(state.failSafeMinutes),
            receiver = GuardReceiver::class.java,
        )
        Log.i(TAG, "тревога начата ($reason), дедлайн ${run.deadlineMillis}")

        try {
            app.startForegroundService(Intent(app, AlarmService::class.java))
        } catch (e: Exception) {
            // Тишина вместо будильника — худший исход, поэтому ошибку видно в логе,
            // а сторож всё равно снимет состояние в срок.
            Log.e(TAG, "не удалось запустить сервис тревоги", e)
        }
    }

    /**
     * Чужой будильник выключен — поднимаем свой экран.
     *
     * @param reason чей: Sleep Cycle или стандартный будильник, для лога.
     */
    fun onForeignAlarmDismissed(context: Context, reason: String) {
        val state = state(context)
        if (!state.masterEnabled) {
            Log.i(TAG, "приложение выключено общим тумблером — тревогу не поднимаю")
            return
        }
        // Один наш экран на утро (владелец, 2026-09-30): идёт наша тревога или испытание
        // пройдено меньше часа назад — второй чужой будильник экран не поднимает.
        if (!ForeignAlarmRule.raisesScreen(AlarmRuntime.alarmActive, state.challengePassedAtMillis, AndroidClock.nowMillis())) {
            Log.i(TAG, "$reason: наш экран уже идёт или испытание пройдено недавно — второй не поднимаю")
            return
        }
        startRun(context, reason, source = "foreign", preview = false)
    }

    /** Показ экрана из настроек: то же самое, но с кнопкой «Выйти». */
    fun onTestFired(context: Context) =
        startRun(context, "показ из настроек", source = "preview", preview = true)

    /**
     * Телефон перезагрузили посреди тревоги.
     *
     * Выключение устройства **не является выходом**: состояние лежит на диске, и если
     * тревога не была выключена по-настоящему, она поднимается снова, как только
     * телефон разблокировали.
     *
     * ⚠️ **Отсчёт начинается заново, с полного таймаута** (решение владельца
     * 2026-08-14). Прежняя схема с абсолютным дедлайном давала обход измором:
     * перезагрузка съедала минуту-две тишины, вторая перезагрузка добивала остаток,
     * и тревога истекала сама, пока телефон был выключен. Теперь каждый перезапуск
     * возвращает полные пять минут (или сколько выставлено), и перезагружаться
     * бессмысленно: тишины она не приносит, только оттягивает.
     */
    fun onResumeAfterBoot(context: Context) {
        val app = context.applicationContext
        val run = store(app).read().run
        if (run == null) {
            Log.i(TAG, "после загрузки поднимать нечего")
            return
        }
        startRun(app, "перезагрузка посреди тревоги", source = "resume", preview = run.preview)
    }

    /**
     * Владелец выполнил условие выключения.
     *
     * @param reason как именно, для журнала: [REASON_CHALLENGE] — прошёл испытание,
     *               [REASON_EXIT] — нажал «Выйти» в проверочном показе.
     */
    fun dismiss(context: Context, reason: String = REASON_CHALLENGE) {
        Log.i(TAG, "тревога выключена вручную ($reason)")
        clearRun(context.applicationContext, reason)
    }

    /** Сторож снял тревогу по дедлайну. */
    fun dismissByGuard(context: Context) {
        Log.i(TAG, "тревога снята бэкапом по дедлайну")
        clearRun(context.applicationContext, REASON_DEADLINE)
    }

    /**
     * Снятие в журнал пишется здесь, а не в сервисе.
     *
     * ⚠️ Сервис для этого не годится: причину он может только угадать по тому, был ли
     * итог испытания, и уже соврал — проверочный показ, закрытый кнопкой «Выйти»,
     * записывался как снятие по дедлайну. Здесь причина известна точно, а сюда сходятся
     * все пути снятия, включая сторожа из процесса `:guard`.
     *
     * Длительность берётся из состояния на диске: оно переживает и перезагрузку,
     * и смерть процесса, в отличие от памяти сервиса.
     */
    private fun clearRun(app: Context, reason: String) {
        val run = store(app).read().run
        if (run != null) {
            val data = LinkedHashMap<String, LogValue>()
            data["reason"] = LogValue.of(reason)
            data["ms"] = LogValue.of((AndroidClock.nowMillis() - run.startedAtMillis).coerceAtLeast(0L))
            if (run.preview) data["preview"] = LogValue.of(true)
            EventLog(app).write(EventType.ALARM_DISMISSED, data)
        }
        // Пройденное испытание настоящей тревоги — доказательство, что владелец проснулся:
        // по нему второй чужой будильник этого утра экран не поднимает. Дедлайн и показ из
        // настроек ничего не доказывают и отметку не ставят.
        val passed = run != null && !run.preview && reason == REASON_CHALLENGE
        val now = AndroidClock.nowMillis()
        store(app).update {
            it.copy(run = null, challengePassedAtMillis = if (passed) now else it.challengePassedAtMillis)
        }
        AlarmScheduling.cancelGuard(app, GuardReceiver::class.java)
        DeviceOwner.releaseHardLock(app)
        app.stopService(Intent(app, AlarmService::class.java))
        app.sendBroadcast(Intent(AlarmActivity.ACTION_DISMISS).setPackage(app.packageName))
    }

    // 🔴 **Разовых замеров света на подъёме и снятии здесь больше нет** (владелец,
    // 2026-08-31). Они делались при **любом** испытании — и 31 августа он это поймал:
    // утро прошло на реакции, метки не прикладывались, а точка света на ленте всё равно
    // появилась ровно в момент подъёма экрана. Правило теперь одно и без исключений:
    // непрерывный ряд) и в момент касания самой метки (внутри `nfc.tag`). Там телефон
    // заведомо в руке и датчик показывает комнату; в остальное время он показывает
    // подушку или карман, и такому числу веры нет.

    /** Причины снятия — общий словарь с журналом. */
    const val REASON_CHALLENGE = "challenge"
    const val REASON_EXIT = "exit"
    const val REASON_DEADLINE = "deadline"
    const val REASON_ESCAPE = "escape"

    /** Отладка: показать экран через [delayMillis], чтобы успеть заблокировать телефон. */
    fun scheduleTest(context: Context, delayMillis: Long) {
        AlarmScheduling.scheduleTest(
            context = context.applicationContext,
            triggerAtMillis = AndroidClock.nowMillis() + delayMillis,
            receiver = AlarmReceiver::class.java,
            showActivity = MainActivity::class.java,
        )
    }
}
