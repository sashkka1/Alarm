package com.sasha.alarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val TILE = 44.dp
private val TILE_SHAPE = RoundedCornerShape(12.dp)
val GAP = 10.dp

/**
 * Плитка темы — квадрат со скруглёнными углами из двух основных цветов темы (фон и акцент) по диагонали.
 * Выбор темы целиком — в окне настроек (`SettingsPanel.kt`). Одинакова во всех приложениях владельца
 * (образец — DrozdRun, `Desktop\Схема переделки приложений.md`).
 */
@Composable
fun ThemeTile(open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TwoTone(theme, selected = open, modifier, Modifier.clickable(onClick = onClick))
}

/** Плитка темы: левый верхний треугольник — фон, правый нижний — акцент. */
@Composable
internal fun TwoTone(t: AppTheme, selected: Boolean, modifier: Modifier, gesture: Modifier) {
    Box(
        modifier
            .size(TILE)
            .clip(TILE_SHAPE)
            .border(if (selected) 2.dp else 1.dp, if (selected) theme.text else theme.sel, TILE_SHAPE)
            .then(gesture),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(t.bg)
            val half = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(half, t.accent)
        }
    }
}

/** Короткое нажатие — [onTap]; удержание дольше системного порога — [onHold], а по отпусканию [onRelease]. */
@Composable
internal fun Modifier.holdOrTap(onTap: () -> Unit, onHold: () -> Unit, onRelease: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val release by rememberUpdatedState(onRelease)
    return pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(onPress = {
                var held = false
                val timer = launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    held = true
                    hold()
                }
                val released = tryAwaitRelease()
                timer.cancel()
                when {
                    held -> release()
                    released -> tap()
                }
            })
        }
    }
}
