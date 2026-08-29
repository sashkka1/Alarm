package com.sasha.alarm.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasha.alarm.core.GuideCircle
import com.sasha.alarm.core.NfcScan
import kotlinx.coroutines.delay

/**
 * Палитра экрана меток — из макета «Экраны (Испытание и Победа)», 2026-08-18.
 *
 * ⚠️ Он единственный тёмный среди экранов тревоги: у остальных фон сигнально-красный.
 * Так задумано — здесь человек уже встал и ходит по квартире, будить его больше
 * не нужно, нужно чтобы крупная цифра читалась в темноте коридора.
 */
private val Bed = Color(0xFF0D0B0A)
private val Signal = Color(0xFFFF0026)
private val TextMain = Color(0xFFECE5E0)

/** Шаги маршрута: пройденный, тот, до которого ещё не дошли, и текущий. */
private val StepPassed = Color(0xFF1C1613)
private val StepFuture = Color(0xFF17110F)
private val StepPassedInk = Color(0xFFECE5E0).copy(alpha = 0.4f)
private val StepFutureInk = Color(0xFFECE5E0).copy(alpha = 0.28f)

/** Блок вердикта: верный ответ оранжевый, ошибка — сигнально-красная. */
private val BannerGood = Color(0xFFFF6A4D)

/**
 * Круг-поводырь — тот же зелёный, что у кружка громкости на остальных экранах.
 * Зелёное в приложении означает «тише», и спутать его ни с чем нельзя.
 * Обводка светлее заливки: на почти чёрном фоне тёмно-зелёное кольцо не видно.
 */
private val Guide = Color(0xFF2E7D32)
private val GuideEdge = Color(0xFF57C46A)

/**
 * Экран тревоги для испытания «обойти метки».
 *
 * Всё, что на нём есть: шапка, лента маршрута под ней и крупная цифра метки,
 * которую нужно приложить следующей. Ни подсказок, ни кнопок — идти и прикладывать.
 *
 * Лента показывает весь маршрут сразу, включая повторы: `2 → 1 → 3 → 2` это четыре
 * шага, и вторая метка честно стоит в ленте дважды. Пройденные шаги видно ясно,
 * непройденные приглушены до бледного, а текущий залит сигнальным красным —
 * взгляд спросонья должен находить его, не читая всю ленту.
 *
 * Шапка — общий [ChallengeHeader], тот же, что у остальных испытаний, но в цветах
 * этого экрана: чёрные часы на тёмном фоне не видно вовсе.
 */
@Composable
fun NfcAlarmScreen(
    timeText: String,
    route: List<Int>,
    done: Int,
    /** Чем кончилось последнее касание. Метки одинаковые — это единственный способ понять, что приложил. */
    scan: NfcScan?,
    /** Растёт с каждым касанием: одинаковый ответ подряд обязан показаться заново. */
    scanTick: Int,
    preview: Boolean,
    /**
     * Держит ли палец круг-поводырь. Зовётся **каждый кадр**, а не по смене:
     * для сервиса это ещё и признак, что экран жив, — см. [GuideLayer].
     */
    onGuideHold: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bed)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChallengeHeader(
            timeText = timeText,
            progressText = "$done / ${route.size}",
            preview = preview,
            // Часы и счёт красным (владелец, 2026-08-19): на тёмном фоне это
            // единственные два числа, и спокойный светлый цвет их прятал.
            ink = Signal,
            exitInk = Color(0xFFFF3B2A),
            exitBorder = Signal,
            onExit = onExit,
        )

        Spacer(Modifier.height(16.dp))
        RouteStrip(route, done)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val next = route.getOrNull(done)
            if (next == null) {
                Text(
                    text = stringResource(R.string.alarm_all_solved),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = next.toString(),
                    fontSize = 150.sp,
                    lineHeight = 150.sp,
                    fontWeight = FontWeight.Black,
                    // Чёрная, а не белая (владелец, 2026-08-19): цифра вырезана из
                    // красного пятна, и на свечении она читается резче светлой.
                    color = Color.Black,
                    // Красное свечение из макета. Тень средствами текста в Compose
                    // размывается слабо, поэтому рисуем её сами — заливкой позади.
                    modifier = Modifier.drawBehind {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.maxDimension * 1.4f
                        drawCircle(
                            brush = Brush.radialGradient(
                                // Три остановки, а не две: с одним переходом пятно
                                // получается кольцом — ярким по краю и пустым внутри.
                                colorStops = arrayOf(
                                    0f to Signal.copy(alpha = 0.55f),
                                    0.45f to Signal.copy(alpha = 0.30f),
                                    1f to Color.Transparent,
                                ),
                                center = center,
                                radius = radius,
                            ),
                            radius = radius,
                            center = center,
                        )
                    },
                )
            }

            GuideLayer(onHold = onGuideHold)
        }

        ScanBanner(scan, scanTick)
    }
}

/**
 * Круг-поводырь: тишина, пока палец ведёт его по экрану.
 *
 * Он всё время едет сам ([GuideCircle]), и держать его можно только рукой в
 * движении — положить палец и уйти не выйдет. Оторвался или отстал за край —
 * звонок возвращается за полторы секунды.
 *
 * ⚠️ Попадание проверяется **каждый кадр**, а не по событию касания: события
 * приходят только когда палец двигается, а круг уезжает и из-под неподвижного
 * пальца. Без покадровой проверки замерший палец давал бы вечную тишину.
 *
 * ⚠️ Тот же кадровый такт — сердцебиение для сервиса: [onHold] зовётся каждый
 * раз, и по нему сервис понимает, что экран жив. Пропало сердцебиение — тишина
 * снимается сама, чем бы её ни оборвало (P0 №7).
 *
 * ⚠️ Время берётся из часов кадров, а не из своего счётчика: они общие у активити
 * и у заслонки, поэтому при подмене окна круг продолжает путь, а не прыгает.
 */
