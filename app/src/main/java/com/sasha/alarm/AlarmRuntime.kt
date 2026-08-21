package com.sasha.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.MathSession
import com.sasha.alarm.core.VictoryStats
import com.sasha.alarm.core.PoseFrame
import com.sasha.alarm.core.PoseModel
import com.sasha.alarm.core.PoseProblem
import com.sasha.alarm.core.PushupOverlay
import com.sasha.alarm.core.PushupState

/**
 * Мостик между частями приложения, которые ведут тревогу.
 *
 * Сервис, экран, оверлей и служба специальных возможностей живут в одном процессе,
 * поэтому им хватает общего объекта: сервис пишет сюда громкость, ход решения и
 * положение кружка, Compose сам перерисовывает экран.
 *
 * Сторож `:guard` сюда не смотрит — у него своя правда на диске.
 */
object AlarmRuntime {

    var volumePercent by mutableIntStateOf(0)
    var remainingMillis by mutableStateOf(0L)

    /** Чем сейчас выключается будильник. */
    var challenge by mutableStateOf(Challenge.MATH)

    /** Ход решения примеров. Пока не решены все — с экрана не уйти. */
    var session by mutableStateOf(MathSession.EMPTY)

    /**
     * Итог пройденного испытания — он же признак победы для экрана.
     *
     * Ставится сервисом **перед** снятием тревоги и намеренно переживает [reset]:
     * снятие останавливает сервис, а экран победы показывается уже после этого.
     * Обнуляется в начале новой тревоги.
     */
    var victory by mutableStateOf<VictoryStats?>(null)

    /** Испытание на внимание: шкала от 100 до 0 и живые кружки на экране. */
    var reactionProgress by mutableStateOf(100.0)
    var reactionCircles by mutableStateOf(emptyList<ReactionCircle>())

    /** Сколько кружков поймано за эту тревогу. Показывается в шапке экрана. */
    var reactionHits by mutableIntStateOf(0)

    /** Поймали кружок. Подставляет сервис. */
    var onReactionHit: (Long) -> Unit = {}

    /** Кружок на экране: где он и когда родился. Координаты — доли ширины и высоты. */
    data class ReactionCircle(val id: Long, val x: Float, val y: Float)

    /**
     * Испытание отжиманиями: сколько засчитано, сколько нужно и что показать человеку.
     *
     * Кадры с камеры приносит экран (только у него есть жизненный цикл, к которому
     * привязывается камера), а считает их сервис — как и в остальных испытаниях,
     * решение «хватит» принимается в одном месте.
     */
    var pushupReps by mutableIntStateOf(0)
    var pushupTarget by mutableIntStateOf(0)

    /** Чем рисовать себя поверх кадра: фигурой или точками. */
    var pushupOverlay by mutableStateOf(PushupOverlay.FIGURE)

    /** Какой моделью распознавать позу. Камеру поднимает экран, выбор делает сервис. */
    var pushupModel by mutableStateOf(PoseModel.FULL)
    var pushupState by mutableStateOf(PushupState.START)

    /** Виден ли человек в кадре настолько, чтобы вообще считать. */
    var pushupVisible by mutableStateOf(false)

    /** Что не так с тем, что видит камера. Одна причина, самая важная. */
    var pushupProblem by mutableStateOf(PoseProblem.NO_POSE)

    /** Последний разобранный кадр — из него экран рисует скелет. */
    var poseFrame by mutableStateOf<PoseFrame?>(null)


    /** Новый кадр с камеры. Подставляет сервис, зовёт экран. */
    var onPoseFrame: (PoseFrame?) -> Unit = {}

    /** Что набрано на клавиатуре сейчас. */
    var answer by mutableStateOf("")

    /** Счётчик неверных ответов: экран мигает красным, когда он меняется. */
    var wrongTick by mutableIntStateOf(0)

    /** Показ из настроек — значит на экране есть кнопка «Выйти». */
    var preview by mutableStateOf(false)


    /** Кружок, сбивающий громкость: виден ли и где именно (доли ширины и высоты). */
    var circleVisible by mutableStateOf(false)
    var circleX by mutableStateOf(0.5f)
    var circleY by mutableStateOf(0.5f)

    /** Идёт ли тревога прямо сейчас. Ставит [AlarmService]. */
    @Volatile
    var alarmActive: Boolean = false

    /**
     * Служба специальных возможностей подключена и берёт возврат экрана на себя.
     * Пока она жива, сервис свой оверлей не показывает — иначе получилось бы два.
     */
    @Volatile
    var accessibilityActive: Boolean = false

    /**
     * Sleep Cycle зазвонил — служба спецвозможностей должна смахнуть его «Стоп» за
     * владельца. Ставит [SleepCycleListener] в момент, когда услышал/увидел звонок;
     * снимает служба, когда смахнула. Это единственный способ погасить чужой будильник
     * с самого телефона: обычному приложению `force-stop` недоступен, а ввод по кабелю
     * Xiaomi блокирует — жест же служба выполняет системно, без этих ограничений.
     */
    @Volatile
    var foreignDismissRequested: Boolean = false

    /** Тап по кружку. Подставляет сервис, зовёт экран. */
    var onCircleTap: () -> Unit = {}

    /** Нажата цифра, «стереть» или «ввод». Подставляет сервис. */
    var onKey: (Key) -> Unit = {}

    enum class Key { DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5,
        DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9, DELETE, ENTER;

        val digit: Int? get() = if (ordinal <= 9) ordinal else null
    }

    fun reset() {
        volumePercent = 0
        remainingMillis = 0L
        session = MathSession.EMPTY
        answer = ""
        wrongTick = 0
        reactionProgress = 100.0
        reactionCircles = emptyList()
        reactionHits = 0
        onReactionHit = {}
        pushupReps = 0
        pushupTarget = 0
        pushupState = PushupState.START
        pushupVisible = false
        pushupProblem = PoseProblem.NO_POSE
        poseFrame = null
        onPoseFrame = {}
        preview = false
        circleVisible = false
        alarmActive = false
        foreignDismissRequested = false
        onCircleTap = {}
        onKey = {}
    }
}
