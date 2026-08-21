package com.sasha.alarm.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.MathOperation
import com.sasha.alarm.core.MathTask
import com.sasha.alarm.core.PoseLandmarks
import com.sasha.alarm.core.PosePoint
import com.sasha.alarm.core.PoseSmoother
import com.sasha.alarm.core.PushupCounter
import com.sasha.alarm.core.PushupOverlay

/**
 * Палитра экрана тревоги — из макета Claude Design (принята 2026-08-16).
 *
 * Сигнальный красный во весь экран и чёрный текст поверх. Раньше он был белым;
 * задача осталась той же — не выглядеть, а разбудить, — но красный на полной
 * яркости решает её лучше белого листа.
 */
private val Signal = Color(0xFFFF0026)
private val Ink = Color(0xFF000000)
private val Muted = Color(0x99000000)
private val Bad = Color(0xFF5A0010)

/** Подложка клавиатуры: почти чёрная, чтобы красные клавиши на ней читались. */
private val KeypadBed = Color(0xFF120B0C)

/** Подложка области камеры: она же видна, пока камера открывается. */
private val CameraBed = Color(0xFF120B0C)
private val KeyDelete = Color(0xFF241417)
private val KeyAccent = Color(0xFFC24A32)

private val Circle = Color(0xFF2E7D32)

/**
 * Экран тревоги: белый фон, чёрный текст, пример и клавиатура.
 *
 * Кнопки «Выключить» нет намеренно (решение владельца 2026-08-14) — выход отсюда
 * один: решить все примеры. Плюс страховки, которые от экрана не зависят: дедлайн
 * бэкапа и команда с компьютера.
 *
 * Белый и яркий он не по недосмотру: задача экрана не беречь глаза, а разбудить.
 */
@Composable
fun AlarmScreen(
    timeText: String,
    challenge: Challenge,
    task: MathTask?,
    answer: String,
    solved: Int,
    total: Int,
    reactionProgress: Double,
    reactionHits: Int,
    reactionCircles: List<ReactionCircleUi>,
    pushupReps: Int,
    pushupTarget: Int,
    /** Что сказать про то, что видно в кадре. Пустая строка — молчим. */
    pushupHint: String,
    /** Скелет, который видит модель. null — позы в кадре нет. */
    pushupSkeleton: PoseSkeletonUi?,
    /**
     * Картинка с камеры. Рисует её `:app` — сама камера живёт в `:platform`,
     * а `:ui` про неё ничего не знает.
     */
    cameraContent: @Composable () -> Unit,
    onReactionHit: (Long) -> Unit,
    wrongTick: Int,
    preview: Boolean,
    circleVisible: Boolean,
    circleX: Float,
    circleY: Float,
    onKey: (AlarmKey) -> Unit,
    onCircleTap: () -> Unit,
    onExit: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Signal),
    ) {
        val width = maxWidth
        val height = maxHeight

        // Клавиатура и полоса опасности идут во всю ширину, поэтому отступы живут
        // не на колонке, а внутри её частей.
        val keypadNeeded = challenge != Challenge.REACTION && challenge != Challenge.PUSHUPS
        val pushups = challenge == Challenge.PUSHUPS

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                ChallengeHeader(
                    timeText = timeText,
                    progressText = challenge.progressText(
                        solved = solved,
                        total = total,
                        reactionHits = reactionHits,
                        pushupReps = pushupReps,
                        pushupTarget = pushupTarget,
                    ),
                    preview = preview,
                    onExit = onExit,
                )

                // Шкала внимания: убывает от пойманных кружков, растёт от упущенных.
                // Без неё по экрану не понять, сколько ещё ловить (владелец, 2026-08-16).
                if (challenge == Challenge.REACTION) {
                    Spacer(Modifier.height(14.dp))
                    ReactionMeterBar(reactionProgress)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Камере отступы ни к чему: она занимает всё, что осталось между
                    // шапкой и подсказкой, — и ровно поэтому больше на них не налезает.
                    .padding(horizontal = if (pushups) 0.dp else 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (challenge) {
                    // При испытании шариками середина пустая: шкала уже в шапке,
                    // а здесь только сами шарики поверх экрана.
                    Challenge.REACTION -> Unit

                    // Картинка живёт здесь, а не подложкой во весь экран: подложка
                    // лезла под шапку и подсказку, и текст оказывался на кадре
                    // (владелец, 2026-08-16). Обрезать её по-прежнему нельзя —
                    // кадрирование и есть причина, по которой счёт отказывает.
                    // Своя тёмная подложка у области камеры не для красоты: камера
                    // открывается не мгновенно, и до первого кадра сквозь неё
                    // просвечивал красный фон — это и было мелькание при входе
                    // (владелец, 2026-08-16). Тёмный прямоугольник просто ждёт.
                    Challenge.PUSHUPS -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CameraBed),
                    ) {
                        cameraContent()
                        pushupSkeleton?.let { PoseOverlay(it) }
                    }

                    // Метки без маршрута идут по примерам — тем же телом экрана.
                    else -> MathBody(task, answer, wrongTick)
                }
            }

            if (pushups) PushupHint(pushupHint)

            if (keypadNeeded) {
                DangerStripe()
                Keypad(onKey)
            }
        }

        // Кружки внимания лежат поверх всего экрана, а не внутри колонки.
        reactionCircles.forEach { circle ->
            key(circle.id) {
                TapCircle(
                    x = width * circle.x,
                    y = height * circle.y,
                    seed = circle.id,
                    onTap = { onReactionHit(circle.id) },
                )
            }
        }

        if (circleVisible) {
            FloatingCircle(
                x = width * circleX,
                y = height * circleY,
                onTap = onCircleTap,
            )
        }
    }
}

