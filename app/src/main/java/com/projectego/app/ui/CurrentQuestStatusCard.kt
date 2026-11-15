package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R
import com.projectego.app.domain.PhysicalQuestVerifier
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.VerificationMethod
import java.util.Locale

/**
 * Pass-2 active mission card used on the Misi tab and Current Quest panel.
 *
 * Replaces the legacy stacked icon slab with a category character badge,
 * reward pill, Focus-style live circular countdown ring, and automatic unlock
 * once the committed window completes.
 */
object CurrentQuestStatusCard {

    private const val RING_DIAMETER_DP = 168
    private const val RING_STROKE_DP = 12
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L

    data class Data(
        val attempt: QuestAttempt,
        val quest: Quest,
        val withHeader: Boolean,
        val weight: SurfaceWeight = SurfaceWeight.MINOR,
        val reclaimedAvailableMinutes: Long,
        val claimedTotalMinutes: Long,
        val onDeclareDone: () -> Boolean,
        val onDeclareSuccess: () -> Unit,
        val onDeclareFailed: () -> Unit,
        val onContinue: () -> Unit,
        val onRewardPresented: (LinearLayout) -> Unit,
        val nowProvider: () -> Long = { System.currentTimeMillis() }
    )

    fun committedDurationMinutes(attempt: QuestAttempt, quest: Quest): Long =
        attempt.committedDurationMinutes.takeIf { it > 0L }
            ?: quest.estimatedDurationMinutes.toLong().coerceAtLeast(1L)

    fun remainingMillis(attempt: QuestAttempt, quest: Quest, nowMs: Long): Long {
        val totalMs = committedDurationMinutes(attempt, quest) * MILLIS_PER_MINUTE
        return when (attempt.state) {
            QuestAttemptState.COMMITTED -> totalMs
            QuestAttemptState.IN_PROGRESS -> {
                val startedAt = attempt.activityStartTimestamp ?: return totalMs
                val elapsed = (nowMs - startedAt).coerceAtLeast(0L)
                (totalMs - elapsed).coerceAtLeast(0L)
            }
            else -> 0L
        }
    }

