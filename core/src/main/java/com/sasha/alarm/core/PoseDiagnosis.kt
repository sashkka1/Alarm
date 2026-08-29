package com.sasha.alarm.core

import kotlin.math.abs

/**
 * Что именно не так с тем, что видит камера.
 *
 * Порядок объявления — это порядок важности: разбор возвращает **одну** причину,
 * самую первую из подошедших. Список претензий в шесть утра нечитаем, а чинить их
 * всё равно приходится по одной: отодвинул телефон — половина ушла сама.
 */
enum class PoseProblem {
    /** Модель не нашла человека вовсе. */
    NO_POSE,

    /** Часть тела за краем кадра. На практике это всегда «телефон слишком близко». */
    TOO_CLOSE,

    /** Фигура занимает крохотную часть кадра — точки станут шумом. */
    TOO_FAR,

    /** Плечи не видно: без них угол в локте не построить. */
    SHOULDERS_HIDDEN,

    /** Локти не видно — а угол считается именно в них. */
    ELBOWS_HIDDEN,

    /** Кисти не видно. */
    WRISTS_HIDDEN,

    /**
     * Руки дают разные углы, значит одну модель дорисовала.
     * Лечится ракурсом: сбоку видно хотя бы одну руку честно.
     */
    ARMS_DISAGREE,

    /** Опускается, но не до конца — повтор не засчитается. */
    NOT_LOW_ENOUGH,

    /** Всё в порядке, можно считать. */
    NONE,
}

/**
 * Разбор кадра: почему счётчик не считает.
 *
 * Живёт в ядре и ничего не знает ни про камеру, ни про экран — на вход только точки.
 * Все пороги взяты из прогона по материалам владельца 2026-08-16, см. CLAUDE.md.
 */
object PoseDiagnosis {

    /**
     * Насколько далеко за край кадра точка может уйти, прежде чем считать,
     * что человек не помещается.
     *
     * Не ноль: модель ставит точки чуть за границу и на совершенно нормальных
     * кадрах — например, кисть у самого края.
     */
    const val FRAME_MARGIN = 0.05f

    /**
     * Сколько точек должны выйти за край, чтобы это было «не помещаешься»,
     * а не «одна рука зашла за границу».
     */
    const val OUT_OF_FRAME_COUNT = 2

    /**
     * Минимальная высота корпуса (плечо — таз) в долях кадра.
     *
     * ⚠️ Порог не проверен на данных: в материалах владельца слишком далеко он
     * не отходил ни разу. Поставлен заведомо мягким, чтобы не мешать зря.
     */
    const val MIN_TORSO = 0.10f

    /**
     * Что показать человеку.
     *
     * @param frame кадр, либо null — модель не нашла позу.
     * @param state текущее состояние счётчика: из него берётся глубина последнего
     *              захода, чтобы сказать «опускайся ниже».
     */
    fun of(frame: PoseFrame?, state: PushupState): PoseProblem {
        if (frame == null) return PoseProblem.NO_POSE

        // ⚠️ Сначала — можем ли мы вообще считать. Если угол в локте считается,
        // претензий нет **никаких**, сколько бы тела ни осталось за кадром
        // (владелец, 2026-08-17): комната маленькая, поставить телефон далеко
        // негде, и головы с ногами в кадре может не быть никогда. Для счёта они
        // и не нужны: отжимание видно по одной согнувшейся руке.
        //
        // Прежде проверки шли в обратном порядке — сначала кадрирование, потом
        // счёт, — и приложение ругалось на обрезанное тело, прекрасно при этом
        // всё считая.
        if (PushupCounter.frameDepth(frame) != null) return depthAdvice(state)

        // Считать не выходит. Разбираемся почему — **сначала причины, потом
        // следствия**. «Не вижу плечи» и «слишком близко» обычно случаются вместе,
        // но двигать надо телефон, а не плечи, поэтому первым говорится расстояние.
        if (armsDisagree(frame)) return PoseProblem.ARMS_DISAGREE

        val outside = frame.corePoints.count { point ->
            point.x < -FRAME_MARGIN || point.x > 1f + FRAME_MARGIN ||
                point.y < -FRAME_MARGIN || point.y > 1f + FRAME_MARGIN
        }
        if (outside >= OUT_OF_FRAME_COUNT) return PoseProblem.TOO_CLOSE

        // «Слишком далеко» меряется по корпусу, поэтому спрашивать про него можно,
        // только когда таз и плечи видно: иначе мерить нечем и вывод был бы выдумкой.
        val torsoVisible = !hidden(frame.left.shoulder, frame.right.shoulder) &&
            !hidden(frame.leftHip, frame.rightHip)
        if (torsoVisible && torsoHeight(frame) < MIN_TORSO) return PoseProblem.TOO_FAR

        if (hidden(frame.left.shoulder, frame.right.shoulder)) return PoseProblem.SHOULDERS_HIDDEN
        if (hidden(frame.left.elbow, frame.right.elbow)) return PoseProblem.ELBOWS_HIDDEN
        if (hidden(frame.left.wrist, frame.right.wrist)) return PoseProblem.WRISTS_HIDDEN

        // Точки есть, а угла нет — значит камера смотрит вдоль руки.
        return PoseProblem.ARMS_DISAGREE
    }

