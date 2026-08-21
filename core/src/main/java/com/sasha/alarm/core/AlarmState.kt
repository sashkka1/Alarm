package com.sasha.alarm.core

/**
 * Всё, что приложение держит на диске.
 *
 * Читается из двух процессов: основного и сторожа `:guard`, поэтому снимок
 * целиком, а не россыпь ключей — иначе два процесса увидели бы разные половины.
 *
 * ⚠️ Собственного будильника здесь нет намеренно: приложение целиком построено на
 * зацепе за Sleep Cycle, своё расписание оказалось не нужно.
 */
data class AlarmState(
    /**
     * Общий выключатель приложения.
     *
     * Выключен — настоящая тревога не поднимается ничем: зацеп молчит, экран не
     * появляется. Показ из настроек при этом работает.
     */
    val masterEnabled: Boolean,
    /** Через сколько минут экран снимется сам, если задание не выполнено. */
    val failSafeMinutes: Int,
    val sound: SoundSettings,
    /** Чем выключается будильник. */
    val challenge: Challenge,
    val math: MathSettings,
    val reaction: ReactionSettings,
    val pushups: PushupSettings,
    val nfc: NfcSettings,
    val run: AlarmRun?,
    /** Разрешения, состояние которых система прочитать не даёт — владелец отмечает их сам. */
    val manualPermissions: Set<String>,
    /** Владелец уже открывал экран настроек хотя бы раз. */
    val settingsVisited: Boolean,
    /**
     * Когда чужой будильник (Sleep Cycle) начал звонить, по данным слушателя уведомлений.
     * null — не звонит. Лежит на диске, потому что слушателя система может перезапустить.
     */
    val foreignRingingSinceMillis: Long?,
    /** Через сколько секунд после загрузки телефона поднимать прерванную тревогу. */
    val resumeDelaySeconds: Int,
) {
    val isRinging: Boolean get() = run != null

    companion object {
        val DEFAULT = AlarmState(
            masterEnabled = true,
            failSafeMinutes = FailSafe.DEFAULT_MINUTES,
            sound = SoundSettings.DEFAULT,
            challenge = Challenge.MATH,
            math = MathSettings.DEFAULT,
            reaction = ReactionSettings.DEFAULT,
            pushups = PushupSettings.DEFAULT,
            nfc = NfcSettings.DEFAULT,
            run = null,
            manualPermissions = emptySet(),
            settingsVisited = false,
            foreignRingingSinceMillis = null,
            resumeDelaySeconds = MIN_RESUME_DELAY_SECONDS,
        )

        const val MIN_RESUME_DELAY_SECONDS = 1
        const val MAX_RESUME_DELAY_SECONDS = 10
    }
}
