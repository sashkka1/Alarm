package com.sasha.alarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PEEK_FADE_MS = 400

/**
 * Шестерёнка настроек — справа вверху, рядом с главным тумблером (владелец, 2026-10-14: «около переключателя
 * в правом верхнем углу»; вид и окно — как в DrozdRun). Открытая — повёрнута на 45°.
 */
@Composable
fun SettingsGear(open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, tween(250), label = "gear")
    val ink = theme.text
    val hole = theme.surf
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(theme.surf)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp).rotate(turn)) {
            val c = center
            val r = size.minDimension / 2
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawRoundRect(
                        ink,
                        topLeft = Offset(c.x - r * 0.17f, c.y - r),
                        size = Size(r * 0.34f, r * 0.5f),
                        cornerRadius = CornerRadius(r * 0.08f),
                    )
                }
            }
            drawCircle(ink, r * 0.7f, c)
            drawCircle(hole, r * 0.3f, c)
        }
    }
}

/** Что показано в окне настроек. Каждая страница заменяет предыдущую на том же месте. */
private sealed interface PanelPage {
    data object Main : PanelPage
    data object Families : PanelPage
    data class Shades(val family: ThemeFamily) : PanelPage
}

/**
 * Смена страницы окна (владелец, 2026-10-13: высота «разгоняется — сначала медленно, потом быстро, в конце
 * медленно… и пропадает с запозданием»). Было: плавный разгон и торможение 220 мс, высоту тянул отдельный
 * `animateContentSize`, прежняя страница гасла столько же, сколько росла новая. Стало: высота меняется
 * вместе со сменой страницы, 180 мс, сразу быстро и с мягкой остановкой, без разгона; прежняя страница
 * гаснет за 90 мс, новая проявляется за то же время, что растёт окно.
 */
private const val PAGE_MS = 180
private const val PAGE_FADE_OUT_MS = 90
private val PAGE_EASING = LinearOutSlowInEasing

private fun AnimatedContentTransitionScope<PanelPage>.pageChange(): ContentTransform =
    (fadeIn(tween(PAGE_MS, easing = PAGE_EASING)) togetherWith fadeOut(tween(PAGE_FADE_OUT_MS)))
        .using(SizeTransform(clip = true) { _, _ -> tween(PAGE_MS, easing = PAGE_EASING) })

/**
 * Окно настроек поверх экрана — то же, что в DrozdRun (владелец, 2026-10-12/13/14). Выдвигается справа налево
 * из шестерёнки ([gear] — её место в координатах корня) почти на всю ширину — с равными полями слева и справа —
 * и закрывает шапку с заголовком и тумблером. Шестерёнка остаётся на своём месте — в правом столбце окна —
 * и закрывает его; под ней, на страницах тем, «‹» — на страницу выше. Нажатие мимо окна тоже закрывает;
 * закрывается всё окно разом.
 * - Первая страница — одна строка: «Разрешения» (с числом невыданных) и справа плитка текущей темы.
 * - Плитка нажата — на том же месте матрица семейств; семейство нажато — матрица его оттенков. Страница
 *   заменяет прежнюю, окно меняет высоту под неё. Матрица — по 4 в ряд, рядов сколько нужно плиткам.
 * - Нажатие на оттенок применяет его. Зажатие — применяет и плавно гасит окно, пока палец на плитке.
 */
