package com.sasha.alarm.platform

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Системная громкость канала будильника.
 *
 * Наши проценты — это доля от неё: при системном нуле любая наша громкость даёт
 * тишину. Поэтому перед звонком проверяем и, если ноль, поднимаем на время звонка,
 * а после — возвращаем как было. Ненулевую громкость не трогаем: раз владелец
 * что-то выставил, значит так и хотел.
 */
class SystemAlarmVolume(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var restoreTo: Int? = null

    /** Поднять, если система стоит в нуле. Возвращает true, если пришлось вмешаться. */
    fun raiseIfSilent(): Boolean {
        val manager = audio ?: return false
        return try {
            val current = manager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current > 0) return false

            val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            restoreTo = current
            manager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            Log.w(TAG, "громкость будильника в системе была 0, поднял до $max на время звонка")
            true
        } catch (e: SecurityException) {
            // Бывает в режиме «Не беспокоить» без особого доступа. Не повод падать.
            Log.w(TAG, "система не дала поменять громкость", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "громкость не поменялась", e)
            false
        }
    }

    /** Вернуть системную громкость, если мы её трогали. */
    fun restore() {
        val manager = audio ?: return
        val value = restoreTo ?: return
        restoreTo = null
        try {
            manager.setStreamVolume(AudioManager.STREAM_ALARM, value, 0)
            Log.i(TAG, "громкость будильника возвращена в $value")
        } catch (e: Exception) {
            Log.w(TAG, "громкость не вернулась", e)
        }
    }

    private companion object {
        const val TAG = "SystemAlarmVolume"
    }
}
