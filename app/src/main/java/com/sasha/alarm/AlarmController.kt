package com.sasha.alarm

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.FailSafe
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner

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
    private fun startRun(context: Context, reason: String, preview: Boolean) {
        val app = context.applicationContext
        val now = AndroidClock.nowMillis()
        val state = store(app).update {
            it.copy(run = FailSafe.runFor(now, it.failSafeMinutes, preview))
        }
        val run = state.run ?: return

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

    /** Владелец выключил чужой будильник (Sleep Cycle) — поднимаем свой экран. */
    fun onForeignAlarmDismissed(context: Context) {
        if (!state(context).masterEnabled) {
            Log.i(TAG, "приложение выключено общим тумблером — тревогу не поднимаю")
            return
        }
        startRun(context, "зацеп за Sleep Cycle", preview = false)
    }

    /** Показ экрана из настроек: то же самое, но с кнопкой «Выйти». */
    fun onTestFired(context: Context) = startRun(context, "показ из настроек", preview = true)

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
        startRun(app, "перезагрузка посреди тревоги", preview = run.preview)
    }

    /** Владелец выполнил условие выключения. */
    fun dismiss(context: Context) {
        Log.i(TAG, "тревога выключена вручную")
        clearRun(context.applicationContext)
    }

    /** Сторож снял тревогу по дедлайну. */
    fun dismissByGuard(context: Context) {
        Log.i(TAG, "тревога снята бэкапом по дедлайну")
        clearRun(context.applicationContext)
    }

    private fun clearRun(app: Context) {
        store(app).update { it.copy(run = null) }
        AlarmScheduling.cancelGuard(app, GuardReceiver::class.java)
        DeviceOwner.releaseHardLock(app)
        app.stopService(Intent(app, AlarmService::class.java))
        app.sendBroadcast(Intent(AlarmActivity.ACTION_DISMISS).setPackage(app.packageName))
    }

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
