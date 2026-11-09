package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * The donut, measured as pixels rather than admired as code.
 *
 * The flat language this chart now obeys has one rule the old drawing broke in
 * three places: colour fields carry the structure, so nothing may be drawn as a
 * line. The ink edge between slices, the ink rim on the hole and the hatch on
 * the aggregate were all lines; the approved redraw separates slices with air,
 * makes the aggregate a translucent field, and says "no data yet" with a dashed
 * band - the same word the family's hollow member uses.
 *
 * Every assertion here is a raster probe under [GraphicsMode.Mode.NATIVE],
 * because a stroke and a gap look identical in source and opposite on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrutalistDonutTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val gold = 0xFFD9A52E.toInt()
    private val blue = 0xFF7FA8CB.toInt()
    private val forest = 0xFFDCE5DC.toInt()
    private val ink = 0xFF141414.toInt()

    @Test
    fun `slices are separated by air, not by an ink edge`() {
        val bitmap = render(donut(BrutalistDonut.Slice("a", 30, gold), BrutalistDonut.Slice("b", 30, blue)))
        val inkInBand = bandPixels(bitmap).filter { it.second == ink }
        assertTrue(
            "the ring still carries an ink edge: ${inkInBand.size} ink pixels in the band",
            inkInBand.isEmpty()
        )
        // And the two fields really are there, on either side of the gap.
        val colours = bandPixels(bitmap).map { it.second }.toSet()
        assertTrue("gold slice missing", gold in colours)
        assertTrue("blue slice missing", blue in colours)
    }

    @Test
    fun `a whole-day slice is a ring, not a hairline`() {
        val bitmap = render(donut(BrutalistDonut.Slice("satu-satunya", 60, gold)))
        val coverage = bandPixels(bitmap).count { it.second != TRANSPARENT }.toDouble() / bandPixels(bitmap).size
        assertTrue("the ring covers ${(coverage * 100).toInt()}% of its band", coverage > 0.98)
        assertTrue(
            "the ring still carries an ink edge",
            bandPixels(bitmap).none { it.second == ink }
        )
    }

    @Test
    fun `the aggregate is a translucent field, not a hatch`() {
        val bitmap = render(
            donut(
                BrutalistDonut.Slice("a", 40, gold),
                BrutalistDonut.Slice("b", 30, blue),
                BrutalistDonut.Slice("lainnya", 10, forest, hatched = true)
            )
        )
        val band = bandPixels(bitmap)
        assertTrue(
            "the aggregate is still hatched: ${band.count { it.second == ink }} ink pixels",
            band.none { it.second == ink }
        )
        val aggregate = band.filter { it.first in forestBandAngles() }
        assertTrue("the aggregate drew nothing", aggregate.isNotEmpty())
        val alphas = aggregate.map { it.second ushr 24 }.distinct()
        assertTrue(
            "the aggregate is not one translucent field, alphas were $alphas",
            alphas.all { it in 90..150 }
        )
    }

    @Test
    fun `no data is a dashed band, never a frame and never a solid ring`() {
        val bitmap = render(donut())
        val mid = midRadius(bitmap)
        val hits = (0 until 360).count { angle ->
            val (x, y) = at(bitmap, mid, angle.toFloat())
            bitmap.getPixel(x, y) ushr 24 > 0
        }
        val coverage = hits / 360.0
        assertTrue(
            "the empty ring is ${(coverage * 100).toInt()}% solid - a dashed band sits between 35% and 90%",
            coverage in 0.35..0.90
        )
        // A band, not a hairline: on a dash the ink runs the full ring depth.
        val angle = (0 until 360).first { a ->
            val (x, y) = at(bitmap, mid, a.toFloat())
            bitmap.getPixel(x, y) ushr 24 > 0
        }
        var depth = 0
        var r = mid
        while (r > 0) {
            val (x, y) = at(bitmap, r.toFloat(), angle.toFloat())
            if (bitmap.getPixel(x, y) ushr 24 == 0) break
            depth++
            r--
        }
        r = mid + 1
        while (r < bitmap.width / 2) {
            val (x, y) = at(bitmap, r.toFloat(), angle.toFloat())
            if (bitmap.getPixel(x, y) ushr 24 == 0) break
            depth++
            r++
        }
        assertTrue("the empty band is only ${depth}px deep - that is a hairline, not a band", depth >= 8)
        // And it is ink at a missing-tone alpha, not a white disc. ARGB_8888 stores
        // premultiplied colour, so a translucent stroke comes back rounded by one
        // per channel - compare with a tolerance, not for equality.
        val (x, y) = at(bitmap, mid, angle.toFloat())
        val pixel = bitmap.getPixel(x, y)
        listOf(16, 8, 0).forEach { shift ->
            val got = pixel ushr shift and 0xFF
            val want = ink ushr shift and 0xFF
            assertTrue("the empty band is not ink: $pixel", kotlin.math.abs(got - want) <= 2)
        }
        assertTrue("the empty band is opaque at ${pixel ushr 24}", pixel ushr 24 in 60..160)
    }

    @Test
    fun `the centre figure still lands in ink`() {
        val bitmap = render(donut(BrutalistDonut.Slice("a", 30, gold)))
        val hole = (bitmap.width / 2f * 0.68f * 0.6f).toInt()
        val inked = (bitmap.width / 2 - hole until bitmap.width / 2 + hole).flatMap { x ->
            (bitmap.height / 2 - hole until bitmap.height / 2 + hole).map { y -> bitmap.getPixel(x, y) }
        }
        assertTrue(
            "the centre figure drew nothing in ink - is the canvas stubbed?",
            inked.any { it == ink }
        )
    }

    // ---------- raster ----------

    private fun donut(vararg slices: BrutalistDonut.Slice): BrutalistDonut =
        BrutalistDonut(context).apply {
            setData(slices.toList(), slices.sumOf { it.minutes })
        }

    private fun render(view: View, size: Int = 200): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Pixels in the ring band, as (angle, argb), skipping the hole and the rim. */
    private fun bandPixels(bitmap: Bitmap): List<Pair<Int, Int>> {
        val mid = midRadius(bitmap)
        return (0 until 360 step 2).map { angle ->
            val (x, y) = at(bitmap, mid, angle.toFloat())
            angle to bitmap.getPixel(x, y)
        }
    }

    /** The aggregate slice owns the last eighth of the ring; stay off its gaps. */
    private fun forestBandAngles(): IntRange = 322..354

    private fun midRadius(bitmap: Bitmap): Float {
        val outer = bitmap.width / 2f
        return (outer + outer * 0.68f) / 2f
    }

    private fun at(bitmap: Bitmap, radius: Float, angleDegrees: Float): Pair<Int, Int> {
        val radians = Math.toRadians((angleDegrees - 90).toDouble())
        val x = (bitmap.width / 2f + radius * cos(radians)).toInt()
        val y = (bitmap.height / 2f + radius * sin(radians)).toInt()
        return x.coerceIn(0, bitmap.width - 1) to y.coerceIn(0, bitmap.height - 1)
    }

    private companion object {
        const val TRANSPARENT = 0
    }
}
