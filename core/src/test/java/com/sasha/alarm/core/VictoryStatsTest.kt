package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VictoryStatsTest {

    private fun stats(
        challenge: Challenge = Challenge.MATH,
        started: Long = 0L,
        finished: Long = 0L,
        solved: Int = 0,
        total: Int = 0,
        wrong: Int = 0,
        hits: Int = 0,
        misses: Int = 0,
    ) = VictoryStats(
        challenge = challenge,
        startedAtMillis = started,
        finishedAtMillis = finished,
        mathSolved = solved,
        mathTotal = total,
        mathWrong = wrong,
        reactionHits = hits,
        reactionMisses = misses,
    )

    @Test
    fun `длительность считается от начала до конца`() {
        assertEquals(134_000L, stats(started = 1_000L, finished = 135_000L).durationMillis)
    }

    @Test
    fun `часы ушли назад — длительность ноль, а не минус`() {
        assertEquals(0L, stats(started = 200_000L, finished = 100_000L).durationMillis)
    }

    @Test
    fun `среднее на пример — длительность на число решённых`() {
        val s = stats(started = 0L, finished = 100_000L, solved = 10, total = 10)
        assertEquals(10_000L, s.millisPerTask)
    }

    @Test
    fun `примеров не было — среднего нет`() {
        assertNull(stats(challenge = Challenge.REACTION, finished = 60_000L).millisPerTask)
    }

    @Test
    fun `точность — доля пойманных от показанных`() {
        assertEquals(80, stats(challenge = Challenge.REACTION, hits = 80, misses = 20).accuracyPercent)
    }

    @Test
    fun `точность округляется до целых процентов`() {
        assertEquals(67, stats(challenge = Challenge.REACTION, hits = 2, misses = 1).accuracyPercent)
    }

    @Test
    fun `кружков не показывали — точности нет`() {
        assertNull(stats(solved = 5, total = 5).accuracyPercent)
    }
}
