package com.sasha.alarm.platform

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.sasha.alarm.core.NightAudio
import java.io.File
import java.io.RandomAccessFile
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Запись звука всей ночи в один файл WAV.
 *
 * Нужна не будильнику, а тому, что задумано после него: владелец хочет разбирать свой сон
 * по звуку сам и сверять разбор со Sleep Cycle (2026-08-26). Для этого нужны настоящие
 * ночи, записанные целиком.
 *
 * Устройство простое до скуки, и намеренно: поток читает микрофон и дописывает байты в
 * файл, больше не делая ничего. Никакой обработки на лету здесь быть не должно — восемь
 * часов подряд ошибётся любая, а переделать разбор по сохранённому файлу можно сколько
 * угодно раз.
 *
 * ⚠️ **Файл дописывается на ходу.** Если телефон выключится посреди ночи, записанное
 * останется на диске — потеряется только заголовок, а его чинит [repair]. Копить ночь в
 * памяти было бы и невозможно (гигабайт), и опаснее.
 */
class NightRecorder(context: Context) {

    private val dir = File(context.filesDir, DIR).apply { mkdirs() }
    private val log = EventLog(context)

    private val running = AtomicBoolean(false)

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var current: File? = null

    /** Идёт ли запись прямо сейчас. */
    val active: Boolean get() = running.get()

    /** Какой файл пишется. `null`, если запись не идёт. */
    val file: File? get() = current

    /**
     * Начать запись. Возвращает файл, в который она пойдёт, либо `null` — если не вышло.
     *
     * ⚠️ Молчаливого отказа здесь быть не может (P0 №7): не завелась запись — об этом
     * появляется событие в журнале, и кнопка на экране остаётся в положении «не пишем».
     */
    @SuppressLint("MissingPermission")
    fun start(startMillis: Long = System.currentTimeMillis()): File? {
        if (running.get()) return current

        val target = freeName(NightAudio.fileName(startMillis, ZoneId.systemDefault()))
        val record = try {
            open()
        } catch (e: Exception) {
            fail("open", e.message)
            return null
        }
        if (record == null) return null

        val out = try {
            RandomAccessFile(target, "rw").apply {
                setLength(0)
                // Место под заголовок. Настоящий ляжет сюда же, когда станет известна длина.
                write(NightAudio.header(0))
            }
        } catch (e: Exception) {
            runCatching { record.release() }
            fail("file", e.message)
            return null
        }

        running.set(true)
        current = target
        log.write(
            com.sasha.alarm.core.EventType.NIGHT_RECORD_STARTED,
            "file" to com.sasha.alarm.core.LogValue.of(target.name),
        )

        worker = thread(name = "night-audio", isDaemon = false) {
            pump(record, out, target, startMillis)
        }
        return target
    }

    /**
     * Остановить запись и дописать заголовок.
     *
     * `reason` уходит в журнал: по нему потом видно, чем ночь кончилась — звонком,
     * крайним сроком или рукой владельца.
     */
    fun stop(reason: String) {
        if (!running.compareAndSet(true, false)) return
        stopReason = reason
        worker?.join(STOP_WAIT_MS)
        worker = null
    }

    @Volatile
    private var stopReason: String = "unknown"

    // ───────────────────────────── внутреннее ─────────────────────────────

    @SuppressLint("MissingPermission")
    private fun open(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            NightAudio.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            fail("buffer", "getMinBufferSize=$minBuffer")
            return null
        }

