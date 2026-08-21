package com.sasha.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Что показывает экран меток.
 *
 * Метки здесь — только номера: идентификатор железа владельцу ни о чём не говорит
 * и на экране не нужен.
 */
data class NfcSetupUiState(
    val tags: List<Int>,
    val route: List<Int>,
    /** Ждём прикладывания новой метки прямо сейчас. */
    val waiting: Boolean,
    /** Есть ли NFC в телефоне и включён ли он в системе. */
    val supported: Boolean,
    val enabled: Boolean,
    /** Что приложили последним. Держится до следующего касания. */
    val touched: NfcTouch?,
)

/**
 * Ответ на касание в настройках.
 *
 * Наклейки одинаковые и ничем не подписаны, поэтому единственный способ узнать, какая
 * из них в руке, — приложить её и прочитать номер. Работает и без «плюса»: считыватель
 * включён всё время, пока экран открыт, а запоминается метка только по явной команде.
 */
sealed interface NfcTouch {
    /** Уже знакомая метка — вот её номер. */
    data class Known(val number: Int) : NfcTouch

    /** Только что запомнили под этим номером. */
    data class Added(val number: Int) : NfcTouch

    /** Метки нет в списке. */
    data object Unknown : NfcTouch
}

data class NfcSetupActions(
    val onArm: () -> Unit,
    val onCancelArm: () -> Unit,
    val onForget: (Int) -> Unit,
    val onAddStep: (Int) -> Unit,
    val onRemoveLastStep: () -> Unit,
    val onClearRoute: () -> Unit,
    val onOpenNfcSettings: () -> Unit,
)

/**
 * Экран меток: сначала их регистрируют, потом из их номеров собирают маршрут.
 *
 * Два раздела и ровно два действия. Сверху «Метки»: плюс включает ожидание, метку
 * прикладывают, она получает следующий свободный номер. Снизу «Маршрут»: нажатие
 * на номер дописывает шаг в конец, поэтому маршрут набирается ровно тем порядком,
 * в котором его будут проходить, и одна метка может встречаться в нём сколько
 * угодно раз.
 */
