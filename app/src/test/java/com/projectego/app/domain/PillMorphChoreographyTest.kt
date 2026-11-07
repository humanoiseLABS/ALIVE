package com.projectego.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pill's morph, which is a choreography before it is an animation: the
 * fractions decide every moving part, so the tests pin the order things appear
 * and disappear in, and that nothing can ever land outside its legal range.
 *
 * Two rules carry the feel of the whole feature. On the way out the shell
 * leads and the content follows - the eye sees the island open before anything
 * is asked to be read. On the way back the content leaves first, so the shell
 * that finishes shrinking is pure ink, never a frame of an empty black box.
 *
 * The third rule is the one a tap can break: a morph can be interrupted at any
 * fraction, and the next one starts from the frame that is on screen. These
 * tests are the seam - the moment a reversal used to reset the content.
 */
class PillMorphChoreographyTest {

    /** The whole range, finely enough to catch a ramp that dips mid-way. */
    private val fractions = (0..100).map { it / 100f }

    @Test
    fun `expansion starts with everything hidden and ends at rest`() {
        val start = PillMorphChoreography.expand(0f)
        assertEquals(0f, start.radius, 0f)
        assertEquals(0f, start.panelAlpha, 0f)
        assertEquals(0f, start.rowsAlpha, 0f)
        assertEquals(0f, start.actionAlpha, 0f)

        assertEquals(PillMorphChoreography.rest(true), PillMorphChoreography.expand(1f))
    }

    @Test
    fun `collapse ends with everything gone and the radius back to the pill`() {
        val end = PillMorphChoreography.collapse(1f)
        assertEquals(0f, end.radius, 0f)
        assertEquals(0f, end.panelAlpha, 0f)
        assertEquals(0f, end.rowsAlpha, 0f)
        assertEquals(0f, end.actionAlpha, 0f)
        assertTrue("nothing may still be dropping in at the end", end.rowsShiftDp <= 0f)
        assertTrue("nothing may still be dropping in at the end", end.actionShiftDp <= 0f)
    }

    @Test
    fun `no value ever leaves its legal range`() {
        fractions.forEach { f ->
            val frame = PillMorphChoreography.expand(f)
            assertTrue("panel alpha out of range at $f", frame.panelAlpha in 0f..1f)
            assertTrue("rows alpha out of range at $f", frame.rowsAlpha in 0f..1f)
            assertTrue("action alpha out of range at $f", frame.actionAlpha in 0f..1f)
            assertTrue("radius out of range at $f", frame.radius in 0f..1f)

            val back = PillMorphChoreography.collapse(f)
            assertTrue("panel alpha out of range at $f", back.panelAlpha in 0f..1f)
            assertTrue("rows alpha out of range at $f", back.rowsAlpha in 0f..1f)
            assertTrue("action alpha out of range at $f", back.actionAlpha in 0f..1f)
            assertTrue("radius out of range at $f", back.radius in 0f..1f)
        }
    }

    @Test
    fun `content appears in reading order on the way out`() {
        fractions.forEach { f ->
            val frame = PillMorphChoreography.expand(f)
            assertTrue("panel must lead the rows at $f", frame.panelAlpha >= frame.rowsAlpha)
            assertTrue("rows must lead the action at $f", frame.rowsAlpha >= frame.actionAlpha)
        }
    }

    @Test
    fun `content leaves in reverse order on the way back`() {
        fractions.forEach { f ->
            val frame = PillMorphChoreography.collapse(f)
            assertTrue("action must leave first at $f", frame.actionAlpha <= frame.rowsAlpha)
            assertTrue("rows must leave before the panel at $f", frame.rowsAlpha <= frame.panelAlpha)
        }
    }

    @Test
    fun `the content is gone while the shell is still shrinking`() {
        // Halfway through a collapse the window is nowhere near the pill yet,
        // but every piece of content is already invisible - the island never
        // shows an empty shell.
        val frame = PillMorphChoreography.collapse(0.45f)
        assertEquals(0f, frame.panelAlpha, 0f)
        assertEquals(0f, frame.rowsAlpha, 0f)
        assertEquals(0f, frame.actionAlpha, 0f)
    }

