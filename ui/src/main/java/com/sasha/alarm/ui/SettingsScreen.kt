package com.sasha.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasha.alarm.core.AlarmState
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.FailSafe
import com.sasha.alarm.core.MathOperation
import com.sasha.alarm.core.MathSettings
import com.sasha.alarm.core.PoseModel
import com.sasha.alarm.core.PushupOverlay
import com.sasha.alarm.core.PushupSettings
import com.sasha.alarm.core.ReactionSettings
import com.sasha.alarm.core.SoundSettings
import kotlin.math.roundToInt

/**
 * Одна строка списка разрешений.
 *
 * Пояснения к строке нет намеренно (макет Claude Design, 2026-08-16): девять абзацев
 * мелким шрифтом никто не читает, а место они занимают всё.
 */
data class PermissionRow(
    val key: String,
    val title: String,
    val granted: Boolean,
)

data class SettingsUiState(
    val masterEnabled: Boolean,
    val challenge: Challenge,
    val math: MathSettings,
    val reaction: ReactionSettings,
    val pushups: PushupSettings,
    /** Сколько шагов в маршруте меток. Ноль — испытание метками невыполнимо. */
    val nfcRouteSteps: Int,
    val sound: SoundSettings,
    val melodyName: String,
    val headphonesConnected: Boolean,
    val failSafeMinutes: Int,
    val resumeDelaySeconds: Int,
    val permissions: List<PermissionRow>,
    /**
     * Что показывать про передачу журнала на компьютер: когда получилось в прошлый раз
     * либо что пошло не так. Пустая строка — журнал ещё ни разу не уезжал.
     */
    val syncCaption: String = "",
    /** Идёт ли запись ночи прямо сейчас. */
    val nightRecording: Boolean = false,
    /** Что показать под кнопкой записи: до какого часа пишем либо сколько ночей лежит. */
    val nightRecordingCaption: String = "",
)

data class SettingsActions(
    val onMasterChange: (Boolean) -> Unit,
    val onChallengeChange: (Challenge) -> Unit,
    val onOperationChange: (MathOperation, Boolean) -> Unit,
    val onMathCountChange: (Int) -> Unit,
    val onMathRangeChange: (Int, Int) -> Unit,
    val onReactionSecondsChange: (Int) -> Unit,
    val onPushupCountChange: (Int) -> Unit,
    val onPushupOverlayChange: (PushupOverlay) -> Unit,
    val onPushupModelChange: (PoseModel) -> Unit,
    val onSoundChange: (SoundSettings) -> Unit,
    val onPickMelody: () -> Unit,
    val onFailSafeChange: (Int) -> Unit,
    val onResumeDelayChange: (Int) -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onOpenPermission: (String) -> Unit,
    val onToggleManual: (String) -> Unit,
    val onTest: () -> Unit,
    /** Отдать журнал компьютеру прямо сейчас, не дожидаясь обеденного окна. */
    val onSendLog: () -> Unit = {},
    /** Начать запись ночи или остановить идущую. */
    val onNightRecordingToggle: () -> Unit = {},
)

/**
 * Полноэкранная страница.
 *
 * Их всего две: меню и список испытаний. Всё остальное — выдвижные окна снизу,
 * см. [Sheet].
 */
private enum class Page { MENU, CHALLENGE }

/**
 * Окно, выезжающее снизу (макет Claude Design, 2026-08-16).
 *
 * Настройки не уводят с экрана: панель наезжает поверх, а под ней остаётся видно,
 * откуда её открыли. Смахнул вниз или нажал мимо — вернулся, куда был.
 *
 * ⚠️ Список испытаний — исключение: он остаётся полноэкранной страницей
 * (владелец, 2026-08-16, возврат к прежнему). Из него настройки конкретного
 * испытания выезжают окном поверх — так видно, к чему они относятся.
 */
private enum class Sheet { SOUND, LOCK, PERMISSIONS, MATH, REACTION, PUSHUPS, NFC }

