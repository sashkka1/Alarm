package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushupCounterTest {

    private fun point(x: Float, y: Float, confidence: Float = 0.9f) = PosePoint(x, y, confidence)

    /** Рука вытянута в прямую линию — угол около 180°. */
    private val straightArm = ArmPose(
        shoulder = point(0.2f, 0.5f),
        elbow = point(0.5f, 0.5f),
        wrist = point(0.8f, 0.5f),
    )

    /** Рука согнута под прямым углом. */
    private val bentArm = ArmPose(
        shoulder = point(0.5f, 0.2f),
        elbow = point(0.5f, 0.5f),
        wrist = point(0.8f, 0.5f),
    )

    @Test
    fun `диапазон числа отжиманий — от десяти до сорока`() {
        assertEquals(10, PushupSettings.MIN_COUNT)
        assertEquals(40, PushupSettings.MAX_COUNT)
        // Число, сохранённое прежней сборкой ниже нового минимума, подтянется вверх
        // при чтении состояния — этим занимается coerceIn в AlarmStateStore.
        assertEquals(10, 3.coerceIn(PushupSettings.MIN_COUNT, PushupSettings.MAX_COUNT))
    }

    @Test
    fun `прямая рука даёт угол около ста восьмидесяти`() {
        val angle = PushupCounter.elbowAngle(straightArm)
        assertNotNull(angle)
        assertEquals(180f, angle!!, 1f)
    }

    @Test
    fun `согнутая под прямым углом рука даёт девяносто`() {
        val angle = PushupCounter.elbowAngle(bentArm)
        assertNotNull(angle)
        assertEquals(90f, angle!!, 1f)
    }

    @Test
    fun `ненадёжная точка отменяет весь кадр`() {
        val shaky = straightArm.copy(elbow = point(0.5f, 0.5f, confidence = 0.2f))
        assertNull(PushupCounter.elbowAngle(shaky))
    }

    @Test
    fun `вырожденная рука не даёт угла`() {
        val collapsed = ArmPose(point(0.5f, 0.5f), point(0.5f, 0.5f), point(0.5f, 0.5f))
        assertNull(PushupCounter.elbowAngle(collapsed))
    }

    /**
     * Прогнать углы через счётчик.
     *
     * Каждый угол подаётся [PushupCounter.PHASE_FRAMES] раз подряд: фаза меняется
     * только после нескольких одинаковых кадров, и тест должен играть по тем же
     * правилам, что и камера.
     */
    private fun run(
        vararg angles: Pair<Float?, Long>,
        from: PushupState = PushupState.START,
    ): Pair<PushupState, List<RepOutcome>> {
        var state = from
        val outcomes = mutableListOf<RepOutcome>()
        angles.forEach { (angle, at) ->
            repeat(PushupCounter.PHASE_FRAMES) {
                val tick = PushupCounter.next(state, angle, at)
                state = tick.state
                if (tick.outcome != RepOutcome.NONE) outcomes += tick.outcome
            }
        }
        return state to outcomes
    }

    @Test
    fun `полный цикл вниз-вверх даёт один повтор`() {
        val (state, outcomes) = run(170f to 0L, 90f to 1_000L, 170f to 2_000L)
        assertEquals(1, state.reps)
        assertEquals(listOf(RepOutcome.COUNTED), outcomes)
    }

    @Test
    fun `один скачок распознавания фазу не переводит`() {
        // Ровно это насчитывало три повтора в первые секунды: одиночные мусорные
        // кадры перекидывали фазу туда-обратно (владелец, 2026-08-18).
        var state = PushupState.START
        repeat(PushupCounter.PHASE_FRAMES) { state = PushupCounter.next(state, 170f, 0L).state }
        // Один-единственный кадр «внизу» — этого мало.
        state = PushupCounter.next(state, 60f, 100L).state
        assertEquals(PushupPhase.UP, state.phase)
        state = PushupCounter.next(state, 170f, 200L).state
        assertEquals(0, state.reps)
    }

    @Test
    fun `только опускание повтора не даёт`() {
        val (state, _) = run(170f to 0L, 90f to 1_000L)
        assertEquals(0, state.reps)
        assertEquals(PushupPhase.DOWN, state.phase)
    }

    @Test
    fun `дрожание вокруг верхнего порога не накручивает повторы`() {
        // Тот самый случай, ради которого нужен гистерезис: без нижнего порога
        // каждое колебание точки насчитало бы повтор.
        var state = PushupState.START
        state = PushupCounter.next(state, 170f, 0L).state
        repeat(20) { i -> state = PushupCounter.next(state, 149f + (i % 2) * 4f, 100L * i).state }
        assertEquals(0, state.reps)
    }

    @Test
    fun `слишком быстрый повтор отбрасывается и объясняется`() {
        val (first, _) = run(170f to 0L, 90f to 100L, 170f to 200L)
        assertEquals(1, first.reps)

        // Второй «повтор» через 100 мс — физически невозможен, значит рывок модели.
        val (state, outcomes) = run(90f to 250L, 170f to 300L, from = first)
        assertEquals(1, state.reps)
        assertEquals(listOf(RepOutcome.TOO_SOON), outcomes)
    }

    @Test
    fun `недожатое отжимание не считается и называется своим именем`() {
        // Опустился до 130° — ниже верхнего порога, но выше нижнего.
        val (state, outcomes) = run(170f to 0L, 130f to 1_000L, 170f to 2_000L)
        assertEquals(0, state.reps)
        assertEquals(listOf(RepOutcome.NOT_LOW_ENOUGH), outcomes)
    }

    @Test
    fun `лёгкое дрожание наверху попыткой не считается`() {
        // 160° — это не «недожал», а рука дрогнула. Придираться не за что.
        val (_, outcomes) = run(170f to 0L, 160f to 1_000L, 170f to 2_000L)
        assertEquals(emptyList<RepOutcome>(), outcomes)
    }

    @Test
    fun `десять честных отжиманий считаются как десять`() {
        var state = PushupState.START
        var now = 0L
        state = run(170f to now, from = state).first
        repeat(10) {
            now += 900L
            state = run(85f to now, from = state).first
            now += 900L
            state = run(172f to now, from = state).first
        }
        assertEquals(10, state.reps)
    }

    @Test
    fun `нераспознанный кадр не сбивает счёт`() {
        val (state, _) = run(170f to 0L, 90f to 1_000L, null to 1_500L, 170f to 2_000L)
        assertEquals(1, state.reps)
    }

    /** Кадр из двух рук. Таз для угла не нужен, но пусть будет как в жизни. */
    private fun frameOf(left: ArmPose, right: ArmPose) = PoseFrame.of(
        PoseLandmarks.LEFT_SHOULDER to left.shoulder,
        PoseLandmarks.LEFT_ELBOW to left.elbow,
        PoseLandmarks.LEFT_WRIST to left.wrist,
        PoseLandmarks.RIGHT_SHOULDER to right.shoulder,
        PoseLandmarks.RIGHT_ELBOW to right.elbow,
        PoseLandmarks.RIGHT_WRIST to right.wrist,
        PoseLandmarks.LEFT_HIP to point(0.20f, 0.60f),
        PoseLandmarks.RIGHT_HIP to point(0.22f, 0.61f),
    )

    @Test
    fun `видно обе руки и они сходятся — берём среднее`() {
        val angle = PushupCounter.frameAngle(frameOf(straightArm, straightArm))
        assertNotNull(angle)
        assertEquals(180f, angle!!, 1f)
    }

    @Test
    fun `руки разошлись — кадру не верим вовсе`() {
        // Так выглядит съёмка сбоку, когда модель дорисовала закрытую телом руку:
        // одна рука выпрямлена, другая якобы согнута пополам.
        assertNull(PushupCounter.frameAngle(frameOf(straightArm, bentArm)))
    }

    @Test
    fun `дальнюю руку не видно — считаем по ближней`() {
        val hidden = bentArm.copy(
            shoulder = bentArm.shoulder.copy(confidence = 0.1f),
            elbow = bentArm.elbow.copy(confidence = 0.1f),
            wrist = bentArm.wrist.copy(confidence = 0.1f),
        )
        val angle = PushupCounter.frameAngle(frameOf(straightArm, hidden))
        assertNotNull(angle)
        assertEquals(180f, angle!!, 1f)
    }

    @Test
    fun `не видно ни одной руки — угла нет`() {
        val blind = straightArm.copy(
            shoulder = straightArm.shoulder.copy(confidence = 0.1f),
            elbow = straightArm.elbow.copy(confidence = 0.1f),
            wrist = straightArm.wrist.copy(confidence = 0.1f),
        )
        assertNull(PushupCounter.frameAngle(frameOf(blind, blind)))
    }

    @Test
    fun `ракурс вдоль руки признаётся негодным`() {
        // Так выглядит съёмка со стороны ног: точки схлопнулись почти в одну.
        val alongArm = ArmPose(
            shoulder = point(0.50f, 0.50f),
            elbow = point(0.51f, 0.52f),
            wrist = point(0.52f, 0.54f),
        )
        assertFalse(PushupCounter.poseUsable(alongArm))
        assertTrue(PushupCounter.poseUsable(straightArm))
    }
}
