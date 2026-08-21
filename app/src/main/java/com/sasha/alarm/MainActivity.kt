package com.sasha.alarm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sasha.alarm.core.MelodySource
import com.sasha.alarm.platform.AudioOutputs
import com.sasha.alarm.platform.MelodyStore
import com.sasha.alarm.platform.PermissionId
import com.sasha.alarm.platform.PermissionStatus
import com.sasha.alarm.platform.Permissions
import com.sasha.alarm.ui.AlarmTheme
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
 * ждёт выключения Sleep Cycle и поднимает свой экран, а настраивать нужно только это.
 */
class MainActivity : ComponentActivity() {

    /** Счётчик возвратов на экран: разрешения могли выдать в системных настройках. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlarmTheme { AppRoot(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun AppRoot(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(AlarmState.DEFAULT) }
    var permissions by remember { mutableStateOf(emptyList<PermissionStatus>()) }
    var headphones by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

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