/**
 * Единственный экран приложения.
 *
 * Текста минимум намеренно (решение владельца 2026-08-14): приложение личное,
 * подписи к собственным кнопкам не нужны.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    /**
     * Содержимое шторки меток. Рисует его `:app`: там живёт считыватель, который
     * умеет работать только на активити переднего плана.
     */
    nfcContent: @Composable () -> Unit,
) {
    var page by remember { mutableStateOf(Page.MENU) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    // Системная «назад» уводит на уровень выше, а не из приложения.
    // Открытое окно закрывается своими средствами, поэтому здесь только страницы.
    BackHandler(enabled = sheet == null && page != Page.MENU) { page = Page.MENU }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        when (page) {
            Page.MENU -> Menu(
                state = state,
                actions = actions,
                openChallenges = { page = Page.CHALLENGE },
                openSheet = { sheet = it },
            )

            Page.CHALLENGE -> Header(
                title = stringResource(R.string.menu_challenge),
                icon = R.drawable.ic_challenge,
                onBack = { page = Page.MENU },
            ) {
                ChallengeBody(state, actions) { sheet = it }
            }
        }
    }

    sheet?.let { open ->
        val close = { sheet = null }
        when (open) {
            Sheet.SOUND -> SlideUp(R.string.menu_sound, R.drawable.ic_sound, close) {
                SoundBody(state, actions)
            }
            Sheet.LOCK -> SlideUp(R.string.menu_lock, R.drawable.ic_lock, close) {
                LockBody(state, actions)
            }
            Sheet.PERMISSIONS -> SlideUp(
                R.string.menu_permissions,
                R.drawable.ic_permissions,
                close,
            ) {
                PermissionsBody(state, actions)
            }
            Sheet.MATH -> SlideUp(R.string.challenge_math, R.drawable.ic_challenge, close) {
                MathBody(state, actions)
            }
            Sheet.REACTION -> SlideUp(R.string.challenge_reaction, R.drawable.ic_challenge, close) {
                ReactionBody(state, actions)
            }
            Sheet.PUSHUPS -> SlideUp(R.string.challenge_pushups, R.drawable.ic_challenge, close) {
                PushupBody(state, actions)
            }
            // Метки — тоже шторка (владелец, 2026-08-17). Считыватель при этом
            // остаётся на активити: главный экран ею и является, отдельная не нужна.
            Sheet.NFC -> SlideUp(R.string.nfc_title, R.drawable.ic_challenge, close) {
                nfcContent()
            }
        }
    }
}