/** Кружок испытания на внимание для отрисовки. */
data class ReactionCircleUi(val id: Long, val x: Float, val y: Float)

enum class AlarmKey { D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, DELETE, ENTER }

/**
 * Палитра кружков внимания.
 *
 * ⚠️ Ни зелёного, ни красного здесь нет намеренно. Зелёный закреплён за кружком,
 * который сбивает громкость, — спутать их нельзя. Красный и всё, что рядом с ним,
 * убрано после проверки на телефоне (владелец, 2026-08-16): экран тревоги сам
 * красный, и красноватые шарики на нём мельтешили, вместо того чтобы ловиться.
 */
private val CirclePalette = listOf(
    Color(0xFF93DBFF) to Color(0xFF0077FF),
    Color(0xFFB9A6FF) to Color(0xFF5B2BFF),
    Color(0xFF9FF3FF) to Color(0xFF00A8C8),
    Color(0xFFFFE08A) to Color(0xFFFFB300),
    Color(0xFFCDEFFF) to Color(0xFF2F6BFF),
    Color(0xFFE4D4FF) to Color(0xFF7A3BFF),
)

/**
 * Кружок испытания на внимание.
 *
 * Растёт **из ничего** — начинается точкой и за секунду доходит до своего размера.
 * Финальный размер у каждого свой, от 70% до 130% базового, цвет тоже: так экран
 * не выглядит набором одинаковых пятен и глаз цепляется за движение.
 */
@Composable
private fun TapCircle(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    seed: Long,
    onTap: () -> Unit,
) {
    val palette = CirclePalette[((seed / 7).mod(CirclePalette.size.toLong())).toInt()]
    val (light, deep) = palette

    // 70%..130% базового размера, семь ступеней по номеру кружка.
    val scaleFactor = 0.7f + ((seed / 13).mod(7L)).toInt() * 0.1f
    val finalSize = (BASE_CIRCLE_DP * scaleFactor).dp

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val grow by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.02f,
        animationSpec = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
        label = "grow",
    )

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(finalSize)
            .scale(grow)
            .background(
                brush = Brush.radialGradient(colors = listOf(light, deep)),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    )
}

private const val BASE_CIRCLE_DP = 62f

/**
 * Скорость шкалы внимания: долей шкалы в секунду.
 *
 * Полную шкалу проходит примерно за полторы секунды. Медленнее — и она не будет
 * успевать за игрой; быстрее — и снова станут видны отдельные рывки.
 */
private const val METER_SPEED = 0.65f

private fun MathOperation.sign(): String = when (this) {
    MathOperation.PLUS -> "+"
    MathOperation.MINUS -> "−"
}

