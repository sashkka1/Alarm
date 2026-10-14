# Темы и иконки под каждую тему — из одного списка тем и одной картинки. Образец — DrozdRun
# (`android_theme_icons.py` скилла theme-icons), разложено по модулям Alarm.
#
#   python art/make_icons.py     # сначала: картинки тем и превью
#   python art/make_themes.py
#
# Вход: art/themes.json — 96 тем «Мягкого» (копия reference/themes.json скилла soft-design); art/woodpecker.png.
# Выход (всё генерируется, руками не править):
#   ui/src/main/java/com/sasha/alarm/ui/Themes.kt                  — темы для экранов (настройки и тревога)
#   app/src/main/java/com/sasha/alarm/ThemeSplash.kt               — стиль загрузочного экрана по id темы
#   app/src/main/res/drawable-nodpi/ic_bird.png                    — дятел одним цветом: монохромный слой
#   app/src/main/res/values/splash_themes.xml                      — загрузочный экран на каждую тему
#   app/src/main/res/mipmap-anydpi-v26/ic_launcher_<тема>.xml      — адаптивная иконка темы
#   AndroidManifest.xml между метками aliases:begin/end            — по псевдониму главного экрана на тему
import json
import os
import re

import numpy as np
from PIL import Image

THEMES = "art/themes.json"
LOGO = "art/woodpecker.png"
RES = "app/src/main/res"
KT = "ui/src/main/java/com/sasha/alarm/ui/Themes.kt"
SPLASH_KT = "app/src/main/java/com/sasha/alarm/ThemeSplash.kt"
MANIFEST = "app/src/main/AndroidManifest.xml"

# Семейства и их порядок в палитре — как в art/themes.json (порядок владельца, 2026-10-13: красный, терракота,
# янтарь, олива, зелёный, бирюза, синий, индиго, фиолетовый, роза, кофе, графит). Тема по умолчанию —
# «Красный · Золото»: ближе всего к исходнику (светло-золотой дятел на тёмно-бордовом).
DEFAULT = "red-2"

# Слой адаптивной иконки — 108dp (432 px), видимое — 72dp, безопасная зона — круг 66dp. Дятел вписан в 250 px.
CANVAS, BIRD = 432, 250
# Маска по зелёному каналу логотипа: фон ≤ 8, дятел ≥ 100.
G_LOW, G_HIGH = 30, 75
# Светотень: яркость дятла растягивается в 0,55…1 — умножение на акцент даёт тень → цвет.
SHADE_LOW = 0.55

with open(THEMES, encoding="utf-8") as f:
    themes = [t for t in json.load(f)["themes"] if t["id"] != "hvoya"]
FAMILIES = []
for t in themes:
    fid = t["id"].rsplit("-", 1)[0]
    if fid not in [f for f, _ in FAMILIES]:
        FAMILIES.append((fid, t["group"]))
by_family = {fid: [t for t in themes if t["id"].startswith(fid + "-")] for fid, _ in FAMILIES}
assert sum(map(len, by_family.values())) == len(themes), "тема вне известных семейств"
assert any(t["id"] == DEFAULT for t in themes)


def res_id(theme_id: str) -> str:
    return theme_id.replace("-", "_")


