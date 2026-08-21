package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.AlarmRun
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.FailSafe
import com.sasha.alarm.core.MathOperation
import com.sasha.alarm.core.MathSettings
import com.sasha.alarm.core.MelodySource
import com.sasha.alarm.core.NfcSettings
import com.sasha.alarm.core.NfcTag
import com.sasha.alarm.core.PoseModel
import com.sasha.alarm.core.PushupOverlay
import com.sasha.alarm.core.PushupSettings
import com.sasha.alarm.core.ReactionSettings
import com.sasha.alarm.core.SoundSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Состояние приложения на диске.
 *
 * Обычный файл, а не DataStore/SharedPreferences, ровно по одной причине:
 * его читает и пишет **второй процесс** — сторож `:guard`. Ни DataStore, ни
 * SharedPreferences межпроцессной согласованности не дают; блокировка файла даёт.
 *
 * Запись атомарная (временный файл + переименование): оборвавшаяся на середине
 * запись не должна оставить будильник с наполовину прочитанным дедлайном.
 *
 * **Настройки не теряются ни при каком обновлении приложения.** Три правила, и все
 * три нужны (владелец, 2026-08-19):
 * 1. Запись идёт **поверх** прежнего файла, а не с нуля: ключ, которого новая версия
 *    не знает, переживает её нетронутым.
 * 2. Перед каждой записью прежнее содержимое откладывается в [BACKUP_NAME].
 * 3. Разбор идёт **по кускам**: не прочиталась одна настройка — умолчание берётся
 *    только для неё, остальные остаются как были.
 */
class AlarmStateStore(context: Context) {

    private val dir: File = context.filesDir
    private val file = File(dir, FILE_NAME)
    private val backupFile = File(dir, BACKUP_NAME)
    private val lockFile = File(dir, LOCK_NAME)

    fun read(): AlarmState = withLock { readUnlocked() }

    fun write(state: AlarmState) = withLock { writeUnlocked(state) }

    /** Прочитать, изменить и записать одним неделимым шагом. */
    fun update(block: (AlarmState) -> AlarmState): AlarmState = withLock {
        val updated = block(readUnlocked())
        writeUnlocked(updated)
        updated
    }

    private fun readUnlocked(): AlarmState {
        val raw = rawUnlocked() ?: return AlarmState.DEFAULT
        return parse(raw)
    }

    /**
     * Файл как он есть, без разбора. `null` — читать нечего вовсе.
     *
     * Основной файл, а если он пропал, пуст или не разбирается — предыдущий,
     * отложенный при прошлой записи. Настройки собираются руками и по одной, и
     * терять их из-за одной оборвавшейся записи нельзя.
     */
    private fun rawUnlocked(): JSONObject? {
        load(file)?.let { return it }

        // Сюда попадаем, только если не разобрался сам файл: отдельные поля защищены
        // каждое своей защитой и досюда не доходят. Откладываем копию — дальше поверх
        // ляжет новая запись, и узнать, что там было, стало бы уже неоткуда.
        if (file.exists()) {
            Log.e(TAG, "состояние не прочиталось целиком, беру резервную копию")
            runCatching { file.copyTo(File(dir, BROKEN_NAME), overwrite = true) }
        }

        load(backupFile)?.let {
            Log.w(TAG, "состояние взято из резервной копии")
            return it
        }
        return null
    }

    /** Разобрать файл. `null` — нет, пуст или не JSON. */
    private fun load(source: File): JSONObject? = try {
        if (!source.exists()) null else source.readText().takeIf { it.isNotBlank() }?.let(::JSONObject)
    } catch (e: Exception) {
        Log.w(TAG, "файл ${source.name} не разобрался", e)
        null
    }

    /**
     * Запись.
     *
     * ⚠️ **Пишем поверх того, что уже лежит на диске, а не с нуля.** Ключ, который
     * новая версия приложения не знает — переименовали enum, убрали поле, откатились
     * на сборку постарше, — при записи с нуля исчезал бы навсегда, хотя терять его
     * никто не просил. Теперь незнакомые ключи просто переживают запись нетронутыми
     * (владелец, 2026-08-19: «каждая минимальная визуальная правка сбрасывает
     * настройки — так не должно работать»).
     *
     * ⚠️ Перед каждой записью прежнее содержимое откладывается в [BACKUP_NAME]. Это
     * цена в одну копию файла на 500 байт и единственный способ пережить запись,
     * оборвавшуюся на середине.
     */
    private fun writeUnlocked(state: AlarmState) {
        try {
            // Основа — то, что на диске: сохраняем всё, о чём эта версия не знает.
            val merged = rawUnlocked() ?: JSONObject()
            serialize(state).let { fresh ->
                fresh.keys().forEach { key -> merged.put(key, fresh.get(key)) }
            }

            runCatching { if (file.exists()) file.copyTo(backupFile, overwrite = true) }

            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(merged.toString())
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Log.e(TAG, "состояние не записалось", e)
        }
    }