@Composable
fun SettingsLayer(
    open: Boolean,
    gear: Rect,
    missingPerms: Int,
    onClose: () -> Unit,
    onPermissions: () -> Unit,
    onPick: (AppTheme) -> Unit,
) {
    var page by remember { mutableStateOf<PanelPage>(PanelPage.Main) }
    LaunchedEffect(open) { if (open) page = PanelPage.Main }
    var peeking by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(if (peeking) 0f else 1f, tween(PEEK_FADE_MS), label = "peek")
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (open) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } })
        // Поля окна — 12dp, внутренний отступ — 12dp: шестерёнка окна ложится ровно на место настоящей.
        // Ширина задана заранее: при выдвигании видимая часть растёт от правого края, а содержимое не
        // перекладывается (иначе окно «криво растёт из середины»).
        val panelWidth = maxWidth - 24.dp
        val top = (with(density) { gear.top.toDp() } - 12.dp).coerceAtLeast(0.dp)
        AnimatedVisibility(
            open,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = 12.dp)
                .graphicsLayer { alpha = fade },
            enter = expandHorizontally(tween(OPEN_MS), expandFrom = Alignment.End, clip = true) + fadeIn(tween(OPEN_MS)),
            exit = shrinkHorizontally(tween(OPEN_MS), shrinkTowards = Alignment.End, clip = true) + fadeOut(tween(OPEN_MS)),
        ) {
            Row(
                Modifier
                    .requiredWidth(panelWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.surf)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        page,
                        transitionSpec = { pageChange() },
                        label = "page",
                    ) { shown ->
                        when (shown) {
                            PanelPage.Main -> MainPage(missingPerms, onPermissions) { page = PanelPage.Families }
                            PanelPage.Families -> TileGrid(Themes.families) { f, size ->
                                TwoTone(
                                    f.main,
                                    selected = f === Themes.familyOf(theme),
                                    size,
                                    Modifier.clickable { page = PanelPage.Shades(f) },
                                )
                            }
                            is PanelPage.Shades -> TileGrid(shown.family.themes) { t, size ->
                                TwoTone(
                                    t,
                                    selected = t === theme,
                                    size,
                                    Modifier.holdOrTap(
                                        onTap = { onPick(t) },
                                        onHold = { onPick(t); peeking = true },
                                        onRelease = { peeking = false },
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    SettingsGear(open = true, onClick = onClose)
                    val back: (() -> Unit)? = when (page) {
                        PanelPage.Main -> null
                        PanelPage.Families -> { -> page = PanelPage.Main }
                        is PanelPage.Shades -> { -> page = PanelPage.Families }
                    }
                    // «‹» появляется вместе с высотой окна и с той же кривой — иначе правый столбец
                    // мгновенно вырастает на ряд и окно дёргается в начале смены страницы.
                    var lastBack by remember { mutableStateOf<() -> Unit>({}) }
                    if (back != null) lastBack = back
                    AnimatedVisibility(
                        back != null,
                        enter = fadeIn(tween(PAGE_MS, easing = PAGE_EASING)) + expandVertically(tween(PAGE_MS, easing = PAGE_EASING)),
                        exit = fadeOut(tween(PAGE_FADE_OUT_MS)) + shrinkVertically(tween(PAGE_MS, easing = PAGE_EASING)),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.field)
                                .clickable(onClick = lastBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("‹", color = theme.text, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

private const val OPEN_MS = 260

/** Первая страница: одна строка — «Разрешения» и плитка темы справа. Высота — как у шестерёнки. */
@Composable
private fun MainPage(missingPerms: Int, onPermissions: () -> Unit, onTheme: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPermissions)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Разрешения", color = theme.text, fontSize = 16.sp)
            if (missingPerms > 0) {
                Text(" · не выдано $missingPerms", color = theme.accent, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        ThemeTile(open = false, modifier = Modifier.size(40.dp), onClick = onTheme)
    }
}

/**
 * Матрица плиток: по [COLUMNS] в ряд на всю ширину окна, рядов — ровно сколько нужно плиткам.
 * [cell] получает модификатор размера плитки.
 */
@Composable
private fun <T> TileGrid(items: List<T>, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        for (row in items.chunked(COLUMNS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (item in row) cell(item, Modifier.weight(1f).aspectRatio(1f))
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Плиток в ряду матрицы (владелец, 2026-10-13: «четыре в ряд»). */
private const val COLUMNS = 4
