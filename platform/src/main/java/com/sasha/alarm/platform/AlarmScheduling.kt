package com.sasha.alarm.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Постановка служебных таймеров в систему.
 *
 * ⚠️ Собственного будильника у приложения больше нет (решение владельца 2026-08-14),
 * поэтому `setAlarmClock()` здесь не используется: остались только сторож бэкапа
 * и отладочный показ экрана, обоим хватает `setExactAndAllowWhileIdle`.
 */
object AlarmScheduling {

    private const val TAG = "AlarmScheduling"
    private const val REQUEST_GUARD = 1002
    private const val REQUEST_TEST = 1003
    private const val REQUEST_GUARD_ELAPSED = 1004
    private const val REQUEST_RESUME = 1005

    /** Пометка «это отладочный показ». */
    const val EXTRA_TEST = "com.sasha.alarm.TEST"

    /** Пометка «поднимаем тревогу заново после перезагрузки». */
    const val EXTRA_RESUME = "com.sasha.alarm.RESUME"

    /** Отличает сторожа на монотонных часах — нужно, чтобы это был отдельный PendingIntent. */
    const val ACTION_GUARD_ELAPSED = "com.sasha.alarm.GUARD_ELAPSED"

    /**
     * Сторож бэкапа: снимет тревогу, даже если основной процесс умрёт.
     * Ставится ДО показа экрана (P0 №5).
     *
     * Ставится **дважды, на разные часы**, и это не перестраховка:
     *  - по календарным часам — на абсолютный дедлайн;
     *  - по монотонным часам (время с загрузки) — через [timeoutMillis].
     *
     * Календарные часы можно перевести назад, и тогда первый сторож отодвинулся бы
     * вместе с ними, а телефон остался бы заперт. Монотонные часы перевести нельзя
     * ничем. Срабатывает тот, что раньше; второй потом видит, что тревоги уже нет,
     * и молча уходит.
     */
    fun scheduleGuard(
        context: Context,
        triggerAtMillis: Long,
        timeoutMillis: Long,
        receiver: Class<out BroadcastReceiver>,
    ) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            guardIntent(context, receiver),
        )
        manager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + timeoutMillis,
            guardElapsedIntent(context, receiver),
        )
        Log.i(TAG, "сторож поставлен на $triggerAtMillis и через $timeoutMillis мс по монотонным часам")
    }

    fun cancelGuard(context: Context, receiver: Class<out BroadcastReceiver>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(guardIntent(context, receiver))
        manager.cancel(guardElapsedIntent(context, receiver))
        Log.i(TAG, "сторож снят")
    }

    /**
     * Показ экрана из настроек.
     *
     * Идёт через `setAlarmClock`, а не `setExactAndAllowWhileIdle`, и это принципиально:
     * второй система вправе придержать — на него есть ограничение по частоте, и при
     * повторных запусках подряд показ уезжал с трёх секунд на десяток. `setAlarmClock`
     * не откладывают никогда, поэтому «через три секунды» означает ровно три секунды.
     */
    fun scheduleTest(
        context: Context,
        triggerAtMillis: Long,
        receiver: Class<out BroadcastReceiver>,
        showActivity: Class<*>,
    ) {
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_TEST,
            Intent(context, receiver).putExtra(EXTRA_TEST, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            REQUEST_TEST,
            Intent(context, showActivity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
        Log.i(TAG, "показ экрана поставлен на $triggerAtMillis")
    }

    /**
     * Поднять тревогу заново после перезагрузки.
     *
     * Идём через будильник, а не запускаем службу напрямую из `BOOT_COMPLETED`:
     * с Android 15 службам переднего плана типа `specialUse` запрещено стартовать
     * по загрузке, а вот у бродкаста от точного будильника такое право есть.
     */
    fun scheduleResume(
        context: Context,
        triggerAtMillis: Long,
        receiver: Class<out BroadcastReceiver>,
    ) {
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_RESUME,
                    Intent(context, receiver)
                        .setAction(EXTRA_RESUME)
                        .putExtra(EXTRA_RESUME, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        Log.i(TAG, "тревога будет поднята заново в $triggerAtMillis")
    }

    private fun guardIntent(context: Context, receiver: Class<out BroadcastReceiver>) =
        PendingIntent.getBroadcast(
            context,
            REQUEST_GUARD,
            Intent(context, receiver),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun guardElapsedIntent(context: Context, receiver: Class<out BroadcastReceiver>) =
        PendingIntent.getBroadcast(
            context,
            REQUEST_GUARD_ELAPSED,
            Intent(context, receiver).setAction(ACTION_GUARD_ELAPSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
