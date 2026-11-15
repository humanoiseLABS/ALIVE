package com.projectego.app.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.projectego.app.R

/**
 * Phase 1 of [CriticalAlertActivity]: a five-second non-dismissible urgency
 * frame around a calm editorial readout so a hasty tap never skips the number.
 */
object CriticalWarningCard {

    const val LOCK_SECONDS = 5
    private const val BREATH_MILLIS = 500L
    private const val SECOND_MILLIS = 1_000L

    private val evaluator = ArgbEvaluator()

    @ColorInt
    fun fieldColour(
        progress: Float,
        animationsOn: Boolean,
        @ColorInt mid: Int,
        @ColorInt deep: Int
    ): Int {
        if (!animationsOn) return mid
        return evaluator.evaluate(progress.coerceIn(0f, 1f), mid, deep) as Int
    }

    fun build(
        context: Context,
        ui: AliveComponents,
        remainingMinutes: Long,
        onContinue: () -> Unit
    ): View {
        val d = ui.dimens
        val mid = ui.palette.shortMid
        val deep = ui.palette.shortDark
        val framePad = d.dp(8)
        val innerRadius = (d.radiusCard - framePad).coerceAtLeast(d.dp(12)).toFloat()

        val field = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = CriticalCardChrome.surfaceDrawable(mid, d.radiusCard.toFloat())
            setPadding(framePad, framePad, framePad, framePad)
        }

        val seconds = ui.sectionHeader(LOCK_SECONDS.toString()).apply {
            setTextColor(ui.palette.shortDark)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }

        val countdownPill = ui.row().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            background = CriticalCardChrome.surfaceDrawable(
                ui.palette.shortPale,
                d.radiusPill.toFloat()
            )
            setPadding(d.m, d.dp(6), d.m, d.dp(6))
            addView(seconds)
            addView(ui.hgap(d.xs))
            addView(
                ui.metaSmall(context.getString(R.string.alert_warning_countdown)).apply {
                    setTextColor(ui.palette.ink)
                    maxLines = 1
                }
            )
        }

        val countdownBar = CriticalCardChrome.CapsuleProgressBar(
            context = context,
            trackColor = ui.palette.white,
            fillColor = ui.palette.shortDark,
            initialFraction = 1f
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                d.dp(8)
            )
        }

        val plate = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = CriticalCardChrome.surfaceDrawable(ui.palette.white, innerRadius)
            val pad = d.dp(18)
            setPadding(pad, pad, pad, pad)
        }

        plate.addView(
            CriticalCardChrome.headerRow(
                context = context,
                ui = ui,
                eyebrowRes = R.string.alert_warning_eyebrow,
                trailingBadge = countdownPill
            )
        )
        plate.addView(ui.vgap(d.l))
        plate.addView(
            CriticalCardChrome.heroBoard(
                context = context,
                ui = ui,
                remainingMinutes = remainingMinutes,
                progressBar = countdownBar
            )
        )
        plate.addView(ui.vgap(d.l))
        plate.addView(
            ui.lead(context.getString(R.string.alert_warning_body)).apply {
                setTextColor(ui.palette.ink)
            }
        )

        field.addView(plate)
        hookTimer(ui, field, seconds, countdownBar, mid, deep, onContinue)
        return field
    }

    private fun hookTimer(
        ui: AliveComponents,
        field: LinearLayout,
        seconds: TextView,
        countdownBar: CriticalCardChrome.CapsuleProgressBar,
        @ColorInt mid: Int,
        @ColorInt deep: Int,
        onContinue: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = LOCK_SECONDS
        var finished = false
        var breath: ValueAnimator? = null
        var barAnimator: ValueAnimator? = null

        fun stopAnimators() {
            breath?.cancel()
            breath = null
            barAnimator?.cancel()
            barAnimator = null
            (field.background as? GradientDrawable)?.setColor(mid)
        }

        val tick = object : Runnable {
            override fun run() {
                if (finished) return
                remaining -= 1
                if (remaining > 0) {
                    seconds.text = remaining.toString()
                    if (!ui.animationsAllowed) {
                        countdownBar.fraction = remaining.toFloat() / LOCK_SECONDS.toFloat()
                    }
                    handler.postDelayed(this, SECOND_MILLIS)
                } else {
                    finished = true
                    countdownBar.fraction = 0f
                    stopAnimators()
                    onContinue()
                }
            }
        }

        field.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (finished) return
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, SECOND_MILLIS)

                if (ui.animationsAllowed && breath == null) {
                    val face = field.background as? GradientDrawable
                    breath = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = BREATH_MILLIS
                        repeatCount = LOCK_SECONDS * 2 - 1
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener { anim ->
                            val value = anim.animatedValue as Float
                            face?.setColor(fieldColour(value, true, mid, deep))
                        }
                        start()
                    }
                    barAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = LOCK_SECONDS * SECOND_MILLIS
                        interpolator = LinearInterpolator()
                        addUpdateListener { anim ->
                            countdownBar.fraction = anim.animatedValue as Float
                        }
                        start()
                    }
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                handler.removeCallbacks(tick)
                stopAnimators()
            }
        })
    }
}
