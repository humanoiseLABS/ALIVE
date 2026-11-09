package com.projectego.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * The two parts of the card that must be ALIVE's own, painted by ALIVE.
 *
 * A widget layout is built by the launcher, and the launcher cannot load a font
 * that ships inside this app - it falls back to its own without a word of
 * complaint, which is how the pixel figure turned into the system face on the
 * home screen. So the figure and the allowance meter are drawn here, where the
 * typeface is readable, and handed over as pictures.
 *
 * The card itself is not painted here: it is [R.drawable.bg_widget], a flat
 * field the host inflates at the widget's real size, so its corner cannot be
 * stretched by the launcher.
 */
object AliveWidgetArt {

    /** The size the figure wants to be, and the smallest it will go to fit. */
    private const val FIGURE_SP = 28f
    private const val FIGURE_MIN_SP = 15f

    /** Widest the figure may be before it steps down a size. */
    private const val FIGURE_MAX_DP = 200f

    /** The gap between two slices, in dp at the size the view is drawn. */
    private const val METER_SEAM_DP = 2.5f

    /**
     * The day's duration in the display face, at the largest size that still fits
     * the card. The size is stepped down rather than the picture scaled: this is
     * a bitmap the launcher will draw at its own density, so scaling it blurs it
     * whatever font is inside - which used to be the reason for the rule and has
     * nothing to do with the face that moved here.
     */
    fun figure(context: Context, text: String, @ColorInt colour: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val maxWidth = FIGURE_MAX_DP * density

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                ?: Typeface.DEFAULT_BOLD
            color = colour
            letterSpacing = 0f
            textSize = FIGURE_SP * density
        }
        while (paint.textSize > FIGURE_MIN_SP * density && paint.measureText(text) > maxWidth) {
            paint.textSize -= density
        }

        val metrics = paint.fontMetrics
        val width = max(1, ceil(paint.measureText(text)).toInt())
        val height = max(1, ceil(metrics.descent - metrics.ascent).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)
        return bitmap
    }

    /**
     * The allowance as slices in the ring's own colours, air between them. The
     * empty part of a day is left transparent so the card's meter field - the
     * mint track the allowance sits in - shows through.
     *
     * The bitmap is drawn at [widthPx] by [heightPx], the size the view is laid
     * out at, rather than at a fixed size the launcher stretches: a rounded end
     * squashed by a different horizontal and vertical scale is a lens, not a
     * capsule.
     */
    fun meter(
        context: Context,
        slices: List<WidgetMeter.Slice>,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val width = max(1, widthPx)
        val height = max(1, heightPx)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = height / 2f
        val halfGap = METER_SEAM_DP * context.resources.displayMetrics.density / 2f

        // The slices are capsules separated by the card showing through: two
        // solid colours touching is one wider colour, and these are separate
        // apps. The first and last keep their rounded ends at the bar's ends.
        val bounds = mutableListOf<Pair<Float, Float>>()
        var edge = 0f
        slices.forEach { slice ->
            val start = edge
            edge += width * slice.share
            bounds += start to edge
        }
        bounds.forEachIndexed { index, (start, end) ->
            val left = if (index == 0) start else start + halfGap
            val right = if (index == bounds.lastIndex) end else end - halfGap
            if (right - left < 1f) return@forEachIndexed
            paint.color = ContextCompat.getColor(context, slices[index].colour)
            canvas.drawRoundRect(left, 0f, right, height.toFloat(), radius, radius, paint)
        }

        return bitmap
    }
}
