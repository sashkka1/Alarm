package com.sasha.alarm.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Уведомление, которое поднимает экран тревоги.
 *
 * Канал важности MAX и `fullScreenIntent` — штатный способ показать окно поверх
 * локскрина. Звука у канала нет намеренно: в версии 1 приложение молчит,
 * звук приезжает в v2.
 */
object AlarmNotifications {

    const val CHANNEL_ID = "alarm_ring"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context, name: String, description: String) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * @param raiseScreen нужно ли уведомлению поднимать экран тревоги.
     *
     * ⚠️ Здесь вся суть надоевшей плашки сверху. Полноэкранное намерение — это и
     * есть механизм подъёма экрана поверх запертого телефона, убрать его насовсем
     * нельзя. Но система показывает его **плашкой**, когда поднимать нечего: наш
     * экран уже открыт, запускать некуда — и вместо окна она рисует уведомление
     * поверх него. Накрыть плашку своим окном невозможно: её рисует системный
     * интерфейс слоем выше любых окон приложения.
     *
     * Поэтому пока экран на месте, уведомление живёт **без** полноэкранного
     * намерения — тогда и показывать плашкой нечего. Экран потеряли — намерение
     * возвращается вместе с задачей поднять его заново.
     */
    fun build(
        context: Context,
        alarmActivity: Class<*>,
        icon: Int,
        title: String,
        text: String,
        raiseScreen: Boolean = true,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)

        if (raiseScreen) {
            val fullScreen = PendingIntent.getActivity(
                context,
                2001,
                Intent(context, alarmActivity).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fullScreen, true)
        }
        // Нажатия нет намеренно: экран и так открыт, а если его потеряли —
        // возвращает сторож, а не человек.
        return builder.build()
    }
}
