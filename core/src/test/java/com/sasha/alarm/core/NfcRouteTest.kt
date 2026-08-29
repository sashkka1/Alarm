package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcRouteTest {

    private val three = NfcSettings(
        tags = listOf(NfcTag(1, "AA"), NfcTag(2, "BB"), NfcTag(3, "CC")),
        route = emptyList(),
    )

    // ──────────────────────────── регистрация меток ────────────────────────────

    @Test
    fun `метки нумеруются по порядку прикладывания начиная с нуля`() {
        // Нумерация с нуля — решение владельца 2026-08-25: нулевая метка уличная,
        // значит первая же зарегистрированная получает ноль и вешается у двери.
        var settings = NfcSettings.DEFAULT
        settings = NfcRules.register(settings, "AA")
        settings = NfcRules.register(settings, "BB")
        assertEquals(listOf(NfcRules.STREET_NUMBER, 1), settings.tags.map { it.number })
        assertEquals(listOf("AA", "BB"), settings.tags.map { it.id })
    }

    @Test
    fun `первая метка становится уличной`() {
        val settings = NfcRules.register(NfcSettings.DEFAULT, "AA")
        assertEquals(NfcRules.STREET_NUMBER, settings.tags.single().number)
    }

    @Test
    fun `удаление уличной метки не сдвигает остальные`() {
        // Номера подписаны на самих наклейках — переприсваивать их нельзя.
        var settings = NfcSettings.DEFAULT
        repeat(3) { i -> settings = NfcRules.register(settings, "T$i") }
        settings = NfcRules.forget(settings, NfcRules.STREET_NUMBER)
        assertEquals(listOf(1, 2), settings.tags.map { it.number })
        assertEquals(3, NfcRules.nextNumber(settings))
    }

    @Test
    fun `знакомая метка второго номера не получает`() {
        var settings = NfcRules.register(NfcSettings.DEFAULT, "AA")
        settings = NfcRules.register(settings, "AA")
        assertEquals(1, settings.tags.size)
    }

    @Test
    fun `пустой идентификатор не регистрируется`() {
        assertEquals(0, NfcRules.register(NfcSettings.DEFAULT, "  ").tags.size)
    }

    @Test
    fun `удалённая метка не отдаёт свой номер следующей`() {
        // Иначе подпись на наклейке разошлась бы с тем, что показывает экран.
        val afterForget = NfcRules.forget(three, 2)
        assertEquals(4, NfcRules.nextNumber(afterForget))
        assertEquals(listOf(1, 3), afterForget.tags.map { it.number })
    }

    @Test
    fun `удаление метки вычищает её шаги из маршрута`() {
        val withRoute = three.copy(route = listOf(2, 1, 3, 2))
        assertEquals(listOf(1, 3), NfcRules.forget(withRoute, 2).route)
    }

    // ──────────────────────────── сборка маршрута ────────────────────────────

    @Test
    fun `маршрут собирается в том порядке, в котором нажимали`() {
        var settings = three
        listOf(2, 1, 3, 2).forEach { settings = NfcRules.addStep(settings, it) }
        assertEquals(listOf(2, 1, 3, 2), settings.route)
    }

    @Test
    fun `маршрут из одной метки допустим`() {
        assertTrue(NfcRules.addStep(three, 2).ready)
    }

    @Test
    fun `несуществующую метку в маршрут не поставить`() {
        assertEquals(emptyList<Int>(), NfcRules.addStep(three, 9).route)
    }

    @Test
    fun `маршрут не растёт дальше предела`() {
        var settings = three
        repeat(NfcSettings.MAX_ROUTE + 5) { settings = NfcRules.addStep(settings, 1) }
        assertEquals(NfcSettings.MAX_ROUTE, settings.route.size)
    }

    @Test
    fun `последний шаг снимается, пустой маршрут снимать нечего`() {
        val settings = three.copy(route = listOf(2, 1))
        assertEquals(listOf(2), NfcRules.removeLastStep(settings).route)
        assertEquals(emptyList<Int>(), NfcRules.removeLastStep(NfcRules.clearRoute(settings)).route)
    }

    @Test
    fun `пустой маршрут не считается готовым`() {
        assertFalse(NfcSettings.DEFAULT.ready)
    }

    // ──────────────────────────── прохождение ────────────────────────────

    @Test
    fun `маршрут из двух меток проходится двумя касаниями`() {
        var run = NfcRun.of(three.copy(route = listOf(2, 1)))
        assertEquals(2, run.expected)
        run = NfcRules.scan(run, three.tags, "BB", 1_000L).run
        assertEquals(1, run.expected)
        run = NfcRules.scan(run, three.tags, "AA", 2_000L).run
        assertTrue(run.isComplete)
        assertNull(run.expected)
    }

    @Test
    fun `верное касание называет номер метки`() {
        val run = NfcRun.of(three.copy(route = listOf(2)))
        assertEquals(NfcScan.Right(2), NfcRules.scan(run, three.tags, "BB", 1_000L).outcome)
    }

    @Test
    fun `не та метка не двигает маршрут и говорит, какая это была`() {
        // Метки физически одинаковые: без номера в ответе не понять, что приложили.
        var run = NfcRun.of(three.copy(route = listOf(2, 1)))
        run = NfcRules.scan(run, three.tags, "BB", 1_000L).run

        val result = NfcRules.scan(run, three.tags, "CC", 2_000L)
        assertEquals(NfcScan.Wrong(number = 3, expected = 1), result.outcome)
        assertEquals(1, result.run.done)
        assertEquals(1, result.run.expected)
    }

    @Test
    fun `незнакомая метка так и называется`() {
        val run = NfcRun.of(three.copy(route = listOf(2)))
        val result = NfcRules.scan(run, three.tags, "ZZ", 1_000L)
        assertEquals(NfcScan.Unknown, result.outcome)
        assertEquals(0, result.run.done)
    }

    @Test
    fun `одно прикладывание не закрывает два одинаковых шага подряд`() {
        // Считыватель выдаёт одно касание пачкой событий; без паузы маршрут «2 → 2»
        // прошёлся бы, не отрывая телефона от метки.
        var run = NfcRun.of(three.copy(route = listOf(2, 2)))
        run = NfcRules.scan(run, three.tags, "BB", 1_000L).run

        val tooSoon = NfcRules.scan(run, three.tags, "BB", 1_000L + NfcRules.SAME_TAG_COOLDOWN_MS - 1)
        assertEquals(NfcScan.TooSoon(2), tooSoon.outcome)
        assertEquals(1, tooSoon.run.done)

        run = NfcRules.scan(run, three.tags, "BB", 1_000L + NfcRules.SAME_TAG_COOLDOWN_MS).run
        assertTrue(run.isComplete)
    }

    @Test
    fun `пауза не мешает пройти две разные метки подряд`() {
        var run = NfcRun.of(three.copy(route = listOf(2, 1)))
        run = NfcRules.scan(run, three.tags, "BB", 1_000L).run
        run = NfcRules.scan(run, three.tags, "AA", 1_010L).run
        assertTrue(run.isComplete)
    }

    @Test
    fun `пройденный маршрут больше ничего не принимает и ничего не говорит`() {
        var run = NfcRun.of(three.copy(route = listOf(1)))
        run = NfcRules.scan(run, three.tags, "AA", 1_000L).run

        val result = NfcRules.scan(run, three.tags, "AA", 9_000L)
        assertEquals(1, result.run.done)
        assertNull(result.outcome)
    }

    @Test
    fun `длинный маршрут с повторами проходится целиком`() {
        val route = listOf(2, 1, 3, 2, 3)
        var run = NfcRun.of(three.copy(route = route))
        var now = 0L
        route.forEach { number ->
            now += NfcRules.SAME_TAG_COOLDOWN_MS * 2
            val id = three.tagFor(number)?.id ?: error("метки $number нет")
            val result = NfcRules.scan(run, three.tags, id, now)
            assertEquals(NfcScan.Right(number), result.outcome)
            run = result.run
        }
        assertTrue(run.isComplete)
        assertEquals(5, run.total)
    }
}
