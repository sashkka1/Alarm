# Иконки Alarm на все темы: дятел в цветах темы, с градиентом, как на листах; где на листе у птицы
# золотые кончики перьев — они же у дятла. Скрипт — `gradient_icons.py` скилла theme-icons (образец —
# DrozdRun), подогнан под дятла: середина туловища своя, отсечки ног нет — ног у дятла на рисунке нет.
#
#   python art/make_icons.py [папка]      # превью — по умолчанию в «Иконки Alarm» на рабочем столе
#
# Выход: app/src/main/res/mipmap-xxxhdpi/ic_art_<тема>.png — целый слой адаптивной иконки 108dp (432 px),
# его берёт art/make_themes.py; в папку превью — то, что видно на телефоне (середина 72dp), и обзор всех.
#
# История (владелец):
#   2026-10-12 — бегунок, залитый акцентом, — «не то»: «нету красивого градиента». Лаунчер показывает только
#                середину значка — фон со свечением уложен в неё, перепад фона усилен (GLOW).
#   2026-10-13 — птица крупная (BIRD), как в первом варианте; золотые кончики — «только в иконки, где золотые
#                кончики уже есть» на листе; 12 семейств вместо 5.
# Как сделано — только детерминированно, без перерисовки (бегунок — пиксели art/roadrunner-logo.png):
#   - Птица. Цвет каждой точки бегунка — краска птицы с листа на том же месте распределения яркости
#     (сопоставление гистограмм): светотень бегунка своя, краски — тень → цвет → блик — с иконки темы.
#   - Фон. Радиальный профиль фона той же иконки листа: медиана по кольцам — свечение и виньетка.
#   - Кончики. Где на листе у птицы кончики другого цвета (см. has_tips), золото ложится на кончики перьев
#     бегунка: место, дальше которого по перу идти некуда (путь от основания пера внутри силуэта почти
#     максимален в своём пере). Ноги и пальцы без золота. Краска золота — точки кончиков с той же иконки.
# Листы — assets/sheets скилла soft-design, сетка 4×2, позиция = номер темы; темы — art/themes.json.
import colorsys
import heapq
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SKILL = os.path.expanduser("~/.claude/skills/soft-design")
SHEETS = os.path.join(SKILL, "assets/sheets")
LOGO = "art/woodpecker.png"   # исходник владельца, 2026-10-14: светлый дятел на бордовом
RES = "app/src/main/res/mipmap-xxxhdpi"
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.expanduser("~"), "Desktop", "Иконки Alarm")
SIZE = 432        # слой адаптивной иконки 108dp при xxxhdpi
VISIBLE = 288     # видимая середина — 72dp
BIRD = 0.86       # бегунок по большей стороне — доля видимой середины (владелец, 2026-10-13: крупный)
GLOW = 2.2        # во сколько раз усилить перепад фона от края к центру — свечение заметнее
G_LOW, G_HIGH = 30, 75
BIRD_DIST = 55    # точка листа — птица, если дальше от фона на столько (евклид RGB)
BIRD_DIST_GLOW = 150
RINGS = 24
TIP, LONG = 210, 120   # px логотипа: длина золотого кончика; путь от основания пера, ближе которого золота нет
LUMA = np.array([0.2126, 0.7152, 0.0722])
# Кончики на листе: позиция 5 («Искры») — всегда; лист графита (2026-10-13) — у всех восьми; прочие —
# если точек другого оттенка ≥ 3 % птицы и лежат они дальше от середины птицы, чем она в среднем (×1,25).
TIPS_ALWAYS = {5}
TIPS_FAMILIES = {"graphite"}

with open("art/themes.json", encoding="utf-8") as f:
    THEMES = [t for t in json.load(f)["themes"] if t["id"] != "hvoya"]
FAMILIES = list(dict.fromkeys(t["id"].rsplit("-", 1)[0] for t in THEMES))
NAMES = {t["id"]: t["name"] for t in THEMES}


