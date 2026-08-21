package com.sasha.alarm.platform

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

/**
 * Жёсткая блокировка через Device Owner.
 *
 * Всё, что здесь есть, работает **только** если приложение назначено владельцем
 * устройства командой с компьютера. Пока не назначено, каждый метод — тихий no-op,
 * и приложение ведёт себя как раньше.
 *
 * ⚠️ Два пути выхода оставлены сознательно (решение владельца 2026-08-14):
 *  1. **Кабель** — [release] снимает права владельца устройства, после чего приложение
 *     можно удалить обычным `adb uninstall`.
 *  2. **Выключение телефона** — состояние тревоги при загрузке чистится, экран не
 *     возвращается. Это единственный выход без компьютера.
 *
 * Всё остальное закрыто: из экрана не выйти, шторки нет, приложение с телефона
 * не удалить, Safe Mode запрещён.
 */
object DeviceOwner {

    private const val TAG = "DeviceOwner"

    private fun manager(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    fun admin(context: Context): ComponentName =
        ComponentName(context.packageName, ADMIN_CLASS)

    fun isActive(context: Context): Boolean =
        manager(context)?.isDeviceOwnerApp(context.packageName) == true

    /**
     * Приложение — администратор устройства (не владелец).
     *
     * Это отдельная, куда более скромная роль: она **не** даёт ни закрепления экрана,
     * ни отключения шторки. Но пока она включена, **приложение нельзя удалить** — сперва
     * придётся снять права в системных настройках. Выдаётся обычным тапом, аккаунты
     * с телефона сносить не нужно, и это её главное достоинство перед Device Owner.
     */
    fun isAdminActive(context: Context): Boolean =
        manager(context)?.isAdminActive(admin(context)) == true

    /**
     * Постоянные ограничения — ставятся один раз, как только приложение стало владельцем.
     * Живут независимо от того, звонит будильник или нет.
     */
    fun applyPermanentPolicies(context: Context) {
        val dpm = manager(context) ?: return
        if (!isActive(context)) return
        val admin = admin(context)
        try {
            // Приложение нельзя удалить с самого телефона — только через кабель, сняв права.
            dpm.setUninstallBlocked(admin, context.packageName, true)
            // Safe Mode отключил бы сторонние приложения вместе с нашим.
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            Log.i(TAG, "постоянные политики применены")
        } catch (e: Exception) {
            Log.e(TAG, "не удалось применить постоянные политики", e)
        }
    }

    /**
     * Включить жёсткий режим на время тревоги: шторка и системные кнопки мертвы.
     *
     * Разрешение закреплять себя выдаётся **здесь, а не навсегда**: пока тревоги нет,
     * список закрепляемых пуст, и запереть экран невозможно даже случайно.
     */
    fun engageHardLock(context: Context) {
        val dpm = manager(context) ?: return
        if (!isActive(context)) return
        try {
            dpm.setLockTaskPackages(admin(context), arrayOf(context.packageName))
            dpm.setStatusBarDisabled(admin(context), true)
            Log.i(TAG, "жёсткий режим включён: шторка отключена, закрепление разрешено")
        } catch (e: Exception) {
            Log.e(TAG, "жёсткий режим не включился", e)
        }
    }

    /**
     * Снять жёсткий режим.
     *
     * ⚠️ **Это единственный способ разорвать закрепление экрана из чужого процесса.**
     * `stopLockTask()` есть только у активити, и если UI-процесс умер или завис,
     * сторож `:guard` вызвать его не может. А вот убрать приложение из списка
     * закрепляемых — может: система сама выходит из Lock Task, когда пакет из
     * списка пропал. Без этого зависший UI запирал бы телефон навсегда.
     */
    fun releaseHardLock(context: Context) {
        val dpm = manager(context) ?: return
        if (!isActive(context)) return
        val admin = admin(context)
        try {
            dpm.setLockTaskPackages(admin, emptyArray())
            Log.i(TAG, "закрепление разорвано")
        } catch (e: Exception) {
            Log.e(TAG, "не удалось разорвать закрепление", e)
        }
        try {
            dpm.setStatusBarDisabled(admin, false)
            Log.i(TAG, "шторка возвращена")
        } catch (e: Exception) {
            Log.e(TAG, "шторка не вернулась", e)
        }
    }

    /**
     * ПУТЬ К ОТСТУПЛЕНИЮ. Полностью отказывается от прав владельца устройства.
     *
     * После этого приложение — обычное, его можно удалить `adb uninstall`.
     * Вызывается только снаружи, через `EscapeReceiver`, то есть с компьютера.
     */
    fun release(context: Context): Boolean {
        val dpm = manager(context) ?: return false
        if (!isActive(context)) {
            Log.i(TAG, "прав владельца устройства и так нет")
            return true
        }
        return try {
            val admin = admin(context)
            dpm.setStatusBarDisabled(admin, false)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.clearDeviceOwnerApp(context.packageName)
            Log.i(TAG, "права владельца устройства сняты, приложение можно удалять")
            true
        } catch (e: Exception) {
            Log.e(TAG, "не удалось снять права владельца устройства", e)
            false
        }
    }

    /** Полное имя класса-администратора. Держим строкой: класс живёт в модуле `:app`. */
    private const val ADMIN_CLASS = "com.sasha.alarm.AlarmDeviceAdminReceiver"
}