/**
 * Окно, выезжающее снизу.
 *
 * Оформление снято с макета: скруглены только верхние углы, по верхней кромке
 * акцентная линия, выше 88% экрана не поднимается, содержимое прокручивается внутри.
 * Ручки для перетаскивания нет — в макете её нет, а закрыть можно и смахиванием,
 * и нажатием мимо, и системной «назад».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlideUp(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlarmColors.Surface,
        contentColor = AlarmColors.TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                // Выше 88% экрана окно не поднимается — как в макете. Короткому
                // содержимому высота не навязывается: окно ровно по нему.
                //
                // Акцентной полоски по верхней кромке нет (владелец, 2026-08-16):
                // пара оранжевых пикселей над окном ничего не означали.
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                // ⚠️ Отступ отдельным Spacer, а не padding внутри size: padding
                // съедал бы размер значка изнутри, и он выходил вдвое мельче
                // заказанного. Ровно на этом значки в меню и оказались мелкими.
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = AlarmColors.Accent,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(title).uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = AlarmColors.TextPrimary,
                )
            }
            content()
        }
    }
}

// ──────────────────────────────── главное меню ────────────────────────────────

@Composable
private fun Menu(
    state: SettingsUiState,
    actions: SettingsActions,
    openChallenges: () -> Unit,
    openSheet: (Sheet) -> Unit,
) {
    // Подписи вернулись (владелец, 2026-08-16, отмена его же прежнего): с одними
    // значками меню читалось хуже. Крупными значки при этом остались.
    // Ни подзаголовка, ни строки состояния перехвата (владелец, 2026-08-16):
    // и то и другое он читал ровно один раз.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_title),
            modifier = Modifier.weight(1f),
            fontSize = 46.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            color = AlarmColors.Accent,
        )
        Switch(
            checked = state.masterEnabled,
            onCheckedChange = actions.onMasterChange,
            colors = switchColors(),
        )
    }

    Spacer(Modifier.height(20.dp))
    MenuRow(
        icon = R.drawable.ic_challenge,
        caption = stringResource(R.string.menu_challenge),
        title = stringResource(state.challenge.titleRes()),
        onClick = openChallenges,
    )

    Spacer(Modifier.height(11.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxWidth()) {
        MenuCard(R.drawable.ic_sound, stringResource(R.string.menu_sound), Modifier.weight(1f)) {
            openSheet(Sheet.SOUND)
        }
        MenuCard(R.drawable.ic_lock, stringResource(R.string.menu_lock), Modifier.weight(1f)) {
            openSheet(Sheet.LOCK)
        }
    }

    Spacer(Modifier.height(11.dp))
    val granted = state.permissions.count { it.granted }
    val all = granted == state.permissions.size
    MenuRow(
        icon = R.drawable.ic_permissions,
        caption = if (all) {
            stringResource(R.string.menu_permissions_all)
        } else {
            stringResource(R.string.menu_permissions_missing, granted, state.permissions.size)
        },
        title = stringResource(R.string.menu_permissions),
        // Недостача обводится акцентом: это единственное, что молча ломает будильник.
        alarming = !all,
        onClick = { openSheet(Sheet.PERMISSIONS) },
    )

    // Передача журнала. Отдельной страницы нет намеренно: настраивать нечего, а нажать
    // может понадобиться — когда ноутбук уже открыт и ждать обеда не хочется.
    Spacer(Modifier.height(11.dp))
    MenuRow(
        icon = R.drawable.ic_permissions,
        caption = state.syncCaption.ifEmpty { stringResource(R.string.sync_never) },
        title = stringResource(R.string.sync_now),
        onClick = actions.onSendLog,
    )

    // Запись ночи. Кнопка, а не расписание: включать её каждую ночь автоматически владелец
    // не захотел — микрофон должен включаться, только когда об этом попросили.
    Spacer(Modifier.height(11.dp))
    MenuRow(
        icon = R.drawable.ic_sound,
        caption = state.nightRecordingCaption.ifEmpty {
            stringResource(R.string.night_record_hint)
        },
        title = if (state.nightRecording) {
            stringResource(R.string.night_record_stop)
        } else {
            stringResource(R.string.night_record_start)
        },
        // Идущая запись обведена акцентом — как и недостача разрешений: и то и другое
        // означает, что прямо сейчас происходит нечто, о чём надо помнить.
        alarming = state.nightRecording,
        onClick = actions.onNightRecordingToggle,
    )

    Spacer(Modifier.height(18.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlarmColors.Accent, RoundedCornerShape(16.dp))
            .quietClick(actions.onTest)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_test),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.menu_test),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.Black,
            )
        }
    }

    if (state.headphonesConnected) {
        Spacer(Modifier.height(16.dp))
        Warning(stringResource(R.string.main_headphones_warning))
    }
}

// ──────────────────────────────── кирпичики страниц ────────────────────────────────

/** Заголовок страницы со стрелкой назад, значком и её содержимое. */
@Composable
private fun Header(
    title: String,
    @DrawableRes icon: Int? = null,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.menu_back),
            fontSize = 30.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Black,
            color = AlarmColors.TextPrimary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 14.dp),
        )
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                // В шапке страницы значок акцентный, в меню — светлый: так в макете.
                tint = AlarmColors.Accent,
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp),
            )
        }
        Text(
            text = title.uppercase(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = AlarmColors.TextPrimary,
        )
    }
    Spacer(Modifier.height(20.dp))
    content()
}

/** Карточка-подложка: тёмная плашка с рамкой, как всё в этом макете. */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    alarming: Boolean = false,
    /** Выбранное обводится акцентом — так в макете отмечено текущее испытание. */
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (alarming) AlarmColors.AccentSoft else AlarmColors.Surface, shape)
            .border(
                width = 1.5.dp,
                color = when {
                    alarming || selected -> AlarmColors.Accent
                    else -> AlarmColors.Outline
                },
                shape = shape,
            )
            .let { if (onClick == null) it else it.quietClick(onClick) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        content()
    }
}

