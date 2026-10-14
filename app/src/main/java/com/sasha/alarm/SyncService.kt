package com.sasha.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors

/**
 * Отдача журнала компьютеру — в службе переднего плана, а не в приёмнике.
 *
 * 🔴 **Появилась из отказа 14.09.2026, и причина у неё ровно одна: срок.** Суточная передача
 * жила внутри `SyncReceiver.onReceive` на `goAsync()`, а бродкаст-приёмнику система отводит
 * ограниченное время и снимает его, как только оно вышло. 14 сентября таймер сработал в
 * 10:00:29, а в 10:01:00 Android снял бродкаст ANR'ом — стек стоял в
 * `LogSender.handOver`, то есть посреди отдачи журнала (отчёт лежит в `dumpsys dropbox`:
 * `Subject: Broadcast of Intent { act=com.sasha.alarm.RESUME … SyncReceiver }`).
 *
 * Заход к этому времени стал слишком тяжёлым для приёмника: снимок использования занимает
 * около 23 секунд, а сам журнал уходит целиком — 192 771 строка, ещё около 19 секунд.
 * Сорок секунд работы в бродкасте были игрой на грани, и 10:00 её проиграло.
 *
 * ⚠️ **Кнопка «Передать статистику» те же сорок секунд отрабатывала спокойно** — она зовёт
 * `collectAndSend` из активити, где никакого срока нет вовсе. Отсюда и загадка «ручная
 * проходит, автоматическая нет»: путь один, сроки разные.
 *
 * ⚠️ **Служба переднего плана, а не `WorkManager`** — намеренно. Во-первых, у неё нет ни
 * срока, ни очереди, ни квоты: работа начинается в ту же секунду. Во-вторых, этот приём в
 * приложении уже обкатан на этом самом телефоне ([AlarmService]) —
 * а планировщик заданий HyperOS душит куда охотнее. Новых зависимостей не понадобилось.
 *
 * ⚠️ **Тип `specialUse`, как у [AlarmService]:** он уже объявлен и работает на этой
 * прошивке. `dataSync` был бы честнее по названию, но с Android 15 у него свои суточные
 * потолки и свои запреты на запуск из фона, а проверять их на живом телефоне владельца
 * ради красоты слова незачем.
 *
 * ⚠️ **Запуск из фона разрешён** двумя независимыми путями: приложение стоит в белом списке
 * экономии батареи, и бродкаст приходит от точного будильника — каждого из них достаточно.
 * Если система всё-таки откажет, [SyncReceiver] уходит на прежний путь: лучше заход с
 * риском не успеть, чем ни одного.
 */
class SyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val isReturn = intent?.getBooleanExtra(EXTRA_RETURN, false) ?: false

        // Сеть и работа с файлами — не на главном потоке; здесь он обслуживает уведомление.
        Executors.newSingleThreadExecutor().execute {
            try {
                SyncReceiver.collectAndSend(applicationContext, isReturn)
            } catch (e: Exception) {
                // Заход провалился — это не авария: страховка возврата поставлена ДО него
                // и осталась стоять, значит вернёмся через два часа (`SyncRetry`).
                Log.w(TAG, "передача не удалась", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        // ⚠️ Именно NOT_STICKY. Поднимать службу заново с пустым intent системе незачем:
        // когда возвращаться — решает цепочка возвратов в `SyncRetry`, а не автоподъём.
        return START_NOT_STICKY
    }

    /**
     * Канал свой и тихий.
     *
     * ⚠️ Уведомление живёт меньше минуты раз в сутки, и звенеть ему нечем и незачем:
     * `IMPORTANCE_LOW`, без звука, без вибрации, без значка на иконке.
     */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sync_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bird)
            .setContentTitle(getString(R.string.sync_title))
            .setContentText(getString(R.string.sync_text))
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    companion object {
        private const val TAG = "SyncService"

        private const val CHANNEL_ID = "log_sync"
        private const val NOTIFICATION_ID = 4713
        private const val EXTRA_RETURN = "isReturn"

        /**
         * Поднять службу и отдать ей заход.
         *
         * @return `false` — система запуск не пустила; звать нас было откуда-то, где это
         *         запрещено. Тогда решает вызывающий: [SyncReceiver] уходит на прежний путь.
         */
        fun start(context: Context, isReturn: Boolean): Boolean = try {
            context.startForegroundService(
                Intent(context, SyncService::class.java).putExtra(EXTRA_RETURN, isReturn),
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "служба передачи не запустилась", e)
            false
        }
    }
}
