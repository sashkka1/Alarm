package com.sasha.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.sasha.alarm.core.PoseFrame
import com.sasha.alarm.core.PoseProblem
import com.sasha.alarm.core.PushupCounter
import com.sasha.alarm.core.TimeFormat
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.ui.AlarmKey
import com.sasha.alarm.ui.AlarmScreen
import com.sasha.alarm.ui.NfcAlarmScreen
import com.sasha.alarm.ui.PoseSkeletonUi
import com.sasha.alarm.ui.ReactionCircleUi
import kotlinx.coroutines.delay
import java.time.ZoneId
import com.sasha.alarm.ui.R as UiR

/**
 * Экран испытания — один состав на всех, кто его рисует.
 *
 * Рисуют его двое: [AlarmActivity] обычным путём и [OverlayWindow], когда активити
 * потеряли. Раньше заслонка показывала заглушку с надписью «выполните задание» —
 * то есть ровно в тот момент, когда человек и так ушёл с экрана, испытание
 * становилось невыполнимым, и оставалось только ждать дедлайна. Теперь состав
 * общий: экран возвращается сразу с испытанием, каким бы путём он ни вернулся
 * (решение владельца 2026-08-16).
 *
 * Состояние берётся из [AlarmRuntime], то есть у обоих оно одно и то же: начатое
 * в активити решение продолжается на заслонке с того же места.
 */
@Composable
fun AlarmContent(
    /**
     * Картинка с камеры для отжиманий. Активити отдаёт настоящую, заслонка — пустую:
     * камеру держит экран, а не окно поверх него.
     */
    cameraContent: @Composable () -> Unit,
    onExit: () -> Unit,
) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(1_000L)
        }
    }

    // Испытание метками идёт своим экраном: на нём только шапка, лента маршрута
    // и номер следующей метки — ни клавиатуры, ни кружков.
    NfcRuntime.run?.let { nfc ->
        NfcAlarmScreen(
            timeText = time,
                route = nfc.route,
            done = nfc.done,
            scan = NfcRuntime.lastScan,
            scanTick = NfcRuntime.scanTick,
            preview = AlarmRuntime.preview,
            onExit = onExit,
        )
        return
    }

    AlarmScreen(
        timeText = time,
        challenge = AlarmRuntime.challenge,
        task = AlarmRuntime.session.current,
        answer = AlarmRuntime.answer,
        solved = AlarmRuntime.session.solved,
        total = AlarmRuntime.session.total,
        reactionProgress = AlarmRuntime.reactionProgress,
        reactionHits = AlarmRuntime.reactionHits,
        pushupReps = AlarmRuntime.pushupReps,
        pushupTarget = AlarmRuntime.pushupTarget,
        pushupHint = pushupHint(AlarmRuntime.pushupProblem),
        pushupSkeleton = AlarmRuntime.poseFrame?.toSkeleton(),
        cameraContent = cameraContent,
        reactionCircles = AlarmRuntime.reactionCircles.map {
            ReactionCircleUi(it.id, it.x, it.y)
        },
        onReactionHit = { AlarmRuntime.onReactionHit(it) },
        wrongTick = AlarmRuntime.wrongTick,
        preview = AlarmRuntime.preview,
        circleVisible = AlarmRuntime.circleVisible,
        circleX = AlarmRuntime.circleX,
        circleY = AlarmRuntime.circleY,
        onKey = { AlarmRuntime.onKey(it.toRuntimeKey()) },
        onCircleTap = { AlarmRuntime.onCircleTap() },
        onExit = onExit,
    )
}

/**
 * Что написать под картинкой с камеры.
 *
 * Ровно тот же текст произносится вслух: во время отжиманий человек смотрит в пол,
 * и надпись он увидит только когда встанет, то есть уже поздно. Голос — основной
 * канал, экран — дубль для тех, кто смотрит.
 */
@Composable
private fun pushupHint(problem: PoseProblem): String = stringResource(
    when (problem) {
        PoseProblem.NO_POSE -> UiR.string.pushup_problem_no_pose
        PoseProblem.TOO_CLOSE -> UiR.string.pushup_problem_too_close
        PoseProblem.TOO_FAR -> UiR.string.pushup_problem_too_far
        PoseProblem.SHOULDERS_HIDDEN -> UiR.string.pushup_problem_shoulders
        PoseProblem.ELBOWS_HIDDEN -> UiR.string.pushup_problem_elbows
        PoseProblem.WRISTS_HIDDEN -> UiR.string.pushup_problem_wrists
        PoseProblem.ARMS_DISAGREE -> UiR.string.pushup_problem_arms
        PoseProblem.NOT_LOW_ENOUGH -> UiR.string.pushup_problem_low
        PoseProblem.NONE -> UiR.string.pushup_problem_none
    },
)

/**
 * Точки модели — в то, что рисует экран.
 *
 * ⚠️ Координата X зеркалится. Картинку с фронтальной камеры система показывает
 * зеркально — так человек привык себя видеть, — а кадр в модель уходит как есть.
 * Без зеркала скелет лёг бы на другую половину экрана.
 */
private fun PoseFrame.toSkeleton() = PoseSkeletonUi(
    points = points.map { point -> point.copy(x = 1f - point.x) },
    aspect = aspect,
    overlay = AlarmRuntime.pushupOverlay,
)

private fun currentTime(): String =
    TimeFormat.clockAt(AndroidClock.nowMillis(), ZoneId.systemDefault())

/** Клавиша экрана — во внутреннее представление. Ordinal у обоих совпадает по порядку. */
private fun AlarmKey.toRuntimeKey(): AlarmRuntime.Key = when (this) {
    AlarmKey.DELETE -> AlarmRuntime.Key.DELETE
    AlarmKey.ENTER -> AlarmRuntime.Key.ENTER
    else -> AlarmRuntime.Key.entries[ordinal]
}
