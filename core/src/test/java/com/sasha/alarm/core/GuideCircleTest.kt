package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideCircleTest {

    @Test
    fun `круг не выходит за область`() {
        // Шагаем мелко и долго: путь квазипериодический, границу он щупает не сразу.
        for (ms in 0L..40_000L step 50L) {
            val x = GuideCircle.x(ms)
            val y = GuideCircle.y(ms)
            assertTrue("x=$x на $ms", x in 0.05f..0.95f)
            assertTrue("y=$y на $ms", y in 0.05f..0.95f)
        }
    }

    @Test
    fun `круг всё время едет`() {
        // Ради этого он и существует: палец на одном месте тишины не даёт.
        var still = 0
        for (ms in 0L..30_000L step 200L) {
            val moved = kotlin.math.hypot(
                GuideCircle.x(ms + 200L) - GuideCircle.x(ms),
                GuideCircle.y(ms + 200L) - GuideCircle.y(ms),
            )
            if (moved < 0.002f) still++
        }
        // Остановки в точках разворота неизбежны, но их единицы, а не половина пути.
        assertTrue("замер $still раз", still < 15)
    }

    @Test
    fun `путь не повторяется по одному кругу`() {
        // Периоды некратные, поэтому через период по горизонтали вертикаль другая.
        val y0 = GuideCircle.y(0L)
        val y1 = GuideCircle.y(GuideCircle.PERIOD_X_MS)
        assertTrue("y0=$y0 y1=$y1", kotlin.math.abs(y1 - y0) > 0.1f)
    }

    @Test
    fun `граница круга строгая`() {
        val r = 100f
        assertTrue(GuideCircle.holds(0f, 0f, r))
        assertTrue(GuideCircle.holds(60f, 60f, r))
        // Ровно по краю — ещё держит, чуть дальше — уже нет.
        assertTrue(GuideCircle.holds(100f, 0f, r))
        assertFalse(GuideCircle.holds(101f, 0f, r))
        assertFalse(GuideCircle.holds(80f, 80f, r))
    }

    @Test
    fun `пока ведёт — тишина и нарастание стоит`() {
        assertEquals(0f, GuideCircle.factor(holding = true, releasedAtMillis = 0L, nowMillis = 5_000L), 0.001f)
        assertFalse(GuideCircle.rampRuns(holding = true))
    }

    @Test
    fun `ни разу не держал — громкость полная`() {
        assertEquals(1f, GuideCircle.factor(holding = false, releasedAtMillis = 0L, nowMillis = 5_000L), 0.001f)
        assertTrue(GuideCircle.rampRuns(holding = false))
    }

    @Test
    fun `сорвался — возвращается плавно`() {
        val released = 10_000L
        assertEquals(0f, GuideCircle.factor(false, released, released), 0.001f)
        assertEquals(
            0.5f,
            GuideCircle.factor(false, released, released + GuideCircle.RETURN_MS / 2),
            0.02f,
        )
        assertEquals(1f, GuideCircle.factor(false, released, released + GuideCircle.RETURN_MS), 0.001f)
        // Дальше уже полная и выше единицы не уходит.
        assertEquals(1f, GuideCircle.factor(false, released, released + 60_000L), 0.001f)
    }

    @Test
    fun `после срыва нарастание начинается заново`() {
        // Владелец, 2026-08-27: звук идёт с начала нарастания, а не с набежавшего
        // уровня. Значит в простой уходит вообще всё время с начала тревоги.
        val started = 1_000L
        val released = started + 90_000L
        assertEquals(90_000L, GuideCircle.rampHoldAfterRelease(started, released))

        val sound = SoundSettings.DEFAULT
        val elapsed = (released - started) - GuideCircle.rampHoldAfterRelease(started, released)
        assertEquals(
            sound.startVolumePercent,
            VolumeCurve.percentAt(sound, elapsed, quietDeduction = 0),
        )
    }

    @Test
    fun `часы перевели назад — простой не уходит в минус`() {
        // Отрицательный простой добавил бы нарастанию времени, которого не было.
        assertEquals(0L, GuideCircle.rampHoldAfterRelease(startedAtMillis = 10_000L, releasedAtMillis = 9_000L))
    }

    @Test
    fun `часы перевели назад — играем громко`() {
        // Тишина вместо будильника хуже любого другого исхода (P0 №7).
        assertEquals(1f, GuideCircle.factor(false, releasedAtMillis = 10_000L, nowMillis = 9_000L), 0.001f)
    }
}
