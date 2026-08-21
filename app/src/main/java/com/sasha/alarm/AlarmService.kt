package com.sasha.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.util.Log
import com.sasha.alarm.core.Challenge
import com.sasha.alarm.core.EffortVolume
import com.sasha.alarm.core.MathGenerator
import com.sasha.alarm.core.ReactionMeter
import com.sasha.alarm.core.ReactionSettings
import com.sasha.alarm.core.MathRules
import com.sasha.alarm.core.MathSession
import com.sasha.alarm.core.PoseDiagnosis
import com.sasha.alarm.core.PoseFrame
import com.sasha.alarm.core.PoseProblem
import com.sasha.alarm.core.PushupCounter
import com.sasha.alarm.core.PushupPhase
import com.sasha.alarm.core.PushupState
import com.sasha.alarm.core.RepOutcome
import com.sasha.alarm.core.SoundSettings
import com.sasha.alarm.core.VictoryStats
import com.sasha.alarm.core.VolumeCurve
import kotlin.random.Random
import com.sasha.alarm.platform.AlarmNotifications
import com.sasha.alarm.platform.AlarmPlayer
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AlarmVibrator
import com.sasha.alarm.platform.AndroidClock
import com.sasha.alarm.platform.DeviceOwner
import com.sasha.alarm.platform.MelodyStore
import com.sasha.alarm.platform.Permissions
import com.sasha.alarm.platform.Speaker
import com.sasha.alarm.platform.SystemAlarmVolume
import com.sasha.alarm.ui.R as UiR

