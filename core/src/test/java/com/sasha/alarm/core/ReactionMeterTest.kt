package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionMeterTest {

    @Test
    fun `идеальная игра снимает шкалу за заданное время`() {
        val settings = ReactionSettings(perfectSeconds = 30)
        var progress = ReactionMeter.START_PERCENT
        var hits = 0
        while (!ReactionMeter.done(progress) && hits < 1000) {
            progress = ReactionMeter.onHit(settings, progress)
            hits++
        }
        val seconds = hits * ReactionMeter.SPAWN_INTERVAL_MS / 1000.0
        assertTrue("вышло $seconds с", seconds in 28.0..32.0)
    }

    @Test
    fun `промах дороже попадания - вялая игра шкалу не двигает`() {
        val settings = ReactionSettings(perfectSeconds = 30)
        val afterHitThenMiss = ReactionMeter.onMiss(
            settings,
            ReactionMeter.onHit(settings, ReactionMeter.START_PERCENT),
        )
        assertTrue(afterHitThenMiss > ReactionMeter.START_PERCENT - 0.001)
    }

    @Test
    fun `шкала не уходит за края`() {
        val settings = ReactionSettings(perfectSeconds = 10)
        assertEquals(0.0, ReactionMeter.onHit(settings, 0.5), 0.0001)
        assertEquals(
            ReactionMeter.START_PERCENT,
            ReactionMeter.onMiss(settings, ReactionMeter.START_PERCENT),
            0.0001,
        )
    }

    @Test
    fun `шкала на нуле закрывает испытание`() {
        assertFalse(ReactionMeter.done(0.4))
        assertTrue(ReactionMeter.done(0.0))
    }

    @Test
    fun `все испытания готовы к работе`() {
        Challenge.entries.forEach { assertTrue(it.name, it.available) }
    }
}