    @Test
    fun `the content finishes with the shell, not long before it`() {
        // Rows and the way out are still arriving while the shell settles, so the
        // last third of the clock is not a dead picture. Fractions here are
        // progress; the ease turns them into 70% of the elapsed run.
        val frame = PillMorphChoreography.expand(PillMorphEngine.decelerate(0.70f))
        assertTrue("rows at ${frame.rowsAlpha}", frame.rowsAlpha in 0.5f..0.99f)
        assertTrue("the way out at ${frame.actionAlpha}", frame.actionAlpha in 0.5f..0.99f)
        assertEquals(
            "and everything lands together with the shell",
            PillMorphChoreography.rest(true),
            PillMorphChoreography.expand(1f)
        )
    }

    @Test
    fun `the radius is the fraction itself`() {
        fractions.forEach { f ->
            assertEquals(f, PillMorphChoreography.expand(f).radius, 0.0001f)
            assertEquals(1f - f, PillMorphChoreography.collapse(f).radius, 0.0001f)
        }
    }

    @Test
    fun `rows and action settle downward on the way out and ride up on the way back`() {
        fractions.forEach { f ->
            val out = PillMorphChoreography.expand(f)
            assertTrue("rows shift must not go up at $f", out.rowsShiftDp >= 0f)
            assertTrue("action shift must not go up at $f", out.actionShiftDp >= 0f)

            val back = PillMorphChoreography.collapse(f)
            assertTrue("rows shift must not go down at $f", back.rowsShiftDp <= 0f)
            assertTrue("action shift must not go down at $f", back.actionShiftDp <= 0f)
        }
    }

    @Test
    fun `out-of-range fractions clamp instead of overshooting`() {
        assertEquals(PillMorphChoreography.expand(1f), PillMorphChoreography.expand(1.4f))
        assertEquals(PillMorphChoreography.expand(0f), PillMorphChoreography.expand(-0.6f))
        assertEquals(PillMorphChoreography.collapse(1f), PillMorphChoreography.collapse(2f))
        assertEquals(PillMorphChoreography.collapse(0f), PillMorphChoreography.collapse(-1f))
    }

    @Test
    fun `every ramp is monotonic`() {
        fun nonDecreasing(values: List<Float>): Boolean =
            values.zipWithNext().all { (a, b) -> b >= a - 0.0001f }

        val expands = fractions.map { PillMorphChoreography.expand(it) }
        assertTrue(nonDecreasing(expands.map { it.panelAlpha }))
        assertTrue(nonDecreasing(expands.map { it.rowsAlpha }))
        assertTrue(nonDecreasing(expands.map { it.actionAlpha }))
        assertTrue(nonDecreasing(expands.map { -it.rowsShiftDp }))
        assertTrue(nonDecreasing(expands.map { -it.actionShiftDp }))

        val collapses = fractions.map { PillMorphChoreography.collapse(it) }
        assertTrue(nonDecreasing(collapses.map { -it.panelAlpha }))
        assertTrue(nonDecreasing(collapses.map { -it.rowsAlpha }))
        assertTrue(nonDecreasing(collapses.map { -it.actionAlpha }))
    }

    // ---------- the numbers, locked ----------

    @Test
    fun `the arrival still lands in reading order at its halfway mark`() {
        // Not a shape test, a drift guard: at half the clock the panel is in, the
        // rows are on their way, and the way out waits - measured through the
        // ease, because the staging lives in progress.
        val frame = PillMorphChoreography.expand(PillMorphEngine.decelerate(0.5f))
        assertEquals(1f, frame.panelAlpha, EPS)
        assertTrue("rows at ${frame.rowsAlpha}", frame.rowsAlpha in 0.3f..0.8f)
        assertEquals(0f, frame.actionAlpha, EPS)
        assertTrue(
            "reading order",
            frame.panelAlpha >= frame.rowsAlpha && frame.rowsAlpha >= frame.actionAlpha
        )
    }

