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
 * The flat family, as five locks on real pixels.
 *
 * The design constitution this family was drawn against states three invariants:
 * one connected body per character, every silhouette distinct, legible at 24 px.
 * The pixel era proved them by reading `pathData` as rectangles on a 16-cell
 * grid; the flat family is curves, so they are proved by rasterising, which
 * [GraphicsMode.Mode.NATIVE] lets Robolectric actually do. Two more locks belong
 * to the family rather than to the invariants - nothing may float off a member,
 * and the eyes must survive on the body they sit on - and both are the rules the
 * two rounds of "no arms" feedback left behind.
 *
 * Three measurements are not naive pixel counts, and each has a reason:
 *
 * - **The silhouette excludes the ground.** `Belum terbaca` is a dashed outline
 *   filled with `flat_ground` so that one connected body survives under it; in
 *   the app that plate is the page, so what reads is the ring and the one eye.
 *   Counting the plate would make it a square and collide with `Beres-beres`,
 *   which is the opposite of what MISSING≠ZERO means.
 * - **The eye histogram ignores the anti-aliased fringe.** A vector's edge is a
 *   ramp of blended colours, and on the ink member that ramp runs from ink to
 *   paper - so a naive histogram finds "a solid colour darker than the eyes"
 *   that is nothing but softness. Only pixels whose four neighbours are also
 *   solid count as artwork.
 * - **Connectivity is measured on the authored body, drawn alone.** A composite
 *   hides two things that are not in the art: a held object that cuts the
 *   rendered body into pieces, and an interior fill that glues dashes together
 *   that the drawing never joined.
 *
 * Every rasterised test opens by asserting its own bitmap is not empty: under a
 * stub canvas Android draws nothing and throws nothing, and an empty family
 * would pass "these two shapes are not identical" by never rendering at all.
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

    /**
     * The seventeen members `family/generate.mjs` emits. The names are the
     * contract `FlatDesignLocksTest` locks; these are what the names draw.
     */
    private val family = listOf(
        "member study" to R.drawable.alive_member_study,
        "member reading" to R.drawable.alive_member_reading,
        "member exercise" to R.drawable.alive_member_exercise,
        "member walking" to R.drawable.alive_member_walking,
        "member cleaning" to R.drawable.alive_member_cleaning,
        "member creative" to R.drawable.alive_member_creative,
        "member coding" to R.drawable.alive_member_coding,
        "member writing" to R.drawable.alive_member_writing,
        "member mindfulness" to R.drawable.alive_member_mindfulness,
        "member social" to R.drawable.alive_member_social,
        "member work" to R.drawable.alive_member_work,
        "state normal" to R.drawable.alive_state_normal,
        "state near" to R.drawable.alive_state_near,
        "state spent" to R.drawable.alive_state_spent,
        "state recovery" to R.drawable.alive_state_recovery,
        "state unknown" to R.drawable.alive_state_unknown,
        "state rest" to R.drawable.alive_state_rest
    )

    /**
     * The size a member is smallest at in the app: the streak strip and the
     * category chips. Below it the family stops being readable, so this is the
     * size the legibility and distinctness rules are measured at.
     */
    private val legibleSize = 24

    /**
     * The pixel art that is still on screen. The flat family replaces it in
     * Batches 4 to 7, and until it is gone it is still the character the user
     * meets in the widget, the history rows and the empty states - so the three
     * shape-agnostic locks below guard it as well. Dropping it here would leave
     * seventeen shipped sprites with no test at all for three batches, which is
     * how a restyle silently breaks art nobody meant to touch.
     */
    private val legacySprites = listOf(
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

    /** Everything that must be one connected, readable body with legible eyes. */
    private val guarded = family + legacySprites

    /** A member that collapses to a dot is not a member. */
    private val opaqueFloor = legibleSize * legibleSize / 8

    /**
     * Two members may share a hue, an eye treatment and a family. They may not
     * share a silhouette: above this line the pair is the same shape wearing a
     * different colour, which is what the pixel era did seventeen times.
     *
     * Measured on this machine against the shipped family, the closest pair is
     * `Olahraga` (circle) / `Koding` (hexagon) at 0,936, then circle / pentagon
     * at 0,900 and hexagon / pentagon at 0,892. The line therefore sits 0,014
     * above the worst real pair: it is a guard against a member being emitted
     * twice or collapsing into a neighbour, not a proof that any two of them
     * read apart - the round convex cluster does not, at 24 px, whatever the
     * corners are. That residual is the spec's own, stated in Section 1.5, and
     * the family separates on hue and eye treatment where the silhouette stops.
     */
    private val distinctBelow = 0.95

    /** Large enough that a capsule of eyes is still several pixels across. */
    private val eyeSample = 144

    private val eyeFloor = eyeSample * eyeSample / 200

    @Test
    fun `the authored body of every member is one connected shape`() {
        guarded.forEach { (name, id) ->
            // `Belum terbaca` has no body to connect: its outline is eight
            // dashes by design, and the only fill wearing its own colour is the
            // ground plate underneath them. Flood-filling that plate would prove
            // nothing about the ring, so the member is guarded instead by
            // `the hollow member is an outline with nothing inside`, which is
            // the property MISSING≠ZERO actually claims.
            if (name == "state unknown") return@forEach
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
                // corner is still one shape. Four-way would call a whole body
                // confetti, and the flat family has no limbs to catch it.
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
    fun `no part floats off a member`() {
        // The flat DNA is one body with bumps on it. An eye, a gloss slash or a
        // dash that does not touch the body is decoration, not a feature - and
        // at 24 px decoration reads as an artefact of a broken sprite.
        //
        // Measured on the silhouette, not on the composite: `Belum terbaca`'s
        // ground plate would glue everything under it into one piece and make
        // this lock pass by painting the page.
        family.forEach { (name, id) ->
            if (name == "state unknown") return@forEach
            val size = authoredSize(id)
            val pixels = silhouette(id, size)
            assertTrue("$name drew nothing at all - is the canvas stubbed?", pixels.size >= size * size / 8)
            val pieces = components(pixels)
            assertTrue("$name is drawn in $pieces pieces; the flat family allows one", pieces == 1)
        }
    }

    @Test
    fun `the hollow member is an outline with nothing inside`() {
        // MISSING≠ZERO, in drawable form. `Belum terbaca` is not a low number
        // and not an empty square: it is a ring with air in it, holding the one
        // eye. A dashed outline and a solid body of the same footprint are
        // indistinguishable to every other lock in this file - both are one
        // connected, legible, distinct shape - so the property that separates
        // them needs its own proof, and so does the eye's place in it: an eye
        // dropped outside the ring would pass all five other locks.
        val id = R.drawable.alive_state_unknown
        val bmp = raster(id, legibleSize)
        val ground = ContextCompat.getColor(context, R.color.flat_ground) and 0xFFFFFF
        val ink = ContextCompat.getColor(context, R.color.flat_ink) and 0xFFFFFF

        val visible = mutableSetOf<Pair<Int, Int>>()
        val eyes = mutableSetOf<Pair<Int, Int>>()
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val argb = bmp.getPixel(x, y)
            if (argb ushr 24 < 0x80) continue
            val colour = argb and 0xFFFFFF
            if (colour == ground) continue
            visible += x to y
            if (colour == ink) eyes += x to y
        }

        assertTrue("the hollow member drew nothing at all", visible.isNotEmpty())
        assertTrue("the hollow member has no eye: $eyes of $visible pixels are ink", eyes.isNotEmpty())
        val ring = visible.size - eyes.size
        assertTrue(
            "the hollow member is ${eyes.size} ink pixels and $ring ring pixels - the outline is the member, the eye is not",
            ring >= opaqueFloor / 2
        )

        fun span(pixels: Set<Pair<Int, Int>>) =
            intArrayOf(pixels.minOf { it.first }, pixels.maxOf { it.first }, pixels.minOf { it.second }, pixels.maxOf { it.second })
        val (left, right, top, bottom) = span(visible)
        val (eyeLeft, eyeRight, eyeTop, eyeBottom) = span(eyes)
        assertTrue(
            "the eye spans ${eyeLeft}..$eyeRight x ${eyeTop}..$eyeBottom inside an outline spanning " +
                "$left..$right x $top..$bottom - it has to sit in the hole, not on the ring or outside it",
            eyeLeft > left && eyeRight < right && eyeTop > top && eyeBottom < bottom
        )

        val width = right - left + 1
        val height = bottom - top + 1
        val ratio = visible.size.toDouble() / (width * height)
        assertTrue(
            "Belum terbaca fills ${(ratio * 100).toInt()}% of its own ${width}x$height box at $legibleSize px; " +
                "an outline with air inside fills under half, a body fills more",
            ratio < 0.5
        )
    }

    @Test
    fun `every member still reads at the size the strip draws it`() {
        guarded.forEach { (name, id) ->
            val pixels = silhouette(id, legibleSize)
            assertTrue("$name draws ${pixels.size} of $opaqueFloor opaque pixels at $legibleSize px", pixels.size >= opaqueFloor)

            val width = pixels.maxOf { it.first } - pixels.minOf { it.first } + 1
            val height = pixels.maxOf { it.second } - pixels.minOf { it.second } + 1
            assertTrue(
                "$name collapses to ${width}x$height at $legibleSize px",
                width >= legibleSize / 2 && height >= legibleSize / 2
            )
        }
    }

    @Test
    fun `no two members share a silhouette at the strip size`() {
        val masks = family.map { (name, id) -> name to silhouette(id, legibleSize) }
        val collisions = mutableListOf<String>()
        val closest = mutableListOf<Pair<String, Double>>()
        for (a in masks.indices) for (b in a + 1 until masks.size) {
            val (first, left) = masks[a]
            val (second, right) = masks[b]
            val intersection = left.count { it in right }
            val union = left.size + right.size - intersection
            val iou = intersection.toDouble() / union
            closest += "$first / $second" to iou
            if (iou >= distinctBelow) collisions += "$first / $second = ${(iou * 1000).toInt() / 1000.0}"
        }
        val top = closest.sortedByDescending { it.second }.take(5)
            .joinToString("\n") { "  ${(it.second * 1000).toInt() / 1000.0}  ${it.first}" }
        assertTrue(
            "silhouettes collide at $legibleSize px, closest pairs:\n$top\n$collisions",
            collisions.isEmpty()
        )
    }

    @Test
    fun `the eyes keep 4_5 against the body they sit on`() {
        // The DNA puts two capsule eyes on a flat body and nothing else, so the
        // body alone has to keep its eyes legible - there is no outline to fall
        // back on. Measured off the rendered drawable, not off the generator, so
        // a member whose eyes drift to the body's own tone fails here.
        guarded.forEach { (name, id) ->
            val counts = solidHistogram(raster(id, eyeSample))
            assertTrue("$name drew nothing at all - is the canvas stubbed?", counts.values.sum() >= eyeSample * eyeSample / 8)

            val body = counts.maxByOrNull { it.value }!!.key
            val eyes = counts.filterKeys { it != body }
                .filter { it.value >= eyeFloor }
                .minByOrNull { luminance(it.key) }?.key

            assertTrue("$name shows no second colour solid enough to be eyes", eyes != null)
            val ratio = contrast(eyes!!, body)
            assertTrue("$name eyes on body = $ratio", ratio >= 4.5)
        }
    }

    // ---------- the authored body ----------

    private class Body(val paths: List<Path>, val viewport: Int, val size: Int)

    /**
     * The body-coloured paths of a member, rebuilt from its own path data.
     *
     * The body is the colour that owns the raster, so the composite decides which
     * XML colour is the body and the XML decides what that colour draws. Reading
     * it any other way either hardcodes a hue the family does not use, or trusts
     * a fill name the art may not.
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
        assertTrue("member ${context.resources.getResourceName(id)} declared no viewportWidth", viewport > 0)
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
     * The subset of SVG path syntax these members use - M, L, H, V, C, Z, which
     * is exactly what `family/generate.mjs` is allowed to emit. Anything else
     * throws, so a future command fails loudly in the suite instead of being
     * silently skipped and leaving a body looking connected because half of it
     * was never drawn.
     */
    private fun pathFrom(data: String): Path {
        // Letters are tokenised as themselves, not skipped: a regex that only
        // recognises the commands it understands hands an A, S, Q or T to the
        // previous command as numbers and draws a different shape in silence.
        val tokens = Regex("[A-Za-z]|-?\\d*\\.?\\d+").findAll(data).map { it.value }.toList()
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
                if (cmd !in "MmLlHhVvCcZz") {
                    throw AssertionError("pathData command '$cmd' is not supported by the silhouette lock")
                }
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
        val drawable = ContextCompat.getDrawable(context, id)!!
        val width = drawable.intrinsicWidth
        // Every measurement here draws a square bitmap from one size. A member
        // whose viewport is not square would be squashed by that and read as a
        // different silhouette, so the shape of the file is asserted too.
        assertTrue(
            "${context.resources.getResourceName(id)} is ${width}x${drawable.intrinsicHeight}; a member is authored square",
            width > 0 && width == drawable.intrinsicHeight
        )
        return width
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
     * fringe of soft specks around every shape; treating that fringe as
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

    /**
     * What the member actually shows: its solid pixels, minus the ones wearing
     * the ground's own colour. `flat_ground` is the page, so a plate of it is
     * not a body - which is the whole point of the hollow member, and the only
     * reason the dashed outline is not measured as a square.
     */
    private fun silhouette(id: Int, size: Int): Set<Pair<Int, Int>> {
        val ground = ContextCompat.getColor(context, R.color.flat_ground) and 0xFFFFFF
        val pixels = linkedSetOf<Pair<Int, Int>>()
        val bmp = raster(id, size)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val argb = bmp.getPixel(x, y)
            if (argb ushr 24 >= 0x80 && argb and 0xFFFFFF != ground) pixels += x to y
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

    /**
     * A histogram of artwork only: a pixel counts if it and its four neighbours
     * are solid, which drops the one-pixel anti-aliased ramp. Without this the
     * ink member's own edge - a blend of ink and paper - outvotes its eyes.
     */
    private fun solidHistogram(bmp: Bitmap): Map<Int, Int> {
        val solid = opaque(bmp)
        val counts = mutableMapOf<Int, Int>()
        for ((x, y) in solid) {
            if (listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).any { it !in solid }) continue
            val colour = bmp.getPixel(x, y) and 0xFFFFFF
            counts[colour] = (counts[colour] ?: 0) + 1
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
