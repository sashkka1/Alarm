package com.sasha.alarm.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Второй, независимый признак того, что чужой будильник звонит: по звуку.
 *
 * Первый признак — текст уведомления Sleep Cycle. Он сломается, если приложение
 * обновится и перепишет строку, или если сменить язык интерфейса. Звук от текста
 * не зависит вовсе: система сама говорит, что кто-то играет по каналу будильника.
 *
 * ⚠️ Чей именно это звук, узнать нельзя: с Android 9 сведения о чужих плеерах
 * обезличены — остаётся только тип использования. Поэтому сигнал годится, чтобы
 * понять «чужой будильник зазвонил», и не годится, чтобы понять «его выключили»:
 * между повторами цикла звук замолкает точно так же.
 */
class ForeignAudioWatcher(
    context: Context,
    private val onAlarmAudio: (playing: Boolean) -> Unit,
) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var lastPlaying = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            report(configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM })
        }
    }

    fun start() {
        val manager = audio ?: return
        try {
            manager.registerAudioPlaybackCallback(callback, handler)
            report(manager.activePlaybackConfigurations.any {
                it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
            })
            Log.i(TAG, "слежу за звуком по каналу будильника")
        } catch (e: Exception) {
            Log.w(TAG, "не удалось подписаться на аудио", e)
        }
    }

    fun stop() {
        try {
            audio?.unregisterAudioPlaybackCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "не удалось отписаться от аудио", e)
        }
    }

    /** Сообщаем только о смене состояния: система шлёт события пачками. */
    private fun report(playing: Boolean) {
        if (playing == lastPlaying) return
        lastPlaying = playing
        Log.i(TAG, if (playing) "по каналу будильника пошёл звук" else "звук по каналу будильника смолк")
        onAlarmAudio(playing)
    }

    private companion object {
        const val TAG = "ForeignAudioWatcher"
    }
}
