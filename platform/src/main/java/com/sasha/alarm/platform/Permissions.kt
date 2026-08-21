package com.sasha.alarm.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Разрешения, без которых экран тревоги не поднимется.
 *
 * Часть система читать не даёт (фирменные тумблеры Xiaomi) — такие помечены
 * [readable] = false, и владелец отмечает их галочкой сам.
 */
enum class PermissionId {
    NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    ACCESSIBILITY,
    FULL_SCREEN_INTENT,
    OVERLAY,
    EXACT_ALARM,
    BATTERY,

    /**
     * Камера для испытания «отжимания».
     *
     * ⚠️ Разрешение времени выполнения, а не тумблер в настройках: объявления в
     * манифесте мало. Ровно на этом сгорел счётчик шагов — он молча возвращал ноль,
     * потому что `ACTIVITY_RECOGNITION` никто не спросил у владельца.
     */
    CAMERA,

    /**
     * NFC для испытания «метки».
     *
     * Само разрешение выдаётся при установке, спрашивать нечего — но выключенный в
     * системе NFC не читает ни одной метки, и узнать об этом надо не в момент звонка.
     */
    NFC,

    XIAOMI_AUTOSTART,
    XIAOMI_BACKGROUND_POPUP,
}

data class PermissionStatus(
    val id: PermissionId,
    val granted: Boolean,
    /** Умеет ли система сказать, выдано ли разрешение. */
    val readable: Boolean,
)

object Permissions {

    private const val TAG = "Permissions"

    fun isXiaomi(): Boolean {
        val vendor = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return "xiaomi" in vendor || "redmi" in vendor || "poco" in vendor
    }

    /**
     * Полный список для экрана настроек. Фирменные пункты показываются только на Xiaomi.
     *
     * Камера и NFC появляются в списке, только если выбрано испытание, которому они
     * нужны: иначе счётчик «выдано N из M» вечно показывал бы недостачу за разрешение,
     * которое ничему не мешает.
     */
    fun all(
        context: Context,
        manual: Set<String>,
        needsCamera: Boolean = false,
        needsNfc: Boolean = false,
    ): List<PermissionStatus> {
        val list = mutableListOf(
            readable(PermissionId.NOTIFICATIONS, notificationsEnabled(context)),
            readable(PermissionId.NOTIFICATION_LISTENER, notificationListenerEnabled(context)),
            readable(PermissionId.ACCESSIBILITY, accessibilityEnabled(context)),
            readable(PermissionId.FULL_SCREEN_INTENT, fullScreenIntentAllowed(context)),
            readable(PermissionId.OVERLAY, Settings.canDrawOverlays(context)),
            readable(PermissionId.EXACT_ALARM, exactAlarmsAllowed(context)),
            readable(PermissionId.BATTERY, batteryUnrestricted(context)),
        )
        if (needsCamera) {
            list += readable(PermissionId.CAMERA, cameraAllowed(context))
        }
        if (needsNfc) {
            list += readable(PermissionId.NFC, nfcEnabled(context))
        }
        if (isXiaomi()) {
            list += manualStatus(PermissionId.XIAOMI_AUTOSTART, manual)
            list += manualStatus(PermissionId.XIAOMI_BACKGROUND_POPUP, manual)
        }
        return list
    }

    /** NFC включён в системе. Выключенный не читает ни одной метки. */
    fun nfcEnabled(context: Context): Boolean =
        context.getSystemService(NfcManager::class.java)?.defaultAdapter?.isEnabled == true

    fun cameraAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** Открывает системный экран, где выдаётся конкретное разрешение. */
    fun open(context: Context, id: PermissionId) {
        val intents = intentsFor(context, id) + appDetails(context)
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "не открылось: $intent", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "не открылось: $intent", e)
            }
        }
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Общий системный экран приложения: разрешения, уведомления, батарея в одном месте. */
    fun openAppDetails(context: Context) {
        try {
            context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "системный экран приложения не открылся", e)
        }
    }

    private fun intentsFor(context: Context, id: PermissionId): List<Intent> = when (id) {
        PermissionId.NOTIFICATIONS -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )

        PermissionId.NOTIFICATION_LISTENER -> listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )

        PermissionId.ACCESSIBILITY -> listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )

        PermissionId.FULL_SCREEN_INTENT -> listOf(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)),
        )

        PermissionId.OVERLAY -> listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)),
        )

        PermissionId.EXACT_ALARM -> listOf(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)),
        )

        PermissionId.BATTERY -> listOf(
            @Suppress("BatteryLife")
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        // Спрашивается диалогом времени выполнения; сюда попадаем, только если
        // владелец уже отказал — тогда единственный путь через системный экран.
        PermissionId.CAMERA -> emptyList()

        PermissionId.NFC -> listOf(Intent(Settings.ACTION_NFC_SETTINGS))

        PermissionId.XIAOMI_AUTOSTART -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
        )

        PermissionId.XIAOMI_BACKGROUND_POPUP -> listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                )
                .putExtra("extra_pkgname", context.packageName),
        )
    }

    private fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /**
     * Доступ к чужим уведомлениям. Без него зацеп за Sleep Cycle невозможен:
     * слушатель просто не получит ни одного события.
     */
    private fun notificationListenerEnabled(context: Context): Boolean = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        enabled.split(':').any { it.substringBefore('/') == context.packageName }
    } catch (e: Exception) {
        Log.w(TAG, "не удалось прочитать список слушателей уведомлений", e)
        false
    }

    private fun fullScreenIntentAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }

    /** Служба специальных возможностей приложения включена в системе. */
    private fun accessibilityEnabled(context: Context): Boolean = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        enabled.split(':').any { it.substringBefore('/') == context.packageName }
    } catch (e: Exception) {
        Log.w(TAG, "не удалось прочитать список служб специальных возможностей", e)
        false
    }

    private fun exactAlarmsAllowed(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun readable(id: PermissionId, granted: Boolean) =
        PermissionStatus(id, granted, readable = true)

    private fun manualStatus(id: PermissionId, manual: Set<String>) =
        PermissionStatus(id, granted = id.name in manual, readable = false)
}
