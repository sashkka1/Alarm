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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.sasha.alarm.core.HookHealth
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
    /**
     * Плашка об отказе передачи: пустая строка — плашки нет.
     *
     * ⚠️ Заведена после того, как телефон двое суток молчал незамеченным (31.08.2026).
     * Текст готовит `:app` — он же знает часы и умеет в ресурсы с подстановкой.
     */
    val syncWarning: String = "",
    /**
     * Что показала проверка зацепа: плашка только на поломку. null — не проверялось
     * (приложение выключено тумблером).
     *
     * ⚠️ Постоянной строки «зацеп работает» нет намеренно: 2026-08-16 владелец убрал
     * строку состояния перехвата — «читал её ровно один раз». Видно только, что сломано.
     */
    val hook: HookHealth.Status? = null,
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
    /** Тема выбрана в окне шестерёнки — применить и запомнить. */
    val onPickTheme: (AppTheme) -> Unit = {},
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
    var gearOpen by remember { mutableStateOf(false) }
    var gear by remember { mutableStateOf(Rect.Zero) }

    // Системная «назад» уводит на уровень выше, а не из приложения.
    // Открытое окно закрывается своими средствами, поэтому здесь только страницы.
    BackHandler(enabled = gearOpen) { gearOpen = false }
    BackHandler(enabled = !gearOpen && sheet == null && page != Page.MENU) { page = Page.MENU }

    Box(Modifier.fillMaxSize().background(theme.bg)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            // Поля 24 dp: шестерёнка окна настроек ложится ровно на место настоящей (как в DrozdRun).
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        when (page) {
            Page.MENU -> Menu(
                state = state,
                actions = actions,
                gearOpen = gearOpen,
                onGear = { gearOpen = !gearOpen },
                onGearPlaced = { gear = it },
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

    // Окно шестерёнки: «Разрешения» и тема — как в DrozdRun (владелец, 2026-10-14).
    SettingsLayer(
        open = gearOpen,
        gear = gear,
        missingPerms = state.permissions.count { !it.granted },
        onClose = { gearOpen = false },
        onPermissions = {
            gearOpen = false
            sheet = Sheet.PERMISSIONS
        },
        onPick = actions.onPickTheme,
    )
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
        containerColor = theme.surf,
        contentColor = theme.text,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
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
                .padding(22.dp),
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
                    tint = theme.text,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(11.dp))
                Text(
                    text = stringResource(title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                )
            }
            content()
        }
    }
}

// ──────────────────────────────── главное меню ────────────────────────────────

/**
 * Плашка предупреждения: «журнал не уехал», «зацеп не работает».
 *
 * Карточка темы без обводки, заголовок — цветом статуса `danger` («Мягкий»: статус — только где есть
 * смысл). Своего цвета заводить незачем: это предупреждение, а не новая сущность.
 */
@Composable
private fun WarningBanner(text: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surf)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = theme.danger,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = hint,
            color = theme.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun Menu(
    state: SettingsUiState,
    actions: SettingsActions,
    gearOpen: Boolean,
    onGear: () -> Unit,
    onGearPlaced: (Rect) -> Unit,
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
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Medium,
            color = theme.text,
        )
        Switch(
            checked = state.masterEnabled,
            onCheckedChange = actions.onMasterChange,
            colors = switchColors(),
        )
        // Шестерёнка — около тумблера в правом верхнем углу (владелец, 2026-10-14).
        Spacer(Modifier.width(11.dp))
        SettingsGear(
            gearOpen,
            Modifier.onGloballyPositioned { onGearPlaced(it.boundsInRoot()) },
            onClick = onGear,
        )
    }

    // ⚠️ Плашка стоит **выше всех настроек**, сразу под заголовком: она про то, что
    // journal не доехал, и увидеть её надо, не листая. Гаснет сама, когда передача
    // прошла, — закрывать её крестиком нечем намеренно: закрытая плашка про молчащий
    // журнал ничем не лучше отсутствующей.
    // Зацеп — первым: без него будильник не поднимет экран вовсе, это важнее журнала.
    when (state.hook) {
        HookHealth.Status.UNBOUND -> {
            Spacer(Modifier.height(16.dp))
            WarningBanner(stringResource(R.string.hook_warn_unbound), stringResource(R.string.hook_warn_unbound_hint))
        }

        HookHealth.Status.NO_ACCESS -> {
            Spacer(Modifier.height(16.dp))
            WarningBanner(stringResource(R.string.hook_warn_no_access), stringResource(R.string.hook_warn_no_access_hint))
        }

        HookHealth.Status.WORKING, null -> Unit
    }
    if (state.syncWarning.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        WarningBanner(state.syncWarning, stringResource(R.string.sync_warn_hint))
    }

    Spacer(Modifier.height(18.dp))
    MenuRow(
        icon = R.drawable.ic_challenge,
        caption = stringResource(R.string.menu_challenge),
        title = stringResource(state.challenge.titleRes()),
        onClick = openChallenges,
    )

    Spacer(Modifier.height(GAP_CARDS))
    Row(horizontalArrangement = Arrangement.spacedBy(GAP_CARDS), modifier = Modifier.fillMaxWidth()) {
        MenuCard(R.drawable.ic_sound, stringResource(R.string.menu_sound), Modifier.weight(1f)) {
            openSheet(Sheet.SOUND)
        }
        MenuCard(R.drawable.ic_lock, stringResource(R.string.menu_lock), Modifier.weight(1f)) {
            openSheet(Sheet.LOCK)
        }
    }

    // Строки «Разрешения» в меню нет (владелец, 2026-10-14): они в окне шестерёнки, там же
    // и число невыданных.

    // Передача журнала. Отдельной страницы нет намеренно: настраивать нечего, а нажать
    // может понадобиться — когда ноутбук уже открыт и ждать обеда не хочется.
    Spacer(Modifier.height(GAP_CARDS))
    MenuRow(
        icon = R.drawable.ic_permissions,
        caption = state.syncCaption.ifEmpty { stringResource(R.string.sync_never) },
        title = stringResource(R.string.sync_now),
        onClick = actions.onSendLog,
    )

    Spacer(Modifier.height(18.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.accent, RoundedCornerShape(16.dp))
            .quietClick(actions.onTest)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_test),
                contentDescription = null,
                tint = theme.onAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.menu_test),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = theme.onAccent,
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
        // «‹» — круг 40 dp на `field`, как «назад» в окне настроек.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(theme.field)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.menu_back), color = theme.text, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = theme.text,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Medium,
            color = theme.text,
        )
    }
    Spacer(Modifier.height(18.dp))
    content()
}

