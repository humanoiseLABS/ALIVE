package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The flat language, as locks. Every rule here is one the approved renders obey
 * and the suite currently cannot see, so each is written against the resources
 * the redesign will produce and is red until the batch that produces them.
 *
 * None of them can pass by omission. A missing token fails by name, a surviving
 * old token fails by name, and a radius that is not one of the three fails by
 * number - so a half-done migration reads as a red suite rather than as a green
 * one wearing two palettes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FlatDesignLocksTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun id(type: String, name: String): Int =
        context.resources.getIdentifier(name, type, context.packageName)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    /** The eleven category members the generator must emit, by key. */
    private val memberKeys = listOf(
        "study", "reading", "exercise", "walking", "cleaning",
        "creative", "coding", "writing", "mindfulness", "social", "work"
    )

    /** The six day-state members, by key. */
    private val stateKeys = listOf("normal", "near", "spent", "recovery", "unknown", "rest")

    @Test
    fun `the pixel voice is gone from the resources`() {
        listOf("silkscreen_regular", "silkscreen_bold", "press_start_2p").forEach { name ->
            assertTrue(
                "$name still ships - deleting the reference is not deleting the font",
                id("font", name) == 0
            )
        }
    }

    @Test
    fun `the old grounds are gone, not merely unused`() {
        // Introduce, repoint, delete: an alias left behind is how a palette ends
        // up with two sources of truth and every contrast test still green.
        listOf("alive_paper", "alive_canvas", "alive_ink", "alive_white").forEach { name ->
            assertTrue("$name still exists - the repoint is not finished", id("color", name) == 0)
        }
    }

    @Test
    fun `border and shadow tokens no longer exist`() {
        listOf("border_bold", "border_button", "border_hairline", "shadow_offset", "shadow_minor").forEach { name ->
            assertTrue("$name still exists - flat is zero border and zero shadow", id("dimen", name) == 0)
        }
    }

    @Test
    fun `the radius scale is 28, 22 and pill, and nothing else`() {
        val card = id("dimen", "radius_card")
        val field = id("dimen", "radius_field")
        assertTrue("radius_card is missing", card != 0)
        assertTrue("radius_field is missing", field != 0)
        assertTrue(
            "radius_card = ${context.resources.getDimensionPixelSize(card)}px, want ${dp(28)}px",
            context.resources.getDimensionPixelSize(card) == dp(28)
        )
        assertTrue(
            "radius_field = ${context.resources.getDimensionPixelSize(field)}px, want ${dp(22)}px",
            context.resources.getDimensionPixelSize(field) == dp(22)
        )
    }

    @Test
    fun `every field carries ink and every dark ground carries paper at 4_5`() {
        // The approved fields are pastel, so ink sits on them; the approved deep
        // grounds are dark, so paper sits on them. Both directions, measured from
        // the real resources, or the token is not allowed to exist.
        val ink = require("flat_ink")
        val paper = require("flat_paper")
        val failures = mutableListOf<String>()

        fieldColours().forEach { (name, argb) ->
            val ratio = contrast(ink, argb)
            if (ratio < 4.5) failures += "ink on $name = $ratio"
        }
        darkGrounds().forEach { (name, argb) ->
            val ratio = contrast(paper, argb)
            if (ratio < 4.5) failures += "paper on $name = $ratio"
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the family exists as seventeen named members`() {
        val missing = (memberKeys.map { "alive_member_$it" } + stateKeys.map { "alive_state_$it" })
            .filter { id("drawable", it) == 0 }
        assertTrue("missing members: $missing", missing.isEmpty())
    }

    // ---------- resolution ----------

    private fun require(name: String): Int {
        val resolved = id("color", name)
        assertTrue("$name does not exist yet", resolved != 0)
        return ContextCompat.getColor(context, resolved)
    }

    private fun fieldColours(): List<Pair<String, Int>> = listOf(
        "peach", "lilac", "mint", "sky", "butter", "rose", "lime", "coral",
        "grape", "aqua", "blossom", "leaf", "sun", "clay"
    ).map { name ->
        val resolved = id("color", "flat_field_$name")
        assertTrue("flat_field_$name does not exist yet", resolved != 0)
        name to (ContextCompat.getColor(context, resolved) and 0xFFFFFF)
    }

    private fun darkGrounds(): List<Pair<String, Int>> = listOf("deep", "deep2", "navy").map { name ->
        val resolved = id("color", "flat_$name")
        assertTrue("flat_$name does not exist yet", resolved != 0)
        name to (ContextCompat.getColor(context, resolved) and 0xFFFFFF)
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
