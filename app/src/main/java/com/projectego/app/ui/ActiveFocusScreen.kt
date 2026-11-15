package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType

/**
 * Active focus session rendered with Pass-2 flat minimalism: one lilac hero
 * countdown ring with a live 1-second MM:SS ticker, a borderless goal card
 * pairing the smooth curved character mark with a duration capsule, two equal
 * permission tiles, and the Time Bank extend control.
 */
object ActiveFocusScreen {

    private const val RING_DIAMETER_DP = 196
    private const val RING_STROKE_DP = 14
    private const val EXTEND_COST_MINUTES = 15L
    private const val TICK_INTERVAL_MS = 1_000L

    data class Data(
        val activityType: FocusActivityType,
        val goal: String,
        val durationMinutes: Long,
        val startTimestampMs: Long,
        val endTimestampMs: Long,
        val allowedAppCount: Int,
        val bankMinutes: Long,
        val clockMillis: () -> Long = { System.currentTimeMillis() },
        val animate: Boolean = true,
        val onExtend: () -> Unit = {}
    )

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val d = ui.dimens
        val root = ui.column()

        val durationSpanMs = (data.durationMinutes.coerceAtLeast(1L) * 60_000L)
        val totalSpanMs = (data.endTimestampMs - data.startTimestampMs)
            .takeIf { it > 0L }
            ?: durationSpanMs

        fun computeRemainingMs(nowMs: Long): Long = (data.endTimestampMs - nowMs).coerceAtLeast(0L)
        fun computeFraction(remainingMs: Long): Float =
            (remainingMs.toFloat() / totalSpanMs.toFloat()).coerceIn(0f, 1f)

        val initialRemainingMs = computeRemainingMs(data.clockMillis())
        val ring = FocusSetupScreen.Ring(
            context = context,
            ui = ui,
            diameterDp = RING_DIAMETER_DP,
            strokeDp = RING_STROKE_DP
        ).apply {
            fraction = computeFraction(initialRemainingMs)
        }

