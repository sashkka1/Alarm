package com.sasha.alarm.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.sasha.alarm.core.PoseFrame
import com.sasha.alarm.core.PoseLandmarks
import com.sasha.alarm.core.PoseModel
import com.sasha.alarm.core.PosePoint
import com.sasha.alarm.core.PoseSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Камера + MediaPipe Pose Landmarker — адаптер порта [PoseSource].
 *
 * Модель лежит в ассетах целиком (~30 МБ) и работает без сети: скачивать её в
 * шесть утра было бы ровно тем отказом, который приложение обязано исключать.
 *
 * Режим [RunningMode.LIVE_STREAM], а не VIDEO: кадры приходят с камеры в реальном
 * времени, и распознавание не должно держать поток анализа. Отставшие кадры
 * CameraX выбрасывает сам ([ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST]) — для счёта
 * отжиманий это правильно: считается движение, а не каждый его кадр.
 */
class CameraPoseSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    /**
     * Куда показывать картинку. null — считать вслепую.
     *
     * Превью здесь не украшение: испытание отказывает ровно тогда, когда человек
     * не попал в кадр целиком, и без картинки он этого не увидит.
     */
    private val previewView: PreviewView? = null,
    /** Какой моделью распознавать позу. Переключается в настройках отжиманий. */
    private val model: PoseModel = PoseModel.FULL,
) : PoseSource {

    private var landmarker: PoseLandmarker? = null
    private var provider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null

    @Volatile
    private var onFrame: ((PoseFrame?) -> Unit)? = null


    /**
     * Пропорции кадра, который ушёл в модель, — уже с учётом поворота.
     *
     * Нужны экрану: точки нормированы по этому кадру, и без пропорций скелет
     * не лечь на картинку. Ставится в потоке анализа, читается в потоке ответа.
     */
    @Volatile
    private var frameAspect: Float = 1f

    override fun start(onFrame: (PoseFrame?) -> Unit) {
        this.onFrame = onFrame
        val worker = Executors.newSingleThreadExecutor()
        executor = worker

        landmarker = createLandmarker() ?: run {
            // Модель не поднялась — испытание невыполнимо. Молчать нельзя:
            // экран обязан узнать об этом и уйти в запасной путь (P0 №7).
            Log.e(TAG, "Pose Landmarker не создался")
            onFrame(null)
            return
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                // Одни и те же пропорции у картинки и у анализа — обязательны.
                // CameraX волен выбрать разные, и тогда скелет ляжет мимо тела:
                // точки нормированы по кадру анализа, а видно кадр превью.
                //
                // 16:9, а не 4:3: экран телефона узкий и длинный, и вписанный в него
                // квадратный кадр оставлял сверху и снизу широкие пустые полосы —
                // почти половину экрана. Вытянутый кадр закрывает заметно больше.
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(resolution)
                    .build()
                analysis.setAnalyzer(worker, ::analyze)

                val preview = previewView?.let {
                    Preview.Builder()
                        .setResolutionSelector(resolution)
                        .build()
                        .apply { surfaceProvider = it.surfaceProvider }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    *listOfNotNull(analysis, preview).toTypedArray(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "камера не открылась", e)
                onFrame(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        onFrame = null
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "камера не отвязалась", e)
        }
        provider = null
        try {
            landmarker?.close()
        } catch (e: Exception) {
            Log.w(TAG, "модель не закрылась", e)
        }
        landmarker = null
        executor?.shutdown()
        executor = null
    }

    private fun createLandmarker(): PoseLandmarker? {
        // Сначала GPU: тяжёлая модель на процессоре даёт единицы кадров в секунду.
        // Не на всех прошивках делегат поднимается, поэтому за ним идёт CPU.
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val base = BaseOptions.builder()
                    .setModelAssetPath(model.asset())
                    .setDelegate(delegate)
                    .build()
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(MIN_DETECTION)
                    .setMinPosePresenceConfidence(MIN_PRESENCE)
                    .setMinTrackingConfidence(MIN_TRACKING)
                    // ⚠️ Маска сегментации выключена (владелец, 2026-08-16): обводка
                    // вокруг тела ему не понравилась, а считается она на каждом кадре
                    // и стоит заметной доли времени. Включается одной строкой обратно.
                    .setResultListener { result, _ -> publish(result) }
                    .setErrorListener { e -> Log.w(TAG, "кадр не разобрался", e) }
                    .build()
                return PoseLandmarker.createFromOptions(context, options)
            } catch (e: Exception) {
                Log.w(TAG, "делегат $delegate не подошёл", e)
            }
        }
        return null
    }

    private fun analyze(image: ImageProxy) {
        try {
            // Кадр поворачиваем сами, а не поручаем это модели через
            // ImageProcessingOptions. Так координаты точек заведомо нормированы по
            // тому же прямому кадру, что виден на экране: любое расхождение здесь
            // — это скелет, лежащий мимо тела, и объяснить его потом нечем.
            //
            // Поворот нужен обязательно: детектор человека обучен на кадрах
            // «головой вверх» и лежащий на боку кадр просто не разбирает.
            val upright = image.toBitmap().upright(image.imageInfo.rotationDegrees)
            frameAspect = upright.width.toFloat() / upright.height

            landmarker?.detectAsync(
                BitmapImageBuilder(upright).build(),
                image.imageInfo.timestamp / 1_000_000,
            )
        } catch (e: Exception) {
            Log.w(TAG, "кадр не обработался", e)
        } finally {
            image.close()
        }
    }

    /** Файл модели в ассетах. Все три лежат в приложении и переключаются в настройках. */
    private fun PoseModel.asset(): String = when (this) {
        PoseModel.LITE -> "pose_landmarker_lite.task"
        PoseModel.FULL -> "pose_landmarker_full.task"
        PoseModel.HEAVY -> "pose_landmarker_heavy.task"
    }

    /** Повернуть кадр так, чтобы верх был вверху. CameraX отдаёт поворот по часовой. */
    private fun Bitmap.upright(rotationDegrees: Int): Bitmap {
        val turn = ((rotationDegrees % 360) + 360) % 360
        if (turn == 0) return this
        val matrix = Matrix().apply { postRotate(turn.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun publish(result: PoseLandmarkerResult?) {
        val callback = onFrame ?: return
        val landmarks = result?.landmarks()?.firstOrNull()
        if (landmarks == null || landmarks.size < PoseLandmarks.COUNT) {
            callback(null)
            return
        }
        callback(PoseFrame(landmarks.map { it.toPoint() }, aspect = frameAspect))
    }

    private fun com.google.mediapipe.tasks.components.containers.NormalizedLandmark.toPoint() =
        PosePoint(
            x = x(),
            y = y(),
            // Глубина относительно таза: меньше — ближе к камере. Нужна экрану,
            // чтобы фигура выглядела объёмной, а не плоской.
            z = z(),
            // Только видимость. Второе число модели — presence — в замерах по
            // материалам владельца (2026-08-16) держалось около единицы даже на
            // кадрах, где скелет был выдуман целиком, и признаком не годится.
            confidence = visibility().orElse(0f),
        )

    private companion object {
        const val TAG = "CameraPoseSource"
        /**
         * Насколько уверенной должна быть модель, чтобы вообще отдать позу.
         *
         * Опущено с 0.5 до 0.25 (владелец, 2026-08-18). Детектор человека обучен на
         * кадрах с головой, и на обрезанном теле при высоком пороге он просто
         * отказывался находить позу — а в маленькой комнате телефон иначе и не
         * поставить. Ниже порог — соглашается на частичного человека.
         *
         * ⚠️ Проверку надёжности это не ослабляет: в счёт точка идёт по своей
         * видимости ([PushupCounter.MIN_CONFIDENCE] = 0.6), и она осталась прежней.
         */
        const val MIN_DETECTION = 0.25f
        const val MIN_PRESENCE = 0.25f
        const val MIN_TRACKING = 0.25f
    }
}
