package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathGeneratorTest {

    private fun settings(
        operations: Set<MathOperation> = MathOperation.entries.toSet(),
        count: Int = 10,
    ) = MathSettings.DEFAULT.copy(operations = operations, count = count)

    @Test
    fun `сколько заказали, столько и примеров`() {
        assertEquals(17, MathGenerator.generate(settings(count = 17), Random(1)).size)
    }

    @Test
    fun `используются только выбранные действия`() {
        val only = setOf(MathOperation.MINUS)
        val tasks = MathGenerator.generate(settings(operations = only, count = 30), Random(2))
        assertTrue(tasks.all { it.operation == MathOperation.MINUS })
    }

    @Test
    fun `ни одного действия не выбрано - берём сложение, а не падаем`() {
        val tasks = MathGenerator.generate(settings(operations = emptySet(), count = 10), Random(3))
        assertTrue(tasks.all { it.operation == MathOperation.PLUS })
    }

    @Test
    fun `нулевого ответа не бывает`() {
        // «31 − 31» решается не думая и выглядит опечаткой (владелец, 2026-08-16).
        // Гоняем много раз с разными зёрнами: ноль ловится не на каждом наборе.
        repeat(60) { seed ->
            MathGenerator.generate(settings(count = 35), Random(seed.toLong())).forEach { task ->
                assertTrue("${task.left} ${task.operation} ${task.right}", task.answer != 0)
            }
        }
    }

    @Test
    fun `ноль не проходит даже когда выбирать не из чего`() {
        // Диапазон схлопнут в одно число: вычитание тут даёт только ноль, и подбор
        // попытками не спасёт. Пример обязан выкрутиться, а не выдать ноль.
        val tight = MathSettings.DEFAULT.copy(
            operations = setOf(MathOperation.MINUS),
            count = 10,
            min = 7,
            max = 7,
        )
        MathGenerator.generate(tight, Random(9)).forEach { task ->
            assertTrue("${task.left} ${task.operation} ${task.right}", task.answer != 0)
        }
    }

    @Test
    fun `ответы всегда сходятся с примером`() {
        MathGenerator.generate(settings(count = 35), Random(4)).forEach { task ->
            val expected = when (task.operation) {
                MathOperation.PLUS -> task.left + task.right
                MathOperation.MINUS -> task.left - task.right
            }
            assertEquals(expected, task.answer)
        }
    }

    @Test
    fun `вычитание никогда не даёт отрицательный ответ`() {
        val tasks = MathGenerator.generate(
            settings(operations = setOf(MathOperation.MINUS), count = 35),
            Random(5),
        )
        assertTrue(tasks.all { it.answer >= 0 })
    }

    @Test
    fun `количество зажимается в допустимые границы`() {
        assertEquals(MathSettings.MAX_COUNT, MathGenerator.generate(settings(count = 500), Random(7)).size)
        assertEquals(MathSettings.MIN_COUNT, MathGenerator.generate(settings(count = 0), Random(8)).size)
    }

    @Test
    fun `операнды не выходят за выбранный диапазон`() {
        val custom = MathSettings.DEFAULT.copy(count = 35, min = 20, max = 30)
        val tasks = MathGenerator.generate(custom, Random(11))
        assertTrue(tasks.all { it.left in 20..30 && it.right in 20..30 })
    }

    @Test
    fun `вывернутый диапазон не ломает генерацию`() {
        val inverted = MathSettings.DEFAULT.copy(count = 20, min = 40, max = 10)
        val tasks = MathGenerator.generate(inverted, Random(12))
        assertTrue(tasks.all { it.left in 10..40 && it.right in 10..40 })
    }

    @Test
    fun `верный ответ двигает счётчик, неверный не трогает`() {
        val tasks = MathGenerator.generate(settings(count = 10), Random(9))
        val session = MathSession(tasks, solved = 0)
        assertEquals(0, MathRules.submit(session, "не число").solved)
        val right = MathRules.submit(session, tasks[0].answer.toString())
        assertEquals(1, right.solved)
        assertFalse(right.isComplete)
    }

    @Test
    fun `решены все примеры - сессия завершена`() {
        val tasks = MathGenerator.generate(settings(count = 10), Random(10))
        var session = MathSession(tasks, solved = 0)
        tasks.forEach { session = MathRules.submit(session, it.answer.toString()) }
        assertTrue(session.isComplete)
    }
}
