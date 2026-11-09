package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.projectego.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Today's entertainment time, split by app.
 *
 * A ring rather than a bar list because the question it answers is "which share
 * of the day was this", and a share is easier to see as an arc. Slices are
 * separated by the card showing through between them, so two light fills next
 * to each other still read as two slices without a line being drawn - and every
 * slice is named in the legend beside the ring, which is where the reading
 * actually happens. The aggregate is the same hue held back to a field, and a
 * day with no numbers yet is a dashed band: a shape that is visibly missing.
 *
 * Nothing animates in. Touch selects, and a drag still scrolls the panel.
 */
class BrutalistDonut(context: Context) : View(context) {

    /**
     * One slice. [hatched] marks the aggregate - the minutes that belong to apps
     * past the fourth - and the name is history: it is drawn as its own hue held
     * back to a translucent field, so it is distinguishable as a weight and not
     * only as a colour nobody can name.
     */
    data class Slice(val label: String, val minutes: Long, val color: Int, val hatched: Boolean = false)

    /** The tapped slice's index, or -1 when the tap cleared the selection. */
    var onSliceTapped: ((Int) -> Unit)? = null

    /** Printed under the number when no slice is selected. */
    var totalCaption: String = ""

    /**
     * How the figure in the hole is written. Left null it is a bare number of
     * minutes; the app hands in a formatter so an amount past an hour is said in
     * hours, the way every other reading in the panel is.
     */
    var minutesText: ((Long) -> String)? = null

