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
 * [percentPerSecondTenths] — скорость нарастания в десятых долях процента в секунду:
 * 5 — самое медленное (0,5% в секунду), 30 — самое быстрое (3% в секунду).
 *
 * ⚠️ Окно расширено владельцем 2026-08-25 взамен прежнего «1% в секунду … 1% за
 * 5 секунд»: быстрый край втрое выше — даже 1% в секунду не успевал разогнать
 * звонок до дедлайна бэкапа. Десятые доли нужны ради шага в полпроцента.
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
    val percentPerSecondTenths: Int,
    val vibrate: Boolean,
    val melody: MelodySource,
) {
    init {
        require(startVolumePercent in 0..100) { "громкость вне диапазона: $startVolumePercent" }
        require(percentPerSecondTenths in MIN_PERCENT_PER_SECOND_TENTHS..MAX_PERCENT_PER_SECOND_TENTHS) {
            "скорость нарастания вне диапазона: $percentPerSecondTenths"
        }
    }

    companion object {
        const val MIN_PERCENT_PER_SECOND_TENTHS = 5
        const val MAX_PERCENT_PER_SECOND_TENTHS = 30

        /** Шаг ползунка — полпроцента в секунду: 0,5 / 1 / 1,5 / 2 / 2,5 / 3. */
        const val PERCENT_PER_SECOND_STEP_TENTHS = 5

        val DEFAULT = SoundSettings(
            enabled = true,
            startVolumePercent = 20,
            percentPerSecondTenths = 15,
            vibrate = true,
            melody = MelodySource.SystemAlarm,
        )
    }
}