@Composable
fun NfcSetupScreen(state: NfcSetupUiState, actions: NfcSetupActions) {
    // Собственных отступов и фона нет: содержимое живёт в шторке настроек и берёт
    // их у неё (владелец, 2026-08-17 — отдельная страница была лишней).
    Column {
        // Сообщение только когда NFC и правда не работает: в остальное время
        // экран молчит — на нём нечего читать, на нём есть что нажать.
        if (!state.supported) {
            Spacer(Modifier.height(12.dp))
            Note(stringResource(R.string.nfc_unsupported))
        } else if (!state.enabled) {
            Spacer(Modifier.height(12.dp))
            Note(stringResource(R.string.nfc_disabled))
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.onOpenNfcSettings) {
                Text(stringResource(R.string.nfc_open_settings), color = AlarmColors.Accent, fontSize = 14.sp)
            }
        }

        SectionTitle(stringResource(R.string.nfc_tags_header))
        Spacer(Modifier.height(12.dp))

        state.touched?.let { touched ->
            TouchBanner(touched)
            Spacer(Modifier.height(12.dp))
        }

        // Плюс стоит прямо в ряду меток, а не отдельной кнопкой с объяснением:
        // сразу видно, куда добавится следующая.
        if (state.waiting) {
            WaitingPanel(actions.onCancelArm)
        } else {
            TagsGrid(state.tags, actions.onArm) { number ->
                TagChip(
                    number = number,
                    onAdd = { actions.onAddStep(number) },
                    onForget = { actions.onForget(number) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.nfc_route_header))
            Spacer(Modifier.weight(1f))
            // Убрать последний и очистить — рядом с самим маршрутом и без подписей.
            if (state.route.isNotEmpty()) {
                RouteAction("⌫", actions.onRemoveLastStep)
                Spacer(Modifier.size(8.dp))
                RouteAction("×", actions.onClearRoute)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.route.isEmpty()) {
            Text(
                text = stringResource(R.string.nfc_route_empty),
                fontSize = 13.sp,
                color = AlarmColors.TextDim,
            )
        } else {
            Grid(state.route) { number ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(AlarmColors.SurfaceRaised, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = number.toString(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlarmColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/** Действие над маршрутом: один знак в квадрате, без подписи. */
@Composable
private fun RouteAction(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(AlarmColors.SurfaceRaised, RoundedCornerShape(9.dp))
            .border(1.dp, AlarmColors.Outline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 16.sp, color = AlarmColors.Accent)
    }
}

// ──────────────────────────────── кирпичики ────────────────────────────────

/** Сколько меток помещается в ряд по ширине шторки. */
private const val PER_ROW = 5

/**
 * Метки и «＋» одной сеткой.
 *
 * «＋» стоит последним в ряду и прижат к правому краю (владелец, 2026-08-18):
 * отдельная строка под ним занимала место ни за чем. В полный ряд шестым он не
 * влезает по ширине, поэтому там переезжает на следующую строку — так же вправо.
 */
@Composable
private fun TagsGrid(tags: List<Int>, onArm: () -> Unit, tag: @Composable (Int) -> Unit) {
    val rows = if (tags.isEmpty()) listOf(emptyList()) else tags.chunked(PER_ROW)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { tag(it) }
                if (index == rows.lastIndex && row.size < PER_ROW) {
                    Spacer(Modifier.weight(1f))
                    AddChip(onArm)
                }
            }
        }
        if (tags.isNotEmpty() && tags.size % PER_ROW == 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AddChip(onArm)
            }
        }
    }
}

/** Ряды по пять: сетка вместо переноса, чтобы не тащить экспериментальный FlowRow. */
@Composable
private fun <T> Grid(items: List<T>, item: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item(it) }
            }
        }
    }
}

/**
 * Метка в списке.
 *
 * Само тело дописывает метку в маршрут — это делают часто. Крестик в углу
 * забывает её насовсем — это делают редко, поэтому он мелкий и в стороне.
 */
@Composable
private fun TagChip(number: Int, onAdd: () -> Unit, onForget: () -> Unit) {
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlarmColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, AlarmColors.Outline, RoundedCornerShape(16.dp))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = AlarmColors.TextPrimary,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(AlarmColors.Background, CircleShape)
                .border(1.dp, AlarmColors.Outline, CircleShape)
                .clickable(onClick = onForget),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", fontSize = 13.sp, lineHeight = 13.sp, color = AlarmColors.TextSecondary)
        }
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(AlarmColors.Accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", fontSize = 24.sp, lineHeight = 24.sp, color = Color.White)
    }
}

@Composable
private fun WaitingPanel(onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlarmColors.SurfaceRaised, RoundedCornerShape(16.dp))
            .border(1.dp, AlarmColors.Accent, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.nfc_waiting),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AlarmColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.nfc_waiting_cancel), color = AlarmColors.Accent, fontSize = 14.sp)
        }
    }
}

/** Какую метку сейчас приложили. Держится до следующего касания — это способ опознать наклейку. */
@Composable
private fun TouchBanner(touched: NfcTouch) {
    val known = touched !is NfcTouch.Unknown
    val text = when (touched) {
        is NfcTouch.Known -> stringResource(R.string.nfc_touch_known, touched.number)
        is NfcTouch.Added -> stringResource(R.string.nfc_touch_added, touched.number)
        NfcTouch.Unknown -> stringResource(R.string.nfc_touch_unknown)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (known) AlarmColors.Good.copy(alpha = 0.12f) else AlarmColors.SurfaceRaised,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (known) AlarmColors.Good else AlarmColors.TextSecondary,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = AlarmColors.TextSecondary,
    )
}

@Composable
private fun Note(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlarmColors.AccentSoft.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, fontSize = 13.sp, color = AlarmColors.TextPrimary)
    }
}
