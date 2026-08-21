package com.sasha.alarm.core

/**
 * Откуда берётся мелодия.
 *
 * Выбранный владельцем файл **копируется внутрь приложения** и дальше играется
 * оттуда: иначе будильник замолчит в тот день, когда файл переименуют, перенесут
 * или почистят папку загрузок.
 */
sealed interface MelodySource {

    /** Системный рингтон будильника. Работает, пока свой файл не выбран. */
    data object SystemAlarm : MelodySource

    /** Файл в личной папке приложения. [fileName] — имя внутри неё. */
    data class Stored(val fileName: String, val displayName: String) : MelodySource
}

/**
 * Настройки звонка.
 *
 * [secondsPerPercent] — скорость нарастания: сколько секунд уходит на +1% громкости.
 * 1 — самое быстрое (1% в секунду), 5 — самое медленное (1% за 5 секунд).
 */
data class SoundSettings(
    /**
     * Играть ли мелодию вообще.
     *
     * Выключается, чтобы проверять экран, блокировку и дедлайн бэкапа, не оглушая
     * себя каждые несколько минут. Расчёт громкости при этом продолжается и виден
     * на экране — просто в тишине.
     */
    val enabled: Boolean,
    val startVolumePercent: Int,
    val secondsPerPercent: Int,
    val vibrate: Boolean,
    val melody: MelodySource,
) {
    init {
        require(startVolumePercent in 0..100) { "громкость вне диапазона: $startVolumePercent" }
        require(secondsPerPercent in MIN_SECONDS_PER_PERCENT..MAX_SECONDS_PER_PERCENT) {
            "скорость нарастания вне диапазона: $secondsPerPercent"
        }
    }

    companion object {
        const val MIN_SECONDS_PER_PERCENT = 1
        const val MAX_SECONDS_PER_PERCENT = 5

        val DEFAULT = SoundSettings(
            enabled = true,
            startVolumePercent = 20,
            secondsPerPercent = 3,
            vibrate = true,
            melody = MelodySource.SystemAlarm,
        )
    }
}