/**
 * Шапка, общая для всех испытаний: часы слева, ход испытания справа.
 *
 * ⚠️ Секундомера «сколько уже идёт» здесь нет и не должно быть (решение владельца
 * 2026-08-16, отмена его же прежнего): ни в примерах, ни в любом другом испытании.
 * Часы — не он: их показ переключается в «Блокировке».
 */
@Composable
internal fun ChallengeHeader(
    timeText: String,
    progressText: String,
    preview: Boolean,
    /**
     * Цвета шапки.
     *
     * Параметрами, а не константами: у метки экран тёмный (макет 2026-08-18), и
     * чёрные часы на нём не видно вовсе. Значения по умолчанию — красный экран,
     * там ничего не меняется.
     */
    ink: Color = Ink,
    exitInk: Color = Bad,
    exitBorder: Color = Ink,
    onExit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Слева часы, по центру «Выйти» (только в проверке), справа счёт —
        // раскладка владельца (2026-08-16). Крайние на своих местах всегда,
        // поэтому середина отдана кнопке и в бою просто пустует.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            HeaderChip(timeText, ink)
        }
        if (preview) {
            Text(
                text = stringResource(R.string.alarm_exit).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = exitInk,
                modifier = Modifier
                    .border(1.5.dp, exitBorder)
                    .clickable(onClick = onExit)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            HeaderChip(progressText, ink)
        }
    }
}

/**
 * Число в шапке.
 *
 * Поверх камеры — белым на тёмной подложке. Чёрный текст на кадре сереет и
 * пропадает, а шапка нужна именно там, где идёт счёт повторов.
 */
@Composable
private fun HeaderChip(text: String, ink: Color) {
    // Часы и счёт — одним начертанием и одного размера (владелец, 2026-08-16):
    // разный кегль по краям шапки читался как два разных шрифта.
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
        color = ink,
    )
}

/**
 * Правый угол шапки — единственное место, где она знает про испытание.
 *
 * У примеров это «решено / всего», у отжиманий — «сделано / нужно», у шариков просто
 * счёт пойманных: общего числа у них нет вовсе, промахи двигают шкалу назад, поэтому
 * «из скольки» соврало бы. Метки ещё не готовы и идут по примерам — их счётчик и
 * показывают.
 */
private fun Challenge.progressText(
    solved: Int,
    total: Int,
    reactionHits: Int,
    pushupReps: Int,
    pushupTarget: Int,
): String = when (this) {
    Challenge.REACTION -> "$reactionHits"
    Challenge.PUSHUPS -> "$pushupReps / $pushupTarget"
    else -> "$solved / $total"
}

/**
 * Скелет, который видит модель, — для отрисовки поверх картинки с камеры.
 *
 * Точки идут в том же порядке, что и кости в [PoseBones]: плечи, локти, кисти, таз.
 * [aspect] — пропорции кадра, из которого точки взяты: без него скелет разъедется
 * с телом на любом экране, кроме случайно совпавшего.
 */
data class PoseSkeletonUi(
    val points: List<PosePoint>,
    val aspect: Float,
    /** Чем рисовать: объёмной фигурой или голыми точками. */
    val overlay: PushupOverlay = PushupOverlay.FIGURE,
)

/**
 * Человечек из блоков поверх картинки с камеры.
 *
 * Не точки и палки, а фигура: голова кругом, корпус и таз плитами, руки и ноги
 * скруглёнными цилиндрами (владелец, 2026-08-16). Полупрозрачный намеренно —
 * сквозь него видно себя, и сразу понятно, где приложение промахнулось.
 *
 * Цвет — плавный переход от красного через жёлтый к зелёному по уверенности
 * модели, а не два состояния: резкая граница мигала у порога и врала, ведь
 * 0.59 и 0.61 это почти одно и то же.
 */
