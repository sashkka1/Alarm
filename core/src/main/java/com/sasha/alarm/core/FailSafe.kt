package com.sasha.alarm.core

/**
 * Идущая прямо сейчас тревога.
 *
 * [deadlineMillis] — абсолютная метка времени, а не «сколько осталось»:
 * сторож живёт в отдельном процессе и переживает смерть UI (P0 №5),
 * поэтому обратный отсчёт в памяти не годится.
 */
data class AlarmRun(
    val startedAtMillis: Long,
    val deadlineMillis: Long,
    /**
     * Это показ из настроек, а не настоящая тревога.
     *
     * Отличие ровно одно: на экране есть кнопка «Выйти». Всё остальное — блокировка,
     * звук, задание, сторож — работает как в бою.
     */
    val preview: Boolean = false,
)

object FailSafe {

    /** Через сколько минут экран снимается сам, если задание не выполнено. */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 10
    const val DEFAULT_MINUTES = 5

    fun timeoutMillis(minutes: Int): Long =
        minutes.coerceIn(MIN_MINUTES, MAX_MINUTES) * 60_000L

    fun runFor(startedAtMillis: Long, minutes: Int, preview: Boolean = false): AlarmRun =
        AlarmRun(startedAtMillis, startedAtMillis + timeoutMillis(minutes), preview)

    /**
     * Пора снимать блокировку.
     *
     * Два случая, а не один:
     *  - дедлайн наступил — обычный путь;
     *  - **часы ушли назад** относительно начала тревоги. Дедлайн хранится абсолютной
     *    меткой, поэтому переводом часов назад его можно было бы отодвинуть сколь угодно
     *    далеко и запереть телефон. Считаем это поводом снять блок немедленно:
     *    лишний раз отпустить безопаснее, чем не отпустить вовсе (P0 №7).
     */
    fun expired(run: AlarmRun, nowMillis: Long): Boolean =
        nowMillis >= run.deadlineMillis || nowMillis < run.startedAtMillis

    fun remainingMillis(run: AlarmRun, nowMillis: Long): Long =
        (run.deadlineMillis - nowMillis).coerceAtLeast(0L)
}
