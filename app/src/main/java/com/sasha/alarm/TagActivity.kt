package com.sasha.alarm

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.LogValue
import com.sasha.alarm.core.NfcRules
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.EventLog
import com.sasha.alarm.platform.LightSensor

/**
 * Касание метки вне тревоги.
 *
 * Уличную метку прикладывают, когда тревога давно снята и приложение закрыто, — значит
 * ловить её должна система, а не наш считыватель: тот живёт только на экране тревоги.
 * Отсюда отдельная активити с фильтром в манифесте.
 *
 * Окна у неё нет: тема прозрачная, работа занимает доли секунды, и всё, что видит
 * владелец, — короткая подсказка внизу экрана.
 *
 * ⚠️ **Чужие метки игнорируются молча.** Фильтр в манифесте ловит любую NFC-метку,
 * включая банковские карты и проездные. Показывать на них хоть что-нибудь значило бы
 * мешать владельцу каждый раз, когда он приложил телефон к турникету.
 *
 * ⚠️ На запертом экране NFC не читается вовсе — так устроен Android (разобрано
 * 2026-08-19). Владелец идёт к метке умышленно и телефон при этом разблокирован.
 */
class TagActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = idOf(intent)
        if (id == null) {
            finish()
            return
        }

        val state = AlarmStateStore(applicationContext).read()
        val tag = state.nfc.tags.firstOrNull { it.id.equals(id, ignoreCase = true) }
        if (tag == null) {
            // Не наша метка — банковская карта, проездной, что угодно. Молча уходим.
            finish()
            return
        }

        val street = tag.number == NfcRules.STREET_NUMBER
        Log.i(TAG, "метка №${tag.number} вне тревоги")

        // Освещённость меряем прямо сейчас: телефон в руке и смотрит наружу — другого
        // такого момента за утро не будет.
        LightSensor(applicationContext).sample { lux ->
            val data = LinkedHashMap<String, LogValue>()
            data["index"] = LogValue.of(tag.number.toLong())
            data["duringAlarm"] = LogValue.of(false)
            if (lux != null) data["lux"] = LogValue.of(lux.toDouble())
            EventLog(applicationContext).write(EventType.NFC_TAG, data)

            if (street) {
                Toast.makeText(applicationContext, hint(lux), Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    /** Короткая подсказка: по ней сразу видно, зачлось ли касание и видит ли телефон свет. */
    private fun hint(lux: Float?): String = when {
        lux == null -> "Отмечено"
        lux >= DAYLIGHT_LUX -> "Отмечено · дневной свет"
        else -> "Отмечено · ${lux.toInt()} лк, это ещё не улица"
    }

    private fun idOf(intent: Intent?): String? {
        val action = intent?.action ?: return null
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return null
        }
        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return null
        return tag.id?.joinToString("") { "%02X".format(it) }?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TAG = "TagActivity"

        /** Ниже этого улицей не пахнет: комната с монитором даёт около 300 люкс. */
        const val DAYLIGHT_LUX = 3_000f
    }
}
