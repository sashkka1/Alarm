package com.sasha.alarm.core

/**
 * Зацеп за чужой будильник.
 *
 * Сигнал — не звонок, а **выключение**: Sleep Cycle звонит своей мелодией, владелец
 * просыпается и выключает его сам, и вот этот момент поднимает наш экран.
 *
 * Почему так, а не «заглушить чужой и заиграть свой»: у уведомления Sleep Cycle нет
 * ни одной кнопки, нажать за владельца нечего, а глушить звук — значит рисковать
 * тишиной вместо будильника. Здесь чужой звук не трогаем вообще.
 *
 * Проверка «а звонил ли он» обязательна: без неё мы сработали бы и вечером, когда
 * владелец просто останавливает отслеживание сна, не дожидаясь будильника.
 */
object ForeignAlarmRule {

    /**
     * Сколько «звонил» остаётся свежим фактом.
     *
     * Уведомление могло исчезнуть спустя часы и по совершенно другой причине —
     * перезапуск приложения, чистка памяти. Такое выключением не считаем.
     */
    const val RINGING_MEMORY_MS = 60 * 60 * 1000L

    /**
     * Уведомление чужого будильника появилось или обновилось.
     *
     * @param ringing текст уведомления говорит, что будильник сейчас звонит
     * @param since   когда он зазвонил по нашим данным (null — не звонил)
     * @return новое значение [since]
     */
    fun onNotification(ringing: Boolean, nowMillis: Long, since: Long?): Long? = when {
        !ringing -> null
        since == null -> nowMillis
        // 🔴 Отметка старше памяти — это не этот звонок, а забытый прошлый (ловушка,
        // найденная 2026-09-30). Раньше она держалась вечно: вечерний звук таймера
        // взводил её, утренний звонок её не обновлял, и снятие Sleep Cycle уже не
        // считалось выключением — чужой смахнут, а наш экран не поднят.
        !isFresh(since, nowMillis) -> nowMillis
        else -> since
    }

    /**
     * Уведомление чужого будильника пропало.
     *
     * @return true, если это именно выключение звонящего будильника — то есть наш сигнал.
     */
    fun isDismissal(since: Long?, nowMillis: Long): Boolean {
        val ringingSince = since ?: return false
        return isFresh(ringingSince, nowMillis)
    }

    /**
     * Сколько после пройденного испытания второй чужой будильник наш экран не поднимает.
     *
     * Владелец, 2026-09-30: «сработает и Sleep Cycle, и часы — нет, мне нужен только один
     * наш экран». Считается от пройденного испытания, а не от подъёма экрана: снятый
     * сторожем по дедлайну экран ничего не доказывает — владелец мог и не проснуться,
     * и тогда второй будильник поднимает экран снова.
     */
    const val ONE_SCREEN_MS = 60 * 60 * 1000L

    /** Владелец недавно прошёл испытание настоящей тревоги — значит, точно проснулся. */
    fun wokeRecently(challengePassedAt: Long?, nowMillis: Long): Boolean {
        val passed = challengePassedAt ?: return false
        val age = nowMillis - passed
        return age >= 0 && age <= ONE_SCREEN_MS
    }

    /**
     * Чужой будильник выключен — поднимать ли наш экран.
     *
     * Наша тревога уже идёт — нет: второй подъём перезапустил бы её с полным дедлайном.
     */
    fun raisesScreen(ourAlarmActive: Boolean, challengePassedAt: Long?, nowMillis: Long): Boolean =
        !ourAlarmActive && !wokeRecently(challengePassedAt, nowMillis)

    /**
     * Гасить ли звонок чужого будильника самим.
     *
     * Пока идёт наша тревога — да: будит она, а второй звонок поверх только мешает. После
     * пройденного испытания — нет: пусть звонит, выключит владелец (решение 2026-09-30:
     * сознательный второй будильник не должен пропасть молча).
     */
    fun silencesForeign(ourAlarmActive: Boolean, challengePassedAt: Long?, nowMillis: Long): Boolean =
        ourAlarmActive || !wokeRecently(challengePassedAt, nowMillis)

    /** Отметка «звонил» ещё про этот звонок: не старше часа и не из будущего. */
    private fun isFresh(since: Long, nowMillis: Long): Boolean {
        val age = nowMillis - since
        return age >= 0 && age <= RINGING_MEMORY_MS
    }
}
