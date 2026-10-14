package com.sasha.alarm.core

/**
 * Всё, что приложение держит на диске.
 *
 * Читается из двух процессов: основного и сторожа `:guard`, поэтому снимок
 * целиком, а не россыпь ключей — иначе два процесса увидели бы разные половины.
 *
 * ⚠️ Собственного будильника здесь нет намеренно: приложение целиком построено на
 * зацепе за чужой будильник (Sleep Cycle, стандартные часы), своё расписание оказалось не нужно.
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
    /**
     * Когда владелец последний раз прошёл испытание настоящей тревоги (не показа из
     * настроек). По нему второй чужой будильник того же утра наш экран не поднимает
     * ([ForeignAlarmRule.raisesScreen]). На диске — между будильниками процесс умирает.
     */
    val challengePassedAtMillis: Long?,
    /**
     * С какого момента зацеп не работает и телефон не смог его поднять ([HookHealth]).
     * null — работает. На диске — чтобы отрезок писался в журнал один раз, а не при
     * каждой проверке, даже если процесс между проверками умирал.
     */
    val hookDownSinceMillis: Long?,
    /**
     * Когда слушатель нашёлся отключённым, а поломка ещё не объявлена: телефон толкнул
     * систему и ждёт повторной проверки ([HookHealth.RECHECK_MS]). На диске — процесс может
     * умереть и между этими двумя проверками.
     */
    val hookSuspectSinceMillis: Long?,
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
            challengePassedAtMillis = null,
            hookDownSinceMillis = null,
            hookSuspectSinceMillis = null,
        )

        const val MIN_RESUME_DELAY_SECONDS = 1
        const val MAX_RESUME_DELAY_SECONDS = 10
    }
}