/** Строка списка: значок, мелкая подпись сверху, крупное значение снизу, стрелка справа. */
@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    caption: String,
    title: String,
    alarming: Boolean = false,
    onClick: () -> Unit,
) {
    Card(alarming = alarming, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Сплошной акцентный, а не двухцветный: владелец выбрал тот вид,
            // что стоял в шапке страницы (2026-08-16).
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AlarmColors.Accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = caption.uppercase(),
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = AlarmColors.TextSecondary,
                )
                Text(
                    text = title.uppercase(),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = AlarmColors.TextPrimary,
                )
            }
            Text(
                text = "›",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (alarming) AlarmColors.Accent else AlarmColors.Outline,
            )
        }
    }
}

/**
 * Карточка меню: крупный значок сверху, подпись снизу.
 *
 * Значок заметно больше прежнего, но подпись при нём осталась: без неё меню
 * читалось хуже (владелец, 2026-08-16).
 */
@Composable
private fun MenuCard(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier, onClick = onClick) {
        Column {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AlarmColors.Accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = AlarmColors.TextPrimary,
            )
        }
    }
}

// ──────────────────────────────── содержимое окон ────────────────────────────────

/**
 * Список испытаний: кружок выбора слева, «настроить» справа.
 *
 * Выбрать и настроить — разные действия и разные кнопки: настройки испытания
 * иногда нужно посмотреть, не переключаясь на него.
 */
@Composable
private fun ChallengeBody(
    state: SettingsUiState,
    actions: SettingsActions,
    openSheet: (Sheet) -> Unit,
) {
    Challenge.entries.forEach { option ->
        val selected = option == state.challenge
        // Испытание без маршрута выбрать нельзя (решение владельца 2026-08-16):
        // честнее не дать его включить, чем включить и молча подменить примерами.
        // «Настроить» при этом работает — иначе маршрут негде было бы собрать.
        val locked = option == Challenge.NFC && state.nfcRouteSteps == 0
        Spacer(Modifier.height(11.dp))
        Card(selected = selected && !locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = if (selected) AlarmColors.Accent else AlarmColors.Outline,
                            shape = CircleShape,
                        )
                        .clickable(enabled = !locked) { actions.onChallengeChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(Modifier.size(10.dp).background(AlarmColors.Accent, CircleShape))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !locked) { actions.onChallengeChange(option) }
                        .padding(horizontal = 14.dp),
                ) {
                    // Капсом — как весь текст в макете.
                    Text(
                        text = stringResource(option.titleRes()).uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (locked) AlarmColors.TextDim else AlarmColors.TextPrimary,
                    )
                    if (locked) {
                        Text(
                            text = stringResource(R.string.challenge_nfc_no_route),
                            fontSize = 11.sp,
                            color = AlarmColors.TextDim,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.menu_configure),
                    modifier = Modifier
                        .background(AlarmColors.SurfaceRaised, RoundedCornerShape(10.dp))
                        .clickable { openSheet(option.settingsSheet()) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = AlarmColors.Accent,
                )
            }
        }
    }
}

