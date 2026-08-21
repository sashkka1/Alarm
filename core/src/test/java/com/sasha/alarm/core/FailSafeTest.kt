package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailSafeTest {

    private val start = 1_000_000L

    @Test
    fun `дедлайн считается от момента старта тревоги`() {
        val run = FailSafe.runFor(start, minutes = 5)
        assertEquals(start, run.startedAtMillis)
        assertEquals(start + 300_000L, run.deadlineMillis)
    }

    @Test
    fun `минуты зажимаются в допустимые границы`() {
        assertEquals(FailSafe.timeoutMillis(FailSafe.MIN_MINUTES), FailSafe.timeoutMillis(0))
        assertEquals(FailSafe.timeoutMillis(FailSafe.MAX_MINUTES), FailSafe.timeoutMillis(99))
    }

    @Test
    fun `до дедлайна тревога жива`() {
        val run = FailSafe.runFor(start, minutes = 1)
        assertFalse(FailSafe.expired(run, start + 59_999L))
    }

    @Test
    fun `ровно в дедлайн тревога снимается`() {
        val run = FailSafe.runFor(start, minutes = 1)
        assertTrue(FailSafe.expired(run, start + 60_000L))
    }

    @Test
    fun `телефон проспал дедлайн - тревога всё равно считается истёкшей`() {
        val run = FailSafe.runFor(start, minutes = 1)
        assertTrue(FailSafe.expired(run, start + 10 * 60_000L))
    }

    @Test
    fun `перевод часов назад снимает блокировку, а не продлевает её`() {
        val run = FailSafe.runFor(start, minutes = 5)
        assertTrue(FailSafe.expired(run, start - 1))
        assertTrue(FailSafe.expired(run, start - 10 * 60_000L))
    }

    @Test
    fun `остаток не уходит в минус`() {
        val run = FailSafe.runFor(start, minutes = 1)
        assertEquals(0L, FailSafe.remainingMillis(run, start + 120_000L))
        assertEquals(30_000L, FailSafe.remainingMillis(run, start + 30_000L))
    }
}
