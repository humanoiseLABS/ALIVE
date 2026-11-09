package com.projectego.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The widget's flat geometry, measured as pixels.
 *
 * A launcher renders the home-screen card in its own process, so no layout test
 * can see what it really draws. What ALIVE hands over is a drawable and two
 * bitmaps, and that is measurable: the card is one tile field with no edge and
 * no shadow, the meter is capsules separated by air rather than an ink seam,
 * and its empty track is the approved mint field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AliveWidgetArtTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val density: Float get() = context.resources.displayMetrics.density

    @Test
    fun `the card is one tile field at the flat corner`() {
        val width = (320f * density).toInt()
        val height = (160f * density).toInt()
        val drawable = requireNotNull(AppCompatResources.getDrawable(context, R.drawable.bg_widget))
        drawable.setBounds(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        val tile = ContextCompat.getColor(context, R.color.flat_tile)
        val ink = ContextCompat.getColor(context, R.color.flat_ink)
        val opaque = opaquePixels(bitmap)

        assertTrue("the card drew nothing - is the canvas stubbed?", opaque.size > width * height * 0.9)
        assertTrue(
            "the card still paints ink - an edge or a shadow survived",
            visiblePixels(bitmap).none { channelsClose(it, ink) }
        )
        assertTrue(
            "the card's face is not the tile field",
            opaque.all { channelsClose(it, tile) }
        )

        // The corner: empty at all four extremes, and the top row starts where
        // the corner circle crosses it - r - sqrt(2r - 1) from the edge.
        listOf(
            0 to 0,
            width - 1 to 0,
            0 to height - 1,
            width - 1 to height - 1
        ).forEach { (x, y) ->
            assertEquals("corner $x,$y is not empty", 0, bitmap.getPixel(x, y) ushr 24)
        }
        val firstTop = (0 until width).first { bitmap.getPixel(it, 0) ushr 24 > 0 }
        assertEqualsCornerStart(22f * density, firstTop)
    }

    @Test
    fun `the meter is capsules with air between them, never ink`() {
        val width = (284f * density).toInt()
        val height = (20f * density).toInt()
        val mint = ContextCompat.getColor(context, R.color.flat_field_mint)
        val sky = ContextCompat.getColor(context, R.color.flat_field_sky)
        val bitmap = AliveWidgetArt.meter(
            context,
            listOf(
                WidgetMeter.Slice(R.color.flat_field_mint, 0.3f),
                WidgetMeter.Slice(R.color.flat_field_sky, 0.3f)
            ),
            width,
            height
        )
        val ink = ContextCompat.getColor(context, R.color.flat_ink)
        val opaque = opaquePixels(bitmap)

        assertEquals("the meter's bitmap is not the width of its view", width, bitmap.width)
        assertEquals("the meter's bitmap is not the height of its view", height, bitmap.height)
        assertTrue("the meter drew nothing - is the canvas stubbed?", opaque.size > width * height * 0.4)
        assertTrue("the meter still draws ink seams", opaque.none { channelsClose(it, ink) })
        assertTrue("the first slice is missing", opaque.any { channelsClose(it, mint) })
        assertTrue("the second slice is missing", opaque.any { channelsClose(it, sky) })

        // Air, not a drawn seam: the gap between the two slices shows the card.
        val gapX = (width * 0.3f).toInt()
        assertTrue(
            "the gap at x=$gapX is filled - the seam is drawn, not left empty",
            (0 until height).all { bitmap.getPixel(gapX, it) ushr 24 == 0 }
        )

        // A capsule end: empty in the corner, filled at the middle of that end,
        // and a full half-height of top edge missing before the fill starts.
        assertEquals(0, bitmap.getPixel(1, 0) ushr 24)
        assertTrue(
            "the bar ends square, not as a capsule",
            bitmap.getPixel(1, height / 2) ushr 24 > 0
        )
        val firstTop = (0 until width).first { bitmap.getPixel(it, 0) ushr 24 > 0 }
        assertEqualsCornerStart(height / 2f, firstTop)
    }

    @Test
    fun `the meter's empty track is a mint field with no edge`() {
        val width = (284f * density).toInt()
        val height = (20f * density).toInt()
        val drawable = requireNotNull(
            AppCompatResources.getDrawable(context, R.drawable.bg_widget_meter)
        )
        drawable.setBounds(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))

        val mint = ContextCompat.getColor(context, R.color.alive_soft_forest)
        val ink = ContextCompat.getColor(context, R.color.flat_ink)
        val opaque = opaquePixels(bitmap)

        assertTrue("the track drew nothing", opaque.size > width * height * 0.9)
        assertTrue(
            "the track still carries an ink edge",
            opaque.none { channelsClose(it, ink) }
        )
        assertTrue(
            "the track is not the approved mint field",
            opaque.all { channelsClose(it, mint) }
        )
        val firstTop = (0 until width).first { bitmap.getPixel(it, 0) ushr 24 > 0 }
        assertEqualsCornerStart(height / 2f, firstTop)
    }

    // ---------- raster ----------

    /**
     * A corner circle of [radius] covers the top row (y in 0..1) from
     * x = r - sqrt(2r - 1) at its lower edge, so that is where the first
     * covered pixel sits. A smaller radius moves that start visibly inward.
     */
    private fun assertEqualsCornerStart(radius: Float, firstTop: Int) {
        assertEquals(
            "the top edge starts ${firstTop}px in, want a ${radius}px corner",
            radius - sqrt(2f * radius - 1f),
            firstTop.toFloat(),
            2f
        )
    }

    private fun opaquePixels(bitmap: Bitmap): List<Int> =
        (0 until bitmap.height).flatMap { y ->
            (0 until bitmap.width).map { x -> bitmap.getPixel(x, y) }
        }.filter { it ushr 24 == 255 }

    /** Anything the eye could see, so a translucent stroke cannot hide. */
    private fun visiblePixels(bitmap: Bitmap): List<Int> =
        (0 until bitmap.height).flatMap { y ->
            (0 until bitmap.width).map { x -> bitmap.getPixel(x, y) }
        }.filter { it ushr 24 > 16 }

    /** Premultiplied storage rounds a translucent edge by one per channel. */
    private fun channelsClose(pixel: Int, want: Int): Boolean =
        listOf(16, 8, 0).all { shift ->
            abs((pixel ushr shift and 0xFF) - (want ushr shift and 0xFF)) <= 2
        }
}
