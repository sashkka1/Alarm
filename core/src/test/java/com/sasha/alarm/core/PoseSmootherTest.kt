package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PoseSmootherTest {

    private fun p(x: Float, y: Float, c: Float = 0.9f) = PosePoint(x, y, c)

    /** Прогнать несколько кадров подряд с постоянным шагом. */
    private fun run(
        start: List<PosePoint>,
        frames: List<List<PosePoint>>,
        stepMillis: Long = 33L,
    ): PoseSmoother.Memory {
        var memory = PoseSmoother.blend(null, start, stepMillis)
        frames.forEach { memory = PoseSmoother.blend(memory, it, stepMillis) }
        return memory
    }

    @Test
    fun `первый кадр показывается как есть`() {
        val fresh = listOf(p(0.5f, 0.5f))
        assertSame(fresh, PoseSmoother.blend(null, fresh, 16L).points)
    }

    @Test
    fun `дрожание на месте почти не двигает точку`() {
        // Модель промахивается на сотые доли кадра — на экране этого быть не должно.
        val jitter = (0 until 20).map { i ->
            listOf(p(0.5f + if (i % 2 == 0) 0.008f else -0.008f, 0.5f))
        }
        val memory = run(listOf(p(0.5f, 0.5f)), jitter)
        assertTrue("ушла на ${memory.points[0].x}", abs(memory.points[0].x - 0.5f) < 0.006f)
    }

    @Test
    fun `за быстрым движением фильтр поспевает`() {
        // Точка едет ровно, кадр за кадром. Через десяток кадров скелет обязан
        // догнать тело, иначе он будет плестись за человеком.
        val moving = (1..12).map { listOf(p(0.2f + it * 0.02f, 0.5f)) }
        val memory = run(listOf(p(0.2f, 0.5f)), moving)
        val target = 0.2f + 12 * 0.02f
        assertTrue(
            "отстал: ${memory.points[0].x} против $target",
            abs(memory.points[0].x - target) < 0.03f,
        )
    }

    @Test
    fun `быстрое движение сглаживается слабее медленного`() {
        // В этом весь смысл One Euro: коэффициент зависит от скорости.
        val slow = run(listOf(p(0.5f, 0.5f)), (1..5).map { listOf(p(0.5f + it * 0.002f, 0.5f)) })
        val fast = run(listOf(p(0.5f, 0.5f)), (1..5).map { listOf(p(0.5f + it * 0.05f, 0.5f)) })

        val slowShare = (slow.points[0].x - 0.5f) / (5 * 0.002f)
        val fastShare = (fast.points[0].x - 0.5f) / (5 * 0.05f)
        assertTrue("медленное $slowShare, быстрое $fastShare", fastShare > slowShare)
    }

    @Test
    fun `далёкий скачок не тянем плавно, а показываем сразу`() {
        // Так выглядит вход в кадр или перескок модели на другого человека.
        val memory = PoseSmoother.blend(
            memory = PoseSmoother.Memory(listOf(p(0.1f, 0.1f)), listOf(0f to 0f)),
            fresh = listOf(p(0.9f, 0.9f)),
            deltaMillis = 16L,
        )
        assertEquals(0.9f, memory.points[0].x, 0.001f)
    }

    @Test
    fun `уверенность берётся свежая, а не смешанная`() {
        val memory = PoseSmoother.blend(
            memory = PoseSmoother.Memory(listOf(p(0.5f, 0.5f, c = 1f)), listOf(0f to 0f)),
            fresh = listOf(p(0.5f, 0.5f, c = 0.1f)),
            deltaMillis = 16L,
        )
        assertEquals(0.1f, memory.points[0].confidence, 0.001f)
    }

    @Test
    fun `изменившийся состав точек не смешивается`() {
        val fresh = listOf(p(1f, 1f), p(0.5f, 0.5f))
        val memory = PoseSmoother.blend(
            memory = PoseSmoother.Memory(listOf(p(0f, 0f)), listOf(0f to 0f)),
            fresh = fresh,
            deltaMillis = 16L,
        )
        assertSame(fresh, memory.points)
    }

    @Test
    fun `нулевой промежуток кадр не двигает`() {
        val memory = PoseSmoother.blend(null, listOf(p(0f, 0f)), deltaMillis = 0L)
        assertEquals(0f, memory.points[0].x, 0.001f)
    }

    @Test
    fun `за больший промежуток проходим ближе к цели`() {
        assertTrue(
            PoseSmoother.alpha(PoseSmoother.MIN_CUTOFF, 0.1) >
                PoseSmoother.alpha(PoseSmoother.MIN_CUTOFF, 0.01),
        )
    }
}
