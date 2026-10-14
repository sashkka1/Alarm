package com.sasha.alarm.core

/**
 * Зацеп за стандартный будильник — часы Xiaomi (`com.android.deskclock`).
 *
 * Второй источник наряду с Sleep Cycle (решение владельца 2026-09-30: «надоел Sleep Cycle,
 * не всегда работает»; оба зацепа остаются). Схема та же: чужой зазвонил — гасим его
 * сами, сразу, и по снятию его уведомления поднимаем свой экран. Выключил владелец
 * руками раньше нас — экран поднимается точно так же.
 *
 * Что подтверждено дампом живого звонка 2026-09-30 (часы 17.45.0, HyperOS 3):
 *  - на время звонка висит уведомление `id=1` на канале [ALARM_CHANNEL], службы переднего
 *    плана, с полноэкранным интентом и двумя кнопками — «Стоп» и «Повтор»;
 *  - **таймер звонит тем же экраном, но на другом канале** — `channel_id_deskclock_timer`;
 *    таймеры наш экран не поднимают (решение владельца 2026-09-30);
 *  - выключение — уведомление снимается, свайп вверх на его экране делает то же, что «Стоп».
 *
 * Своего «звонил ли он» на диске здесь нет, в отличие от Sleep Cycle: уведомление звонка
 * живёт ровно пока он звонит, поэтому его снятие само доказывает, что звонок был.
 */
object DeskClockAlarm {

    const val PACKAGE = "com.android.deskclock"

    /** Канал уведомления звонка будильника. Таймер — на `channel_id_deskclock_timer`. */
    const val ALARM_CHANNEL = "channel_id_deskclock_alarm"

    /**
     * Сколько после конца звонка звук по каналу будильника ещё считается звуком часов.
     *
     * Звук обезличен (с Android 9 чужой плеер не называет пакет), и без этого окна звук
     * часов засчитывался бы звонком Sleep Cycle: остановка его отслеживания в ближайший
     * час подняла бы второй экран поверх уже пройденного утра.
     */
    const val AUDIO_TAIL_MS = 10_000L

    /** Подписи кнопки, которая выключает звонок. «Повтор» (отложить) не нажимаем никогда. */
    private val STOP_TITLES = setOf("стоп", "stop", "dismiss")

    /**
     * Это уведомление — звонящий будильник, а не таймер, не «скоро будильник» и не
     * «отложен»: только звонок держит службу переднего плана на канале будильника.
     */
    fun isRinging(channelId: String?, foregroundService: Boolean): Boolean =
        channelId == ALARM_CHANNEL && foregroundService

    /** Какую из кнопок уведомления нажать, чтобы выключить звонок; null — такой нет. */
    fun stopActionIndex(titles: List<String?>): Int? {
        val index = titles.indexOfFirst { it?.trim()?.lowercase() in STOP_TITLES }
        return index.takeIf { it >= 0 }
    }

    /**
     * Звук по каналу будильника — это часы, а не Sleep Cycle?
     *
     * @param ringing       уведомление звонка часов висит прямо сейчас
     * @param ringEndedAt   когда оно последний раз снялось (null — не снималось)
     */
    fun isItsAudio(ringing: Boolean, ringEndedAt: Long?, nowMillis: Long): Boolean {
        if (ringing) return true
        val ended = ringEndedAt ?: return false
        val age = nowMillis - ended
        return age in 0..AUDIO_TAIL_MS
    }
}
