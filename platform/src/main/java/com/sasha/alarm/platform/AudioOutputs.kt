package com.sasha.alarm.platform

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

/**
 * Куда уйдёт звук будильника.
 *
 * Наушники или колонка, подключённые на ночь, — тихий способ проспать: звук уходит
 * в них, а телефон рядом молчит. Перебить маршрут звука публичным API нельзя,
 * поэтому единственное, что можно сделать, — предупредить заранее.
 */
object AudioOutputs {

    private const val TAG = "AudioOutputs"

    fun headphonesConnected(context: Context): Boolean = try {
        val manager = context.getSystemService(AudioManager::class.java)
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in EXTERNAL_TYPES }
    } catch (e: Exception) {
        Log.w(TAG, "не удалось прочитать список аудиовыходов", e)
        false
    }

    private val EXTERNAL_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
}
