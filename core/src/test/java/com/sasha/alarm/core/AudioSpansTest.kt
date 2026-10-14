package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSpansTest {

    private fun started(at: Long) = AudioSpans.Event(at, AudioSpans.Mark.STARTED)
    private fun stopped(at: Long) = AudioSpans.Event(at, AudioSpans.Mark.STOPPED)

    @Test
    fun `пара запуск-остановка даёт отрезок`() {
        val span = AudioSpans.of(listOf(started(1_000), stopped(5_000))).single()
        assertEquals(1_000L, span.from)
        assertEquals(5_000L, span.to)
        assertEquals(4_000L, span.durationMs)
    }

    @Test
    fun `незакрытый отрезок не отдаётся`() {
        // 🔴 Окно снимка кончается «сейчас». Отдай мы играющую музыку с концом по краю
        // окна — на следующем снимке тот же отрезок пришёл бы с другим концом, и в
        // журнале осталось бы два неверных вместо одного верного.
        assertTrue(AudioSpans.of(listOf(started(1_000))).isEmpty())
    }

    @Test
    fun `два запуска подряд — это перезапуск, начало прежнее`() {
        val span = AudioSpans.of(listOf(started(1_000), started(2_000), stopped(5_000))).single()
        assertEquals(1_000L, span.from)
    }

    @Test
    fun `остановка без запуска ничего не создаёт`() {
        assertTrue(AudioSpans.of(listOf(stopped(5_000))).isEmpty())
    }

    @Test
    fun `несколько сеансов за вечер идут отдельными отрезками`() {
        val spans = AudioSpans.of(
            listOf(started(1_000), stopped(2_000), started(3_000), stopped(9_000))
        )
        assertEquals(2, spans.size)
        assertEquals(3_000L, spans[1].from)
    }

    @Test
    fun `события разбираются независимо от порядка в списке`() {
        // Система отдаёт историю по времени, но полагаться на это правило не обязано.
        val spans = AudioSpans.of(listOf(stopped(5_000), started(1_000)))
        assertEquals(1, spans.size)
    }

    @Test
    fun `мгновенный отрезок отбрасывается`() {
        assertTrue(AudioSpans.of(listOf(started(1_000), stopped(1_000))).isEmpty())
    }

    @Test
    fun `пустой список ничего не даёт`() {
        assertTrue(AudioSpans.of(emptyList()).isEmpty())
    }
}
