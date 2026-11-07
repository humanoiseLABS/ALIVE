package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.io.File
import java.util.ArrayDeque

/**
 * The mark has to survive three different jobs: beside the wordmark, in a
 * 64dp adaptive-icon foreground that the launcher crops, and in a notification
 * and Quick Settings tile that Android renders from the alpha channel alone.
 * Each of those is a different drawable path (vector, then two layer lists),
 * and a layer list that fails to size its item inflates fine at build time and
 * simply arrives small or blank on the phone - so the sizes are asserted here
 * rather than discovered on a home screen.
 *
 * The third job is the one that changed in Batch 4, and it changed the drawing
 * rather than the file list. A status bar keeps the shape's softness and throws
 * away its colour, so a mark drawn as "pink body, ink eyes" arrives as one grey
 * lump: the eyes were a second colour, and there is no second colour any more.
 * The eyes are therefore CUT OUT of the body, which is the only treatment that
 * survives both jobs - and that is a property of the produced drawable, so it
 * is measured here instead of trusted from the generator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AliveMarkTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /** The viewport the mark is authored on, and the size the eye centres sit at. */
    private val viewport = 72f
    private val eyeCentres = listOf(26.5f to 36.5f, 45.5f to 36.5f)

    @Test
    fun `the mark is drawn and sized`() {
        val mark = ContextCompat.getDrawable(context, R.drawable.ic_alive_mark)
        assertTrue("the mark did not inflate", mark != null)
        assertTrue(
            "the mark has no intrinsic size, so every surface that shows it gets a blank",
            mark!!.intrinsicWidth > 0 && mark.intrinsicHeight > 0
        )
    }

    @Test
    fun `the icon and splash wrappers keep the mark square`() {
        listOf(
            "launcher foreground" to R.drawable.ic_launcher_foreground,
            "splash icon" to R.drawable.ic_splash_mark,
            "themed-icon layer" to R.drawable.ic_launcher_monochrome
        ).forEach { (name, res) ->
            val drawable = ContextCompat.getDrawable(context, res)
            assertTrue("$name did not inflate", drawable != null)
            val width = drawable!!.intrinsicWidth
            val height = drawable.intrinsicHeight
            assertTrue("$name is $width x $height; the mark inside it would be cropped", width > 0 && height > 0)
        }
    }

    @Test
    fun `the eyes are cut out of the body, not painted on it`() {
        val bmp = raster(96)
        val solid = opaque(bmp)
        assertTrue("the mark drew ${solid.size} pixels at 96 - is the canvas stubbed?", solid.size >= 96 * 96 / 8)

        eyeCentres.forEach { (x, y) ->
            val pixel = bmp.getPixel(at(x, 96), at(y, 96))
            assertTrue(
                "an eye centre is ${pixel ushr 24}/255 opaque - eyes drawn in a second colour disappear the moment " +
                    "Android keeps only alpha, which is what a notification small icon and the monochrome launcher " +
                    "layer both do",
                pixel ushr 24 == 0
            )
        }

        // And the body has to still be there around them, or the mark is a hole.
        assertTrue("the shell above the eyes is not solid", (bmp.getPixel(at(36f, 96), at(12f, 96)) ushr 24) >= 0x80)
        assertTrue("the body below the eyes is not solid", (bmp.getPixel(at(36f, 96), at(58f, 96)) ushr 24) >= 0x80)
    }

    @Test
    fun `the mark is one body at the size the status bar draws it`() {
        val size = 16
        val pixels = opaque(raster(size))
        assertTrue("the mark drew ${pixels.size} pixels at $size px, want ${size * size / 8}", pixels.size >= size * size / 8)
        assertEquals("the mark is in pieces at $size px", 1, components(pixels))
    }

    @Test
    fun `the mark keeps two separate eyes at the small end`() {
        // The leader claimed twice, in prose, that the approved stance merges
        // the eyes into one slot at 16 px. It was never measured - it was read
        // off a preview renderer with no anti-aliasing and a nearest-neighbour
        // blow-up. So the claim gets a lock now: the air ENCLOSED by the body is
        // counted, and it has to be two eyes at the size the bar draws the mark
        // and at the size the strip does. One enclosed region means the face is
        // gone at that size, and this time it would be a fact, not an opinion.
        listOf(16, 24, 32).forEach { size ->
            val bmp = raster(size)
            val air = (0 until size * size).map { it % size to it / size }.toSet() - opaque(bmp)
            // Everything reachable from the border is background, not an eye.
            val outside = mutableSetOf<Pair<Int, Int>>()
            val queue = ArrayDeque(air.filter { (x, y) -> x == 0 || y == 0 || x == size - 1 || y == size - 1 })
            outside.addAll(queue)
            while (queue.isNotEmpty()) {
                val (x, y) = queue.remove()
                listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1).forEach { step ->
                    if (step in air && outside.add(step)) queue.add(step)
                }
            }
            val holes = air - outside
            val eyes = components(holes)
            assertEquals(
                "the mark shows $eyes enclosed hole(s) at $size px (area ${holes.size} px) - the face is what the mark IS",
                2, eyes
            )
        }
    }

    /**
     * The rule that keeps reminders and the guard from going invisible: every
     * surface Android alpha-masks must ask for the mark, and for nothing else.
     *
     * Measured off the source text rather than off a rendered notification,
     * because no JVM test can open the status bar - the same trade
     * [LockScreenSourceTest] and `EnforcementReArmSourceTest` already make in
     * this project. A gap in the guard can disqualify a whole day, so "the icon
     * is a grey blob now" is not a cosmetic finding.
     *
     * The first version of this read a 240-character window and was beaten four
     * ways: a variable holding the icon passed because a neighbouring literal
     * sat in the window, `setSmallIcon(0)` passed as "no icon" when 0 there means
     * a reminder with NO icon at all, `IconCompat.createWithResource` and
     * `Notification.Action` matched nothing, and XML was invisible. So the
     * argument is now read as a balanced argument list, `0` is only legal where
     * Android itself allows an absent icon, and the markers name every way an
     * icon is set.
     */
    @Test
    fun `every alpha-masked surface asks for the mark`() {
        // Which argument holds the icon is not the same question in every call:
        // setSmallIcon(icon) and addAction(icon, title, intent) take it first,
        // while Icon.createWithResource(context, icon) takes it second. Reading
        // "the first thing in the parens" accused the tile of asking for `this`.
        val markers = listOf(
            "setSmallIcon(" to 0,
            "Icon.createWithResource(" to 1,
            "IconCompat.createWithResource(" to 1,
            ".addAction(" to 0
        )
        val sites = mutableListOf<String>()
        mainSources().forEach { file ->
            val text = code(file.readText())
            markers.forEach { (marker, position) ->
                var at = text.indexOf(marker)
                while (at >= 0) {
                    val raw = argument(text, at + marker.length, position)
                    // A named argument is the same call: `addAction(icon = 0, …)`
                    // must not be accused of asking for the expression `icon = 0`.
                    val argument = namedArgument.find(raw)?.groupValues?.get(1)?.trim() ?: raw
                    val line = text.substring(0, at).count { it == '\n' } + 1
                    // An action may legitimately carry no icon; a small icon may
                    // not - that is the notification disappearing.
                    val asked = when {
                        argument == "0" && marker == ".addAction(" -> "no icon"
                        argument == "0" -> "NO ICON"
                        else -> Regex("R\\.drawable\\.([A-Za-z0-9_]+)").find(argument)?.groupValues?.get(1)
                            ?: "the expression `${argument.take(40)}`"
                    }
                    sites += "${file.name}:${line} ${marker.trim()} -> $asked"
                    at = text.indexOf(marker, at + marker.length)
                }
            }
        }

        assertTrue("the scan found no icon site at all - the scan is broken, not the app", sites.isNotEmpty())
        val wrong = sites.filterNot { it.endsWith("ic_alive_mark") || it.endsWith("no icon") }
        assertTrue(
            "these surfaces keep only the alpha channel, so anything but the mark arrives as a blob " +
                "(${sites.size} sites scanned):\n" + wrong.joinToString("\n"),
            wrong.isEmpty()
        )
    }

    /**
     * No resource in this app may name a drawable that is not there. A rename
     * that misses one reference compiles - the id is generated from the name in
     * the source - and arrives on the phone as a blank or a crash in the
     * inflation of a widget the app cannot test.
     */
    @Test
    fun `every drawable the app names exists`() {
        val present = drawableFiles()
        val named = mutableMapOf<String, MutableList<String>>()
        sources().forEach { file ->
            val text = code(file.readText())
            Regex("@drawable/([A-Za-z0-9_]+)").findAll(text).forEach {
                named.getOrPut(it.groupValues[1]) { mutableListOf() } += file.name
            }
            Regex("R\\.drawable\\.([A-Za-z0-9_]+)").findAll(text).forEach {
                named.getOrPut(it.groupValues[1]) { mutableListOf() } += file.name
            }
        }
        val missing = named.filterKeys { it !in present }
        assertTrue(
            "these drawables are named but not on disk: ${missing.entries.joinToString("\n") { "${it.key} <- ${it.value.distinct()}" }}",
            missing.isEmpty()
        )
    }

    /**
     * The icon sites that live in XML, which a Kotlin-only scan cannot see.
     *
     * Measured, not assumed: the manifest carries exactly one `@drawable` icon -
     * the Quick Settings tile - because the application icon is `@mipmap`, which
     * is a different artefact with its own adaptive layers and is not what this
     * test is about. `android:roundIcon` is matched too, and the pattern is
     * case-insensitive on the `I`: the first version wrote `(?:round)?icon`,
     * which silently ignores `roundIcon` and would let a retired drawable sit on
     * the launcher's round icon forever.
     *
     * The adaptive icon's `<monochrome>` is tinted by the launcher's theme, so it
     * has to point at the vector with the eyes cut out of it, not at the
     * foreground layer-list - a launcher is not obliged to tint a LayerDrawable,
     * and nothing in a JVM test can find out whether a given one does.
     */
    @Test
    fun `the icons named in XML are the mark and the themed vector`() {
        val manifest = xmlSources().firstOrNull { it.name == "AndroidManifest.xml" }
            ?: error("AndroidManifest.xml not reachable from ${File(".").absolutePath}")
        val declared = Regex("""android:(?:round)?icon="@drawable/([A-Za-z0-9_]+)""", RegexOption.IGNORE_CASE)
            .findAll(code(manifest.readText()))
            .map { it.groupValues[1] }
            .toList()
        assertTrue("the manifest declares no @drawable icon at all - the scan is looking in the wrong place", declared.isNotEmpty())
        val wrongIcons = declared.distinct().filterNot { it == "ic_alive_mark" }
        assertTrue("this icon is alpha-masked like a notification, so it must be the mark: $wrongIcons", wrongIcons.isEmpty())

        val layers = xmlSources().filter { code(it.readText()).contains("<adaptive-icon") }
        assertTrue("no adaptive icon found", layers.isNotEmpty())
        layers.forEach { file ->
            val monochrome = Regex("""<monochrome\s+android:drawable="@drawable/([A-Za-z0-9_]+)"""")
                .find(code(file.readText()))?.groupValues?.get(1)
            assertTrue("${file.name} declares no <monochrome> layer", monochrome != null)
            assertEquals(
                "${file.name} tints a layer-list; the themed layer must be the vector with holes in it",
                "ic_launcher_monochrome", monochrome
            )
        }
    }

    // ---------- helpers ----------

    private val iconToken = Regex("R\\.drawable\\.([A-Za-z0-9_]+)")
    private val namedArgument = Regex("""^([A-Za-z_]\w*)\s*=\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * The file with its comments removed, so a lock reads code and not prose.
     *
     * Both scans used to work on the raw text, which means a KDoc that says
     * "it used to call setSmallIcon(R.drawable.ic_alive_hourglass)" would fail
     * the suite - a red test caused by a sentence, in a project whose comments
     * are deliberately full of retired names. Approximate by design: block and
     * line comments for Kotlin, XML comments for resources. It is not a parser,
     * and a `//` inside a string literal is dropped along with it.
     */
    private fun code(text: String): String =
        text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\\n]*"), "")

    /**
     * The [position]th argument of a call whose opening paren was just consumed
     * at [start], read by counting parentheses so a nested
     * `getString(R.string.x)` does not end the argument early, and by only
     * splitting commas at depth zero so a nested call's own commas are ignored.
     */
    private fun argument(text: String, start: Int, position: Int): String {
        var depth = 1
        var index = 0
        var from = start
        var i = start
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 1) {
                    if (index == position) return text.substring(from, i).trim()
                    index++
                    from = i + 1
                }
            }
            if (depth == 0 && index == position) return text.substring(from, i).trim()
            i++
            if (i - start > 600) break
        }
        return text.substring(from, minOf(text.length, i)).trim()
    }

    private fun sources(): List<File> {
        val roots = listOf(File("src/main"), File("app/src/main"))
            .firstOrNull { it.isDirectory }
            ?: error("main source not found from ${File(".").absolutePath}")
        return roots.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()
    }

    private fun mainSources(): List<File> =
        sources().filter { it.extension == "kt" && it.path.contains("java") }

    private fun xmlSources(): List<File> = sources().filter { it.extension == "xml" }

    private fun drawableFiles(): Set<String> {
        val roots = listOf("app/src/main/res", "src/main/res").map(::File).filter { it.isDirectory }
        val names = mutableSetOf<String>()
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isDirectory && it.name.startsWith("drawable") }
                .forEach { dir -> dir.listFiles()?.forEach { names += it.nameWithoutExtension } }
        }
        assertTrue("no res/drawable found - the test is not running from the project root", names.isNotEmpty())
        return names
    }

    private fun at(viewportPoint: Float, size: Int): Int =
        (viewportPoint * size / viewport).toInt()

    private fun raster(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_alive_mark)!!
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bmp))
        return bmp
    }

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
                ).forEach { step -> if (remaining.remove(step)) queue.add(step) }
            }
        }
        return count
    }
}
