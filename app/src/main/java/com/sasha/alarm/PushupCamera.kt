package com.sasha.alarm

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.sasha.alarm.platform.CameraPoseSource
import com.sasha.alarm.platform.Permissions

/**
 * Картинка с камеры для испытания отжиманиями.
 *
 * Живёт в `:app`, а не в `:ui`, ровно потому, что связывает два слоя: показывает
 * системное вью камеры и подсовывает разобранные кадры сервису через [AlarmRuntime].
 *
 * Камера привязана к жизненному циклу экрана: экран ушёл — камера погасла сама,
 * даже если что-то забыли остановить руками.
 */
@Composable
fun PushupCamera(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Без разрешения камера не откроется, а испытание станет невыполнимым.
    // Молчать нельзя: пусть в журнале останется причина (P0 №7).
    val allowed = remember { Permissions.cameraAllowed(context) }
    if (!allowed) {
        Log.e(TAG, "нет разрешения на камеру — отжимания посчитать нечем")
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            // Картинка вписывается целиком, поэтому сверху и снизу остаются полосы.
            // По умолчанию вью заливает их чёрным, и экран тревоги выглядел чёрным,
            // а не красным. Прозрачный фон пускает в полосы цвет экрана.
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    // Модель — часть ключа: сменил её в настройках, камера поднимается заново с
    // другим файлом. Иначе выбор доехал бы только до следующего запуска тревоги.
    val model = AlarmRuntime.pushupModel
    DisposableEffect(previewView, model) {
        val source = CameraPoseSource(context, lifecycleOwner, previewView, model)
        source.start { frame -> AlarmRuntime.onPoseFrame(frame) }
        onDispose { source.stop() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val TAG = "PushupCamera"
