package com.sasha.alarm.core

/**
 * Жив ли зацеп — слушатель уведомлений, на котором держатся оба чужих будильника.
 *
 * 🔴 **Заведено после четырёх молчаливых утр** (разбор 2026-09-30): с 26.09 слушатель был
 * не подключён — «очистка всего» убила процесс, и система службу не подняла, — и ни
 * Sleep Cycle, ни часы экран не поднимали, а снаружи всё выглядело исправным. Владелец:
 * «лучше обезопасить себя — проверка и переустановка зацепа, и писать, если зацеп не работает».
 *
 * Проверка идёт раз в [CHECK_INTERVAL_MS] и при каждом открытии приложения. Не подключён —
 * телефон толкает систему переподключить его и проверяет снова через [RECHECK_MS]: сигнал
 * системы доходит за десятки секунд. Не поднялся и тогда — отрезок «не работает»
 * открывается один раз: запись в журнал, уведомление, внеочередная отдача журнала (хаб
 * пишет в Telegram). Уведомление висит, пока зацеп не заработает снова.
 *
 * ⚠️ Ждать повторной проверки, а не кричать сразу, — ради Telegram: поломка, которую
 * телефон сам починил за полминуты, не должна будить владельца сообщением.
 */
object HookHealth {

    /** Как часто проверять. После «очистки» зацеп поднимется не позже чем через столько. */
    const val CHECK_INTERVAL_MS = 15 * 60_000L

    /**
     * Через сколько проверить снова после толчка. Система сообщает о смене пакета с
     * задержкой — на телефоне владельца 30.09 это заняло около 25 секунд.
     */
    const val RECHECK_MS = 60_000L

    /** Что показала проверка — уже после быстрой попытки переподключить. */
    enum class Status {
        /** Слушатель подключён. */
        WORKING,

        /** Доступ к уведомлениям есть, а слушатель не подключён. */
        UNBOUND,

        /** У приложения отобран доступ к уведомлениям — переподключать нечего. */
        NO_ACCESS,
    }

    /** Что записать в журнал: тип события и его поля. */
    data class Entry(val type: String, val data: Map<String, LogValue>)

    /**
     * Итог проверки: новые отметки для диска и что сделать снаружи.
     *
     * @property downSince       новое `hookDownSinceMillis` — начало объявленного отрезка
     * @property suspectSince    новое `hookSuspectSinceMillis` — не подключён, ждём повторной проверки
     * @property entry           событие в журнал; null — писать нечего
     * @property showNotice      висеть ли уведомлению «зацеп не работает»
     * @property sendJournalNow  отдать журнал сейчас, не дожидаясь суточной передачи
     * @property recheckSoon     поставить повторную проверку через [RECHECK_MS]
     */
    data class Outcome(
        val downSince: Long?,
        val suspectSince: Long?,
        val entry: Entry?,
        val showNotice: Boolean,
        val sendJournalNow: Boolean,
        val recheckSoon: Boolean,
    )

    /**
     * @param downSince     начало объявленного отрезка «не работает» с диска
     * @param suspectSince  когда слушатель впервые нашёлся отключённым, если отрезок ещё не объявлен
     * @param status        что показала проверка
     * @param rebound       слушатель был отключён, и эта же проверка его подняла
     */
    fun judge(
        downSince: Long?,
        suspectSince: Long?,
        status: Status,
        rebound: Boolean,
        nowMillis: Long,
    ): Outcome = when (status) {
        Status.WORKING -> when {
            downSince != null -> Outcome(
                downSince = null,
                suspectSince = null,
                entry = Entry(
                    EventType.HOOK_UP,
                    mapOf("downMs" to LogValue.of((nowMillis - downSince).coerceAtLeast(0L))),
                ),
                showNotice = false,
                sendJournalNow = false,
                recheckSoon = false,
            )

            // Поднялся после толчка или сразу — поломки не было, есть только строка в журнал.
            suspectSince != null || rebound ->
                Outcome(null, null, Entry(EventType.HOOK_REBOUND, emptyMap()), false, false, false)

            else -> Outcome(null, null, entry = null, showNotice = false, sendJournalNow = false, recheckSoon = false)
        }

        // Отобранный доступ толчком не вернуть — ждать нечего, отрезок открывается сразу.
        Status.NO_ACCESS -> declareOrKeep(downSince, start = suspectSince ?: nowMillis, status = status)

        Status.UNBOUND -> when {
            downSince != null -> declareOrKeep(downSince, start = downSince, status = status)
            // Впервые: толкнули систему и ждём повторной проверки, ни о чём пока не говоря.
            suspectSince == null ->
                Outcome(null, nowMillis, entry = null, showNotice = false, sendJournalNow = false, recheckSoon = true)
            // Повторная проверка, а он всё ещё лежит — поломка настоящая.
            else -> declareOrKeep(null, start = suspectSince, status = status)
        }
    }

    /**
     * Открыть отрезок, если он ещё не открыт, или держать уже открытый.
     *
     * ⚠️ Отрезок пишется один раз, на его начало: иначе проверка раз в 15 минут за ночь
     * дала бы три десятка одинаковых строк и столько же жалоб. Начало — когда слушатель
     * впервые нашёлся отключённым, а не когда в этом убедились.
     */
    private fun declareOrKeep(downSince: Long?, start: Long, status: Status): Outcome =
        if (downSince != null) {
            Outcome(downSince, null, entry = null, showNotice = true, sendJournalNow = false, recheckSoon = false)
        } else {
            Outcome(
                downSince = start,
                suspectSince = null,
                entry = Entry(EventType.HOOK_DOWN, mapOf("reason" to LogValue.of(reason(status)))),
                showNotice = true,
                sendJournalNow = true,
                recheckSoon = false,
            )
        }

    /** Значение поля `reason` у [EventType.HOOK_DOWN] — общий словарь с хабом. */
    fun reason(status: Status): String = when (status) {
        Status.NO_ACCESS -> "no_access"
        Status.UNBOUND, Status.WORKING -> "unbound"
    }
}
