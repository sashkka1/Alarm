package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDeltaTest {

    private val files = mapOf("2026-08.jsonl" to 1000, "2026-09.jsonl" to 300)

    @Test
    fun `обычный день — шлём только хвосты`() {
        val plan = SyncDelta.plan(
            files = files,
            delivered = mapOf("2026-08.jsonl" to 1000, "2026-09.jsonl" to 250),
            haveNow = 1250,
            haveWhenDelivered = 1250,
        )
        assertEquals(SyncDelta.Mode.INCREMENTAL, plan.mode)
        assertFalse(plan.full)
        assertEquals(mapOf("2026-08.jsonl" to 1000, "2026-09.jsonl" to 250), plan.skip)
    }

    @Test
    fun `старый компьютер молчит о запасе — шлём всё`() {
        val plan = SyncDelta.plan(files, mapOf("2026-08.jsonl" to 1000), null, 1250)
        assertEquals(SyncDelta.Mode.FULL_NO_HAVE, plan.mode)
        assertTrue(plan.full)
        assertEquals(mapOf("2026-08.jsonl" to 0, "2026-09.jsonl" to 0), plan.skip)
    }

    @Test
    fun `первой удачной передачи ещё не было — шлём всё`() {
        val plan = SyncDelta.plan(files, emptyMap(), 0, null)
        assertEquals(SyncDelta.Mode.FULL_FIRST_TIME, plan.mode)
        assertEquals(mapOf("2026-08.jsonl" to 0, "2026-09.jsonl" to 0), plan.skip)
    }

    @Test
    fun `хранилище компьютера похудело — шлём всё заново`() {
        val plan = SyncDelta.plan(
            files = files,
            delivered = mapOf("2026-08.jsonl" to 1000, "2026-09.jsonl" to 300),
            haveNow = 7,
            haveWhenDelivered = 1300,
        )
        assertEquals(SyncDelta.Mode.FULL_SHRUNK, plan.mode)
        assertEquals(mapOf("2026-08.jsonl" to 0, "2026-09.jsonl" to 0), plan.skip)
    }

    @Test
    fun `компьютер прибавил за это время — это не похудение`() {
        val plan = SyncDelta.plan(files, mapOf("2026-09.jsonl" to 300), 9999, 1300)
        assertEquals(SyncDelta.Mode.INCREMENTAL, plan.mode)
    }

    @Test
    fun `новый месяц заводится сам и уходит целиком`() {
        val plan = SyncDelta.plan(
            files = files + ("2026-10.jsonl" to 12),
            delivered = mapOf("2026-08.jsonl" to 1000, "2026-09.jsonl" to 300),
            haveNow = 1300,
            haveWhenDelivered = 1300,
        )
        assertEquals(0, plan.skip["2026-10.jsonl"])
    }

    @Test
    fun `догруженная задним числом история ловится хвостом старого файла`() {
        // Шаги за август дописались в конец августовского файла — отметка их не прячет.
        val plan = SyncDelta.plan(
            files = mapOf("2026-08.jsonl" to 5000),
            delivered = mapOf("2026-08.jsonl" to 1000),
            haveNow = 1300,
            haveWhenDelivered = 1300,
        )
        assertEquals(1000, plan.skip["2026-08.jsonl"])
    }

    @Test
    fun `файл короче отметки перечитывается целиком, а не уходит в минус`() {
        val plan = SyncDelta.plan(
            files = mapOf("2026-08.jsonl" to 40),
            delivered = mapOf("2026-08.jsonl" to 1000),
            haveNow = 1300,
            haveWhenDelivered = 1300,
        )
        assertEquals(40, plan.skip["2026-08.jsonl"])
    }

    @Test
    fun `в плане есть строка на каждый файл, забыть файл нельзя`() {
        val plan = SyncDelta.plan(files, emptyMap(), 10, 10)
        assertEquals(files.keys, plan.skip.keys)
    }
}
