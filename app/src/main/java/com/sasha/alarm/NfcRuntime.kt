package com.sasha.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.NfcRules
import com.sasha.alarm.core.NfcScan
import com.sasha.alarm.core.NfcRun
import com.sasha.alarm.core.NfcTag

/**
 * Ход прохождения маршрута меток во время тревоги.
 *
 * Отдельно от [AlarmRuntime] намеренно: считыватель живёт на активити, а не в
 * сервисе, и всё испытание целиком укладывается в пару «экран + метка». Общее у
 * них одно — процесс, поэтому пересоздание активити (а её пересоздают: сторож
 * возвращает экран обратно, если с него ушли) прогресс не теряет.
 *
 * ⚠️ Правил здесь нет: считает [NfcRules] в `:core`, тут только состояние.
 */
object NfcRuntime {

    var run by mutableStateOf<NfcRun?>(null)
        private set

    private var tags: List<NfcTag> = emptyList()

    /** Для какого запуска тревоги начат маршрут — чтобы не начинать его заново при возврате экрана. */
    var startedAtMillis: Long = 0L
        private set

    /**
     * Подготовить прохождение под текущую тревогу.
     *
     * Испытание считается заданным, только если выбраны метки **и** собран
     * маршрут: пустой маршрут прошёлся бы сам собой, поэтому экран в этом случае
     * идёт по примерам — тем же запасным путём, что и остальные незаданные испытания.
     */
    fun begin(state: AlarmState) {
        val startedAt = state.run?.startedAtMillis
        if (startedAt == null || state.challenge != Challenge.NFC || !state.nfc.ready) {
            reset()
            return
        }
        if (run != null && startedAtMillis == startedAt) return

        startedAtMillis = startedAt
        tags = state.nfc.tags
        run = NfcRun.of(state.nfc)
        lastScan = null
        scanTick = 0
    }

    /**
     * Что сказал прошлый раз считыватель и сколько касаний было всего.
     *
     * Счётчик нужен, потому что ответ может повториться слово в слово: приложили ту же
     * не ту метку дважды — сообщение то же самое, а показать его надо заново.
     */
    var lastScan by mutableStateOf<NfcScan?>(null)
        private set

    var scanTick by mutableIntStateOf(0)
        private set

    /** Метка приложена. `true` — маршрут пройден целиком. */
    fun onTag(id: String, nowMillis: Long): Boolean {
        val current = run ?: return false
        val result = NfcRules.scan(current, tags, id, nowMillis)
        run = result.run
        result.outcome?.let {
            lastScan = it
            scanTick++
        }
        return result.run.isComplete
    }

    fun reset() {
        run = null
        tags = emptyList()
        startedAtMillis = 0L
        lastScan = null
        scanTick = 0
    }
}
