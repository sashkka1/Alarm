package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCurveTest {

    private fun settings(start: Int = 20, secondsPerPercent: Int = 3) = SoundSettings(
        enabled = true,
        startVolumePercent = start,
        secondsPerPercent = secondsPerPercent,
        vibrate = true,
        melody = MelodySource.SystemAlarm,
    )

    /** Нажать по кружку [times] раз подряд в момент [elapsedMs], каждый раз прося максимум. */
    private fun tap(s: SoundSettings, elapsedMs: Long, times: Int): Int {
        var deduction = 0
        repeat(times) {
            deduction = VolumeCurve.deductionAfterTap(
                settings = s,
                elapsedMs = elapsedMs,
                quietDeduction = deduction,
                step = VolumeCurve.QUIET_STEP_MAX_PERCENT,
                grownSinceLastTapPercent = 100,
            )
        }
        return deduction
    }

    @Test
    fun `в начале звонка играет стартовая громкость`() {
        assertEquals(20, VolumeCurve.percentAt(settings(), elapsedMs = 0L, quietDeduction = 0))
    }

    @Test
    fun `самое быстрое нарастание - процент в секунду`() {
        val s = settings(start = 0, secondsPerPercent = 1)
        assertEquals(10, VolumeCurve.percentAt(s, elapsedMs = 10_000L, quietDeduction = 0))
    }

    @Test
    fun `самое медленное нарастание - процент за пять секунд`() {
        val s = settings(start = 0, secondsPerPercent = 5)
        assertEquals(2, VolumeCurve.percentAt(s, elapsedMs = 10_000L, quietDeduction = 0))
    }

    @Test
    fun `каждое нажатие сбивает свой шаг`() {
        val s = settings()
        val step = VolumeCurve.QUIET_STEP_MAX_PERCENT
        assertEquals(20 - step, VolumeCurve.percentAt(s, 0L, tap(s, 0L, 1)))
        assertEquals(20 - 3 * step, VolumeCurve.percentAt(s, 0L, tap(s, 0L, 3)))
    }

    @Test
    fun `на нуле лишние нажатия ничего не копят`() {
        // Тот самый баг: раньше сбитое уходило в минус, и потом нарастанию
        // приходилось сначала отыграть невидимый долг.
        val s = settings(start = 20, secondsPerPercent = 1)
        val deduction = tap(s, elapsedMs = 0L, times = 50)
        assertEquals(20, deduction)
        assertEquals(0, VolumeCurve.percentAt(s, 0L, deduction))
    }

    @Test
    fun `с нуля громкость растёт сразу, без задержки`() {
        val s = settings(start = 20, secondsPerPercent = 1)
        val deduction = tap(s, elapsedMs = 0L, times = 50)
        assertEquals(0, VolumeCurve.percentAt(s, 0L, deduction))
        assertEquals(1, VolumeCurve.percentAt(s, 1_000L, deduction))
        assertEquals(10, VolumeCurve.percentAt(s, 10_000L, deduction))
    }

    @Test
    fun `сбить можно и то, что успело набежать`() {
        val s = settings(start = 0, secondsPerPercent = 1)
        val deduction = tap(s, elapsedMs = 30_000L, times = 2)
        assertEquals(2 * VolumeCurve.QUIET_STEP_MAX_PERCENT, deduction)
        assertEquals(30 - 2 * VolumeCurve.QUIET_STEP_MAX_PERCENT, VolumeCurve.percentAt(s, 30_000L, deduction))
    }

    @Test
    fun `громкость не уходит за сто процентов`() {
        val s = settings(start = 5, secondsPerPercent = 1)
        assertEquals(100, VolumeCurve.percentAt(s, 10 * 60_000L, quietDeduction = 0))
    }

    @Test
    fun `громкость проигрывателя это доля единицы`() {
        assertEquals(0f, VolumeCurve.playerVolume(0), 0.001f)
        assertEquals(0.5f, VolumeCurve.playerVolume(50), 0.001f)
        assertEquals(1f, VolumeCurve.playerVolume(100), 0.001f)
    }
}
