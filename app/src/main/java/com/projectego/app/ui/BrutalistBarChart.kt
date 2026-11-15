package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt
import com.projectego.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The usage chart's bars, drawn rather than assembled from views.
 *
 * Drawn because a month is 31 columns: as views that is 31 layouts with 31 tap
 * listeners inside a scroll view, and the bars still could not carry a texture.
 * Everything the shape of a day means is here - a hollow stub is a day ALIVE
 * never saw, a hatched stub is a day there was no allowance to spend, a square
 * on the baseline is a day with the allowance and nothing spent, an ink bar is
 * a day that used it all up.
 *
 * No entry animation and no springs: the bar is already where it belongs when
 * the card opens. Touch only ever selects, so a drag still scrolls the panel.
 *
 * Columns are laid out by [ChartGeometry]: when a month cannot have an honest
 * slot width in one row, it becomes several balanced rows, each with its own
 * baseline. A chart that shrinks to fit 31 days is a chart of 31 hairlines.
 */
class BrutalistBarChart(context: Context) : View(context) {

    /**
     * One column. [fraction] is a share of that day's own allowance, so a full
     * bar means the same thing whatever the allowance was.
     */
    data class Column(
        val label: String,
        val minutes: Long,
        val fraction: Float,
        val kind: DayBarKind,
        /** The column the person is inside right now; it wears the pink cap. */
        val isNow: Boolean = false
    )

    /** The tapped column's index, or -1 when the tap cleared the selection. */
    var onColumnTapped: ((Int) -> Unit)? = null

