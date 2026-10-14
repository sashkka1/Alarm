package com.sasha.alarm

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.sasha.alarm.ui.AppTheme
import com.sasha.alarm.ui.Themes

/**
 * Выбранная тема (`AppTheme.id`) — помнится между запусками; от неё же иконка на рабочем столе и
 * загрузочный экран. Одна на все экраны: настройки, тревогу, победу и заслонку (владелец, 2026-10-14).
 */
object ThemePrefs {
    private const val FILE = "theme"
    private const val KEY = "theme"

    fun id(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: Themes.DEFAULT_ID

    fun current(context: Context): AppTheme = Themes.byId(id(context))

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, theme.id).apply()
    }
}

/**
 * Фон окна и системные полосы — под тему (образец — DrozdRun). Полосы прозрачные, под ними фон экрана;
 * значки в полосах тёмные на светлой теме, светлые на тёмной. Фон окна виден в заминку до первого кадра
 * Compose — он того же цвета, что экран, поэтому заминки не видно.
 */
fun ComponentActivity.paintWindow(t: AppTheme) {
    window.setBackgroundDrawable(ColorDrawable(t.bg.toArgb()))
    val bars = if (t.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
}
