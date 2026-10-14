package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.SyncHealth
import org.json.JSONObject
import java.io.File

/**
 * Чем кончилась последняя передача журнала.
 *
 * ⚠️ **Отдельный файл, а не поле в `alarm-state.json`** — намеренно. То состояние держит
 * тревогу и её дедлайн, его читает и пишет сторож из второго процесса, и каждая запись
 * идёт под блокировкой файла. Отметка «журнал уехал» к надёжности будильника отношения
 * не имеет, и мешать её с дедлайном значит трогать критический файл ради пустяка.
 *
 * ⚠️ Не прочиталось — считаем, что не знаем ничего ([SyncHealth.State.UNKNOWN]), а не
 * «всё плохо»: плашка об отказе, поднятая из-за битого файла, врёт ровно так же, как
 * молчание при настоящем отказе.
 */
class SyncStateStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    fun read(): SyncHealth.State = try {
        val json = raw()
        if (json == null) {
            SyncHealth.State.UNKNOWN
        } else {
            SyncHealth.State(
                lastAttemptAt = json.optLong(KEY_ATTEMPT_AT, 0L).takeIf { it > 0L },
                lastOkAt = json.optLong(KEY_OK_AT, 0L).takeIf { it > 0L },
                lastAttemptOk = json.optBoolean(KEY_ATTEMPT_OK, true),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "состояние передачи не прочиталось", e)
        SyncHealth.State.UNKNOWN
    }

    /**
     * Записать исход попытки.
     *
     * ⚠️ Время успеха **не затирается неудачей**: по нему считается давность, и обнулять
     * его при первом же промахе значит терять единственный ответ на вопрос «а когда в
     * последний раз вообще доходило».
     *
     * @param returnsSpent сколько двухчасовых возвратов потрачено в текущей цепочке
     */
    fun remember(atMillis: Long, ok: Boolean, returnsSpent: Int = 0) {
        val previous = read()
        write { json ->
            json.put(KEY_ATTEMPT_AT, atMillis)
                .put(KEY_ATTEMPT_OK, ok)
                .put(KEY_OK_AT, if (ok) atMillis else (previous.lastOkAt ?: 0L))
                .put(KEY_RETURNS, returnsSpent)
        }
    }

    /** Сколько возвратов уже потрачено. Ноль — цепочка ещё не начиналась. */
    fun returnsSpent(): Int = try {
        raw()?.optInt(KEY_RETURNS, 0) ?: 0
    } catch (e: Exception) {
        0
    }

    /**
     * Адрес компьютера, по которому он отозвался в этой сети в прошлый раз.
     *
     * ⚠️ Отдельно для каждой сети — см. [NetworkKey]. `null` — в этой сети компьютер ещё
     * не находили, и остаётся широковещательный поиск.
     */
    fun rememberedHost(networkKey: String?): String? {
        if (networkKey == null) return null
        return try {
            raw()?.optJSONObject(KEY_HOSTS)?.optString(networkKey)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Запомнить, где в этой сети нашёлся компьютер.
     *
     * ⚠️ Пишется **только после удачной передачи**, а не после удачного поиска: адрес,
     * по которому не приняли журнал, вспоминать незачем — он вернёт нас туда же.
     */
    fun rememberHost(networkKey: String?, host: String) {
        if (networkKey == null) return
        write { json ->
            val hosts = json.optJSONObject(KEY_HOSTS) ?: JSONObject()
            json.put(KEY_HOSTS, hosts.put(networkKey, host))
        }
    }

    /**
     * Сколько строк каждого файла журнала компьютер уже принял.
     *
     * 🔴 **Это ускорение, а не правда о доставленном** (2026-09-14). Правду знает компьютер
     * и называет её сам; отметка лишь избавляет от пересылки одного и того же каждый день.
     * Разошлась с действительностью — `SyncDelta` велит слать всё заново, и она починится.
     *
     * ⚠️ Считаются **строки в файлах**, а не время событий: история, догруженная задним
     * числом, дописывается в конец старых файлов, и только счётчик строк её замечает.
     */
    fun deliveredLines(): Map<String, Int> = try {
        val sent = raw()?.optJSONObject(KEY_SENT) ?: return emptyMap()
        sent.keys().asSequence().associateWith { sent.optInt(it, 0) }
    } catch (e: Exception) {
        Log.w(TAG, "отметка о доставленном не разобралась", e)
        emptyMap()
    }

    /** Сколько было у компьютера в момент прошлой удачной передачи. `null` — не знаем. */
    fun deliveredHave(): Int? = try {
        raw()?.optInt(KEY_SENT_HAVE, -1)?.takeIf { it >= 0 }
    } catch (e: Exception) {
        null
    }

    /**
     * Запомнить, что столько строк доехало и столько было у компьютера.
     *
     * ⚠️ Зовётся **только после ответа компьютера**. Отметка, поставленная до ответа,
     * однажды скроет строки, которые он не принял, — и пропажу никто не заметит.
     */
    fun rememberDelivered(lines: Map<String, Int>, have: Int?) {
        write { json ->
            json.put(KEY_SENT, JSONObject(lines as Map<*, *>))
                .put(KEY_SENT_HAVE, have ?: -1)
        }
    }

    /**
     * Файл как он есть. `null` — файла нет **либо он не разобрался**.
     *
     * 🔴 Битый файл обязан читаться как «ничего не знаем», а не бросать исключение.
     * Поймано на телефоне 02.09.2026: [write] сохраняет чужие поля, а значит сперва
     * читает файл — и на разборе битого падало в catch, то есть запись не проходила
     * **никогда**. Файл, испортившийся однажды, оставался бы испорченным навсегда, и
     * плашка о передаче замолчала бы вместе с ним. Прежняя версия чинилась сама лишь
     * потому, что перезаписывала файл целиком, не глядя.
     */
    private fun raw(): JSONObject? = try {
        if (!file.isFile) null else JSONObject(file.readText())
    } catch (e: Exception) {
        Log.w(TAG, "состояние передачи не разобралось, начинаем заново", e)
        null
    }

    /**
     * Правка файла с сохранением всего остального.
     *
     * ⚠️ Перезаписывать целиком нельзя: в одном файле теперь живут и исход попытки, и
     * запомненные адреса, а пишут их разные места в разные моменты. Собери файл заново из
     * одних своих полей — и адреса тихо исчезнут при первой же неудачной передаче.
     */
    private fun write(change: (JSONObject) -> JSONObject) {
        try {
            val json = (raw() ?: JSONObject())
            file.writeText(change(json).toString())
        } catch (e: Exception) {
            // Не записалось — в следующий раз запишется. Ронять из-за этого передачу нельзя:
            // сам журнал уже уехал, а отметка о нём вторична.
            Log.w(TAG, "состояние передачи не записалось", e)
        }
    }

    private companion object {
        const val FILE_NAME = "sync-state.json"
        const val KEY_ATTEMPT_AT = "lastAttemptAt"
        const val KEY_OK_AT = "lastOkAt"
        const val KEY_ATTEMPT_OK = "lastAttemptOk"
        const val KEY_RETURNS = "returnsSpent"
        const val KEY_HOSTS = "hosts"
        const val KEY_SENT = "sent"
        const val KEY_SENT_HAVE = "sentHave"
        const val TAG = "SyncStateStore"
    }
}