    @Test
    fun `the departure still has the content gone by its halfway mark`() {
        val frame = PillMorphChoreography.collapse(0.5f)
        assertEquals(0.5f, frame.radius, EPS)
        assertEquals(0f, frame.panelAlpha, EPS)
        assertEquals(0f, frame.rowsAlpha, EPS)
        assertEquals(0f, frame.actionAlpha, EPS)
        assertEquals(-5f, frame.rowsShiftDp, EPS)
        assertEquals(-8f, frame.actionShiftDp, EPS)
    }

    // ---------- reversal: the seam ----------

    @Test
    fun `a reversal starts on the frame that is already on screen`() {
        val halfOpen = PillMorphChoreography.expand(0.35f)
        assertEquals("collapse takes over the half-open picture", halfOpen,
            PillMorphChoreography.collapse(0f, halfOpen))
        assertEquals("re-opening takes over the half-open picture", halfOpen,
            PillMorphChoreography.expand(0f, halfOpen))

        val halfGone = PillMorphChoreography.collapse(0.2f)
        assertEquals("expand takes over the half-gone picture", halfGone,
            PillMorphChoreography.expand(0f, halfGone))
        assertEquals("re-collapsing takes over the half-gone picture", halfGone,
            PillMorphChoreography.collapse(0f, halfGone))
    }

    @Test
    fun `collapsing out of a half-open panel never brightens a thing`() {
        // The flash: a collapse used to begin from the full panel pose, so rows
        // that had barely started arriving jumped to opaque in one frame.
        val halfOpen = PillMorphChoreography.expand(0.35f)
        assertTrue("the capture must be mid-arrival", halfOpen.rowsAlpha < 0.5f)
        fractions.forEach { f ->
            val frame = PillMorphChoreography.collapse(f, halfOpen)
            assertTrue("panel went up at $f", frame.panelAlpha <= halfOpen.panelAlpha + EPS)
            assertTrue("rows went up at $f", frame.rowsAlpha <= halfOpen.rowsAlpha + EPS)
            assertTrue("action went up at $f", frame.actionAlpha <= halfOpen.actionAlpha + EPS)
            assertTrue("shell opened back up at $f", frame.radius <= halfOpen.radius + EPS)
        }
        assertTrue("nothing may travel back down into the picture",
            nonIncreasing(fractions.map { PillMorphChoreography.collapse(it, halfOpen).rowsShiftDp }))
        assertTrue("nothing may travel back down into the picture",
            nonIncreasing(fractions.map { PillMorphChoreography.collapse(it, halfOpen).actionShiftDp }))
    }

    @Test
    fun `re-opening out of a half-gone panel never dims a thing`() {
        val halfGone = PillMorphChoreography.collapse(0.2f)
        assertTrue("the capture must be mid-departure", halfGone.panelAlpha > 0.3f)
        fractions.forEach { f ->
            val frame = PillMorphChoreography.expand(f, halfGone)
            assertTrue("panel went down at $f", frame.panelAlpha >= halfGone.panelAlpha - EPS)
            assertTrue("rows went down at $f", frame.rowsAlpha >= halfGone.rowsAlpha - EPS)
            assertTrue("action went down at $f", frame.actionAlpha >= halfGone.actionAlpha - EPS)
            assertTrue("shell closed back down at $f", frame.radius >= halfGone.radius - EPS)
            assertTrue("rows may only settle upward at $f",
                frame.rowsShiftDp >= halfGone.rowsShiftDp - EPS)
        }
    }

    @Test
    fun `an interrupted run lands where a finished one lands`() {
        val landed = PillMorphChoreography.collapse(1f, PillMorphChoreography.expand(0.35f))
        assertFrame(PillMorphChoreography.collapse(1f), landed, "a reversal still ends the departure")

        val opened = PillMorphChoreography.expand(1f, PillMorphChoreography.collapse(0.2f))
        assertFrame(PillMorphChoreography.rest(true), opened, "a reversal still ends the arrival")
    }

