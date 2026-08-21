package com.sasha.alarm.core

/**
 * Действие в примере.
 *
 * Умножение и деление убраны (решение владельца 2026-08-14): в уме спросонья они
 * либо решаются автоматически по таблице, либо намертво стопорят.
 */
enum class MathOperation { PLUS, MINUS }

/** Один пример: `left ⋅ right = answer`. */
data class MathTask(
    val operation: MathOperation,
    val left: Int,
    val right: Int,
    val answer: Int,
)

/**
 * Настройки задания, которое заменяет кнопку «Выключить».
 */
data class MathSettings(
    val operations: Set<MathOperation>,
    val count: Int,
    /** Границы операндов. */
    val min: Int,
    val max: Int,
) {
    /** Хотя бы одно действие должно остаться включённым, иначе примеры не из чего строить. */
    val effectiveOperations: Set<MathOperation>
        get() = operations.ifEmpty { setOf(MathOperation.PLUS) }

    /** Границы с гарантией «нижняя не выше верхней» — ползунок может прийти вывернутым. */
    val range: IntRange get() = minOf(min, max)..maxOf(min, max)

    companion object {
        const val MIN_COUNT = 10
        const val MAX_COUNT = 35

        const val BOUND_MIN = 1
        const val BOUND_MAX = 50

        val DEFAULT = MathSettings(
            operations = setOf(MathOperation.PLUS, MathOperation.MINUS),
            count = 10,
            min = 1,
            max = 50,
        )
    }
}

/** Ход решения: список примеров и сколько уже решено. */
data class MathSession(
    val tasks: List<MathTask>,
    val solved: Int,
) {
    val current: MathTask? get() = tasks.getOrNull(solved)
    val isComplete: Boolean get() = solved >= tasks.size
    val total: Int get() = tasks.size

    companion object {
        val EMPTY = MathSession(emptyList(), 0)
    }
}

object MathRules {

    /** Ответ введён верно? Пустая строка и мусор — всегда нет. */
    fun isCorrect(task: MathTask, input: String): Boolean {
        val value = input.trim().toIntOrNull() ?: return false
        return value == task.answer
    }

    /**
     * Принять ответ.
     *
     * Ошибка ничего не сбрасывает и не наказывает — только подсветка
     * (решение владельца 2026-08-14).
     */
    fun submit(session: MathSession, input: String): MathSession {
        val task = session.current ?: return session
        return if (isCorrect(task, input)) session.copy(solved = session.solved + 1) else session
    }
}
