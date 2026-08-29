package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.LogWire
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Спрашивает компьютер, не пора ли освободиться.
 *
 * ⚠️ **Спрашивает телефон, а не приказывает компьютер.** Это принципиально: направление
 * остаётся прежним — телефон только клиент и портов не открывает (ADR-0006), — и
 * освобождение по сети не требует ни единого послабления в правилах. Ценой одного
 * коротенького запроса раз в три секунды.
 *
 * Работает **только пока идёт тревога**. Тревога занимает минуты в сутки, поэтому
 * батарее это ничего не стоит; вне тревоги освобождать нечего, и спрашивать не о чем.
 *
 * ⚠️ Ненадёжнее кабеля и не претендует на замену: нужен работающий Wi-Fi, доступный
 * компьютер и живой процесс. Не дозвонились — тревога снимется сторожем по дедлайну,
 * как снималась всегда.
 */
class ReleaseWatcher(context: Context, private val onRelease: () -> Unit) {

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "release-watch").apply { isDaemon = true }
    }

    @Volatile
    private var host: String? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        worker.scheduleWithFixedDelay(::tick, FIRST_DELAY_MS, PERIOD_MS, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        running = false
        worker.shutdownNow()
    }

    private fun tick() {
        if (!running) return
        try {
            // Адрес ищем один раз и запоминаем: широковещательный запрос каждые три
            // секунды — это шум в сети на ровном месте.
            val target = host ?: LogSender(app).discoverHost()?.also { host = it } ?: return
            if (ask(target)) {
                Log.i(TAG, "компьютер просит освободить")
                running = false
                onRelease()
            }
        } catch (e: Exception) {
            // Компьютер выключен, сеть пропала, окно приёма закрыто — обычное дело.
            host = null
        }
    }

    private fun ask(target: String): Boolean = Socket().use { socket ->
        socket.connect(InetSocketAddress(target, LogWire.TCP_PORT), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS
        socket.getOutputStream().apply {
            write((LogWire.ASK_RELEASE + "\n").toByteArray(Charsets.UTF_8))
            flush()
        }
        val answer = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine()
        answer?.trim() == LogWire.RELEASE_NOW
    }

    private companion object {
        const val TAG = "ReleaseWatcher"

        /** Первый вопрос — не мгновенно: в первые секунды тревоги поднимается экран. */
        const val FIRST_DELAY_MS = 2_000L

        /** Раз в три секунды (владелец, 2026-08-26): тревога длится минуты, батарее всё равно. */
        const val PERIOD_MS = 3_000L

        const val CONNECT_TIMEOUT_MS = 1_500
        const val READ_TIMEOUT_MS = 2_000
    }
}
