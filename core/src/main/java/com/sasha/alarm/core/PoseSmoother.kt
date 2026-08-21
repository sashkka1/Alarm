package com.sasha.alarm.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Сглаживание скелета для показа на экране — фильтр «одно евро» (One Euro).
 *
 * Тяжёлая модель на телефоне выдаёт единицы кадров в секунду, и точки между
 * ответами не двигаются вовсе, а потом прыгают. Плюс сама модель на каждом кадре
 * чуть промахивается, и точки дрожат, даже когда человек стоит неподвижно.
 *
 * Обычное сглаживание с одним коэффициентом лечит одно ценой другого: давит дрожь
 * — отстаёт на движении, поспевает за движением — пропускает дрожь. One Euro решает
 * это тем, что **коэффициент зависит от скорости точки**: стоит на месте — сглаживаем
 * сильно, поехала быстро — почти не сглаживаем. Тот же фильтр применяет у себя и сам
 * MediaPipe.
 *
 * ⚠️ **Только для показа.** Счёт повторов идёт по несглаженным точкам: сглаживание
 * подрезает края движения, а именно по краям и стоят пороги «внизу/вверху».
 */
object PoseSmoother {

    /**
     * Частота среза для неподвижной точки, герцы.
     *
     * Чем меньше, тем спокойнее стоит скелет и тем заметнее отставание на старте
     * движения. Значение из работы про One Euro, подошло без подгонки.
     */
    const val MIN_CUTOFF = 1.2

    /**
     * Насколько скорость поднимает частоту среза.
     *
     * Ноль означал бы обычное сглаживание. Чем больше, тем охотнее фильтр пропускает
     * быстрое движение — и тем меньше отставание там, где оно и мешает.
     *
     * ⚠️ Число намного больше канонического 0.35 не по ошибке: в исходной работе
     * координаты пиксельные, а здесь доли кадра — те же движения дают скорость
     * примерно в тысячу раз меньше. С каноническим значением фильтр не разгонялся
     * и скелет полз за телом; это поймал тест «за быстрым движением поспевает».
     */
    const val BETA = 4.0

    /** Сглаживание самой оценки скорости: без него скорость дрожит вместе с точкой. */
    const val DERIVATIVE_CUTOFF = 1.0

    /**
     * Дальше этого точку не догоняем, а прыгаем к ней сразу.
     *
     * Человек мог войти в кадр или модель перескочила на другого — тянуть точку
     * через пол-экрана плавно значило бы секунды заведомо неверного скелета.
     */
    const val JUMP_DISTANCE = 0.35f

    /** Что фильтр помнит между кадрами. Хранит экран, ядро остаётся без состояния. */
    data class Memory(
        val points: List<PosePoint>,
        /** Скорость каждой точки по осям, доли кадра в секунду. */
        val velocity: List<Pair<Float, Float>>,
    )

    /**
     * Новый кадр.
     *
     * @param memory что было нарисовано и с какой скоростью двигалось; null — первый кадр.
     * @param fresh что пришло от модели.
     * @param deltaMillis сколько прошло с прошлой отрисовки.
     */
    fun blend(memory: Memory?, fresh: List<PosePoint>, deltaMillis: Long): Memory {
        if (memory == null || memory.points.size != fresh.size || deltaMillis <= 0L) {
            return Memory(fresh, List(fresh.size) { 0f to 0f })
        }

        val seconds = deltaMillis / 1000.0
        val points = ArrayList<PosePoint>(fresh.size)
        val velocity = ArrayList<Pair<Float, Float>>(fresh.size)

        for (i in fresh.indices) {
            val target = fresh[i]
            val was = memory.points[i]

            val jumped = max(abs(was.x - target.x), abs(was.y - target.y)) > JUMP_DISTANCE
            if (jumped) {
                points += target
                velocity += 0f to 0f
                continue
            }

            val (vx, vy) = smoothVelocity(memory.velocity[i], was, target, seconds)
            points += PosePoint(
                x = follow(was.x, target.x, vx, seconds),
                y = follow(was.y, target.y, vy, seconds),
                // Видимость не сглаживаем: «точку видно» — это да или нет,
                // и половинчатое значение здесь означало бы неправду.
                confidence = target.confidence,
                // Глубину сглаживаем обязательно: по ней решается, что чем
                // перекрывается, и её дрожь давала бы мигание порядка отрисовки.
                z = follow(was.z, target.z, 0f, seconds),
            )
            velocity += vx to vy
        }
        return Memory(points, velocity)
    }

    /** Скорость точки, сама по себе сглаженная — иначе она дрожит вместе с точкой. */
    private fun smoothVelocity(
        previous: Pair<Float, Float>,
        was: PosePoint,
        target: PosePoint,
        seconds: Double,
    ): Pair<Float, Float> {
        val weight = alpha(DERIVATIVE_CUTOFF, seconds)
        val rawX = ((target.x - was.x) / seconds).toFloat()
        val rawY = ((target.y - was.y) / seconds).toFloat()
        return (previous.first + (rawX - previous.first) * weight) to
            (previous.second + (rawY - previous.second) * weight)
    }

    /** Шаг к цели с частотой среза, поднятой скоростью. */
    private fun follow(was: Float, target: Float, velocity: Float, seconds: Double): Float {
        val cutoff = MIN_CUTOFF + BETA * abs(velocity)
        val weight = alpha(cutoff, seconds)
        return was + (target - was) * weight
    }

    /**
     * Доля пути к цели за этот кадр.
     *
     * Зависит и от частоты среза, и от того, сколько прошло времени: за вдвое
     * больший промежуток проходим ближе к цели, и плавность не зависит от того,
     * как часто рисует экран.
     */
    fun alpha(cutoff: Double, seconds: Double): Float {
        if (seconds <= 0.0) return 0f
        val tau = 1.0 / (2.0 * Math.PI * cutoff)
        return min(1.0, max(0.0, 1.0 / (1.0 + tau / seconds))).toFloat()
    }
}