/**
 * Карточка «Мягкого»: заливка `surf` на `bg`, без обводки и тени, радиус 16. Выбранное — заливка `sel`
 * (так отмечено текущее испытание); тревожное отмечается не рамкой, а цветом статуса в самой строке.
 */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    /** Выбранное — заливкой `sel`. */
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) theme.sel else theme.surf, shape)
            .let { if (onClick == null) it else it.quietClick(onClick) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Значок — цветом текста рядом («Мягкий»: цветных значков интерфейса нет).
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = theme.text,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                )
                // Тревожное — подпись цветом `danger`: недостача разрешений молча ломает будильник.
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    color = if (alarming) theme.danger else theme.muted,
                )
            }
            Text(
                text = "›",
                fontSize = 22.sp,
                color = if (alarming) theme.danger else theme.faint,
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
                tint = theme.text,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = theme.text,
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
        Spacer(Modifier.height(GAP_CARDS))
        Card(selected = selected && !locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = if (selected) theme.accent else theme.faint,
                            shape = CircleShape,
                        )
                        .clickable(enabled = !locked) { actions.onChallengeChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(Modifier.size(10.dp).background(theme.accent, CircleShape))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !locked) { actions.onChallengeChange(option) }
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = stringResource(option.titleRes()),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (locked) theme.faint else theme.text,
                    )
                    if (locked) {
                        Text(
                            text = stringResource(R.string.challenge_nfc_no_route),
                            fontSize = 13.sp,
                            color = theme.muted,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.menu_configure),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.field)
                        .clickable { openSheet(option.settingsSheet()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
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
                // Три чипа в ряд: на 17sp длинное слово не помещается.
                fontSize = 14.sp,
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
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(theme.field)
            .clickable(onClick = actions.onOpenAppSettings),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_app_settings),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = theme.text,
        )
    }

    Spacer(Modifier.height(8.dp))
    val divider = theme.sel
    state.permissions.forEach { row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Тонкая линия снизу вместо карточки: строк девять, и рамка
                    // вокруг каждой превращает список в лестницу.
                    val y = size.height - 0.5f
                    drawLine(divider, Offset(0f, y), Offset(size.width, y), 1f)
                }
                .padding(vertical = 12.dp),
        ) {
            // Точка — отметка владельца, и по ней же он её переставляет.
            // Выдано — акцентом темы, а не зелёным (владелец, 2026-08-16):
            // зелёный в приложении больше нигде не встречается и выбивался.
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        if (row.granted) theme.accent else theme.sel,
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
                fontSize = 14.sp,
                color = theme.text,
            )
            // Переход в системные настройки есть у каждой строки без исключения:
            // раньше у части строк вместо него стоял переключатель, и попасть в
            // систему по ним было нельзя вовсе.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.field)
                    .clickable { actions.onOpenPermission(row.key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_external),
                    contentDescription = null,
                    tint = theme.text,
                    modifier = Modifier.size(16.dp),
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
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = theme.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Заголовок группы внутри страницы. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = theme.muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Value(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = theme.muted,
        )
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.text)
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
        // Главное число окна — единственное место акцента в нём («Мягкий»).
        fontSize = 35.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = theme.accent,
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
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, color = theme.text)
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
    fontSize: TextUnit = 17.sp,
    onClick: () -> Unit,
) {
    // Капсула «Мягкого»: выбранная — `accent` / `onAccent`, прочие — `field` / `muted`.
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (on) theme.accent else theme.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            color = if (on) theme.onAccent else theme.muted,
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
        shape = RoundedCornerShape(10.dp),
        // Вторичная кнопка «Мягкого»: `field` / `text`. Основная на экране одна — «Тест экрана».
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.field,
            contentColor = theme.text,
        ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Warning(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surf, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = theme.warn, textAlign = TextAlign.Start)
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
    thumbColor = theme.accent,
    activeTrackColor = theme.accent,
    inactiveTrackColor = theme.sel,
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
 * Тумблер «Мягкого»: включённый — дорожка `accent`, ручка `onAccent` (по акценту пишут `onAccent`:
 * белый кружок на светлом акценте размывался — владелец, 2026-08-16); выключенный — дорожка `sel`,
 * ручка `muted`.
 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = theme.onAccent,
    checkedTrackColor = theme.accent,
    checkedBorderColor = theme.accent,
    uncheckedThumbColor = theme.muted,
    uncheckedTrackColor = theme.sel,
    uncheckedBorderColor = theme.sel,
)

/** Зазор между карточками («Мягкий», мобайл — 12 dp). */
private val GAP_CARDS = 12.dp
