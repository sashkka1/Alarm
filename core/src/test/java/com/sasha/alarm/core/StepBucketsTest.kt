package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBucketsTest {

    private val sec = 1_000L
    private val m = StepBuckets.BUCKET_MS

    /** «Сейчас» далеко впереди: все корзины теста уже осели. */
    private val later = 1_000 * m

    /** Строка в секундах: так видно куски по ~30 с, какими их отдаёт система. */
    private fun row(fromSeconds: Long, toSeconds: Long, steps: Long) =
        StepBuckets.Row(fromSeconds * sec, toSeconds * sec, steps)

    @Test
    fun `корзина шириной в минуту`() {
        assertEquals(60_000L, StepBuckets.BUCKET_MS)
    }

    @Test
    fun `строка внутри корзины целиком ложится в неё`() {
        val buckets = StepBuckets.of(listOf(row(10, 40, 30)), later, emptySet())
        assertEquals(listOf(StepBuckets.Bucket(0, m, 30)), buckets)
    }

    @Test
    fun `строка через границу минуты делится пропорционально перекрытию`() {
        // 45…75 секунд: пятнадцать секунд в первой минуте, пятнадцать во второй.
        val buckets = StepBuckets.of(listOf(row(45, 75, 40)), later, emptySet())
        assertEquals(
            listOf(StepBuckets.Bucket(0, m, 20), StepBuckets.Bucket(m, 2 * m, 20)),
            buckets,
        )
    }

    @Test
    fun `длинная строка режется на много минут`() {
        // 0…5 минут, 500 шагов: по сотне на минуту.
        val buckets = StepBuckets.of(listOf(row(0, 300, 500)), later, emptySet())
        assertEquals((0L until 5L).map { StepBuckets.Bucket(it * m, (it + 1) * m, 100) }, buckets)
    }

    @Test
    fun `много ходил, потом мало — минуты это видят`() {
        // Первая минута — две строки по 30 с с полусотней шагов, вторая — одна вялая строка.
        val rows = listOf(row(0, 30, 50), row(30, 60, 50), row(60, 90, 4))
        val buckets = StepBuckets.of(rows, later, emptySet())
        assertEquals(listOf(StepBuckets.Bucket(0, m, 100), StepBuckets.Bucket(m, 2 * m, 4)), buckets)
    }

    @Test
    fun `дробные доли копятся и округляются по корзине`() {
        // Три строки дают первой корзине по трети шага: по отдельности каждая доля
        // округлилась бы в ноль, а вместе это целый шаг.
        val rows = listOf(row(50, 80, 1), row(50, 80, 1), row(50, 80, 1))
        val buckets = StepBuckets.of(rows, later, emptySet())
        assertEquals(listOf(StepBuckets.Bucket(0, m, 1), StepBuckets.Bucket(m, 2 * m, 2)), buckets)
    }

    @Test
    fun `незакрытая корзина не отдаётся`() {
        // Минута 0…1 кончилась, но не отлежалась SETTLE_MS: система ещё может дописывать строку.
        val now = m + StepBuckets.SETTLE_MS - 1
        assertTrue(StepBuckets.of(listOf(row(10, 40, 30)), now, emptySet()).isEmpty())
        val settled = StepBuckets.of(listOf(row(10, 40, 30)), now + 1, emptySet())
        assertEquals(1, settled.size)
    }

    @Test
    fun `известная корзина не отдаётся повторно`() {
        val rows = listOf(row(45, 75, 40))
        val buckets = StepBuckets.of(rows, later, known = setOf(0L))
        assertEquals(listOf(StepBuckets.Bucket(m, 2 * m, 20)), buckets)
    }

    @Test
    fun `старые корзины по 15 минут знакомыми не считаются`() {
        val quarter = 15 * m
        val known = StepBuckets.known(
            listOf(
                0L to quarter, // старая четверть часа — не в счёт
                quarter to quarter + m, // минутная — знакомая
                2 * quarter to null, // неполная — пропускается
            )
        )
        assertEquals(setOf(quarter), known)
        // Строка в первой минуте старой корзины всё равно отдаётся.
        val buckets = StepBuckets.of(listOf(row(10, 40, 30)), later, known)
        assertEquals(listOf(StepBuckets.Bucket(0, m, 30)), buckets)
    }

    @Test
    fun `битые строки отбрасываются`() {
        val rows = listOf(
            row(40, 10, 100), // конец раньше начала
            row(10, 40, 0), // без шагов
            row(10, 40, -7), // отрицательные шаги
        )
        assertTrue(StepBuckets.of(rows, later, emptySet()).isEmpty())
    }

    @Test
    fun `строка без длины целиком уходит в корзину своего начала`() {
        val buckets = StepBuckets.of(listOf(row(60, 60, 40)), later, emptySet())
        assertEquals(listOf(StepBuckets.Bucket(m, 2 * m, 40)), buckets)
    }

    @Test
    fun `корзина, округлившаяся в ноль, не отдаётся`() {
        // Один шаг на 4 минуты: по четверти шага на корзину — после округления пусто.
        assertTrue(StepBuckets.of(listOf(row(0, 240, 1)), later, emptySet()).isEmpty())
    }

    @Test
    fun `корзины идут по возрастанию начала`() {
        val rows = listOf(row(130, 150, 10), row(5, 25, 10), row(70, 90, 10))
        val buckets = StepBuckets.of(rows, later, emptySet())
        assertEquals(listOf(0L, m, 2 * m), buckets.map { it.from })
    }
}
