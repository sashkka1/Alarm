package com.sasha.alarm.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Голос приложения.
 *
 * Нужен, потому что во время отжиманий человек смотрит в пол, а не в экран:
 * написанное «отодвинь телефон» он увидит только когда встанет, то есть никогда.
 *
 * Речь идёт по каналу будильника (`USAGE_ALARM`) намеренно: на телефоне, где
 * уведомления приглушены или стоит «Не беспокоить», любой другой канал промолчит —
 * а это ровно тот отказ, который приложение обязано исключать (P0 №7).
 */
class Speaker(context: Context) {

    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    /** Что сказали последним и когда — чтобы не тараторить одно и то же. */
    private var lastText: String? = null
    private var lastAtMillis: Long = 0L

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "синтез речи не поднялся, код $status")
                return@TextToSpeech
            }
            val engine = engine ?: return@TextToSpeech
            val result = engine.setLanguage(Locale("ru"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Не отказываемся: даже чужим голосом цифры произносятся узнаваемо.
                Log.w(TAG, "русского голоса нет, говорю тем, что есть")
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            engine.setSpeechRate(SPEECH_RATE)
            ready = true
        }
    }

    /**
     * Сказать вслух.
     *
     * @param interrupt оборвать то, что говорится сейчас. Для счёта повторов — да:
     *                  «семь» важнее, чем договорить «шесть».
     * @param minRepeatMillis не повторять тот же текст чаще этого. Ноль — можно всегда.
     */
    fun say(
        text: String,
        nowMillis: Long,
        interrupt: Boolean = true,
        minRepeatMillis: Long = 0L,
    ) {
        val engine = engine ?: return
        if (!ready) return
        if (text == lastText && nowMillis - lastAtMillis < minRepeatMillis) return

        lastText = text
        lastAtMillis = nowMillis
        try {
            val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, text)
        } catch (e: Exception) {
            Log.w(TAG, "не выговорилось: $text", e)
        }
    }

    /** Забыть сказанное: следующая тревога начинается с чистого листа. */
    fun forget() {
        lastText = null
        lastAtMillis = 0L
    }

    fun release() {
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "синтез речи не выключился", e)
        }
        engine = null
        ready = false
    }

    private companion object {
        const val TAG = "Speaker"

        /**
         * Скорость речи.
         *
         * Была замедлена до 0.85 (2026-08-18), но под нагрузкой оказалось наоборот:
         * медленный голос не успевает сказать число до следующего повтора. Ускорено
         * в 1.6 раза от прежнего (владелец, 2026-08-25) — это заметно быстрее
         * обычной речи движка.
         */
        const val SPEECH_RATE = 1.36f
    }
}
