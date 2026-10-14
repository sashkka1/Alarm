package com.sasha.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.sasha.alarm.core.HookHealth
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.EventLog
import com.sasha.alarm.platform.Permissions

/**
 * Сторож зацепа: жив ли слушатель уведомлений, поднять его, если нет, и сказать, если
 * не вышло. Решения — в [HookHealth], здесь только исполнение.
 *
 * Порядок лечения: сначала `requestRebind` — но система слушается его, только если слушатель
 * отвязан по собственной просьбе (`requestUnbind`), поэтому следом идёт толчок ([nudge]):
 * переключение пустышки даёт системе «пакет изменился», и она заново привязывает разрешённых
 * слушателей пакета — уже к живому процессу, в котором мы сейчас работаем. Сигнал доходит
 * с задержкой в десятки секунд, поэтому итог смотрит повторная проверка через минуту.
 *
 * 🔴 **Сам слушатель не выключать никогда — даже на секунду.** Проверено на телефоне
 * 30.09: выключенный компонент слушателя система вычёркивает из разрешённых, то есть
 * отбирает у приложения доступ к уведомлениям, и включение его обратно доступ не
 * возвращает — вернул только кабель (`cmd notification allow_listener`).
 */
object HookWatch {

    private const val TAG = "HookWatch"

    /** Сколько ждать подключения после `requestRebind`. */
    private const val WAIT_MS = 3_000L
    private const val POLL_MS = 200L

    /** Пустышка для толчка — `activity-alias` в манифесте, выключенный по умолчанию. */
    private const val NUDGE = "com.sasha.alarm.HookNudge"

    private const val CHANNEL_ID = "hook_health"
    private const val NOTIFICATION_ID = 4715

    /**
     * Идёт проверка. Слушатель, подключившийся посреди неё, отчитываться не должен —
     * это сделает сама проверка, иначе в журнал легли бы два события на одно подключение.
     */
    @Volatile
    private var checking = false

    /** Поставить следующую проверку через [HookHealth.CHECK_INTERVAL_MS]. */
    fun schedule(context: Context) {
        AlarmScheduling.scheduleHookCheck(
            context = context.applicationContext,
            triggerAtMillis = AndroidClock.nowMillis() + HookHealth.CHECK_INTERVAL_MS,
            receiver = HookCheckReceiver::class.java,
        )
    }

    /**
     * Проверить, при нужде поднять и отчитаться.
     *
     * ⚠️ Только с фонового потока: переподключение ждёт до трёх секунд.
     *
     * @return что показала проверка; null — приложение выключено тумблером и зацеп не нужен
     */
    @Synchronized
    fun run(context: Context): HookHealth.Status? {
        val app = context.applicationContext
        if (!AlarmStateStore(app).read().masterEnabled) {
            // Выключено владельцем — молчащий зацеп не поломка, и висеть уведомлению незачем.
            cancelNotice(app)
            return null
        }
        checking = true
        try {
            val component = ComponentName(app, SleepCycleListener::class.java)
            ensureEnabled(app, component)
            var rebound = false
            val status = when {
                !Permissions.notificationListenerEnabled(app) -> HookHealth.Status.NO_ACCESS
                AlarmRuntime.hookConnected -> HookHealth.Status.WORKING
                else -> {
                    rebound = rebind(component)
                    if (rebound) {
                        HookHealth.Status.WORKING
                    } else {
                        nudge(app)
                        HookHealth.Status.UNBOUND
                    }
                }
            }
            if (report(app, status, rebound)?.recheckSoon == true) {
                AlarmScheduling.scheduleHookCheck(
                    context = app,
                    triggerAtMillis = AndroidClock.nowMillis() + HookHealth.RECHECK_MS,
                    receiver = HookCheckReceiver::class.java,
                )
            }
            return status
        } finally {
            checking = false
        }
    }