/**
 * Сервис, который ведёт звонок.
 *
 * Обязанности: не дать системе выгрузить процесс, поднять экран (а если экран
 * не поднялся — оверлей), играть мелодию с нарастанием громкости и вибрировать.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: OverlayWindow? = null

    private val player by lazy { AlarmPlayer(this) }
    private val vibrator by lazy { AlarmVibrator(this) }
    private val melodyStore by lazy { MelodyStore(this) }
    private val systemVolume by lazy { SystemAlarmVolume(this) }
    /**
     * Голос. Отдельная ссылка на `lazy` — чтобы при остановке не поднимать движок
     * синтеза только ради того, чтобы тут же его выключить: испытания, кроме
     * отжиманий, к нему не обращаются вовсе.
     *
     * ⚠️ Напрямую не зовётся: все реплики идут через [say], который молчит при
     * выключенном звуке.
     */
    private val speakerLazy = lazy { Speaker(this) }
    private val speaker by speakerLazy

    /**
     * Сказать вслух — если звук вообще включён.
     *
     * Выключенный звук выключает и голос (решение владельца 2026-08-16): тумблер
     * «Звук» означает тишину, а не «тишину, кроме подсказок». Он же тумблер для
     * проверок, и говорящее приложение при выключенном звуке — это неожиданность.
     *
     * Проверка живёт здесь одна на все реплики: раскидай её по местам вызова —
     * однажды забудешь в новом.
     */
    private fun say(text: String, nowMillis: Long) {
        if (!sound.enabled) return
        speaker.say(text = text, nowMillis = nowMillis)
    }
    private val statusBarBlocker by lazy { StatusBarBlocker(this) }

    private var sound: SoundSettings = SoundSettings.DEFAULT
    private var startedAtMillis: Long = 0L
    private var deadlineMillis: Long = 0L
    /** Сколько процентов громкости сбито кружком. Ниже нуля не уходит. */
    private var quietDeduction: Int = 0

    /** Сколько было набежало нарастанием на момент прошлого тапа — чтобы считать прирост между тапами. */
    private var grownAtLastTap: Long = 0L

    /** Когда последний раз звали активити обратно. Чтобы не звать её каждую проверку. */
    private var lastLaunchAtMillis: Long = 0L

    /**
     * Несёт ли уведомление полноэкранное намерение прямо сейчас.
     *
     * Нужно, чтобы не переписывать уведомление каждые сто пятьдесят миллисекунд:
     * трогаем его только когда экран появился или пропал.
     */
    private var notificationRaisesScreen: Boolean = true

    private val random = Random(System.nanoTime())
    private var reaction: ReactionSettings = ReactionSettings.DEFAULT
    private var lastSpawnAt: Long = 0L
    private val bornAt = mutableMapOf<Long, Long>()

    /** Упущенные кружки — только для итога на экране победы. Пойманные считает [AlarmRuntime]. */
    private var reactionMisses: Int = 0

    /**
     * Когда человек в последний раз пошёл вниз. 0 — ни разу.
     * Пока это было недавно, звонок приглушён и не растёт.
     */
    private var effortMovedAtMillis: Long = 0L

    /** Сколько всего времени звонок простоял приглушённым — на это нарастание не идёт. */
    private var effortHoldMillis: Long = 0L

    /** Трясёт ли телефон прямо сейчас. Нужно, чтобы не перезапускать узор каждый такт. */
    private var vibrating: Boolean = false

    /** Когда последний раз считали громкость — чтобы копить простой по факту, а не по такту. */
    private var lastVolumeAtMillis: Long = 0L

    /** Разбор кадра: что держится сейчас, что уже сказано и когда. */
    private var pendingProblem: PoseProblem? = null
    private var pendingSinceMillis: Long = 0L
    private var spokenProblem: PoseProblem? = null
    private var spokenAtMillis: Long = 0L

    /**
     * Когда началось испытание отжиманиями. Первые [POSE_WARMUP_MS] кадры не считаем.
     *
     * Пока камера наводится и модель хватает первого человека, точки прыгают по
     * кадру, и счётчик успевал выдать несколько повторов до того, как владелец
     * вообще лёг на пол (владелец, 2026-08-18: «в начале мне сразу 3 засчитало»).
     */
    private var poseStartedAtMillis: Long = 0L

    /**
     * Кружок выныривает в случайном месте через случайную паузу.
     *
     * Случайность и в паузе, и в месте — чтобы его нельзя было ждать пальцем
     * на одной точке: чтобы держать громкость низкой, приходится следить за экраном.
     */
    private val showCircle = Runnable {
        AlarmRuntime.circleX = 0.12f + random.nextFloat() * 0.76f
        AlarmRuntime.circleY = 0.12f + random.nextFloat() * 0.62f
        AlarmRuntime.circleVisible = true
    }

    /**
     * Пересчёт громкости.
     *
     * Чаще, чем раз в секунду, ради возврата после отжиманий: посекундными
     * ступенями плавный подъём слышен как лесенка, а он должен быть незаметным.
     */
    private val tick = object : Runnable {
        override fun run() {
            applyVolume()
            handler.postDelayed(this, VOLUME_TICK_MS)
        }
    }

    /**
     * Держит экран тревоги на месте — первый слой блокировки.
     *
     * Проверка не разовая, а постоянная, и в этом весь смысл: закрепления экрана на
     * HyperOS 3 не существует, поэтому «нельзя уйти» приходится делать иначе — уйти
     * можно, но через мгновение поверх всего снова лежит наш экран.
     *
     * Оверлей выбран потому, что это окно, а не активити: запрет на запуск активити
     * из фона к нему не относится вовсе.
     *
     * Заслонку, пока подключена служба специальных возможностей, кладёт она — её окно
     * живучее и приходит по событию, а не по таймеру. **Но возвращать активити и будить
     * экран продолжает сервис в любом случае** (2026-08-16): раньше он при подключённой
     * службе не делал вообще ничего, и погашенный кнопкой питания экран не возвращался
     * сам — оставалось время разблокировать телефон и уйти в систему.
     */
    private val keepOnScreen = object : Runnable {
        override fun run() {
            handler.postDelayed(this, KEEP_INTERVAL_MS)

            if (!AlarmRuntime.alarmActive) return
            syncNotification()
            if (AlarmActivity.isShowing) {
                overlay?.hide()
                overlay = null
                return
            }

            // Экрана нет — значит с него ушли или его погасили. Оба случая лечатся
            // одинаково: разбудить и позвать активити обратно.
            wakeScreen()
            relaunchAlarmScreen()

            if (AlarmRuntime.accessibilityActive) return
            if (overlay != null) return
            if (!Settings.canDrawOverlays(this@AlarmService)) {
                Log.e(TAG, "экрана нет, а разрешения на оверлей нет — вернуть нечем")
                return
            }

            Log.w(TAG, "экран тревоги пропал — закрываю заслонкой и возвращаю активити")
            overlay = OverlayWindow(
                context = this@AlarmService,
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            ).also { it.show() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(AlarmNotifications.NOTIFICATION_ID, buildNotification())

        val state = AlarmStateStore(applicationContext).read()
        val run = state.run
        if (run == null) {
            // Система могла перезапустить сервис после того, как тревога уже снята.
            Log.i(TAG, "тревоги нет, сервис останавливается")
            stopSelf()
            return START_NOT_STICKY
        }

        sound = state.sound
        startedAtMillis = run.startedAtMillis
        deadlineMillis = run.deadlineMillis
        quietDeduction = 0
        grownAtLastTap = 0L
        effortMovedAtMillis = 0L
        effortHoldMillis = 0L
        lastVolumeAtMillis = 0L

        AlarmRuntime.onCircleTap = { quiet() }
        AlarmRuntime.onKey = { onKey(it) }
        AlarmRuntime.preview = run.preview
        AlarmRuntime.answer = ""
        // Итог прошлого испытания к новой тревоге отношения не имеет.
        AlarmRuntime.victory = null
        AlarmRuntime.alarmActive = true
        // Испытание ставит startChallenge: оно может подменить выбранное на примеры,
        // если выполнить выбранное сейчас нечем.
        startChallenge(state)
        scheduleCircle()

        acquireWakeLock()
        startSound()
        run {
            // Блокировка включена всегда (решение владельца 2026-08-14): тумблер убран,
            // экран обязан блокировать всё, что может.
            // С правами владельца устройства шторка отключается по-настоящему;
            // без них остаётся только прикрыть её полоской.
            DeviceOwner.engageHardLock(this)
            if (!DeviceOwner.isActive(this) && Settings.canDrawOverlays(this)) statusBarBlocker.show()
        }
        launchAlarmScreen()

        handler.removeCallbacks(keepOnScreen)
        handler.postDelayed(keepOnScreen, FIRST_CHECK_MS)
        handler.removeCallbacks(tick)
        handler.post(tick)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        vibrator.stop()
        vibrating = false
        if (speakerLazy.isInitialized()) speaker.release()
        systemVolume.restore()
        statusBarBlocker.hide()
        overlay?.hide()
        overlay = null
        AlarmRuntime.reset()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    /** Тап по кружку: минус случайный шаг и пауза до следующего появления. */
    private fun quiet() {
        val now = AndroidClock.nowMillis()
        val elapsed = rampElapsed(now)
        val grownNow = VolumeCurve.grown(sound, elapsed)
        val grownSinceLastTap = (grownNow - grownAtLastTap).coerceAtLeast(0L).toInt()
        grownAtLastTap = grownNow

        val step = random.nextInt(
            VolumeCurve.QUIET_STEP_MIN_PERCENT,
            VolumeCurve.QUIET_STEP_MAX_PERCENT + 1,
        )
        quietDeduction = VolumeCurve.deductionAfterTap(
            settings = sound,
            elapsedMs = elapsed,
            quietDeduction = quietDeduction,
            step = step,
            grownSinceLastTapPercent = grownSinceLastTap,
        )

        AlarmRuntime.circleVisible = false
        applyVolume()
        scheduleCircle()
        Log.i(TAG, "тише: сбито всего $quietDeduction%, громкость ${AlarmRuntime.volumePercent}%")
    }

    private fun scheduleCircle() {
        handler.removeCallbacks(showCircle)

        // На отжиманиях зелёного кружка нет: руки на полу, нажать его нечем, а
        // лежал бы он поверх картинки с камеры — ровно там, где человек смотрит
        // на себя. Тишину здесь зарабатывают движением, а не пальцем.
        if (AlarmRuntime.challenge == Challenge.PUSHUPS) return

        val pause = VolumeCurve.QUIET_PAUSE_MIN_MS +
            random.nextLong(VolumeCurve.QUIET_PAUSE_MAX_MS - VolumeCurve.QUIET_PAUSE_MIN_MS + 1)
        handler.postDelayed(showCircle, pause)
    }

    /**
     * Запустить испытание.
     *
     * Пока готовы только примеры; NFC и отжимания ждут своего часа, и до тех пор
     * ведут себя как примеры, чтобы экран не оказался пустым.
     *
     * Испытание на внимание идёт **поверх** любого задания, а не вместо него:
     * кружки ловятся параллельно решению.
     */
    private fun startChallenge(state: com.sasha.alarm.core.AlarmState) {
        reaction = state.reaction
        AlarmRuntime.reactionProgress = ReactionMeter.START_PERCENT
        AlarmRuntime.reactionCircles = emptyList()
        AlarmRuntime.reactionHits = 0
        AlarmRuntime.onReactionHit = { id -> onReactionHit(id) }
        reactionMisses = 0
        bornAt.clear()
        handler.removeCallbacks(reactionTick)

        AlarmRuntime.pushupReps = 0
        AlarmRuntime.pushupTarget = state.pushups.count
        AlarmRuntime.pushupOverlay = state.pushups.overlay
        AlarmRuntime.pushupModel = state.pushups.model
        AlarmRuntime.pushupState = PushupState.START
        // Отсчёт прогрева: пока камера наводится, повторы не считаем.
        poseStartedAtMillis = AndroidClock.nowMillis()
        AlarmRuntime.pushupVisible = false
        AlarmRuntime.pushupProblem = PoseProblem.NO_POSE
        AlarmRuntime.poseFrame = null
        // Кадры приходят из потока анализа камеры, а состояние экрана и снятие
        // тревоги — дело главного потока. Перекладываем здесь, в одном месте.
        AlarmRuntime.onPoseFrame = { frame -> handler.post { onPoseFrame(frame) } }
        spokenProblem = null
        // Только если движок уже поднимали: при выключенном звуке его не должно быть
        // вовсе, и забывать ему нечего.
        if (speakerLazy.isInitialized()) speaker.forget()

        // Испытание, которое невозможно выполнить, — это не испытание, а тишина до
        // дедлайна (P0 №7). Отжимания без разрешения на камеру считать нечем, метки
        // без собранного маршрута проходить нечего: и то и другое уходит в примеры.
        val challenge = when {
            state.challenge == Challenge.PUSHUPS && !Permissions.cameraAllowed(this) -> {
                Log.e(TAG, "нет разрешения на камеру — отжимания невыполнимы, иду по примерам")
                Challenge.MATH
            }

            state.challenge == Challenge.NFC && !state.nfc.ready -> {
                Log.w(TAG, "маршрут меток не собран — иду по примерам")
                Challenge.MATH
            }

            else -> state.challenge
        }
        AlarmRuntime.challenge = challenge

        when (challenge) {
            Challenge.REACTION -> handler.post(reactionTick)

            // Кадры приносит экран — только у него есть жизненный цикл, к которому
            // привязывается камера. Здесь остаётся сказать, с чего начать.
            Challenge.PUSHUPS -> say(
                text = getString(UiR.string.pushup_problem_start),
                nowMillis = AndroidClock.nowMillis(),
            )

            else -> if (AlarmRuntime.session.tasks.isEmpty() || AlarmRuntime.session.isComplete) {
                AlarmRuntime.session = MathSession(
                    tasks = MathGenerator.generate(state.math, random),
                    solved = 0,
                )
            }
        }
    }

    /**
     * Пульс испытания на внимание: рождает новые кружки и хоронит просроченные.
     *
     * Промах — это не «нажал мимо», а «дал кружку погаснуть»: так игру нельзя
     * просидеть, ничего не делая, шкала сама поползёт вверх.
     */
    private val reactionTick = object : Runnable {
        override fun run() {
            handler.postDelayed(this, REACTION_TICK_MS)
            val now = AndroidClock.nowMillis()

            val expired = AlarmRuntime.reactionCircles.filter {
                now - (bornAt[it.id] ?: now) > ReactionMeter.LIFETIME_MS
            }
            if (expired.isNotEmpty()) {
                expired.forEach { bornAt.remove(it.id) }
                AlarmRuntime.reactionCircles = AlarmRuntime.reactionCircles - expired.toSet()
                reactionMisses += expired.size
                repeat(expired.size) {
                    AlarmRuntime.reactionProgress =
                        ReactionMeter.onMiss(reaction, AlarmRuntime.reactionProgress)
                }
            }

            if (now - lastSpawnAt >= ReactionMeter.SPAWN_INTERVAL_MS) {
                lastSpawnAt = now
                // Шарики рождаются строго ниже шкалы (владелец, 2026-08-16):
                // накрыв её собой, они прятали единственный признак прогресса.
                val circle = AlarmRuntime.ReactionCircle(
                    id = now,
                    x = 0.12f + random.nextFloat() * 0.76f,
                    y = REACTION_TOP + random.nextFloat() * (0.86f - REACTION_TOP),
                )
                bornAt[circle.id] = now
                AlarmRuntime.reactionCircles = AlarmRuntime.reactionCircles + circle
            }
        }
    }

    private fun onReactionHit(id: Long) {
        val circle = AlarmRuntime.reactionCircles.firstOrNull { it.id == id } ?: return
        bornAt.remove(id)
        AlarmRuntime.reactionCircles = AlarmRuntime.reactionCircles - circle
        AlarmRuntime.reactionHits++
        AlarmRuntime.reactionProgress = ReactionMeter.onHit(reaction, AlarmRuntime.reactionProgress)

        if (ReactionMeter.done(AlarmRuntime.reactionProgress)) {
            Log.i(TAG, "шкала внимания дошла до нуля — снимаю тревогу")
            win()
        }
    }

    /**
     * Новый кадр с камеры при испытании отжиманиями.
     *
     * Кадр, которому нельзя верить, не сбрасывает счёт и не двигает фазу: пропуск
     * не должен ни засчитывать повтор, ни отнимать засчитанный.
     */
    private fun onPoseFrame(frame: PoseFrame?) {
        val now = AndroidClock.nowMillis()
        val angle = frame?.let { PushupCounter.frameAngle(it) }

        AlarmRuntime.poseFrame = frame
        AlarmRuntime.pushupVisible = angle != null

        // Прогрев. Кадр показываем, но в счёт не берём: пока камера наводится,
        // точки прыгают, и счётчик успевает выдать повторы до начала подхода.
        if (now - poseStartedAtMillis < POSE_WARMUP_MS) return

        val before = AlarmRuntime.pushupState
        val tick = PushupCounter.next(before, angle, now)
        val after = tick.state
        AlarmRuntime.pushupState = after

        // «Работает» — это уход вниз, а не любое шевеление: лежать в упоре можно
        // сколько угодно, и тишину за это выдавать не за что.
        if (after.phase == PushupPhase.DOWN && before.phase != PushupPhase.DOWN) {
            effortMovedAtMillis = now
        }

        val problem = PoseDiagnosis.of(frame, after)
        AlarmRuntime.pushupProblem = problem

        // Вердикт на каждый подъём (владелец, 2026-08-18): засчитан — голое число,
        // не засчитан — причина. Причина повторяется, даже если та же самая: молчание
        // на второй одинаковой ошибке читается как «на этот раз зачли».
        when (tick.outcome) {
            RepOutcome.COUNTED -> {
                AlarmRuntime.pushupReps = after.reps
                Log.i(TAG, "отжиманий ${after.reps} из ${AlarmRuntime.pushupTarget}")

                // Голый счёт, без единого лишнего слова (решение владельца 2026-08-16):
                // это отсчёт под нагрузкой, а не диктор. Он важнее любой придирки и
                // обрывает её на полуслове — сказанное вслед за «шесть» уже неважно.
                say(text = after.reps.toString(), nowMillis = now)
                spokenProblem = null
                pendingProblem = null
                effortMovedAtMillis = now

                if (after.reps >= AlarmRuntime.pushupTarget) {
                    Log.i(TAG, "отжимания сделаны — снимаю тревогу")
                    win()
                }
                return
            }

            RepOutcome.NOT_LOW_ENOUGH -> {
                sayVerdict(getString(UiR.string.pushup_problem_low), now)
                return
            }

            RepOutcome.TOO_SOON -> {
                sayVerdict(getString(UiR.string.pushup_verdict_too_soon), now)
                return
            }

            RepOutcome.NONE -> Unit
        }

        // Постоянные придирки — только когда считать нечем. Пока угол считается,
        // человек уже всё делает правильно, и говорить ему по ходу движения нечего:
        // за поток команд владелец не слышал собственный счёт (2026-08-18).
        if (angle != null) {
            spokenProblem = null
            pendingProblem = null
            return
        }
        speakProblem(problem, now)
    }

    /**
     * Вердикт по подъёму: почему не засчитали.
     *
     * Говорится всегда, без выдержек: это ответ на конкретное движение, а не
     * фоновая жалоба. Сбрасывает накопленную придирку — иначе она догонит сразу
     * следом и получится две фразы на один подъём.
     */
    private fun sayVerdict(text: String, nowMillis: Long) {
        pendingProblem = null
        spokenProblem = null
        say(text = text, nowMillis = nowMillis)
    }

    /**
     * Сказать вслух, что не так с кадром.
     *
     * Две задержки, и обе нужны: [PROBLEM_HOLD_MS] не даёт озвучивать мигание
     * распознавания, [PROBLEM_REPEAT_MS] не даёт долбить одним и тем же, пока
     * человек идёт переставлять телефон.
     */
    private fun speakProblem(problem: PoseProblem, nowMillis: Long) {
        if (problem == PoseProblem.NONE) {
            spokenProblem = null
            return
        }
        if (problem != pendingProblem) {
            pendingProblem = problem
            pendingSinceMillis = nowMillis
            return
        }
        if (nowMillis - pendingSinceMillis < PROBLEM_HOLD_MS) return
        if (problem == spokenProblem && nowMillis - spokenAtMillis < PROBLEM_REPEAT_MS) return

        spokenProblem = problem
        spokenAtMillis = nowMillis
        say(getString(problem.textRes()), nowMillis)
    }

    private fun PoseProblem.textRes(): Int = when (this) {
        PoseProblem.NO_POSE -> UiR.string.pushup_problem_no_pose
        PoseProblem.TOO_CLOSE -> UiR.string.pushup_problem_too_close
        PoseProblem.TOO_FAR -> UiR.string.pushup_problem_too_far
        PoseProblem.SHOULDERS_HIDDEN -> UiR.string.pushup_problem_shoulders
        PoseProblem.ELBOWS_HIDDEN -> UiR.string.pushup_problem_elbows
        PoseProblem.WRISTS_HIDDEN -> UiR.string.pushup_problem_wrists
        PoseProblem.ARMS_DISAGREE -> UiR.string.pushup_problem_arms
        PoseProblem.NOT_LOW_ENOUGH -> UiR.string.pushup_problem_low
        PoseProblem.NONE -> UiR.string.pushup_problem_none
    }

    /**
     * Испытание выполнено.
     *
     * Итог собирается **до** снятия тревоги: снятие останавливает сервис, а тот
     * обнуляет [AlarmRuntime]. После этого экран покажет победу вместо того, чтобы
     * закрыться. Снятие сторожем по дедлайну сюда не заходит — там победы не было.
     */
    private fun win() {
        AlarmRuntime.victory = VictoryStats(
            challenge = AlarmRuntime.challenge,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = AndroidClock.nowMillis(),
            mathSolved = AlarmRuntime.session.solved,
            mathTotal = AlarmRuntime.session.total,
            mathWrong = AlarmRuntime.wrongTick,
            reactionHits = AlarmRuntime.reactionHits,
            reactionMisses = reactionMisses,
            pushupReps = AlarmRuntime.pushupReps,
            pushupTarget = AlarmRuntime.pushupTarget,
        )
        AlarmController.dismiss(this)
    }

    /** Нажата клавиша на цифровой клавиатуре экрана решения. */
    private fun onKey(key: AlarmRuntime.Key) {
        when (key) {
            AlarmRuntime.Key.DELETE -> AlarmRuntime.answer = AlarmRuntime.answer.dropLast(1)

            AlarmRuntime.Key.ENTER -> {
                val before = AlarmRuntime.session
                val after = MathRules.submit(before, AlarmRuntime.answer)
                AlarmRuntime.answer = ""
                if (after.solved == before.solved) {
                    AlarmRuntime.wrongTick++
                    Log.i(TAG, "ответ неверный")
                } else {
                    AlarmRuntime.session = after
                    Log.i(TAG, "решено ${after.solved} из ${after.total}")
                    if (after.isComplete) {
                        Log.i(TAG, "все примеры решены — снимаю тревогу")
                        win()
                    }
                }
            }

            else -> {
                val digit = key.digit ?: return
                if (AlarmRuntime.answer.length < MAX_ANSWER_LENGTH) {
                    AlarmRuntime.answer += digit.toString()
                }
            }
        }
    }

    private fun startSound() {
        AlarmRuntime.volumePercent = sound.startVolumePercent

        if (!sound.enabled) {
            // Тихий режим для проверок: экран, блокировка и дедлайн работают как обычно,
            // громкость считается и видна, но в динамик ничего не идёт.
            Log.i(TAG, "звук выключен в настройках — иду молча")
            if (sound.vibrate) {
            vibrator.start()
            vibrating = true
        }
            return
        }

        // Наши проценты — доля от системной громкости будильника: при системном нуле
        // любая наша громкость даёт тишину.
        systemVolume.raiseIfSilent()

        val uri = melodyStore.resolve(sound.melody)
        if (uri == null) {
            Log.e(TAG, "мелодии нет — играть нечего")
        } else {
            player.start(uri, VolumeCurve.playerVolume(sound.startVolumePercent))
        }
        if (sound.vibrate) {
            vibrator.start()
            vibrating = true
        }
    }

    private fun applyVolume() {
        val now = AndroidClock.nowMillis()

        // Время, которое человек провёл в работе, нарастанию не засчитывается:
        // иначе за подход набежало бы столько, что после паузы звук вернулся бы
        // не туда, откуда стих, а заметно выше.
        if (!EffortVolume.rampRuns(effortMovedAtMillis, now)) {
            val since = (now - lastVolumeAtMillis).coerceIn(0L, MAX_HOLD_STEP_MS)
            if (lastVolumeAtMillis != 0L) effortHoldMillis += since
        }
        lastVolumeAtMillis = now

        val elapsed = rampElapsed(now)
        val base = VolumeCurve.percentAt(sound, elapsed, quietDeduction)
        val factor = EffortVolume.factor(effortMovedAtMillis, now)
        val percent = EffortVolume.percent(base, factor)

        AlarmRuntime.volumePercent = percent
        AlarmRuntime.remainingMillis = (deadlineMillis - now).coerceAtLeast(0L)

        player.setVolume(VolumeCurve.playerVolume(percent))
        applyVibration(quiet = factor < 1f)
    }

    /**
     * Вибрация идёт ровно тогда, когда звонок не приглушён.
     *
     * ⚠️ Приглушили за работу — вибрация замолкает вместе с музыкой (владелец,
     * 2026-08-19). Причина не в удобстве: от вибрации телефон **уезжает по полу**, и
     * выставленный ракурс камеры сбивается. А сбитый ракурс — это отказ считать
     * повторы, то есть испытание, которое нельзя выполнить.
     *
     * Возвращается только вместе с полной громкостью: пока идёт плавный возврат,
     * телефон должен стоять неподвижно.
     *
     * ⚠️ У остальных испытаний это ничего не меняет: [effortMovedAtMillis] двигают
     * только кадры с камеры, и без них множитель всегда равен единице.
     */
    private fun applyVibration(quiet: Boolean) {
        if (!sound.vibrate) return
        val shouldVibrate = !quiet
        // Сравнение обязательно: повторный start каждые 200 мс перезапускал бы узор
        // с начала, и вместо ритма получилось бы сплошное жужжание.
        if (shouldVibrate == vibrating) return
        vibrating = shouldVibrate
        if (shouldVibrate) vibrator.start() else vibrator.stop()
    }

    /** Сколько времени нарастание реально шло: за вычетом того, что человек работал. */
    private fun rampElapsed(nowMillis: Long): Long =
        (nowMillis - startedAtMillis - effortHoldMillis).coerceAtLeast(0L)

    private fun buildNotification(raiseScreen: Boolean = true) = AlarmNotifications.build(
        context = this,
        alarmActivity = AlarmActivity::class.java,
        icon = R.drawable.ic_alarm,
        title = getString(UiR.string.notification_title),
        text = getString(UiR.string.notification_text),
        raiseScreen = raiseScreen,
    ).also {
        AlarmNotifications.ensureChannel(
            context = this,
            name = getString(UiR.string.notification_channel_name),
            description = getString(UiR.string.notification_channel_description),
        )
    }

    /**
     * Держать полноэкранное намерение ровно тогда, когда оно нужно.
     *
     * Экран на месте — намерения нет, и системе нечего показать плашкой поверх него.
     * Экран потеряли — намерение возвращается вместе с задачей поднять его заново.
     */
    private fun syncNotification() {
        val raiseScreen = !AlarmActivity.isShowing
        if (raiseScreen == notificationRaisesScreen) return
        notificationRaisesScreen = raiseScreen
        try {
            getSystemService(NotificationManager::class.java)
                .notify(AlarmNotifications.NOTIFICATION_ID, buildNotification(raiseScreen))
        } catch (e: Exception) {
            // Тревога важнее опрятности уведомления.
            Log.w(TAG, "уведомление не обновилось", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "alarm:screen",
        ).apply { acquire(WAKE_LOCK_MS) }
    }

    /**
     * Вернуть погашенный экран.
     *
     * Кнопка питания сильнее любого захвата: нажатие усыпляет телефон, и уже взятый
     * захват экран обратно не зажигает. Разбудить можно только новым захватом с
     * `ACQUIRE_CAUSES_WAKEUP` — поэтому старый отпускается и берётся заново.
     */
    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        Log.w(TAG, "экран погасили — зажигаю обратно")
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        acquireWakeLock()
    }

    /**
     * Позвать активити обратно, но не чаще, чем нужно.
     *
     * Проверка идёт часто, а запуск активити — работа не бесплатная, и дёргать её
     * по нескольку раз в секунду незачем: система всё равно поднимает её не мгновенно.
     */
    private fun relaunchAlarmScreen() {
        val now = AndroidClock.nowMillis()
        if (now - lastLaunchAtMillis < RELAUNCH_INTERVAL_MS) return
        lastLaunchAtMillis = now
        launchAlarmScreen()
    }

    private fun launchAlarmScreen() {
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "startActivity отказал, ждём оверлей", e)
        }
    }

    private companion object {
        const val TAG = "AlarmService"

        /**
         * Через сколько проверяем в первый раз. Достаточно, чтобы активити успела
         * стартовать, и мало, чтобы человек не увидел пустоту вместо будильника.
         */
        const val FIRST_CHECK_MS = 250L

        /** Как часто проверяем, что экран всё ещё на месте. */
        const val KEEP_INTERVAL_MS = 150L

        /** Как часто зовём активити обратно, пока её нет. */
        const val RELAUNCH_INTERVAL_MS = 600L

        const val WAKE_LOCK_MS = 10 * 60 * 1000L
        const val MAX_ANSWER_LENGTH = 6

        /**
         * Сколько претензия должна продержаться, прежде чем её произнести.
         *
         * Распознавание мигает, и без выдержки голос комментировал бы каждое
         * дрожание вместо того, чтобы сказать одно дельное указание.
         *
         * Поднято с 900 мс до двух с половиной секунд (владелец, 2026-08-18): теперь
         * претензия звучит, только когда считать нечем **уже несколько секунд**, а не
         * при каждой заминке распознавания. За частой скороговоркой не было слышно
         * собственный счёт, а он важнее любого указания.
         */
        const val PROBLEM_HOLD_MS = 2_500L

        /** Как часто можно повторять одну и ту же претензию. */
        const val PROBLEM_REPEAT_MS = 6_000L

        /**
         * Сколько кадров после запуска камеры не идёт в счёт.
         *
         * Пока камера наводится и модель хватается за первого попавшегося человека,
         * точки прыгают по кадру: счётчик успевал выдать несколько повторов до того,
         * как владелец лёг на пол (2026-08-18: «в начале мне сразу 3 засчитало»).
         */
        const val POSE_WARMUP_MS = 1_500L

        /** Как часто пересчитывается громкость. */
        const val VOLUME_TICK_MS = 200L

        /** Выше этой доли экрана шарики внимания не появляются: там шкала и часы. */
        const val REACTION_TOP = 0.22f

        /**
         * Наибольший кусок времени, который может уйти в «простой нарастания» за раз.
         *
         * Страховка от заснувшего процесса и от перевода часов вперёд: без потолка
         * один такой скачок списал бы часы нарастания разом, и звонок остался бы
         * навсегда тихим — то есть почти тишиной (P0 №7).
         */
        const val MAX_HOLD_STEP_MS = 2_000L

        /** Как часто пересчитываем кружки внимания. */
        const val REACTION_TICK_MS = 120L
    }
}