    /**
     * Разбор состояния с диска.
     *
     * ⚠️ Каждый кусок разбирается **отдельно и в своей защите**. Раньше сбой в любом
     * одном поле обнулял всё состояние целиком: `parse` бросал, и наверху возвращались
     * умолчания. А дальше первая же правка настроек записывала эти умолчания на диск —
     * и настройки терялись навсегда, хотя на диске лежали правильные (владелец,
     * 2026-08-16: «после обновления настройки сбиваются»).
     *
     * Теперь испорченным может оказаться только тот кусок, который и правда испорчен;
     * остальные переживают и обновление, и добавление новых полей.
     */
    private fun parse(json: JSONObject): AlarmState {
        val startedAt = json.optLong(KEY_RUN_STARTED, 0L)
        val deadline = json.optLong(KEY_RUN_DEADLINE, 0L)
        val run = if (startedAt > 0L && deadline > 0L) {
            AlarmRun(startedAt, deadline, json.optBoolean(KEY_RUN_PREVIEW, false))
        } else {
            null
        }

        return AlarmState(
            masterEnabled = json.optBoolean(KEY_MASTER, true),
            failSafeMinutes = part("бэкап", FailSafe.DEFAULT_MINUTES) {
                json.optInt(KEY_FAILSAFE_MINUTES, FailSafe.DEFAULT_MINUTES)
                    .coerceIn(FailSafe.MIN_MINUTES, FailSafe.MAX_MINUTES)
            },
            sound = part("звук", SoundSettings.DEFAULT) { parseSound(json) },
            challenge = part("испытание", Challenge.MATH) {
                Challenge.entries.firstOrNull { it.name == json.optString(KEY_CHALLENGE) }
                    ?: Challenge.MATH
            },
            math = part("примеры", MathSettings.DEFAULT) { parseMath(json) },
            reaction = part("реакция", ReactionSettings.DEFAULT) {
                ReactionSettings(
                    perfectSeconds = json.optInt(
                        KEY_REACTION_SECONDS,
                        ReactionSettings.DEFAULT.perfectSeconds,
                    ).coerceIn(ReactionSettings.MIN_SECONDS, ReactionSettings.MAX_SECONDS),
                )
            },
            pushups = part("отжимания", PushupSettings.DEFAULT) {
                PushupSettings(
                    count = json.optInt(KEY_PUSHUP_COUNT, PushupSettings.DEFAULT.count)
                        .coerceIn(PushupSettings.MIN_COUNT, PushupSettings.MAX_COUNT),
                    overlay = PushupOverlay.entries
                        .firstOrNull { it.name == json.optString(KEY_PUSHUP_OVERLAY) }
                        ?: PushupSettings.DEFAULT.overlay,
                    model = PoseModel.entries
                        .firstOrNull { it.name == json.optString(KEY_PUSHUP_MODEL) }
                        ?: PushupSettings.DEFAULT.model,
                )
            },
            nfc = part("метки", NfcSettings.DEFAULT) { parseNfc(json) },
            run = run,
            manualPermissions = part("разрешения", emptySet()) {
                buildSet {
                    json.optJSONArray(KEY_MANUAL)?.let { array ->
                        for (i in 0 until array.length()) add(array.optString(i))
                    }
                }
            },
            settingsVisited = json.optBoolean(KEY_SETTINGS_VISITED, false),
            foreignRingingSinceMillis = json.optLong(KEY_FOREIGN_RINGING, 0L).takeIf { it > 0L },
            resumeDelaySeconds = part("возврат", AlarmState.MIN_RESUME_DELAY_SECONDS) {
                json.optInt(KEY_RESUME_DELAY, AlarmState.MIN_RESUME_DELAY_SECONDS)
                    .coerceIn(AlarmState.MIN_RESUME_DELAY_SECONDS, AlarmState.MAX_RESUME_DELAY_SECONDS)
            },
        )
    }