def cell(sheet, n):
    """Плитка n (1–8) листа 4×2 без рамок: середина ячейки."""
    h, w = sheet.shape[:2]
    cw, ch = w / 4, h / 2
    col, row = (n - 1) % 4, (n - 1) // 4
    return sheet[int(row * ch + ch * .07):int((row + 1) * ch - ch * .07),
                 int(col * cw + cw * .08):int((col + 1) * cw - cw * .08)].astype(np.float64)


def paint_of(px):
    """Краска: точки по яркости → 48 ступеней с медианой (без зерна бумаги) → 1024 значения, тень → блик."""
    px = px[np.argsort(px @ LUMA)]
    steps = np.array([np.median(c, axis=0) for c in np.array_split(px, 48)])
    q, grid = np.linspace(0, 1, len(steps)), np.linspace(0, 1, 1024)
    return np.stack([np.interp(grid, q, steps[:, i]) for i in range(3)], axis=1)


def analyze(fam, n, tile):
    """(краска тела, краска кончиков или None, радиальный профиль фона)."""
    h, w = tile.shape[:2]
    edge = np.concatenate([tile[:6].reshape(-1, 3), tile[-6:].reshape(-1, 3),
                           tile[:, :6].reshape(-1, 3), tile[:, -6:].reshape(-1, 3)])
    bg0 = np.median(edge, axis=0)
    # «Сияние» (*-8): вокруг птицы светится ореол — с обычным порогом он попадает в краску птицы, и у бегунка
    # появляются тёмные полосы. Там птица — только точки, далёкие от фона.
    bird = np.linalg.norm(tile - bg0, axis=2) > (BIRD_DIST_GLOW if n == 8 else BIRD_DIST)
    grown = np.array(Image.fromarray((bird * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(21))) > 0
    ys, xs = np.nonzero(bird)
    px = tile[bird]
    r = np.hypot(ys - ys.mean(), xs - xs.mean())

    # Чужой оттенок: у серой птицы — просто насыщенные точки, у цветной — дальше 35° от основного.
    hsv = np.array([colorsys.rgb_to_hsv(*p) for p in px / 255])
    hue, sat = hsv[:, 0] * 360, hsv[:, 1]
    if (sat > .25).mean() < .3:
        far = sat > .3
    else:
        hist = np.bincount((hue[sat > .25] // 15).astype(int) % 24, minlength=24)
        main = hist.argmax() * 15 + 7.5
        far = (np.abs((hue - main + 180) % 360 - 180) > 35) & (sat > .3)
    detected = far.mean() >= .03 and r[far].mean() / r.mean() >= 1.25
    tips = None
    body = px
    if detected or n in TIPS_ALWAYS or fam in TIPS_FAMILIES:
        if detected or far.mean() >= .03:
            sel = far
        else:
            # Кончики того же оттенка, что тело (терракота, янтарь…): крайние точки птицы, сильнее всего
            # отличающиеся от её середины.
            outer = r >= np.percentile(r, 82)
            diff = np.linalg.norm(px - np.median(px[~outer], axis=0), axis=1)
            sel = outer & (diff >= np.percentile(diff[outer], 50))
        tips = paint_of(px[sel])
        body = px[~sel]

    yy, xx = np.mgrid[0:h, 0:w]
    ring = np.minimum((np.hypot((xx - w / 2) / (w / 2), (yy - h / 2) / (h / 2)) / np.sqrt(2) * RINGS).astype(int), RINGS - 1)
    # Кольца снаружи внутрь: где фона мало (закрыт птицей), берётся внешнее — иначе в центр просачивается птица.
    prof = np.zeros((RINGS, 3))
    for k in range(RINGS - 1, -1, -1):
        sel = (ring == k) & ~grown
        ok = sel.sum() > max(30, .35 * (ring == k).sum())
        prof[k] = np.median(tile[sel], axis=0) if ok else (prof[k + 1] if k < RINGS - 1 else bg0)
    prof = np.array([prof[max(0, k - 1):k + 2].mean(axis=0) for k in range(RINGS)])
    prof = np.clip(prof[-1] + (prof - prof[-1]) * GLOW, 0, 255)
    return paint_of(body), tips, prof


def blur(a, sigma):
    r = int(3 * sigma)
    k = np.exp(-.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, mode="valid"), 0, a)
    return np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, mode="valid"), 1, a)