/** Куда ведёт «настроить». */
private fun Challenge.settingsSheet(): Sheet = when (this) {
    Challenge.MATH -> Sheet.MATH
    Challenge.REACTION -> Sheet.REACTION
    Challenge.PUSHUPS -> Sheet.PUSHUPS
    Challenge.NFC -> Sheet.NFC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MathBody(state: SettingsUiState, actions: SettingsActions) {
    val math = state.math
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MathOperation.entries.forEach { operation ->
            val on = operation in math.operations
            Chip(operation.sign(), on, Modifier.weight(1f)) {
                actions.onOperationChange(operation, !on)
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Value(stringResource(R.string.math_count_title), "${math.count}")
    Slider(
        value = math.count.toFloat(),
        onValueChange = { actions.onMathCountChange(it.roundToInt()) },
        valueRange = MathSettings.MIN_COUNT.toFloat()..MathSettings.MAX_COUNT.toFloat(),
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )

    Spacer(Modifier.height(10.dp))
    Value(stringResource(R.string.math_range_title), "${math.range.first}–${math.range.last}")
    RangeSlider(
        value = minOf(math.min, math.max).toFloat()..maxOf(math.min, math.max).toFloat(),
        onValueChange = { actions.onMathRangeChange(it.start.roundToInt(), it.endInclusive.roundToInt()) },
        valueRange = MathSettings.BOUND_MIN.toFloat()..MathSettings.BOUND_MAX.toFloat(),
        colors = sliderColors(),
        track = { PlainRangeTrack(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionBody(state: SettingsUiState, actions: SettingsActions) {
    BigValue("${state.reaction.perfectSeconds} с")
    Slider(
        value = state.reaction.perfectSeconds.toFloat(),
        onValueChange = { actions.onReactionSecondsChange(it.roundToInt()) },
        valueRange = ReactionSettings.MIN_SECONDS.toFloat()..ReactionSettings.MAX_SECONDS.toFloat(),
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )
    // Пояснения нет: в макете у реакции его нет, и без него понятно.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushupBody(state: SettingsUiState, actions: SettingsActions) {
    // Чем себя видеть поверх кадра: фигурой или скелетом.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PushupOverlay.entries.forEach { option ->
            Chip(
                glyph = stringResource(option.titleRes()),
                on = state.pushups.overlay == option,
                modifier = Modifier.weight(1f),
            ) { actions.onPushupOverlayChange(option) }
        }
    }

    Spacer(Modifier.height(12.dp))
    // Модель распознавания. Все три в приложении, чтобы сравнить на живом телефоне.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PoseModel.entries.forEach { option ->
            Chip(
                glyph = stringResource(option.titleRes()),
                on = state.pushups.model == option,
                modifier = Modifier.weight(1f),
                // Три чипа в ряд: на 22sp длинное слово не помещается.
                fontSize = 15.sp,
            ) { actions.onPushupModelChange(option) }
        }
    }
    Hint(stringResource(R.string.pushup_model_hint))

    Spacer(Modifier.height(22.dp))
    BigValue("${state.pushups.count}")
    Slider(
        value = state.pushups.count.toFloat(),
        onValueChange = { actions.onPushupCountChange(it.roundToInt()) },
        valueRange = PushupSettings.MIN_COUNT.toFloat()..PushupSettings.MAX_COUNT.toFloat(),
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )
    // Подсказки про кадр здесь нет (владелец, 2026-08-16): она нужна на самом
    // испытании, а не в настройках, и там она есть — и текстом, и голосом.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundBody(state: SettingsUiState, actions: SettingsActions) {
    val sound = state.sound
    Toggle(stringResource(R.string.sound_enabled_title), sound.enabled) {
        actions.onSoundChange(sound.copy(enabled = it))
    }
    Toggle(stringResource(R.string.sound_vibrate_title), sound.vibrate) {
        actions.onSoundChange(sound.copy(vibrate = it))
    }

    Spacer(Modifier.height(14.dp))
    Value(stringResource(R.string.sound_start_volume_title), "${sound.startVolumePercent}%")
    Slider(
        value = sound.startVolumePercent.toFloat(),
        onValueChange = { actions.onSoundChange(sound.copy(startVolumePercent = it.roundToInt())) },
        valueRange = 0f..100f,
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )

    // Зеркалить больше нечего: хранятся проценты в секунду, и вправо — как и было,
    // быстрее. Ползунок стоит по ступеням в полпроцента: 2 / 2,5 / 3.
    val tenths = sound.percentPerSecondTenths
    val whole = tenths / 10
    val fraction = tenths % 10
    Value(
        stringResource(R.string.sound_ramp_title),
        if (fraction == 0) "$whole% / с" else "$whole,$fraction% / с",
    )
    Slider(
        value = tenths.toFloat(),
        onValueChange = {
            actions.onSoundChange(sound.copy(percentPerSecondTenths = it.roundToInt()))
        },
        valueRange = SoundSettings.MIN_PERCENT_PER_SECOND_TENTHS.toFloat()..
            SoundSettings.MAX_PERCENT_PER_SECOND_TENTHS.toFloat(),
        steps = (SoundSettings.MAX_PERCENT_PER_SECOND_TENTHS -
            SoundSettings.MIN_PERCENT_PER_SECOND_TENTHS) /
            SoundSettings.PERCENT_PER_SECOND_STEP_TENTHS - 1,
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )

    Spacer(Modifier.height(10.dp))
    Value(stringResource(R.string.sound_melody_title), state.melodyName)
    AccentButton(stringResource(R.string.sound_melody_pick), actions.onPickMelody)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockBody(state: SettingsUiState, actions: SettingsActions) {
    // Тумблера часов здесь нет (владелец, 2026-08-16): часы показываются всегда
    // и всегда в левом верхнем углу, выключать их незачем.
    Value(stringResource(R.string.settings_failsafe_title), "${state.failSafeMinutes} мин")
    Slider(
        value = state.failSafeMinutes.toFloat(),
        onValueChange = { actions.onFailSafeChange(it.roundToInt()) },
        valueRange = FailSafe.MIN_MINUTES.toFloat()..FailSafe.MAX_MINUTES.toFloat(),
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )

    Value(stringResource(R.string.settings_resume_title), "${state.resumeDelaySeconds} с")
    Slider(
        value = state.resumeDelaySeconds.toFloat(),
        onValueChange = { actions.onResumeDelayChange(it.roundToInt()) },
        valueRange = AlarmState.MIN_RESUME_DELAY_SECONDS.toFloat()..
            AlarmState.MAX_RESUME_DELAY_SECONDS.toFloat(),
        colors = sliderColors(),
        track = { PlainTrack(it) },
    )
}

@Composable
private fun PermissionsBody(state: SettingsUiState, actions: SettingsActions) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlarmColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .border(1.5.dp, AlarmColors.Track, RoundedCornerShape(12.dp))
            .clickable(onClick = actions.onOpenAppSettings)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_app_settings).uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = AlarmColors.Accent,
        )
    }

    Spacer(Modifier.height(8.dp))
    state.permissions.forEach { row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Тонкая линия снизу вместо карточки: строк девять, и рамка
                    // вокруг каждой превращает список в лестницу.
                    val y = size.height - 0.5f
                    drawLine(AlarmColors.SurfaceRaised, Offset(0f, y), Offset(size.width, y), 1f)
                }
                .padding(vertical = 12.dp),
        ) {
            // Точка — отметка владельца, и по ней же он её переставляет.
            // Выдано — акцентным оранжевым, а не зелёным (владелец, 2026-08-16):
            // зелёный в приложении больше нигде не встречается и выбивался.
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        if (row.granted) AlarmColors.Accent else AlarmColors.DotOff,
                        CircleShape,
                    )
                    .quietClick { actions.onToggleManual(row.key) },
            )
            Text(
                text = row.title,
                modifier = Modifier
                    .weight(1f)
                    .clickable { actions.onToggleManual(row.key) }
                    .padding(horizontal = 12.dp),
                fontSize = 13.sp,
                color = AlarmColors.TextPrimary,
            )
            // Переход в системные настройки есть у каждой строки без исключения:
            // раньше у части строк вместо него стоял переключатель, и попасть в
            // систему по ним было нельзя вовсе.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AlarmColors.SurfaceRaised, RoundedCornerShape(9.dp))
                    .border(1.dp, AlarmColors.Outline, RoundedCornerShape(9.dp))
                    .clickable { actions.onOpenPermission(row.key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_external),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

// ──────────────────────────────── кирпичики ────────────────────────────────

/**
 * Нажатие без подсветки.
 *
 * Стандартная волна Material разбегается по блоку в стороны и на тёмных карточках
 * выглядит грязью (владелец, 2026-08-16). Нажатие и так подтверждается тем, что
 * открылось окно.
 */
@Composable
private fun Modifier.quietClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** Мелкая пояснительная строка под настройкой. */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = AlarmColors.TextDim,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Заголовок группы внутри страницы. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = AlarmColors.TextSecondary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun Value(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = AlarmColors.TextSecondary,
        )
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AlarmColors.Value)
    }
}