    private val palette = AlivePalette(context)
    private val dimens = AliveDimens(context)

    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = palette.workPale
    }

    /**
     * The axis: the two edges of a selection. Data ink, not a card border - one
     * hairline in the secondary tone, the weight the month's labels are written
     * in.
     */
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.inkSecondary
        strokeWidth = dp(1f)
    }

    /**
     * A day the system never saw: a hollow capsule at a missing-tone alpha, so
     * the gap in the record has a body of its own and can never be read as a
     * zero-height bar.
     */
    private val missingRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = palette.inkDisabled
    }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = palette.inkSecondary
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val averageLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.manrope_medium)
        textSize = sp(10f)
        textAlign = Paint.Align.RIGHT
        color = palette.inkSecondary
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.manrope_medium)
        // Ten is the floor: below it a label stops being a word and becomes a
        // texture, which is what eight sp made the month's labels.
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = palette.inkSecondary
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.space_grotesk_bold)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = palette.paper
    }

    private val bubble = RectF()
    private val shape = RectF()

    /** Optional attach/data-swap delay so the bars wave up right as their host card lands. */
    var initialRevealDelayMs: Long = DEFAULT_ATTACH_REVEAL_DELAY_MS

    private var columns: List<Column> = emptyList()
    private var selected = -1
    private var lastSelected = -1
    private var revealProgress = 1f
    private var revealPending = false
    private var revealAnimator: ValueAnimator? = null
    private var selectionProgress = 1f
    private var selectionAnimator: ValueAnimator? = null
    private var deselectAlpha = 0f
    private var deselectAnimator: ValueAnimator? = null
    private val linearTime = LinearInterpolator()
    private val selectionEase = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    private val revealBarSpring = OvershootInterpolator(1.15f)
    private val bubbleSpring = OvershootInterpolator(1.75f)

    /**
     * One row, as wide as the columns need, meant for a horizontal scroller.
     *
     * A month is 31 columns. Wrapped into balanced rows it reads as two charts
     * stacked on each other, so the card can instead give it a scroller: every
     * day keeps a thumb-sized slot and the row is swiped. Off by default, because
     * a week and a day fit.
     */
    var singleRowScroll: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * The fill of a bar with minutes in it, over the kind's own colour.
     *
     * The measure chips name three different questions; a chart that answers all
     * of them in the same pale green makes the chip feel like decoration. The
     * role colours behind the chips - blue for the whole screen, red for the
     * allowance, green for the rest - are the same ones here.
     */
    var barColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The column being lived in wears this instead of [barColor], when it is
     * set: the approved Statistik draws the week white and today ink.
     */
    var nowColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** The mean line is a reading of the chart; a header that states the mean
     *  makes it redundant, and pass 2 dropped it. */
    var showAverage: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** How far a finger may wander and still be called a tap. */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            selectAt(event.x, event.y)
            return true
        }
    })

    fun setData(columns: List<Column>) {
        this.columns = columns
        selected = -1
        lastSelected = -1
        revealAnimator?.cancel()
        selectionAnimator?.cancel()
        deselectAnimator?.cancel()
        selectionProgress = 1f
        deselectAlpha = 0f
        if (columns.isNotEmpty() && isAttachedToWindow && AliveMotion.shouldAnimate(this)) {
            startRevealAnimation(delayMs = initialRevealDelayMs)
        } else {
            revealProgress = 1f
            revealPending = columns.isNotEmpty()
        }
        requestLayout()
        invalidate()
    }

    /** Replays the staggered baseline wave reveal cleanly after panel or range transitions. */
    fun replayReveal(delayMs: Long = initialRevealDelayMs) {
        if (columns.isEmpty()) return
        if (isAttachedToWindow && AliveMotion.shouldAnimate(this)) {
            revealPending = false
            startRevealAnimation(delayMs)
        } else {
            revealPending = true
        }
    }

    /** Current global reveal wave fraction exposed for motion diagnostics and tests. */
    val revealFraction: Float get() = revealProgress

    /** Current linear selection progression exposed for motion diagnostics and tests. */
    val selectionFraction: Float get() = selectionProgress

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (revealPending && columns.isNotEmpty() && AliveMotion.shouldAnimate(this)) {
            revealPending = false
            startRevealAnimation(delayMs = initialRevealDelayMs)
        }
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        selectionAnimator?.cancel()
        selectionAnimator = null
        deselectAnimator?.cancel()
        deselectAnimator = null
        revealProgress = 1f
        selectionProgress = 1f
        deselectAlpha = 0f
        lastSelected = -1
        super.onDetachedFromWindow()
    }

    private fun startRevealAnimation(delayMs: Long) {
        revealAnimator?.cancel()
        revealProgress = 0f
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = delayMs
            duration = REVEAL_DURATION_MS
            interpolator = linearTime
            addUpdateListener { anim ->
                revealProgress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    revealProgress = 1f
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) {
                    revealProgress = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun startSelectionAnimation(previousSelected: Int) {
        selectionAnimator?.cancel()
        deselectAnimator?.cancel()
        if (!isAttachedToWindow || !AliveMotion.shouldAnimate(this)) {
            selectionProgress = 1f
            deselectAlpha = 0f
            lastSelected = -1
            return
        }
        if (previousSelected >= 0 && previousSelected != selected) {
            lastSelected = previousSelected
            deselectAlpha = 1f
            deselectAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = DESELECT_DURATION_MS
                interpolator = selectionEase
                addUpdateListener { anim ->
                    deselectAlpha = anim.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        deselectAlpha = 0f
                        lastSelected = -1
                        invalidate()
                    }
                })
                start()
            }
        } else {
            lastSelected = -1
            deselectAlpha = 0f
        }
        if (selected >= 0) {
            selectionProgress = 0f
            selectionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SELECTION_DURATION_MS
                interpolator = linearTime
                addUpdateListener { anim ->
                    selectionProgress = anim.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        selectionProgress = 1f
                        invalidate()
                    }
                })
                start()
            }
        } else {
            selectionProgress = 1f
        }
    }

    /** Which column is selected, so the card can print the day under the chart. */
    val selectedIndex: Int get() = selected

    fun select(index: Int) {
        revealAnimator?.cancel()
        selectionAnimator?.cancel()
        deselectAnimator?.cancel()
        selected = if (index in columns.indices) index else -1
        lastSelected = -1
        selectionProgress = 1f
        deselectAlpha = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // In scroller mode the chart asks for one slot per column instead of
        // fitting the screen; a HorizontalScrollView measures its child with an
        // unspecified width, so this is the width it gets.
        val desiredWidth = if (singleRowScroll && columns.isNotEmpty()) {
            (paddingLeft + paddingRight + 2f * dimens.s + columns.size * scrollSlotPx()).toInt()
        } else {
            suggestedMinimumWidth
        }
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val grid = geometryFor(measuredWidth)
        setMeasuredDimension(measuredWidth, resolveSize(ceil(desiredHeight(grid)).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val grid = geometry()
        if (columns.isEmpty() || grid.isEmpty) return

        val labelBand = labelBandHeight()
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        if (right <= left) return

        val singleRow = grid.rowCount == 1
        val headroom = if (singleRow) bubbleBand() else dimens.s.toFloat()
        val top = paddingTop + headroom
        val foot = height - paddingBottom - labelBand
        val pitch = (foot - top - dp(1f)) / grid.rowCount
        val plotHeight = pitch - rowGap()
        if (plotHeight <= 0f) return

        val slot = (right - left) / grid.columnsPerRow
        val barWidth = ChartGeometry.barWidth(slot, dp(40f), dp(3f))
        val labelMargin = ChartGeometry.LABEL_MARGIN_DP * resources.displayMetrics.density
        val count = columns.size.coerceAtLeast(1)
        val selLinear = selectionProgress.coerceIn(0f, 1f)
        val selEase = selectionEase.getInterpolation(selLinear)
        val dimStrength = when {
            selected >= 0 -> selEase
            lastSelected >= 0 -> deselectAlpha
            else -> 0f
        }

        columns.forEachIndexed { index, column ->
            val row = grid.rowOf(index)
            val centre = left + slot * (index - row * grid.columnsPerRow) + slot / 2f
            val baseline = top + row * pitch + plotHeight

            val colLocal = columnLocalProgress(index, count)
            val colEase = if (colLocal >= 0.999f) 1f else selectionEase.getInterpolation(colLocal)
            val colScale = if (colLocal >= 0.999f) 1f else revealBarSpring.getInterpolation(colLocal)

            if (index == selected) {
                val origBandAlpha = band.alpha
                val origAxisAlpha = axis.alpha
                val bandTop = baseline - plotHeight * selEase
                if (selEase < 0.999f) {
                    band.alpha = (origBandAlpha * selEase).toInt()
                    axis.alpha = (origAxisAlpha * selEase).toInt()
                }
                // The selection is a band across the whole slot rather than a
                // hairline around the bar: at month width a bar is 4dp and an
                // inset outline on it is invisible, while a band is legible at
                // any width and cannot overflow its own column.
                shape.set(centre - slot / 2f, bandTop, centre + slot / 2f, baseline)
                canvas.drawRect(shape, band)
                val axisTop = baseline - (baseline - (top + row * pitch)) * selEase
                canvas.drawLine(shape.left, axisTop, shape.left, baseline, axis)
                canvas.drawLine(shape.right, axisTop, shape.right, baseline, axis)
                band.alpha = origBandAlpha
                axis.alpha = origAxisAlpha
            } else if (index == lastSelected && selected != lastSelected && deselectAlpha > 0.01f) {
                val origBandAlpha = band.alpha
                val origAxisAlpha = axis.alpha
                band.alpha = (origBandAlpha * deselectAlpha).toInt()
                axis.alpha = (origAxisAlpha * deselectAlpha).toInt()
                shape.set(centre - slot / 2f, baseline - plotHeight, centre + slot / 2f, baseline)
                canvas.drawRect(shape, band)
                canvas.drawLine(shape.left, top + row * pitch, shape.left, baseline, axis)
                canvas.drawLine(shape.right, top + row * pitch, shape.right, baseline, axis)
                band.alpha = origBandAlpha
                axis.alpha = origAxisAlpha
            }

            val barEmphasis = if (index == selected && selLinear < 0.999f) {
                1f + 0.11f * sin(PI * selLinear).toFloat()
            } else {
                1f
            }
            val focusAlpha = if (dimStrength > 0.001f && index != selected) {
                if (selected >= 0) {
                    1f - 0.34f * dimStrength
                } else if (index == lastSelected) {
                    1f
                } else {
                    1f - 0.34f * dimStrength
                }
            } else {
                1f
            }

            drawColumn(
                canvas = canvas,
                column = column,
                centre = centre,
                barWidth = barWidth * barEmphasis,
                baseline = baseline,
                plotHeight = plotHeight,
                revealScale = colScale,
                revealEase = colEase,
                focusAlpha = focusAlpha
            )

            // Only the last row is named: a label per row would be three bands of
            // type between the bars, and the day each column is stays readable
            // from its order.
            if (row == grid.rowCount - 1 &&
                ChartGeometry.labelFits(labelPaint.measureText(column.label), slot, labelMargin)
            ) {
                labelPaint.color = if (index == selected) palette.ink else palette.inkSecondary
                val baseAlpha = labelPaint.alpha
                val labelRevealAlpha = if (colEase < 0.999f) 0.15f + 0.85f * colEase else 1f
                labelPaint.alpha = (baseAlpha * labelRevealAlpha * (0.72f + 0.28f * focusAlpha)).toInt()
                labelPaint.isFakeBoldText = column.isNow || index == selected
                val text = column.label.uppercase()
                val labelRise = if (colEase < 0.999f) dp(5f) * (1f - colEase) else 0f
                val labelBaseline = baseline + dimens.s - labelPaint.fontMetrics.ascent + labelRise
                canvas.drawText(text, centre, labelBaseline, labelPaint)
                labelPaint.isFakeBoldText = false
                labelPaint.alpha = baseAlpha
            }
        }

        // The average is a comparison, not a bar: a dashed line the eye can
        // measure every column against. A wrapped month has no single baseline
        // worth averaging across, so it is only drawn when there is one row.
        if (grid.rowCount == 1 && showAverage) drawAverage(canvas, left, right, top, plotHeight)

        // The number belongs in the sky above a bar, and a wrapped month has no
        // sky - the day line under the chart says it instead.
        if (singleRow && selected in columns.indices) {
            val row = grid.rowOf(selected)
            drawValueBubble(
                canvas = canvas,
                column = columns[selected],
                centre = left + slot * (selected - row * grid.columnsPerRow) + slot / 2f,
                top = top,
                progress = selLinear,
                exiting = false
            )
        } else if (singleRow && selected < 0 && lastSelected in columns.indices && deselectAlpha > 0.02f) {
            val row = grid.rowOf(lastSelected)
            drawValueBubble(
                canvas = canvas,
                column = columns[lastSelected],
                centre = left + slot * (lastSelected - row * grid.columnsPerRow) + slot / 2f,
                top = top,
                progress = deselectAlpha,
                exiting = true
            )
        }
    }

    private fun fillWith(@ColorInt color: Int) {
        face.color = color
    }

    private fun columnLocalProgress(index: Int, count: Int): Float {
        if (revealProgress >= 0.999f || count <= 0) return 1f
        val staggerWindow = if (count > 1) WAVE_STAGGER_WINDOW else 0f
        val start = if (count <= 1) {
            0f
        } else if (singleRowScroll && count > VISIBLE_SCROLL_COLUMNS) {
            val firstVisible = count - VISIBLE_SCROLL_COLUMNS
            val rel = (index - firstVisible).coerceAtLeast(0)
            (rel.toFloat() / (VISIBLE_SCROLL_COLUMNS - 1).coerceAtLeast(1)) * staggerWindow
        } else {
            (index.toFloat() / (count - 1)) * staggerWindow
        }
        val span = (1f - staggerWindow).coerceAtLeast(0.01f)
        return ((revealProgress - start) / span).coerceIn(0f, 1f)
    }

    private fun drawColumn(
        canvas: Canvas,
        column: Column,
        centre: Float,
        barWidth: Float,
        baseline: Float,
        plotHeight: Float,
        revealScale: Float = 1f,
        revealEase: Float = 1f,
        focusAlpha: Float = 1f
    ) {
        val half = barWidth / 2f
        val stubHeight = dp(9f)
        // Pass 2 draws a bar as a rounded rectangle, not a capsule: at a phone's
        // width a full half-width radius read as a lozenge, and the week's bars
        // need a side you can read a height against.
        val corner = min(half, dp(7f))
        val stubAlphaScale = if (revealEase >= 0.999f) 1f else revealEase
        val stubLiftScale = if (revealScale >= 0.999f) 1f else revealScale.coerceAtLeast(0f)

        when (column.kind) {
            // Never seen: a hollow capsule, so the gap in the record keeps a body
            // of its own instead of being flattened into a zero. Inset by half the
            // rim so the marker is exactly as wide as a real bar.
            DayBarKind.MISSING -> {
                if (stubAlphaScale <= 0.02f) return
                val origAlpha = missingRim.alpha
                missingRim.alpha = (origAlpha * stubAlphaScale * focusAlpha).toInt().coerceIn(0, 255)
                val inset = missingRim.strokeWidth / 2f
                val h = stubHeight * (0.45f + 0.55f * stubLiftScale)
                val riseY = if (revealEase < 0.999f) dp(6f) * (1f - revealEase) else 0f
                shape.set(centre - half, baseline - h + riseY, centre + half, baseline + riseY)
                shape.inset(inset, inset)
                canvas.drawRoundRect(shape, corner - inset, corner - inset, missingRim)
                missingRim.alpha = origAlpha
            }

            // No allowance to spend: the same stub as an unseen day, but as a
            // held-back field - the weight the donut gives its aggregate - so it
            // cannot be mistaken for a day that was merely unseen.
            DayBarKind.RECOVERY -> {
                if (stubAlphaScale <= 0.02f) return
                val h = stubHeight * (0.45f + 0.55f * stubLiftScale)
                val riseY = if (revealEase < 0.999f) dp(6f) * (1f - revealEase) else 0f
                shape.set(centre - half, baseline - h + riseY, centre + half, baseline + riseY)
                fillWith(palette.warmGray)
                face.alpha = (HELD_BACK_ALPHA * stubAlphaScale * focusAlpha).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(shape, corner, corner, face)
                face.alpha = 255
            }

            // An allowance and nothing spent: a square on the baseline. Drawing a
            // hairline bar here would claim a height the day does not have.
            DayBarKind.ZERO -> {
                if (stubAlphaScale <= 0.02f) return
                val tick = min(barWidth, dp(8f)) * (0.5f + 0.5f * stubLiftScale)
                val riseY = if (revealEase < 0.999f) dp(5f) * (1f - revealEase) else 0f
                shape.set(centre - tick / 2f, baseline - tick + riseY, centre + tick / 2f, baseline + riseY)
                fillWith(palette.ink)
                face.alpha = (255 * stubAlphaScale * focusAlpha).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(shape, tick / 2f, tick / 2f, face)
                face.alpha = 255
            }

            else -> {
                // A spent day is never invisible: the shortest bar the eye can
                // still find is a sliver of its own field.
                val fullHeight = max(column.fraction * plotHeight, dp(3f))
                val height = if (revealScale >= 0.999f && revealEase >= 0.999f) {
                    fullHeight
                } else {
                    max(fullHeight * revealScale, dp(3f) * revealEase)
                }
                if (height <= 0.1f) return
                shape.set(centre - half, baseline - height, centre + half, baseline)
                val full = column.kind == DayBarKind.FULL
                // Soft forest, not forest: forest means "ALIVE is offering a
                // choice", and a bar of minutes used is not a choice the app made.
                // A day that ran its allowance out goes to the red that already
                // means "this is running out" everywhere else in the app - the
                // number under the chart says the same thing, as it always must.
                fillWith(
                    if (column.isNow && nowColor != null) {
                        nowColor!!
                    } else {
                        barColor ?: if (full) palette.shortMid else palette.softForest
                    }
                )
                val baseAlpha = face.alpha
                val revealAlpha = if (revealEase >= 0.999f) 1f else (0.28f + 0.72f * revealEase)
                face.alpha = (baseAlpha * focusAlpha * revealAlpha).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(shape, corner, corner, face)
                face.alpha = baseAlpha
            }
        }
    }

    /** A dashed line at the mean of the columns, labelled once at its right. */
    private fun drawAverage(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        plotHeight: Float
    ) {
        val data = columns.filter { it.kind != DayBarKind.MISSING }
        if (data.size < 2) return
        val average = data.sumOf { it.fraction.toDouble() }.toFloat() / data.size
        if (average <= 0f) return
        val y = top + plotHeight - average * plotHeight
        canvas.drawLine(left, y, right, y, averagePaint)
        canvas.drawText(
            context.getString(R.string.chart_average),
            right - dp(2f),
            y - dp(4f),
            averageLabelPaint
        )
    }

    private fun drawValueBubble(
        canvas: Canvas,
        column: Column,
        centre: Float,
        top: Float,
        progress: Float,
        exiting: Boolean
    ) {
        val text = column.minutes.toString()
        val boxWidth = valuePaint.measureText(text) + dp(12f)
        val height = bubbleBand() - dp(6f)
        val halfWidth = boxWidth / 2f
        var bubbleLeft = centre - halfWidth
        var bubbleRight = centre + halfWidth

        // Keep the box on the card: the first and last columns sit at the edges.
        val overflowLeft = (paddingLeft + dp(2f)) - bubbleLeft
        if (overflowLeft > 0f) {
            bubbleLeft += overflowLeft
            bubbleRight += overflowLeft
        }
        val overflowRight = bubbleRight - (this.width - paddingRight - dp(2f))
        if (overflowRight > 0f) {
            bubbleLeft -= overflowRight
            bubbleRight -= overflowRight
        }

        bubble.set(bubbleLeft, top - height, bubbleRight, top)
        val raw = progress.coerceIn(0f, 1f)
        val popScale = if (exiting) {
            0.80f + 0.20f * raw
        } else {
            0.66f + 0.34f * bubbleSpring.getInterpolation(raw)
        }
        val fade = if (exiting) raw else selectionEase.getInterpolation(raw)
        val liftY = if (exiting) dp(4f) * (1f - raw) else dp(7f) * (1f - fade)
        val bubbleAlpha = (255 * fade).toInt().coerceIn(0, 255)
        val origValueAlpha = valuePaint.alpha
        canvas.save()
        canvas.translate(0f, liftY)
        canvas.scale(popScale, popScale, bubble.centerX(), bubble.bottom)
        face.color = palette.ink
        face.alpha = bubbleAlpha
        canvas.drawRoundRect(bubble, height / 2f, height / 2f, face)
        face.alpha = 255
        valuePaint.alpha = bubbleAlpha
        val metrics = valuePaint.fontMetrics
        canvas.drawText(text, bubble.centerX(), bubble.centerY() - (metrics.ascent + metrics.descent) / 2f, valuePaint)
        valuePaint.alpha = origValueAlpha
        canvas.restore()
    }

    private fun bubbleBand(): Float = dp(24f)

    private fun rowGap(): Float = dp(10f)

    private fun labelBandHeight(): Float =
        (labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent) + dimens.s + dp(2f)

    /** The columns this width asks for - and therefore how tall the view wants to be. */
    private fun geometry(): ChartLayout = geometryFor(width)

    private fun geometryFor(viewWidth: Int): ChartLayout =
        if (singleRowScroll && columns.isNotEmpty()) {
            // One row, however wide it becomes: the scroller owns the overflow.
            ChartLayout(columns.size, 1, columns.size)
        } else {
            ChartGeometry.layout(
                viewWidth - paddingLeft - paddingRight - 2f * dimens.s,
                columns.size,
                resources.displayMetrics.density
            )
        }

    /** The width one day gets in scroller mode: readable, and a thumb wide. */
    private fun scrollSlotPx(): Float = dp(SCROLL_SLOT_DP)

    /**
     * The height a grid needs, asked for rather than imposed.
     *
     * A month used to be handed 150dp for 31 columns and expected to fit; the
     * result was bars thinner than their own outline. Here the chart says how
     * many rows its columns need and how tall those rows are, and the card gives
     * it that.
     */
    private fun desiredHeight(grid: ChartLayout): Float {
        if (grid.isEmpty) return dimens.xl.toFloat()
        val headroom = if (grid.rowCount == 1) bubbleBand() else dimens.s.toFloat()
        val plot = dp(if (grid.rowCount == 1) SINGLE_ROW_PLOT_DP else WRAPPED_ROW_PLOT_DP)
        return paddingTop + headroom +
            grid.rowCount * (plot + rowGap()) - rowGap() +
            dp(1f) + labelBandHeight() + paddingBottom
    }

    private fun selectAt(x: Float, y: Float) {
        if (columns.isEmpty()) return
        val grid = geometry()
        if (grid.isEmpty) return
        val left = paddingLeft + dimens.s.toFloat()
        val right = width - paddingRight - dimens.s.toFloat()
        if (x < left || x > right) return

        val singleRow = grid.rowCount == 1
        val top = paddingTop + (if (singleRow) bubbleBand() else dimens.s.toFloat())
        val foot = height - paddingBottom - labelBandHeight()
        val pitch = (foot - top - dp(1f)) / grid.rowCount
        if (pitch <= 0f) return

        val slot = (right - left) / grid.columnsPerRow
        val row = ((y - top) / pitch).toInt().coerceIn(0, grid.rowCount - 1)
        val column = ((x - left) / slot).toInt().coerceIn(0, grid.columnsPerRow - 1)
        val index = grid.indexAt(row, column)
        if (index < 0) return

        val previous = selected
        selected = if (selected == index) -1 else index
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        startSelectionAnimation(previous)
        invalidate()
        onColumnTapped?.invoke(selected)
    }

    /** A TalkBack double tap has no coordinate, so it walks to the next day. */
    override fun performClick(): Boolean {
        if (columns.isNotEmpty()) {
            val previous = selected
            selected = if (selected + 1 < columns.size) selected + 1 else 0
            startSelectionAnimation(previous)
            invalidate()
            onColumnTapped?.invoke(selected)
        }
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                // A thumb tap is never perfectly still, and the panel's ScrollView
                // claims the gesture the moment it drifts past the touch slop -
                // which is why tapping a bar used to answer with nothing at all.
                // Ask for the gesture up front, and hand it straight back as soon
                // as this really is a drag, so scrolling keeps working.
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE ->
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }

            else -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        taps.onTouchEvent(event)
        return true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val DEFAULT_ATTACH_REVEAL_DELAY_MS = 120L
        const val REVEAL_DURATION_MS = 740L
        const val SELECTION_DURATION_MS = 260L
        const val DESELECT_DURATION_MS = 180L
        const val WAVE_STAGGER_WINDOW = 0.48f
        const val VISIBLE_SCROLL_COLUMNS = 9

        /** A recovery day's field, held back the way the donut holds its aggregate. */
        const val HELD_BACK_ALPHA = 115

        /** One row gets a whole chart's plot height: there is nothing above it. */
        const val SINGLE_ROW_PLOT_DP = 140f

        /** One day's slot in scroller mode: wide enough for its number and a thumb. */
        const val SCROLL_SLOT_DP = 26f

        /** A wrapped month is a grid, and a grid row is a step, not a stage. */
        const val WRAPPED_ROW_PLOT_DP = 48f

    }
}