    @Test
    fun `a run of taps ends closed however it was interrupted`() {
        val frame = walk(listOf(0.45f, 0.3f, 0.7f, 0.2f, 0.6f, 1f))
        assertFrame(PillMorphChoreography.collapse(1f), frame, "six taps, ending shut")
    }

    @Test
    fun `a run of taps ends open however it was interrupted`() {
        val frame = walk(listOf(0.3f, 0.6f, 0.2f, 0.8f, 1f))
        assertFrame(PillMorphChoreography.rest(true), frame, "five taps, ending open")
    }

    // ---------- the freeze, for the sprite layer's removal ----------

    @Test
    fun `the six surviving channels are frozen against the pre-cleanup baseline`() {
        // Batch 6 removed the panel's dead sprite layer and the two channels that
        // only ever drove it. The six that survive are frozen here, one digest per
        // channel so a failure names the channel that moved instead of only saying
        // "something did".
        //
        // What this lock is for is the future, not this batch. Recording these six
        // digests after the removal produced exactly the strings a recording before
        // it would have given, because the surviving channels are computed by lines
        // the patch never touches - so the digests cannot be the proof that nothing
        // moved; they are a forward guard against drift, and nothing more. The
        // proof is the diff: in production it deletes the two fields, their two
        // Spans, the constant that fed one of them, and the code that read them, and
        // adds no statement. A reviewer also rebuilt the ramp arithmetic in strict
        // float32 and reproduced all six digests from both the pre- and post-patch
        // choreography, which is the strongest form of that claim this machine has.
        val frames = frames()
        assertEquals(
            "the freeze and its baseline must name the same six channels",
            setOf("radius", "panelAlpha", "rowsAlpha", "rowsShiftDp", "actionAlpha", "actionShiftDp"),
            channels().map { it.first }.toSet() + BASELINE.keys.toSet()
        )
        val moved = channels().mapNotNull { (name, pick) ->
            val actual = digestOf(frames.map(pick))
            if (actual == BASELINE.getValue(name)) null else "$name = $actual"
        }
        assertEquals("the choreography moved: $moved", emptyList<String>(), moved)
    }

    /** The six surviving channels, in the order their digests were recorded. */
    private fun channels(): List<Pair<String, (PillMorphChoreography.Frame) -> Float>> = listOf(
        "radius" to { it.radius },
        "panelAlpha" to { it.panelAlpha },
        "rowsAlpha" to { it.rowsAlpha },
        "rowsShiftDp" to { it.rowsShiftDp },
        "actionAlpha" to { it.actionAlpha },
        "actionShiftDp" to { it.actionShiftDp }
    )

    /**
     * The frames the freeze covers: both rests, both clamped ends, a full
     * millisecond grid in each direction from rest, the same grid from a live
     * mid-morph frame, and the two tap walks.
     */
    private fun frames(): List<PillMorphChoreography.Frame> {
        val grid = (0..1000).map { it / 1000f }
        val halfOpen = PillMorphChoreography.expand(0.35f)
        val halfGone = PillMorphChoreography.collapse(0.2f)
        return listOf(
            PillMorphChoreography.rest(true),
            PillMorphChoreography.rest(false),
            PillMorphChoreography.expand(-0.6f),
            PillMorphChoreography.expand(1.4f),
            PillMorphChoreography.collapse(-1f),
            PillMorphChoreography.collapse(2f)
        ) + grid.map { PillMorphChoreography.expand(it) } +
            grid.map { PillMorphChoreography.collapse(it) } +
            grid.map { PillMorphChoreography.collapse(it, halfOpen) } +
            grid.map { PillMorphChoreography.expand(it, halfGone) } +
            listOf(walk(listOf(0.45f, 0.3f, 0.7f, 0.2f, 0.6f, 1f)), walk(listOf(0.3f, 0.6f, 0.2f, 0.8f, 1f)))
    }