/**
 * Настройка из одного числа: крупное значение и ползунок под ним.
 *
 * Без подписи (владелец, 2026-08-16): окно называется «ОТЖИМАНИЯ», и объяснять,
 * что за число в нём стоит, уже нечего.
 */
@Composable
private fun BigValue(value: String) {
    Text(
        text = value,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 56.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-2).sp,
        textAlign = TextAlign.Center,
        color = AlarmColors.Value,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, color = AlarmColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
private fun Chip(
    glyph: String,
    on: Boolean,
    modifier: Modifier,
    /**
     * Размер надписи.
     *
     * Параметром, а не константой: на два чипа в ряд слова помещаются крупно, а на
     * три — уже нет, и «ТЯЖЁЛАЯ» обрезалась. Шрифт мельче здесь честнее, чем
     * перенос на вторую строку или сокращение слова.
     */
    fontSize: TextUnit = 22.sp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(if (on) AlarmColors.Accent else AlarmColors.SurfaceRaised, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = if (on) Color.White else AlarmColors.TextSecondary,
        )
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AlarmColors.SurfaceRaised,
            contentColor = AlarmColors.Accent,
        ),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun Warning(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlarmColors.AccentSoft.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, fontSize = 13.sp, color = AlarmColors.TextPrimary, textAlign = TextAlign.Start)
    }
}