def geodesic(mask, seed):
    """Длина пути от seed до каждой точки внутри силуэта (Дейкстра, 8 соседей); вне силуэта — -1."""
    h, w = mask.shape
    dist = np.full((h, w), np.inf)
    dist[seed] = 0
    heap = [(0.0, seed)]
    steps = [(-1, 0, 1), (1, 0, 1), (0, -1, 1), (0, 1, 1), (-1, -1, 1.414), (-1, 1, 1.414), (1, -1, 1.414), (1, 1, 1.414)]
    while heap:
        d, (y, x) = heapq.heappop(heap)
        if d > dist[y, x]:
            continue
        for dy, dx, c in steps:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and d + c < dist[ny, nx]:
                dist[ny, nx] = d + c
                heapq.heappush(heap, (d + c, (ny, nx)))
    return np.where(np.isfinite(dist), dist, -1)


smooth = lambda t: np.clip(t, 0, 1) ** 2 * (3 - 2 * np.clip(t, 0, 1))

# ---------- бегунок: прозрачность, светотень, где кончики перьев ----------
logo = np.asarray(Image.open(LOGO).convert("RGB")).astype(np.float64)
alpha = np.clip((logo[:, :, 1] - G_LOW) / (G_HIGH - G_LOW), 0, 1)
ys, xs = np.nonzero(alpha > .02)
logo, alpha = logo[ys.min():ys.max() + 1, xs.min():xs.max() + 1], alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
luma = logo @ LUMA
order = np.sort(luma[alpha > .8])
rank = blur(np.searchsorted(order, luma) / len(order), 2)