    /** SHA-256 over one channel's values, as raw float bits, in order. */
    private fun digestOf(values: List<Float>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = java.nio.ByteBuffer.allocate(4)
        values.forEach {
            buffer.clear()
            digest.update(buffer.putFloat(it).array())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * Plays a tap sequence the way the service does: alternate directions, stop
     * each run part way, hand the next run the frame that is on screen. Every
     * seam has to match and nothing may overshoot inside a run.
     */
    private fun walk(taps: List<Float>): PillMorphChoreography.Frame {
        var frame = PillMorphChoreography.rest(false)
        var opening = true
        taps.forEach { stop ->
            val path = (0..20).map { i ->
                val f = stop * i / 20f
                if (opening) PillMorphChoreography.expand(f, frame)
                else PillMorphChoreography.collapse(f, frame)
            }
            assertEquals("the run to $stop starts on the live frame", frame, path.first())
            val target = path.last()
            path.forEachIndexed { i, sample ->
                assertWithin(frame, target, sample, "run to $stop at step $i")
            }
            frame = target
            opening = !opening
        }
        return frame
    }

    private fun assertWithin(
        from: PillMorphChoreography.Frame,
        to: PillMorphChoreography.Frame,
        actual: PillMorphChoreography.Frame,
        label: String
    ) {
        fun check(name: String, a: Float, b: Float, v: Float) = assertTrue(
            "$label: $name overshot ($a -> $b, was $v)",
            v >= minOf(a, b) - EPS && v <= maxOf(a, b) + EPS
        )
        check("radius", from.radius, to.radius, actual.radius)
        check("panel", from.panelAlpha, to.panelAlpha, actual.panelAlpha)
        check("rows", from.rowsAlpha, to.rowsAlpha, actual.rowsAlpha)
        check("rows shift", from.rowsShiftDp, to.rowsShiftDp, actual.rowsShiftDp)
        check("action", from.actionAlpha, to.actionAlpha, actual.actionAlpha)
        check("action shift", from.actionShiftDp, to.actionShiftDp, actual.actionShiftDp)
    }

    private fun assertFrame(
        expected: PillMorphChoreography.Frame,
        actual: PillMorphChoreography.Frame,
        label: String
    ) {
        assertEquals("$label: radius", expected.radius, actual.radius, EPS)
        assertEquals("$label: panel alpha", expected.panelAlpha, actual.panelAlpha, EPS)
        assertEquals("$label: rows alpha", expected.rowsAlpha, actual.rowsAlpha, EPS)
        assertEquals("$label: rows shift", expected.rowsShiftDp, actual.rowsShiftDp, EPS)
        assertEquals("$label: action alpha", expected.actionAlpha, actual.actionAlpha, EPS)
        assertEquals("$label: action shift", expected.actionShiftDp, actual.actionShiftDp, EPS)
    }

    private fun nonIncreasing(values: List<Float>): Boolean =
        values.zipWithNext().all { (a, b) -> b <= a + EPS }

    private companion object {
        /** Fractions are read to four decimals; anything finer is not motion. */
        const val EPS = 0.0001f

        /** The freeze: one digest per surviving channel, over the frames above. */
        val BASELINE = mapOf(
            "radius" to "b143b72625127373bf05e353db9d98897fd602cc110d314339616d6a82ad1883",
            "panelAlpha" to "62045f9d732b3aa98997f9511587756a3758aa864d44a1c0f2db7c6184290be6",
            "rowsAlpha" to "f4cfd609c66f7e8c4d093976a5275e34c063d8f32d573709ca49f41b50ee3cf9",
            "rowsShiftDp" to "49746ba478ef17eb98044dccadaad481b43564ce244228222459c0f1b53d6ac6",
            "actionAlpha" to "66e6fe6a4df3cddd377682d562bc34ec935c721cd92978a3609efde4f4b35d1f",
            "actionShiftDp" to "2ede56dd7fc54bd393c543858ad75d7aa6c4797c33acab56ecbd8c611d29d297"
        )
    }
}
