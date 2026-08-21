package com.sasha.alarm.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Точка скелета, как её отдаёт модель распознавания позы.
 *
 * Координаты нормированные (0..1 по ширине и высоте кадра), [confidence] — насколько
 * точку **видно**: не закрыта телом и не вышла за край кадра.
 *
 * ⚠️ Это именно «видимость», а не «уверенность вообще». Модель отдаёт два числа,
 * и второе («точка есть в кадре») в замерах 2026-08-16 держалось около единицы даже
 * там, где скелет был откровенной выдумкой. Годным признаком оказалось только первое.
 */
data class PosePoint(
    val x: Float,
    val y: Float,
    val confidence: Float,
    /**
     * Глубина: насколько точка дальше от камеры, чем таз. Меньше — ближе.
     *
     * В счёте не участвует: угол в локте считается по плоской проекции и в глубине
     * не нуждается. Нужна экрану — чтобы фигура выглядела объёмной, а не плоской:
     * по ней решается, какая рука ближе и что чем перекрывается.
     */
    val z: Float = 0f,
)

/** Три точки руки — этого достаточно, чтобы считать отжимания по углу в локте. */
data class ArmPose(
    val shoulder: PosePoint,
    val elbow: PosePoint,
    val wrist: PosePoint,
)

/**
 * Обе руки с одного кадра, как их отдала модель.
 *
 * Обе, а не одна: при съёмке сбоку дальняя рука закрыта телом, и какая именно
 * дальняя — зависит от того, с какой стороны лежит телефон. Выбирать сторону
 * руками владелец не должен, поэтому выбирает счётчик, каждый кадр заново.
 */
data class PoseFrame(
    /**
     * Все точки скелета в том порядке, в каком их нумерует модель ([PoseLandmarks]).
     *
     * Целиком, а не выборка: счёту нужны руки и таз, а человеку на экране — вся
     * фигура. Двенадцать точек без головы и ступней читались как россыпь, по
     * которой не понять, увидела камера человека или выдумала.
     */
    val points: List<PosePoint>,
    /**
     * Ширина кадра, делённая на высоту.
     *
     * Точки нормированы по этому кадру, и без его пропорций их нельзя положить
     * поверх картинки на экране — скелет разъедется с телом.
     */
    val aspect: Float = 1f,
) {
    operator fun get(index: Int): PosePoint = points.getOrElse(index) { PoseLandmarks.MISSING }

    val left: ArmPose
        get() = ArmPose(
            this[PoseLandmarks.LEFT_SHOULDER],
            this[PoseLandmarks.LEFT_ELBOW],
            this[PoseLandmarks.LEFT_WRIST],
        )

    val right: ArmPose
        get() = ArmPose(
            this[PoseLandmarks.RIGHT_SHOULDER],
            this[PoseLandmarks.RIGHT_ELBOW],
            this[PoseLandmarks.RIGHT_WRIST],
        )

    /**
     * Таз. Углы по нему не считаются — он нужен, чтобы понять, поместился ли
     * человек в кадр: по одним рукам «слишком близко» от «сняли только руки»
     * не отличить.
     */
    val leftHip: PosePoint get() = this[PoseLandmarks.LEFT_HIP]
    val rightHip: PosePoint get() = this[PoseLandmarks.RIGHT_HIP]

    /**
     * Точки, по которым судим о кадре и считаем повторы.
     *
     * Ног и головы здесь нет намеренно: при отжиманиях ступни часто уходят за край
     * кадра, и если считать это поводом для «слишком близко», жалоба не смолкнет.
     */
    val corePoints: List<PosePoint>
        get() = listOf(
            left.shoulder, left.elbow, left.wrist,
            right.shoulder, right.elbow, right.wrist,
            leftHip, rightHip,
        )

    companion object {
        /**
         * Собрать кадр из отдельных точек. Всё, что не назвали, считается невидимым.
         *
         * Нужно тестам: перечислять тридцать три точки ради проверки одного угла
         * значило бы прятать смысл теста за списком нулей.
         */
        fun of(vararg named: Pair<Int, PosePoint>, aspect: Float = 1f): PoseFrame {
            val points = MutableList(PoseLandmarks.COUNT) { PoseLandmarks.MISSING }
            named.forEach { (index, point) -> points[index] = point }
            return PoseFrame(points, aspect)
        }
    }
}

/**
 * Нумерация точек модели Pose Landmarker и скелет из них.
 *
 * Порядок задан моделью, менять его нельзя — по этим номерам приходят данные.
 */
object PoseLandmarks {

    const val COUNT = 33

