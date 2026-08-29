package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseDiagnosisTest {

    private fun point(x: Float, y: Float, confidence: Float = 0.9f) = PosePoint(x, y, confidence)

    /** Съёмка сбоку с расстояния: человек в кадре целиком, руки выпрямлены. */
    private val goodFrame = frame()

    private val up = PushupState(PushupPhase.UP, reps = 0, lastRepAtMillis = 0L)

    /**
     * Годный кадр с точечными правками.
     *
     * Именованные точки перечислены здесь один раз: тесту важны две-три из них,
     * а не весь список из тридцати трёх.
     */
    private fun frame(vararg changes: Pair<Int, PosePoint>): PoseFrame = PoseFrame.of(
        PoseLandmarks.LEFT_SHOULDER to point(0.30f, 0.35f),
        PoseLandmarks.LEFT_ELBOW to point(0.40f, 0.50f),
        PoseLandmarks.LEFT_WRIST to point(0.50f, 0.65f),
        PoseLandmarks.RIGHT_SHOULDER to point(0.32f, 0.36f),
        PoseLandmarks.RIGHT_ELBOW to point(0.42f, 0.51f),
        PoseLandmarks.RIGHT_WRIST to point(0.52f, 0.66f),
        PoseLandmarks.LEFT_HIP to point(0.20f, 0.60f),
        PoseLandmarks.RIGHT_HIP to point(0.22f, 0.61f),
        *changes,
    )

    /** Та же точка, но модель ей не верит. */
    private fun dim(index: Int, frame: PoseFrame = goodFrame): Pair<Int, PosePoint> =
        index to frame[index].copy(confidence = 0.1f)

    @Test
    fun `хороший кадр не вызывает претензий`() {
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(goodFrame, PushupState.START))
    }

    @Test
    fun `позы нет — так и говорим`() {
        assertEquals(PoseProblem.NO_POSE, PoseDiagnosis.of(null, PushupState.START))
    }

    @Test
    fun `видно одну руку — считаем и молчим, даже если тело обрезано`() {
        // Комната маленькая, телефон поставить далеко негде: головы, ног и таза
        // в кадре может не быть никогда (владелец, 2026-08-17). Пока сгибается
        // рука, отжимание видно — и придираться не к чему.
        val cropped = frame(
            PoseLandmarks.LEFT_HIP to point(-0.30f, 0.60f),
            PoseLandmarks.RIGHT_HIP to point(-0.35f, 0.61f),
            dim(PoseLandmarks.RIGHT_SHOULDER),
            dim(PoseLandmarks.RIGHT_ELBOW),
            dim(PoseLandmarks.RIGHT_WRIST),
        )
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(cropped, PushupState.START))
    }

    @Test
    fun `точки за краем кадра читаются как «слишком близко» — но только если считать нечем`() {
        // Ровно так выглядит телефон, лежащий вплотную: тело не поместилось, и
        // рук в кадре тоже нет — вот тогда и говорим «отодвинь».
        // Точки, вылетевшие за кадр, модель отдаёт с низкой видимостью — так и тут.
        val cropped = PoseFrame.of(
            PoseLandmarks.LEFT_SHOULDER to point(-0.30f, 0.35f, confidence = 0.1f),
            PoseLandmarks.LEFT_ELBOW to point(-0.40f, 0.50f, confidence = 0.1f),
            PoseLandmarks.LEFT_WRIST to point(-0.50f, 0.65f, confidence = 0.1f),
            PoseLandmarks.LEFT_HIP to point(-0.30f, 0.60f, confidence = 0.1f),
            PoseLandmarks.RIGHT_HIP to point(-0.35f, 0.61f, confidence = 0.1f),
        )
        assertEquals(PoseProblem.TOO_CLOSE, PoseDiagnosis.of(cropped, PushupState.START))
    }

    @Test
    fun `мелкая фигура читается как «слишком далеко»`() {
        // Считать нечем — рук не видно, — зато плечи с тазом видно, и по ним
        // понятно, что человек далеко.
        val far = frame(
            PoseLandmarks.LEFT_HIP to point(0.20f, 0.37f),
            PoseLandmarks.RIGHT_HIP to point(0.22f, 0.38f),
            dim(PoseLandmarks.LEFT_WRIST),
            dim(PoseLandmarks.RIGHT_WRIST),
        )
        assertEquals(PoseProblem.TOO_FAR, PoseDiagnosis.of(far, PushupState.START))
    }

    @Test
    fun `далеко не жалуемся, если корпус мерить нечем`() {
        // Таза в кадре нет — значит и «слишком далеко» сказать не из чего.
        val noHips = frame(
            dim(PoseLandmarks.LEFT_HIP),
            dim(PoseLandmarks.RIGHT_HIP),
            dim(PoseLandmarks.LEFT_WRIST),
            dim(PoseLandmarks.RIGHT_WRIST),
        )
        assertEquals(PoseProblem.WRISTS_HIDDEN, PoseDiagnosis.of(noHips, PushupState.START))
    }

    @Test
    fun `не видно обоих плеч — жалуемся на плечи`() {
        val blind = frame(dim(PoseLandmarks.LEFT_SHOULDER), dim(PoseLandmarks.RIGHT_SHOULDER))
        assertEquals(PoseProblem.SHOULDERS_HIDDEN, PoseDiagnosis.of(blind, PushupState.START))
    }

    @Test
    fun `не видно одного плеча — молчим`() {
        // Съёмка сбоку: дальняя рука закрыта телом честно, считать можно по ближней.
        val half = frame(dim(PoseLandmarks.LEFT_SHOULDER))
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(half, PushupState.START))
    }

    @Test
    fun `не видно обоих локтей — жалуемся на локти`() {
        val blind = frame(dim(PoseLandmarks.LEFT_ELBOW), dim(PoseLandmarks.RIGHT_ELBOW))
        assertEquals(PoseProblem.ELBOWS_HIDDEN, PoseDiagnosis.of(blind, PushupState.START))
    }

    @Test
    fun `руки говорят разное — жалуемся на ракурс`() {
        val bent = frame(
            PoseLandmarks.RIGHT_SHOULDER to point(0.32f, 0.20f),
            PoseLandmarks.RIGHT_ELBOW to point(0.32f, 0.50f),
            PoseLandmarks.RIGHT_WRIST to point(0.62f, 0.50f),
        )
        assertEquals(PoseProblem.ARMS_DISAGREE, PoseDiagnosis.of(bent, PushupState.START))
    }

    @Test
    fun `недожатое отжимание объясняется словами`() {
        // Плечо не дошло до локтя самую малость: повтор не засчитается, и человек
        // обязан узнать почему.
        val shallow = up.copy(deepestDrop = -0.05f)
        assertEquals(PoseProblem.NOT_LOW_ENOUGH, PoseDiagnosis.of(goodFrame, shallow))
    }

    @Test
    fun `дожатое отжимание претензий не вызывает`() {
        val deep = up.copy(deepestDrop = 0.05f)
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(goodFrame, deep))
    }

    @Test
    fun `движение в верхней половине придиркой не считается`() {
        // Раньше на такие кадры и сыпалось бесконечное «ниже» (владелец, 2026-08-25):
        // человек просто шевелится наверху, опускаться он ещё и не начинал.
        val idle = up.copy(deepestDrop = -0.6f)
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(goodFrame, idle))
    }

    @Test
    fun `на спуске о глубине не напоминаем`() {
        // Посреди движения такая придирка сыпалась бы на каждый кадр.
        val going = PushupState(PushupPhase.DOWN, reps = 0, lastRepAtMillis = 0L, deepestDrop = -0.05f)
        assertEquals(PoseProblem.NONE, PoseDiagnosis.of(goodFrame, going))
    }

    @Test
    fun `двигается, а повторов нет — говорим про высоту телефона`() {
        // Ровно случай съёмки с пола: размах глубины большой, счёт стоит.
        assertTrue(PoseDiagnosis.cameraTooLow(spanSeen = 0.35f, sinceProgressMillis = 12_000L))
    }

    @Test
    fun `лежащего неподвижно про телефон не спрашиваем`() {
        // Отдых между подходами не повод переставлять телефон: движения нет вовсе.
        assertFalse(PoseDiagnosis.cameraTooLow(spanSeen = 0.02f, sinceProgressMillis = 30_000L))
    }

    @Test
    fun `пока повторы идут, про телефон молчим`() {
        assertFalse(PoseDiagnosis.cameraTooLow(spanSeen = 0.9f, sinceProgressMillis = 2_000L))
    }

    @Test
    fun `счётчик копит глубину и обнуляет её на засчитанном повторе`() {
        var state = PushupState.START
        fun feed(depth: Float, at: Long) {
            repeat(PushupCounter.PHASE_FRAMES) { state = PushupCounter.next(state, depth, at).state }
        }

        feed(-0.9f, 0L)
        feed(0.05f, 1_000L)
        assertEquals(0.05f, state.deepestDrop, 0.01f)

        feed(-0.9f, 2_000L)
        assertEquals(1, state.reps)
        assertEquals(PushupState.NO_DEPTH, state.deepestDrop, 0.01f)
    }
}
