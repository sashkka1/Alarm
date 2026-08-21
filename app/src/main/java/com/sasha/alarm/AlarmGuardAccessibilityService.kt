package com.sasha.alarm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Второй слой блокировки: возврат экрана тревоги по событию, а не по таймеру.
 *
 * Зачем он нужен сверх обычного оверлея из [AlarmService] — две вещи:
 *
 *  1. **Скорость.** Система сама сообщает «сменилось активное окно» в тот же миг,
 *     когда это произошло. Возврат получается мгновенным, а не через проверку раз
 *     в несколько сотен миллисекунд.
 *  2. **Живучесть окна.** Обычный оверлей система прячет на некоторых своих экранах —
 *     в первую очередь в системных настройках, то есть ровно там, куда пошёл бы
 *     человек, чтобы отобрать у приложения разрешения. Окно службы специальных
 *     возможностей там не прячется.
 *
 * Служба полностью необязательна: не выдано разрешение — работает первый слой.
 * Содержимое чужих окон не читается (`canRetrieveWindowContent="false"`) — нам
 * важен только факт смены окна.
 */
class AlarmGuardAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayWindow? = null

    /** Что было на переднем плане по последнему событию смены окна. */
    private var lastForegroundPackage: String? = null
    private var lastForegroundClass: String? = null

    /** Когда последний раз смахнули Sleep Cycle — чтобы не долбить жестами подряд. */
    private var lastForeignSwipeAt = 0L

    /**
     * Заглушка на системные кнопки. Живёт всю тревогу, а не только когда экран
     * потеряли: кнопки должны быть мертвы и тогда, когда наш экран на месте.
     */
    private val navBlocker by lazy { NavBarBlocker(this) }

    /** Подстраховка на случай, если событие о смене окна почему-то не придёт. */
    private val recheck = object : Runnable {
        override fun run() {
            handler.postDelayed(this, RECHECK_INTERVAL_MS)
            maybeDismissForeign()
            sync()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AlarmRuntime.accessibilityActive = true
        handler.removeCallbacks(recheck)
        handler.post(recheck)
        Log.i(TAG, "служба подключена, беру возврат экрана на себя")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastForegroundPackage = event.packageName?.toString()
            lastForegroundClass = event.className?.toString()
        }
        maybeDismissForeign()
        sync()
    }

    /**
     * Sleep Cycle зазвонил — смахиваем его «Стоп» за владельца.
     *
     * Свайп годится, только когда его будильничный экран действительно на переднем
     * плане: иначе жест уйдёт мимо. После смахивания Sleep Cycle снимет уведомление, и
     * наш экран поднимет уже [SleepCycleListener]. Пока чужой не погас, он звонит снова
     * и снова — [SleepCycleListener] заново взводит флаг, а мы повторяем жест с паузой.
     */
    private fun maybeDismissForeign() {
        if (!AlarmRuntime.foreignDismissRequested) return
        // Наша тревога уже идёт — чужой погашен, смахивать нечего.
        if (AlarmRuntime.alarmActive) {
            AlarmRuntime.foreignDismissRequested = false
            return
        }
        if (lastForegroundPackage != FOREIGN_PACKAGE) return
        if (lastForegroundClass?.contains(FOREIGN_ALARM_ACTIVITY) != true) return

        val now = SystemClock.uptimeMillis()
        if (now - lastForeignSwipeAt < FOREIGN_SWIPE_COOLDOWN_MS) return
        lastForeignSwipeAt = now
        // Флаг НЕ гасим: один свайп мог уйти мимо — например экран Sleep Cycle ещё
        // не дорисовался. Пока он звонит, пробуем снова каждые
        // FOREIGN_SWIPE_COOLDOWN_MS; снимает флаг тот, кто увидел конец звонка.
        swipeUpToDismissForeign()
    }

    /** Вертикальный свайп снизу вверх по центру — «Стоп» на экране Sleep Cycle. */
    private fun swipeUpToDismissForeign() {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, metrics.heightPixels * SWIPE_START_FRACTION)
            lineTo(x, metrics.heightPixels * SWIPE_END_FRACTION)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        Log.i(TAG, "смахиваю «Стоп» Sleep Cycle за владельца")
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "служба отключена")
        AlarmRuntime.accessibilityActive = false
        handler.removeCallbacksAndMessages(null)
        navBlocker.hide()
        hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AlarmRuntime.accessibilityActive = false
        handler.removeCallbacksAndMessages(null)
        navBlocker.hide()
        hide()
        super.onDestroy()
    }

    /** Один вопрос: должен ли экран тревоги быть сейчас на виду — и есть ли он там. */
    private fun sync() {
        if (!AlarmRuntime.alarmActive) {
            navBlocker.hide()
            hide()
            return
        }
        // Кнопки навигации закрываем на всё время тревоги, независимо от того,
        // на переднем плане экран или нет.
        navBlocker.show()
        if (AlarmActivity.isShowing) {
            hide()
            return
        }
        show()
    }

    private fun show() {
        if (overlay != null) return
        Log.w(TAG, "экран тревоги пропал — закрываю заслонкой немедленно")
        overlay = OverlayWindow(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        ).also { it.show() }
    }

    private fun hide() {
        overlay?.hide()
        overlay = null
    }

    private companion object {
        const val TAG = "AlarmGuardA11y"
        /**
         * Подстраховочная проверка. Событие о смене окна приходит и так мгновенно,
         * но экран умеют отобрать и без него — например погасив кнопкой питания.
         */
        const val RECHECK_INTERVAL_MS = 250L

        /** Чей будильничный экран смахиваем. */
        const val FOREIGN_PACKAGE = "com.northcube.sleepcycle"
        const val FOREIGN_ALARM_ACTIVITY = "AlarmActivity"

        /** Не смахивать чаще: если один жест не погасил, второй пойдёт с паузой. */
        const val FOREIGN_SWIPE_COOLDOWN_MS = 2_500L

        /** Свайп снизу вверх: от 82% высоты к 20%, за 300 мс — уверенное движение. */
        const val SWIPE_START_FRACTION = 0.82f
        const val SWIPE_END_FRACTION = 0.20f
        const val SWIPE_DURATION_MS = 300L
    }
}