    /** Точка, которой в кадре нет. Видимость нулевая, поэтому её не нарисуют. */
    val MISSING = PosePoint(0f, 0f, 0f)

    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HAND = 19
    const val RIGHT_HAND = 20
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_FOOT = 31
    const val RIGHT_FOOT = 32

    /**
     * Кости: пары точек, соединяемые линией.
     *
     * Живут в ядре, а не в экране, потому что это анатомия, а не оформление.
     */
    val BONES: List<Pair<Int, Int>> = listOf(
        NOSE to LEFT_EAR, NOSE to RIGHT_EAR,
        LEFT_EAR to LEFT_SHOULDER, RIGHT_EAR to RIGHT_SHOULDER,
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST, LEFT_WRIST to LEFT_HAND,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST, RIGHT_WRIST to RIGHT_HAND,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_FOOT,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_FOOT,
    )
}

/**
 * Сколько отжиманий требуется, чтобы будильник выключился.
 *
 * Границы 10…40 заданы владельцем (2026-08-16). Нижняя не случайна: меньше десяти
 * — это уже не испытание, а формальность, которую делают не просыпаясь.
 */
data class PushupSettings(
    val count: Int,
    /** Чем рисовать себя поверх кадра. */
    val overlay: PushupOverlay = PushupOverlay.FIGURE,
    /** Какой моделью распознавать позу. */
    val model: PoseModel = PoseModel.FULL,
) {
    companion object {
        const val MIN_COUNT = 10
        const val MAX_COUNT = 40
        val DEFAULT = PushupSettings(count = MIN_COUNT)
    }
}

/**
 * Модель распознавания позы.
 *
 * Все три лежат в приложении, переключаются в настройках отжиманий
 * (владелец, 2026-08-18) — чтобы сравнить на живом телефоне, а не гадать.
 *
 * ⚠️ Тяжёлая модель ставит точки точнее, но выдаёт **меньше кадров в секунду**, а
 * для счёта повторов важнее именно частота: отжимание занимает секунды полторы, и
 * при трёх кадрах в секунду нижняя точка может не попасть ни в один кадр. Точность
 * расстановки точек тут выигрывает меньше, чем проигрывает пропущенный повтор.
 */
enum class PoseModel {
    /** Самая быстрая и самая грубая. */
    LITE,

    /** Середина. Значение по умолчанию. */
    FULL,

    /** Самая точная и самая медленная. */
    HEAVY,
}

/**
 * Как показывать себя поверх картинки с камеры.
 *
 * Два варианта, переключаются в настройках отжиманий (владелец, 2026-08-17): один
 * человек узнаёт себя по фигуре, другому она мешает смотреть на самого себя.
 */
enum class PushupOverlay {
    /** Объёмный человечек из блоков. */
    FIGURE,

    /** Только точки, без линий между ними. */
    DOTS,
}

/** В какой фазе движения человек сейчас. */
enum class PushupPhase { UNKNOWN, UP, DOWN }

data class PushupState(
    val phase: PushupPhase,
    val reps: Int,
    /** Когда в последний раз засчитали повтор — чтобы отсеять дребезг. */
    val lastRepAtMillis: Long,
    /**
     * Самый острый угол с прошлого засчитанного повтора.
     *
     * Нужен ровно для одного: сказать «опускайся ниже». Без этого недожатое
     * отжимание молча не считается, и человек не понимает, почему счётчик стоит.
     */
    val deepestAngle: Float = NO_ANGLE,
    /**
     * За какую фазу голосуют последние кадры и сколько их подряд.
     *
     * Фаза меняется не с первого кадра за порогом, а только когда за неё
     * проголосовали [PushupCounter.PHASE_FRAMES] кадров подряд. Один скачок
     * распознавания больше не переводит фазу — а именно так и набегали ложные
     * повторы в первые секунды (владелец, 2026-08-18: «сразу 3 засчитало»).
     */
    val votedPhase: PushupPhase = PushupPhase.UNKNOWN,
    val votes: Int = 0,
) {
    companion object {
        /** Углов ещё не было. Не 0f: ноль — это полностью сложенная рука. */
        const val NO_ANGLE = Float.MAX_VALUE

        val START = PushupState(PushupPhase.UNKNOWN, reps = 0, lastRepAtMillis = 0L)
    }
}

/** Чем кончился подъём из нижней точки. */
enum class RepOutcome {
    /** Ничего не произошло: движение продолжается. */
    NONE,

    /** Повтор засчитан. */
    COUNTED,

    /** Поднялся слишком быстро после прошлого повтора — так не бывает. */
    TOO_SOON,

