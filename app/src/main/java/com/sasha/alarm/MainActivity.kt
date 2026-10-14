package com.sasha.alarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.HookHealth
import com.sasha.alarm.core.MelodySource
import com.sasha.alarm.core.SyncHealth
import com.sasha.alarm.platform.AudioOutputs
import com.sasha.alarm.platform.LogSender
import com.sasha.alarm.platform.MelodyStore
import com.sasha.alarm.platform.SyncStateStore
import com.sasha.alarm.platform.PermissionId
import com.sasha.alarm.platform.PermissionStatus
import com.sasha.alarm.platform.Permissions
import com.sasha.alarm.platform.PhoneSteps
import com.sasha.alarm.ui.AlarmTheme
import com.sasha.alarm.ui.AppTheme
import com.sasha.alarm.ui.PermissionRow
import com.sasha.alarm.ui.SettingsActions
import com.sasha.alarm.ui.SettingsScreen
import com.sasha.alarm.ui.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sasha.alarm.ui.R as UiR

/**
 * Единственный экран приложения — настройки.
 *
 * Главного экрана нет намеренно (решение владельца 2026-08-14): он существовал ради
 * собственного будильника, а будильника больше нет. Всё, что делает приложение, —
 * ждёт выключения чужого будильника (Sleep Cycle или стандартного) и поднимает свой экран,
 * а настраивать нужно только это.
 */
class MainActivity : ComponentActivity() {

    /** Счётчик возвратов на экран: разрешения могли выдать в системных настройках. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Чистая установка не проходит через BOOT_COMPLETED, поэтому ежедневную передачу
        // журнала ставим и отсюда. Повторный вызов просто переставляет тот же таймер.
        SyncReceiver.schedule(this)
        // Проверку зацепа — тем же способом: после установки её иначе никто не поставит.
        HookWatch.schedule(this)
        setContent {
            var current by remember { mutableStateOf(ThemePrefs.current(this)) }
            SideEffect {
                paintWindow(current)
                rememberSplash(current)
            }
            AlarmTheme(current) {
                AppRoot(resumeTick) { picked ->
                    current = picked
                    ThemePrefs.save(this, picked)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onStop() {
        super.onStop()
        // Значок меняем, когда экран ушёл, и не во время тревоги: смена может перезапустить процесс.
        if (!AlarmRuntime.alarmActive) LauncherIcon.sync(this, ThemePrefs.id(this))
    }

    /**
     * Загрузочный экран рисует система до запуска приложения, по стилю активности, — сам он темы не знает.
     * С Android 13 система запоминает стиль загрузочного экрана на следующие запуски: отдаём ей стиль темы.
     */
    private fun rememberSplash(t: AppTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) splashScreen.setSplashScreenTheme(ThemeSplash.style(t.id))
    }
}

