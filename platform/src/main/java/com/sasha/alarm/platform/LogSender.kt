package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.LogWire
import com.sasha.alarm.core.SyncDelta
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
     * Адрес берётся из трёх мест по порядку, и порядок этот не случаен:
     *
     * 1. **[host], если передан** — путь для проверок и для будущего поля в настройках.
     * 2. **Запомненный адрес этой сети** ([SyncStateStore.rememberedHost]) — попытка
     *    стоит доли секунды и в девяти случаях из десяти на этом всё и кончается.
     * 3. **Широковещательный поиск** — когда сеть незнакомая или роутер сменил адрес.
     *
     * ⚠️ **Запомненный адрес не отменяет поиск, а опережает его.** Не отозвался — идём
     * искать как раньше; иначе смена адреса по DHCP превратилась бы в молчаливый отказ
     * до тех пор, пока владелец не полез бы в настройки.
     *
     * @param host адрес, вписанный руками. `null` — вспомнить, а потом искать.
     */
    fun send(host: String? = null): Result {
        val files = readLogFiles()
        if (files.values.sumOf { it.size } == 0) return Result.NothingToSend

        val state = SyncStateStore(app)
        val networkKey = NetworkKey.current(app)

        val explicit = host?.takeIf { it.isNotBlank() }
        val remembered = if (explicit == null) state.rememberedHost(networkKey) else null

        // ⚠️ У запомненного адреса таймаут короче: он либо ответит сразу, либо его там
        // нет вовсе, а ждать на нём четыре секунды значит съесть треть захода впустую.
        val attempts = buildList {
            explicit?.let { add(it to CONNECT_TIMEOUT_MS) }
            remembered?.let { add(it to REMEMBERED_CONNECT_TIMEOUT_MS) }
        }

        for ((address, timeout) in attempts) {
            when (val result = handOver(files, address, timeout)) {
                is Result.Sent -> return result
                else -> Log.i(TAG, "по адресу $address не вышло: $result")
            }
        }

        val found = discover()?.first
            ?: return Result.NotFound("компьютер не отозвался в сети")
        val result = handOver(files, found, CONNECT_TIMEOUT_MS)
        if (result is Result.Sent) state.rememberHost(networkKey, found)
        return result
    }

    /**
     * Один разговор с компьютером по конкретному адресу.
     *
     * ⚠️ **Пустая пачка — тоже разговор, и обрывать его нельзя** (2026-09-14). Компьютер
     * отмечает «телефон приходил» на любой состоявшийся разговор, и по этой отметке он
     * решает, не пропущена ли сегодняшняя передача. Замолчи мы в день, когда нового нет, —
     * и хаб пожалуется на исправный телефон.
     */
    private fun handOver(
        files: Map<String, List<String>>,
        host: String,
        connectTimeoutMs: Int,
    ): Result = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, LogWire.TCP_PORT), connectTimeoutMs)
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            out.write((LogWire.HELLO + "\n").toByteArray(Charsets.UTF_8))
            out.flush()

            // ⚠️ Сколько у него есть, компьютер называет ДО пачки — иначе решать было бы
            // не на чем. Срок тут короткий и свой: старый компьютер про запас молчит и
            // просто закрывает соединение, ждать его полминуты незачем.
            socket.soTimeout = HAVE_TIMEOUT_MS
            val have = try {
                LogWire.parseHave(reader.readLine())
            } catch (e: Exception) {
                null
            }

            val state = SyncStateStore(app)
            val counts = files.mapValues { it.value.size }
            val plan = SyncDelta.plan(
                files = counts,
                delivered = state.deliveredLines(),
                haveNow = have,
                haveWhenDelivered = state.deliveredHave(),
            )

            socket.soTimeout = READ_TIMEOUT_MS
            var sent = 0
            for ((name, lines) in files) {
                val skip = plan.skip[name] ?: 0
                for (i in skip until lines.size) {
                    out.write((lines[i] + "\n").toByteArray(Charsets.UTF_8))
                    sent++
                }
            }
            out.write((LogWire.END_OF_BATCH + "\n").toByteArray(Charsets.UTF_8))
            out.flush()

            val accepted = LogWire.parseAccepted(reader.readLine())
            if (accepted == null) {
                Result.Failed("компьютер ответил непонятно")
            } else {
                // Отметка двигается только по ответу компьютера. Не ответил — не считается.
                state.rememberDelivered(counts, have)
                Log.i(TAG, "отдано $sent строк (${plan.mode}) на $host, у него было $have")
                Result.Sent(sent, accepted, host)
            }
        }
    } catch (e: Exception) {
        Result.Failed(e.message ?: "связь оборвалась")
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
    /**
     * Журнал по файлам, в порядке имён.
     *
     * ⚠️ **Разбиение по файлам здесь не оформление, а основа отметки о доставленном:**
     * она считает строки в каждом файле отдельно (`SyncDelta`). Склей всё в один список —
     * и догруженная задним числом история перестанет находиться.
     */
    private fun readLogFiles(): Map<String, List<String>> = try {
        logDir.listFiles { f: File -> f.isFile && f.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            ?.associate { file -> file.name to file.readLines().filter { it.isNotBlank() } }
            ?: emptyMap()
    } catch (e: Exception) {
        Log.w(TAG, "журнал не прочитался", e)
        emptyMap()
    }

    private companion object {
        const val TAG = "LogSender"
        const val CONNECT_TIMEOUT_MS = 4_000

        /** Запомненный адрес либо отвечает сразу, либо его там нет. */
        const val REMEMBERED_CONNECT_TIMEOUT_MS = 1_200
        const val READ_TIMEOUT_MS = 30_000

        /**
         * Сколько ждать ответа про запас компьютера.
         *
         * ⚠️ Свой срок, короткий: компьютер старой версии про запас не говорит вовсе, и
         * ждать его тридцать секунд значило бы съесть весь заход на пустом месте. Не
         * дождались — считаем, что он не сказал, и шлём журнал целиком.
         */
        const val HAVE_TIMEOUT_MS = 4_000
        const val DISCOVER_TIMEOUT_MS = 2_500
    }
}
