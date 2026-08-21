package com.sasha.alarm.core

import kotlin.math.roundToInt

/**
 * Итог пройденного испытания — то, что видно на экране победы.
 *
 * Считается за **одно** испытание и нигде не хранится: приложение не ведёт историю,
 * поэтому снимок живёт ровно до следующей тревоги.
 *
 * Собирается только при настоящей победе. Снятие по дедлайну сторожем итога не даёт —
 * там показывать нечего.
 */
data class VictoryStats(
    val challenge: Challenge,
    /** Когда поднялся экран тревоги. */
    val startedAtMillis: Long,
    /** Когда испытание было выполнено. */
    val finishedAtMillis: Long,
    val mathSolved: Int = 0,
    val mathTotal: Int = 0,
    /** Сколько раз ответ был неверным. */
    val mathWrong: Int = 0,
    val reactionHits: Int = 0,
    /** Кружки, которым дали погаснуть. */
    val reactionMisses: Int = 0,
    val pushupReps: Int = 0,
    val pushupTarget: Int = 0,
    /** Сколько касаний меток было в маршруте. За победу пройдены все. */
    val nfcSteps: Int = 0,
) {
    /** Среднее время на одну метку. null — меток не обходили. */
    val millisPerTag: Long? get() = if (nfcSteps > 0) durationMillis / nfcSteps else null

    /** Сколько заняло испытание. Часы могли уйти назад — тогда ноль, а не минус. */
    val durationMillis: Long get() = (finishedAtMillis - startedAtMillis).coerceAtLeast(0L)

    /** Среднее время на один пример. null — примеров не решали. */
    val millisPerTask: Long? get() = if (mathSolved > 0) durationMillis / mathSolved else null

    /** Доля пойманных кружков, 0..100. null — кружков не показывали вовсе. */
    val accuracyPercent: Int? get() {
        val shown = reactionHits + reactionMisses
        return if (shown > 0) (reactionHits * 100.0 / shown).roundToInt() else null
    }
}