    fun formatClock(remainingMs: Long): String {
        val totalSeconds = if (remainingMs <= 0L) 0L else (remainingMs + 999L) / MILLIS_PER_SECOND
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    @DrawableRes
    fun categoryCharacterDrawable(category: QuestCategory): Int = when (category) {
        QuestCategory.STUDY -> R.drawable.alive_member_study
        QuestCategory.READING -> R.drawable.alive_member_reading
        QuestCategory.EXERCISE -> R.drawable.alive_member_exercise
        QuestCategory.WALKING -> R.drawable.alive_member_walking
        QuestCategory.CLEANING -> R.drawable.alive_member_cleaning
        QuestCategory.CREATIVE -> R.drawable.alive_member_creative
        QuestCategory.CODING -> R.drawable.alive_member_coding
        QuestCategory.WRITING -> R.drawable.alive_member_writing
        QuestCategory.MINDFULNESS -> R.drawable.alive_member_mindfulness
        QuestCategory.SOCIAL -> R.drawable.alive_member_social
        QuestCategory.OTHER -> R.drawable.ic_alive_character
    }

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val d = ui.dimens
        val attempt = data.attempt
        val quest = data.quest
        val hue = ContextCompat.getColor(context, QuestHues.quest(quest.category))
        val isRewarded = attempt.state == QuestAttemptState.REWARDED
        val primaryTextColor = ui.palette.ink
        val secondaryTextColor = if (isRewarded) ui.palette.ink else ui.palette.inkSecondary

        val card = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                cornerRadius = d.radiusCard.toFloat()
                setColor(if (isRewarded) ui.palette.flatMint else ui.palette.tile)
            }
            val pad = d.dp(20)
            setPadding(pad, pad, pad, pad)
        }

        // Top editorial header: Pass-2 smooth character badge + kicker/title + reward pill.
        val header = ui.row().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL

            val avatar = ui.mark(
                categoryCharacterDrawable(quest.category),
                d.dp(44),
                context.getString(AliveStates.categoryLabel(quest.category))
            )
            addView(avatar)
            addView(ui.hgap(d.m))

            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    if (data.withHeader) {
                        addView(
                            ui.kicker(context.getString(R.string.quest_current)).apply {
                                setTextColor(secondaryTextColor)
                                maxLines = 1
                            }
                        )
                        addView(ui.vgap(d.xs))
                    }
                    addView(
                        ui.lead(quest.title).apply {
                            setTextColor(primaryTextColor)
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                        }
                    )
                }
            )
            addView(ui.hgap(d.s))
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.quest_reward_chip, quest.rewardMinutes)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                    setTextColor(ui.palette.ink)
                    background = GradientDrawable().apply {
                        cornerRadius = d.radiusPill.toFloat()
                        setColor(if (isRewarded) ui.palette.rewardPale else hue)
                    }
                    setPadding(d.m, d.dp(6), d.m, d.dp(6))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }
        card.addView(header)

        if (quest.description.isNotBlank()) {
            card.addView(ui.vgap(d.s))
            card.addView(
                ui.note(quest.description).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(secondaryTextColor)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
        }

        val totalMinutes = committedDurationMinutes(attempt, quest)
        val totalMillis = totalMinutes * MILLIS_PER_MINUTE
        val showRing = (attempt.state == QuestAttemptState.IN_PROGRESS ||
            attempt.state == QuestAttemptState.COMMITTED) && totalMillis > 0L

        var ringView: FocusSetupScreen.Ring? = null
        var clockView: TextView? = null
        var clockKickerView: TextView? = null

        val nowInitial = data.nowProvider()
        val initialRemainingMs = remainingMillis(attempt, quest, nowInitial)

        if (showRing) {
            card.addView(ui.vgap(d.l))
            val ring = FocusSetupScreen.Ring(
                context = context,
                ui = ui,
                diameterDp = RING_DIAMETER_DP,
                strokeDp = RING_STROKE_DP
            ).apply {
                fraction = (initialRemainingMs.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            }
            ringView = ring

            val clock = ui.timer(formatClock(initialRemainingMs)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
                setTextColor(ui.palette.ink)
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            clockView = clock

            val kickerRes = when {
                attempt.state == QuestAttemptState.COMMITTED -> R.string.quest_countdown_start_short
                initialRemainingMs > 0L -> R.string.quest_countdown_left_short
                else -> R.string.quest_countdown_ready_short
            }
            val clockKicker = ui.kicker(context.getString(kickerRes)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ui.palette.ink)
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = d.dp(4)
                }
            }
            clockKickerView = clockKicker

            val centerBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(clock)
                addView(clockKicker)
            }

            val heroStage = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                background = GradientDrawable().apply {
                    cornerRadius = d.radiusField.toFloat()
                    setColor(hue)
                }
                setPadding(d.l, d.dp(22), d.l, d.dp(22))
                addView(
                    ring,
                    FrameLayout.LayoutParams(
                        d.dp(RING_DIAMETER_DP),
                        d.dp(RING_DIAMETER_DP),
                        Gravity.CENTER
                    )
                )
                addView(
                    centerBox,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
            card.addView(heroStage)
        }

        if (!showRing) {
            card.addView(ui.vgap(d.l))

            if (isRewarded) {
                val rewardLine = ui.row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ui.lead(context.getString(R.string.quest_finished_label)))
                    addView(ui.hgap(d.m))
                    addView(ui.rewardChip(context.getString(R.string.unit_minutes_gain, data.claimedTotalMinutes)))
                }
                card.addView(rewardLine)
                data.onRewardPresented(card)
            } else if (attempt.state == QuestAttemptState.FAILED) {
                card.addView(ui.error(context.getString(AliveStates.attemptHeadline(attempt.state))))
            } else {
                card.addView(ui.lead(context.getString(AliveStates.attemptHeadline(attempt.state))))
            }
        }

        val physicalWait = attempt.method == VerificationMethod.PHYSICAL &&
            attempt.state == QuestAttemptState.IN_PROGRESS

        fun computeGateLeft(nowMs: Long): Long = if (physicalWait) {
            PhysicalQuestVerifier.remainingMinutes(
                attempt.activityStartTimestamp,
                nowMs,
                attempt.committedDurationMinutes
            )
        } else {
            0L
        }

        fun formatSupport(gateLeft: Long): String = when {
            physicalWait -> context.getString(
                if (gateLeft > 0L) R.string.quest_gate_left else R.string.quest_gate_open,
                gateLeft
            )
            attempt.state == QuestAttemptState.VERIFIED -> context.getString(
                if (attempt.method == VerificationMethod.PHYSICAL) {
                    R.string.verified_reason_signals
                } else {
                    AliveStates.verifiedSupport()
                }
            )
            isRewarded -> context.getString(
                R.string.attempt_rewarded_total,
                data.reclaimedAvailableMinutes
            )
            else -> context.getString(AliveStates.attemptSupport(attempt.state))
        }

        val initialGateLeft = computeGateLeft(nowInitial)
        val supportView: TextView? = if (!showRing) {
            ui.note(formatSupport(initialGateLeft)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(secondaryTextColor)
            }.also {
                card.addView(ui.vgap(d.lineGap))
                card.addView(it)
            }
        } else {
            null
        }

        if (isRewarded && attempt.autoSettled) {
            card.addView(ui.vgap(d.pairGap))
            card.addView(
                ui.note(context.getString(R.string.quest_finished_auto)).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(secondaryTextColor)
                }
            )
        }

        if (attempt.state == QuestAttemptState.FAILED) {
            val reason = AliveStates.classifyFailure(attempt.verificationResult?.reason)
            if (reason != VerificationReasonClass.UNKNOWN) {
                card.addView(ui.vgap(d.groupGap))
                card.addView(ui.rowRule())
                card.addView(ui.vgap(d.lineGap))
                card.addView(ui.reason(context.getString(AliveStates.failureSupport(reason))))
            }
        }

        var declareButton: View? = null

        if (physicalWait) {
            card.addView(ui.vgap(d.l))
            val btn = ui.primary(context.getString(R.string.quest_declare_done)).apply {
                isEnabled = initialGateLeft == 0L
                minHeight = maxOf(d.touchMin, d.dp(52))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    if (data.onDeclareDone()) {
                        data.onDeclareSuccess()
                    } else {
                        data.onDeclareFailed()
                    }
                }
            }
            declareButton = btn
            card.addView(btn)
        } else if (AliveStates.offersAction(attempt.state)) {
            card.addView(ui.vgap(d.l))
            card.addView(
                ui.secondary(context.getString(R.string.action_continue)).apply {
                    minHeight = maxOf(d.touchMin, d.dp(52))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { data.onContinue() }
                }
            )
        }

        if (showRing &&
            attempt.state == QuestAttemptState.IN_PROGRESS &&
            attempt.activityStartTimestamp != null &&
            ringView != null &&
            clockView != null &&
            clockKickerView != null
        ) {
            hookCountdownTicker(
                card = card,
                context = context,
                attempt = attempt,
                quest = quest,
                totalMillis = totalMillis,
                ring = ringView,
                clock = clockView,
                clockKicker = clockKickerView,
                support = supportView,
                declareButton = declareButton,
                physicalWait = physicalWait,
                formatSupport = ::formatSupport,
                computeGateLeft = ::computeGateLeft,
                nowProvider = data.nowProvider
            )
        }

        return card
    }

    private fun hookCountdownTicker(
        card: LinearLayout,
        context: Context,
        attempt: QuestAttempt,
        quest: Quest,
        totalMillis: Long,
        ring: FocusSetupScreen.Ring,
        clock: TextView,
        clockKicker: TextView,
        support: TextView?,
        declareButton: View?,
        physicalWait: Boolean,
        formatSupport: (Long) -> String,
        computeGateLeft: (Long) -> Long,
        nowProvider: () -> Long
    ) {
        val handler = Handler(Looper.getMainLooper())
        var attached = false

        fun syncNow(): Long {
            val nowMs = nowProvider()
            val leftMs = remainingMillis(attempt, quest, nowMs)
            ring.fraction = (leftMs.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
            clock.text = formatClock(leftMs)
            val kickerText = context.getString(
                if (leftMs > 0L) R.string.quest_countdown_left_short
                else R.string.quest_countdown_ready_short
            )
            if (clockKicker.text.toString() != kickerText) {
                clockKicker.text = kickerText
            }
            if (physicalWait) {
                val gateLeft = computeGateLeft(nowMs)
                if (support != null) {
                    val nextSupport = formatSupport(gateLeft)
                    if (support.text.toString() != nextSupport) {
                        support.text = nextSupport
                    }
                }
                val unlocked = gateLeft == 0L
                if (declareButton != null && declareButton.isEnabled != unlocked) {
                    declareButton.isEnabled = unlocked
                }
            }
            return leftMs
        }

        val tick = object : Runnable {
            override fun run() {
                if (!attached) return
                val leftMs = syncNow()
                if (leftMs > 0L) {
                    handler.postDelayed(this, MILLIS_PER_SECOND)
                }
            }
        }

        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                attached = true
                handler.removeCallbacks(tick)
                val leftMs = syncNow()
                if (leftMs > 0L) {
                    handler.postDelayed(tick, MILLIS_PER_SECOND)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                attached = false
                handler.removeCallbacks(tick)
            }
        })
    }
}