# Кончик пера: на маске /4 у каждого куска силуэта (перья отделены просветами) — путь от его ближнего
# к туловищу места; золото там, где путь почти максимален в округе своего куска.
SMALL = 4
small = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).resize(
    (alpha.shape[1] // SMALL, alpha.shape[0] // SMALL), Image.BILINEAR)) > 100
hs, ws = small.shape
torso = np.array([hs * .47, ws * .35])   # грудь дятла в рамке рисунка
geo = np.full(small.shape, -1.0)
parts = []
left = small.copy()
while left.any():
    pts = np.argwhere(left)
    part = geodesic(left, tuple(pts[np.argmin(np.hypot(pts[:, 0] - torso[0], pts[:, 1] - torso[1]))]))
    reached = part >= 0
    geo[reached] = part[reached]
    parts.append(reached)
    left &= ~reached
tip_px = TIP / SMALL
near_max = np.zeros(small.shape)
for reached in parts:
    g = np.where(reached, geo, 0)
    top = max(g.max(), 1)
    g8 = Image.fromarray(np.clip(g / top * 255, 0, 255).astype(np.uint8))
    near_max[reached] = (np.asarray(g8.filter(ImageFilter.MaxFilter(2 * int(tip_px) + 1))) / 255 * top)[reached]
tip = smooth((geo - (near_max - tip_px)) / tip_px) * (geo > LONG / SMALL) * small
gold = blur(np.clip(np.asarray(Image.fromarray(tip.astype(np.float32), "F").resize(
    (alpha.shape[1], alpha.shape[0]), Image.BILINEAR)), 0, 1), 6)

scale = VISIBLE * BIRD / max(logo.shape[:2])
bw, bh = round(logo.shape[1] * scale), round(logo.shape[0] * scale)
fit = lambda a: np.asarray(Image.fromarray(a.astype(np.float32), "F").resize((bw, bh), Image.BILINEAR))
rank_s, alpha_s, gold_s = fit(rank), fit(alpha), np.clip(fit(gold), 0, 1)
ox, oy = (SIZE - bw) // 2, (SIZE - bh) // 2
yy, xx = np.mgrid[0:SIZE, 0:SIZE]
# Радиус 1 — угол видимой середины: весь профиль фона (свечение → виньетка) укладывается в видимое.
r_out = np.minimum(np.hypot(xx - SIZE / 2, yy - SIZE / 2) / (VISIBLE / 2 * np.sqrt(2)), 1.0)
crop = ((SIZE - VISIBLE) // 2,) * 2 + ((SIZE + VISIBLE) // 2,) * 2


def render(body, tips, prof):
    pos = np.clip(r_out * RINGS - .5, 0, RINGS - 1)
    k0 = np.floor(pos).astype(int)
    t = (pos - k0)[..., None]
    img = prof[k0] * (1 - t) + prof[np.minimum(k0 + 1, RINGS - 1)] * t
    bird = body[np.clip((rank_s * 1023).round().astype(int), 0, 1023)]
    if tips is not None:
        # Золото — светлая половина краски кончиков: на листах кончики ярче тела.
        g = np.clip(gold_s * 1.6, 0, 1)[..., None]
        bird = bird * (1 - g) + tips[np.clip(((.45 + .55 * rank_s) * 1023).round().astype(int), 0, 1023)] * g
    a = alpha_s[..., None]
    img[oy:oy + bh, ox:ox + bw] = bird * a + img[oy:oy + bh, ox:ox + bw] * (1 - a)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


os.makedirs(OUT, exist_ok=True)
os.makedirs(RES, exist_ok=True)
for name in os.listdir(RES):
    if name.startswith("ic_art_"):
        os.remove(os.path.join(RES, name))
for name in os.listdir(OUT):
    if name.endswith(".png"):
        os.remove(os.path.join(OUT, name))
thumbs, with_tips = {}, []
for fam in FAMILIES:
    sheet = np.asarray(Image.open(os.path.join(SHEETS, f"{fam}.png")).convert("RGB"))
    for n in range(1, 9):
        tid = f"{fam}-{n}"
        body, tips, prof = analyze(fam, n, cell(sheet, n))
        if tips is not None:
            with_tips.append(tid)
        im = render(body, tips, prof)
        im.save(os.path.join(RES, f"ic_art_{tid.replace('-', '_')}.png"), optimize=True)
        shown = im.crop(crop)
        shown.save(os.path.join(OUT, f"{tid}.png"))
        thumbs[tid] = shown

# Обзор: все так, как их показывает телефон, — середина в маске «скруглённый квадрат»; ряд — семейство.
T, PAD, LABEL = 150, 12, 34
board = Image.new("RGB", (8 * (T + PAD) + PAD + 110, len(FAMILIES) * (T + PAD + LABEL) + PAD), (24, 24, 26))
draw = ImageDraw.Draw(board)
font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 13)
bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 15)
mask = Image.new("L", (T, T), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, T - 1, T - 1), radius=36, fill=255)
groups = {t["id"].rsplit("-", 1)[0]: t["group"] for t in THEMES}
for r, fam in enumerate(FAMILIES):
    y = PAD + r * (T + PAD + LABEL)
    draw.text((PAD, y + T // 2), f"{r + 1}. {groups[fam]}", fill=(230, 230, 230), font=bold, anchor="lm")
    for c in range(8):
        tid = f"{fam}-{c + 1}"
        x = 110 + PAD + c * (T + PAD)
        board.paste(thumbs[tid].resize((T, T), Image.LANCZOS), (x, y), mask)
        draw.text((x + 2, y + T + 4), tid + (" · кончики" if tid in with_tips else ""), fill=(220, 220, 220), font=font)
        draw.text((x + 2, y + T + 18), NAMES.get(tid, ""), fill=(150, 150, 150), font=font)
board.save(os.path.join(OUT, "_все.png"))
print(f"готово: {len(thumbs)} иконок, с кончиками: {', '.join(with_tips)}")
