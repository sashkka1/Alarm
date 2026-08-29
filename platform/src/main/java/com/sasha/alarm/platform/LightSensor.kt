package com.sasha.alarm.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Разовый замер освещённости.
 *
 * Отвечает на единственный вопрос: на улице человек или в комнате. Разница тут такая,
 * что порог подбирать не нужно — комната даёт около 300 люкс, пасмурная улица 10 000,
 * солнце до 100 000. Мерить постоянно незачем и вредно для батареи, поэтому замер
 * разовый: берётся первое же показание и датчик отпускается.
 *
 * ⚠️ Это отдельный датчик рядом с динамиком, а не камера — экран может быть погашен,
 * разрешений он не требует. Но телефон в кармане покажет темноту, поэтому замер
 * привязан к касанию метки: в этот момент телефон заведомо в руке.
 *
 * Датчика может не быть вовсе — тогда приходит `null`, и это не ошибка.
 */
class LightSensor(context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val available: Boolean get() = sensor != null

    /**
     * Взять одно показание. Колбэк зовётся ровно один раз — со значением или с `null`,
     * если датчика нет либо он молчит дольше [timeoutMs].
     */
    fun sample(timeoutMs: Long = DEFAULT_TIMEOUT_MS, onResult: (Float?) -> Unit) {
        val manager = this.manager
        val sensor = this.sensor
        if (manager == null || sensor == null) {
            onResult(null)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (done) return
                done = true
                handler.removeCallbacksAndMessages(this)
                runCatching { manager.unregisterListener(this) }
                onResult(event.values.firstOrNull())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // Молчащий датчик не должен подвесить вызывающего навсегда: ответ придёт
        // в любом случае, пусть и пустой.
        handler.postDelayed({
            if (done) return@postDelayed
            done = true
            runCatching { manager.unregisterListener(listener) }
            onResult(null)
        }, timeoutMs)

        val registered = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }.getOrDefault(false)

        if (!registered && !done) {
            done = true
            handler.removeCallbacksAndMessages(null)
            onResult(null)
        }
    }

    /**
     * Следить за освещённостью, пока идёт испытание метками.
     *
     * ⚠️ **Непрерывная съёмка света разрешена только здесь** (владелец, 2026-08-27:
     * «в момент испытания меткой ты можешь собирать статистику по освещению»). Всё
     * остальное время замер остаётся разовым и привязанным к касанию метки — постоянно
     * включённый датчик это батарея, а испытание длится минуты и телефон в руке.
     *
     * ⚠️ Показания прореживаются по времени ([everyMs]): датчик отдаёт их десятками в
     * секунду, и без прореживания одно испытание положило бы в журнал тысячу строк.
     * Между отдачами берётся **максимум**, а не последнее значение: за пять секунд ходьбы
     * по квартире важно, что человек прошёл мимо окна, а не то, где он оказался в конце.
     *
     * @return как остановить наблюдение. Звать обязательно — иначе датчик останется
     *   включённым и после испытания.
     */
    fun watch(everyMs: Long = DEFAULT_EVERY_MS, onValue: (Float) -> Unit): () -> Unit {
        val manager = this.manager
        val sensor = this.sensor
        if (manager == null || sensor == null) return {}

        val listener = object : SensorEventListener {
            private var lastAt = 0L
            private var peak = Float.NEGATIVE_INFINITY

            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull() ?: return
                if (value > peak) peak = value
                val now = System.currentTimeMillis()
                if (lastAt != 0L && now - lastAt < everyMs) return
                lastAt = now
                val out = peak
                peak = Float.NEGATIVE_INFINITY
                onValue(out)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            // ⚠️ `SENSOR_DELAY_NORMAL`, а не `FASTEST`: частоту всё равно режет прореживание,
            // а лишние пробуждения процессора при живой тревоге — это батарея ровно тогда,
            // когда телефон уже держит экран на полной яркости.
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!registered) return {}

        return { runCatching { manager.unregisterListener(listener) } }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 700L

        /**
         * Как часто отдавать показание при наблюдении.
         *
         * Пять секунд: испытание метками идёт минуты, и такой шаг даёт полтора десятка
         * точек — достаточно, чтобы увидеть «вышел на свет» и «вернулся в комнату»,
         * и мало, чтобы журнал не распух.
         */
        const val DEFAULT_EVERY_MS = 5_000L
    }
}
