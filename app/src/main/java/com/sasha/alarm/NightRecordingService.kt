package com.sasha.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.sasha.alarm.core.NightAudio
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.NightRecorder
import java.io.File
import java.time.ZoneId

/**
 * Служба, которая пишет ночь.
 *
 * Отдельная от [AlarmService] намеренно: у той своя задача — вести тревогу, и она
 * поднимается и гаснет вместе с ней. Запись же начинается вечером, живёт всю ночь и должна
 * пережить всё, что случится с тревогой.
 *
 * ⚠️ **Служба переднего плана с типом `microphone` — единственный способ писать при
 * погашенном экране.** Обычный сервис Android усыпит через пару минут, а с Android 14
 * доступ к микрофону из фона запрещён прямо: приложение, не показавшее уведомление,
 * получает тишину вместо звука.
 */
class NightRecordingService : Service() {

    private lateinit var recorder: NightRecorder

    override fun onCreate() {
        super.onCreate()
        recorder = NightRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop(intent.getStringExtra(EXTRA_REASON) ?: "manual")
                return START_NOT_STICKY
            }

            else -> start()
        }
        // ⚠️ Именно NOT_STICKY, и это важно. С START_STICKY система поднимает службу
        // сама, с пустым intent — и она начинает писать **новую** ночь без всякой просьбы.
        // Поймано на телефоне 2026-08-26: через 280 мс после остановки запись стартовала
        // заново и затёрла 17 записанных секунд. Микрофон включается только по кнопке;
        // пережить смерть процесса запись всё равно не может — микрофон закрывается вместе
        // с ним, — поэтому автоподъём здесь не спасает ничего, а только вредит.
        return START_NOT_STICKY
    }

    private fun start() {
        if (recorder.active) return

        val now = System.currentTimeMillis()
        ensureChannel(this)
        startForegroundCompat()

        val file = recorder.start(now)
        if (file == null) {
            // Не завелась — уходим сразу, а не висим уведомлением о записи, которой нет.
            // Почему не завелась, уже записано в журнал самим рекордером (P0 №7).
            Log.i(TAG, "запись не началась")
            active = false
            stopSelf()
            return
        }

        active = true
        startedAt = now
        deadlineAt = NightAudio.deadline(now, ZoneId.systemDefault())

        // ⚠️ Крайний срок ставится будильником системы, а не таймером в памяти: восемь часов
        // ожидания переживёт только он. Тот же приём, что у сторожа тревоги (P0 №5).
        AlarmScheduling.scheduleGuard(
            context = applicationContext,
            triggerAtMillis = deadlineAt,
            timeoutMillis = deadlineAt - now,
            receiver = NightDeadlineReceiver::class.java,
        )

        Log.i(TAG, "запись пошла: ${file.name}, до ${deadlineAt}")
        notifyState()
    }

    private fun stop(reason: String) {
        AlarmScheduling.cancelGuard(applicationContext, NightDeadlineReceiver::class.java)
        recorder.stop(reason)
        active = false
        startedAt = 0L
        deadlineAt = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Службу могли убить не через нашу кнопку — системой, «очистить всё», обновлением.
        // Заголовок файла при этом обязан быть дописан, иначе ночь останется непроигрываемой.
        if (recorder.active) recorder.stop("killed")
        active = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ───────────────────────────── уведомление ─────────────────────────────

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Канал у записи свой и тихий.
     *
     * ⚠️ Ни в коем случае не канал тревоги: тот сделан громким и настойчивым (`IMPORTANCE_HIGH`),
     * а уведомление о записи висит всю ночь. Оно должно молчать и не будить.
     */
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.night_recording_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notifyState() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, NightRecordingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_REASON, "manual"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Кнопка «Остановить» прямо в уведомлении: телефон ночью заперт, и лезть за
        // остановкой в приложение — лишние шаги там, где их делают спросонья.
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.night_recording_title))
            .setContentText(getString(R.string.night_recording_text))
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.night_recording_stop),
                    stop,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val TAG = "NightRecording"

        const val ACTION_STOP = "com.sasha.alarm.NIGHT_RECORD_STOP"
        const val EXTRA_REASON = "reason"

        private const val CHANNEL_ID = "night_recording"
        private const val NOTIFICATION_ID = 4711

        /**
         * Идёт ли запись.
         *
         * ⚠️ Флаг в памяти, а не на диске, и это правильно: запись физически существует
         * только пока жив процесс. Умер процесс — запись кончилась, и врать об этом
         * пережившим перезапуск флагом было бы хуже, чем не помнить вовсе.
         */
        @Volatile
        var active: Boolean = false
            private set

        @Volatile
        var startedAt: Long = 0L
            private set

        @Volatile
        var deadlineAt: Long = 0L
            private set

        fun start(context: Context) {
            val intent = Intent(context, NightRecordingService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Остановить запись, если она идёт.
         *
         * Зовётся отовсюду, где ночь считается законченной: кнопкой, звонком чужого
         * будильника, подъёмом нашей тревоги, крайним сроком. Если записи нет — не делает
         * ничего, поэтому звать можно свободно.
         */
        fun stop(context: Context, reason: String) {
            if (!active) return
            val intent = Intent(context, NightRecordingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_REASON, reason)
            runCatching { context.startService(intent) }
        }

        /** Записи, лежащие на телефоне. */
        fun recordings(context: Context): List<File> = NightRecorder(context).recordings()
    }
}
