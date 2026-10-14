package com.sasha.alarm.platform

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.LogEvent
import com.sasha.alarm.core.LogValue
import com.sasha.alarm.core.StepBuckets
import java.util.TimeZone

/**
 * Шаги из системного шагомера Xiaomi.
 *
 * ⚠️ **Шаги телефон не считает сам** (владелец, 2026-09-13). Их круглосуточно считает
 * служба `com.miui.rom` и хранит в своём провайдере — значит ни датчика, ни живого
 * процесса, ни батареи. Alarm при каждой передаче журнала переписывает к себе то, что
 * накопилось, и шаги уезжают на компьютер той же передачей.
 *
 * ✅ **Схема провайдера подтверждена на телефоне** (1.106, 2026-09-13): `_begin_time`,
 * `_end_time`, `_steps`. Столбцы всё равно берутся по имени — обновление прошивки может
 * сменить схему; нужных нет — одно предупреждение со списком того, что есть, и пустой снимок.
 *
 * ⚠️ **Раскладка по корзинам — правило ядра** (`StepBuckets`), здесь только чтение.
 */
object PhoneSteps {

    /**
     * Снять шаги за окно и дописать в журнал.
     *
     * ⚠️ **Пишутся только незнакомые корзины**, как и у `PhoneUsage`: окно снимка шире
     * суток, а передач за день бывает несколько. Корзина узнаётся по началу — но только
     * среди корзин текущей ширины.
     *
     * 🔴 **Старые корзины по 15 минут в «знакомые» не идут** (переход на минуту, 2026-09-13).
     * Их в журнале десятки тысяч, и начало каждой совпадает с началом минутной — узнавай мы
     * по одному началу, каждая пятнадцатая минута истории молча не записалась бы. Поэтому
     * вся история один раз перевыгружается минутами, а крупные корзины остаются в журнале
     * как есть: перекрытые компьютер отбрасывает сам.
     *
     * ⚠️ Корзин за всю историю — сотня тысяч, поэтому они уходят в журнал одной пачкой
     * ([EventLog.writeAll]), а не задачей на строку.
     *
     * Никогда не бросает: нет разрешения, нет провайдера, пустой курсор — предупреждение и 0.
     * Шаги нужны статистике, а не тревоге, и ронять из-за них передачу журнала нельзя.
     *
     * @return сколько корзин записано
     */
    fun snapshot(context: Context, fromMillis: Long, toMillis: Long): Int = try {
        val rows = read(context, fromMillis, toMillis)
        val log = EventLog(context)
        // Журнал читается здесь один раз: знакомые корзины собираются из того же прохода.
        val known = StepBuckets.known(
            log.events(EventType.STEPS_INTERVAL).map { it.long("fromMillis") to it.long("toMillis") }
        )
        // Конец окна и есть «сейчас» снимка: часы россыпью не берутся (P0 №9).
        val buckets = StepBuckets.of(rows, toMillis, known)

        val zone = TimeZone.getDefault()
        log.writeAll(
            buckets.map { bucket ->
                LogEvent(
                    // Датируется концом корзины, как и отрезки пользования.
                    at = bucket.to,
                    tzOffsetMinutes = zone.getOffset(bucket.to) / 60_000,
                    type = EventType.STEPS_INTERVAL,
                    data = mapOf(
                        "fromMillis" to LogValue.of(bucket.from),
                        "toMillis" to LogValue.of(bucket.to),
                        "steps" to LogValue.of(bucket.steps),
                    ),
                )
            }
        )
        buckets.size
    } catch (e: Exception) {
        // SecurityException (разрешение не выдано), провайдера нет, курсор не открылся.
        Log.w(TAG, "шаги не прочитались", e)
        0
    }

    /**
     * Можно ли читать шаги: разрешение выдано и провайдер есть на этом телефоне.
     *
     * ⚠️ Разрешение уровня `normal` выдаётся при установке, если объявлено в манифесте, —
     * но только когда в системе есть тот, кто его определил (`com.miui.rom`). На телефоне
     * без шагомера его не выдают, а провайдера может не быть и при выданном — поэтому
     * проверяются оба условия.
     */
    // ⚠️ Провайдер пускает только при двух разрешениях: READ_STEPS и ACTIVITY_RECOGNITION.
    // Второе — диалогом; без него запрос падает SecurityException (телефон, 2026-09-13).
    fun available(context: Context): Boolean = try {
        val granted = context.checkSelfPermission(READ_STEPS) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(ACTIVITY) == PackageManager.PERMISSION_GRANTED
        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0)
        granted && provider != null
    } catch (e: Exception) {
        Log.w(TAG, "не удалось проверить доступ к шагам", e)
        false
    }

    /**
     * Строки провайдера как есть, до раскладки по корзинам — для разбора по кабелю.
     *
     * ⚠️ Нужна, потому что оболочка adb провайдер не читает вовсе (нет ни `READ_STEPS`, ни
     * `ACCESS_CONTENT_PROVIDERS_EXTERNALLY` для `run-as`), а корзины (тогда по 15 минут) прятали
     * границы прогулок. Поймано 2026-09-13 при замере длины шага. Бросает — ловит вызывающий.
     */
    fun rawRows(context: Context, fromMillis: Long, toMillis: Long): List<StepBuckets.Row> =
        read(context, fromMillis, toMillis)

    private fun read(context: Context, fromMillis: Long, toMillis: Long): List<StepBuckets.Row> {
        val resolver = context.contentResolver
        val cursor = try {
            resolver.query(
                ITEMS,
                null,
                "$COL_END > ? AND $COL_BEGIN < ?",
                arrayOf(fromMillis.toString(), toMillis.toString()),
                null,
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            // Провайдер может не понимать условия (чужая схема) — тогда читаем всё и
            // отсеиваем здесь.
            Log.w(TAG, "запрос шагов с условием не прошёл, читаю без условия", e)
            resolver.query(ITEMS, null, null, null, null)
        }
        if (cursor == null) {
            Log.w(TAG, "провайдер шагов вернул пустой курсор")
            return emptyList()
        }
        return cursor.use { rows(it, fromMillis, toMillis) }
    }

    private fun rows(cursor: Cursor, fromMillis: Long, toMillis: Long): List<StepBuckets.Row> {
        val begin = cursor.getColumnIndex(COL_BEGIN)
        val end = cursor.getColumnIndex(COL_END)
        val steps = cursor.getColumnIndex(COL_STEPS)
        if (begin < 0 || end < 0 || steps < 0) {
            Log.w(TAG, "в провайдере шагов нет нужных столбцов: ${cursor.columnNames.joinToString()}")
            return emptyList()
        }
        val rows = mutableListOf<StepBuckets.Row>()
        while (cursor.moveToNext()) {
            val row = StepBuckets.Row(cursor.getLong(begin), cursor.getLong(end), cursor.getLong(steps))
            // Отсев повторяет условие запроса: без условия провайдер отдаёт всю историю.
            if (row.to > fromMillis && row.from < toMillis) rows += row
        }
        return rows
    }

    private const val AUTHORITY = "com.miui.providers.steps"
    private val ITEMS: Uri = Uri.parse("content://$AUTHORITY/item")
    private const val READ_STEPS = "miui.permission.READ_STEPS"
    const val ACTIVITY = "android.permission.ACTIVITY_RECOGNITION"
    private const val COL_BEGIN = "_begin_time"
    private const val COL_END = "_end_time"
    private const val COL_STEPS = "_steps"

    private const val TAG = "PhoneSteps"
}