        val countdownDigits = ui.timer(CurrentQuestStatusCard.formatClock(initialRemainingMs)).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            setTextColor(ui.palette.flatInk)
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val countdownKicker = ui.kicker(context.getString(kickerResFor(initialRemainingMs))).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ui.palette.flatInk)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = d.dp(4)
            }
        }
        val centerOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(countdownDigits)
            addView(countdownKicker)
        }

        val ringPx = d.dp(RING_DIAMETER_DP)
        val heroCard = FrameLayout(context).apply {
            background = flatField(ui.palette.fieldLilac, d.radiusCard.toFloat())
            setPadding(d.dp(20), d.dp(28), d.dp(20), d.dp(28))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(ring, FrameLayout.LayoutParams(ringPx, ringPx, Gravity.CENTER))
            addView(
                centerOverlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
        root.addView(heroCard)
        root.addView(ui.vgap(d.cardGap))

        val goalCard = buildGoalCard(context, ui, data)
        root.addView(goalCard)
        root.addView(ui.vgap(d.m))

        val infoRow = ui.equalRow().apply {
            addView(
                ui.weighted(
                    buildStatField(
                        ui = ui,
                        fillColor = ui.palette.flatMint,
                        kickerText = context.getString(R.string.focus_kicker_allowed),
                        valueText = context.getString(R.string.focus_allowed_count, data.allowedAppCount)
                    )
                )
            )
            addView(ui.hgap(d.m))
            addView(
                ui.weighted(
                    buildStatField(
                        ui = ui,
                        fillColor = ui.palette.fieldButter,
                        kickerText = context.getString(R.string.focus_kicker_message),
                        valueText = context.getString(R.string.focus_message_value)
                    )
                )
            )
        }
        root.addView(infoRow)
        root.addView(ui.vgap(d.cardGap))

        val extendButton = ui.secondary(context.getString(R.string.focus_extend)).apply {
            isEnabled = data.bankMinutes >= EXTEND_COST_MINUTES
            AliveMotion.attachPressSpring(this, pressedScale = 0.96f, hapticOnDown = isEnabled)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                AliveMotion.pulsePop(it, dipScale = 0.94f)
                data.onExtend()
            }
        }
        root.addView(extendButton)
        root.addView(ui.vgap(d.s))
        root.addView(
            ui.meta(context.getString(R.string.focus_extend_bank_short, data.bankMinutes)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ui.palette.inkSecondary)
                gravity = Gravity.CENTER
                maxLines = 1
            }
        )

        attachCountdownTicker(
            host = root,
            context = context,
            ring = ring,
            digits = countdownDigits,
            kicker = countdownKicker,
            clockMillis = data.clockMillis,
            computeRemainingMs = ::computeRemainingMs,
            computeFraction = ::computeFraction
        )

        return root
    }

    private fun buildGoalCard(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val d = ui.dimens
        val avatarSize = d.dp(52)
        val avatarImage = ImageView(context).apply {
            setImageResource(memberVectorFor(data.activityType))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(
                AliveStates.faceDescription(AliveStates.focusPreviewSpriteFor(data.activityType))
            )
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
            if (data.animate) {
                AliveMotion.startBreathing(this, maxScale = 1.045f, periodMs = 2800L)
            }
        }

        val goalText = data.goal.trim().ifEmpty { context.getString(AliveStates.focusActivityLabel(data.activityType)) }
        val textColumn = ui.column().apply {
            addView(
                ui.kicker(context.getString(R.string.focus_goal_label)).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(ui.palette.inkSecondary)
                    letterSpacing = 0.06f
                    maxLines = 1
                }
            )
            addView(ui.vgap(d.xs))
            addView(
                ui.headline(goalText).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(ui.palette.flatInk)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
        }

        val durationPill = ui.meta(
            "${data.durationMinutes} ${context.getString(R.string.unit_minutes_short)}"
        ).apply {
            setTextColor(ui.palette.flatInk)
            maxLines = 1
            background = flatField(ui.palette.soft, d.radiusPill.toFloat())
            setPadding(d.dp(12), d.dp(6), d.dp(12), d.dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        return ui.column().apply {
            background = flatField(ui.palette.tile, d.radiusCard.toFloat())
            setPadding(d.dp(18), d.dp(18), d.dp(18), d.dp(18))
            addView(
                ui.row().apply {
                    addView(avatarImage)
                    addView(ui.hgap(d.l))
                    addView(ui.weighted(textColumn))
                    addView(ui.hgap(d.m))
                    addView(durationPill)
                }
            )
        }
    }

    private fun buildStatField(
        ui: AliveComponents,
        fillColor: Int,
        kickerText: String,
        valueText: String
    ): LinearLayout {
        val d = ui.dimens
        return ui.column().apply {
            background = flatField(fillColor, d.radiusCard.toFloat())
            setPadding(d.dp(18), d.dp(18), d.dp(18), d.dp(18))
            addView(
                ui.kicker(kickerText).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(ui.palette.inkSecondary)
                    letterSpacing = 0.06f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
            addView(ui.vgap(d.s))
            addView(
                ui.headline(valueText).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(ui.palette.flatInk)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
        }
    }

    private fun attachCountdownTicker(
        host: View,
        context: Context,
        ring: FocusSetupScreen.Ring,
        digits: TextView,
        kicker: TextView,
        clockMillis: () -> Long,
        computeRemainingMs: (Long) -> Long,
        computeFraction: (Long) -> Float
    ) {
        val handler = Handler(Looper.getMainLooper())
        var running = false
        val tickRunnable = object : Runnable {
            override fun run() {
                val remaining = computeRemainingMs(clockMillis())
                ring.fraction = computeFraction(remaining)
                digits.text = CurrentQuestStatusCard.formatClock(remaining)
                kicker.text = context.getString(kickerResFor(remaining))
                if (remaining > 0L && host.isAttachedToWindow) {
                    handler.postDelayed(this, TICK_INTERVAL_MS)
                } else {
                    running = false
                }
            }
        }

        fun syncNow() {
            val remaining = computeRemainingMs(clockMillis())
            ring.fraction = computeFraction(remaining)
            digits.text = CurrentQuestStatusCard.formatClock(remaining)
            kicker.text = context.getString(kickerResFor(remaining))
            if (remaining > 0L && !running && host.isAttachedToWindow) {
                running = true
                handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
            }
        }

        host.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    syncNow()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    running = false
                    handler.removeCallbacks(tickRunnable)
                }
            }
        )
        if (host.isAttachedToWindow) {
            syncNow()
        }
    }

    private fun kickerResFor(remainingMs: Long): Int =
        if (remainingMs > 0L) R.string.quest_countdown_left_short
        else R.string.quest_countdown_ready_short

    @DrawableRes
    fun memberVectorFor(activityType: FocusActivityType): Int = when (activityType) {
        FocusActivityType.STUDY -> R.drawable.alive_member_study
        FocusActivityType.WORK -> R.drawable.alive_member_work
        FocusActivityType.CODING -> R.drawable.alive_member_coding
        FocusActivityType.CREATIVE -> R.drawable.alive_member_creative
        FocusActivityType.WRITING -> R.drawable.alive_member_writing
    }

    private fun flatField(color: Int, cornerRadius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            this.cornerRadius = cornerRadius
        }
}
