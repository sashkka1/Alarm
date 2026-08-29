package com.sasha.alarm.core

/**
 * Одно событие журнала — единица всего сбора данных.
 *
 * Журнал **только дописывается**: строка, однажды попавшая в файл, не правится и не
 * удаляется никогда. Отсюда главное решение схемы — событие хранит **факт, а не вывод**.
 * Не «владелец вышел на улицу», а «приложена метка №0, освещённость 12500». Все выводы
 * делаются при чтении, поэтому ошибку в правилах разбора можно исправить переписыванием
 * функции и пересчётом всей истории, а не потерей данных.
 *
 * По той же причине поля лежат в свободной карте, а не в запечатанной иерархии на каждый
 * тип события: сбор идёт широко и заранее неизвестно, что окажется мусором. Новое поле
 * не ломает старые строки, а неизвестный тип события спокойно доживает до того дня, когда
 * его научатся читать.
 *
 * @param at              момент события — **абсолютные** миллисекунды эпохи. Только они
 *                        переживают перевод часов и смену часового пояса (P0 №9).
 * @param tzOffsetMinutes смещение местного времени от UTC в минутах на момент записи.
 *                        Хранится рядом, потому что по одной лишь эпохе нельзя сказать,
 *                        какое утро имелось в виду, а часовой пояс со временем меняется.
 * @param type            тип события, см. [EventType].
 * @param data            поля события.
 * @param schema          версия схемы, [SCHEMA]. Читатель обязан пережить чужую версию.
 */
data class LogEvent(
    val at: Long,
    val tzOffsetMinutes: Int,
    val type: String,
    val data: Map<String, LogValue> = emptyMap(),
    val schema: Int = SCHEMA,
) {
    companion object {
        /** Версия схемы. Растёт, когда меняется смысл уже существующих полей. */
        const val SCHEMA = 1
    }

    /** Поле события, если оно есть и нужного типа. */
    fun text(key: String): String? = (data[key] as? LogValue.Text)?.value

    fun long(key: String): Long? = when (val v = data[key]) {
        is LogValue.Integer -> v.value
        is LogValue.Decimal -> v.value.toLong()
        else -> null
    }

    fun double(key: String): Double? = when (val v = data[key]) {
        is LogValue.Decimal -> v.value
        is LogValue.Integer -> v.value.toDouble()
        else -> null
    }

    fun flag(key: String): Boolean? = (data[key] as? LogValue.Flag)?.value
}

/**
 * Значение поля события.
 *
 * Целые и дробные разделены намеренно: миллисекунды эпохи в `Double` теряют точность,
 * а освещённость в `Long` теряет смысл.
 */
sealed interface LogValue {

    data class Text(val value: String) : LogValue

    data class Integer(val value: Long) : LogValue

    data class Decimal(val value: Double) : LogValue

    data class Flag(val value: Boolean) : LogValue

    companion object {
        fun of(value: String): LogValue = Text(value)
        fun of(value: Long): LogValue = Integer(value)
        fun of(value: Int): LogValue = Integer(value.toLong())
        fun of(value: Double): LogValue = Decimal(value)
        fun of(value: Boolean): LogValue = Flag(value)
    }
}

/**
 * Типы событий — общий словарь телефона и компьютера.
 *
 * Строки, а не enum: неизвестный тип должен доезжать до читателя целым, даже если тот
 * старше писателя. Значения не переименовываются никогда — под ними лежит история.
 */
object EventType {

    // --- чужой будильник ---
    /** Sleep Cycle зазвонил. `source`: "notification" | "audio". */
    const val FOREIGN_RING = "foreign.ring"

    /** Уведомление Sleep Cycle снято — наш сигнал. */
    const val FOREIGN_DISMISSED = "foreign.dismissed"

    // --- наша тревога ---
    /** Поднялся экран тревоги. `via`: "activity" | "overlay". */
    const val ALARM_SHOWN = "alarm.shown"

    /** Тревога снята. `reason`: "challenge" | "deadline" | "escape". `ms` — сколько заняла. */
    const val ALARM_DISMISSED = "alarm.dismissed"

    // --- испытание ---
    /** Началось испытание. `kind`: "pushups" | "nfc" | "math". */
    const val CHALLENGE_STARTED = "challenge.started"

    /** Испытание пройдено. `kind`, `ms`, `reps`, `failures`. */
    const val CHALLENGE_FINISHED = "challenge.finished"

    /** Кадр не засчитан. `verdict` — вердикт [PoseDiagnosis]. Нужен, чтобы настроить счётчик по реальным утрам. */
    const val POSE_REJECTED = "pose.rejected"

    // --- метки и свет ---
    /** Приложена метка. `index` — номер метки, 0 значит уличная. `lux` — освещённость, если известна. */
    const val NFC_TAG = "nfc.tag"

    /** Замер освещённости. `lux`. */
    const val LIGHT_SAMPLE = "light.sample"

    // --- телефон ---
    const val PHONE_BOOT = "phone.boot"
    const val PHONE_SHUTDOWN = "phone.shutdown"

    /** Телефоном пользовались. `fromMillis`, `toMillis`. Снимок из UsageStats — система хранит их около недели. */
    const val PHONE_USAGE = "phone.usage"

    /** Что со звуком в момент звонка. `alarmVolume`, `maxVolume`, `dnd`, `headphones`. Объясняет «будильник не сработал». */
    const val AUDIO_STATE = "audio.state"

    // --- компьютер ---
    const val PC_BOOT = "pc.boot"

    /** Выключение компьютера. `clean` — штатное или аварийное, `initiator` — кто инициировал. */
    const val PC_SHUTDOWN = "pc.shutdown"

    const val PC_SLEEP = "pc.sleep"
    const val PC_RESUME = "pc.resume"

    // --- запись ночи ---
    /** Начата запись звука ночи. `file` — имя файла на телефоне. */
    const val NIGHT_RECORD_STARTED = "night.record.started"

    /**
     * Запись ночи закончилась. `file`, `reason`: "ring" | "deadline" | "manual" | "error",
     * `ms` — сколько шла, `bytes` и `seconds` — сколько записано.
     */
    const val NIGHT_RECORD_FINISHED = "night.record.finished"

    /** Запись не завелась вовсе. `stage` — на чём споткнулась, `error` — что сказала система. */
    const val NIGHT_RECORD_FAILED = "night.record.failed"

    // --- импорт ---
    /** Ночь из экспорта Sleep Cycle. `toBedMillis`, `wokeMillis`, `inBedSec`, `asleepSec`, `latencySec`, `quality`. */
    const val SLEEP_SESSION = "sleep.session"
}
