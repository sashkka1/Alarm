package com.sasha.alarm.core

/**
 * Громкость звонка в каждый момент времени.
 *
 * Две силы тянут её в разные стороны:
 *  - нарастание: со временем громкость сама ползёт вверх;
 *  - зелёная кнопка: каждое нажатие сбивает её на [QUIET_STEP_PERCENT].
 *
 * Сбитое копится, а нарастание продолжает идти — поэтому чтобы держать будильник
 * тихим, кнопку надо жать снова и снова. Перестал — снова громко.
 *
 * ⚠️ Сбитое **ограничено снизу нулём** и не может уйти в минус. Без этого получался
 * невидимый долг: на нуле громкости кнопка продолжала «снимать» проценты, и потом
 * нарастанию приходилось сначала отыграть их обратно — со стороны выглядело так,
 * будто громкость вообще перестала подниматься.
 */
object VolumeCurve {

    /** Кружок появляется через случайную паузу в этих границах. */
    const val QUIET_PAUSE_MIN_MS = 3_000L
    const val QUIET_PAUSE_MAX_MS = 5_000L

    /** И сбивает случайную величину в этих границах. */
    const val QUIET_STEP_MIN_PERCENT = 2
    const val QUIET_STEP_MAX_PERCENT = 4

    /**
     * Какую долю набежавшего кружок может забрать за раз.
     *
     * Ради этого числа он и существует именно таким: **громкость обязана расти, даже
     * если жать по кружку каждый раз.** Без потолка арифметика не сходится — на
     * медленном нарастании за паузу набегает меньше процента, а кружок снимает
     * два-четыре, и звук намертво стоит в нуле. Потолок делает обещание «всё равно
     * растёт» верным при любых настройках нарастания.
     */
    const val QUIET_MAX_SHARE = 0.7

    /** Сколько процентов набежало нарастанием к моменту [elapsedMs]. */
    fun grown(settings: SoundSettings, elapsedMs: Long): Long =
        elapsedMs.coerceAtLeast(0L) * settings.percentPerSecondTenths / 10_000L

    fun percentAt(settings: SoundSettings, elapsedMs: Long, quietDeduction: Int): Int {
        val raw = settings.startVolumePercent + grown(settings, elapsedMs) - quietDeduction
        return raw.coerceIn(0L, 100L).toInt()
    }

    /**
     * Сколько будет сбито после очередного нажатия по кружку.
     *
     * Два ограничения, и оба обязательны:
     *  - **не ниже нуля**: иначе копится невидимый долг, и потом нарастание долго
     *    отыгрывает его вместо того, чтобы поднимать громкость;
     *  - **не больше [QUIET_MAX_SHARE] от набежавшего с прошлого нажатия**: иначе на
     *    медленном нарастании кружок съедает больше, чем прибывает, и звук навсегда
     *    остаётся в нуле.
     *
     * @param step сколько просит снять этот тап (случайное число в границах шага)
     * @param grownSinceLastTapPercent сколько процентов набежало нарастанием с прошлого нажатия
     */
    fun deductionAfterTap(
        settings: SoundSettings,
        elapsedMs: Long,
        quietDeduction: Int,
        step: Int,
        grownSinceLastTapPercent: Int,
    ): Int {
        val allowed = minOf(step.toLong(), (grownSinceLastTapPercent * QUIET_MAX_SHARE).toLong())
        val everything = settings.startVolumePercent + grown(settings, elapsedMs)
        val wanted = quietDeduction + allowed.coerceAtLeast(0L)
        return minOf(wanted, everything).coerceAtLeast(0L).toInt()
    }

    /** Громкость для проигрывателя: 0.0..1.0. */
    fun playerVolume(percent: Int): Float = percent.coerceIn(0, 100) / 100f
}
