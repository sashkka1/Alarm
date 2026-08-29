package com.sasha.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sasha.alarm.ui.AlarmColors
import com.sasha.alarm.ui.AlarmTheme

/**
 * Заслонка на случай, когда экран тревоги ушёл с переднего плана.
 *
 * Показывает **то же самое испытание**, что и активити, а не заглушку с надписью
 * (решение владельца 2026-08-16). Раньше здесь были только часы и строка «выполните
 * задание»: стоило системе задержать возврат активити — и человек оставался перед
 * экраном, на котором нечего нажать, то есть выключить будильник было нельзя
 * вообще, только дождаться дедлайна.
 *
 * Состояние общее ([AlarmRuntime]), поэтому решение продолжается с того же места,
 * а не начинается заново.
 *
 * ⚠️ Compose в окне без активити требует подпорок: своего владельца жизненного
 * цикла и хранилища состояния — иначе `ComposeView` откажется рисовать. Они здесь
 * же, в [OverlayViewOwner].
 */
class OverlayWindow(
    private val context: Context,
    /**
     * `TYPE_APPLICATION_OVERLAY` доступен всем, но система прячет такие окна на
     * некоторых своих экранах. `TYPE_ACCESSIBILITY_OVERLAY` этого недостатка лишён,
     * но добавить окно такого типа может только служба специальных возможностей.
     */
    private val windowType: Int,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var owner: OverlayViewOwner? = null

    fun show() {
        if (root != null) return
        val view = try {
            buildChallengeView()
        } catch (e: Exception) {
            // Тишина и пустота хуже любой заглушки (P0 №7): не собрался Compose —
            // закрываем экран хотя бы ровным листом, чтобы уйти всё равно не вышло.
            Log.e(TAG, "испытание на заслонке не собралось, кладу ровный лист", e)
            buildBlankView()
        }
        try {
            windowManager.addView(view, buildParams())
            root = view
            Log.i(TAG, "заслонка показана")
        } catch (e: Exception) {
            releaseOwner()
            Log.e(TAG, "заслонка не показалась", e)
        }
    }

    fun hide() {
        val view = root ?: return
        root = null
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "заслонка не снялась", e)
        }
        releaseOwner()
    }

    /** Тот же состав, что и в активити: часы, задание, клавиатура, кружки. */
    private fun buildChallengeView(): View {
        // Если активити так и не поднялась, маршрут меток никто не начинал, и заслонка
        // показала бы примеры вместо испытания метками — то есть чужой способ выхода.
        NfcRuntime.begin(AlarmController.state(context))

        val treeOwner = OverlayViewOwner().apply { start() }
        owner = treeOwner
        // Compose тянет цвета и размеры из темы; у контекста службы её может не быть.
        val themed = ContextThemeWrapper(context, R.style.Theme_Alarm)
        return ComposeView(themed).apply {
            // Тот же красный, что и на самом экране тревоги: подложка видна ровно в те
            // мгновения, пока Compose не нарисовал первый кадр, и белой ей быть нельзя —
            // белый лист читается как поломка, а не как будильник (владелец, 2026-08-25).
            setBackgroundColor(AlarmColors.Signal.toArgb())
            setViewTreeLifecycleOwner(treeOwner)
            setViewTreeSavedStateRegistryOwner(treeOwner)
            setContent {
                AlarmTheme {
                    AlarmContent(
                        // Камеру держит активити: два владельца одной камеры —
                        // это отказ у обоих.
                        cameraContent = {},
                        onExit = { AlarmController.dismiss(context, AlarmController.REASON_EXIT) },
                    )
                }
            }
        }
    }

    private fun buildBlankView(): View =
        FrameLayout(context).apply { setBackgroundColor(AlarmColors.Signal.toArgb()) }

    private fun releaseOwner() {
        owner?.stop()
        owner = null
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            screenBrightness = 1f
        }

    private companion object {
        const val TAG = "OverlayWindow"
    }
}

/**
 * Минимальный владелец жизненного цикла для окна поверх всего.
 *
 * `ComposeView` отказывается рисовать, пока у вью нет владельца жизненного цикла и
 * хранилища состояния — в активити их даёт сама активити, а у окна, добавленного
 * через `WindowManager`, взять их неоткуда. Здесь ровно столько, сколько требует
 * Compose: окно живо — состояние «на виду», окно снято — «уничтожено».
 */
private class OverlayViewOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
