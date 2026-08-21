package com.sasha.alarm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sasha.alarm.core.NfcRules
import com.sasha.alarm.core.NfcSettings
import com.sasha.alarm.platform.NfcReader
import com.sasha.alarm.ui.NfcSetupActions
import com.sasha.alarm.ui.NfcSetupScreen
import com.sasha.alarm.ui.NfcSetupUiState
import com.sasha.alarm.ui.NfcTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Метки и маршрут — содержимое шторки настроек.
 *
 * Прежде это была отдельная активити: считыватель NFC включается **на активити** и
 * только пока она на переднем плане. Отдельная для этого не нужна — главный экран
 * сам активити, и считыватель прекрасно живёт на нём (владелец, 2026-08-17).
 *
 * Считыватель работает всё время, пока шторка открыта, но метка запоминается только
 * после явного «плюса»: иначе случайное касание тумбочки завело бы метку молча.
 */
@Composable
fun NfcSetupSheet(
    /**
     * Маршрут или список меток изменились.
     *
     * Шторка пишет их прямо в хранилище, минуя состояние экрана настроек, и без
     * этого сигнала экран так и считал бы маршрут пустым: испытание метками
     * оставалось заблокированным сразу после того, как маршрут собрали.
     */
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    // ⚠️ Шторка живёт в отдельном окне, и её контекст — не сама активити, а обёртка
    // вокруг неё. Прямое приведение к Activity здесь не проходит, и содержимое
    // молча не рисовалось вовсе. Разворачиваем обёртки до настоящей активити.
    val activity = remember(context) { context.findActivity() }
    if (activity == null) {
        Log.e(TAG, "активити не нашлась — считыватель меток включить негде")
        return
    }
    val scope = rememberCoroutineScope()
    val reader = remember(activity) { NfcReader(activity) }

    var settings by remember { mutableStateOf(NfcSettings.DEFAULT) }
    var waiting by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf<NfcTouch?>(null) }
    var nfcEnabled by remember { mutableStateOf(reader.enabled) }

    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) { AlarmController.state(context).nfc }
    }

    fun mutate(block: (NfcSettings) -> NfcSettings) {
        scope.launch {
            settings = withContext(Dispatchers.IO) {
                AlarmController.store(context).update { it.copy(nfc = block(it.nfc)) }.nfc
            }
            onChanged()
        }
    }

    /**
     * Метка приложена.
     *
     * Знакомую метку второй раз не заводим, даже если ждали новую: номер у неё уже
     * есть. Всё остальное время касание отвечает на другой вопрос — «какая это из
     * наклеек»: они одинаковые, и опознать их можно только так.
     */
    fun onTag(id: String) {
        val known = settings.tags.firstOrNull { it.id == id }
        if (known != null) {
            waiting = false
            touched = NfcTouch.Known(known.number)
            return
        }
        if (!waiting) {
            touched = NfcTouch.Unknown
            return
        }
        waiting = false
        touched = NfcTouch.Added(NfcRules.nextNumber(settings))
        mutate { NfcRules.register(it, id) }
    }

    // Считыватель живёт ровно столько, сколько открыта шторка: закрылась — выключили,
    // иначе он остался бы перехватывать метки у всей системы.
    DisposableEffect(reader) {
        nfcEnabled = reader.enabled
        reader.start { id -> onTag(id) }
        onDispose { reader.stop() }
    }

    NfcSetupScreen(
        state = NfcSetupUiState(
            tags = settings.tags.map { it.number },
            route = settings.route,
            waiting = waiting,
            supported = reader.supported,
            enabled = nfcEnabled,
            touched = touched,
        ),
        actions = NfcSetupActions(
            onArm = { waiting = true; touched = null },
            onCancelArm = { waiting = false },
            onForget = { number -> mutate { NfcRules.forget(it, number) } },
            onAddStep = { number -> mutate { NfcRules.addStep(it, number) } },
            onRemoveLastStep = { mutate { NfcRules.removeLastStep(it) } },
            onClearRoute = { mutate { NfcRules.clearRoute(it) } },
            onOpenNfcSettings = { NfcReader.openSettings(context) },
        ),
    )
}

/** Развернуть обёртки контекста до активити. Окно шторки отдаёт именно обёртку. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val TAG = "NfcSetupSheet"
