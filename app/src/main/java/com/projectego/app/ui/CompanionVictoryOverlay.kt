package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
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
 * Uses a self-contained cosmic wave backdrop painter for refractive bevel waves,
 * triggers zero audio, respects reduced-motion settings, and strictly emits
 * `onDismiss(event)` at most once across CTA taps, scrim taps, or Back presses.
 */
class CompanionVictoryOverlay(
    context: Context,
    private val ui: AliveComponents
) : FrameLayout(context) {

    var currentEvent: VictoryEvent? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val activeAnimators = mutableListOf<Animator>()
    private var dismissCallback: ((VictoryEvent) -> Unit)? = null
    private var dismissing = false
    private var dismissDispatched = false

    private var scrimView: View? = null
    private var cardView: AliveLiquidGlassCard? = null
    private var mascotBadge: View? = null
    private var bondBarView: BondProgressBarView? = null
    private var beat2Runnable: Runnable? = null

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

        val scrim = View(context).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        scrimView = scrim
        addView(scrim)

        val backdropPainter = CosmicVictoryPainter(context, memberHue)
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
        setPadding(hPad, vPad, hPad, vPad)
        addView(
            cappedHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        val animate = animationsEnabled()
        val targetShare = CompanionSynergy.shareToNext(event.bondXpAfter)
        val startShare = CompanionSynergy.shareToNext(event.bondXpBefore)
        bondBarView?.progressShare = if (animate && event.bondXpAfter > event.bondXpBefore) startShare else targetShare

        if (parent != host) {
            (parent as? ViewGroup)?.removeView(this)
            host.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        if (animate) {
            prepareAndStartCeremony(event, memberHue, startShare, targetShare)
        } else {
            scrim.alpha = 1f
            glassCard.alpha = 1f
            glassCard.scaleX = 1f
            glassCard.scaleY = 1f
            glassCard.translationY = 0f
            mascotBadge?.scaleX = 1f
            mascotBadge?.scaleY = 1f
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
        val outAnimators = mutableListOf<Animator>()
        scrim?.let {
            outAnimators.add(ObjectAnimator.ofFloat(it, View.ALPHA, it.alpha, 0f).setDuration(EXIT_MS))
        }
        card?.let {
            outAnimators.add(ObjectAnimator.ofFloat(it, View.ALPHA, it.alpha, 0f).setDuration(EXIT_MS))
            outAnimators.add(ObjectAnimator.ofFloat(it, View.SCALE_X, it.scaleX, 0.94f).setDuration(EXIT_MS))
            outAnimators.add(ObjectAnimator.ofFloat(it, View.SCALE_Y, it.scaleY, 0.94f).setDuration(EXIT_MS))
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
        beat2Runnable != null || activeAnimators.any { it.isRunning || it.isStarted }

    private fun prepareAndStartCeremony(
        event: VictoryEvent,
        @ColorInt memberHue: Int,
        startShare: Float,
        targetShare: Float
    ) {
        val scrim = scrimView
        val card = cardView
        val mascot = mascotBadge
        scrim?.alpha = 0f
        card?.alpha = 0f
        card?.scaleX = 0.92f
        card?.scaleY = 0.92f
        card?.translationY = dp(18f)
        mascot?.scaleX = 0.72f
        mascot?.scaleY = 0.72f

        val entrance = mutableListOf<Animator>()
        scrim?.let {
            entrance.add(ObjectAnimator.ofFloat(it, View.ALPHA, 0f, 1f).setDuration(SCRIM_ENTER_MS))
        }
        card?.let {
            val cardIn = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(it, View.SCALE_X, 0.92f, 1f),
                    ObjectAnimator.ofFloat(it, View.SCALE_Y, 0.92f, 1f),
                    ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, dp(18f), 0f)
                )
                duration = CARD_ENTER_MS
                interpolator = DecelerateInterpolator(1.6f)
            }
            entrance.add(cardIn)
        }
        mascot?.let {
            val pop = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, View.SCALE_X, 0.72f, 1f),
                    ObjectAnimator.ofFloat(it, View.SCALE_Y, 0.72f, 1f)
                )
                startDelay = 80L
                duration = 280L
                interpolator = OvershootInterpolator(1.35f)
            }
            entrance.add(pop)
        }
        if (entrance.isNotEmpty()) {
            AnimatorSet().apply {
                playTogether(entrance)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimators.remove(this@apply)
                    }
                })
                activeAnimators.add(this)
                start()
            }
        }

        if (event.bondXpAfter > event.bondXpBefore) {
            val bar = bondBarView
            if (bar != null) {
                val barAnim = if (event.rankAfter > event.rankBefore) {
                    val toFull = ValueAnimator.ofFloat(startShare, 1f).apply {
                        duration = 260L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { bar.progressShare = it.animatedValue as Float }
                    }
                    val toTarget = ValueAnimator.ofFloat(0f, targetShare).apply {
                        duration = 390L
                        interpolator = DecelerateInterpolator(1.4f)
                        addUpdateListener { bar.progressShare = it.animatedValue as Float }
                    }
                    AnimatorSet().apply {
                        playSequentially(toFull, toTarget)
                    }
                } else {
                    ValueAnimator.ofFloat(startShare, targetShare).apply {
                        duration = 550L
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
                        if (!cancelled) bar.progressShare = targetShare
                    }
                })
                barAnim.startDelay = 120L
                activeAnimators.add(barAnim)
                barAnim.start()
            }
        }

        post {
            if (!isAttachedToWindow || dismissing || !animationsEnabled()) return@post
            val burstColors = intArrayOf(
                ui.palette.rewardMid,
                memberHue,
                TEXT_ON_GLASS,
                ColorUtils.blendARGB(memberHue, ui.palette.flatMint, 0.5f)
            )
            val centerX = width / 2f
            val centerY = (cardView?.y ?: (height * 0.42f)) + ((cardView?.height ?: 0) * 0.28f)
            RewardBurst(context).apply {
                paletteColors = burstColors
                show(this@CompanionVictoryOverlay, centerX, centerY)
            }
        }

        val celebratoryMilestone = event.synergyMatched ||
            event.newlyAwakened ||
            event.rankAfter > event.rankBefore ||
            event.growthStageUp
        if (celebratoryMilestone) {
            val runnable = Runnable {
                beat2Runnable = null
                if (isAttachedToWindow && !dismissing) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            beat2Runnable = runnable
            handler.postDelayed(runnable, BEAT_2_DELAY_MS)
        }
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                letterSpacing = 0.08f
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
        )

        // 2. Milestone flare badges
        val flares = buildList {
            if (event.newlyAwakened) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_awakened),
                        ColorUtils.setAlphaComponent(ui.palette.flatMint, 92),
                        ColorUtils.setAlphaComponent(ui.palette.flatMint, 210)
                    )
                )
            }
            if (event.rankAfter > event.rankBefore) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_rank_up, event.rankAfter),
                        ColorUtils.setAlphaComponent(ui.palette.rewardMid, 108),
                        ColorUtils.setAlphaComponent(ui.palette.rewardPale, 215)
                    )
                )
            }
            if (event.growthStageUp) {
                add(
                    Triple(
                        context.getString(R.string.victory_flare_level_up, event.growthLevelAfter),
                        ColorUtils.setAlphaComponent(ui.palette.fieldLilac, 104),
                        ColorUtils.setAlphaComponent(ui.palette.fieldSky, 215)
                    )
                )
            }
        }
        if (flares.isNotEmpty()) {
            addView(ui.vgap(dpInt(8f)))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    flares.forEachIndexed { idx, (label, fill, stroke) ->
                        if (idx > 0) addView(ui.hgap(dpInt(6f)))
                        addView(flarePill(label, fill, stroke))
                    }
                }
            )
        }

        // 3. Mascot badge (64dp inside translucent hue halo)
        addView(ui.vgap(dpInt(14f)))
        val haloContainer = FrameLayout(context).apply {
            val haloSize = dpInt(82f)
            layoutParams = LinearLayout.LayoutParams(haloSize, haloSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(memberHue, 68))
                setStroke(dpInt(1.2f), ColorUtils.setAlphaComponent(memberHue, 196))
            }
            val mascotImg = ImageView(context).apply {
                setImageResource(memberDrawable)
                contentDescription = memberTitle
                scaleType = ImageView.ScaleType.FIT_CENTER
                val iconSize = dpInt(64f)
                layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
            }
            addView(mascotImg)
        }
        mascotBadge = haloContainer
        addView(haloContainer)

        // 4. Activity headline
        addView(ui.vgap(dpInt(12f)))
        addView(
            TextView(context).apply {
                text = event.title
                setTextColor(TEXT_ON_GLASS)
                applyGlassShadow(this, 3.5f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.08f)
                includeFontPadding = false
            }
        )

        // 5. Synergy match badge
        if (event.synergyMatched) {
            addView(ui.vgap(dpInt(8f)))
            addView(
                flarePill(
                    label = context.getString(R.string.victory_synergy_active, memberTitle),
                    face = ColorUtils.setAlphaComponent(memberHue, 92),
                    stroke = ColorUtils.setAlphaComponent(memberHue, 212)
                )
            )
        }

        // 6. Two equal-height frosted sub-tiles (Bank/Focus + Bond XP)
        addView(ui.vgap(dpInt(16f)))
        val leftTileData = when {
            event.source == VictorySource.QUEST -> Triple(
                context.getString(R.string.victory_tile_bank_label),
                context.getString(R.string.victory_tile_minutes_gain, event.baseMinutes + event.synergyBonusMinutes),
                if (event.synergyBonusMinutes > 0L) {
                    context.getString(R.string.victory_tile_synergy_note, event.synergyBonusMinutes)
                } else {
                    context.getString(R.string.victory_tile_quest_base_note)
                }
            )
            event.synergyBonusMinutes > 0L -> Triple(
                context.getString(R.string.victory_tile_bank_label),
                context.getString(R.string.victory_tile_minutes_gain, event.synergyBonusMinutes),
                context.getString(R.string.victory_tile_synergy_note, event.synergyBonusMinutes)
            )
            else -> Triple(
                context.getString(R.string.victory_tile_focus_label),
                context.getString(R.string.victory_tile_minutes_duration, event.durationMinutes),
                context.getString(R.string.victory_tile_focus_note, event.durationMinutes)
            )
        }
        val bondTileData = Triple(
            context.getString(R.string.victory_tile_bond_label, companionTitle),
            context.getString(R.string.victory_tile_bond_delta, event.bondDelta),
            context.getString(R.string.bond_tile_sub, event.rankAfter, bondRankLabel(context, event.rankAfter))
        )
        val pairRow = ui.equalRow().apply {
            addView(
                frostedTile(leftTileData.first, leftTileData.second, leftTileData.third, memberHue).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(ui.hgap(dpInt(10f)))
            addView(
                frostedTile(bondTileData.first, bondTileData.second, bondTileData.third, ui.palette.fieldLilac).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        }
        addView(pairRow)

        // 7. Bond rank line & 6dp animated meter
        addView(ui.vgap(dpInt(14f)))
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
        addView(ui.vgap(dpInt(6f)))
        val meter = BondProgressBarView(
            context = context,
            trackColor = METER_TRACK,
            fillColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(0xFFFFFFFF.toInt(), memberHue, 0.25f),
                244
            )
        ).apply {
            tag = TAG_BOND_BAR
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpInt(6f)
            )
        }
        bondBarView = meter
        addView(meter)

        // 8. Pearlescent CTA pill
        addView(ui.vgap(dpInt(18f)))
        val ctaLabel = if (remainingQueuedCount > 0) {
            context.getString(R.string.victory_cta_next, remainingQueuedCount)
        } else {
            context.getString(R.string.victory_cta_done)
        }
        addView(
            TextView(context).apply {
                tag = TAG_CTA
                text = ctaLabel
                setTextColor(ui.palette.flatInk)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                gravity = Gravity.CENTER
                includeFontPadding = false
                minHeight = dpInt(46f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                background = GradientDrawable().apply {
                    setColor(PRIMARY_PILL_FILL)
                    cornerRadius = dp(999f)
                }
                val h = dpInt(16f)
                val v = dpInt(11f)
                setPadding(h, v, h, v)
                isClickable = true
                isFocusable = true
                AliveMotion.attachPressSpring(this, pressedScale = 0.96f, hapticOnDown = true)
                setOnClickListener { dismiss(animated = true) }
            }
        )
    }

    private fun flarePill(
        label: String,
        @ColorInt face: Int,
        @ColorInt stroke: Int
    ): TextView = TextView(context).apply {
        text = label
        setTextColor(TEXT_ON_GLASS)
        applyGlassShadow(this, 2f)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        background = GradientDrawable().apply {
            setColor(face)
            cornerRadius = dp(999f)
            setStroke(dpInt(1f), stroke)
        }
        setPadding(dpInt(10f), dpInt(4f), dpInt(10f), dpInt(4f))
    }

    private fun frostedTile(
        label: String,
        figure: String,
        note: String,
        @ColorInt tint: Int
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val face = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(0xFFFFFFFF.toInt(), tint, 0.42f),
            44
        )
        background = GradientDrawable().apply {
            setColor(face)
            cornerRadius = dp(16f)
            setStroke(dpInt(1f), SUB_TILE_STROKE)
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
        addView(
            TextView(context).apply {
                text = figure
                setTextColor(TEXT_ON_GLASS)
                applyGlassShadow(this, 3f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                maxLines = 1
                includeFontPadding = false
            }
        )
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

    private fun applyGlassShadow(tv: TextView, radiusDp: Float) {
        val d = resources.displayMetrics.density
        tv.setShadowLayer(radiusDp * d, 0f, 1f * d, GLASS_TEXT_SHADOW)
    }

    private fun cancelRunningEffects() {
        beat2Runnable?.let { handler.removeCallbacks(it) }
        beat2Runnable = null
        activeAnimators.toList().forEach { it.cancel() }
        activeAnimators.clear()
    }

    private fun resetState() {
        cancelRunningEffects()
        scrimView = null
        cardView = null
        mascotBadge = null
        bondBarView = null
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

    private class BondProgressBarView(
        context: Context,
        @ColorInt trackColor: Int,
        @ColorInt fillColor: Int
    ) : View(context) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }

        var progressShare: Float = 0f
            set(value) {
                val clamped = value.coerceIn(0f, 1f)
                if (field != clamped) {
                    field = clamped
                    invalidate()
                }
            }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val r = h / 2f
            canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)
            if (progressShare > 0f) {
                val fillW = (w * progressShare).coerceAtLeast(h.coerceAtMost(w))
                canvas.drawRoundRect(0f, 0f, fillW, h, r, r, fillPaint)
            }
        }
    }

    private class CosmicVictoryPainter(
        context: Context,
        @param:ColorInt private val memberHue: Int
    ) {
        private val deep = ContextCompat.getColor(context, R.color.flat_deep)
        private val deep2 = ContextCompat.getColor(context, R.color.flat_deep2)
        private val accentSecondary = ContextCompat.getColor(context, R.color.alive_fam_coding)

        private var cachedW = 0
        private var cachedH = 0
        private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val waveBackPath = Path()
        private val waveFrontPath = Path()

        fun draw(canvas: Canvas, w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            if (cachedW != w || cachedH != h) {
                cachedW = w
                cachedH = h
                val wf = w.toFloat()
                val hf = h.toFloat()

                groundPaint.shader = LinearGradient(
                    0f, 0f, 0f, hf,
                    intArrayOf(deep, deep, deep2),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                orbPaint.shader = RadialGradient(
                    wf * 0.5f, hf * 0.25f, (wf * 0.65f).coerceAtLeast(1f),
                    intArrayOf(ColorUtils.setAlphaComponent(memberHue, 68), 0x00000000),
                    null,
                    Shader.TileMode.CLAMP
                )

                waveBackPath.reset()
                waveBackPath.moveTo(-wf * 0.1f, hf * 0.72f)
                waveBackPath.cubicTo(wf * 0.25f, hf * 0.42f, wf * 0.65f, hf * 0.76f, wf * 1.1f, hf * 0.46f)
                waveBackPath.lineTo(wf * 1.1f, hf * 1.05f)
                waveBackPath.lineTo(-wf * 0.1f, hf * 1.05f)
                waveBackPath.close()

                waveBackPaint.shader = LinearGradient(
                    0f, hf * 0.45f, wf, hf,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(ColorUtils.blendARGB(0xFF1D3998.toInt(), memberHue, 0.35f), 205),
                        ColorUtils.setAlphaComponent(accentSecondary, 175),
                        ColorUtils.setAlphaComponent(deep2, 220)
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )

                waveFrontPath.reset()
                waveFrontPath.moveTo(-wf * 0.1f, hf * 0.58f)
                waveFrontPath.cubicTo(wf * 0.32f, hf * 0.84f, wf * 0.68f, hf * 0.50f, wf * 1.1f, hf * 0.76f)
                waveFrontPath.lineTo(wf * 1.1f, hf * 1.05f)
                waveFrontPath.lineTo(-wf * 0.1f, hf * 1.05f)
                waveFrontPath.close()

                waveFrontPaint.shader = LinearGradient(
                    0f, hf * 0.52f, wf, hf,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(memberHue, 165),
                        ColorUtils.setAlphaComponent(0xFF285CE0.toInt(), 185),
                        ColorUtils.setAlphaComponent(deep, 210)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), groundPaint)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), orbPaint)
            canvas.drawPath(waveBackPath, waveBackPaint)
            canvas.drawPath(waveFrontPath, waveFrontPaint)
        }
    }

    companion object {
        const val TAG_CARD = "victoryCard"
        const val TAG_CTA = "victoryCtaButton"
        const val TAG_BOND_BAR = "bondProgressBar"

        private const val MAX_CARD_WIDTH_DP = 360f
        private const val SCRIM_COLOR = 0xAD060920.toInt()
        private const val TEXT_ON_GLASS = 0xFFFFFFFF.toInt()
        private const val BODY_ON_GLASS = 0xF2F2F7FF.toInt()
        private const val MUTED_ON_GLASS = 0xDDE8F2FF.toInt()
        private const val PRIMARY_PILL_FILL = 0xF2FFFFFF.toInt()
        private const val SUB_TILE_STROKE = 0x62FFFFFF
        private const val METER_TRACK = 0x38FFFFFF
        private const val GLASS_TEXT_SHADOW = 0x52040822
        private const val SCRIM_ENTER_MS = 200L
        private const val CARD_ENTER_MS = 280L
        private const val EXIT_MS = 180L
        private const val BEAT_2_DELAY_MS = 260L
    }
}
