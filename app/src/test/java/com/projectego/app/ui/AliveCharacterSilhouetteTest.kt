package com.projectego.app.ui

import android.content.Context
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.ArrayDeque

/**
 * The character has to be one piece, not a pile of confetti.
 *
 * Two rounds of feedback were the same complaint wearing different words: arms
 * flush against the body read as "no arms", and arms lifted a pixel off the body
 * read as "no arms" again, because a floating square is decoration, not a limb.
 * The rule that survives both is that the body is one connected shape and the
 * limbs are bumps on it.
 *
 * The rule used to be proved by reading `pathData` as rectangles, which only
 * worked while every sprite was pixel art on a 16-cell grid. The flat family is
 * curves, so the body is now rebuilt as a real [Path] from its own path data and
 * drawn on its own: connectivity has to be measured on the artwork as authored,
 * because a raster cannot see it. Two things hide in a composite that are not
 * there in the art - the sparkles this face lets float above its head, and the
 * book another pose holds in front of its torso, which cuts the rendered body
 * into three pieces while the authored body is perfectly whole.
 *
 * Under [GraphicsMode.Mode.NATIVE] Robolectric really rasterises, which the other
 * two rules here need. Without it the canvas is a stub that draws nothing and
 * throws nothing - an empty bitmap is a silent success - so every rasterised
 * test opens by asserting its own bitmap is not empty before measuring it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AliveCharacterSilhouetteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private val standingSprites = listOf(
        "ic_alive_character" to R.drawable.ic_alive_character,
        "ic_alive_face_happy" to R.drawable.ic_alive_face_happy,
        "ic_alive_face_sad" to R.drawable.ic_alive_face_sad,
        "ic_alive_face_sleepy" to R.drawable.ic_alive_face_sleepy,
        "ic_alive_face_angry" to R.drawable.ic_alive_face_angry,
        "ic_alive_face_angry_full" to R.drawable.ic_alive_face_angry_full,
        "ic_alive_face_confused" to R.drawable.ic_alive_face_confused,
        "ic_alive_face_thinking" to R.drawable.ic_alive_face_thinking,
        "ic_alive_face_worried" to R.drawable.ic_alive_face_worried,
        "ic_alive_face_tired" to R.drawable.ic_alive_face_tired,
        "alive_pose_alive" to R.drawable.alive_pose_alive,
        "alive_pose_unaware" to R.drawable.alive_pose_unaware,
        "ic_alive_pose_book" to R.drawable.ic_alive_pose_book,
        "ic_alive_pose_camera" to R.drawable.ic_alive_pose_camera,
        "ic_alive_pose_pencil" to R.drawable.ic_alive_pose_pencil,
        "ic_alive_pose_wave" to R.drawable.ic_alive_pose_wave,
        "alive_anim_laptop" to R.drawable.alive_anim_laptop
    )

    /**
     * The size a member is smallest at in the app: the streak strip and the
     * category chips. Below it the family stops being readable, so this is the
     * size the legibility rule is measured at.
     */
    private val legibleSize = 24

    /** A member that collapses to a dot is not a member. */
    private val opaqueFloor = legibleSize * legibleSize / 8

    @Test
    fun `the authored body of every pose is one connected shape`() {
        standingSprites.forEach { (name, id) ->
            val body = bodyPaths(id)
            assertTrue("$name has no body-coloured path at all", body.paths.isNotEmpty())

            val bmp = Bitmap.createBitmap(body.size, body.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(body.size.toFloat() / body.viewport, body.size.toFloat() / body.viewport)
            body.paths.forEach { canvas.drawPath(it, Paint().apply { style = Paint.Style.FILL }) }

            val pixels = opaque(bmp)
            assertTrue("$name's body drew nothing - is the canvas stubbed?", pixels.size >= body.size * body.size / 8)

            val seen = mutableSetOf(pixels.first())
            val queue = ArrayDeque(listOf(pixels.first()))
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                // Eight neighbours, not four: a limb that meets the torso at a
                // corner is still one shape, and pixel art joins its arms to the
                // torso on the diagonal. Four-way would call a whole body confetti.
                listOf(
                    x + 1 to y, x - 1 to y, x to y + 1, x to y - 1,
                    x + 1 to y + 1, x + 1 to y - 1, x - 1 to y + 1, x - 1 to y - 1
                ).forEach { step ->
                    if (step in pixels && seen.add(step)) queue.add(step)
                }
            }

            val adrift = pixels.size - seen.size
            assertTrue("$name's body is in ${components(pixels)} pieces: $adrift of ${pixels.size} pixels adrift", adrift == 0)
        }
    }

    @Test
    fun `every pose still reads at the size the strip draws it`() {
        standingSprites.forEach { (name, id) ->
            val pixels = opaque(raster(id, legibleSize))
            assertTrue("$name drew nothing at all - is the canvas stubbed?", pixels.size >= opaqueFloor)

            val width = pixels.maxOf { it.first } - pixels.minOf { it.first } + 1
            val height = pixels.maxOf { it.second } - pixels.minOf { it.second } + 1
            assertTrue(
                "$name collapses to ${width}x$height at $legibleSize px",
                width >= legibleSize / 2 && height >= legibleSize / 2
            )
        }
    }

    @Test
    fun `the eyes keep 4_5 against the body they sit on`() {
        // The DNA puts two capsule eyes on a flat body and nothing else, so the
        // body alone has to keep its eyes legible - there is no outline to fall
        // back on. Measured off the rendered drawable, not off the generator, so
        // a member whose eyes drift to the body's own tone fails here.
        standingSprites.forEach { (name, id) ->
            val counts = histogram(raster(id, 96))
            assertTrue("$name drew nothing at all - is the canvas stubbed?", counts.values.sum() >= 96 * 96 / 8)

            val body = counts.maxByOrNull { it.value }!!.key
            val eyes = counts.filterKeys { it != body }
                .filter { it.value >= 96 * 96 / 200 }
                .minByOrNull { luminance(it.key) }?.key

            assertTrue("$name shows no second colour solid enough to be eyes", eyes != null)
            val ratio = contrast(eyes!!, body)
            assertTrue("$name eyes on body = $ratio", ratio >= 4.5)
        }
    }

    // ---------- the authored body ----------

    private class Body(val paths: List<Path>, val viewport: Int, val size: Int)

    /**
     * The body-coloured paths of a sprite, rebuilt from its own path data.
     *
     * The body is the colour that owns the raster, so the composite decides which
     * XML colour is the body and the XML decides what that colour draws. Reading
     * it any other way either hardcodes `alive_pink_soft` - which the flat family
     * retires - or trusts a fill name the art may not use.
     */
    private fun bodyPaths(id: Int): Body {
        val bodyArgb = histogram(raster(id, authoredSize(id))).maxByOrNull { it.value }!!.key

        var viewport = 0
        val paths = mutableListOf<Path>()
        val parser = context.resources.getXml(id)
        try {
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                var fill: String? = null
                var data: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "viewportWidth" -> viewport = parser.getAttributeValue(i).toFloat().toInt()
                        "fillColor" -> fill = parser.getAttributeValue(i)
                        "pathData" -> data = parser.getAttributeValue(i)
                    }
                }
                if (data == null || !isColour(fill, bodyArgb)) continue
                paths += pathFrom(data)
            }
        } finally {
            parser.close()
        }
        assertTrue("sprite ${context.resources.getResourceName(id)} declared no viewportWidth", viewport > 0)
        return Body(paths, viewport, authoredSize(id))
    }

    /**
     * A compiled vector stores its fill as `@2131034166`, not as a colour name,
     * so the reference has to be resolved before it can be compared to a pixel.
     */
    private fun isColour(fill: String?, argb: Int): Boolean {
        val token = fill?.removePrefix("@")?.toIntOrNull() ?: return false
        return ContextCompat.getColor(context, token) and 0xFFFFFF == argb
    }

    /**
     * The subset of SVG path syntax these sprites use. Anything else throws, so a
     * future curve command fails loudly in the suite instead of being silently
     * skipped and leaving the body looking connected because half of it was
     * never drawn.
     */
    private fun pathFrom(data: String): Path {
        val tokens = Regex("[MmLlHhVvCcZz]|-?\\d*\\.?\\d+").findAll(data).map { it.value }.toList()
        val path = Path()
        var i = 0
        var cmd = ' '
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        fun next(): Float {
            val value = tokens[i].toFloatOrNull()
                ?: throw AssertionError("pathData wanted a number, found '${tokens[i]}'")
            i++
            return value
        }
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) {
                cmd = tokens[i][0]
                i++
            }
            when (cmd) {
                'M' -> { x = next(); y = next(); startX = x; startY = y; path.moveTo(x, y) }
                'm' -> { x += next(); y += next(); startX = x; startY = y; path.moveTo(x, y) }
                'L' -> { x = next(); y = next(); path.lineTo(x, y) }
                'l' -> { x += next(); y += next(); path.lineTo(x, y) }
                'H' -> { x = next(); path.lineTo(x, y) }
                'h' -> { x += next(); path.lineTo(x, y) }
                'V' -> { y = next(); path.lineTo(x, y) }
                'v' -> { y += next(); path.lineTo(x, y) }
                'C' -> {
                    val x1 = next(); val y1 = next(); val x2 = next(); val y2 = next()
                    x = next(); y = next()
                    path.cubicTo(x1, y1, x2, y2, x, y)
                }
                'c' -> {
                    val x1 = x + next(); val y1 = y + next(); val x2 = x + next(); val y2 = y + next()
                    x += next(); y += next()
                    path.cubicTo(x1, y1, x2, y2, x, y)
                }
                'Z', 'z' -> { path.close(); x = startX; y = startY }
                else -> throw AssertionError("pathData command '$cmd' is not supported by the silhouette lock")
            }
        }
        return path
    }

    private fun authoredSize(id: Int): Int {
        val intrinsic = ContextCompat.getDrawable(context, id)!!.intrinsicWidth
        return if (intrinsic > 0) intrinsic else 96
    }

    // ---------- rasters ----------

    private fun raster(id: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = ContextCompat.getDrawable(context, id)!!
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bmp))
        return bmp
    }

    /**
     * Solid pixels only. A vector's edge is anti-aliased, so a raster carries a
     * fringe of alpha-1 specks around every shape; treating that fringe as
     * geometry would seed the flood fill on a stray pixel and report the whole
     * body as floating. Half coverage is where softness ends and the body begins.
     */
    private fun opaque(bmp: Bitmap): Set<Pair<Int, Int>> {
        val pixels = linkedSetOf<Pair<Int, Int>>()
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            if (bmp.getPixel(x, y) ushr 24 >= 0x80) pixels += x to y
        }
        return pixels
    }

    private fun components(pixels: Set<Pair<Int, Int>>): Int {
        val remaining = pixels.toMutableSet()
        var count = 0
        while (remaining.isNotEmpty()) {
            count++
            val seed = remaining.first()
            remaining.remove(seed)
            val queue = ArrayDeque(listOf(seed))
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                listOf(
                    x + 1 to y, x - 1 to y, x to y + 1, x to y - 1,
                    x + 1 to y + 1, x + 1 to y - 1, x - 1 to y + 1, x - 1 to y - 1
                ).forEach { step ->
                    if (remaining.remove(step)) queue.add(step)
                }
            }
        }
        return count
    }

    private fun histogram(bmp: Bitmap): Map<Int, Int> {
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val pixel = bmp.getPixel(x, y)
            if (pixel ushr 24 >= 0x80) counts[pixel and 0xFFFFFF] = (counts[pixel and 0xFFFFFF] ?: 0) + 1
        }
        return counts
    }

    /** WCAG 2.1 contrast between two opaque colours, as AlivePaletteTest measures it. */
    private fun contrast(foreground: Int, background: Int): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return ((lighter + 0.05) / (darker + 0.05) * 100).toInt() / 100.0
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val value = (rgb ushr shift and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