private fun Challenge.titleRes(): Int = when (this) {
    Challenge.MATH -> R.string.challenge_math
    Challenge.REACTION -> R.string.challenge_reaction
    Challenge.NFC -> R.string.challenge_nfc
    Challenge.PUSHUPS -> R.string.challenge_pushups
}

private fun PushupOverlay.titleRes(): Int = when (this) {
    PushupOverlay.FIGURE -> R.string.pushup_overlay_figure
    PushupOverlay.DOTS -> R.string.pushup_overlay_dots
}

private fun PoseModel.titleRes(): Int = when (this) {
    PoseModel.LITE -> R.string.pushup_model_lite
    PoseModel.FULL -> R.string.pushup_model_full
    PoseModel.HEAVY -> R.string.pushup_model_heavy
}

private fun MathOperation.sign(): String = when (this) {
    MathOperation.PLUS -> "+"
    MathOperation.MINUS -> "−"
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = AlarmColors.Accent,
    activeTrackColor = AlarmColors.Accent,
    inactiveTrackColor = AlarmColors.Track,
)

/**
 * Дорожка ползунка без точки на конце.
 *
 * Material рисует у края «указатель остановки» — оранжевую точку. Она ничего не
 * означает и читалась как соринка (владелец, 2026-08-16), поэтому дорожку рисуем
 * сами: две полосы и всё.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainTrack(state: SliderState) {
    SliderDefaults.Track(
        sliderState = state,
        colors = sliderColors(),
        drawStopIndicator = null,
        thumbTrackGapSize = 0.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainRangeTrack(state: RangeSliderState) {
    SliderDefaults.Track(
        rangeSliderState = state,
        colors = sliderColors(),
        drawStopIndicator = null,
        thumbTrackGapSize = 0.dp,
    )
}

/**
 * Цвета переключателя — точно из макета.
 *
 * Включённый: оранжевая дорожка, **чёрный** кружок. Белый кружок на оранжевом
 * выглядел размыто (владелец, 2026-08-16), а чёрный держит контраст — по акценту
 * в этом приложении вообще пишут чёрным.
 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = AlarmColors.Accent,
    checkedBorderColor = AlarmColors.Accent,
    uncheckedThumbColor = AlarmColors.TextSecondary,
    uncheckedTrackColor = AlarmColors.SurfaceRaised,
    uncheckedBorderColor = AlarmColors.Track,
)
