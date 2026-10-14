package com.sasha.alarm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.sasha.alarm.ui.AppTheme
import com.sasha.alarm.ui.Themes

/**
 * Иконка на рабочем столе следует теме (образец — DrozdRun). Нарисовать значок на ходу Android не даёт —
 * только выбрать из заготовленных в APK, поэтому на каждую тему в манифесте свой псевдоним главного экрана
 * (`activity-alias`, генерирует `art/make_themes.py`), и включён ровно один.
 *
 * Переключение — при уходе с экрана настроек ([MainActivity.onStop]) и не во время тревоги: на части
 * лаунчеров смена псевдонима перезапускает процесс и убирает значок с рабочего стола.
 * ⚠️ Трогаются только псевдонимы `Icon_*`: слушатель уведомлений и пустышка `.HookNudge` — не наши.
 */
object LauncherIcon {
    private const val TAG = "LauncherIcon"

    /** Включает псевдоним темы [themeId] и гасит остальные. Ничего не делает, если он уже включён. */
    fun sync(context: Context, themeId: String) {
        val pm = context.packageManager
        val target = Themes.byId(themeId)
        if (enabled(context, target)) return
        // Сначала включить новый, потом гасить старые — чтобы ни на миг не остаться без значка.
        pm.setComponentEnabledSetting(component(context, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (theme in Themes.all) {
            if (theme !== target && enabled(context, theme)) {
                pm.setComponentEnabledSetting(component(context, theme), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
        Log.i(TAG, "иконка на рабочем столе — тема ${target.id}")
    }

    private fun enabled(context: Context, theme: AppTheme): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, theme))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> theme.id == Themes.DEFAULT_ID // так в манифесте
            else -> false
        }

    private fun component(context: Context, theme: AppTheme) =
        ComponentName(context.packageName, "com.sasha.alarm." + theme.alias)
}
