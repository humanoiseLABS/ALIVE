package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.projectego.app.R
import kotlin.math.roundToInt

/**
 * Shared chrome helpers for [CriticalWarningCard] and [CriticalTimeCard] so
 * both phases of [CriticalAlertActivity] share one visual grammar, header badge,
 * hero minute readout, and capsule meter.
 */
internal object CriticalCardChrome {

    fun surfaceDrawable(
        @ColorInt fillColor: Int,
        cornerRadiusPx: Float,
        @ColorInt strokeColor: Int = 0,
        strokeWidthPx: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = cornerRadiusPx
        if (strokeWidthPx > 0 && strokeColor != 0) {
            setStroke(strokeWidthPx, strokeColor)
        }
    }

    fun headerRow(
        context: Context,
        ui: AliveComponents,
        @StringRes eyebrowRes: Int,
        trailingBadge: View? = null
    ): LinearLayout {
        val d = ui.dimens
        return ui.row().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL

            val companionBadge = LinearLayout(context).apply {
                val badgeSize = d.markSize
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
                gravity = Gravity.CENTER
                val pad = d.dp(6)
                setPadding(pad, pad, pad, pad)
                background = surfaceDrawable(ui.palette.shortPale, d.dp(16).toFloat())
                addView(
                    ui.mark(
                        AliveStates.faceSpriteFor(AliveFace.ANGRY_FULL),
                        d.dp(36),
                        context.getString(AliveStates.faceDescription(AliveFace.ANGRY_FULL))
                    )
                )
            }
            addView(companionBadge)
            addView(ui.hgap(d.m))

            addView(
                ui.kicker(context.getString(eyebrowRes)).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    letterSpacing = 0.10f
                    setTextColor(ui.palette.shortDark)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )

            if (trailingBadge != null) {
                addView(ui.hgap(d.s))
                addView(
                    trailingBadge,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
    }

    fun heroBoard(
        context: Context,
        ui: AliveComponents,
        remainingMinutes: Long,
        progressBar: View? = null
    ): LinearLayout {
        val d = ui.dimens
        val safeRemaining = remainingMinutes.coerceAtLeast(0L)
        return ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = surfaceDrawable(ui.palette.shortPale, d.radiusField.toFloat())
            val hPad = d.dp(18)
            val vPad = d.dp(16)
            setPadding(hPad, vPad, hPad, vPad)

            val figureRow = ui.row().apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.BOTTOM

                addView(
                    ui.timer(context.getString(R.string.stat_minutes, safeRemaining)).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
                        setTextColor(ui.palette.ink)
                        maxLines = 1
                    }
                )
                addView(
                    ui.meta(context.getString(R.string.stat_min_left)).apply {
                        setTextColor(ui.palette.ink)
                        setPadding(d.s, 0, 0, d.dp(6))
                    }
                )
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    }
                )
                addView(
                    ImageView(context).apply {
                        val iconSize = d.dp(28)
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            bottomMargin = d.dp(6)
                        }
                        setImageResource(R.drawable.ic_hourglass_flat)
                        setColorFilter(ui.palette.shortDark)
                        contentDescription = null
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                )
            }
            addView(figureRow)

            if (progressBar != null) {
                addView(ui.vgap(d.dp(12)))
                addView(progressBar)
            }
        }
    }

    class CapsuleProgressBar(
        context: Context,
        @ColorInt trackColor: Int,
        @ColorInt fillColor: Int,
        initialFraction: Float
    ) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }

        var fraction: Float = initialFraction.coerceIn(0f, 1f)
            set(value) {
                val clamped = value.coerceIn(0f, 1f)
                if (field != clamped) {
                    field = clamped
                    invalidate()
                }
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val radius = h / 2f
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackPaint)
            if (fraction > 0f) {
                val fillW = (w * fraction).coerceAtLeast(h.coerceAtMost(w))
                canvas.drawRoundRect(0f, 0f, fillW, h, radius, radius, fillPaint)
            }
        }
    }
}

/**
 * The cross-app critical remaining-time alert card displayed inside
 * [CriticalAlertActivity] after the initial five-second pause.
 */
object CriticalTimeCard {

    fun build(
        context: Context,
        ui: AliveComponents,
        remainingMinutes: Long,
        limitMinutes: Long,
        onDismiss: () -> Unit
    ): View {
        val d = ui.dimens
        val safeRemaining = remainingMinutes.coerceAtLeast(0L)
        val hasLimit = limitMinutes > 0L

        val quotaBadge = if (hasLimit) {
            val pct = ((safeRemaining.toDouble() / limitMinutes.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
            ui.metaSmall("$pct%").apply {
                setTextColor(ui.palette.shortDark)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                background = CriticalCardChrome.surfaceDrawable(
                    ui.palette.shortPale,
                    d.radiusPill.toFloat()
                )
                setPadding(d.m, d.dp(6), d.m, d.dp(6))
            }
        } else {
            null
        }

        val meterBar = if (hasLimit) {
            val share = (safeRemaining.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
            CriticalCardChrome.CapsuleProgressBar(
                context = context,
                trackColor = ui.palette.white,
                fillColor = ui.palette.shortDark,
                initialFraction = share
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    d.dp(10)
                )
            }
        } else {
            null
        }

        val card = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = CriticalCardChrome.surfaceDrawable(
                fillColor = ui.palette.white,
                cornerRadiusPx = d.radiusCard.toFloat(),
                strokeColor = ui.palette.inkHairline,
                strokeWidthPx = d.dp(1)
            )
            val pad = d.dp(20)
            setPadding(pad, pad, pad, pad)
        }

        card.addView(
            CriticalCardChrome.headerRow(
                context = context,
                ui = ui,
                eyebrowRes = R.string.chart_alert_eyebrow,
                trailingBadge = quotaBadge
            )
        )
        card.addView(ui.vgap(d.l))
        card.addView(
            CriticalCardChrome.heroBoard(
                context = context,
                ui = ui,
                remainingMinutes = safeRemaining,
                progressBar = meterBar
            )
        )
        card.addView(ui.vgap(d.l))
        card.addView(
            ui.headline(
                context.getString(AliveStates.alertHeadline(remainingMinutes), remainingMinutes)
            ).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            }
        )
        card.addView(ui.vgap(d.s))
        card.addView(
            ui.note(context.getString(R.string.alert_support)).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
        )
        card.addView(ui.vgap(d.dp(20)))
        card.addView(
            ui.primary(context.getString(R.string.action_back)).apply {
                minHeight = maxOf(d.touchMin, d.dp(52))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { onDismiss() }
            }
        )

        return card
    }
}
