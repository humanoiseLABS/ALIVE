package com.projectego.app.ui

import android.content.Context
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
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
import java.io.File
import java.util.ArrayDeque

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuardianSpriteTest {
    private lateinit var context: Context
    private val poses = linkedMapOf(
        "idle" to R.drawable.ic_guardian_idle,
        "alert" to R.drawable.ic_guardian_alert,
        "recovery" to R.drawable.ic_guardian_recovery,
        "restored" to R.drawable.ic_guardian_restored
    )
    private data class Part(val name: String, val colour: Int, val path: Path)
    private data class Art(val viewport: Int, val parts: List<Part>)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `every pose is one flat body and two capsule eyes`() {
        poses.forEach { (name, id) ->
            val art = art(id)
            assertEquals("$name has extra parts or no capsule eyes", listOf("body", "eye_left", "eye_right"), art.parts.map { it.name })
            assertEquals("$name body must use crimson", colour(R.color.alive_red_short_mid), art.parts[0].colour)
            art.parts.drop(1).forEach { eye ->
                assertEquals("$name ${eye.name} must use ink", colour(R.color.flat_ink), eye.colour)
            }
        }
    }

    @Test
    fun `the authored body is connected without its eyes`() {
        poses.forEach { (name, id) ->
            val art = art(id)
            val body = pixels(partRaster(art, "body", art.viewport))
            assertTrue("$name body draws nothing", body.size >= art.viewport * art.viewport / 8)
            assertEquals("$name has a detached body part", 1, components(body).size)
        }
    }

    @Test
    fun `every eye is contained in the body and nothing floats`() {
        poses.forEach { (name, id) ->
            val art = art(id)
            val body = pixels(partRaster(art, "body", 144))
            assertTrue("$name body draws nothing", body.size >= 144 * 144 / 8)
            listOf("eye_left", "eye_right").forEach { eye ->
                val eyePixels = pixels(partRaster(art, eye, 144))
                assertTrue("$name $eye draws nothing", eyePixels.size >= 24)
                assertTrue("$name $eye leaves the body", eyePixels.all { (x, y) ->
                    listOf(x to y, x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).all { it in body }
                })
            }
            val drawn = pixels(raster(id, 144))
            assertTrue("$name composite draws nothing", drawn.size >= 144 * 144 / 8)
            assertEquals("$name has a floating accent", 1, components(drawn).size)
            assertTrue("$name paints outside its body", (drawn - body).isEmpty())
        }
    }

    @Test
    fun `the figure and both eyes survive at 24 pixels`() {
        val output = File("build/reports/guardian-native").apply { mkdirs() }
        poses.forEach { (name, id) ->
            val art = art(id)
            val bitmap = raster(id, 24)
            val drawn = pixels(bitmap)
            assertTrue("$name draws only ${drawn.size} pixels", drawn.size >= 72)
            assertTrue("$name is too narrow", drawn.maxOf { it.first } - drawn.minOf { it.first } + 1 >= 12)
            assertTrue("$name is too short", drawn.maxOf { it.second } - drawn.minOf { it.second } + 1 >= 12)
            assertTrue("$name clips at the viewport edge", drawn.all { (x, y) -> x in 1..22 && y in 1..22 })
            val eyes = pixels(bitmap) { it == art.parts.first { p -> p.name == "eye_left" }.colour }
            val clusters = components(eyes)
            assertEquals("$name needs two separate visible eyes at 24px", 2, clusters.size)
            clusters.forEach { eye ->
                assertTrue("$name eye has only ${eye.size} solid pixels", eye.size >= 4)
                assertTrue("$name eye is narrower than 2px", eye.maxOf { it.first } - eye.minOf { it.first } + 1 >= 2)
                assertTrue("$name eye is shorter than 3px", eye.maxOf { it.second } - eye.minOf { it.second } + 1 >= 3)
            }
            listOf(24, 96).forEach { size ->
                File(output, "$name-$size.png").outputStream().use {
                    assertTrue("$name PNG export failed", raster(id, size).compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
    }

    @Test
    fun `every pose has a distinct silhouette at 24 pixels`() {
        val silhouettes = poses.mapValues { (name, id) ->
            val mask = pixels(raster(id, 24))
            assertTrue("$name draws nothing", mask.size >= 72)
            // Translation is placement, not a different pose; keep width and height unchanged.
            val left = mask.minOf { it.first }
            val top = mask.minOf { it.second }
            mask.map { (x, y) -> x - left to y - top }.toSet()
        }.toList()
        for (i in silhouettes.indices) for (j in i + 1 until silhouettes.size) {
            val (a, first) = silhouettes[i]
            val (b, second) = silhouettes[j]
            val iou = (first intersect second).size.toDouble() / (first union second).size
            println("Guardian silhouette $a/$b IoU=$iou")
            assertTrue("$a and $b collide at 24px: IoU=$iou", iou < 0.95)
        }
    }

    @Test
    fun `rendered eyes keep 4_5 contrast on the rendered body`() {
        poses.forEach { (name, id) ->
            val art = art(id)
            val bitmap = raster(id, 96)
            val body = art.parts.first { it.name == "body" }.colour
            assertTrue("$name has no visible body", pixels(bitmap) { it == body }.size >= 96 * 96 / 8)
            art.parts.filter { it.name.startsWith("eye_") }.forEach { eye ->
                assertTrue("$name ${eye.name} is not visible", pixels(bitmap) { it == eye.colour }.size >= 32)
                val ratio = contrast(eye.colour, body)
                println("Guardian $name eyes/body=$ratio")
                assertTrue("$name eyes on body = $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `the rendered body reads on light cards and the actual dark plate`() {
        val ui = AliveComponents(context)
        val plate = Guardian.plate(ui).background as android.graphics.drawable.GradientDrawable
        val grounds = listOf(colour(R.color.flat_ground), colour(R.color.flat_tile), ui.palette.softForest, plate.color!!.defaultColor)
        poses.forEach { (name, id) ->
            val body = art(id).parts.first { it.name == "body" }.colour
            assertTrue("$name body draws nothing", pixels(raster(id, 96)) { it == body }.size >= 96 * 96 / 8)
            grounds.forEach { ground ->
                val ratio = contrast(body, ground)
                println("Guardian $name body/ground=${ground.toUInt().toString(16)} ratio=$ratio")
                assertTrue("$name body does not separate from its ground: $ratio", ratio >= 3.0)
            }
        }
    }

    private fun art(id: Int): Art {
        var viewport = 0
        val parts = mutableListOf<Part>()
        val ns = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(id).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                assertTrue("unexpected ${parser.name} in flat Guardian", parser.name in listOf("vector", "path"))
                if (parser.name == "vector") {
                    viewport = parser.getAttributeValue(ns, "viewportWidth").toFloat().toInt()
                    assertTrue("missing viewport", viewport > 0)
                    assertEquals("viewport must be square", viewport.toFloat(), parser.getAttributeValue(ns, "viewportHeight").toFloat())
                    assertEquals("Guardian must be opaque", 1f, parser.getAttributeFloatValue(ns, "alpha", 1f))
                    assertTrue("Guardian must not be tinted", parser.getAttributeValue(ns, "tint") == null)
                } else {
                    assertTrue("flat Guardian cannot have a stroke", parser.getAttributeValue(ns, "strokeColor") == null)
                    assertEquals("flat Guardian must be opaque", 1f, parser.getAttributeFloatValue(ns, "fillAlpha", 1f))
                    assertTrue("flat Guardian cannot trim paths", (0 until parser.attributeCount).none { parser.getAttributeName(it).startsWith("trimPath") })
                    val fill = parser.getAttributeResourceValue(ns, "fillColor", 0)
                    assertTrue("Guardian must use a colour resource", fill != 0)
                    val data = parser.getAttributeValue(ns, "pathData")
                    assertTrue("Guardian must use curves, not pixel runs", data.contains('C'))
                    val path = PathParser.createPathFromPathData(data)!!
                    path.fillType = if (parser.getAttributeIntValue(ns, "fillType", 0) == 1) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    parts += Part(parser.getAttributeValue(ns, "name") ?: "", colour(fill), path)
                }
            }
        }
        val drawable = ContextCompat.getDrawable(context, id)!!
        assertTrue("drawable must be square", drawable.intrinsicWidth > 0 && drawable.intrinsicWidth == drawable.intrinsicHeight)
        return Art(viewport, parts)
    }

    private fun partRaster(art: Art, name: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(size.toFloat() / art.viewport, size.toFloat() / art.viewport)
        val part = art.parts.single { it.name == name }
        canvas.drawPath(part.path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = part.colour })
        return bitmap
    }

    private fun raster(id: Int, size: Int): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        ContextCompat.getDrawable(context, id)!!.apply {
            setBounds(0, 0, size, size)
            draw(Canvas(it))
        }
    }

    private fun pixels(bitmap: Bitmap, accepts: (Int) -> Boolean = { true }): Set<Pair<Int, Int>> = buildSet {
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (pixel ushr 24 >= 128 && accepts(pixel)) add(x to y)
        }
    }

    private fun components(pixels: Set<Pair<Int, Int>>): List<Set<Pair<Int, Int>>> {
        val remaining = pixels.toMutableSet()
        val groups = mutableListOf<Set<Pair<Int, Int>>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.first()
            remaining.remove(seed)
            val group = mutableSetOf(seed)
            val queue = ArrayDeque(listOf(seed))
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                for (dx in -1..1) for (dy in -1..1) {
                    val point = x + dx to y + dy
                    if (remaining.remove(point)) {
                        group += point
                        queue.add(point)
                    }
                }
            }
            groups += group
        }
        return groups
    }

    private fun colour(id: Int): Int = ContextCompat.getColor(context, id)

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val value = (rgb ushr shift and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
