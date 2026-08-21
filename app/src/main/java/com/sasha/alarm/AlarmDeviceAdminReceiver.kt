package com.sasha.alarm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.DeviceOwner

/**
 * Точка, за которую система держит приложение как владельца устройства.
 *
 * Сама по себе ничего не делает: права появляются командой с компьютера
 * (`dpm set-device-owner`), а здесь мы только узнаём об этом и применяем политики.
 */
class AlarmDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "приложение стало администратором устройства")
        DeviceOwner.applyPermanentPolicies(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "права администратора устройства сняты")
    }

    private companion object {
        const val TAG = "AlarmDeviceAdmin"
    }
}
