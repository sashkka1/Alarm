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

    /** Верхняя точка: рука выпрямлена, плечо почти на длину кости выше локтя. */
    private val top = -0.90f

    /** Нижняя точка: плечо провалилось под локоть. */
    private val bottom = 0.05f

    /**
     * Прогнать признаки глубины через счётчик.
     *
     * Каждое значение подаётся [PushupCounter.PHASE_FRAMES] раз подряд: фаза меняется
     * только после нескольких одинаковых кадров, и тест должен играть по тем же
     * правилам, что и камера.
     */
    private fun run(
        vararg depths: Pair<Float?, Long>,
        from: PushupState = PushupState.START,
    ): Pair<PushupState, List<RepOutcome>> {
        var state = from
        val outcomes = mutableListOf<RepOutcome>()
        depths.forEach { (depth, at) ->
            repeat(PushupCounter.PHASE_FRAMES) {
                val tick = PushupCounter.next(state, depth, at)
                state = tick.state
                if (tick.outcome != RepOutcome.NONE) outcomes += tick.outcome
            }
        }
        return state to outcomes
    }

    @Test
    fun `полный цикл вниз-вверх даёт один повтор`() {
        val (state, outcomes) = run(top to 0L, bottom to 1_000L, top to 2_000L)
        assertEquals(1, state.reps)
        assertEquals(listOf(RepOutcome.COUNTED), outcomes)
    }

    @Test
    fun `плечо ровно на уровне локтя уже считается низом`() {
        // Владелец сформулировал критерий так: лопатка опустилась ниже локтя —
        // засчитываем. Ровно ноль — это граница, и она входит в «низ».
        val (state, _) = run(top to 0L, 0f to 1_000L, top to 2_000L)
        assertEquals(1, state.reps)
    }

    @Test
    fun `один скачок распознавания фазу не переводит`() {
        // Ровно это насчитывало три повтора в первые секунды: одиночные мусорные
        // кадры перекидывали фазу туда-обратно (владелец, 2026-08-18).
        var state = PushupState.START
        repeat(PushupCounter.PHASE_FRAMES) { state = PushupCounter.next(state, top, 0L).state }
        // Один-единственный кадр «внизу» — этого мало.
        state = PushupCounter.next(state, bottom, 100L).state
        assertEquals(PushupPhase.UP, state.phase)
        state = PushupCounter.next(state, top, 200L).state
        assertEquals(0, state.reps)
    }

    @Test
    fun `только опускание повтора не даёт`() {
        val (state, _) = run(top to 0L, bottom to 1_000L)
        assertEquals(0, state.reps)
        assertEquals(PushupPhase.DOWN, state.phase)
    }

    @Test
    fun `дрожание вокруг верхнего порога не накручивает повторы`() {
        // Тот самый случай, ради которого нужен гистерезис: без нижнего порога
        // каждое колебание точки насчитало бы повтор.
        var state = PushupState.START
        state = PushupCounter.next(state, top, 0L).state
        repeat(20) { i -> state = PushupCounter.next(state, -0.16f + (i % 2) * 0.02f, 100L * i).state }
        assertEquals(0, state.reps)
    }

    @Test
    fun `слишком быстрый повтор отбрасывается и объясняется`() {
        val (first, _) = run(top to 0L, bottom to 100L, top to 200L)
        assertEquals(1, first.reps)

        // Второй «повтор» через 100 мс — физически невозможен, значит рывок модели.
        val (state, outcomes) = run(bottom to 250L, top to 300L, from = first)
        assertEquals(1, state.reps)
        assertEquals(listOf(RepOutcome.TOO_SOON), outcomes)
    }

    @Test
    fun `недожатое отжимание не считается и называется своим именем`() {
        // Опустился до −0.05: почти дошёл до локтя, но плечо под него не ушло.
        val (state, outcomes) = run(top to 0L, -0.05f to 1_000L, top to 2_000L)
        assertEquals(0, state.reps)
        assertEquals(listOf(RepOutcome.NOT_LOW_ENOUGH), outcomes)
    }

    @Test
    fun `лёгкое дрожание наверху попыткой не считается`() {
        // −0.6 — это не «недожал», а движение в верхней половине. Раньше именно
        // такие кадры и рождали бесконечное «ниже» (владелец, 2026-08-25).
        val (_, outcomes) = run(top to 0L, -0.6f to 1_000L, top to 2_000L)
        assertEquals(emptyList<RepOutcome>(), outcomes)
    }

    @Test
    fun `десять честных отжиманий считаются как десять`() {
        var state = PushupState.START
        var now = 0L
        state = run(top to now, from = state).first
        repeat(10) {
            now += 900L
            state = run(0.1f to now, from = state).first
            now += 900L
            state = run(-0.95f to now, from = state).first
        }
        assertEquals(10, state.reps)
    }

    @Test
    fun `нераспознанный кадр не сбивает счёт`() {
        val (state, _) = run(top to 0L, bottom to 1_000L, null to 1_500L, top to 2_000L)
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
    fun `рука выпрямлена вертикально — плечо на длину кости выше локтя`() {
        // Верхняя точка отжимания: плечо ровно над локтем.
        val vertical = ArmPose(
            shoulder = point(0.50f, 0.20f),
            elbow = point(0.50f, 0.50f),
            wrist = point(0.50f, 0.80f),
        )
        val drop = PushupCounter.armDrop(vertical)
        assertNotNull(drop)
        assertEquals(-1f, drop!!, 0.01f)
    }

    @Test
    fun `плечо провалилось под локоть — глубина положительная`() {
        val low = ArmPose(
            shoulder = point(0.50f, 0.60f),
            elbow = point(0.50f, 0.50f),
            wrist = point(0.80f, 0.50f),
        )
        val drop = PushupCounter.armDrop(low)
        assertNotNull(drop)
        assertTrue(drop!! > 0f)
    }

    @Test
    fun `вырожденная плечевая кость глубины не даёт`() {
        // Делить не на что: точки схлопнулись, частное улетело бы в бессмыслицу.
        val collapsed = ArmPose(
            shoulder = point(0.500f, 0.500f),
            elbow = point(0.505f, 0.501f),
            wrist = point(0.80f, 0.50f),
        )
        assertNull(PushupCounter.armDrop(collapsed))
    }

    @Test
    fun `глубина не зависит от расстояния до телефона`() {
        // Тот же человек вдвое дальше: числа в кадре вдвое меньше, признак тот же.
        val near = ArmPose(point(0.50f, 0.20f), point(0.50f, 0.50f), point(0.80f, 0.50f))
        val far = ArmPose(point(0.50f, 0.35f), point(0.50f, 0.50f), point(0.65f, 0.50f))
        assertEquals(PushupCounter.armDrop(near)!!, PushupCounter.armDrop(far)!!, 0.01f)
    }

    @Test
    fun `негодный ракурс глубину не отдаёт вовсе`() {
        // Входной контроль остался прежним: не сошлись руки — кадру не верим целиком,
        // сколько бы аккуратно ни считалось смещение плеча.
        assertNull(PushupCounter.frameDepth(frameOf(straightArm, bentArm)))
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