    /**
     * Слушатель подключился — закрыть отрезок «не работает» или подозрение, если они были:
     * после толчка система привязывает его уже без нас, и итог надо записать сразу.
     */
    fun onConnected(context: Context) {
        if (checking) return
        val app = context.applicationContext
        val state = AlarmStateStore(app).read()
        if (state.hookDownSinceMillis == null && state.hookSuspectSinceMillis == null) return
        report(app, HookHealth.Status.WORKING, rebound = false)
    }

    /** Быстрая попытка: `requestRebind`. @return подключился ли слушатель */
    private fun rebind(component: ComponentName): Boolean {
        Log.w(TAG, "слушатель уведомлений не подключён — переподключаю")
        try {
            NotificationListenerService.requestRebind(component)
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind отказал", e)
        }
        return waitConnected()
    }

    /**
     * Толкнуть систему перепривязать слушатель: переключить пустышку [NUDGE].
     *
     * Каждое переключение — «пакет изменился»; какое из двух состояний у пустышки, неважно,
     * поэтому она просто меняет своё на противоположное.
     */
    private fun nudge(app: Context) {
        val pm = app.packageManager
        val alias = ComponentName(app.packageName, NUDGE)
        val next = if (pm.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        pm.setComponentEnabledSetting(alias, next, PackageManager.DONT_KILL_APP)
        Log.w(TAG, "толкаю систему перепривязать слушатель, проверю снова через минуту")
    }

    /**
     * Компонент слушателя выключен — включить. Сами мы его не выключаем (см. заголовок),
     * но выключить его могли снаружи, например `pm disable` по кабелю.
     */
    private fun ensureEnabled(app: Context, component: ComponentName) {
        val pm = app.packageManager
        if (pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            Log.w(TAG, "компонент слушателя был выключен — включаю")
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun waitConnected(): Boolean {
        val until = System.nanoTime() + WAIT_MS * 1_000_000L
        while (System.nanoTime() < until) {
            if (AlarmRuntime.hookConnected) return true
            try {
                Thread.sleep(POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return AlarmRuntime.hookConnected
            }
        }
        return AlarmRuntime.hookConnected
    }

    private fun report(app: Context, status: HookHealth.Status, rebound: Boolean): HookHealth.Outcome? {
        val now = AndroidClock.nowMillis()
        var outcome: HookHealth.Outcome? = null
        AlarmStateStore(app).update { state ->
            val judged = HookHealth.judge(state.hookDownSinceMillis, state.hookSuspectSinceMillis, status, rebound, now)
            outcome = judged
            state.copy(hookDownSinceMillis = judged.downSince, hookSuspectSinceMillis = judged.suspectSince)
        }
        val result = outcome ?: return null
        Log.i(TAG, "зацеп: $status${if (rebound) " (переподключён)" else ""}")
        result.entry?.let { EventLog(app).write(it.type, it.data) }
        if (result.showNotice) showNotice(app, status) else cancelNotice(app)
        // Хаб узнаёт о поломке из журнала и пишет в Telegram. Ждать суточной передачи
        // нельзя: к утру было бы поздно (владелец, 2026-09-30).
        if (result.sendJournalNow) SyncService.start(app, isReturn = false)
        return result
    }

    private fun showNotice(app: Context, status: HookHealth.Status) {
        val manager = app.getSystemService(NotificationManager::class.java)
        // ⚠️ Без звука: поломка может случиться ночью, а будить из-за неё — не дело
        // уведомления. Висит на виду и не смахивается, пока зацеп не заработает.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                app.getString(R.string.hook_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val text = app.getString(
            if (status == HookHealth.Status.NO_ACCESS) R.string.hook_notice_no_access else R.string.hook_notice_unbound,
        )
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bird)
            .setContentTitle(app.getString(R.string.hook_notice_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notice)
        } catch (e: SecurityException) {
            // Уведомления запрещены — остаются плашка на экране и жалоба в Telegram.
            Log.w(TAG, "уведомление о зацепе не показать", e)
        }
    }

    private fun cancelNotice(app: Context) {
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
