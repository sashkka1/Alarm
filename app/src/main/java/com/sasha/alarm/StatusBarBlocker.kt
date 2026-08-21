package com.sasha.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Полоска-заглушка поверх статус-бара на время тревоги.
 *
 * Полностью отключить шторку уведомлений может только Device Owner, а это v6.
 * До тех пор доступен единственный приём: закрыть верхнюю кромку экрана своим
 * окном, которое съедает жест «потянуть вниз». Из-под закреплённого экрана
 * шторку так не вытащить.
 *
 * ⚠️ Это ограничение, а не запрет: жест можно начать и вне полоски, если система
 * позволяет. Настоящий запрет появится с Device Owner.
 */
class StatusBarBlocker(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        try {
            val blocker = object : View(context) {
                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = true
            }
            windowManager.addView(blocker, buildParams())
            view = blocker
            Log.i(TAG, "шторка прикрыта")
        } catch (e: Exception) {
            Log.w(TAG, "не удалось прикрыть шторку", e)
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeView(current)
        } catch (e: Exception) {
            Log.w(TAG, "заглушка шторки не снялась", e)
        }
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }

    private fun statusBarHeight(): Int {
        val fromInsets = try {
            windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars()).top
        } catch (e: Exception) {
            Log.w(TAG, "высота статус-бара не прочиталась", e)
            0
        }
        val fallback = (FALLBACK_DP * context.resources.displayMetrics.density).toInt()
        return maxOf(fromInsets, fallback)
    }

    private companion object {
        const val TAG = "StatusBarBlocker"
        const val FALLBACK_DP = 48
    }
}