@Composable
private fun PoseOverlay(skeleton: PoseSkeletonUi) {
    val latest by rememberUpdatedState(skeleton.points)
    var memory by remember { mutableStateOf<PoseSmoother.Memory?>(null) }

    // Скелет догоняет модель каждый кадр экрана, а не появляется рывками вместе с
    // её ответами: тяжёлая модель успевает несколько раз в секунду, экран рисует
    // шестьдесят, и без этого точки просто телепортируются.
    LaunchedEffect(Unit) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val delta = if (previousFrameNanos == 0L) 0L else (nanos - previousFrameNanos) / 1_000_000
                previousFrameNanos = nanos
                memory = PoseSmoother.blend(memory, latest, delta)
            }
        }
    }

    val points = memory?.points ?: skeleton.points

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Картинка вписана в экран целиком (FIT_CENTER), поэтому и точки кладём
        // в тот же вписанный прямоугольник, а не растягиваем на весь холст.
        val fitByWidth = size.width / size.height < skeleton.aspect
        val drawWidth = if (fitByWidth) size.width else size.height * skeleton.aspect
        val drawHeight = if (fitByWidth) size.width / skeleton.aspect else size.height
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f

        fun at(index: Int): Offset? {
            val point = points.getOrNull(index) ?: return null
            // Точку, которой в кадре нет, модель отдаёт нулевой видимостью —
            // рисовать её значит пририсовать человеку конечность в углу экрана.
            if (point.confidence <= 0f) return null
            return Offset(left + point.x * drawWidth, top + point.y * drawHeight)
        }

        fun depth(index: Int): Float = points.getOrNull(index)?.z ?: 0f
        fun sure(vararg indexes: Int): Float =
            indexes.minOf { points.getOrNull(it)?.confidence ?: 0f }

        // Скелет: точки, связанные линиями (владелец, 2026-08-17 — без линий это
        // была россыпь). Второй вариант из настроек, рядом с объёмной фигурой.
        if (skeleton.overlay == PushupOverlay.DOTS) {
            PoseLandmarks.BONES.forEach { (a, b) ->
                val from = at(a) ?: return@forEach
                val to = at(b) ?: return@forEach
                // Кость не надёжнее худшего своего конца.
                drawLine(
                    color = confidenceColor(sure(a, b)),
                    start = from,
                    end = to,
                    strokeWidth = 7f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEachIndexed { index, point ->
                val at = at(index) ?: return@forEachIndexed
                drawCircle(confidenceColor(point.confidence), radius = 10f, center = at)
            }
            return@Canvas
        }

        /**
         * Мерка фигуры.
         *
         * Не ширина плеч: при съёмке сбоку плечи проецируются почти в одну точку,
         * и по ним всё тело схлопывалось в ниточку. Берём длину корпуса — она
         * ракурсом не убивается, потому что тело поперёк кадра всегда протяжённое.
         */
        val spineTop = midpoint(at(PoseLandmarks.LEFT_SHOULDER), at(PoseLandmarks.RIGHT_SHOULDER))
        val spineBottom = midpoint(at(PoseLandmarks.LEFT_HIP), at(PoseLandmarks.RIGHT_HIP))
        val unit = if (spineTop != null && spineBottom != null) {
            (spineTop - spineBottom).getDistance() * 0.62f
        } else {
            drawWidth * 0.14f
        }

        /**
         * Одна часть фигуры, отложенная до сортировки.
         *
         * Рисуем не подряд, а от дальнего к ближнему: тогда ближняя рука ложится
         * поверх дальней, и плоская картинка начинает читаться объёмной. Это
         * единственное, что даёт ощущение трёхмерности без настоящей трёхмерной сцены.
         */
        val parts = mutableListOf<Triple<Float, Float, DrawScope.() -> Unit>>()

        /** Толщина с поправкой на глубину: ближе к камере — толще. */
        fun thicknessAt(base: Float, z: Float): Float =
            base * (1f - z.coerceIn(-DEPTH_RANGE, DEPTH_RANGE) / DEPTH_RANGE * DEPTH_SCALE)

        /** Цвет с поправкой на глубину: дальнее темнее, как в тени. */
        fun shade(color: Color, z: Float): Color {
            val far = ((z / DEPTH_RANGE).coerceIn(-1f, 1f) + 1f) / 2f
            return lerp(color, Color.Black, far * DEPTH_SHADE).copy(alpha = FIGURE_ALPHA)
        }

        fun limb(from: Int, to: Int, base: Float) {
            val a = at(from) ?: return
            val b = at(to) ?: return
            val z = (depth(from) + depth(to)) / 2f
            val color = shade(confidenceColor(sure(from, to)), z)
            val width = thicknessAt(unit * base, z)
            parts += Triple(z, width) {
                drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
            }
        }

        // Корпус: не плоский четырёхугольник по четырём точкам, а цилиндр вдоль
        // позвоночника. Четырёхугольник исчезал, когда грудь развёрнута к камере
        // и плечи с тазом сходились в линию (владелец, 2026-08-16). У цилиндра
        // толщина своя и от ракурса не зависит.
        if (spineTop != null && spineBottom != null) {
            val z = (
                depth(PoseLandmarks.LEFT_SHOULDER) + depth(PoseLandmarks.RIGHT_SHOULDER) +
                    depth(PoseLandmarks.LEFT_HIP) + depth(PoseLandmarks.RIGHT_HIP)
                ) / 4f
            val color = shade(
                confidenceColor(
                    sure(
                        PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER,
                        PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP,
                    ),
                ),
                z,
            )
            // Ширина корпуса — по плечам, но не уже собственной меры: даже строго
            // анфас или строго в профиль грудь остаётся грудью, а не ниткой.
            val span = (at(PoseLandmarks.LEFT_SHOULDER) ?: spineTop)
                .minus(at(PoseLandmarks.RIGHT_SHOULDER) ?: spineTop).getDistance()
            val width = thicknessAt(maxOf(span * 0.8f, unit * 0.62f), z)
            parts += Triple(z, width) {
                drawLine(color, spineTop, spineBottom, strokeWidth = width, cap = StrokeCap.Round)
            }
        }

        limb(PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP, 0.38f)

        // Руки и ноги цилиндрами. Ноги толще рук — как у человека.
        // Все толщины срезаны примерно на треть (владелец, 2026-08-16): прежняя
        // фигура выходила слишком жирной и закрывала собой человека.
        limb(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_ELBOW, 0.21f)
        limb(PoseLandmarks.LEFT_ELBOW, PoseLandmarks.LEFT_WRIST, 0.17f)
        limb(PoseLandmarks.LEFT_WRIST, PoseLandmarks.LEFT_HAND, 0.14f)
        limb(PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_ELBOW, 0.21f)
        limb(PoseLandmarks.RIGHT_ELBOW, PoseLandmarks.RIGHT_WRIST, 0.17f)
        limb(PoseLandmarks.RIGHT_WRIST, PoseLandmarks.RIGHT_HAND, 0.14f)
        limb(PoseLandmarks.LEFT_HIP, PoseLandmarks.LEFT_KNEE, 0.25f)
        limb(PoseLandmarks.LEFT_KNEE, PoseLandmarks.LEFT_ANKLE, 0.20f)
        limb(PoseLandmarks.LEFT_ANKLE, PoseLandmarks.LEFT_FOOT, 0.16f)
        limb(PoseLandmarks.RIGHT_HIP, PoseLandmarks.RIGHT_KNEE, 0.25f)
        limb(PoseLandmarks.RIGHT_KNEE, PoseLandmarks.RIGHT_ANKLE, 0.20f)
        limb(PoseLandmarks.RIGHT_ANKLE, PoseLandmarks.RIGHT_FOOT, 0.16f)

        // Голова шаром. Центр — между ушами, а не в носу: нос смещён вперёд, и
        // круг по нему сползал бы с головы при съёмке сбоку.
        val head = midpoint(at(PoseLandmarks.LEFT_EAR), at(PoseLandmarks.RIGHT_EAR))
            ?: at(PoseLandmarks.NOSE)
        if (head != null) {
            val z = (depth(PoseLandmarks.LEFT_EAR) + depth(PoseLandmarks.RIGHT_EAR)) / 2f
            val color = shade(
                confidenceColor(sure(PoseLandmarks.LEFT_EAR, PoseLandmarks.RIGHT_EAR)),
                z,
            )
            spineTop?.let { neck ->
                val width = thicknessAt(unit * 0.22f, z)
                parts += Triple(z, width) {
                    drawLine(color, head, neck, strokeWidth = width, cap = StrokeCap.Round)
                }
            }
            val radius = thicknessAt(unit * 0.34f, z)
            parts += Triple(z, radius) { drawCircle(color, radius, head) }
        }

        // От дальнего к ближнему.
        parts.sortedByDescending { it.first }.forEach { (_, _, draw) -> draw() }
    }
}

