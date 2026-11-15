package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.R
import com.projectego.app.data.CharacterFamilyRepository.VictoryEvent
import com.projectego.app.data.CharacterFamilyRepository.VictorySource
import com.projectego.app.domain.CompanionSynergy
import com.projectego.app.domain.GalaxyCatalog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun bondRankLabel(context: Context, rank: Int): String = context.getString(
    when (rank) {
        1 -> R.string.bond_rank_1_name
        2 -> R.string.bond_rank_2_name
        3 -> R.string.bond_rank_3_name
        else -> R.string.bond_rank_4_name
    }
)

internal fun formatBondProgressLine(context: Context, bondXp: Int): String {
    val rank = CompanionSynergy.rankFor(bondXp)
    val rankName = bondRankLabel(context, rank)
    val progress = CompanionSynergy.progressWithin(bondXp)
    return if (progress == null) {
        context.getString(R.string.bond_rank_master_line, rank, rankName)
    } else {
        context.getString(R.string.bond_rank_line, rank, rankName, progress.first, progress.second)
    }
}

/**
 * Full-screen Crystal Liquid Glass celebration ceremony surfaced after a verified
 * quest payout or clean >= 10m standalone Focus completion.
 *
 * Uses a festive aurora/confetti choreography, staggered counter roll-ups,
 * liquid XP bar sheen, and tactile multi-beat haptics while triggering zero audio,
 * honoring reduced-motion settings, and strictly emitting `onDismiss(event)` at most once.
 */
