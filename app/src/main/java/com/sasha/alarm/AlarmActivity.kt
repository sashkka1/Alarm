package com.sasha.alarm

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.VictoryStats
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner
import com.sasha.alarm.platform.NfcReader
import com.sasha.alarm.ui.AlarmTheme
import com.sasha.alarm.ui.VictoryScreen

/**
 * Экран тревоги.
 *
 * Основной путь показа. Запасной — [OverlayWindow], который поднимает [AlarmService],
 * если эта активити не появилась за отведённое время.
 */
class AlarmActivity : ComponentActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "пришла команда закрыть экран")
            releaseLock()

            // Победа — единственная причина остаться: испытание выполнено, тревога
            // снята, и вместо закрытия показывается итог. Снятие сторожем по дедлайну
            // итога не оставляет, поэтому экран просто закрывается.
            val stats = AlarmRuntime.victory
            if (stats == null) {
                finish()
                return
            }
            Log.i(TAG, "испытание пройдено — показываю итог")
            victory = stats
            relaxWindow()
        }
    }

    private var locked = false

    private val nfcReader by lazy { NfcReader(this) }

    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())

    /** Повторная просьба снять замок, если прошлую отменили. */
    private val retryUnlock = Runnable { requestUnlockForNfc() }

    /** Итог пройденного испытания. Не null — на экране победа, а не тревога. */
    private var victory by mutableStateOf<VictoryStats?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        // Кнопка «Назад» тревогу не выключает: выйти можно только кнопкой на экране
        // или по бэкапу. Свернуть экран в версии 1 всё ещё можно — блокировка в v4.
        // На экране победы держать уже нечего, и «Назад» работает как «Выйти».
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (victory != null) finish()
            }
        })

        registerReceiver(
            dismissReceiver,
            IntentFilter(ACTION_DISMISS),
            Context.RECEIVER_NOT_EXPORTED,
        )

        // Маршрут меток берётся с диска, а не из сервиса: экран пересоздают (сторож
        // возвращает его обратно, если с него ушли), и пройденное при этом не теряется.
        NfcRuntime.begin(AlarmController.state(this))

        setContent {
            AlarmTheme {
                victory?.let { stats ->
                    VictoryScreen(stats = stats, onExit = { finish() })
                    return@AlarmTheme
                }

                // Тот же состав рисует заслонка-оверлей, когда экран у нас отобрали,
                // поэтому он живёт одним куском в [AlarmContent].
                AlarmContent(
                    cameraContent = { PushupCamera(Modifier.fillMaxSize()) },
                    onExit = {
                        releaseLock()
                        AlarmController.dismiss(this@AlarmActivity)
                        finish()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        engageLock()
    }

    /**
     * Считыватель меток включается только на переднем плане — так требует reader mode.
     *
     * ⚠️ Экран тревоги висит поверх локскрина, а метки система читает не всегда,
     * пока телефон заперт. Если окажется, что не читает — придётся снимать замок
     * перед испытанием; проверяется только на живом телефоне.
     */
    override fun onResume() {
        super.onResume()
        isShowing = true
        if (NfcRuntime.run != null) {
            nfcReader.start { id -> onNfcTag(id) }
            requestUnlockForNfc()
        }
    }

    /**
     * Снять замок — без этого метки не прочитать вовсе.
     *
     * ⚠️ Проверено на телефоне 2026-08-19: пока экран заперт, система держит
     * `mScreenState=ON_LOCKED` и **не включает reader mode** (`mEnableReader=false`),
     * даже когда наш запрос принят. То есть на запертом телефоне метка не читается
     * ни за десять секунд, ни за полторы минуты — это ограничение Android, а не наш
     * баг: старая сборка 1.27 вела себя точно так же. Работало оно только тогда,
     * когда владелец запускал проверку с уже разблокированного телефона.
     *
     * ⚠️ Сами разблокировать не можем: защищённый замок (PIN, отпечаток) не снимает
     * ни одно приложение — запрет Android для всех, включая владельца устройства.
     * Поэтому просим систему, а она спрашивает палец. Если замка нет или сработала
     * умная разблокировка, снимется молча, без единого действия.
     *
     * Звук, экран и блокировка от этого не меняются: тревога звенит как звенела, а
     * снятый замок не даёт уйти с экрана — его держат оверлей и служба спецвозможностей.
     */
    private fun requestUnlockForNfc() {
        handler.removeCallbacks(retryUnlock)
        if (NfcRuntime.run == null) return
        val manager = keyguard ?: return
        if (!manager.isKeyguardLocked) return

        Log.i(TAG, "испытание метками — прошу снять замок")
        manager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                // Считыватель перезапускаем: пока экран был заперт, система его
                // запрос не применяла, и без перезапуска он мог остаться мёртвым.
                Log.i(TAG, "замок снят — перезапускаю считыватель меток")
                nfcReader.stop()
                nfcReader.start { id -> onNfcTag(id) }
            }

            override fun onDismissCancelled() {
                // Отказ не выход: без замка метки не прочитать, а значит тревогу не
                // снять. Спрашиваем снова, пока идёт испытание.
                Log.w(TAG, "разблокировку отменили — попрошу снова")
                handler.postDelayed(retryUnlock, UNLOCK_RETRY_MS)
            }

            override fun onDismissError() {
                Log.e(TAG, "разблокировка не удалась — попрошу снова")
                handler.postDelayed(retryUnlock, UNLOCK_RETRY_MS)
            }
        })
    }

    /**
     * ⚠️ «Экран у нас отобрали» отмечается здесь, а не в `onStop` (2026-08-16).
     * `onStop` система зовёт только после того, как переход доиграет — почти секунда,
     * и всё это время сторож считал, что экран на месте, и ничего не делал. `onPause`
     * приходит сразу: и когда ушли на другой экран, и когда погасили кнопкой питания.
     */
    override fun onPause() {
        isShowing = false
        handler.removeCallbacks(retryUnlock)
        nfcReader.stop()
        super.onPause()
    }

    /**
     * Метка приложена. Маршрут пройден целиком — тревога снимается.
     *
     * Итог собирается здесь, а не в сервисе, как у остальных испытаний: считыватель
     * живёт на активити, и момент победы известен только ей.
     */
    private fun onNfcTag(id: String) {
        if (!NfcRuntime.onTag(id, AndroidClock.nowMillis())) return
        Log.i(TAG, "маршрут меток пройден — снимаю тревогу")
        AlarmRuntime.victory = VictoryStats(
            challenge = Challenge.NFC,
            startedAtMillis = NfcRuntime.startedAtMillis,
            finishedAtMillis = AndroidClock.nowMillis(),
            nfcSteps = NfcRuntime.run?.total ?: 0,
        )
        AlarmController.dismiss(this)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseLock()
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    /**
     * Мягкая блокировка: Home, Recents и Back перестают уводить с экрана.
     *
     * **Включается только если сторож уже запланирован** (P0 №5): признак этого —
     * непустой `run` в состоянии. Нет сторожа — нет и блокировки, иначе телефон
     * можно запереть без единого способа выйти.
     *
     * ⚠️ **Только с правами владельца устройства** (решение владельца 2026-08-16).
     * Без них `startLockTask()` включает системное «Закрепление экрана», а оно
     * каждый раз показывает поверх нашего экрана сообщение «приложение закреплено,
     * чтобы открепить...». Спрятать это сообщение нечем — его рисует система, — а
     * убрать можно только вместе с вызовом, который его вызывает.
     *
     * Терять при этом нечего: на HyperOS 3 закрепления экрана нет вовсе (проверено
     * 2026-08-14, `lock_to_app_enabled` = null), и вызов не закреплял ничего. Держат
     * экран другие слои — оверлей сервиса и служба специальных возможностей.
     * С правами владельца устройства `startLockTask()` работает молча, и там он нужен.
     */
    private fun engageLock() {
        if (locked) return
        val state = AlarmController.state(this)
        if (state.run == null) {
            Log.w(TAG, "тревога не запущена — блокировку не включаю")
            return
        }
        if (!DeviceOwner.isActive(this)) {
            Log.i(TAG, "прав владельца устройства нет — закрепление пропускаю, держим оверлеем")
            return
        }
        try {
            startLockTask()
            locked = true
            val manager = getSystemService(ActivityManager::class.java)
            Log.i(TAG, "блокировка включена, режим=${manager.lockTaskModeState}")
        } catch (e: Exception) {
            // Тревога важнее блокировки: не вышло — экран всё равно показываем.
            Log.e(TAG, "блокировка не включилась", e)
        }
    }

    /**
     * Отпустить окно после победы.
     *
     * Экран тревоги обязан гореть на полной яркости и не гаснуть; итогу это уже не
     * нужно, а висеть он может сколько угодно — держать подсветку всё это время
     * незачем.
     */
    private fun relaxWindow() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun releaseLock() {
        if (!locked) return
        locked = false
        try {
            stopLockTask()
            Log.i(TAG, "блокировка снята")
        } catch (e: Exception) {
            Log.e(TAG, "блокировка не снялась", e)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.sasha.alarm.DISMISS"
        private const val TAG = "AlarmActivity"

        /** Через сколько просить снять замок заново, если прошлую просьбу отклонили. */
        private const val UNLOCK_RETRY_MS = 10_000L

        /** Читается сервисом из того же процесса, чтобы решить, нужен ли оверлей. */
        @Volatile
        var isShowing: Boolean = false
            private set
    }
}
