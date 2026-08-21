package com.sasha.alarm.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasha.alarm.core.VictoryStats
import kotlin.math.cos
import kotlin.math.sin

/**
 * Палитра экрана победы — из макета «Экраны (Испытание и Победа)», 2026-08-18.
 *
 * Тёплая и светлая намеренно: это единственный экран приложения, который не будит,
 * а отпускает. Сигнально-красный здесь был бы обманом — тревоги уже нет.
 */
private val Paper = Color(0xFFFFF1E6)
private val Glow = Color(0xFFFF5A1A)
private val Ink = Color(0xFF2A1206)
private val Muted = Color(0xFFB5794E)
private val Exit = Color(0xFFE23A17)
private val Ray = Color(0xFFFF5A1A)
private val SunCore = Color(0xFFFF8A3D)
private val SunHighlight = Color(0xFFFFB067)

/**
 * Экран победы: испытание выполнено, тревога уже снята.
 *
 * Показывается **только за настоящую победу** (решение владельца 2026-08-16): снятие
 * по дедлайну сторожем закрывает экран молча, поздравлять там не с чем.
 *
 * Фон светлый и тёплый: победа приходит сразу после экрана тревоги, и мигать чёрным
 * в глаза на этом переходе незачем.
 *
 * ⚠️ **Статистики здесь больше нет** (владелец, 2026-08-19: «я пока от неё отойду,
 * мне не интересно»). Осталось солнце, два слова и кнопка. [stats] по-прежнему
 * считается и доходит сюда — вернуть таблицу, когда она понадобится, дешевле, чем
 * восстанавливать всю цепочку подсчёта.
 */
@Composable
fun VictoryScreen(@Suppress("UNUSED_PARAMETER") stats: VictoryStats, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Свечение сверху по центру — оттуда «встаёт» солнце. Рисуем сами, а не
            // фоном-градиентом: радиус и центр заданы долями экрана, а не пикселями.
            .drawBehind {
                drawRect(Paper)
                val center = Offset(size.width / 2f, size.height * 0.15f)
                val radius = size.height * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Glow.copy(alpha = 0.22f), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        Sun()
        Spacer(Modifier.height(34.dp))
        Text(
            text = stringResource(R.string.victory_title),
            fontSize = 46.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.victory_subtitle),
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Exit, RoundedCornerShape(18.dp))
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.victory_exit),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * Солнце — единственная картинка в приложении, и та нарисована кодом: файлов-ассетов
 * в проекте нет вовсе.
 *
 * Всходит: круг вырастает пружиной, лучи медленно поворачиваются и дышат.
 */
@Composable
private fun Sun() {
    var risen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { risen = true }

    val rise by animateFloatAsState(
        targetValue = if (risen) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 120f),
        label = "rise",
    )

    val transition = rememberInfiniteTransition(label = "sun")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "spin",
    )
    // Лучи слегка тянутся и втягиваются — иначе картинка выглядит наклейкой.
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse),
        label = "breath",
    )

    Canvas(
        modifier = Modifier
            // Крупнее макета (владелец, 2026-08-19): статистики на экране больше нет,
            // и солнце осталось единственным, на что тут смотреть.
            .size(196.dp)
            .scale(rise),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        rotate(degrees = spin, pivot = center) {
            repeat(RAYS) { index ->
                val angle = (2.0 * Math.PI * index / RAYS).toFloat()
                val dx = cos(angle)
                val dy = sin(angle)
                // Доли взяты прямо из макета: луч идёт от 0.547 до 0.827 радиуса.
                val inner = radius * 0.547f
                val outer = radius * 0.827f * breath
                drawLine(
                    color = Ray.copy(alpha = 0.92f),
                    start = center + Offset(dx * inner, dy * inner),
                    end = center + Offset(dx * outer, dy * outer),
                    strokeWidth = radius * 0.093f,
                    cap = StrokeCap.Round,
                )
            }
        }

        drawCircle(color = SunCore, radius = radius * 0.4f, center = center)
        // Блик смещён к левому верхнему краю — от него шар и выглядит шаром.
        drawCircle(
            color = SunHighlight,
            radius = radius * 0.32f,
            center = center - Offset(radius * 0.093f, radius * 0.093f),
        )
    }
}

/** Лучей восемь: четыре по осям и четыре по диагоналям, как в макете. */
private const val RAYS = 8
