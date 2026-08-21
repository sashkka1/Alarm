package com.sasha.alarm.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Проигрыватель звонка.
 *
 * `USAGE_ALARM` — не косметика: с ним звук идёт по каналу будильника, то есть
 * играет в беззвучном режиме и пробивается через «Не беспокоить». Обычный
 * медиа-канал в этих режимах промолчал бы.
 */
class AlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player != null

    fun start(uri: Uri, volume: Float) {
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
            Log.i(TAG, "звонок пошёл, громкость $volume")
        } catch (e: Exception) {
            Log.e(TAG, "мелодия не запустилась: $uri", e)
            releaseQuietly()
        }
    }

    fun setVolume(volume: Float) {
        try {
            player?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.w(TAG, "громкость не применилась", e)
        }
    }

    fun stop() {
        val current = player ?: return
        player = null
        try {
            if (current.isPlaying) current.stop()
        } catch (e: Exception) {
            Log.w(TAG, "остановка проигрывателя дала сбой", e)
        } finally {
            try {
                current.release()
            } catch (e: Exception) {
                Log.w(TAG, "проигрыватель не освободился", e)
            }
        }
    }

    private fun releaseQuietly() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