# ---------- дятел: белая маска со светотенью ----------
src = np.asarray(Image.open(LOGO).convert("RGB")).astype(np.float64)
alpha = np.clip((src[:, :, 1] - G_LOW) * 255 / (G_HIGH - G_LOW), 0, 255)
lum = 0.2126 * src[:, :, 0] + 0.7152 * src[:, :, 1] + 0.0722 * src[:, :, 2]
inside = alpha > 200
lo, hi = np.percentile(lum[inside], [5, 95])
shade = np.clip(SHADE_LOW + (1 - SHADE_LOW) * (lum - lo) / (hi - lo), SHADE_LOW, 1) * 255
rgba = np.dstack([shade, shade, shade, alpha]).astype(np.uint8)
bird = Image.fromarray(rgba, "RGBA")
bird = bird.crop(Image.fromarray(alpha.astype(np.uint8)).getbbox())
scale = BIRD / max(bird.size)
bird = bird.resize((round(bird.size[0] * scale), round(bird.size[1] * scale)), Image.LANCZOS)
layer = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
layer.paste(bird, ((CANVAS - bird.size[0]) // 2, (CANVAS - bird.size[1]) // 2), bird)
os.makedirs(f"{RES}/drawable-nodpi", exist_ok=True)
layer.save(f"{RES}/drawable-nodpi/ic_bird.png")

# ---------- ресурсы иконок ----------
os.makedirs(f"{RES}/drawable", exist_ok=True)
os.makedirs(f"{RES}/mipmap-anydpi-v26", exist_ok=True)
for name in os.listdir(f"{RES}/drawable"):
    if name.startswith("ic_fg_"):
        os.remove(f"{RES}/drawable/{name}")
if os.path.exists(f"{RES}/values/theme_icons.xml"):
    os.remove(f"{RES}/values/theme_icons.xml")
for name in os.listdir(f"{RES}/mipmap-anydpi-v26"):
    if name.startswith("ic_launcher_") and name != "ic_launcher.xml":
        os.remove(f"{RES}/mipmap-anydpi-v26/{name}")

GEN = "<!-- Генерирует art/make_themes.py, руками не править. -->"
for t in themes:
    rid = res_id(t["id"])
    assert os.path.exists(f"{RES}/mipmap-xxxhdpi/ic_art_{rid}.png"), "нет картинки темы — сначала python art/make_icons.py"
    with open(f"{RES}/mipmap-anydpi-v26/ic_launcher_{rid}.xml", "w", encoding="utf-8") as f:
        f.write(f'<?xml version="1.0" encoding="utf-8"?>\n{GEN}\n'
                f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f'    <background android:drawable="@mipmap/ic_art_{rid}" />\n'
                f'    <foreground android:drawable="@android:color/transparent" />\n'
                f'    <monochrome android:drawable="@drawable/ic_bird" />\n'
                f'</adaptive-icon>\n')

# ---------- загрузочный экран под тему ----------
# Android 12+ рисует загрузочный экран сам, до запуска приложения, по стилю активности: фон и значок.
# Стиль на каждую тему; выбранный запоминает система (`setSplashScreenTheme`, Android 13+, MainActivity) —
# иначе загрузочный экран всегда цвета темы по умолчанию (владелец, 2026-10-13: «серая тема — загрузочный
# экран красный»).
splash = ['<?xml version="1.0" encoding="utf-8"?>', GEN, '<resources>']
for t in themes:
    rid = res_id(t["id"])
    bg = "#FF" + t["core"]["bg"][1:]
    splash += [f'    <style name="Theme.Alarm.Splash.{rid}" parent="Theme.Alarm">',
               f'        <item name="android:windowBackground">{bg}</item>',
               f'        <item name="android:windowSplashScreenBackground">{bg}</item>',
               f'        <item name="android:windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_{rid}</item>',
               '    </style>']
splash.append("</resources>")
with open(f"{RES}/values/splash_themes.xml", "w", encoding="utf-8") as f:
    f.write("\n".join(splash) + "\n")

# ---------- псевдонимы в манифесте ----------
aliases = []
for t in themes:
    rid = res_id(t["id"])
    enabled = "true" if t["id"] == DEFAULT else "false"
    aliases.append(
        f'        <activity-alias\n'
        f'            android:name=".Icon_{rid}"\n'
        f'            android:enabled="{enabled}"\n'
        f'            android:exported="true"\n'
        f'            android:icon="@mipmap/ic_launcher_{rid}"\n'
        f'            android:targetActivity=".MainActivity">\n'
        f'            <intent-filter>\n'
        f'                <action android:name="android.intent.action.MAIN" />\n'
        f'                <category android:name="android.intent.category.LAUNCHER" />\n'
        f'            </intent-filter>\n'
        f'        </activity-alias>')
with open(MANIFEST, encoding="utf-8") as f:
    manifest = f.read()
block = "<!-- aliases:begin -->\n" + "\n".join(aliases) + "\n        <!-- aliases:end -->"
manifest, n = re.subn(r"<!-- aliases:begin -->.*?<!-- aliases:end -->", lambda _: block, manifest, flags=re.S)
assert n == 1, "в манифесте нет меток aliases:begin / aliases:end"
with open(MANIFEST, "w", encoding="utf-8") as f:
    f.write(manifest)


# ---------- Themes.kt ----------
def c(hex_: str) -> str:
    return f"Color(0xFF{hex_[1:].upper()})"


def kt_str(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


lines = [
    "// Генерирует art/make_themes.py из art/themes.json — руками не править.",
    "package com.sasha.alarm.ui",
    "",
    "import androidx.compose.ui.graphics.Color",
    "",
    "/** Семейство тем — первый уровень палитры. [main] — тема, чьи два цвета рисуют плитку семейства. */",
    "class ThemeFamily(val id: String, val name: String, val main: AppTheme, val themes: List<AppTheme>)",
    "",
    "/** Тема дизайн-кода «Мягкий»: два основных цвета — фон [bg] и текст [text], остальное выведено из них. */",
    "class AppTheme(",
    "    val id: String,",
    "    val name: String,",
    "    val dark: Boolean,",
    "    val bg: Color,",
    "    val surf: Color,",
    "    val field: Color,",
    "    val sel: Color,",
    "    val text: Color,",
    "    val muted: Color,",
    "    val faint: Color,",
    "    val accent: Color,",
    "    val onAccent: Color,",
    "    val ok: Color,",
    "    val warn: Color,",
    "    val danger: Color,",
    ") {",
    "    /** Псевдоним главного экрана с иконкой этой темы (манифест). */",
    "    val alias: String get() = \"Icon_\" + id.replace('-', '_')",
    "}",
    "",
    "object Themes {",
    f"    const val DEFAULT_ID = {kt_str(DEFAULT)}",
    "",
]
for t in themes:
    var = "t_" + res_id(t["id"])
    lines += [
        f"    private val {var} = AppTheme(",
        f"        {kt_str(t['id'])}, {kt_str(t['group'] + ' · ' + t['name'])}, {str(t['type'] != 'light').lower()},",
        f"        bg = {c(t['core']['bg'])}, surf = {c(t['surface']['surf'])}, field = {c(t['surface']['field'])}, sel = {c(t['surface']['sel'])},",
        f"        text = {c(t['ink']['text'])}, muted = {c(t['ink']['muted'])}, faint = {c(t['ink']['faint'])},",
        f"        accent = {c(t['accent'])}, onAccent = {c(t['onAccent'])},",
        f"        ok = {c(t['status']['ok'])}, warn = {c(t['status']['warn'])}, danger = {c(t['status']['danger'])},",
        "    )",
    ]
lines += ["", "    val families: List<ThemeFamily> = listOf("]
for fid, fname in FAMILIES:
    members = ", ".join("t_" + res_id(t["id"]) for t in by_family[fid])
    lines.append(f"        ThemeFamily({kt_str(fid)}, {kt_str(fname)}, t_{fid}_1, listOf({members})),")
lines += [
    "    )",
    "",
    "    val all: List<AppTheme> = families.flatMap { it.themes }",
    "",
    "    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }",
    "",
    "    fun familyOf(theme: AppTheme): ThemeFamily = families.first { theme in it.themes }",
    "}",
    "",
]
with open(KT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

splash_kt = [
    "// Генерирует art/make_themes.py — руками не править.",
    "package com.sasha.alarm",
    "",
    "/** Стиль загрузочного экрана темы (`values/splash_themes.xml`): система рисует его до запуска приложения. */",
    "object ThemeSplash {",
    "    fun style(themeId: String): Int = when (themeId) {",
]
for t in themes:
    splash_kt.append(f"        {kt_str(t['id'])} -> R.style.Theme_Alarm_Splash_{res_id(t['id'])}")
splash_kt += [f"        else -> R.style.Theme_Alarm_Splash_{res_id(DEFAULT)}", "    }", "}", ""]
with open(SPLASH_KT, "w", encoding="utf-8") as f:
    f.write("\n".join(splash_kt))

print(f"готово: {len(themes)} тем, {len(FAMILIES)} семейств; по умолчанию {DEFAULT}")