class CompanionVictoryOverlay(
    context: Context,
    private val ui: AliveComponents
) : FrameLayout(context) {

    var currentEvent: VictoryEvent? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val activeAnimators = mutableListOf<Animator>()
    private val scheduledRunnables = mutableListOf<Runnable>()
    private var dismissCallback: ((VictoryEvent) -> Unit)? = null
    private var dismissing = false
    private var dismissDispatched = false

    private var scrimView: VictoryScrimView? = null
    private var cardView: AliveLiquidGlassCard? = null
    private var cardSheenView: CardSheenView? = null
    private var confettiView: VictoryConfettiView? = null
    private var mascotCrest: CelebrationCrestView? = null
    private var flareRowView: View? = null
    private var titleView: View? = null
    private var synergyView: View? = null
    private var leftTileView: View? = null
    private var rightTileView: View? = null
    private var leftValueView: TextView? = null
    private var bondValueView: TextView? = null
    private var bondSectionView: View? = null
    private var bondBarView: BondProgressBarView? = null
    private var ctaView: View? = null

    private var leftValueFormatter: ((Int) -> String)? = null
    private var leftValueTarget: Int = 0
    private var bondDeltaTarget: Int = 0

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        setOnClickListener { dismiss(animated = true) }
    }

    fun show(
        host: ViewGroup,
        event: VictoryEvent,
        remainingQueuedCount: Int = 0,
        onDismiss: (VictoryEvent) -> Unit
    ) {
        resetState()
        removeAllViews()
        currentEvent = event
        dismissCallback = onDismiss
        dismissing = false
        dismissDispatched = false

        val memberSpec = GalaxyCatalog.members.firstOrNull { it.id == event.memberId }
            ?: GalaxyCatalog.members.firstOrNull { it.id == GalaxyCatalog.DEFAULT_COMPANION_ID }
            ?: GalaxyCatalog.members.first()
        val companionSpec = GalaxyCatalog.members.firstOrNull { it.id == event.companionId }
            ?: memberSpec
        val memberHue = ContextCompat.getColor(context, memberSpec.hueRes)
        val memberTitle = context.getString(memberSpec.titleRes)
        val companionTitle = context.getString(companionSpec.titleRes)

        val scrim = VictoryScrimView(context, memberHue, ui.palette.rewardMid).apply {
            isClickable = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        scrimView = scrim
        // Child index 0 must stay the full-bleed scrim for hit-testing and test invariants.
        addView(scrim)

        val backdropPainter = CosmicVictoryPainter(context, memberHue, ui.palette.rewardMid)
        val glassCard = AliveLiquidGlassCard(context).apply {
            tag = TAG_CARD
            isClickable = true
            isFocusable = true
            setOnClickListener { /* Consume card-surface taps so only scrim/CTA dismisses. */ }
            attachBackdropSources(this@CompanionVictoryOverlay, null) { canvas, w, h ->
                backdropPainter.draw(canvas, w, h)
            }
        }
        cardView = glassCard

        val content = buildCardContent(
            event = event,
            memberHue = memberHue,
            memberDrawable = memberSpec.drawableRes,
            memberTitle = memberTitle,
            companionTitle = companionTitle,
            remainingQueuedCount = remainingQueuedCount
        )
        glassCard.addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        val sheenOverlay = CardSheenView(context, ui.palette.rewardPale).apply {
            isClickable = false
            isFocusable = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        cardSheenView = sheenOverlay
        glassCard.addView(sheenOverlay)

        val cappedHost = CappedWidthContainer(context, dpInt(MAX_CARD_WIDTH_DP)).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                glassCard,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
        }

        val hPad = dpInt(18f)
        val vPad = dpInt(24f)
        setPadding(0, 0, 0, 0)
        addView(
            cappedHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(hPad, vPad, hPad, vPad)
            }
        )

        val confetti = VictoryConfettiView(
            context = context,
            colors = intArrayOf(
                ColorUtils.blendARGB(ui.palette.rewardMid, 0xFFFFFFFF.toInt(), 0.25f),
                memberHue,
                ui.palette.fieldBlossom,
                ContextCompat.getColor(context, R.color.flat_field_lime),
                ui.palette.fieldSky,
                0xFFFFFFFF.toInt()
            ),
            seed = event.eventId.hashCode()
        ).apply {
            isClickable = false
            isFocusable = false
            translationZ = dp(8f)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        confettiView = confetti
        addView(confetti)

        val animate = animationsEnabled()
        val targetShare = CompanionSynergy.shareToNext(event.bondXpAfter)
        val startShare = CompanionSynergy.shareToNext(event.bondXpBefore)
        bondBarView?.progressShare = if (animate && event.bondXpAfter > event.bondXpBefore) startShare else targetShare

        if (parent != host) {
            (parent as? ViewGroup)?.removeView(this)
            host.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        if (animate) {
            prepareAndStartCeremony(event, startShare, targetShare)
        } else {
            scrim.alpha = 1f
            scrim.ceremonyProgress = 1f
            glassCard.alpha = 1f
            glassCard.scaleX = 1f
            glassCard.scaleY = 1f
            glassCard.translationY = 0f
            mascotCrest?.snapResting()
            snapSettledFigures()
            bondBarView?.progressShare = targetShare
        }
    }

    fun dismiss(animated: Boolean = true) {
        if (dismissing || dismissDispatched) return
        dismissing = true
        cancelRunningEffects()

        if (!animated || !isAttachedToWindow || !animationsEnabled()) {
            finishDismiss()
            return
        }

        val card = cardView
        val scrim = scrimView
        val confetti = confettiView
        val outAnimators = mutableListOf<Animator>()
        scrim?.let {
            outAnimators.add(ObjectAnimator.ofFloat(it, View.ALPHA, it.alpha, 0f).setDuration(EXIT_MS))
        }
        confetti?.let {
            outAnimators.add(ObjectAnimator.ofFloat(it, View.ALPHA, it.alpha, 0f).setDuration(EXIT_MS))
        }
        card?.let {
            outAnimators.add(ObjectAnimator.ofFloat(it, View.ALPHA, it.alpha, 0f).setDuration(EXIT_MS))
            outAnimators.add(ObjectAnimator.ofFloat(it, View.SCALE_X, it.scaleX, 0.93f).setDuration(EXIT_MS))
            outAnimators.add(ObjectAnimator.ofFloat(it, View.SCALE_Y, it.scaleY, 0.93f).setDuration(EXIT_MS))
            outAnimators.add(ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, it.translationY, -dp(12f)).setDuration(EXIT_MS))
        }
        if (outAnimators.isEmpty()) {
            finishDismiss()
            return
        }
        AnimatorSet().apply {
            playTogether(outAnimators)
            interpolator = DecelerateInterpolator()
            var cancelled = false
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    activeAnimators.remove(this@apply)
                    if (!cancelled) finishDismiss()
                }
            })
            activeAnimators.add(this)
            start()
        }
    }

    override fun onDetachedFromWindow() {
        cancelRunningEffects()
        overlay.clear()
        super.onDetachedFromWindow()
    }

    internal fun currentBondProgressShare(): Float = bondBarView?.progressShare ?: 0f

    internal fun hasPendingTimersOrAnimatorsForTesting(): Boolean =
        scheduledRunnables.isNotEmpty() || activeAnimators.any { it.isRunning || it.isStarted }

    private fun prepareAndStartCeremony(
        event: VictoryEvent,
        startShare: Float,
        targetShare: Float
    ) {
        val scrim = scrimView
        val card = cardView
        val crest = mascotCrest
        scrim?.alpha = 0f
        card?.alpha = 0f
        card?.scaleX = 0.87f
        card?.scaleY = 0.87f
        card?.translationY = dp(28f)

        val staggerViews = listOf(
            flareRowView to 95L,
            titleView to 120L,
            synergyView to 145L,
            leftTileView to 175L,
            rightTileView to 220L,
            bondSectionView to 250L,
            ctaView to 285L
        ).mapNotNull { (view, delay) -> view?.let { it to delay } }
        staggerViews.forEach { (v, _) ->
            v.alpha = 0f
            v.translationY = dp(14f)
            v.scaleX = 0.94f
            v.scaleY = 0.94f
        }

        // Beat 0: tactile entrance tap + explosive card spring
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val entrance = mutableListOf<Animator>()
        scrim?.let { s ->
            entrance.add(ObjectAnimator.ofFloat(s, View.ALPHA, 0f, 1f).setDuration(SCRIM_ENTER_MS))
            val auraAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = AURA_DURATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener { s.ceremonyProgress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        s.ceremonyProgress = 1f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                    }
                })
            }
            activeAnimators.add(auraAnim)
            auraAnim.start()
        }

        card?.let { c ->
            val cardIn = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(c, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(c, View.SCALE_X, 0.87f, 1f),
                    ObjectAnimator.ofFloat(c, View.SCALE_Y, 0.87f, 1f),
                    ObjectAnimator.ofFloat(c, View.TRANSLATION_Y, dp(28f), 0f)
                )
                duration = CARD_ENTER_MS
                interpolator = OvershootInterpolator(1.1f)
            }
            entrance.add(cardIn)
        }

        staggerViews.forEach { (view, delay) ->
            entrance.add(
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, dp(14f), 0f),
                        ObjectAnimator.ofFloat(view, View.SCALE_X, 0.94f, 1f),
                        ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.94f, 1f)
                    )
                    startDelay = delay
                    duration = 260L
                    interpolator = OvershootInterpolator(1.25f)
                }
            )
        }

        if (entrance.isNotEmpty()) {
            AnimatorSet().apply {
                playTogether(entrance)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                    }
                })
                activeAnimators.add(this)
                start()
            }
        }

        // Beat 1: Companion crest burst + playful elastic wiggle
        crest?.startCelebration(activeAnimators)

        // Holographic collector card foil sheen pass
        cardSheenView?.let { sheen ->
            val sheenAnim = ValueAnimator.ofFloat(-0.25f, 1.35f).apply {
                startDelay = 120L
                duration = 760L
                interpolator = DecelerateInterpolator(1.3f)
                addUpdateListener { sheen.sheenProgress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        sheen.sheenProgress = -1f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        sheen.sheenProgress = -1f
                    }
                })
            }
            activeAnimators.add(sheenAnim)
            sheenAnim.start()
        }

        // Rolling reward counter figures (settles cleanly within 600ms)
        if (leftValueTarget > 0 || bondDeltaTarget > 0) {
            leftValueView?.text = leftValueFormatter?.invoke(0) ?: ""
            bondValueView?.text = context.getString(R.string.victory_tile_bond_delta, 0)
            val counterAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 190L
                duration = 380L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { anim ->
                    val frac = anim.animatedValue as Float
                    leftValueView?.text = leftValueFormatter?.invoke(
                        ((leftValueTarget * frac) + 0.45f).toInt().coerceIn(0, leftValueTarget)
                    ) ?: ""
                    bondValueView?.text = context.getString(
                        R.string.victory_tile_bond_delta,
                        ((bondDeltaTarget * frac) + 0.45f).toInt().coerceIn(0, bondDeltaTarget)
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        snapSettledFigures()
                        pulseRewardTiles()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        snapSettledFigures()
                    }
                })
            }
            activeAnimators.add(counterAnim)
            counterAnim.start()
        }

        // Beat 2..4: Liquid XP progress bar sweep + meniscus glow + settlement pulse
        val bar = bondBarView
        if (bar != null && event.bondXpAfter > event.bondXpBefore) {
            val shimmerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 180L
                duration = 1150L
                interpolator = LinearInterpolator()
                addUpdateListener { bar.sheenPhase = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        bar.sheenPhase = -1f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        bar.sheenPhase = -1f
                    }
                })
            }
            activeAnimators.add(shimmerAnim)
            shimmerAnim.start()

            val barAnim = if (event.rankAfter > event.rankBefore) {
                val toFull = ValueAnimator.ofFloat(startShare, 1f).apply {
                    duration = 230L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { bar.progressShare = it.animatedValue as Float }
                }
                val toTarget = ValueAnimator.ofFloat(0f, targetShare).apply {
                    duration = 320L
                    interpolator = DecelerateInterpolator(1.4f)
                    addUpdateListener { bar.progressShare = it.animatedValue as Float }
                }
                AnimatorSet().apply { playSequentially(toFull, toTarget) }
            } else {
                ValueAnimator.ofFloat(startShare, targetShare).apply {
                    duration = 460L
                    interpolator = DecelerateInterpolator(1.4f)
                    addUpdateListener { bar.progressShare = it.animatedValue as Float }
                }
            }
            var cancelled = false
            barAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    activeAnimators.remove(barAnim)
                    if (!cancelled) {
                        bar.progressShare = targetShare
                        triggerBarSettlementFlash(bar, event.rankAfter > event.rankBefore)
                    }
                }
            })
            barAnim.startDelay = 190L
            activeAnimators.add(barAnim)
            barAnim.start()
        }

        // Three-wave screen confetti & 4-point star fountain synced to settled mascot crown coords
        confettiView?.let { cf ->
            lateinit var launchConfetti: Runnable
            launchConfetti = Runnable {
                scheduledRunnables.remove(launchConfetti)
                if (!isAttachedToWindow || dismissing || !animationsEnabled()) return@Runnable
                val crest = mascotCrest
                val card = cardView
                val crestCenter = crest?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { centerInOverlay(it) }
                val crestCx = crestCenter?.first ?: (width * 0.5f)
                val crestCy = crestCenter?.second ?: (height * 0.36f)
                val leftX: Float
                val rightX: Float
                val shoulderY: Float
                if (card != null && card.width > 0 && card.height > 0) {
                    val cardCenter = centerInOverlay(card)
                    val cardLeft = cardCenter.first - card.width * 0.5f
                    val cardTop = cardCenter.second - card.height * 0.5f
                    leftX = cardLeft + card.width * 0.04f
                    rightX = cardLeft + card.width * 0.96f
                    shoulderY = cardTop + card.height * 0.30f
                } else {
                    leftX = crestCx - dp(150f)
                    rightX = crestCx + dp(150f)
                    shoulderY = crestCy + dp(24f)
                }
                scrimView?.setSpotlightCenter(crestCx, crestCy)
                cf.configureAndStart(
                    fountainX = crestCx,
                    fountainY = crestCy,
                    leftShoulderX = leftX,
                    rightShoulderX = rightX,
                    shoulderY = shoulderY,
                    screenHeight = height.toFloat().coerceAtLeast(dp(640f)),
                    screenWidth = width.toFloat().coerceAtLeast(dp(360f))
                )
            }
            scheduledRunnables.add(launchConfetti)
            post(launchConfetti)

            val confettiAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CONFETTI_DURATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener { cf.progress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        cf.visibility = View.GONE
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                    }
                })
            }
            activeAnimators.add(confettiAnim)
            confettiAnim.start()
        }

        // Tactile 3-beat haptic crescendo (audio-free satisfaction)
        scheduleHaptic(110L, confirmHapticConstant())
        scheduleHaptic(260L, HapticFeedbackConstants.CLOCK_TICK)
        val celebratoryMilestone = event.synergyMatched ||
            event.newlyAwakened ||
            event.rankAfter > event.rankBefore ||
            event.growthStageUp
        if (celebratoryMilestone || event.bondXpAfter > event.bondXpBefore) {
            scheduleHaptic(560L, confirmHapticConstant())
        }
    }

    private fun triggerBarSettlementFlash(bar: BondProgressBarView, rankedUp: Boolean) {
        if (!isAttachedToWindow || dismissing || !animationsEnabled()) return
        val flash = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = if (rankedUp) 420L else 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                bar.flashStrength = f
                val s = 1f + f * (if (rankedUp) 0.28f else 0.16f)
                bar.scaleY = s
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    activeAnimators.remove(this@apply)
                    bar.flashStrength = 0f
                    bar.scaleY = 1f
                }

                override fun onAnimationCancel(animation: Animator) {
                    activeAnimators.remove(this@apply)
                    bar.flashStrength = 0f
                    bar.scaleY = 1f
                }
            })
        }
        activeAnimators.add(flash)
        flash.start()
    }

    private fun scheduleHaptic(delayMs: Long, feedbackConstant: Int) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            scheduledRunnables.remove(runnable)
            if (isAttachedToWindow && !dismissing) {
                performHapticFeedback(feedbackConstant)
            }
        }
        scheduledRunnables.add(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    private fun centerInOverlay(target: View): Pair<Float, Float> {
        var x = target.width * 0.5f
        var y = target.height * 0.5f
        var cur: View? = target
        while (cur != null && cur !== this) {
            x += cur.left
            y += cur.top
            cur = cur.parent as? View
        }
        return x to y
    }

    private fun pulseRewardTiles() {
        if (!isAttachedToWindow || dismissing || !animationsEnabled()) return
        listOfNotNull(leftTileView, rightTileView).forEachIndexed { idx, tile ->
            val peak = 1.045f
            val pulse = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(tile, View.SCALE_X, 1f, peak, 1f),
                    ObjectAnimator.ofFloat(tile, View.SCALE_Y, 1f, peak, 1f)
                )
                startDelay = idx * 45L
                duration = 210L
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        tile.scaleX = 1f
                        tile.scaleY = 1f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimators.remove(this@apply)
                        tile.scaleX = 1f
                        tile.scaleY = 1f
                    }
                })
            }
            activeAnimators.add(pulse)
            pulse.start()
        }
    }

    private fun snapSettledFigures() {
        leftValueView?.text = leftValueFormatter?.invoke(leftValueTarget) ?: leftValueView?.text
        bondValueView?.text = context.getString(R.string.victory_tile_bond_delta, bondDeltaTarget)
    }

    private fun confirmHapticConstant(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }

    private fun buildCardContent(
        event: VictoryEvent,
        @ColorInt memberHue: Int,
        memberDrawable: Int,
        memberTitle: String,
        companionTitle: String,
        remainingQueuedCount: Int
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setPadding(dpInt(20f), dpInt(22f), dpInt(20f), dpInt(20f))

        // 1. Kicker
        addView(
            TextView(context).apply {
                text = context.getString(
                    if (event.source == VictorySource.QUEST) R.string.victory_kicker_quest
                    else R.string.victory_kicker_focus
                )
                setTextColor(MUTED_ON_GLASS)
                applyGlassShadow(this, 2f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                letterSpacing = 0.12f
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
        )

        // 2. Milestone flare badges
        val jewelAnchor = ContextCompat.getColor(context, R.color.flat_deep2)
        val flares = buildList {
            if (event.newlyAwakened) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_awakened),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(jewelAnchor, ui.palette.flatMint, 0.56f), 228),
                        ColorUtils.setAlphaComponent(ui.palette.flatMint, 240)
                    )
                )
            }
            if (event.rankAfter > event.rankBefore) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_rank_up, event.rankAfter),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(jewelAnchor, ui.palette.rewardMid, 0.58f), 232),
                        ColorUtils.setAlphaComponent(ui.palette.rewardPale, 245)
                    )
                )
            }
            if (event.growthStageUp) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_level_up, event.growthLevelAfter),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(jewelAnchor, ui.palette.fieldLilac, 0.56f), 228),
                        ColorUtils.setAlphaComponent(ui.palette.fieldSky, 240)
                    )
                )
            }
        }
        if (flares.isNotEmpty()) {
            addView(ui.vgap(dpInt(8f)))
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                flares.forEachIndexed { idx, (label, fill, stroke) ->
                    if (idx > 0) addView(ui.hgap(dpInt(6f)))
                    addView(flarePill(label, fill, stroke))
                }
            }
            flareRowView = row
            addView(row)
        }

        // 3. Celebratory mascot crest (104dp sunburst + pulse rings + pixel sprite)
        addView(ui.vgap(dpInt(10f)))
        val crest = CelebrationCrestView(
            context = context,
            memberHue = memberHue,
            goldHue = ui.palette.rewardMid,
            sparkleColor = ui.palette.rewardPale,
            drawableRes = memberDrawable,
            title = memberTitle
        ).apply {
            val size = dpInt(104f)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        mascotCrest = crest
        addView(crest)

        // 4. Activity headline
        addView(ui.vgap(dpInt(8f)))
        val titleTv = TextView(context).apply {
            text = event.title
            setTextColor(TEXT_ON_GLASS)
            applyGlassShadow(this, 3.5f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.08f)
            includeFontPadding = false
        }
        titleView = titleTv
        addView(titleTv)

        // 5. Synergy match badge
        if (event.synergyMatched) {
            addView(ui.vgap(dpInt(8f)))
            val syn = flarePill(
                label = context.getString(R.string.victory_synergy_active, memberTitle),
                face = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(jewelAnchor, memberHue, 0.56f), 220),
                stroke = ColorUtils.setAlphaComponent(memberHue, 240)
            )
            synergyView = syn
            addView(syn)
        }

        // 6. Two equal-height frosted sub-tiles (Bank/Focus + Bond XP) with counter roll-up
        addView(ui.vgap(dpInt(16f)))
        val leftLabel: String
        val leftNote: String
        when {
            event.source == VictorySource.QUEST -> {
                leftLabel = context.getString(R.string.victory_tile_bank_label)
                leftValueTarget = (event.baseMinutes + event.synergyBonusMinutes).toInt().coerceAtLeast(0)
                leftValueFormatter = { v -> context.getString(R.string.victory_tile_minutes_gain, v) }
                leftNote = if (event.synergyBonusMinutes > 0L) {
                    context.getString(R.string.victory_tile_synergy_note, event.synergyBonusMinutes)
                } else {
                    context.getString(R.string.victory_tile_quest_base_note)
                }
            }
            event.synergyBonusMinutes > 0L -> {
                leftLabel = context.getString(R.string.victory_tile_bank_label)
                leftValueTarget = event.synergyBonusMinutes.toInt().coerceAtLeast(0)
                leftValueFormatter = { v -> context.getString(R.string.victory_tile_minutes_gain, v) }
                leftNote = context.getString(R.string.victory_tile_synergy_note, event.synergyBonusMinutes)
            }
            else -> {
                leftLabel = context.getString(R.string.victory_tile_focus_label)
                leftValueTarget = event.durationMinutes.toInt().coerceAtLeast(0)
                leftValueFormatter = { v -> context.getString(R.string.victory_tile_minutes_duration, v) }
                leftNote = context.getString(R.string.victory_tile_focus_note, event.durationMinutes)
            }
        }
        val leftFormatted = leftValueFormatter?.invoke(leftValueTarget) ?: ""
        val (leftTile, leftValTv) = frostedTile(leftLabel, leftFormatted, leftNote, memberHue)
        leftTileView = leftTile
        leftValueView = leftValTv

        bondDeltaTarget = event.bondDelta.coerceAtLeast(0)
        val (bondTile, bondValTv) = frostedTile(
            label = context.getString(R.string.victory_tile_bond_label, companionTitle),
            figure = context.getString(R.string.victory_tile_bond_delta, bondDeltaTarget),
            note = context.getString(R.string.bond_tile_sub, event.rankAfter, bondRankLabel(context, event.rankAfter)),
            tint = ui.palette.rewardMid
        )
        rightTileView = bondTile
        bondValueView = bondValTv

        val pairRow = ui.equalRow().apply {
            addView(leftTile.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(ui.hgap(dpInt(10f)))
            addView(bondTile.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        addView(pairRow)

        // 7. Bond rank line & 10dp luminous liquid meter
        addView(ui.vgap(dpInt(14f)))
        val bondBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(
                TextView(context).apply {
                    text = formatBondProgressLine(context, event.bondXpAfter)
                    setTextColor(BODY_ON_GLASS)
                    applyGlassShadow(this, 2.5f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }
            )
            addView(ui.vgap(dpInt(8f)))
            val meter = BondProgressBarView(
                context = context,
                trackColor = METER_TRACK,
                memberHue = memberHue,
                goldHue = ui.palette.rewardMid
            ).apply {
                tag = TAG_BOND_BAR
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpInt(10f)
                )
            }
            bondBarView = meter
            addView(meter)
        }
        bondSectionView = bondBlock
        addView(bondBlock)

        // 8. Tactile pearlescent CTA pill
        addView(ui.vgap(dpInt(18f)))
        val ctaLabel = if (remainingQueuedCount > 0) {
            context.getString(R.string.victory_cta_next, remainingQueuedCount)
        } else {
            context.getString(R.string.victory_cta_done)
        }
        val cta = TextView(context).apply {
            tag = TAG_CTA
            text = ctaLabel
            setTextColor(ui.palette.flatInk)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = dpInt(48f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFEDF3FF.toInt())
            ).apply {
                cornerRadius = dp(999f)
                setStroke(dpInt(1f), 0x80FFFFFF.toInt())
            }
            val h = dpInt(16f)
            val v = dpInt(12f)
            setPadding(h, v, h, v)
            isClickable = true
            isFocusable = true
            AliveMotion.attachPressSpring(this, pressedScale = 0.94f, hapticOnDown = true)
            setOnClickListener {
                performHapticFeedback(confirmHapticConstant())
                dismiss(animated = true)
            }
        }
        ctaView = cta
        addView(cta)
    }

    private fun flarePill(
        label: String,
        @ColorInt face: Int,
        @ColorInt stroke: Int
    ): TextView = TextView(context).apply {
        text = label
        setTextColor(TEXT_ON_GLASS)
        applyGlassShadow(this, 2f)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.04f
        typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        background = GradientDrawable().apply {
            setColor(face)
            cornerRadius = dp(999f)
            setStroke(dpInt(1.2f), stroke)
        }
        setPadding(dpInt(12f), dpInt(5f), dpInt(12f), dpInt(5f))
    }

    private fun frostedTile(
        label: String,
        figure: String,
        note: String,
        @ColorInt tint: Int
    ): Pair<LinearLayout, TextView> {
        lateinit var valueTv: TextView
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val topFace = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(0xFFFFFFFF.toInt(), tint, 0.55f),
                66
            )
            val bottomFace = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(0xFF1A2152.toInt(), tint, 0.45f),
                48
            )
            val strokeCol = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(0xFFFFFFFF.toInt(), tint, 0.4f),
                128
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(topFace, bottomFace)
            ).apply {
                cornerRadius = dp(16f)
                setStroke(dpInt(1f), strokeCol)
            }
            setPadding(dpInt(12f), dpInt(10f), dpInt(12f), dpInt(10f))
            addView(
                TextView(context).apply {
                    text = label
                    setTextColor(MUTED_ON_GLASS)
                    applyGlassShadow(this, 2f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                }
            )
            addView(ui.vgap(dpInt(3f)))
            valueTv = TextView(context).apply {
                text = figure
                setTextColor(TEXT_ON_GLASS)
                applyGlassShadow(this, 3.5f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                maxLines = 1
                includeFontPadding = false
            }
            addView(valueTv)
            addView(ui.vgap(dpInt(2f)))
            addView(
                TextView(context).apply {
                    text = note
                    setTextColor(BODY_ON_GLASS)
                    applyGlassShadow(this, 2f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                }
            )
        }
        return container to valueTv
    }

    private fun applyGlassShadow(tv: TextView, radiusDp: Float) {
        val d = resources.displayMetrics.density
        tv.setShadowLayer(radiusDp * d, 0f, 1f * d, GLASS_TEXT_SHADOW)
    }

    private fun cancelRunningEffects() {
        scheduledRunnables.toList().forEach { handler.removeCallbacks(it) }
        scheduledRunnables.clear()
        mascotCrest?.cancel()
        activeAnimators.toList().forEach { it.cancel() }
        activeAnimators.clear()
    }

    private fun resetState() {
        cancelRunningEffects()
        scrimView = null
        cardView = null
        cardSheenView = null
        confettiView = null
        mascotCrest = null
        flareRowView = null
        titleView = null
        synergyView = null
        leftTileView = null
        rightTileView = null
        leftValueView = null
        bondValueView = null
        bondSectionView = null
        bondBarView = null
        ctaView = null
        leftValueFormatter = null
        leftValueTarget = 0
        bondDeltaTarget = 0
    }

    private fun finishDismiss() {
        if (dismissDispatched) return
        dismissDispatched = true
        val event = currentEvent
        val cb = dismissCallback
        currentEvent = null
        dismissCallback = null
        overlay.clear()
        (parent as? ViewGroup)?.removeView(this)
        if (event != null) {
            cb?.invoke(event)
        }
    }

    private fun animationsEnabled(): Boolean {
        val sysScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
        return sysScale && ValueAnimator.areAnimatorsEnabled()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dpInt(value: Float): Int = (dp(value) + 0.5f).toInt()

    private class CappedWidthContainer(
        context: Context,
        private val maxWidthPx: Int
    ) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val capped = if (mode != MeasureSpec.UNSPECIFIED && size > maxWidthPx) {
                MeasureSpec.makeMeasureSpec(maxWidthPx, mode)
            } else {
                widthMeasureSpec
            }
            super.onMeasure(capped, heightMeasureSpec)
        }
    }

    /**
     * Full-bleed celebration backdrop behind the card: midnight dim scrim + warm member/gold
     * radial spotlight, slow ceremonial sunburst beams, and two expanding entrance shockwaves.
     */
    private class VictoryScrimView(
        context: Context,
        @param:ColorInt private val memberHue: Int,
        @param:ColorInt private val goldHue: Int
    ) : View(context) {

        private val d = resources.displayMetrics.density
        private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM_COLOR }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val crownHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rayPath = Path()

        private var cachedW = 0
        private var cachedH = 0
        private var focusX = -1f
        private var focusY = -1f

        var ceremonyProgress: Float = 0f
            set(value) {
                val c = value.coerceIn(0f, 1f)
                if (field != c) {
                    field = c
                    invalidate()
                }
            }

        fun setSpotlightCenter(cx: Float, cy: Float) {
            if (focusX != cx || focusY != cy) {
                focusX = cx
                focusY = cy
                cachedW = 0
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = if (focusX >= 0f) focusX else w * 0.5f
            val cy = if (focusY >= 0f) focusY else h * 0.38f
            val cardCy = cy + 68f * d
            if (cachedW != width || cachedH != height) {
                cachedW = width
                cachedH = height
                haloPaint.shader = RadialGradient(
                    cx, cardCy, (w * 0.88f).coerceAtLeast(1f),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(memberHue, goldHue, 0.35f), 116),
                        ColorUtils.setAlphaComponent(memberHue, 46),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                crownHaloPaint.shader = RadialGradient(
                    cx, cy, (w * 0.54f).coerceAtLeast(1f),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(goldHue, 128),
                        ColorUtils.setAlphaComponent(memberHue, 52),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                val rayReach = (w * 0.72f).coerceAtLeast(1f)
                rayPaint.shader = RadialGradient(
                    cx, cy, rayReach,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(goldHue, memberHue, 0.4f), 96),
                        ColorUtils.setAlphaComponent(memberHue, 40),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            canvas.drawRect(0f, 0f, w, h, scrimPaint)

            val p = ceremonyProgress
            val haloFade = (p / 0.16f).coerceIn(0f, 1f)
            if (haloFade > 0f) {
                canvas.save()
                val scale = 0.85f + 0.15f * haloFade
                canvas.scale(scale, scale, cx, cardCy)
                haloPaint.alpha = (255 * haloFade).toInt()
                crownHaloPaint.alpha = (255 * haloFade).toInt()
                canvas.drawRect(0f, 0f, w, h, haloPaint)
                canvas.drawRect(0f, 0f, w, h, crownHaloPaint)
                canvas.restore()
            }

            // Ceremonial 12-beam sunburst flaring brightly on impact then settling into a soft velvet glow
            val rayOpacity = when {
                p < 0.20f -> (p / 0.20f) * 0.86f
                else -> 0.86f - 0.68f * ((p - 0.20f) / 0.80f)
            }.coerceIn(0f, 0.86f)
            if (rayOpacity > 0.02f) {
                val rayLen = w * 0.74f
                val spinDeg = p * 24f
                rayPaint.alpha = (255 * rayOpacity).toInt()
                canvas.save()
                canvas.rotate(spinDeg, cx, cy)
                val beams = 12
                for (i in 0 until beams) {
                    val angle = (2.0 * PI * i) / beams
                    val halfSpread = 0.072
                    val x1 = cx + cos(angle - halfSpread).toFloat() * rayLen
                    val y1 = cy + sin(angle - halfSpread).toFloat() * rayLen
                    val x2 = cx + cos(angle + halfSpread).toFloat() * rayLen
                    val y2 = cy + sin(angle + halfSpread).toFloat() * rayLen
                    rayPath.reset()
                    rayPath.moveTo(cx, cy)
                    rayPath.lineTo(x1, y1)
                    rayPath.lineTo(x2, y2)
                    rayPath.close()
                    canvas.drawPath(rayPath, rayPaint)
                }
                canvas.restore()
            }

            // Expanding celebration shockwaves during entrance beats
            drawRipple(canvas, cx, cy, p, start = 0.03f, end = 0.46f, maxR = 250f * d, color = memberHue, peakAlpha = 185)
            drawRipple(canvas, cx, cy, p, start = 0.12f, end = 0.58f, maxR = 225f * d, color = goldHue, peakAlpha = 165)
        }

        private fun drawRipple(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            p: Float,
            start: Float,
            end: Float,
            maxR: Float,
            @ColorInt color: Int,
            peakAlpha: Int
        ) {
            if (p < start || p > end) return
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t)
            val r = 46f * d + (maxR - 46f * d) * eased
            ringPaint.color = color
            ringPaint.alpha = (peakAlpha * (1f - eased)).toInt().coerceIn(0, 255)
            ringPaint.strokeWidth = (3.4f - 2.4f * eased) * d
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
    }

    /**
     * Celebratory mascot emblem: radiant sunburst aura, double jewel rim, out-blooming
     * pulse rings, 8 crisp 4-point sparkles, and elastic pop-and-wiggle entrance on the sprite.
     */
    private class CelebrationCrestView(
        context: Context,
        @param:ColorInt private val memberHue: Int,
        @param:ColorInt private val goldHue: Int,
        @param:ColorInt private val sparkleColor: Int,
        drawableRes: Int,
        title: String
    ) : FrameLayout(context) {

        private val d = resources.displayMetrics.density
        private val mascotImg: ImageView
        private val localAnimators = mutableListOf<Animator>()

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sunburstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(memberHue, 74)
        }
        private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(memberHue, goldHue, 0.45f), 230)
        }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * d
            color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 155)
        }
        private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = sparkleColor
        }
        private val starPath = Path()
        private var cachedSize = 0

        private var burstProgress: Float = 1f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        init {
            setWillNotDraw(false)
            clipChildren = false
            clipToPadding = false
            mascotImg = ImageView(context).apply {
                setImageResource(drawableRes)
                contentDescription = title
                scaleType = ImageView.ScaleType.FIT_CENTER
                val iconSize = (68f * d + 0.5f).toInt()
                layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
            }
            addView(mascotImg)
        }

        fun startCelebration(sharedTracker: MutableList<Animator>) {
            cancel()
            mascotImg.scaleX = 0.32f
            mascotImg.scaleY = 0.32f
            mascotImg.rotation = -10f
            mascotImg.translationY = 6f * d
            burstProgress = 0f

            val popX = ObjectAnimator.ofFloat(mascotImg, View.SCALE_X, 0.32f, 1f).apply {
                startDelay = 70L
                duration = 380L
                interpolator = OvershootInterpolator(1.65f)
            }
            val popY = ObjectAnimator.ofFloat(mascotImg, View.SCALE_Y, 0.32f, 1f).apply {
                startDelay = 70L
                duration = 380L
                interpolator = OvershootInterpolator(1.65f)
            }
            val hop = ObjectAnimator.ofFloat(mascotImg, View.TRANSLATION_Y, 6f * d, -5f * d, 0f).apply {
                startDelay = 70L
                duration = 360L
                interpolator = DecelerateInterpolator()
            }
            val wiggle = ObjectAnimator.ofFloat(mascotImg, View.ROTATION, -10f, 6.5f, -2.5f, 0f).apply {
                startDelay = 110L
                duration = 420L
                interpolator = DecelerateInterpolator()
            }
            val aura = ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = 60L
                duration = 1650L
                interpolator = LinearInterpolator()
                addUpdateListener { burstProgress = it.animatedValue as Float }
            }

            val set = AnimatorSet().apply {
                playTogether(popX, popY, hop, wiggle, aura)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        localAnimators.remove(this@apply)
                        sharedTracker.remove(this@apply)
                        burstProgress = 1f
                        mascotImg.scaleX = 1f
                        mascotImg.scaleY = 1f
                        mascotImg.rotation = 0f
                        mascotImg.translationY = 0f
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        localAnimators.remove(this@apply)
                        sharedTracker.remove(this@apply)
                    }
                })
            }
            localAnimators.add(set)
            sharedTracker.add(set)
            set.start()
        }

        fun snapResting() {
            cancel()
            burstProgress = 1f
            mascotImg.scaleX = 1f
            mascotImg.scaleY = 1f
            mascotImg.rotation = 0f
            mascotImg.translationY = 0f
        }

        fun cancel() {
            localAnimators.toList().forEach { it.cancel() }
            localAnimators.clear()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w * 0.5f
            val cy = h * 0.5f
            val maxR = minOf(w, h) * 0.5f
            val discR = 40f * d

            if (cachedSize != width) {
                cachedSize = width
                glowPaint.shader = RadialGradient(
                    cx, cy, maxR.coerceAtLeast(1f),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(goldHue, 145),
                        ColorUtils.setAlphaComponent(memberHue, 90),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.56f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            val p = burstProgress
            val fadeIn = (p / 0.14f).coerceIn(0f, 1f)
            glowPaint.alpha = (255 * fadeIn).toInt()
            canvas.drawCircle(cx, cy, maxR, glowPaint)

            // Transient slender flare rays blossoming on entry and fading into calm glow at rest
            val rayEnvelope = if (p < 0.82f) sin((p / 0.82f) * PI).toFloat() else 0f
            val rayAlpha = (145 * rayEnvelope).toInt().coerceIn(0, 255)
            if (rayAlpha > 2) {
                val rayCount = 8
                sunburstPaint.color = ColorUtils.blendARGB(goldHue, 0xFFFFFFFF.toInt(), 0.35f)
                sunburstPaint.alpha = rayAlpha
                val rot = p * 34f
                canvas.save()
                canvas.rotate(rot, cx, cy)
                for (i in 0 until rayCount) {
                    val a = (2.0 * PI * i) / rayCount
                    val outer = maxR * (if (i % 2 == 0) 1.0f else 0.86f)
                    val inner = discR * 0.82f
                    starPath.reset()
                    starPath.moveTo(cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer)
                    starPath.lineTo(cx + cos(a - 0.08).toFloat() * inner, cy + sin(a - 0.08).toFloat() * inner)
                    starPath.lineTo(cx + cos(a + 0.08).toFloat() * inner, cy + sin(a + 0.08).toFloat() * inner)
                    starPath.close()
                    canvas.drawPath(starPath, sunburstPaint)
                }
                canvas.restore()
            }

            // Two quick expanding aura pulse rings
            if (p in 0.04f..0.48f) {
                val t = (p - 0.04f) / 0.44f
                pulsePaint.color = goldHue
                pulsePaint.alpha = (210 * (1f - t)).toInt()
                pulsePaint.strokeWidth = 2.2f * d
                canvas.drawCircle(cx, cy, discR + 14f * d * t, pulsePaint)
            }
            if (p in 0.14f..0.64f) {
                val t = (p - 0.14f) / 0.50f
                pulsePaint.color = memberHue
                pulsePaint.alpha = (180 * (1f - t)).toInt()
                pulsePaint.strokeWidth = 1.8f * d
                canvas.drawCircle(cx, cy, discR + 18f * d * t, pulsePaint)
            }

            // Inner frosted badge + crisp double luminous ring
            canvas.drawCircle(cx, cy, discR, discPaint)
            canvas.drawCircle(cx, cy, discR - 2.6f * d, innerRingPaint)
            canvas.drawCircle(cx, cy, discR, outerRingPaint)

            // 8 small crisp 4-point sparkles winking softly around crest
            val sparkles = 8
            for (i in 0 until sparkles) {
                val angle = -PI / 2.0 + (2.0 * PI * i) / sparkles + 0.22
                val phase = ((p - 0.08f - i * 0.045f) * 1.9f).coerceIn(0f, 1f)
                val pulse = if (p >= 0.99f) {
                    if (i % 2 == 0) 0.55f else 0f
                } else {
                    sin(phase * PI).toFloat()
                }
                if (pulse <= 0.05f) continue
                val dist = discR + (6f + (i % 2) * 5f) * d
                val sx = cx + cos(angle).toFloat() * dist
                val sy = cy + sin(angle).toFloat() * dist
                val r = (4.2f + (i % 2) * 1.6f) * d * pulse
                starPaint.color = if (i % 2 == 0) sparkleColor else ColorUtils.blendARGB(memberHue, 0xFFFFFFFF.toInt(), 0.6f)
                starPaint.alpha = (245 * pulse).toInt().coerceIn(0, 255)
                drawFourPointStar(canvas, sx, sy, r, starPaint, starPath)
            }
        }
    }

    /**
     * One-shot tilted holographic light glint sliding across the face of the frosted glass card.
     */
    private class CardSheenView(
        context: Context,
        @param:ColorInt private val tint: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shaderMatrix = Matrix()
        private var cachedW = 0
        private var cachedH = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Return 0 desired height in pass 1 so WRAP_CONTENT FrameLayout does not stretch to AT_MOST;
            // pass 2 (MATCH_PARENT) still supplies MeasureSpec.EXACTLY with content height.
            setMeasuredDimension(
                resolveSize(0, widthMeasureSpec),
                resolveSize(0, heightMeasureSpec)
            )
        }

        var sheenProgress: Float = -1f
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        override fun onDraw(canvas: Canvas) {
            if (sheenProgress < -0.25f || sheenProgress > 1.35f) return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (cachedW != width || cachedH != height) {
                cachedW = width
                cachedH = height
                val band = w * 0.42f
                paint.shader = LinearGradient(
                    0f, 0f, band, band * 0.48f,
                    intArrayOf(
                        0x00000000,
                        ColorUtils.setAlphaComponent(tint, 56),
                        0x46FFFFFF,
                        ColorUtils.setAlphaComponent(tint, 44),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.34f, 0.50f, 0.66f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            val sweepSpan = w * 1.55f
            val tx = -w * 0.4f + sweepSpan * sheenProgress
            shaderMatrix.setTranslate(tx, -h * 0.1f + (h * 0.6f * sheenProgress))
            paint.shader?.setLocalMatrix(shaderMatrix)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    /**
     * Multi-wave celebration confetti fountain: fluttering 3D ribbons, 4-point geometric stars,
     * and pixel confetti with density-scaled air drag + gravity rendered cleanly via hardware/software Canvas.
     */
    private class VictoryConfettiView(
        context: Context,
        private val colors: IntArray,
        seed: Int
    ) : View(context) {

        private enum class Shape { RIBBON, STAR, PIXEL }

        private class Particle(
            val spawnDelayFrac: Float,
            val lifeFrac: Float,
            val startX: Float,
            val startY: Float,
            val vxDp: Float,
            val vyDp: Float,
            val gravityDp: Float,
            val flutterAmpDp: Float,
            val flutterFreq: Float,
            val baseAngleDeg: Float,
            val spinDegPerSec: Float,
            val tumbleSpeed: Float,
            val sizeDp: Float,
            val aspect: Float,
            val shape: Shape,
            val color: Int
        )

        private val d = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val starPath = Path()
        private var particles: List<Particle> = emptyList()
        private var configured = false
        private val lcgSeed = if (seed == 0) 0x5A17C3 else seed

        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        fun configureAndStart(
            fountainX: Float,
            fountainY: Float,
            leftShoulderX: Float,
            rightShoulderX: Float,
            shoulderY: Float,
            screenWidth: Float,
            screenHeight: Float
        ) {
            if (configured && particles.isNotEmpty()) return
            configured = true
            var state = lcgSeed
            fun nextUnit(): Float {
                state = state * 1664525 + 1013904223
                return ((state ushr 8) and 0xFFFFFF) / 16777216f
            }

            val list = ArrayList<Particle>(102)

            // Wave 1: 52 high-impulse pieces erupting upward & wide past card edges from mascot crown
            for (i in 0 until 52) {
                val spreadNorm = (i / 51f) * 2f - 1f
                val jitter = (nextUnit() - 0.5f) * 0.22f
                val angleRad = (-PI / 2.0) + (spreadNorm + jitter) * 1.32
                val speed = 290f + nextUnit() * 340f
                list.add(
                    Particle(
                        spawnDelayFrac = 0.01f + nextUnit() * 0.04f,
                        lifeFrac = 0.76f + nextUnit() * 0.21f,
                        startX = fountainX + (nextUnit() - 0.5f) * 26f * d,
                        startY = fountainY + (nextUnit() - 0.5f) * 16f * d,
                        vxDp = (cos(angleRad) * speed).toFloat(),
                        vyDp = (sin(angleRad) * speed).toFloat() - 80f,
                        gravityDp = 365f + nextUnit() * 135f,
                        flutterAmpDp = 14f + nextUnit() * 18f,
                        flutterFreq = 7f + nextUnit() * 6f,
                        baseAngleDeg = nextUnit() * 360f,
                        spinDegPerSec = (if (i % 2 == 0) 1 else -1) * (170f + nextUnit() * 250f),
                        tumbleSpeed = 6f + nextUnit() * 8f,
                        sizeDp = 5.5f + nextUnit() * 5.2f,
                        aspect = 1.65f + nextUnit() * 0.85f,
                        shape = when {
                            i % 5 == 0 -> Shape.STAR
                            i % 4 == 0 -> Shape.PIXEL
                            else -> Shape.RIBBON
                        },
                        color = colors[i % colors.size]
                    )
                )
            }

            // Wave 2: 28 outward framing cannons from card shoulders arching down along both sides
            for (i in 0 until 28) {
                val fromLeft = (i % 2 == 0)
                val sx = if (fromLeft) leftShoulderX else rightShoulderX
                val angleDeg = -64.0 + nextUnit() * 44.0 // steep upward-outward fan (-64..-20)
                val rad = angleDeg * (PI / 180.0)
                val dirX = (if (fromLeft) -1f else 1f) * abs(cos(rad)).toFloat()
                val dirY = -abs(sin(rad)).toFloat()
                val speed = (230f + nextUnit() * 250f) * (screenWidth / 380f).coerceIn(0.85f, 1.35f)
                val fallScale = (screenHeight / 780f).coerceIn(0.85f, 1.25f)
                list.add(
                    Particle(
                        spawnDelayFrac = 0.09f + nextUnit() * 0.05f,
                        lifeFrac = 0.72f + nextUnit() * 0.17f,
                        startX = sx + (nextUnit() - 0.5f) * 16f * d,
                        startY = shoulderY + (nextUnit() - 0.5f) * 20f * d,
                        vxDp = dirX * speed,
                        vyDp = dirY * speed - 45f,
                        gravityDp = (330f + nextUnit() * 120f) * fallScale,
                        flutterAmpDp = 12f + nextUnit() * 16f,
                        flutterFreq = 6f + nextUnit() * 5f,
                        baseAngleDeg = nextUnit() * 360f,
                        spinDegPerSec = (if (fromLeft) -1 else 1) * (150f + nextUnit() * 220f),
                        tumbleSpeed = 5f + nextUnit() * 7f,
                        sizeDp = 5.2f + nextUnit() * 4.8f,
                        aspect = 1.7f + nextUnit() * 0.75f,
                        shape = when {
                            i % 3 == 0 -> Shape.STAR
                            i % 4 == 1 -> Shape.PIXEL
                            else -> Shape.RIBBON
                        },
                        color = colors[(i + 1) % colors.size]
                    )
                )
            }

            // Wave 3: 22 overhead golden & star glitter shower drifting across the upper frame
            for (i in 0 until 22) {
                val spreadX = ((i / 21f) - 0.5f) * 2.0f
                list.add(
                    Particle(
                        spawnDelayFrac = 0.16f + nextUnit() * 0.08f,
                        lifeFrac = 0.68f + nextUnit() * 0.14f,
                        startX = fountainX + spreadX * screenWidth * 0.34f,
                        startY = shoulderY - 90f * d + (nextUnit() - 0.5f) * 36f * d,
                        vxDp = spreadX * (75f + nextUnit() * 85f),
                        vyDp = -120f - nextUnit() * 150f,
                        gravityDp = 240f + nextUnit() * 90f,
                        flutterAmpDp = 16f + nextUnit() * 14f,
                        flutterFreq = 5f + nextUnit() * 4f,
                        baseAngleDeg = nextUnit() * 360f,
                        spinDegPerSec = (if (i % 2 == 0) 1 else -1) * (120f + nextUnit() * 160f),
                        tumbleSpeed = 4f + nextUnit() * 5f,
                        sizeDp = 4.6f + nextUnit() * 3.8f,
                        aspect = 1.5f + nextUnit() * 0.5f,
                        shape = if (i % 2 == 0) Shape.STAR else Shape.RIBBON,
                        color = if (i % 3 == 0) 0xFFFFFFFF.toInt() else colors[0]
                    )
                )
            }

            particles = list
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (particles.isEmpty()) return
            val durationSec = CONFETTI_DURATION_MS / 1000f
            val k = 1.38f // air resistance damping coefficient

            particles.forEach { pt ->
                val localP = (progress - pt.spawnDelayFrac) / pt.lifeFrac
                if (localP <= 0f || localP >= 1f) return@forEach

                val tSec = localP * durationSec
                // Exponential air drag displacement: (1 - e^(-kt))/k
                val expDrag = (1.0 - kotlin.math.exp((-k * tSec).toDouble())).toFloat() / k
                val px = pt.startX + (pt.vxDp * expDrag + sin((tSec * pt.flutterFreq + pt.baseAngleDeg).toDouble()).toFloat() * pt.flutterAmpDp * localP) * d
                val py = pt.startY + (pt.vyDp * expDrag + 0.5f * pt.gravityDp * tSec * tSec) * d

                val alpha = when {
                    localP < 0.05f -> localP / 0.05f
                    localP > 0.72f -> 1f - ((localP - 0.72f) / 0.28f)
                    else -> 1f
                }.coerceIn(0f, 1f)

                paint.color = pt.color
                val baseAlpha = paint.alpha
                paint.alpha = (baseAlpha * alpha).toInt().coerceIn(0, 255)
                if (paint.alpha <= 2) return@forEach

                val rot = pt.baseAngleDeg + pt.spinDegPerSec * tSec
                val sz = pt.sizeDp * d
                when (pt.shape) {
                    Shape.RIBBON -> {
                        val wobbleScale = (0.32f + 0.68f * abs(cos((tSec * pt.tumbleSpeed + pt.baseAngleDeg).toDouble()).toFloat()))
                        canvas.save()
                        canvas.translate(px, py)
                        canvas.rotate(rot)
                        canvas.scale(wobbleScale, 1f)
                        val hw = sz * 0.42f
                        val hh = sz * 0.42f * pt.aspect
                        canvas.drawRoundRect(-hw, -hh, hw, hh, 1.4f * d, 1.4f * d, paint)
                        canvas.restore()
                    }
                    Shape.STAR -> {
                        canvas.save()
                        canvas.translate(px, py)
                        canvas.rotate(rot * 0.45f)
                        drawFourPointStar(canvas, 0f, 0f, sz * 0.85f, paint, starPath)
                        canvas.restore()
                    }
                    Shape.PIXEL -> {
                        canvas.save()
                        canvas.translate(px, py)
                        canvas.rotate(rot * 0.7f)
                        val hs = sz * 0.45f
                        canvas.drawRect(-hs, -hs, hs, hs, paint)
                        canvas.restore()
                    }
                }
            }
        }
    }

    /**
     * Luminous liquid XP energy bar with multi-stop gradient, traveling sheen wave,
     * leading meniscus spark halo, and settlement completion flash.
     */
    private class BondProgressBarView(
        context: Context,
        @ColorInt trackColor: Int,
        @param:ColorInt private val memberHue: Int,
        @param:ColorInt private val goldHue: Int
    ) : View(context) {

        private val d = resources.displayMetrics.density
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        private val trackRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.85f * d
            color = 0x55FFFFFF
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tipGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        private val barPath = Path()
        private val barRect = RectF()
        private val sheenMatrix = Matrix()

        private var cachedW = 0f
        private var cachedH = 0f

        var progressShare: Float = 0f
            set(value) {
                val clamped = value.coerceIn(0f, 1f)
                if (field != clamped) {
                    field = clamped
                    invalidate()
                }
            }

        var sheenPhase: Float = -1f
            set(value) {
                if (field != value) {
                    field = value
                    invalidate()
                }
            }

        var flashStrength: Float = 0f
            set(value) {
                val c = value.coerceIn(0f, 1f)
                if (field != c) {
                    field = c
                    invalidate()
                }
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val minW = suggestedMinimumWidth
            val minH = (10f * d + 0.5f).toInt()
            setMeasuredDimension(
                resolveSize(minW, widthMeasureSpec),
                resolveSize(minH, heightMeasureSpec)
            )
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val r = h / 2f

            if (cachedW != w || cachedH != h) {
                cachedW = w
                cachedH = h
                fillPaint.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    intArrayOf(
                        ColorUtils.blendARGB(memberHue, 0xFFFFFFFF.toInt(), 0.22f),
                        ColorUtils.blendARGB(memberHue, goldHue, 0.45f),
                        ColorUtils.blendARGB(goldHue, 0xFFFFFFFF.toInt(), 0.48f),
                        0xFFFFF7D6.toInt()
                    ),
                    floatArrayOf(0f, 0.45f, 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
                val sheenW = (46f * d).coerceAtLeast(h * 3f)
                sheenPaint.shader = LinearGradient(
                    0f, 0f, sheenW, h,
                    intArrayOf(0x00000000, 0x70FFFFFF, 0xC8FFFFFF.toInt(), 0x70FFFFFF, 0x00000000),
                    floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)
            canvas.drawRoundRect(0.4f * d, 0.4f * d, w - 0.4f * d, h - 0.4f * d, r, r, trackRimPaint)

            if (progressShare > 0f) {
                val fillW = (w * progressShare).coerceAtLeast(h.coerceAtMost(w))
                barRect.set(0f, 0f, fillW, h)
                barPath.reset()
                barPath.addRoundRect(barRect, r, r, Path.Direction.CW)

                val save = canvas.save()
                canvas.clipPath(barPath)
                canvas.drawRoundRect(barRect, r, r, fillPaint)

                if (sheenPhase in 0f..1f) {
                    val sx = -46f * d + (fillW + 92f * d) * sheenPhase
                    sheenMatrix.setTranslate(sx, 0f)
                    sheenPaint.shader?.setLocalMatrix(sheenMatrix)
                    canvas.drawRect(barRect, sheenPaint)
                }

                if (flashStrength > 0.01f) {
                    flashPaint.alpha = (180 * flashStrength).toInt().coerceIn(0, 255)
                    canvas.drawRect(barRect, flashPaint)
                }
                canvas.restoreToCount(save)

                // Soft glowing meniscus halo right at the advancing fill edge while filling, plus landing spark
                if ((sheenPhase in 0f..1f && progressShare < 0.995f) || flashStrength > 0.05f) {
                    val burst = flashStrength.coerceIn(0f, 1f)
                    val glowR = ((10f + 7f * burst) * d).coerceAtLeast(h)
                    val tipX = (fillW - h * 0.25f).coerceIn(r, w)
                    val peakWhite = (195 + (60 * burst)).toInt().coerceIn(0, 255)
                    tipGlowPaint.shader = RadialGradient(
                        tipX, h * 0.5f, glowR,
                        intArrayOf(
                            ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), peakWhite),
                            ColorUtils.setAlphaComponent(goldHue, (115 + 100 * burst).toInt().coerceIn(0, 255)),
                            0x00000000
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(tipX, h * 0.5f, glowR, tipGlowPaint)
                }
            }
        }
    }

    /**
     * Paints the festive jewel aurora interior sampled behind the Liquid Glass bevel:
     * member hue blended with warm champagne celebration gold over a deep high-contrast anchor.
     */
    private class CosmicVictoryPainter(
        context: Context,
        @param:ColorInt private val memberHue: Int,
        @param:ColorInt private val goldHue: Int
    ) {
        private val deep = ContextCompat.getColor(context, R.color.flat_deep)
        private val deep2 = ContextCompat.getColor(context, R.color.flat_deep2)
        private val accentSecondary = ContextCompat.getColor(context, R.color.alive_fam_coding)

        private var cachedW = 0
        private var cachedH = 0
        private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val crownGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveBackPath = Path()
        private val waveGoldPath = Path()
        private val waveFrontPath = Path()

        fun draw(canvas: Canvas, w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            if (cachedW != w || cachedH != h) {
                cachedW = w
                cachedH = h
                val wf = w.toFloat()
                val hf = h.toFloat()

                val jewelTop = ColorUtils.blendARGB(deep, 0xFF10153A.toInt(), 0.42f)
                val jewelMid = ColorUtils.blendARGB(deep2, memberHue, 0.28f)
                val jewelBottom = ColorUtils.blendARGB(deep2, 0xFF0C1030.toInt(), 0.45f)

                groundPaint.shader = LinearGradient(
                    0f, 0f, 0f, hf,
                    intArrayOf(jewelTop, jewelMid, jewelBottom),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )

                val warmCrest = ColorUtils.blendARGB(memberHue, goldHue, 0.36f)
                crownGlowPaint.shader = RadialGradient(
                    wf * 0.5f, hf * 0.33f, (wf * 0.58f).coerceAtLeast(1f),
                    intArrayOf(
                        ColorUtils.setAlphaComponent(warmCrest, 175),
                        ColorUtils.setAlphaComponent(memberHue, 92),
                        0x00000000
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )

                waveBackPath.reset()
                waveBackPath.moveTo(-wf * 0.1f, hf * 0.60f)
                waveBackPath.cubicTo(wf * 0.24f, hf * 0.32f, wf * 0.65f, hf * 0.66f, wf * 1.1f, hf * 0.36f)
                waveBackPath.lineTo(wf * 1.1f, hf * 1.05f)
                waveBackPath.lineTo(-wf * 0.1f, hf * 1.05f)
                waveBackPath.close()

                waveBackPaint.shader = LinearGradient(
                    0f, hf * 0.35f, wf, hf,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(deep, memberHue, 0.58f), 200),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accentSecondary, memberHue, 0.52f), 175),
                        ColorUtils.setAlphaComponent(jewelBottom, 225)
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )

                waveGoldPath.reset()
                waveGoldPath.moveTo(-wf * 0.1f, hf * 0.48f)
                waveGoldPath.cubicTo(wf * 0.32f, hf * 0.62f, wf * 0.72f, hf * 0.34f, wf * 1.1f, hf * 0.54f)
                waveGoldPath.lineTo(wf * 1.1f, hf * 0.85f)
                waveGoldPath.lineTo(-wf * 0.1f, hf * 0.85f)
                waveGoldPath.close()

                waveGoldPaint.shader = LinearGradient(
                    0f, hf * 0.34f, wf, hf * 0.78f,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(goldHue, 92),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(goldHue, memberHue, 0.5f), 118),
                        ColorUtils.setAlphaComponent(memberHue, 68)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )

                waveFrontPath.reset()
                waveFrontPath.moveTo(-wf * 0.1f, hf * 0.64f)
                waveFrontPath.cubicTo(wf * 0.34f, hf * 0.86f, wf * 0.68f, hf * 0.54f, wf * 1.1f, hf * 0.76f)
                waveFrontPath.lineTo(wf * 1.1f, hf * 1.05f)
                waveFrontPath.lineTo(-wf * 0.1f, hf * 1.05f)
                waveFrontPath.close()

                waveFrontPaint.shader = LinearGradient(
                    0f, hf * 0.54f, wf, hf,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(memberHue, deep, 0.28f), 195),
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(accentSecondary, 0xFF3E3592.toInt(), 0.4f), 190),
                        ColorUtils.setAlphaComponent(jewelTop, 220)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), groundPaint)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), crownGlowPaint)
            canvas.drawPath(waveBackPath, waveBackPaint)
            canvas.drawPath(waveGoldPath, waveGoldPaint)
            canvas.drawPath(waveFrontPath, waveFrontPaint)
        }
    }

    companion object {
        const val TAG_CARD = "victoryCard"
        const val TAG_CTA = "victoryCtaButton"
        const val TAG_BOND_BAR = "bondProgressBar"

        private const val MAX_CARD_WIDTH_DP = 360f
        private const val SCRIM_COLOR = 0xB8060A1D.toInt()
        private const val TEXT_ON_GLASS = 0xFFFFFFFF.toInt()
        private const val BODY_ON_GLASS = 0xF5F4F8FF.toInt()
        private const val MUTED_ON_GLASS = 0xFFEBF3FF.toInt()
        private const val METER_TRACK = 0x34FFFFFF
        private const val GLASS_TEXT_SHADOW = 0x6403061C
        private const val SCRIM_ENTER_MS = 200L
        private const val CARD_ENTER_MS = 310L
        private const val EXIT_MS = 180L
        private const val AURA_DURATION_MS = 1900L
        private const val CONFETTI_DURATION_MS = 2350L

        private fun drawFourPointStar(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            radius: Float,
            paint: Paint,
            path: Path
        ) {
            if (radius <= 1.2f) return
            path.reset()
            path.moveTo(cx, cy - radius)
            path.quadTo(cx, cy, cx + radius, cy)
            path.quadTo(cx, cy, cx, cy + radius)
            path.quadTo(cx, cy, cx - radius, cy)
            path.quadTo(cx, cy, cx, cy - radius)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
