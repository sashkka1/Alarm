package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.LogWire
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Отправка журнала на компьютер владельца.
 *
 * Телефон здесь **только клиент**: открывает исходящее соединение, отдаёт и закрывает.
 * Портов не слушает (ADR-0006).
 *
 * Шлётся **весь** журнал целиком, а не «новое с прошлого раза»: повторы компьютер
 * отбрасывает сам, зато нечего рассинхронизировать и хранилище компьютера восстановится
 * из телефона, если однажды потеряется.
 *
 * ⚠️ Ни один метод не бросает исключений: недоступный компьютер — обычное состояние
 * (ноутбук выключен, телефон вне дома), а не авария. Не дошло — попробуем в следующий раз,
 * журнал никуда не девается.
 *
 * ⚠️ Вызывать только с фонового потока: сеть в главном потоке Android запрещает сама.
 */
class LogSender(context: Context) {

    private val app = context.applicationContext
    private val logDir = File(app.filesDir, "log")

    sealed interface Result {
        data class Sent(val lines: Int, val accepted: Int, val host: String) : Result
        data object NothingToSend : Result
        data class NotFound(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Найти компьютер и отдать ему журнал.
     *
     * @param host адрес, вписанный руками. `null` — искать широковещательным запросом.
     */
    fun send(host: String? = null): Result {
        val lines = readLog()
        if (lines.isEmpty()) return Result.NothingToSend

        val target = host?.takeIf { it.isNotBlank() }?.let { it to LogWire.TCP_PORT }
            ?: discover()
            ?: return Result.NotFound("компьютер не отозвался в сети")

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.first, target.second), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write((LogWire.HELLO + "\n").toByteArray(Charsets.UTF_8))
                    for (line in lines) write((line + "\n").toByteArray(Charsets.UTF_8))
                    write((LogWire.END_OF_BATCH + "\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
                val answer = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine()
                val accepted = LogWire.parseAccepted(answer)
                if (accepted == null) {
                    Result.Failed("компьютер ответил непонятно")
                } else {
                    Log.i(TAG, "отдано $accepted строк на ${target.first}")
                    Result.Sent(lines.size, accepted, target.first)
                }
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "связь оборвалась")
        }
    }

    /**
     * Широковещательный вопрос «где тут компьютер».
     *
     * ⚠️ В некоторых сетях широковещательный трафик запрещён — тогда ответа не будет
     * никогда, и остаётся адрес, вписанный руками в настройках.
     */
    /** Только адрес компьютера, без передачи. Нужен сторожу освобождения. */
    fun discoverHost(): String? = discover()?.first

    private fun discover(): Pair<String, Int>? = try {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = DISCOVER_TIMEOUT_MS
            val question = LogWire.DISCOVER_QUESTION.toByteArray(Charsets.UTF_8)

            // ⚠️ Спрашиваем и по общему 255.255.255.255, и по широковещательному адресу
            // каждой своей сети. Общий адрес роутеры и Windows разносят не всегда,
            // а подсетевой (вида 192.168.1.255) доходит надёжнее. Дешевле послать оба,
            // чем разбираться утром, почему статистика не уехала.
            for (address in broadcastAddresses()) {
                runCatching {
                    socket.send(DatagramPacket(question, question.size, address, LogWire.UDP_PORT))
                }
            }

            val buffer = ByteArray(256)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val port = LogWire.parseDiscoverAnswer(
                String(packet.data, 0, packet.length, Charsets.UTF_8)
            )
            if (port == null) null else packet.address.hostAddress?.let { it to port }
        }
    } catch (e: SocketTimeoutException) {
        null // компьютер выключен или окно приёма закрыто — обычное дело
    } catch (e: Exception) {
        null
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach(out::add)
        }
        return out.distinct()
    }

    /** Весь журнал, строка за строкой. Файлы читаются по порядку месяцев. */
    private fun readLog(): List<String> = try {
        logDir.listFiles { f: File -> f.isFile && f.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            ?.flatMap { it.readLines() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "журнал не прочитался", e)
        emptyList()
    }

    private companion object {
        const val TAG = "LogSender"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 30_000
        const val DISCOVER_TIMEOUT_MS = 2_500
    }
}
