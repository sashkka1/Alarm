package com.sasha.alarm.core

/**
 * Метка, которую владелец приложил к телефону и приложение запомнило.
 *
 * Номер — единственное, что владелец видит и чем метка названа в маршруте: его же
 * он подписывает на самой наклейке. Поэтому номера **не переприсваиваются** при
 * удалении: убрали вторую — третья остаётся третьей, иначе подписи на метках
 * разъедутся с тем, что показывает экран.
 */
data class NfcTag(
    val number: Int,
    /** Идентификатор железа в hex — то, что отдаёт считыватель. Владельцу не показывается. */
    val id: String,
)

/**
 * Испытание «обойти метки»: какие метки известны и в каком порядке их касаться.
 */
data class NfcSettings(
    val tags: List<NfcTag>,
    /**
     * Маршрут — номера меток по порядку прохождения.
     *
     * Повторы разрешены намеренно: `2, 1, 3, 2` — это четыре касания, и вторая
     * метка встречается дважды.
     */
    val route: List<Int>,
) {
    /** Есть ли что проходить. Пустой маршрут — испытания нет. */
    val ready: Boolean get() = route.isNotEmpty()

    fun tagFor(number: Int): NfcTag? = tags.firstOrNull { it.number == number }

    companion object {
        val DEFAULT = NfcSettings(tags = emptyList(), route = emptyList())

        const val MAX_TAGS = 20
        const val MAX_ROUTE = 30
    }
}

/**
 * Ход прохождения маршрута во время тревоги.
 *
 * Живёт отдельно от [NfcSettings]: настройки — то, что владелец собрал вечером,
 * а это — сколько шагов пройдено сейчас.
 */
data class NfcRun(
    val route: List<Int>,
    val done: Int,
    /** Что и когда зачли в прошлый раз — только для защиты от двойного зачёта. */
    val lastId: String?,
    val lastAtMillis: Long,
) {
    /** Номер метки, которую ждём следующей. */
    val expected: Int? get() = route.getOrNull(done)

    val isComplete: Boolean get() = done >= route.size

    val total: Int get() = route.size

    companion object {
        fun of(settings: NfcSettings) = NfcRun(
            route = settings.route,
            done = 0,
            lastId = null,
            lastAtMillis = 0L,
        )
    }
}

/**
 * Чем кончилось прикладывание метки.
 *
 * Метки физически одинаковые и ничем не подписаны, поэтому «ничего не произошло» —
 * негодный ответ: по нему не понять, промахнулся ли дверью, приложил ли чужую метку
 * или просто не оторвал телефон. Приложение знает про метку всё, что нужно, и обязано
 * это сказать.
 */
sealed interface NfcScan {
    /** Та самая: шаг засчитан. */
    data class Right(val number: Int) : NfcScan

    /** Знакомая метка, но не та по порядку. */
    data class Wrong(val number: Int, val expected: Int) : NfcScan

    /** Такой метки в списке нет вовсе. */
    data object Unknown : NfcScan

    /** Та самая, но телефон не отрывали от неё — второй шаг подряд так не закрыть. */
    data class TooSoon(val number: Int) : NfcScan
}

/** Новое состояние маршрута и то, что сказать про касание. [outcome] пуст, если маршрут уже пройден. */
data class NfcScanResult(val run: NfcRun, val outcome: NfcScan?)

object NfcRules {

    /**
     * Сколько миллисекунд одна и та же метка не может закрыть второй шаг подряд.
     *
     * Считыватель выдаёт одно прикладывание пачкой событий, а маршрут вида `2 → 2`
     * без этой паузы прошёлся бы одним касанием. Значение подобрано так, чтобы
     * поднять телефон и приложить снова было заведомо дольше.
     */
    const val SAME_TAG_COOLDOWN_MS = 1_500L

    /** Номер, который получит следующая зарегистрированная метка. */
    fun nextNumber(settings: NfcSettings): Int =
        (settings.tags.maxOfOrNull { it.number } ?: 0) + 1

    /**
     * Запомнить приложенную метку.
     *
     * Уже знакомая метка второго номера не получает — иначе одна наклейка
     * расплодилась бы по списку от каждого случайного касания.
     */
    fun register(settings: NfcSettings, id: String): NfcSettings = when {
        id.isBlank() -> settings
        settings.tags.any { it.id == id } -> settings
        settings.tags.size >= NfcSettings.MAX_TAGS -> settings
        else -> settings.copy(tags = settings.tags + NfcTag(nextNumber(settings), id))
    }

    /** Забыть метку. Из маршрута она уходит вместе со всеми своими шагами. */
    fun forget(settings: NfcSettings, number: Int): NfcSettings = settings.copy(
        tags = settings.tags.filterNot { it.number == number },
        route = settings.route.filterNot { it == number },
    )

    /** Дописать шаг в конец маршрута. Номера несуществующей метки в маршруте не бывает. */
    fun addStep(settings: NfcSettings, number: Int): NfcSettings = when {
        settings.tagFor(number) == null -> settings
        settings.route.size >= NfcSettings.MAX_ROUTE -> settings
        else -> settings.copy(route = settings.route + number)
    }

    fun removeLastStep(settings: NfcSettings): NfcSettings =
        if (settings.route.isEmpty()) settings else settings.copy(route = settings.route.dropLast(1))

    fun clearRoute(settings: NfcSettings): NfcSettings = settings.copy(route = emptyList())

    /**
     * К телефону приложили метку.
     *
     * Шаг засчитывается, только если это ровно та метка, которой ждём: чужая,
     * незнакомая или не та по порядку не делает ничего — ни продвижения, ни отката.
     * Откат намеренно отсутствует: спросонья промахнуться дверью легко, и терять
     * из-за этого весь пройденный маршрут — наказание без смысла.
     */
    fun scan(run: NfcRun, tags: List<NfcTag>, id: String, nowMillis: Long): NfcScanResult {
        val expected = run.expected ?: return NfcScanResult(run, null)
        val tag = tags.firstOrNull { it.id == id }
            ?: return NfcScanResult(run, NfcScan.Unknown)
        if (tag.number != expected) {
            return NfcScanResult(run, NfcScan.Wrong(tag.number, expected))
        }
        if (id == run.lastId && nowMillis - run.lastAtMillis < SAME_TAG_COOLDOWN_MS) {
            return NfcScanResult(run, NfcScan.TooSoon(tag.number))
        }
        return NfcScanResult(
            run = run.copy(done = run.done + 1, lastId = id, lastAtMillis = nowMillis),
            outcome = NfcScan.Right(tag.number),
        )
    }
}
