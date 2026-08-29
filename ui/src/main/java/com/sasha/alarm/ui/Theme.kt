package com.sasha.alarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Тема приложения: тёмная, чёрно-красная. Одна, без светлого варианта —
 * приложение открывают ночью и спросонья, и белая простыня в глаза тут ни к чему.
 *
 * ⚠️ Экран тревоги живёт по своим правилам: он сигнально-красный с чёрным текстом и
 * на полной яркости, потому что его задача не беречь глаза, а разбудить.
 */
object AlarmColors {
    /**
     * Сигнальный красный экрана тревоги.
     *
     * Лежит здесь, а не только на самом экране: тем же цветом красится фон окна до
     * первого кадра Compose (`app/res/values/themes.xml`) и заслонка-оверлей. Иначе в
     * стыке между ними мелькает посторонний цвет — а он читается как поломка.
     */
    val Signal = Color(0xFFFF0026)

    /** Фон экрана — тёплый чёрный, а не синеватый. */
    val Background = Color(0xFF14110F)

    /** Карточки и строки списка. */
    val Surface = Color(0xFF1D1815)

    /** То, что лежит поверх карточки: кнопки, плашки, значения. */
    val SurfaceRaised = Color(0xFF241E1A)

    val Outline = Color(0xFF302A26)

    /** Дорожка ползунка — светлее рамки, иначе её не видно. */
    val Track = Color(0xFF3A332E)

    /** Обожжённый оранжевый. Единственный яркий цвет во всём приложении. */
    val Accent = Color(0xFFC24A32)

    /** Фон плашки, которая должна тревожить: почти чёрный с краснотой. */
    val AccentSoft = Color(0xFF211613)

    val TextPrimary = Color(0xFFECE5E0)
    val TextSecondary = Color(0xFF8A7E77)

    /** Совсем тихие подписи — пояснения под настройками. */
    val TextDim = Color(0xFF6B625C)

    /** Числовые значения рядом с ползунками. */
    val Value = Color(0xFFC08A6E)

    val Good = Color(0xFF00E07A)

    /**
     * Погасшая точка состояния.
     *
     * Серая, а не красная: невыданное разрешение — это «ещё не сделано», а не авария.
     * Красным в этом приложении кричит только то, что и правда требует внимания.
     */
    val DotOff = Color(0xFF4A423C)
}

private val Scheme = darkColorScheme(
    primary = AlarmColors.Accent,
    // По акценту пишем чёрным, а не белым: он светлый, и белое по нему не читается.
    onPrimary = Color.Black,
    primaryContainer = AlarmColors.AccentSoft,
    onPrimaryContainer = AlarmColors.TextPrimary,
    secondary = AlarmColors.Accent,
    onSecondary = Color.Black,
    background = AlarmColors.Background,
    onBackground = AlarmColors.TextPrimary,
    surface = AlarmColors.Surface,
    onSurface = AlarmColors.TextPrimary,
    surfaceVariant = AlarmColors.SurfaceRaised,
    onSurfaceVariant = AlarmColors.TextSecondary,
    surfaceContainer = AlarmColors.Surface,
    surfaceContainerHigh = AlarmColors.SurfaceRaised,
    outline = AlarmColors.Outline,
    outlineVariant = AlarmColors.Outline,
    error = AlarmColors.Accent,
    onError = Color.Black,
)

@Composable
fun AlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