    private val palette = AlivePalette(context)
    private val dimens = AliveDimens(context)

    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * The empty ring's band: ink at a missing-tone alpha, dashed. A solid ring
     * would read as data, and a frame would read as a border, so "no numbers
     * yet" is the same word the family's hollow member uses - a shape that is
     * visibly missing.
     */
    private val emptyBand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.inkDisabled
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.space_grotesk_bold)
        textSize = sp(40f)
        textAlign = Paint.Align.CENTER
        color = palette.ink
    }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.manrope_medium)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        color = palette.inkSecondary
    }

    private val bounds = RectF()
    private val hole = RectF()
    private val path = Path()

    private var slices: List<Slice> = emptyList()
    private var totalMinutes = 0L
    private var selected = -1

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            selectAt(event.x, event.y)
            return true
        }
    })

    fun setData(slices: List<Slice>, totalMinutes: Long) {
        this.slices = slices
        this.totalMinutes = totalMinutes
        selected = -1
        invalidate()
    }

    val selectedIndex: Int get() = selected

    /** Driven by the legend beside the ring, so both point at the same slice. */
    fun select(index: Int) {
        selected = if (index in slices.indices) index else -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centreX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centreY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val outer = min(
            (width - paddingLeft - paddingRight) / 2f,
            (height - paddingTop - paddingBottom) / 2f
        )
        val inner = outer * HOLE_RATIO
        if (outer <= 0f) return

        if (totalMinutes <= 0L || slices.isEmpty()) {
            drawEmptyRing(canvas, centreX, centreY, outer, inner)
            return
        }

        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.minutes / totalMinutes
            if (sweep <= 0f) return@forEachIndexed
            val isSelected = index == selected
            val dimmed = selected != -1 && !isSelected
            val lift = if (isSelected) dp(6f) else 0f
            val mid = Math.toRadians((startAngle + sweep / 2f).toDouble())

            bounds.set(
                centreX - outer + (cos(mid) * lift).toFloat(),
                centreY - outer + (sin(mid) * lift).toFloat(),
                centreX + outer + (cos(mid) * lift).toFloat(),
                centreY + outer + (sin(mid) * lift).toFloat()
            )

            face.color = slice.color
            face.alpha = when {
                dimmed -> DIMMED_ALPHA
                slice.hatched -> AGGREGATE_ALPHA
                else -> FULL_ALPHA
            }

            hole.set(bounds)
            hole.inset(bounds.width() * (1f - HOLE_RATIO) / 2f, bounds.height() * (1f - HOLE_RATIO) / 2f)

            path.reset()
            if (sweep >= FULL_CIRCLE_DEGREES) {
                // A 360-degree arc starts and ends on the same point, so the two
                // arcs of the usual wedge collapse into a line - one app in a day
                // used to be drawn as a hairline. An even-odd annulus is a ring
                // in every renderer, with no seam to draw.
                path.fillType = Path.FillType.EVEN_ODD
                path.addOval(bounds, Path.Direction.CW)
                path.addOval(hole, Path.Direction.CW)
            } else {
                // Slices are separated by the card showing through, not by an ink
                // edge: each wedge stops short of its neighbour by half a gap on
                // both sides. Two light fills next to each other still read as
                // two slices because the gap is the surface between them.
                val gap = if (slices.size > 1) minOf(GAP_DEGREES, sweep / 4f) else 0f
                path.fillType = Path.FillType.WINDING
                path.arcTo(bounds, startAngle + gap / 2f, sweep - gap)
                path.arcTo(hole, startAngle + sweep - gap / 2f, -(sweep - gap), false)
                path.close()
            }
            canvas.drawPath(path, face)

            startAngle += sweep
        }

        face.alpha = FULL_ALPHA
        drawCentre(canvas, centreX, centreY, inner)
    }

    private fun drawEmptyRing(canvas: Canvas, centreX: Float, centreY: Float, outer: Float, inner: Float) {
        // One dashed band at the ring's mid radius, as deep as the ring itself:
        // no data is a shape that is visibly missing, never a frame and never a
        // zero-height ring.
        val mid = (outer + inner) / 2f
        emptyBand.strokeWidth = outer - inner
        bounds.set(centreX - mid, centreY - mid, centreX + mid, centreY + mid)
        var angle = 0f
        repeat(EMPTY_DASHES) {
            canvas.drawArc(bounds, angle, EMPTY_DASH_SWEEP, false, emptyBand)
            angle += 360f / EMPTY_DASHES
        }
        drawCentre(canvas, centreX, centreY, inner)
    }

    /** The number in the hole: the selected slice, or the day's total. */
    private fun drawCentre(canvas: Canvas, centreX: Float, centreY: Float, inner: Float) {
        val slice = slices.getOrNull(selected)
        val number = minutesText?.invoke(slice?.minutes ?: totalMinutes)
            ?: (slice?.minutes ?: totalMinutes).toString()
        val caption = slice?.label ?: totalCaption
        val maxWidth = inner * 1.5f

        // Sized to the hole rather than fixed at one size. "3j32" is four glyphs
        // and a long day reaches five, and a figure that crosses the inner edge
        // stops being a figure and becomes a defect.
        numberPaint.textSize = fittedNumberSize(number, maxWidth)
        val numberMetrics = numberPaint.fontMetrics
        val captionMetrics = captionPaint.fontMetrics
        val numberHeight = numberMetrics.descent - numberMetrics.ascent
        val captionHeight = if (caption.isEmpty()) 0f else captionMetrics.descent - captionMetrics.ascent + dp(4f)
        val top = centreY - (numberHeight + captionHeight) / 2f

        canvas.drawText(
            number,
            centreX,
            top - numberMetrics.ascent,
            numberPaint
        )
        if (caption.isNotEmpty()) {
            val fitted = TextUtils.ellipsize(caption.uppercase(), captionPaint, maxWidth, TextUtils.TruncateAt.END)
            canvas.drawText(
                fitted,
                0,
                fitted.length,
                centreX,
                top + numberHeight + dp(4f) - captionMetrics.ascent,
                captionPaint
            )
        }
    }

    private fun selectAt(x: Float, y: Float) {
        if (slices.isEmpty() || totalMinutes <= 0L) return

        val centreX = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val centreY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val outer = min(
            (width - paddingLeft - paddingRight) / 2f,
            (height - paddingTop - paddingBottom) / 2f
        )
        val distance = hypot(x - centreX, y - centreY)
        if (distance > outer + dp(8f) || distance < outer * HOLE_RATIO * 0.8f) return

        // atan2 gives -180..180 from the positive x axis; the ring starts at the
        // top and runs clockwise, so both have to be shifted into that frame.
        var angle = Math.toDegrees(atan2((y - centreY).toDouble(), (x - centreX).toDouble())).toFloat() + 90f
        if (angle < 0f) angle += 360f

        var swept = 0f
        slices.forEachIndexed { index, slice ->
            val sweep = 360f * slice.minutes / totalMinutes
            if (angle >= swept && angle < swept + sweep) {
                selected = if (selected == index) -1 else index
                invalidate()
                onSliceTapped?.invoke(selected)
                return
            }
            swept += sweep
        }
    }

    /** A TalkBack double tap has no coordinate, so it walks to the next slice. */
    override fun performClick(): Boolean {
        if (slices.isNotEmpty()) {
            selected = if (selected + 1 < slices.size) selected + 1 else 0
            invalidate()
            onSliceTapped?.invoke(selected)
        }
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        taps.onTouchEvent(event)
        // See BrutalistBarChart: claiming the gesture is what makes taps arrive,
        // and the scroll view still intercepts the moment the finger moves.
        return true
    }

    /**
     * The biggest the centre figure may be drawn without crossing the hole.
     * Measured at its full size first, then scaled down - a share keeps the
     * shape of the number instead of re-deciding it per string.
     */
    private fun fittedNumberSize(text: String, maxWidth: Float): Float {
        val full = sp(NUMBER_TEXT_SP)
        numberPaint.textSize = full
        val width = numberPaint.measureText(text)
        return if (width <= maxWidth || width <= 0f) full else full * (maxWidth / width)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        /** The hole as a share of the outer radius. */
        const val HOLE_RATIO = 0.68f

        /** At or above this a "slice" is the whole ring, which needs its own path. */
        const val FULL_CIRCLE_DEGREES = 359.9f

        /** The centre figure at full size; it shrinks only when it has to. */
        const val NUMBER_TEXT_SP = 40f

        const val FULL_ALPHA = 255
        const val DIMMED_ALPHA = 96

        /** The air between two neighbouring slices, in degrees. */
        const val GAP_DEGREES = 2f

        /** The aggregate's field: the hue, held back so it reads as "the rest". */
        const val AGGREGATE_ALPHA = 115

        /** How many dashes the empty band breaks into, and how long each is. */
        const val EMPTY_DASHES = 14
        const val EMPTY_DASH_SWEEP = 16f
    }
}
