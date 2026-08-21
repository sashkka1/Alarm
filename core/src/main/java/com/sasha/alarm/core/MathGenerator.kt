package com.sasha.alarm.core

import kotlin.random.Random

/**
 * Сборка набора примеров на раунд.
 *
 * Правила взяты из GoodMathScore: вычитание переставляет операнды, чтобы ответ был
 * неотрицательным, а примеры в раунде не повторяются.
 *
 * Случайность приходит снаружи ([Random]), поэтому набор воспроизводим в тестах.
 */
object MathGenerator {

    /** Сколько раз пробуем подобрать неповторяющийся пример, прежде чем взять какой есть. */
    private const val UNIQUE_ATTEMPTS = 40

    fun generate(settings: MathSettings, random: Random): List<MathTask> {
        val operations = settings.effectiveOperations.toList()
        val count = settings.count.coerceIn(MathSettings.MIN_COUNT, MathSettings.MAX_COUNT)
        val tasks = mutableListOf<MathTask>()

        repeat(count) {
            var candidate = single(operations.random(random), settings, random)
            var attempt = 0
            while (attempt < UNIQUE_ATTEMPTS && !candidate.acceptable(tasks)) {
                candidate = single(operations.random(random), settings, random)
                attempt++
            }
            tasks += candidate.fixZero(settings)
        }
        return tasks
    }

    /** Годится ли пример: не повторяет уже набранные и не даёт нулевого ответа. */
    private fun MathTask.acceptable(taken: List<MathTask>): Boolean =
        answer != 0 && taken.none { it.isSameAs(this) }

    /**
     * Последняя страховка от нулевого ответа.
     *
     * Ноль запрещён (владелец, 2026-08-16): «31 − 31» выглядит как опечатка, а не
     * как задание, и решается не думая. Подбор попытками может не успеть — например,
     * когда диапазон схлопнут в одно число. Тогда уменьшаем вычитаемое на единицу,
     * а если и это невозможно, превращаем пример в сложение: ответ не ноль в любом
     * случае, потому что операнды всегда положительные.
     */
    private fun MathTask.fixZero(settings: MathSettings): MathTask = when {
        answer != 0 -> this
        right > settings.range.first -> copy(right = right - 1, answer = left - (right - 1))
        else -> MathTask(MathOperation.PLUS, left, right, left + right)
    }

    private fun MathTask.isSameAs(other: MathTask): Boolean =
        operation == other.operation && left == other.left && right == other.right

    private fun single(
        operation: MathOperation,
        settings: MathSettings,
        random: Random,
    ): MathTask {
        val a = operand(settings, random)
        val b = operand(settings, random)
        return when (operation) {
            MathOperation.PLUS -> MathTask(operation, a, b, a + b)
            MathOperation.MINUS -> {
                // Большее всегда первым: отрицательных ответов спросонья не хочется.
                val left = maxOf(a, b)
                val right = minOf(a, b)
                MathTask(operation, left, right, left - right)
            }
        }
    }

    private fun operand(settings: MathSettings, random: Random): Int =
        settings.range.let { random.nextInt(it.first, it.last + 1) }
}