@Composable
private fun AppRoot(resumeTick: Int, onTheme: (AppTheme) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(AlarmState.DEFAULT) }
    var permissions by remember { mutableStateOf(emptyList<PermissionStatus>()) }
    var headphones by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // Передача журнала на компьютер. Состояние живёт на экране, а не на диске: это
    // сообщение о последнем нажатии, а не настройка.
    var syncCaption by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendingText = stringResource(UiR.string.sync_sending)
    val describe: (LogSender.Result) -> String = { result ->
        when (result) {
            is LogSender.Result.Sent ->
                context.getString(UiR.string.sync_done, result.lines, result.accepted, result.host)

            LogSender.Result.NothingToSend -> context.getString(UiR.string.sync_empty)
            is LogSender.Result.NotFound -> context.getString(UiR.string.sync_not_found)
            is LogSender.Result.Failed -> context.getString(UiR.string.sync_failed, result.reason)
        }
    }

    // Плашка «журнал не уехал». Судит ядро (`SyncHealth`), здесь только текст.
    var syncWarning by remember { mutableStateOf("") }

    // Плашка «зацеп не работает» (владелец, 2026-09-30). Открытие приложения — ещё и
    // проверка с переподключением: владелец открывает его, когда что-то не так.
    var hook by remember { mutableStateOf<HookHealth.Status?>(null) }

    LaunchedEffect(resumeTick, reloadTick) {
        state = withContext(Dispatchers.IO) { AlarmController.state(context) }
        permissions = withContext(Dispatchers.IO) {
            Permissions.all(
                context = context,
                manual = state.manualPermissions,
                needsCamera = state.challenge == Challenge.PUSHUPS,
                needsNfc = state.challenge == Challenge.NFC,
            )
        }
        headphones = withContext(Dispatchers.IO) { AudioOutputs.headphonesConnected(context) }

        // ⚠️ Пересчитывается на каждый возврат на экран, а не один раз при запуске:
        // владелец открывает приложение именно затем, чтобы проверить, дошло ли.
        val sync = withContext(Dispatchers.IO) { SyncStateStore(context).read() }
        val now = System.currentTimeMillis()
        syncWarning = when (SyncHealth.verdict(sync, now)) {
            SyncHealth.Verdict.FINE -> ""
            SyncHealth.Verdict.FAILED -> context.getString(UiR.string.sync_warn_failed)
            SyncHealth.Verdict.STALE ->
                context.getString(UiR.string.sync_warn_stale, SyncHealth.hoursSinceOk(sync, now) ?: 0L)
        }

        // Последним: переподключение ждёт до восьми секунд, остальное экрану нужно раньше.
        hook = withContext(Dispatchers.IO) { HookWatch.run(context) }
    }

    val mutate: ((AlarmState) -> AlarmState) -> Unit = { block ->
        scope.launch {
            withContext(Dispatchers.IO) { AlarmController.store(context).update(block) }
            reloadTick++
        }
    }

    val melodyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) { MelodyStore(context).save(uri) }
            if (stored != null) mutate { it.copy(sound = it.sound.copy(melody = stored)) }
        }
    }

    // Камера — разрешение времени выполнения: системный экран его не выдаёт, нужен
    // диалог. Именно на этом молча сломался прежний счётчик шагов.
    val cameraRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { reloadTick++ }

    // Шаги Xiaomi — провайдер требует «Физическую активность», а она только диалогом.
    val stepsRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { reloadTick++ }

    SettingsScreen(
        state = SettingsUiState(
            masterEnabled = state.masterEnabled,
            challenge = state.challenge,
            math = state.math,
            reaction = state.reaction,
            pushups = state.pushups,
            nfcRouteSteps = state.nfc.route.size,
            sound = state.sound,
            melodyName = melodyName(state.sound.melody),
            headphonesConnected = headphones,
            failSafeMinutes = state.failSafeMinutes,
            resumeDelaySeconds = state.resumeDelaySeconds,
            permissions = permissionRows(permissions, state.manualPermissions),
            syncCaption = syncCaption,
            syncWarning = syncWarning,
            hook = hook,
        ),
        actions = SettingsActions(
            onMasterChange = { enabled -> mutate { it.copy(masterEnabled = enabled) } },
            onChallengeChange = { challenge -> mutate { it.copy(challenge = challenge) } },
            onReactionSecondsChange = { seconds ->
                mutate { it.copy(reaction = it.reaction.copy(perfectSeconds = seconds)) }
            },
            onPushupCountChange = { count ->
                mutate { it.copy(pushups = it.pushups.copy(count = count)) }
            },
            onPushupOverlayChange = { overlay ->
                mutate { it.copy(pushups = it.pushups.copy(overlay = overlay)) }
            },
            onPushupModelChange = { model ->
                mutate { it.copy(pushups = it.pushups.copy(model = model)) }
            },
            onMathRangeChange = { min, max ->
                mutate { it.copy(math = it.math.copy(min = min, max = max)) }
            },
            onOperationChange = { operation, on ->
                mutate {
                    val ops = it.math.operations
                    val updated = if (on) ops + operation else ops - operation
                    // Хотя бы одно действие обязано остаться — иначе примеры не из чего строить.
                    it.copy(math = it.math.copy(operations = updated.ifEmpty { ops }))
                }
            },
            onMathCountChange = { count -> mutate { it.copy(math = it.math.copy(count = count)) } },
            onSoundChange = { updated -> mutate { it.copy(sound = updated) } },
            onPickMelody = { melodyPicker.launch(arrayOf("audio/*")) },
            onFailSafeChange = { minutes -> mutate { it.copy(failSafeMinutes = minutes) } },
            onResumeDelayChange = { seconds -> mutate { it.copy(resumeDelaySeconds = seconds) } },
            onOpenAppSettings = { Permissions.openAppDetails(context) },
            onOpenPermission = { key ->
                val id = PermissionId.valueOf(key)
                if (id == PermissionId.CAMERA) {
                    cameraRequest.launch(android.Manifest.permission.CAMERA)
                } else if (id == PermissionId.STEPS && !Permissions.activityAllowed(context)) {
                    stepsRequest.launch(PhoneSteps.ACTIVITY)
                } else {
                    Permissions.open(context, id)
                }
            },
            onToggleManual = { key ->
                mutate {
                    val manual = it.manualPermissions
                    it.copy(manualPermissions = if (key in manual) manual - key else manual + key)
                }
            },
            onTest = { AlarmController.scheduleTest(context, TEST_DELAY_MS) },
            onPickTheme = onTheme,
            onSendLog = {
                if (!sending) {
                    sending = true
                    syncCaption = sendingText
                    scope.launch {
                        // ⚠️ Тот же путь, что у ежедневной задачи: снять снимок пользования,
                        // дождаться записи, отдать. Кнопка, которая только отправляет
                        // лежащее, молча теряла бы всё сегодняшнее (владелец, 2026-08-29).
                        val result = withContext(Dispatchers.IO) { SyncReceiver.collectAndSend(context) }
                        syncCaption = describe(result)
                        sending = false
                        // Плашка обязана погаснуть сразу после удачной передачи, а не
                        // ждать следующего захода на экран: иначе она врёт о только что
                        // исправленном.
                        reloadTick++
                    }
                }
            },
        ),
        // Метки рисует :app: считыватель работает только на активити, а она здесь.
        nfcContent = { NfcSetupSheet(onChanged = { reloadTick++ }) },
    )
}

