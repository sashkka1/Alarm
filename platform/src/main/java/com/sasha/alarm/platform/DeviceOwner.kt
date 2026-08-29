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

    /**
     * Один шаг политики, отдельно от остальных.
     *
     * ⚠️ Каждый вызов обёрнут **по одному** намеренно. Вендорные прошивки любят
     * отказывать в отдельно взятой политике, и общий `try` на весь блок означал бы,
     * что первый же отказ молча выбрасывает всё, что шло после него. Особенно
     * больно это в [releaseHardLock] и [release]: там недоделанная уборка — это
     * телефон, с которого не уйти.
     */
    private inline fun step(what: String, action: () -> Unit) {
        try {
            action()
            Log.i(TAG, "политика применена: $what")
        } catch (e: Exception) {
            Log.e(TAG, "политика не применилась: $what", e)
        }
    }

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
        // Приложение нельзя удалить с самого телефона — только через кабель, сняв права.
        step("запрет удаления") { dpm.setUninstallBlocked(admin, context.packageName, true) }
        // Safe Mode отключил бы сторонние приложения вместе с нашим.
        step("запрет Safe Mode") { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        // Разрешения выдаются молча и **отозвать их с телефона нельзя**. Отозванная
        // ночью камера или уведомления — это молчащий будильник наутро (P0 №7, №8).
        for (permission in AUTO_GRANTED) {
            step("выдача $permission") {
                dpm.setPermissionGrantState(
                    admin,
                    context.packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }
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
        val admin = admin(context)
        step("закрепление разрешено") {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        }
        // ⚠️ Единственное, чем Android даёт закрыть меню питания: пока экран
        // закреплён и снят LOCK_TASK_FEATURE_GLOBAL_ACTIONS, долгое нажатие на
        // питание не открывает ничего — то есть выключить и перезагрузить телефон
        // из меню нельзя. Аппаратное удержание ~10 с этим не закрывается, это
        // прошивка; после такой перезагрузки экран поднимает BootReceiver.
        //
        // Нулём разом снимаются: меню питания, Home, Recents, шторка, системные
        // значки и замок экрана. Замок здесь не лишний: без него метки NFC не
        // читаются вовсе (система не поллит считыватель на запертом экране).
        step("системные кнопки отключены") {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
        step("шторка отключена") { dpm.setStatusBarDisabled(admin, true) }
        // Приложение нельзя ни остановить, ни выкинуть из недавних. Закрывает
        // дыру, проверенную в бою 2026-08-14: «очистить всё» убивало процесс.
        step("защита от остановки") {
            dpm.setUserControlDisabledPackages(admin, listOf(context.packageName))
        }
        // ⛔ `DISALLOW_ADJUST_VOLUME` здесь НЕ ставится и ставиться не должно.
        // Документация Android про него: «If set, the master volume will be muted».
        // То есть запрет менять громкость глушит звук целиком — это буквально
        // тишина вместо будильника, худший из возможных отказов (P0 №7). Заодно он
        // отобрал бы громкость и у нас: `SystemAlarmVolume.raiseIfSilent` поднимает
        // системный ноль через тот же `setStreamVolume`.
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
        // Сначала самое главное: без пакета в списке система выходит из Lock Task сама.
        step("закрепление разорвано") { dpm.setLockTaskPackages(admin, emptyArray()) }
        // Меню питания обратно на место — телефон снова можно выключить.
        step("меню питания возвращено") {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        }
        step("шторка возвращена") { dpm.setStatusBarDisabled(admin, false) }
        step("остановка снова разрешена") {
            dpm.setUserControlDisabledPackages(admin, emptyList())
        }
        // Снимаем на случай, если ограничение осталось от прежней сборки: пока оно
        // стоит, система держит звук замьюченным целиком.
        step("громкость снова меняется") {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADJUST_VOLUME)
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
        val admin = admin(context)
        // ⚠️ Каждый шаг сам по себе, и `clearDeviceOwnerApp` идёт последним при
        // любом исходе предыдущих. Один общий `try` означал бы, что первый же
        // отказ вендорной прошивки оставляет права владельца на месте — то есть
        // ровно то, ради чего этот метод существует, не происходит.
        releaseHardLock(context)
        step("запрет удаления снят") {
            dpm.setUninstallBlocked(admin, context.packageName, false)
        }
        step("Safe Mode разрешён") {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
        for (permission in AUTO_GRANTED) {
            step("$permission отдано обратно пользователю") {
                dpm.setPermissionGrantState(
                    admin,
                    context.packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
                )
            }
        }
        return try {
            dpm.clearDeviceOwnerApp(context.packageName)
            Log.i(TAG, "права владельца устройства сняты, приложение можно удалять")
            true
        } catch (e: Exception) {
            Log.e(TAG, "не удалось снять права владельца устройства", e)
            false
        }
    }

    /**
     * Разрешения, которые владелец устройства выдаёт себе сам.
     *
     * Оба нужны критическому пути: без камеры не считаются отжимания, без
     * уведомлений не поднимется экран через full-screen intent.
     */
    private val AUTO_GRANTED = listOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    /** Полное имя класса-администратора. Держим строкой: класс живёт в модуле `:app`. */
    private const val ADMIN_CLASS = "com.sasha.alarm.AlarmDeviceAdminReceiver"
}
