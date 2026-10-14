package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRetryTest {

    private val now = 1_000_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun `заход начинается с попытки без задержки`() {
        assertEquals(0L, SyncRetry.burstOffsets().first())
    }

    @Test
    fun `попытки идут раз в две секунды`() {
        val offsets = SyncRetry.burstOffsets()
        val steps = offsets.zipWithNext { a, b -> b - a }.distinct()
        assertEquals(listOf(SyncRetry.BURST_STEP_MS), steps)
    }

    @Test
    fun `заход укладывается в отведённое окно`() {
        val offsets = SyncRetry.burstOffsets()
        assertTrue(offsets.last() < SyncRetry.BURST_WINDOW_MS)
        assertTrue(offsets.last() + SyncRetry.BURST_STEP_MS >= SyncRetry.BURST_WINDOW_MS)
    }

    @Test
    fun `пятнадцать секунд по две дают восемь начал попыток`() {
        assertEquals(8, SyncRetry.burstOffsets().size)
    }

    @Test
    fun `первый неудачный заход зовёт вернуться через два часа`() {
        assertEquals(now + 2 * hour, SyncRetry.nextReturnAt(returnsSpent = 0, nowMillis = now))
    }

    @Test
    fun `второй и третий возвраты тоже назначаются`() {
        assertEquals(now + 2 * hour, SyncRetry.nextReturnAt(returnsSpent = 1, nowMillis = now))
        assertEquals(now + 2 * hour, SyncRetry.nextReturnAt(returnsSpent = 2, nowMillis = now))
    }

    @Test
    fun `после третьего возврата ждём следующего дня, а не стучимся дальше`() {
        assertNull(SyncRetry.nextReturnAt(returnsSpent = SyncRetry.MAX_RETURNS, nowMillis = now))
    }

    @Test
    fun `сорванный счётчик не открывает четвёртый возврат`() {
        assertNull(SyncRetry.nextReturnAt(returnsSpent = 99, nowMillis = now))
    }

    @Test
    fun `цепочка считается живой, пока возвраты не потрачены`() {
        assertFalse(SyncRetry.exhausted(0))
        assertFalse(SyncRetry.exhausted(SyncRetry.MAX_RETURNS - 1))
        assertTrue(SyncRetry.exhausted(SyncRetry.MAX_RETURNS))
    }

    @Test
    fun `поставленная страховка сразу считается потраченной`() {
        assertEquals(1, SyncRetry.spentAfterArming(0))
        assertEquals(2, SyncRetry.spentAfterArming(1))
        assertEquals(3, SyncRetry.spentAfterArming(2))
    }

    @Test
    fun `на исчерпанной цепочке тратить нечего`() {
        assertEquals(SyncRetry.MAX_RETURNS, SyncRetry.spentAfterArming(SyncRetry.MAX_RETURNS))
        assertEquals(99, SyncRetry.spentAfterArming(99))
    }

    @Test
    fun `смерть захода на полпути не открывает вечный стук`() {
        // Заход ставит страховку и умирает, не дойдя до записи исхода, — и так каждый раз.
        // Счётчик обязан расти всё равно, иначе возвраты не кончатся никогда.
        var spent = 0
        var armed = 0
        while (SyncRetry.nextReturnAt(spent, now) != null) {
            spent = SyncRetry.spentAfterArming(spent)
            armed++
        }
        assertEquals(SyncRetry.MAX_RETURNS, armed)
        assertTrue(SyncRetry.exhausted(spent))
    }

    @Test
    fun `вся цепочка укладывается в шесть часов`() {
        var at = now
        var spent = 0
        while (true) {
            at = SyncRetry.nextReturnAt(spent, at) ?: break
            spent++
        }
        assertEquals(SyncRetry.MAX_RETURNS, spent)
        assertEquals(6 * hour, at - now)
    }
}
