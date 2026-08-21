package com.sasha.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Заглушка поверх системных кнопок «Назад», «Домой» и «Недавние».
 *
 * Отключить эти кнопки не даёт ни один публичный API — но окно службы специальных
 * возможностей лежит **выше** панели навигации в порядке наложения, поэтому нажатие
 * достаётся ему, а не системе. Кнопки видно, они просто перестают работать: палец
 * попадает в прозрачную заглушку, которая съедает касание и ничего не делает.
 *
 * ⚠️ Работает **только** из службы специальных возможностей. Обычный оверлей
 * (`TYPE_APPLICATION_OVERLAY`) лежит НИЖЕ панели навигации, и перехватить нажатие
 * по кнопкам ему нечем — поэтому фолбэка здесь нет: не выдано разрешение, значит
 * остаётся прежний слой «ушёл — вернули».
 *
 * Заглушка занимает ровно ту полоску, которую система отвела под панель навигации,
 * то есть ровно ту, куда экран тревоги и так не рисует (`safeDrawing`). Своя
 * клавиатура из-за неё не страдает.
 */
class NavBarBlocker(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        try {
            val blocker = object : View(context) {
                override fun onTouchEvent(event: MotionEvent?): Boolean = true
            }
            val height = navBarHeight()
            windowManager.addView(blocker, buildParams(height))
            view = blocker
            Log.i(TAG, "кнопки навигации закрыты заглушкой, высота $height")
        } catch (e: Exception) {
            // Блокировка важна, но тревога важнее: не вышло — экран всё равно идёт.
            Log.w(TAG, "не удалось закрыть кнопки навигации", e)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeView(current)
            Log.i(TAG, "заглушка кнопок снята")
        } catch (e: Exception) {
            Log.w(TAG, "заглушка кнопок не снялась", e)
        }
    }

    private fun buildParams(height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Не забираем фокус: экран тревоги должен продолжать получать нажатия
            // клавиш и не терять курсор ввода. Касания при этом заглушка получает.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            // Иначе система сама отодвинет окно от панели навигации — ровно от того,
            // что мы собираемся закрыть.
            setFitInsetsTypes(0)
        }

    /**
     * Высота полоски навигации.
     *
     * Берём ту, что система отвела панели прямо сейчас. Ноль означает, что панели
     * нет вовсе (жесты со скрытой полоской) — тогда закрываем нижнюю кромку на
     * глазок, иначе жест «домой» останется живым.
     */
    private fun navBarHeight(): Int {
        val fromInsets = try {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.navigationBars()).bottom
        } catch (e: Exception) {
            Log.w(TAG, "высота панели навигации не прочиталась", e)
            0
        }
        if (fromInsets > 0) return fromInsets
        return (FALLBACK_DP * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val TAG = "NavBarBlocker"
        const val FALLBACK_DP = 32
    }
}