    /**
     * Единственная претензия, которую стоит высказывать во время нормального счёта.
     *
     * Про глубину говорим только наверху: посреди движения это была бы придирка
     * к каждому кадру спуска.
     */
    private fun depthAdvice(state: PushupState): PoseProblem {
        val deepest = state.deepestDrop
        val shallow = state.phase == PushupPhase.UP &&
            deepest != PushupState.NO_DEPTH &&
            deepest > PushupCounter.ATTEMPT_DEPTH &&
            deepest < PushupCounter.DOWN_DEPTH
        return if (shallow) PoseProblem.NOT_LOW_ENOUGH else PoseProblem.NONE
    }

    /**
     * Сколько человек должен двигаться впустую, прежде чем сказать про телефон.
     *
     * Десять секунд — это заведомо больше одного отжимания и заведомо меньше, чем
     * нужно, чтобы бросить попытки.
     */
    const val STUCK_MS = 10_000L

    /**
     * Какой размах признака глубины считается «человек явно двигается».
     *
     * Снято с видео владельца 2026-08-25: при съёмке с пола признак ходил в пределах
     * 0.35, при этом ни один повтор не засчитывался. Лежащий неподвижно даёт размах
     * около нуля, поэтому отдыхающего эта проверка не трогает.
     */
    const val MOVING_SPAN = 0.25f

    /**
     * Телефон стоит слишком низко: человек двигается, а повторы не идут.
     *
     * ⚠️ Это не «он плохо отжимается», а именно про ракурс. С телефона, лежащего на
     * полу, подъём и спуск в кадре сжимаются почти в ноль — на видео владельца
     * (2026-08-25) счётчик не брал ни одного повтора из пяти, причём **скелет был
     * правильный**. Ни одна модель и ни один порог этого не чинят: чинится только
     * высотой, на которой стоит телефон. Значит единственное честное поведение —
     * сказать об этом вслух, а не молчать до дедлайна.
     *
     * @param spanSeen размах глубины за последнее окно наблюдения
     * @param sinceProgressMillis сколько прошло с последнего засчитанного повтора
     */
    fun cameraTooLow(spanSeen: Float, sinceProgressMillis: Long): Boolean =
        sinceProgressMillis >= STUCK_MS && spanSeen >= MOVING_SPAN

    /** Высота корпуса: от плеча до таза, по той стороне, что видно лучше. */
    fun torsoHeight(frame: PoseFrame): Float {
        val left = abs(frame.left.shoulder.y - frame.leftHip.y)
        val right = abs(frame.right.shoulder.y - frame.rightHip.y)
        return maxOf(left, right)
    }

    private fun hidden(a: PosePoint, b: PosePoint): Boolean =
        a.confidence < PushupCounter.MIN_CONFIDENCE && b.confidence < PushupCounter.MIN_CONFIDENCE

    /** Обе руки видно, но они говорят разное — кадру верить нельзя. */
    private fun armsDisagree(frame: PoseFrame): Boolean {
        val left = frame.left.takeIf(PushupCounter::poseUsable)?.let(PushupCounter::elbowAngle)
        val right = frame.right.takeIf(PushupCounter::poseUsable)?.let(PushupCounter::elbowAngle)
        if (left == null || right == null) return false
        return abs(left - right) > PushupCounter.MAX_ARM_DISAGREEMENT
    }
}
