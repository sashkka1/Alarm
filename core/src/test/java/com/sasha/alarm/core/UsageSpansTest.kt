package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSpansTest {

    private val min = 60_000L

    private fun unlocked(minutes: Long) = UsageSpans.Event(minutes * min, UsageSpans.Mark.UNLOCKED)
    private fun locked(minutes: Long) = UsageSpans.Event(minutes * min, UsageSpans.Mark.LOCKED)
    private fun screenOff(minutes: Long) = UsageSpans.Event(minutes * min, UsageSpans.Mark.SCREEN_OFF)

    @Test
    fun `сеанс идёт от снятия замка до его возврата`() {
        val spans = UsageSpans.of(listOf(unlocked(10), locked(40)))
        assertEquals(1, spans.size)
        assertEquals(10 * min, spans[0].from)
        assertEquals(40 * min, spans[0].to)
    }

    @Test
    fun `сплошной сеанс остаётся одним отрезком, сколько бы приложений внутри ни сменилось`() {
        // Ради этого правило и переписано (владелец, 2026-08-29): прежний способ считал
        // по приложению на переднем плане и рвал 33 минуты экрана на 23,7 минуты отрезков.
        // Здесь событий между снятием и возвратом замка нет вовсе — и рвать нечего.
        val spans = UsageSpans.of(listOf(unlocked(0), locked(33)))
        assertEquals(1, spans.size)
        assertEquals(33 * min, spans[0].to - spans[0].from)
    }

    @Test
    fun `погасший экран закрывает сеанс не хуже замка`() {
        // Без защищённого замка или при умной разблокировке LOCKED может не прийти вовсе.
        val spans = UsageSpans.of(listOf(unlocked(5), screenOff(20)))
        assertEquals(1, spans.size)
        assertEquals(15 * min, spans[0].to - spans[0].from)
    }

    @Test
    fun `незакрытый сеанс не возвращается`() {
        // Телефон в руках прямо сейчас: записать его концом «сейчас» значило бы получить
        // два наложенных отрезка на следующем снимке.
        assertTrue(UsageSpans.of(listOf(unlocked(5))).isEmpty())
    }

    @Test
    fun `короткая вспышка экрана пользованием не считается`() {
        val spans = UsageSpans.of(
            listOf(
                UsageSpans.Event(0, UsageSpans.Mark.UNLOCKED),
                UsageSpans.Event(20_000, UsageSpans.Mark.LOCKED),
            )
        )
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `заперся и тут же открыл обратно — это один сеанс, а не два`() {
        val spans = UsageSpans.of(
            listOf(
                unlocked(0),
                locked(10),
                UsageSpans.Event(10 * min + 20_000, UsageSpans.Mark.UNLOCKED),
                locked(25),
            )
        )
        assertEquals(1, spans.size)
        assertEquals(0L, spans[0].from)
        assertEquals(25 * min, spans[0].to)
    }

    @Test
    fun `разрыв длиннее склейки оставляет два сеанса`() {
        val spans = UsageSpans.of(listOf(unlocked(0), locked(10), unlocked(30), locked(45)))
        assertEquals(2, spans.size)
        assertEquals(10 * min, spans[0].to)
        assertEquals(30 * min, spans[1].from)
    }

    @Test
    fun `повторное снятие замка без возврата сеанс не удлиняет`() {
        // Система шлёт KEYGUARD_HIDDEN не по одному разу; открывающим считается первый.
        val spans = UsageSpans.of(listOf(unlocked(0), unlocked(3), locked(10)))
        assertEquals(1, spans.size)
        assertEquals(0L, spans[0].from)
        assertEquals(10 * min, spans[0].to)
    }

    @Test
    fun `возврат замка без снятия ничего не открывает`() {
        assertTrue(UsageSpans.of(listOf(locked(5), screenOff(6))).isEmpty())
    }

    @Test
    fun `события разбираются по времени, а не по порядку в списке`() {
        val spans = UsageSpans.of(listOf(locked(40), unlocked(10)))
        assertEquals(1, spans.size)
        assertEquals(10 * min, spans[0].from)
    }

    @Test
    fun `пустой список даёт пустой ответ`() {
        assertTrue(UsageSpans.of(emptyList()).isEmpty())
    }
}