@Composable
private fun melodyName(melody: MelodySource): String = when (melody) {
    is MelodySource.SystemAlarm -> stringResource(UiR.string.sound_melody_system)
    is MelodySource.Stored -> melody.displayName
}

@Composable
private fun permissionRows(
    statuses: List<PermissionStatus>,
    manual: Set<String>,
): List<PermissionRow> =
    statuses.map { status ->
        val titleRes = when (status.id) {
            PermissionId.NOTIFICATIONS -> UiR.string.perm_notifications_title
            PermissionId.NOTIFICATION_LISTENER -> UiR.string.perm_notification_listener_title
            PermissionId.ACCESSIBILITY -> UiR.string.perm_accessibility_title
            PermissionId.FULL_SCREEN_INTENT -> UiR.string.perm_full_screen_title
            PermissionId.OVERLAY -> UiR.string.perm_overlay_title
            PermissionId.EXACT_ALARM -> UiR.string.perm_exact_alarm_title
            PermissionId.BATTERY -> UiR.string.perm_battery_title
            PermissionId.CAMERA -> UiR.string.perm_camera_title
            PermissionId.NFC -> UiR.string.perm_nfc_title
            PermissionId.USAGE_STATS -> UiR.string.perm_usage_stats_title
            PermissionId.STEPS -> UiR.string.perm_steps_title
            PermissionId.XIAOMI_AUTOSTART -> UiR.string.perm_xiaomi_autostart_title
            PermissionId.XIAOMI_BACKGROUND_POPUP -> UiR.string.perm_xiaomi_popup_title
        }
        PermissionRow(
            key = status.id.name,
            title = stringResource(titleRes),
            // Отметка владельца поверх того, что видит система. Разрешение, которое
            // система подтверждает, погасить нельзя — иначе экран состояния врал бы
            // ровно там, где обязан не врать (P0 №8).
            granted = status.granted || status.id.name in manual,
        )
    }

/** Без паузы: будильник, поставленный на «сейчас», срабатывает немедленно. */
private const val TEST_DELAY_MS = 0L