/** Середина между двумя точками. null, если хотя бы одной нет в кадре. */
private fun midpoint(a: Offset?, b: Offset?): Offset? = when {
    a != null && b != null -> Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    else -> a ?: b
}

/** Насколько человечек прозрачен: сквозь него должно быть видно себя. */
private const val FIGURE_ALPHA = 0.62f

/**
 * Разброс глубины, на который рассчитаны поправки.
 *
 * Модель отдаёт глубину в тех же долях, что и ширину кадра, относительно таза.
 * Полкадра туда-обратно с запасом покрывает вытянутую к камере руку.
 */
private const val DEPTH_RANGE = 0.5f

/** Насколько ближняя часть толще дальней. Половина — заметно, но не карикатурно. */
private const val DEPTH_SCALE = 0.5f

/** Насколько дальняя часть темнее. Тень, а не чернота. */
private const val DEPTH_SHADE = 0.45f

/**
 * Цвет по уверенности: красный → оранжевый → жёлтый → зелёный.
 *
 * Границы шкалы не абстрактные: ниже [PushupCounter.MIN_CONFIDENCE] точка в счёт
 * не идёт вовсе, поэтому там цвет уже откровенно красный, а полностью зелёным
 * становится только то, в чём модель уверена целиком.
 */
private fun confidenceColor(confidence: Float): Color {
    val share = confidence.coerceIn(0f, 1f)
    return when {
        share < 0.5f -> lerp(SkeletonBad, SkeletonWarn, share / 0.5f)
        else -> lerp(SkeletonWarn, SkeletonGood, (share - 0.5f) / 0.5f)
    }
}

