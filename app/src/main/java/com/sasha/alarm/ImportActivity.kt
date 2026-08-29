package com.sasha.alarm

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.SleepCycleCsv
import com.sasha.alarm.platform.EventLog
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Приём экспорта Sleep Cycle с телефона.
 *
 * Путь владельца: Sleep Cycle → Экспорт → «Поделиться» → **Alarm**. Ночи ложатся в наш
 * журнал и уезжают на компьютер следующей передачей — ежедневной в обед либо по кнопке
 * «Отдать журнал», когда захочется не ждать.
 *
 * ⚠️ Мимо файла их данные не прочитать вовсе: **Sleep Cycle не пишет в Health Connect**
 * (проверено 2026-08-26 на версии 4.26.24 — он не запрашивает ни одного разрешения
 * здоровья). Остаётся их собственный экспорт.
 *
 * ⚠️ **Делится владелец сам, когда захочет.** Служба спецвозможностей, ходившая по их
 * экранам и жавшая кнопки за него, была написана и работала (полный проход за 2,6 с),
 * но удалена 2026-08-26 решением владельца. Причина не в хрупкости, а в том, что проход
 * требовал **разблокированного бодрствующего телефона** — на запертом экране наверху
 * системный keyguard, читать и нажимать нечего. Значит запускать её по расписанию нельзя
 * было в принципе, а раз момент всё равно выбирает человек, то пусть он и делится.
 *
 * Повторы отсеиваются на приёме, поэтому делиться одним и тем же файлом безопасно —
 * этим владелец и пользуется: шлёт когда вспомнил, не отслеживая, что уже отправлено.
 *
 * Окна у активити нет: тема прозрачная, всё видимое — короткая подсказка внизу экрана.
 */
class ImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = uriOf(intent)
        if (uri == null) {
            toast(getString(com.sasha.alarm.ui.R.string.import_no_file))
            finish()
            return
        }
        // Чтение файла и разбор сотни ночей — не работа для главного потока.
        Executors.newSingleThreadExecutor().execute { handle(uri) }
    }

    private fun handle(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "файл не прочитался", e)
            null
        }

        val message = if (text.isNullOrBlank()) {
            getString(com.sasha.alarm.ui.R.string.import_unreadable)
        } else {
            val parsed = SleepCycleCsv.parse(text, TimeZone.getDefault().toZoneId())
            if (parsed.sessions.isEmpty()) {
                getString(com.sasha.alarm.ui.R.string.import_not_sleep_cycle)
            } else {
                // ⚠️ Повторы отсеиваются **здесь**, а не только на компьютере.
                // Сначала казалось, что хватит приёмника: журнал всё равно только
                // дозаписывается. Но с ежедневной автоматической выгрузкой один и тот
                // же экспорт приходит каждый день целиком, и журнал рос бы на сотню
                // строк в сутки вечно. Поймано на телефоне 2026-08-26: 119 ночей
                // превратились в 238 за один прогон.
                val log = EventLog(applicationContext)
                val known = log.events(EventType.SLEEP_SESSION)
                    .mapNotNull { SleepCycleCsv.sessionKey(it) }
                    .toSet()
                val fresh = parsed.sessions.filter { SleepCycleCsv.sessionKey(it) !in known }
                fresh.forEach(log::write)
                Log.i(TAG, "принято ночей: ${fresh.size} из ${parsed.sessions.size}")
                if (fresh.isEmpty()) {
                    getString(com.sasha.alarm.ui.R.string.import_known, parsed.sessions.size)
                } else {
                    getString(com.sasha.alarm.ui.R.string.import_done, fresh.size)
                }
            }
        }

        runOnUiThread {
            toast(message)
            finish()
        }
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()

    private fun uriOf(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private companion object {
        const val TAG = "ImportActivity"
    }
}