    /** Опустился, но не до конца: повтор не засчитан. */
    NOT_LOW_ENOUGH,
}

/** Новое состояние счётчика и то, что случилось на этом кадре. */
data class PushupTick(val state: PushupState, val outcome: RepOutcome)

/**
 * Счётчик отжиманий по углу в локте.
 *
 * Почему именно угол, а не высота плеч: угол не зависит ни от расстояния до камеры,
 * ни от того, под каким углом лежит телефон, ни от роста. Высота плеч зависит от
 * всего этого сразу и требует калибровки на каждый заход.
 *
 * Два порога, а не один ([DOWN_ANGLE] и [UP_ANGLE]) — это гистерезис. С одним порогом
 * дрожание точек вокруг него насчитало бы десяток повторов за секунду.
 *
 * Повтор засчитывается на переходе **вниз → вверх**: то есть за опускание с
 * последующим подъёмом, а не за любое движение.
 */
object PushupCounter {

    /**
     * Ниже этой видимости точка считается мусором, рука не учитывается.
     *
     * Порог снят с данных 2026-08-16: прогон модели по 16 фото и 50-секундному видео
     * владельца показал чистое разделение. Там, где скелет на глаз верный, видимость
     * держится 0.6…1.0; там, где модель дорисовывает закрытую телом руку, — ниже 0.5.
     */
    const val MIN_CONFIDENCE = 0.6f

    /** Локоть согнут — человек внизу. */
    const val DOWN_ANGLE = 110f

    /** Локоть выпрямлен — человек вверху. */
    const val UP_ANGLE = 150f

    /**
     * Насколько руки могут расходиться в показаниях.
     *
     * В отжимании обе руки гнутся вместе — это физика, а не пожелание. Расхождение
     * в полсотни градусов означает, что одну из рук модель выдумала, и такому кадру
     * верить нельзя целиком, даже если по видимости он прошёл.
     */
    const val MAX_ARM_DISAGREEMENT = 30f

    /**
     * Быстрее этого повторы не считаем.
     *
     * Отжимание физически не делается за секунду, а вот скачок распознавания —
     * запросто. Это защита от рывков модели, а не от жульничества.
     *
     * Число поднято с 700 мс после прогона по видео 2026-08-16: там счётчик выдал
     * пару «повторов» в 0.9 с друг от друга, пока камеру просто переносили.
     * Ошибки здесь несимметричны: пропущенный повтор стоит одного лишнего
     * отжимания, а лишний засчитанный отпускает с экрана раньше времени.
     */
    const val MIN_REP_INTERVAL_MS = 1_200L