private val SkeletonBad = Color(0xFFFF2D2D)
private val SkeletonWarn = Color(0xFFFFD400)
private val SkeletonGood = Color(0xFF00E07A)

/**
 * Подсказка про то, что видит камера.
 *
 * Лежит внизу и держится за низ блока: середина занята человеком в кадре, и
 * закрывать её надписью — значит прятать ровно то, ради чего картинка нужна.
 */
@Composable
private fun PushupHint(hint: String) {
    // Подсказка живёт под кадром, на красном, и цветом ничем не отличается от
    // остального экрана тревоги: чёрным по сигнальному.
    Text(
        text = hint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Black,
        color = Ink,
        textAlign = TextAlign.Center,
    )
}

/** Тело испытания примерами: сам пример и поле ответа. */
@Composable
private fun MathBody(task: MathTask?, answer: String, wrongTick: Int) {
    if (task == null) {
        Text(stringResource(R.string.alarm_all_solved), fontSize = 26.sp, color = Ink)
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        // Крупно, как в макете: пример должен читаться спросонья, не приглядываясь.
        Text(
            text = "${task.left} ${task.operation.sign()} ${task.right}",
            fontSize = 72.sp,
            lineHeight = 72.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-3).sp,
            color = Ink,
        )
        AnswerBox(answer, wrongTick)
    }
}

/**
 * Шкала внимания: убывает от пойманных кружков, растёт от упущенных.
 *
 * Ширина едет плавно, а не скачет от каждого попадания — иначе полоска дёргается
 * и читать по ней прогресс невозможно.
 */
@Composable
private fun ReactionMeterBar(progress: Double) {
    val target by rememberUpdatedState((progress / 100.0).toFloat().coerceIn(0f, 1f))
    var width by remember { mutableStateOf(target) }

    // Шкала едет с постоянной скоростью, а не догоняет каждое событие отдельно
    // (владелец, 2026-08-16). Пружина здесь не годилась: промахи приходят пачками
    // по несколько за такт, она успевала доехать до каждого — и ход разбивался
    // на кусочки. Тут цель может прыгать сколько угодно, полоска всё равно идёт
    // ровно и одинаково в обе стороны.
    LaunchedEffect(Unit) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val seconds =
                    if (previousFrameNanos == 0L) 0f else (nanos - previousFrameNanos) / 1_000_000_000f
                previousFrameNanos = nanos
                val step = METER_SPEED * seconds
                width = when {
                    kotlin.math.abs(target - width) <= step -> target
                    target > width -> width + step
                    else -> width - step
                }
            }
        }
    }

    // Пояснения под шкалой нет: по убывающей полоске и так видно, куда она едет,
    // а лишний текст на экране тревоги не читают (владелец, 2026-08-16).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(Color(0x33000000), RoundedCornerShape(7.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(width)
                .height(14.dp)
                .background(Ink, RoundedCornerShape(7.dp)),
        )
    }
}

