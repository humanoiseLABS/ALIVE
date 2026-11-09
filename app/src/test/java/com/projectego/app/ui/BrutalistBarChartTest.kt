package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The month's bars, measured as pixels.
 *
 * Three rules the flat language puts on this chart, each of which the stroked
 * drawing broke: a bar is a colour field and carries no ink edge; a day the
 * system never saw keeps a body of its own so it can never be read as a zero;
 * and a bar's corners are the pill's, not a 4dp step, because the step belonged
 * to a grid this app no longer draws on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrutalistBarChartTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val ink = 0xFF141414.toInt()
    private val forest = 0xFFDCE5DC.toInt()

    @Test
    fun `a spent day is a colour field with no ink edge`() {
        val bitmap = render(chart(BrutalistBarChart.Column("S", 40, 0.5f, DayBarKind.SPENT)))
        val inked = pixels(bitmap).filter { it.second == ink }
        assertTrue("the bar still wears an ink edge or an ink axis: ${inked.size} ink pixels", inked.isEmpty())
        assertTrue("the bar's field is missing", pixels(bitmap).any { it.second == forest })
    }

    @Test
    fun `a day that was never seen keeps a body, and it is not a zero`() {
        val missing = render(chart(BrutalistBarChart.Column("S", 0, 0f, DayBarKind.MISSING)))
        val zero = render(chart(BrutalistBarChart.Column("S", 0, 0f, DayBarKind.ZERO)))
        val plotTop = (missing.height * 0.6f).toInt()

        // The zero is a solid ink tick on the baseline.
        val zeroInk = pixels(zero).filter { (xy, p) -> xy.second < plotTop && p == ink }
        assertTrue("the zero day drew no ink tick", zeroInk.isNotEmpty())

        // The missing day is a hollow marker: an ink rim at a missing-tone alpha
        // around air. Anything opaque in its plot area means it was painted as a
        // body of colour - which is exactly how a missing day gets read as a zero.
        val rim = pixels(missing).filter { (xy, p) ->
            xy.second < plotTop && p ushr 24 in 60..160 && channelsClose(p, ink)
        }
        assertTrue("the missing day has no body at all - it collapsed into the zero", rim.size >= 8)
        val solid = pixels(missing).filter { (xy, p) -> xy.second < plotTop && p ushr 24 > 200 }
        assertTrue(
            "the missing day is painted solid (${solid.size} opaque pixels) - a hollow marker is what says 'unseen'",
            solid.isEmpty()
        )
    }

    @Test
    fun `a bar is one field, with no pixel grain inside it`() {
        // The old drawing poured 1.5dp ink dots into every bar wide enough to hold
        // them, a grain it shared with the canvas. Flat is a colour field: no
        // texture, no third tone inside the bar.
        //
        // A capsule's corner radius cannot be measured here - at a month's width
        // a 4dp step and a pill are both ~half the bar - so this test measures the
        // grain, which really did change, and the radius is left to the preview.
        val bitmap = render(chart(BrutalistBarChart.Column("S", 40, 0.6f, DayBarKind.SPENT)), width = 400)
        val bar = pixels(bitmap).filter { it.second == forest }.map { it.first }
        assertTrue("the bar's field is missing", bar.size >= 40)
        val left = bar.minOf { it.first } + 3
        val right = bar.maxOf { it.first } - 3
        val top = bar.minOf { it.second } + 3
        val bottom = bar.maxOf { it.second } - 3
        // A grain dot is drawn *over* the field, so it lands opaque and merely
        // darker - the question is not "is there a translucent pixel" but "is the
        // field still one tone".
        val offTone = (left..right).flatMap { x ->
            (top..bottom).map { y -> bitmap.getPixel(x, y) }
        }.filter { pixel -> pixel ushr 24 > 200 && !channelsClose(pixel, forest) }
        assertTrue("the bar carries ${offTone.size} pixels of grain or a second tone", offTone.isEmpty())
    }

    // ---------- raster ----------

    private fun chart(column: BrutalistBarChart.Column): BrutalistBarChart =
        BrutalistBarChart(context).apply { setData(listOf(column)) }

    private fun render(view: View, width: Int = 200, height: Int = 200): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): List<Pair<Pair<Int, Int>, Int>> =
        (0 until bitmap.height).flatMap { y ->
            (0 until bitmap.width).map { x -> (x to y) to bitmap.getPixel(x, y) }
        }

    /** Premultiplied storage rounds a translucent stroke by one per channel. */
    private fun channelsClose(pixel: Int, want: Int): Boolean =
        listOf(16, 8, 0).all { shift ->
            kotlin.math.abs((pixel ushr shift and 0xFF) - (want ushr shift and 0xFF)) <= 2
        }
}