    /**
     * Угол в локте в градусах: плечо — локоть — запястье.
     *
     * @return null, если хотя бы одна точка ненадёжна или вырождена.
     */
    fun elbowAngle(arm: ArmPose): Float? {
        if (arm.shoulder.confidence < MIN_CONFIDENCE) return null
        if (arm.elbow.confidence < MIN_CONFIDENCE) return null
        if (arm.wrist.confidence < MIN_CONFIDENCE) return null

        val ax = arm.shoulder.x - arm.elbow.x
        val ay = arm.shoulder.y - arm.elbow.y
        val bx = arm.wrist.x - arm.elbow.x
        val by = arm.wrist.y - arm.elbow.y

        val lenA = sqrt(ax * ax + ay * ay)
        val lenB = sqrt(bx * bx + by * by)
        if (lenA < 1e-4f || lenB < 1e-4f) return null

        val cos = ((ax * bx + ay * by) / (lenA * lenB)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /**
     * Сколько кадров подряд должны сказать одно и то же, чтобы фаза сменилась.
     *
     * Один кадр — это мнение, два подряд — уже событие. Скачок распознавания
     * длиной в кадр после этого не переводит фазу и не рождает повтор.
     */
    const val PHASE_FRAMES = 2

    /**
     * Насколько ниже верхнего порога нужно опуститься, чтобы это считалось попыткой.
     *
     * Мельче — это не «недожал», а дрожание руки на месте, и говорить про него
     * «опускайся ниже» значило бы придираться к неподвижному человеку.
     */
    const val ATTEMPT_MARGIN = 15f

    /**
     * Новый кадр.
     *
     * @param angle угол в локте, либо null если кадр не распознался — тогда состояние
     *              не меняется вовсе: пропущенный кадр не должен ни считать, ни сбрасывать.
     */
    fun next(state: PushupState, angle: Float?, nowMillis: Long): PushupTick {
        if (angle == null) return PushupTick(state, RepOutcome.NONE)

        val deepened = state.copy(deepestAngle = minOf(state.deepestAngle, angle))

        val vote = when {
            angle <= DOWN_ANGLE -> PushupPhase.DOWN
            angle >= UP_ANGLE -> PushupPhase.UP
            // Между порогами движение идёт, и голосовать не за что.
            else -> return PushupTick(deepened.copy(votedPhase = PushupPhase.UNKNOWN, votes = 0), RepOutcome.NONE)
        }

        val votes = if (vote == deepened.votedPhase) deepened.votes + 1 else 1
        val voting = deepened.copy(votedPhase = vote, votes = votes)
        // Фаза меняется только после нескольких одинаковых кадров подряд.
        if (votes < PHASE_FRAMES) return PushupTick(voting, RepOutcome.NONE)

        if (vote == PushupPhase.DOWN) return PushupTick(voting.copy(phase = PushupPhase.DOWN), RepOutcome.NONE)

        val rising = voting.copy(phase = PushupPhase.UP)
        val wasDown = state.phase == PushupPhase.DOWN
        val tooSoon = state.lastRepAtMillis != 0L &&
            nowMillis - state.lastRepAtMillis < MIN_REP_INTERVAL_MS

        return when {
            wasDown && tooSoon -> PushupTick(rising, RepOutcome.TOO_SOON)

            // Повтор засчитан — глубину считаем заново, со следующего.
            wasDown -> PushupTick(
                rising.copy(
                    reps = state.reps + 1,
                    lastRepAtMillis = nowMillis,
                    deepestAngle = PushupState.NO_ANGLE,
                ),
                RepOutcome.COUNTED,
            )

            // ⚠️ Недожатая попытка фазу не меняет вовсе: до нижнего порога человек
            // не дошёл, и всё это время он формально «вверху». Поэтому проверяем её
            // по накопленной глубине, а не по смене фазы — иначе она не находится.
            // Глубину сбрасываем, чтобы сказать об этом один раз, а не каждый кадр.
            state.deepestAngle < UP_ANGLE - ATTEMPT_MARGIN ->
                PushupTick(rising.copy(deepestAngle = PushupState.NO_ANGLE), RepOutcome.NOT_LOW_ENOUGH)

            else -> PushupTick(rising, RepOutcome.NONE)
        }
    }

    /**
     * Похоже ли на то, что человек вообще перед камерой и в правильном ракурсе.
     *
     * Ракурс проверяем по разбросу точек: если плечо, локоть и запястье схлопнулись
     * почти в одну точку, значит камера смотрит вдоль руки — с такого ракурса угол
     * считать нельзя. Именно это происходит, когда телефон стоит со стороны ног.
     */
    fun poseUsable(arm: ArmPose): Boolean {
        if (elbowAngle(arm) == null) return false
        return spread(arm) >= MIN_SPREAD
    }

    /** Насколько рука растянута по кадру. Мера годности ракурса, не длины руки. */
    fun spread(arm: ArmPose): Float = maxOf(
        abs(arm.shoulder.x - arm.wrist.x),
        abs(arm.shoulder.y - arm.wrist.y),
    )

    /** Минимальный разброс точек руки по кадру, ниже которого ракурс негоден. */
    const val MIN_SPREAD = 0.08f

    /**
     * Угол, по которому считаем повтор, из целого кадра.
     *
     * Обе руки сразу не требуем: при съёмке сбоку дальняя рука закрыта телом
     * честно, и отказываться из-за неё значило бы отказаться от единственного
     * ракурса, который вообще работает.
     *
     * Но если видно обе — они обязаны сойтись: разошлись, значит одна из них
     * дорисована, и кадр не годится целиком.
     */
    fun frameAngle(frame: PoseFrame): Float? {
        val left = frame.left.takeIf { poseUsable(it) }?.let(::elbowAngle)
        val right = frame.right.takeIf { poseUsable(it) }?.let(::elbowAngle)
        return when {
            left != null && right != null ->
                if (abs(left - right) > MAX_ARM_DISAGREEMENT) null else (left + right) / 2f

            else -> left ?: right
        }
    }
}

/**
 * Источник поз с камеры.
 *
 * Порт: `:core` не знает ни про камеру, ни про модель распознавания — ему нужны
 * только точки. Адаптер живёт в `:platform`.
 */
interface PoseSource {
    /**
     * Начать съёмку. Колбэк зовётся на каждый разобранный кадр;
     * `null` — человека в кадре не нашли вовсе.
     */
    fun start(onFrame: (PoseFrame?) -> Unit)

    fun stop()
}