/**
 * Поле ответа.
 *
 * Ни фона, ни черты (владелец, 2026-08-16): на месте ответа просто пусто, пока
 * не начал набирать, а потом там просто цифры. Ошибка показывается тем, что цифры
 * мигают красным, — рамке для этого взяться неоткуда.
 */
@Composable
private fun AnswerBox(answer: String, wrongTick: Int) {
    val transition = rememberInfiniteTransition(label = "wrong")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    val bad = wrongTick > 0 && answer.isEmpty()

    Box(
        modifier = Modifier.height(66.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = answer,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            color = if (bad) Bad.copy(alpha = 0.4f + pulse * 0.6f) else Ink,
        )
    }
}

/**
 * Полоса опасности между заданием и клавиатурой.
 *
 * Из макета: чёрно-красные диагонали. Она же служит границей — ниже начинается
 * тёмная подложка клавиатуры, и глаз не путает поле ввода с цифрами.
 */
@Composable
private fun DangerStripe() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val step = 11.dp.toPx()
        drawRect(Signal)
        // Диагонали под 45°: начинаем левее холста, чтобы полоса не обрывалась у края.
        var x = -size.height
        while (x < size.width + size.height) {
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, size.height)
                    lineTo(x + size.height, 0f)
                    lineTo(x + size.height + step, 0f)
                    lineTo(x + step, size.height)
                    close()
                },
                color = Ink,
            )
            x += step * 2
        }
    }
}

@Composable
private fun Keypad(onKey: (AlarmKey) -> Unit) {
    val rows = listOf(
        listOf(AlarmKey.D1, AlarmKey.D2, AlarmKey.D3),
        listOf(AlarmKey.D4, AlarmKey.D5, AlarmKey.D6),
        listOf(AlarmKey.D7, AlarmKey.D8, AlarmKey.D9),
        listOf(AlarmKey.DELETE, AlarmKey.D0, AlarmKey.ENTER),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(KeypadBed)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> Key(key, Modifier.weight(1f)) { onKey(key) } }
            }
        }
    }
}

@Composable
private fun Key(key: AlarmKey, modifier: Modifier, onClick: () -> Unit) {
    val label = when (key) {
        AlarmKey.DELETE -> "⌫"
        AlarmKey.ENTER -> "✓"
        else -> key.ordinal.toString()
    }
    // Два вида клавиш: цифры красные с чёрным, служебные — чёрные с красным.
    // «Стереть» и «ввод» одного вида (владелец, 2026-08-16): разными цветами
    // они читались как разные по важности, хотя обе просто служебные.
    val service = key == AlarmKey.DELETE || key == AlarmKey.ENTER
    val background = if (service) Ink else Signal
    val ink = if (service) Signal else Ink
    Box(
        modifier = modifier
            .height(54.dp)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}

/**
 * Кружок, сбивающий громкость.
 *
 * Мелкий, быстрый и мечущийся: по вертикали и по горизонтали ходит с разными
 * периодами, поэтому траектория не повторяется и палец на одном месте не поможет —
 * его приходится ловить. Лежит поверх всего, включая клавиатуру.
 */
@Composable
private fun FloatingCircle(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "circle")
    val driftY by transition.animateFloat(
        initialValue = -46f,
        targetValue = 46f,
        animationSpec = infiniteRepeatable(tween(780), RepeatMode.Reverse),
        label = "driftY",
    )
    // Период по горизонтали намеренно не кратен вертикальному — так путь выходит рваным.
    val driftX by transition.animateFloat(
        initialValue = -34f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "driftX",
    )
    val breath by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "breath",
    )

    Box(
        modifier = Modifier
            .offset(x = x + driftX.dp, y = y + driftY.dp)
            // Крупнее прежнего (владелец, 2026-08-16): он мечется, и попасть по
            // нему на бегу было слишком тонкой работой для шести утра.
            .size(50.dp)
            .scale(breath)
            .background(Circle, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    )
}
