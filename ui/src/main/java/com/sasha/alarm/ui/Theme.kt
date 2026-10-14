package com.sasha.alarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Тема приложения — одна из 96 тем дизайн-кода «Мягкий» (`Themes.kt`, генерирует `art/make_themes.py`),
 * выбирается в окне настроек (владелец, 2026-10-14).
 *
 * ⚠️ Экран тревоги живёт в той же теме, что настройки (владелец, 2026-10-14: «экраны настроек и экраны,
 * когда я просыпаюсь от будильника, будут одинаковые»). Прежний сигнально-красный экран тревоги снят.
 * На экранах — только роли темы (`bg`, `surf`, `field`, `sel`, `text`, `muted`, `faint`, `accent`,
 * `onAccent`, `ok`, `warn`, `danger`), ни одного цвета числом.
 */
val LocalAppTheme = staticCompositionLocalOf { Themes.byId(Themes.DEFAULT_ID) }

/** Текущая тема экрана. */
val theme: AppTheme
    @Composable @ReadOnlyComposable get() = LocalAppTheme.current

@Composable
fun AlarmTheme(current: AppTheme, content: @Composable () -> Unit) {
    val scheme = if (current.dark) {
        darkColorScheme(
            primary = current.accent, onPrimary = current.onAccent,
            secondary = current.accent, onSecondary = current.onAccent,
            background = current.bg, onBackground = current.text,
            surface = current.surf, onSurface = current.text,
            surfaceVariant = current.field, onSurfaceVariant = current.muted,
            surfaceContainer = current.surf, surfaceContainerHigh = current.surf,
            outline = current.sel, outlineVariant = current.sel,
            error = current.danger, onError = current.onAccent,
        )
    } else {
        lightColorScheme(
            primary = current.accent, onPrimary = current.onAccent,
            secondary = current.accent, onSecondary = current.onAccent,
            background = current.bg, onBackground = current.text,
            surface = current.surf, onSurface = current.text,
            surfaceVariant = current.field, onSurfaceVariant = current.muted,
            surfaceContainer = current.surf, surfaceContainerHigh = current.surf,
            outline = current.sel, outlineVariant = current.sel,
            error = current.danger, onError = current.onAccent,
        )
    }
    CompositionLocalProvider(LocalAppTheme provides current) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