    /** Разобрать один кусок настроек. Не вышло — только он и берётся из умолчаний. */
    private fun <T> part(name: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "настройка «$name» не прочиталась, беру умолчание", e)
        fallback
    }

    private fun parseSound(json: JSONObject): SoundSettings {
        val storedFile = json.optString(KEY_MELODY_FILE).takeIf { it.isNotEmpty() }
        val melody = if (storedFile != null) {
            MelodySource.Stored(storedFile, json.optString(KEY_MELODY_NAME, storedFile))
        } else {
            MelodySource.SystemAlarm
        }
        return SoundSettings(
            enabled = json.optBoolean(KEY_SOUND_ENABLED, true),
            startVolumePercent = json.optInt(KEY_START_VOLUME, SoundSettings.DEFAULT.startVolumePercent)
                .coerceIn(0, 100),
            secondsPerPercent = json.optInt(KEY_SECONDS_PER_PERCENT, SoundSettings.DEFAULT.secondsPerPercent)
                .coerceIn(SoundSettings.MIN_SECONDS_PER_PERCENT, SoundSettings.MAX_SECONDS_PER_PERCENT),
            vibrate = json.optBoolean(KEY_VIBRATE, SoundSettings.DEFAULT.vibrate),
            melody = melody,
        )
    }

    private fun parseMath(json: JSONObject): MathSettings = MathSettings(
        operations = json.optString(KEY_MATH_OPS)
            .split(',')
            .mapNotNull { name -> MathOperation.entries.firstOrNull { it.name == name } }
            .toSet()
            .ifEmpty { MathSettings.DEFAULT.operations },
        count = json.optInt(KEY_MATH_COUNT, MathSettings.DEFAULT.count)
            .coerceIn(MathSettings.MIN_COUNT, MathSettings.MAX_COUNT),
        min = json.optInt(KEY_MATH_MIN, MathSettings.DEFAULT.min),
        max = json.optInt(KEY_MATH_MAX, MathSettings.DEFAULT.max),
    )

    /**
     * Метки и маршрут.
     *
     * Метка хранится парой «номер + идентификатор железа»: номер владелец видит и
     * подписывает на наклейке, идентификатор сравнивается при касании. Маршрут —
     * просто список номеров, повторы в нём разрешены.
     */
    private fun parseNfc(json: JSONObject): NfcSettings {
        val tags = mutableListOf<NfcTag>()
        json.optJSONArray(KEY_NFC_TAGS)?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val number = item.optInt(KEY_NFC_TAG_NUMBER, 0)
                val id = item.optString(KEY_NFC_TAG_ID)
                if (number > 0 && id.isNotEmpty()) tags += NfcTag(number, id)
            }
        }

        val route = mutableListOf<Int>()
        json.optJSONArray(KEY_NFC_ROUTE)?.let { array ->
            for (i in 0 until array.length()) {
                val number = array.optInt(i, 0)
                // Шаг на метку, которой больше нет, молча выпадает: маршрут обязан
                // состоять только из того, что реально можно приложить.
                if (tags.any { it.number == number }) route += number
            }
        }

        return NfcSettings(tags = tags, route = route)
    }

    private fun serialize(state: AlarmState): JSONObject = JSONObject().apply {
        put(KEY_MASTER, state.masterEnabled)
        put(KEY_FAILSAFE_MINUTES, state.failSafeMinutes)
        put(KEY_RUN_STARTED, state.run?.startedAtMillis ?: 0L)
        put(KEY_RUN_DEADLINE, state.run?.deadlineMillis ?: 0L)
        put(KEY_RUN_PREVIEW, state.run?.preview ?: false)
        put(KEY_MANUAL, JSONArray(state.manualPermissions.toList()))
        put(KEY_SOUND_ENABLED, state.sound.enabled)
        put(KEY_START_VOLUME, state.sound.startVolumePercent)
        put(KEY_SECONDS_PER_PERCENT, state.sound.secondsPerPercent)
        put(KEY_VIBRATE, state.sound.vibrate)
        // ⚠️ Пишутся всегда, в том числе пустыми. Раньше при системной мелодии ключей
        // просто не было — а теперь запись идёт поверх прежнего файла, и «не написать»
        // означало бы «оставить старую мелодию»: вернуться с своей на системную стало
        // бы невозможно. Пустая строка при разборе и читается как системная.
        val stored = state.sound.melody as? MelodySource.Stored
        put(KEY_MELODY_FILE, stored?.fileName ?: "")
        put(KEY_MELODY_NAME, stored?.displayName ?: "")
        put(KEY_CHALLENGE, state.challenge.name)
        put(KEY_MATH_OPS, state.math.operations.joinToString(",") { it.name })
        put(KEY_MATH_COUNT, state.math.count)
        put(KEY_MATH_MIN, state.math.min)
        put(KEY_MATH_MAX, state.math.max)
        put(KEY_REACTION_SECONDS, state.reaction.perfectSeconds)
        put(KEY_PUSHUP_COUNT, state.pushups.count)
        put(KEY_PUSHUP_OVERLAY, state.pushups.overlay.name)
        put(KEY_PUSHUP_MODEL, state.pushups.model.name)
        put(
            KEY_NFC_TAGS,
            JSONArray(
                state.nfc.tags.map {
                    JSONObject().put(KEY_NFC_TAG_NUMBER, it.number).put(KEY_NFC_TAG_ID, it.id)
                },
            ),
        )
        put(KEY_NFC_ROUTE, JSONArray(state.nfc.route))
        put(KEY_SETTINGS_VISITED, state.settingsVisited)
        put(KEY_FOREIGN_RINGING, state.foreignRingingSinceMillis ?: 0L)
        put(KEY_RESUME_DELAY, state.resumeDelaySeconds)
    }

    private fun <T> withLock(block: () -> T): T {
        return try {
            RandomAccessFile(lockFile, "rw").use { raf ->
                raf.channel.use { channel ->
                    val lock = channel.lock()
                    try {
                        block()
                    } finally {
                        lock.release()
                    }
                }
            }
        } catch (e: Exception) {
            // Не смогли взять блокировку — работаем без неё. Отказ читать состояние
            // означал бы молчащий будильник, а это худший исход (P0 №7).
            Log.w(TAG, "блокировка файла не взялась, работаю без неё", e)
            block()
        }
    }

    private companion object {
        const val TAG = "AlarmStateStore"
        const val FILE_NAME = "alarm-state.json"
        const val LOCK_NAME = "alarm-state.lock"

        /** Прежнее содержимое, отложенное перед записью. Спасательный круг настроек. */
        const val BACKUP_NAME = "alarm-state.bak.json"

        /** Копия файла, который не удалось разобрать. Чтобы было что посмотреть потом. */
        const val BROKEN_NAME = "alarm-state.broken.json"

        const val KEY_MASTER = "masterEnabled"
        const val KEY_FAILSAFE_MINUTES = "failSafeMinutes"
        const val KEY_RUN_STARTED = "runStartedAt"
        const val KEY_RUN_DEADLINE = "runDeadline"
        const val KEY_RUN_PREVIEW = "runPreview"
        const val KEY_MANUAL = "manual"
        const val KEY_SOUND_ENABLED = "soundEnabled"
        const val KEY_START_VOLUME = "startVolume"
        const val KEY_SECONDS_PER_PERCENT = "secondsPerPercent"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_MELODY_FILE = "melodyFile"
        const val KEY_MELODY_NAME = "melodyName"
        const val KEY_CHALLENGE = "challenge"
        const val KEY_MATH_OPS = "mathOperations"
        const val KEY_MATH_COUNT = "mathCount"
        const val KEY_MATH_MIN = "mathMin"
        const val KEY_MATH_MAX = "mathMax"
        const val KEY_REACTION_SECONDS = "reactionPerfectSeconds"
        const val KEY_PUSHUP_COUNT = "pushupCount"
        const val KEY_PUSHUP_OVERLAY = "pushupOverlay"
        const val KEY_PUSHUP_MODEL = "pushupModel"
        const val KEY_NFC_TAGS = "nfcTags"
        const val KEY_NFC_TAG_NUMBER = "n"
        const val KEY_NFC_TAG_ID = "id"
        const val KEY_NFC_ROUTE = "nfcRoute"
        const val KEY_SETTINGS_VISITED = "settingsVisited"
        const val KEY_FOREIGN_RINGING = "foreignRingingSince"
        const val KEY_RESUME_DELAY = "resumeDelaySeconds"
    }
}
