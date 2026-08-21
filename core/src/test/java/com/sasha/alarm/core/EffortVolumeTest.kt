package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortVolumeTest {

    @Test
    fun `до первого движения громкость полная`() {
        assertEquals(1f, EffortVolume.factor(lastMovementAtMillis = 0L, nowMillis = 10_000L), 0.001f)
    }

    @Test
    fun `пошёл вниз — сразу в десять раз тише`() {
        // Приглушение мгновенное намеренно: награда должна быть заметна сразу.
        assertEquals(0.1f, EffortVolume.factor(1_000L, 1_000L), 0.001f)
        assertEquals(0.1f, EffortVolume.factor(1_000L, 3_000L), 0.001f)
    }

    @Test
    fun `между повторами громкость не мигает`() {
        // Отжимание занимает пару секунд, и всё это время движения «нет».
        assertEquals(0.1f, EffortVolume.factor(0L + 1_000L, 1_000L + 3_500L), 0.001f)
    }

    @Test
    fun `перестал — возвращается плавно, а не рывком`() {
        val started = 1_000L
        val quietUntil = started + EffortVolume.WORK_MEMORY_MS

        // Сразу после конца памяти — ещё тихо.
        assertEquals(0.1f, EffortVolume.factor(started, quietUntil), 0.01f)

        // На середине возврата — примерно посередине между тихо и громко.
        val half = quietUntil + EffortVolume.RETURN_MS / 2
        assertEquals(0.55f, EffortVolume.factor(started, half), 0.02f)

        // К концу возврата — полная.
        val full = quietUntil + EffortVolume.RETURN_MS
        assertEquals(1f, EffortVolume.factor(started, full), 0.001f)
    }

    @Test
    fun `дальше полной громкости не растёт`() {
        assertEquals(1f, EffortVolume.factor(1_000L, 10_000_000L), 0.001f)
    }

    @Test
    fun `часы ушли назад — не приглушаем`() {
        // Лишний раз сыграть громко безопаснее, чем замолчать (P0 №7).
        assertEquals(1f, EffortVolume.factor(lastMovementAtMillis = 50_000L, nowMillis = 10_000L), 0.001f)
    }

    @Test
    fun `пока работает — нарастание стоит`() {
        assertFalse(EffortVolume.rampRuns(1_000L, 2_000L))
        assertFalse(EffortVolume.rampRuns(1_000L, 1_000L + EffortVolume.WORK_MEMORY_MS))
    }

    @Test
    fun `перестал — нарастание идёт снова`() {
        val after = 1_000L + EffortVolume.WORK_MEMORY_MS + 1_000L
        assertTrue(EffortVolume.rampRuns(1_000L, after))
    }

    @Test
    fun `нарастание идёт, пока движения не было ни разу`() {
        assertTrue(EffortVolume.rampRuns(lastMovementAtMillis = 0L, nowMillis = 5_000L))
    }

    @Test
    fun `проценты приглушаются и не выходят за границы`() {
        assertEquals(8, EffortVolume.percent(80, 0.1f))
        assertEquals(80, EffortVolume.percent(80, 1f))
        assertEquals(0, EffortVolume.percent(0, 0.1f))
        assertEquals(100, EffortVolume.percent(200, 1f))
    }
}
