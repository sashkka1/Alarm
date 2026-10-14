// Генерирует art/make_themes.py из art/themes.json — руками не править.
package com.sasha.alarm.ui

import androidx.compose.ui.graphics.Color

/** Семейство тем — первый уровень палитры. [main] — тема, чьи два цвета рисуют плитку семейства. */
class ThemeFamily(val id: String, val name: String, val main: AppTheme, val themes: List<AppTheme>)

/** Тема дизайн-кода «Мягкий»: два основных цвета — фон [bg] и текст [text], остальное выведено из них. */
class AppTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val surf: Color,
    val field: Color,
    val sel: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
) {
    /** Псевдоним главного экрана с иконкой этой темы (манифест). */
    val alias: String get() = "Icon_" + id.replace('-', '_')
}

object Themes {
    const val DEFAULT_ID = "red-2"

    private val t_red_1 = AppTheme(
        "red-1", "Красный · Коралл", true,
        bg = Color(0xFF210201), surf = Color(0xFF2D0805), field = Color(0xFF270503), sel = Color(0xFF3B140F),
        text = Color(0xFFFFE9DD), muted = Color(0xFFB69A95), faint = Color(0xFF7F6560),
        accent = Color(0xFFF48763), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_red_2 = AppTheme(
        "red-2", "Красный · Золото", true,
        bg = Color(0xFF2C0806), surf = Color(0xFF38110E), field = Color(0xFF310D0A), sel = Color(0xFF471C19),
        text = Color(0xFFFBECD9), muted = Color(0xFFB79995), faint = Color(0xFF806461),
        accent = Color(0xFFEB9B3E), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_red_3 = AppTheme(
        "red-3", "Красный · Светлая", false,
        bg = Color(0xFFF6DEC5), surf = Color(0xFFF5E9DC), field = Color(0xFFF2D8BC), sel = Color(0xFFEDCEAD),
        text = Color(0xFF3A0E0B), muted = Color(0xFF735551), faint = Color(0xFFAA928E),
        accent = Color(0xFFBE2423), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFB54076),
    )
    private val t_red_4 = AppTheme(
        "red-4", "Красный · Пламя", true,
        bg = Color(0xFF1E0201), surf = Color(0xFF2A0805), field = Color(0xFF230503), sel = Color(0xFF38130E),
        text = Color(0xFFFCEBDA), muted = Color(0xFFB59A96), faint = Color(0xFF7E6561),
        accent = Color(0xFFBF510B), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_red_5 = AppTheme(
        "red-5", "Красный · Искры", true,
        bg = Color(0xFF190201), surf = Color(0xFF240704), field = Color(0xFF1E0402), sel = Color(0xFF32110D),
        text = Color(0xFFFFEADB), muted = Color(0xFFB39B97), faint = Color(0xFF7C6662),
        accent = Color(0xFFE3483F), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_red_6 = AppTheme(
        "red-6", "Красный · Алый", true,
        bg = Color(0xFF1B0201), surf = Color(0xFF260705), field = Color(0xFF200403), sel = Color(0xFF34120E),
        text = Color(0xFFFFE7E4), muted = Color(0xFFB49B96), faint = Color(0xFF7D6561),
        accent = Color(0xFFE3483F), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_red_7 = AppTheme(
        "red-7", "Красный · Насыщенная", true,
        bg = Color(0xFF910D10), surf = Color(0xFFA21919), field = Color(0xFF981817), sel = Color(0xFFB62824),
        text = Color(0xFFFFE7E3), muted = Color(0xFFE4C1BC), faint = Color(0xFFB69591),
        accent = Color(0xFFFFDCD6), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB6D4),
    )
    private val t_red_8 = AppTheme(
        "red-8", "Красный · Жар", true,
        bg = Color(0xFF140201), surf = Color(0xFF1F0705), field = Color(0xFF190403), sel = Color(0xFF2C120E),
        text = Color(0xFFFDEBDA), muted = Color(0xFFB09C99), faint = Color(0xFF7A6764),
        accent = Color(0xFFD33E10), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_terracotta_1 = AppTheme(
        "terracotta-1", "Терракота · Классика", true,
        bg = Color(0xFF190601), surf = Color(0xFF230D05), field = Color(0xFF1E0903), sel = Color(0xFF31190E),
        text = Color(0xFFFFEADB), muted = Color(0xFFAF9D96), faint = Color(0xFF786861),
        accent = Color(0xFFF28D61), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_terracotta_2 = AppTheme(
        "terracotta-2", "Терракота · Металл", true,
        bg = Color(0xFF230902), surf = Color(0xFF2E1106), field = Color(0xFF280D04), sel = Color(0xFF3D1D11),
        text = Color(0xFFF8EDD8), muted = Color(0xFFB29C93), faint = Color(0xFF7B675F),
        accent = Color(0xFFE09337), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_terracotta_3 = AppTheme(
        "terracotta-3", "Терракота · Светлая", false,
        bg = Color(0xFFF3DABF), surf = Color(0xFFF1E5D7), field = Color(0xFFEFD4B6), sel = Color(0xFFEACAA7),
        text = Color(0xFF391002), muted = Color(0xFF72564D), faint = Color(0xFFAA928B),
        accent = Color(0xFFAD471C), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFB54076),
    )
    private val t_terracotta_4 = AppTheme(
        "terracotta-4", "Терракота · Двухтон", true,
        bg = Color(0xFF170601), surf = Color(0xFF210D05), field = Color(0xFF1C0903), sel = Color(0xFF2E180E),
        text = Color(0xFFFDEBDD), muted = Color(0xFFAE9E96), faint = Color(0xFF776861),
        accent = Color(0xFFE27344), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_terracotta_5 = AppTheme(
        "terracotta-5", "Терракота · Искры", true,
        bg = Color(0xFF150401), surf = Color(0xFF1F0B04), field = Color(0xFF1A0702), sel = Color(0xFF2C160D),
        text = Color(0xFFFFEADC), muted = Color(0xFFAE9D97), faint = Color(0xFF776862),
        accent = Color(0xFFBD4F17), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_terracotta_6 = AppTheme(
        "terracotta-6", "Терракота · Вариация", true,
        bg = Color(0xFF160101), surf = Color(0xFF210604), field = Color(0xFF1B0302), sel = Color(0xFF2F100D),
        text = Color(0xFFFFE8E1), muted = Color(0xFFB29C98), faint = Color(0xFF7B6663),
        accent = Color(0xFFD04316), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_terracotta_7 = AppTheme(
        "terracotta-7", "Терракота · Насыщенная", true,
        bg = Color(0xFF882500), surf = Color(0xFF982E05), field = Color(0xFF8E2B05), sel = Color(0xFFAB3B14),
        text = Color(0xFFFFE8E0), muted = Color(0xFFE3C2B8), faint = Color(0xFFB6968D),
        accent = Color(0xFFFFDDD0), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB6D4),
    )
    private val t_terracotta_8 = AppTheme(
        "terracotta-8", "Терракота · Сияние", true,
        bg = Color(0xFF1A0803), surf = Color(0xFF241108), field = Color(0xFF1F0D05), sel = Color(0xFF321C12),
        text = Color(0xFFF4EDE5), muted = Color(0xFFAD9E97), faint = Color(0xFF776862),
        accent = Color(0xFFE89C7E), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_amber_1 = AppTheme(
        "amber-1", "Янтарь · Классика", true,
        bg = Color(0xFF180B01), surf = Color(0xFF221406), field = Color(0xFF1C0F03), sel = Color(0xFF2F1F0F),
        text = Color(0xFFF8EDDA), muted = Color(0xFFAA9F95), faint = Color(0xFF746A60),
        accent = Color(0xFFEFB45D), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_amber_2 = AppTheme(
        "amber-2", "Янтарь · Металл", true,
        bg = Color(0xFF1C0E04), surf = Color(0xFF26170B), field = Color(0xFF211308), sel = Color(0xFF332316),
        text = Color(0xFFF0EEEC), muted = Color(0xFFAB9F96), faint = Color(0xFF746A61),
        accent = Color(0xFFB6AFA5), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_amber_3 = AppTheme(
        "amber-3", "Янтарь · Светлая", false,
        bg = Color(0xFFF5E1C0), surf = Color(0xFFF5EBDA), field = Color(0xFFF1DBB7), sel = Color(0xFFEBD1A6),
        text = Color(0xFF351500), muted = Color(0xFF6E5946), faint = Color(0xFFA69586),
        accent = Color(0xFFAF5C00), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFB54076),
    )
    private val t_amber_4 = AppTheme(
        "amber-4", "Янтарь · Двухтон", true,
        bg = Color(0xFF170B01), surf = Color(0xFF201405), field = Color(0xFF1B0F03), sel = Color(0xFF2D1F0F),
        text = Color(0xFFF8EDDC), muted = Color(0xFFAAA095), faint = Color(0xFF736A60),
        accent = Color(0xFFE4B978), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_amber_5 = AppTheme(
        "amber-5", "Янтарь · Искры", true,
        bg = Color(0xFF170B01), surf = Color(0xFF201405), field = Color(0xFF1B0F03), sel = Color(0xFF2D1F0F),
        text = Color(0xFFFAECD9), muted = Color(0xFFAAA095), faint = Color(0xFF736A60),
        accent = Color(0xFFD78820), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_amber_6 = AppTheme(
        "amber-6", "Янтарь · Вариация", true,
        bg = Color(0xFF150801), surf = Color(0xFF1E1105), field = Color(0xFF190C03), sel = Color(0xFF2B1C0E),
        text = Color(0xFFFDEBDA), muted = Color(0xFFAA9F96), faint = Color(0xFF746A61),
        accent = Color(0xFFE98B05), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_amber_7 = AppTheme(
        "amber-7", "Янтарь · Насыщенная", true,
        bg = Color(0xFF7A3500), surf = Color(0xFF883F00), field = Color(0xFF803B00), sel = Color(0xFF9B4C00),
        text = Color(0xFFFFE9DD), muted = Color(0xFFDEC6B0), faint = Color(0xFFB19A85),
        accent = Color(0xFFFFE2C6), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB6D4),
    )
    private val t_amber_8 = AppTheme(
        "amber-8", "Янтарь · Сияние", true,
        bg = Color(0xFF1F0F04), surf = Color(0xFF29180B), field = Color(0xFF241408), sel = Color(0xFF372416),
        text = Color(0xFFF5EEDD), muted = Color(0xFFAC9F95), faint = Color(0xFF756961),
        accent = Color(0xFFD8BC90), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_1 = AppTheme(
        "olive-1", "Олива · Классика", true,
        bg = Color(0xFF101004), surf = Color(0xFF19190B), field = Color(0xFF151507), sel = Color(0xFF252515),
        text = Color(0xFFF1EFDE), muted = Color(0xFFA2A298), faint = Color(0xFF6C6D63),
        accent = Color(0xFFB8B567), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_2 = AppTheme(
        "olive-2", "Олива · Металл", true,
        bg = Color(0xFF121106), surf = Color(0xFF1B1A0D), field = Color(0xFF171609), sel = Color(0xFF272618),
        text = Color(0xFFF7EED8), muted = Color(0xFFA3A298), faint = Color(0xFF6D6C63),
        accent = Color(0xFFC3984A), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_3 = AppTheme(
        "olive-3", "Олива · Светлая", false,
        bg = Color(0xFFEDE5CA), surf = Color(0xFFF1EDE0), field = Color(0xFFE8DFC1), sel = Color(0xFFE1D6B3),
        text = Color(0xFF222101), muted = Color(0xFF5F6045), faint = Color(0xFF9A9A85),
        accent = Color(0xFF696825), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_olive_4 = AppTheme(
        "olive-4", "Олива · Двухтон", true,
        bg = Color(0xFF111104), surf = Color(0xFF1A1A0B), field = Color(0xFF161607), sel = Color(0xFF262616),
        text = Color(0xFFF1F0DA), muted = Color(0xFFA2A297), faint = Color(0xFF6C6D62),
        accent = Color(0xFFC6C381), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_5 = AppTheme(
        "olive-5", "Олива · Искры", true,
        bg = Color(0xFF0F0F04), surf = Color(0xFF18180B), field = Color(0xFF141407), sel = Color(0xFF242415),
        text = Color(0xFFEFF1DA), muted = Color(0xFFA2A298), faint = Color(0xFF6C6D63),
        accent = Color(0xFF81833A), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_6 = AppTheme(
        "olive-6", "Олива · Вариация", true,
        bg = Color(0xFF101004), surf = Color(0xFF19190B), field = Color(0xFF151507), sel = Color(0xFF252515),
        text = Color(0xFFEFF1DA), muted = Color(0xFFA2A298), faint = Color(0xFF6C6D63),
        accent = Color(0xFF949A1A), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_olive_7 = AppTheme(
        "olive-7", "Олива · Насыщенная", true,
        bg = Color(0xFF4E4D08), surf = Color(0xFF595812), field = Color(0xFF535211), sel = Color(0xFF67661F),
        text = Color(0xFFF0EFE0), muted = Color(0xFFCCCDB0), faint = Color(0xFFA0A184),
        accent = Color(0xFFE9EAD1), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_olive_8 = AppTheme(
        "olive-8", "Олива · Сияние", true,
        bg = Color(0xFF151306), surf = Color(0xFF1F1D0D), field = Color(0xFF1A1809), sel = Color(0xFF2B2918),
        text = Color(0xFFF1EFE0), muted = Color(0xFFA3A297), faint = Color(0xFF6D6C62),
        accent = Color(0xFF827740), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_1 = AppTheme(
        "green-1", "Зелёный · Классика", true,
        bg = Color(0xFF04120B), surf = Color(0xFF0B1C13), field = Color(0xFF08170F), sel = Color(0xFF16281F),
        text = Color(0xFFEFEFE3), muted = Color(0xFF99A59E), faint = Color(0xFF646F69),
        accent = Color(0xFF6E9C66), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_2 = AppTheme(
        "green-2", "Зелёный · Металл", true,
        bg = Color(0xFF04130B), surf = Color(0xFF0C1D13), field = Color(0xFF08180F), sel = Color(0xFF17291E),
        text = Color(0xFFF9EDD8), muted = Color(0xFF99A59D), faint = Color(0xFF646F68),
        accent = Color(0xFFE2AA58), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_3 = AppTheme(
        "green-3", "Зелёный · Светлая", false,
        bg = Color(0xFFEBE1C2), surf = Color(0xFFEEE9DA), field = Color(0xFFE6DBB9), sel = Color(0xFFDFD2AA),
        text = Color(0xFF0A260F), muted = Color(0xFF4F6451), faint = Color(0xFF8C9E8E),
        accent = Color(0xFF3B7345), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_green_4 = AppTheme(
        "green-4", "Зелёный · Двухтон", true,
        bg = Color(0xFF04130B), surf = Color(0xFF0C1D13), field = Color(0xFF08180F), sel = Color(0xFF17291E),
        text = Color(0xFFEEF0E4), muted = Color(0xFF99A59D), faint = Color(0xFF646F68),
        accent = Color(0xFF68A673), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_5 = AppTheme(
        "green-5", "Зелёный · Искры", true,
        bg = Color(0xFF03110A), surf = Color(0xFF091B12), field = Color(0xFF06160E), sel = Color(0xFF14271D),
        text = Color(0xFFF9EDDA), muted = Color(0xFF99A59E), faint = Color(0xFF646F68),
        accent = Color(0xFF4B8A56), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_6 = AppTheme(
        "green-6", "Зелёный · Вариация", true,
        bg = Color(0xFF03110A), surf = Color(0xFF091B12), field = Color(0xFF06160E), sel = Color(0xFF14271D),
        text = Color(0xFFE2F4E4), muted = Color(0xFF99A59E), faint = Color(0xFF646F68),
        accent = Color(0xFF5A9665), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_green_7 = AppTheme(
        "green-7", "Зелёный · Насыщенная", true,
        bg = Color(0xFF335133), surf = Color(0xFF3C5D3C), field = Color(0xFF385738), sel = Color(0xFF486B48),
        text = Color(0xFFE3F3EC), muted = Color(0xFFBCD1BC), faint = Color(0xFF91A590),
        accent = Color(0xFFDEEDDE), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_green_8 = AppTheme(
        "green-8", "Зелёный · Сияние", true,
        bg = Color(0xFF06140C), surf = Color(0xFF0E1E15), field = Color(0xFF0A1910), sel = Color(0xFF192A20),
        text = Color(0xFFF1EFE3), muted = Color(0xFF99A59E), faint = Color(0xFF646F68),
        accent = Color(0xFF729166), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_1 = AppTheme(
        "teal-1", "Бирюза · Классика", true,
        bg = Color(0xFF021516), surf = Color(0xFF081F20), field = Color(0xFF051A1B), sel = Color(0xFF132B2C),
        text = Color(0xFFE2F3EF), muted = Color(0xFF96A5A5), faint = Color(0xFF616F6F),
        accent = Color(0xFF36B4AD), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_2 = AppTheme(
        "teal-2", "Бирюза · Металл", true,
        bg = Color(0xFF021A1B), surf = Color(0xFF092425), field = Color(0xFF061F20), sel = Color(0xFF143132),
        text = Color(0xFFF7EED8), muted = Color(0xFF95A5A6), faint = Color(0xFF606F70),
        accent = Color(0xFFE6B969), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_3 = AppTheme(
        "teal-3", "Бирюза · Светлая", false,
        bg = Color(0xFFD7EEE8), surf = Color(0xFFE8F3F0), field = Color(0xFFCFE9E2), sel = Color(0xFFC3E1D9),
        text = Color(0xFF002629), muted = Color(0xFF426566), faint = Color(0xFF839EA0),
        accent = Color(0xFF068B8F), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_teal_4 = AppTheme(
        "teal-4", "Бирюза · Двухтон", true,
        bg = Color(0xFF021415), surf = Color(0xFF081E1F), field = Color(0xFF05191A), sel = Color(0xFF132A2B),
        text = Color(0xFFE3F2F0), muted = Color(0xFF97A5A5), faint = Color(0xFF626F6F),
        accent = Color(0xFF2BA099), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_5 = AppTheme(
        "teal-5", "Бирюза · Искры", true,
        bg = Color(0xFF021516), surf = Color(0xFF081F20), field = Color(0xFF051A1B), sel = Color(0xFF132B2C),
        text = Color(0xFFD9F5F3), muted = Color(0xFF96A5A5), faint = Color(0xFF616F6F),
        accent = Color(0xFF159B97), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_6 = AppTheme(
        "teal-6", "Бирюза · Вариация", true,
        bg = Color(0xFF021010), surf = Color(0xFF071A19), field = Color(0xFF041515), sel = Color(0xFF112625),
        text = Color(0xFFD9F5F3), muted = Color(0xFF97A5A4), faint = Color(0xFF626F6E),
        accent = Color(0xFF21D3CD), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_teal_7 = AppTheme(
        "teal-7", "Бирюза · Насыщенная", true,
        bg = Color(0xFF005452), surf = Color(0xFF00605E), field = Color(0xFF005A58), sel = Color(0xFF006F6D),
        text = Color(0xFFE0F3F2), muted = Color(0xFFAED3D1), faint = Color(0xFF82A7A5),
        accent = Color(0xFFCFF0ED), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_teal_8 = AppTheme(
        "teal-8", "Бирюза · Сияние", true,
        bg = Color(0xFF041A1B), surf = Color(0xFF0B2324), field = Color(0xFF071E1F), sel = Color(0xFF163031),
        text = Color(0xFFE9F0EE), muted = Color(0xFF96A5A5), faint = Color(0xFF616F6F),
        accent = Color(0xFF4F9393), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_1 = AppTheme(
        "blue-1", "Синий · Классика", true,
        bg = Color(0xFF010E21), surf = Color(0xFF06182C), field = Color(0xFF041326), sel = Color(0xFF10243A),
        text = Color(0xFFE9EFF5), muted = Color(0xFF96A3B2), faint = Color(0xFF616D7B),
        accent = Color(0xFF4A8EE2), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_2 = AppTheme(
        "blue-2", "Синий · Золото", true,
        bg = Color(0xFF02132C), surf = Color(0xFF071C38), field = Color(0xFF041831), sel = Color(0xFF122947),
        text = Color(0xFFF9EDD8), muted = Color(0xFF94A3B7), faint = Color(0xFF5F6D80),
        accent = Color(0xFFD9AA64), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_3 = AppTheme(
        "blue-3", "Синий · Светлая", false,
        bg = Color(0xFFC6E1F6), surf = Color(0xFFDAE7F2), field = Color(0xFFBEDBF2), sel = Color(0xFFB0D3EE),
        text = Color(0xFF071E40), muted = Color(0xFF505F74), faint = Color(0xFF8D99AB),
        accent = Color(0xFF1959A7), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_blue_4 = AppTheme(
        "blue-4", "Синий · Двухтон", true,
        bg = Color(0xFF010F24), surf = Color(0xFF06182F), field = Color(0xFF041329), sel = Color(0xFF10243E),
        text = Color(0xFFE9EFF5), muted = Color(0xFF95A3B5), faint = Color(0xFF616D7D),
        accent = Color(0xFF4292E6), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_5 = AppTheme(
        "blue-5", "Синий · Искры", true,
        bg = Color(0xFF010C1D), surf = Color(0xFF061527), field = Color(0xFF031122), sel = Color(0xFF0F2135),
        text = Color(0xFFE1F0FF), muted = Color(0xFF97A3B1), faint = Color(0xFF626D7A),
        accent = Color(0xFF4C83E3), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_6 = AppTheme(
        "blue-6", "Синий · Серебро", true,
        bg = Color(0xFF02142D), surf = Color(0xFF071D39), field = Color(0xFF051932), sel = Color(0xFF122A48),
        text = Color(0xFFE9EFF6), muted = Color(0xFF94A3B7), faint = Color(0xFF5F6D80),
        accent = Color(0xFF88A6C8), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_blue_7 = AppTheme(
        "blue-7", "Синий · Насыщенная", true,
        bg = Color(0xFF1652A5), surf = Color(0xFF1E5DB6), field = Color(0xFF1D58AC), sel = Color(0xFF2A6CCB),
        text = Color(0xFFE2F0FF), muted = Color(0xFFBBCCE5), faint = Color(0xFF8FA0B8),
        accent = Color(0xFFD4EAFF), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_blue_8 = AppTheme(
        "blue-8", "Синий · Сияние", true,
        bg = Color(0xFF010C1D), surf = Color(0xFF061527), field = Color(0xFF031122), sel = Color(0xFF0F2135),
        text = Color(0xFFEAEFF4), muted = Color(0xFF97A3B1), faint = Color(0xFF626D7A),
        accent = Color(0xFF84A7CA), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_1 = AppTheme(
        "indigo-1", "Индиго · Классика", true,
        bg = Color(0xFF050A1E), surf = Color(0xFF0C1329), field = Color(0xFF080E23), sel = Color(0xFF171E37),
        text = Color(0xFFEBEEFA), muted = Color(0xFF9BA1B2), faint = Color(0xFF656B7B),
        accent = Color(0xFF8591DC), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_2 = AppTheme(
        "indigo-2", "Индиго · Металл", true,
        bg = Color(0xFF050C22), surf = Color(0xFF0C152D), field = Color(0xFF091127), sel = Color(0xFF17213B),
        text = Color(0xFFEEEEEF), muted = Color(0xFF99A2B4), faint = Color(0xFF646C7D),
        accent = Color(0xFFC9C9CC), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_3 = AppTheme(
        "indigo-3", "Индиго · Светлая", false,
        bg = Color(0xFFDCE2F9), surf = Color(0xFFE8EBF6), field = Color(0xFFD5DCF5), sel = Color(0xFFCBD3F0),
        text = Color(0xFF161B40), muted = Color(0xFF565C75), faint = Color(0xFF9297AB),
        accent = Color(0xFF32398E), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_indigo_4 = AppTheme(
        "indigo-4", "Индиго · Двухтон", true,
        bg = Color(0xFF040A1D), surf = Color(0xFF0A1328), field = Color(0xFF070E22), sel = Color(0xFF141E35),
        text = Color(0xFFEDEDFC), muted = Color(0xFF9AA2B2), faint = Color(0xFF656C7B),
        accent = Color(0xFF8C8BC9), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_5 = AppTheme(
        "indigo-5", "Индиго · Искры", true,
        bg = Color(0xFF04071B), surf = Color(0xFF091026), field = Color(0xFF060B20), sel = Color(0xFF141B33),
        text = Color(0xFFFCEBDA), muted = Color(0xFF9BA1B3), faint = Color(0xFF666B7C),
        accent = Color(0xFF6E78EA), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_6 = AppTheme(
        "indigo-6", "Индиго · Вариация", true,
        bg = Color(0xFF05081D), surf = Color(0xFF0C1128), field = Color(0xFF080D22), sel = Color(0xFF161D36),
        text = Color(0xFFE9EDFF), muted = Color(0xFF9BA1B3), faint = Color(0xFF666B7C),
        accent = Color(0xFF7377EC), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_indigo_7 = AppTheme(
        "indigo-7", "Индиго · Насыщенная", true,
        bg = Color(0xFF32419B), surf = Color(0xFF3B4CAC), field = Color(0xFF3747A1), sel = Color(0xFF465AC0),
        text = Color(0xFFE7EEFF), muted = Color(0xFFC1CAE5), faint = Color(0xFF959EB8),
        accent = Color(0xFFDBE7FF), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_indigo_8 = AppTheme(
        "indigo-8", "Индиго · Сияние", true,
        bg = Color(0xFF080C25), surf = Color(0xFF101630), field = Color(0xFF0C112A), sel = Color(0xFF1A213F),
        text = Color(0xFFECEEF4), muted = Color(0xFF9AA1B5), faint = Color(0xFF656B7E),
        accent = Color(0xFF6E80CF), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_violet_1 = AppTheme(
        "violet-1", "Фиолетовый · Классика", true,
        bg = Color(0xFF0F031A), surf = Color(0xFF190924), field = Color(0xFF14061F), sel = Color(0xFF251432),
        text = Color(0xFFF5E9FC), muted = Color(0xFFA79DB1), faint = Color(0xFF71677A),
        accent = Color(0xFFA771DA), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_violet_2 = AppTheme(
        "violet-2", "Фиолетовый · Персик", true,
        bg = Color(0xFF1E0D2A), surf = Color(0xFF281636), field = Color(0xFF221130), sel = Color(0xFF352144),
        text = Color(0xFFFEE9E5), muted = Color(0xFFA89CB3), faint = Color(0xFF72667C),
        accent = Color(0xFFCB9AA0), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_violet_3 = AppTheme(
        "violet-3", "Фиолетовый · Светлая", false,
        bg = Color(0xFFEADAF4), surf = Color(0xFFEEE6F3), field = Color(0xFFE5D3F0), sel = Color(0xFFDEC9EB),
        text = Color(0xFF281439), muted = Color(0xFF635870), faint = Color(0xFF9D94A7),
        accent = Color(0xFF6C2194), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_violet_4 = AppTheme(
        "violet-4", "Фиолетовый · Двухтон", true,
        bg = Color(0xFF10031D), surf = Color(0xFF1A0A28), field = Color(0xFF150622), sel = Color(0xFF261536),
        text = Color(0xFFF4EAFC), muted = Color(0xFFA79CB3), faint = Color(0xFF70677C),
        accent = Color(0xFF9A6AE4), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_violet_5 = AppTheme(
        "violet-5", "Фиолетовый · Искры", true,
        bg = Color(0xFF100318), surf = Color(0xFF1A0922), field = Color(0xFF15061C), sel = Color(0xFF26142F),
        text = Color(0xFFFFE7E6), muted = Color(0xFFA89CAF), faint = Color(0xFF726779),
        accent = Color(0xFF9A65E0), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_violet_6 = AppTheme(
        "violet-6", "Фиолетовый · Фуксия", true,
        bg = Color(0xFF12041C), surf = Color(0xFF1C0C26), field = Color(0xFF170821), sel = Color(0xFF281734),
        text = Color(0xFFFEE7F4), muted = Color(0xFFA89CB1), faint = Color(0xFF71677A),
        accent = Color(0xFFCD49B1), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_violet_7 = AppTheme(
        "violet-7", "Фиолетовый · Насыщенная", true,
        bg = Color(0xFF5C2990), surf = Color(0xFF6833A0), field = Color(0xFF622F96), sel = Color(0xFF7740B4),
        text = Color(0xFFF3EAFF), muted = Color(0xFFCFC5E1), faint = Color(0xFFA399B4),
        accent = Color(0xFFEEE1FF), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB8B0),
    )
    private val t_violet_8 = AppTheme(
        "violet-8", "Фиолетовый · Сияние", true,
        bg = Color(0xFF170521), surf = Color(0xFF210D2C), field = Color(0xFF1C0926), sel = Color(0xFF2E193A),
        text = Color(0xFFF3EBF9), muted = Color(0xFFA99BB2), faint = Color(0xFF73667B),
        accent = Color(0xFFC39FE0), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_rose_1 = AppTheme(
        "rose-1", "Роза · Классика", true,
        bg = Color(0xFF1A030B), surf = Color(0xFF250913), field = Color(0xFF1F060F), sel = Color(0xFF33141F),
        text = Color(0xFFF5ECED), muted = Color(0xFFB19BA1), faint = Color(0xFF7A656B),
        accent = Color(0xFFE87196), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_rose_2 = AppTheme(
        "rose-2", "Роза · Металл", true,
        bg = Color(0xFF1C030B), surf = Color(0xFF270913), field = Color(0xFF21060F), sel = Color(0xFF35141F),
        text = Color(0xFFFDEBDB), muted = Color(0xFFB29AA0), faint = Color(0xFF7B656A),
        accent = Color(0xFFE6A77F), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_rose_3 = AppTheme(
        "rose-3", "Роза · Светлая", false,
        bg = Color(0xFFF6DAE1), surf = Color(0xFFF5E6EA), field = Color(0xFFF2D3DB), sel = Color(0xFFEDC9D2),
        text = Color(0xFF390D19), muted = Color(0xFF725459), faint = Color(0xFFAA9195),
        accent = Color(0xFFC44062), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFB54076),
    )
    private val t_rose_4 = AppTheme(
        "rose-4", "Роза · Двухтон", true,
        bg = Color(0xFF1A030B), surf = Color(0xFF250913), field = Color(0xFF1F060F), sel = Color(0xFF33141F),
        text = Color(0xFFF9EAED), muted = Color(0xFFB19BA1), faint = Color(0xFF7A656B),
        accent = Color(0xFFDD5A7E), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_rose_5 = AppTheme(
        "rose-5", "Роза · Искры", true,
        bg = Color(0xFF180109), surf = Color(0xFF230612), field = Color(0xFF1D040E), sel = Color(0xFF31111D),
        text = Color(0xFFFFE8E1), muted = Color(0xFFB29AA1), faint = Color(0xFF7B656C),
        accent = Color(0xFFDA5C7C), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_rose_6 = AppTheme(
        "rose-6", "Роза · Вариация", true,
        bg = Color(0xFF150108), surf = Color(0xFF1F0610), field = Color(0xFF1A030C), sel = Color(0xFF2D101C),
        text = Color(0xFFFFE7EB), muted = Color(0xFFB19BA2), faint = Color(0xFF7A656C),
        accent = Color(0xFFE3366E), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_rose_7 = AppTheme(
        "rose-7", "Роза · Насыщенная", true,
        bg = Color(0xFF822547), surf = Color(0xFF912E52), field = Color(0xFF882B4C), sel = Color(0xFFA43A5F),
        text = Color(0xFFFFE7EB), muted = Color(0xFFE2C0C8), faint = Color(0xFFB5959C),
        accent = Color(0xFFFFDBE5), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB6D4),
    )
    private val t_rose_8 = AppTheme(
        "rose-8", "Роза · Сияние", true,
        bg = Color(0xFF1B0309), surf = Color(0xFF260912), field = Color(0xFF20060E), sel = Color(0xFF34141D),
        text = Color(0xFFF3ECEE), muted = Color(0xFFB29B9F), faint = Color(0xFF7B656A),
        accent = Color(0xFFBA6C78), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_coffee_1 = AppTheme(
        "coffee-1", "Кофе · Классика", true,
        bg = Color(0xFF120A04), surf = Color(0xFF1C120A), field = Color(0xFF170E07), sel = Color(0xFF281D15),
        text = Color(0xFFF4EDE6), muted = Color(0xFFA99F98), faint = Color(0xFF736A63),
        accent = Color(0xFFB18461), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_coffee_2 = AppTheme(
        "coffee-2", "Кофе · Металл", true,
        bg = Color(0xFF130A04), surf = Color(0xFF1D120A), field = Color(0xFF180E07), sel = Color(0xFF2A1D15),
        text = Color(0xFFEEEEEE), muted = Color(0xFFA99F99), faint = Color(0xFF736A64),
        accent = Color(0xFFB1B2B3), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_coffee_3 = AppTheme(
        "coffee-3", "Кофе · Светлая", false,
        bg = Color(0xFFECD9C4), surf = Color(0xFFECE3D8), field = Color(0xFFE8D3BC), sel = Color(0xFFE2C9AE),
        text = Color(0xFF2D1A10), muted = Color(0xFF6F584C), faint = Color(0xFFA99389),
        accent = Color(0xFF522D1A), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFB54076),
    )
    private val t_coffee_4 = AppTheme(
        "coffee-4", "Кофе · Двухтон", true,
        bg = Color(0xFF110803), surf = Color(0xFF1B1108), field = Color(0xFF160C05), sel = Color(0xFF271C12),
        text = Color(0xFFF4EDE6), muted = Color(0xFFA9A098), faint = Color(0xFF726A63),
        accent = Color(0xFFB1794F), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_coffee_5 = AppTheme(
        "coffee-5", "Кофе · Искры", true,
        bg = Color(0xFF100702), surf = Color(0xFF1A0F07), field = Color(0xFF150B05), sel = Color(0xFF261A11),
        text = Color(0xFFF7EDDC), muted = Color(0xFFA99F99), faint = Color(0xFF736A64),
        accent = Color(0xFFA6683D), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_coffee_6 = AppTheme(
        "coffee-6", "Кофе · Вариация", true,
        bg = Color(0xFF100702), surf = Color(0xFF1A0F07), field = Color(0xFF150B05), sel = Color(0xFF261A11),
        text = Color(0xFFF4EDE6), muted = Color(0xFFA99F99), faint = Color(0xFF736A64),
        accent = Color(0xFFEBD8C0), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_coffee_7 = AppTheme(
        "coffee-7", "Кофе · Насыщенная", true,
        bg = Color(0xFF6C3D1E), surf = Color(0xFF794726), field = Color(0xFF724223), sel = Color(0xFF8A5431),
        text = Color(0xFFFBEBE2), muted = Color(0xFFE1C4B3), faint = Color(0xFFB39888),
        accent = Color(0xFFFAE3D5), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFFA1E4AE), warn = Color(0xFFF9D280), danger = Color(0xFFFFB6D4),
    )
    private val t_coffee_8 = AppTheme(
        "coffee-8", "Кофе · Сияние", true,
        bg = Color(0xFF130B04), surf = Color(0xFF1D130A), field = Color(0xFF180F07), sel = Color(0xFF291F15),
        text = Color(0xFFF2EEE8), muted = Color(0xFFA8A098), faint = Color(0xFF726A63),
        accent = Color(0xFFD7C0A7), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_graphite_1 = AppTheme(
        "graphite-1", "Графит · Классика", true,
        bg = Color(0xFF0D0D0D), surf = Color(0xFF161616), field = Color(0xFF111111), sel = Color(0xFF212121),
        text = Color(0xFFEFEEED), muted = Color(0xFFA5A197), faint = Color(0xFF6F6C62),
        accent = Color(0xFF9C9FA7), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_graphite_2 = AppTheme(
        "graphite-2", "Графит · Металл", true,
        bg = Color(0xFF0E0F0F), surf = Color(0xFF171818), field = Color(0xFF131414), sel = Color(0xFF232424),
        text = Color(0xFFFCECD9), muted = Color(0xFF97A5A5), faint = Color(0xFF626F6F),
        accent = Color(0xFFB97F41), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_graphite_3 = AppTheme(
        "graphite-3", "Графит · Светлая", false,
        bg = Color(0xFFE6E2DD), surf = Color(0xFFEDEBE8), field = Color(0xFFE0DCD7), sel = Color(0xFFD8D3CD),
        text = Color(0xFF201F1E), muted = Color(0xFF5E5D5C), faint = Color(0xFF999896),
        accent = Color(0xFF1E1F20), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF33854A), warn = Color(0xFFAF7C00), danger = Color(0xFFBD413F),
    )
    private val t_graphite_4 = AppTheme(
        "graphite-4", "Графит · Двухтон", true,
        bg = Color(0xFF0D0E0E), surf = Color(0xFF161717), field = Color(0xFF111313), sel = Color(0xFF212323),
        text = Color(0xFFECEFEF), muted = Color(0xFF97A5A5), faint = Color(0xFF626F6F),
        accent = Color(0xFFD1B27A), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_graphite_5 = AppTheme(
        "graphite-5", "Графит · Искры", true,
        bg = Color(0xFF0C0D0D), surf = Color(0xFF151616), field = Color(0xFF101111), sel = Color(0xFF202222),
        text = Color(0xFFECEFEF), muted = Color(0xFF97A5A5), faint = Color(0xFF626F6F),
        accent = Color(0xFF73767D), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )
    private val t_graphite_6 = AppTheme(
        "graphite-6", "Графит · Вариация", true,
        bg = Color(0xFF0B0D0B), surf = Color(0xFF131613), field = Color(0xFF0F110F), sel = Color(0xFF1F221F),
        text = Color(0xFFEEEFEE), muted = Color(0xFF9CA49C), faint = Color(0xFF676E67),
        accent = Color(0xFFDDDBDA), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_graphite_7 = AppTheme(
        "graphite-7", "Графит · Насыщенная", true,
        bg = Color(0xFF111212), surf = Color(0xFF1A1B1B), field = Color(0xFF161717), sel = Color(0xFF262727),
        text = Color(0xFFF8ECE2), muted = Color(0xFF97A5A5), faint = Color(0xFF626F6F),
        accent = Color(0xFF878686), onAccent = Color(0xFFFFFFFF),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF075AA),
    )
    private val t_graphite_8 = AppTheme(
        "graphite-8", "Графит · Сияние", true,
        bg = Color(0xFF111211), surf = Color(0xFF1A1B1A), field = Color(0xFF161716), sel = Color(0xFF262726),
        text = Color(0xFFEEEEEE), muted = Color(0xFF9CA49C), faint = Color(0xFF676E67),
        accent = Color(0xFFE5BE86), onAccent = Color(0xFF0B0F0D),
        ok = Color(0xFF69BA7C), warn = Color(0xFFE9B452), danger = Color(0xFFF97770),
    )

    val families: List<ThemeFamily> = listOf(
        ThemeFamily("red", "Красный", t_red_1, listOf(t_red_1, t_red_2, t_red_3, t_red_4, t_red_5, t_red_6, t_red_7, t_red_8)),
        ThemeFamily("terracotta", "Терракота", t_terracotta_1, listOf(t_terracotta_1, t_terracotta_2, t_terracotta_3, t_terracotta_4, t_terracotta_5, t_terracotta_6, t_terracotta_7, t_terracotta_8)),
        ThemeFamily("amber", "Янтарь", t_amber_1, listOf(t_amber_1, t_amber_2, t_amber_3, t_amber_4, t_amber_5, t_amber_6, t_amber_7, t_amber_8)),
        ThemeFamily("olive", "Олива", t_olive_1, listOf(t_olive_1, t_olive_2, t_olive_3, t_olive_4, t_olive_5, t_olive_6, t_olive_7, t_olive_8)),
        ThemeFamily("green", "Зелёный", t_green_1, listOf(t_green_1, t_green_2, t_green_3, t_green_4, t_green_5, t_green_6, t_green_7, t_green_8)),
        ThemeFamily("teal", "Бирюза", t_teal_1, listOf(t_teal_1, t_teal_2, t_teal_3, t_teal_4, t_teal_5, t_teal_6, t_teal_7, t_teal_8)),
        ThemeFamily("blue", "Синий", t_blue_1, listOf(t_blue_1, t_blue_2, t_blue_3, t_blue_4, t_blue_5, t_blue_6, t_blue_7, t_blue_8)),
        ThemeFamily("indigo", "Индиго", t_indigo_1, listOf(t_indigo_1, t_indigo_2, t_indigo_3, t_indigo_4, t_indigo_5, t_indigo_6, t_indigo_7, t_indigo_8)),
        ThemeFamily("violet", "Фиолетовый", t_violet_1, listOf(t_violet_1, t_violet_2, t_violet_3, t_violet_4, t_violet_5, t_violet_6, t_violet_7, t_violet_8)),
        ThemeFamily("rose", "Роза", t_rose_1, listOf(t_rose_1, t_rose_2, t_rose_3, t_rose_4, t_rose_5, t_rose_6, t_rose_7, t_rose_8)),
        ThemeFamily("coffee", "Кофе", t_coffee_1, listOf(t_coffee_1, t_coffee_2, t_coffee_3, t_coffee_4, t_coffee_5, t_coffee_6, t_coffee_7, t_coffee_8)),
        ThemeFamily("graphite", "Графит", t_graphite_1, listOf(t_graphite_1, t_graphite_2, t_graphite_3, t_graphite_4, t_graphite_5, t_graphite_6, t_graphite_7, t_graphite_8)),
    )

    val all: List<AppTheme> = families.flatMap { it.themes }

    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }

    fun familyOf(theme: AppTheme): ThemeFamily = families.first { theme in it.themes }
}