@Composable
private fun BoxScope.GuideLayer(onHold: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val radiusPx = with(LocalDensity.current) { GuideCircle.RADIUS_DP.dp.toPx() }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Палец читается из потока касаний, а сверяется с кругом в кадровом цикле,
        // поэтому это состояние, а не локальная переменная.
        val finger = remember { mutableStateOf<Offset?>(null) }
        var center by remember { mutableStateOf(Offset(width / 2f, height / 2f)) }
        var held by remember { mutableStateOf(false) }
        val hold by rememberUpdatedState(onHold)

        LaunchedEffect(width, height, radiusPx) {
            while (true) {
                val frameMs = withFrameMillis { it }
                // Центр ходит в пределах, где круг помещается целиком: иначе он
                // наполовину уезжал бы за край, и держать его было бы нечем.
                val cx = radiusPx + (width - 2 * radiusPx) * GuideCircle.x(frameMs)
                val cy = radiusPx + (height - 2 * radiusPx) * GuideCircle.y(frameMs)
                center = Offset(cx, cy)

                val touch = finger.value
                val holding = touch != null &&
                    GuideCircle.holds(touch.x - cx, touch.y - cy, radiusPx)
                held = holding
                hold(holding)
            }
        }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    // Свой разбор касаний, а не detectDragGestures: тот начинает
                    // считать только после порога сдвига, и первые миллиметры за
                    // кругом шли бы мимо — то есть круг терялся бы в момент захвата.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        finger.value = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            finger.value = change.position
                        }
                        finger.value = null
                    }
                },
        ) {
            // Держат — заливка плотная; нет — сквозь неё видно цифру метки, а она
            // на этом экране главная.
            drawCircle(
                color = Guide.copy(alpha = if (held) 0.92f else 0.26f),
                radius = radiusPx,
                center = center,
            )
            drawCircle(
                color = GuideEdge,
                radius = radiusPx,
                center = center,
                style = Stroke(width = if (held) 5.dp.toPx() else 3.dp.toPx()),
            )
        }
    }
}

/**
 * Ответ на касание.
 *
 * Метки ничем не подписаны и на ощупь неотличимы, поэтому молчание — негодный ответ:
 * по нему не понять, промахнулся ли комнатой, приложил ли чужую метку или просто не
 * оторвал телефон. Плашка живёт [BANNER_MS] и гаснет сама, чтобы экран возвращался
 * к своему единственному делу — крупной цифре.
 *
 * ⚠️ Одно слово во весь блок (владелец, 2026-08-19), номера метки в ответе больше
 * нет: спросонья он не нужен, нужно «туда или не туда». Цвет несёт то же самое и
 * читается ещё раньше текста — верно оранжевым, ошибка сигнально-красным.
 */
@Composable
private fun ScanBanner(scan: NfcScan?, scanTick: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scanTick) {
        if (scanTick == 0) return@LaunchedEffect
        visible = true
        delay(BANNER_MS)
        visible = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (!visible || scan == null) return@Box

        val good = scan is NfcScan.Right
        val text = when (scan) {
            is NfcScan.Right -> stringResource(R.string.nfc_scan_right)
            // «Ещё раз», а не «Неверно»: метка та самая, но телефон не оторвали.
            // Сказать про неё «неверно» значило бы отправить искать другую.
            is NfcScan.TooSoon -> stringResource(R.string.nfc_scan_too_soon)
            else -> stringResource(R.string.nfc_scan_wrong)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (good) BannerGood else Signal,
                    shape = RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // ⚠️ По сигнальному красному пишем чёрным: он светлый, и белое по нему
                // не читается — то же правило, что у клавиатуры экрана тревоги.
                text = text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Высота блока вердикта. Крупный намеренно: его читают в движении, не приглядываясь. */
private val BANNER_HEIGHT = 92.dp

/** Сколько висит ответ на касание. */
private const val BANNER_MS = 2_500L

/**
 * Лента маршрута под шапкой.
 *
 * Едет сама: после каждого засчитанного шага текущая метка выезжает к левому краю,
 * поэтому длинный маршрут не приходится листать пальцем посреди ночи.
 */
@Composable
private fun RouteStrip(route: List<Int>, done: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(done) {
        if (route.isNotEmpty()) {
            listState.animateScrollToItem(index = (done - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(route) { index, number ->
            RouteStep(
                number = number,
                passed = index < done,
                current = index == done,
            )
        }
    }
}

@Composable
private fun RouteStep(number: Int, passed: Boolean, current: Boolean) {
    // Текущий шаг крупнее всех и залит сигнальным красным; пройденные читаются
    // спокойно; те, до которых ещё не дошли, приглушены почти до фона.
    val size by animateDpAsState(
        targetValue = if (current) 58.dp else 40.dp,
        animationSpec = spring(),
        label = "stepSize",
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = when {
                    current -> Signal
                    passed -> StepPassed
                    else -> StepFuture
                },
                shape = RoundedCornerShape(if (current) 16.dp else 12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontSize = if (current) 26.sp else 17.sp,
            fontWeight = FontWeight.Black,
            // По красному — чёрным, как везде в приложении.
            color = when {
                current -> Color.Black
                passed -> StepPassedInk
                else -> StepFutureInk
            },
        )
    }
}