        // Буфер с большим запасом: восемь часов подряд поток обязан переживать любую
        // задержку планировщика, а переполнение буфера — это дырка в записи навсегда.
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            NightAudio.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * BUFFER_FACTOR, NightAudio.BYTES_PER_SECOND * BUFFER_SECONDS),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            fail("init", "state=${record.state}")
            return null
        }
        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { record.release() }
                fail("start", "recordingState=${record.recordingState}")
                null
            } else {
                record
            }
        } catch (e: Exception) {
            runCatching { record.release() }
            fail("start", e.message)
            null
        }
    }

    private fun pump(record: AudioRecord, out: RandomAccessFile, target: File, startMillis: Long) {
        val chunk = ByteArray(NightAudio.BYTES_PER_SECOND / 2)
        var written = 0L
        var lastHeader = 0L
        var trouble: String? = null

        try {
            while (running.get()) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) {
                    // Отрицательное — ошибка чтения; ноль — устройство молчит совсем.
                    // И то и другое означает, что ночь дальше не пишется, а значит запись
                    // должна кончиться сейчас, а не тянуться пустым файлом до утра.
                    trouble = "read=$read"
                    break
                }
                out.write(chunk, 0, read)
                written += read

                // Заголовок обновляется по ходу дела, а не только в конце: файл, оборванный
                // выключением телефона, останется проигрываемым до последней записанной минуты.
                if (written - lastHeader >= HEADER_REFRESH_BYTES) {
                    lastHeader = written
                    writeHeader(out, written)
                }
            }
        } catch (e: Exception) {
            trouble = e.message ?: e.javaClass.simpleName
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching { writeHeader(out, written) }
            runCatching { out.close() }
            running.set(false)
            current = null
        }

        log.write(
            com.sasha.alarm.core.EventType.NIGHT_RECORD_FINISHED,
            mapOf(
                "file" to com.sasha.alarm.core.LogValue.of(target.name),
                "reason" to com.sasha.alarm.core.LogValue.of(trouble?.let { "error" } ?: stopReason),
                "ms" to com.sasha.alarm.core.LogValue.of(System.currentTimeMillis() - startMillis),
                "bytes" to com.sasha.alarm.core.LogValue.of(written),
                "seconds" to com.sasha.alarm.core.LogValue.of(written / NightAudio.BYTES_PER_SECOND),
            ) + (trouble?.let { mapOf("error" to com.sasha.alarm.core.LogValue.of(it)) } ?: emptyMap()),
        )
    }

    private fun writeHeader(out: RandomAccessFile, dataBytes: Long) {
        val at = out.filePointer
        out.seek(0)
        out.write(NightAudio.header(dataBytes.toInt()))
        out.seek(at)
    }

    private fun fail(stage: String, detail: String?) {
        running.set(false)
        current = null
        log.write(
            com.sasha.alarm.core.EventType.NIGHT_RECORD_FAILED,
            "stage" to com.sasha.alarm.core.LogValue.of(stage),
            "error" to com.sasha.alarm.core.LogValue.of(detail ?: "неизвестно"),
        )
    }

    /**
     * Свободное имя: записанное не затирается никогда.
     *
     * ⚠️ Имя из [NightAudio.fileName] точно до минуты, и два запуска подряд дают одно и то
     * же. Поймано на телефоне 2026-08-26: перезапуск службы стёр 17 уже записанных секунд,
     * открыв тот же файл заново. Ночь стоит восьми часов сна — терять её из-за совпадения
     * имён недопустимо, поэтому к занятому имени приписывается номер.
     */
    private fun freeName(name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val base = name.removeSuffix(".wav")
        var n = 2
        while (true) {
            val next = File(dir, "$base-$n.wav")
            if (!next.exists()) return next
            n++
        }
    }

    /** Все записи, что лежат на телефоне, свежие сверху. */
    fun recordings(): List<File> =
        dir.listFiles()?.filter { it.isFile && it.extension == "wav" }?.sortedByDescending { it.name }
            ?: emptyList()

    /**
     * Починить заголовок у файла, оборванного на середине.
     *
     * ⚠️ Нужно потому, что телефон может выключиться ночью: звук на диске цел, но в
     * заголовке остались нули последнего обновления, и проигрыватель покажет пустоту.
     */
    fun repair(target: File): Boolean = try {
        RandomAccessFile(target, "rw").use { out ->
            val data = out.length() - NightAudio.HEADER_BYTES
            if (data <= 0) {
                false
            } else {
                out.seek(0)
                out.write(NightAudio.header(data.toInt()))
                true
            }
        }
    } catch (e: Exception) {
        false
    }

    companion object {
        const val DIR = "night-audio"

        private const val BUFFER_FACTOR = 4
        private const val BUFFER_SECONDS = 4

        /** Как часто переписывается заголовок — раз в минуту записи. */
        private const val HEADER_REFRESH_BYTES = NightAudio.BYTES_PER_SECOND * 60

        private const val STOP_WAIT_MS = 3_000L
    }
}
