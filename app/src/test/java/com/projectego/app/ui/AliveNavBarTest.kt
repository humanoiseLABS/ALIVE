package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The approved dock: one white pill, four equal keys, and the chosen key is an
 * ink pill inside it. The old notch and the floating circle belonged to the
 * bordered strip the flat language retired, so the test now holds the dock's
 * three promises instead of a circle's geometry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveNavBarTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ui = AliveComponents(context)

    private fun dock(bar: AliveNavBar): LinearLayout = bar.getChildAt(0) as LinearLayout

    private fun cell(bar: AliveNavBar, index: Int): LinearLayout =
        dock(bar).getChildAt(index) as LinearLayout

    private fun pillColour(bar: AliveNavBar, index: Int): Int? =
        (cell(bar, index).background as? GradientDrawable)?.color?.defaultColor

    @Test
    fun `only the chosen key wears the ink pill`() {
        val bar = AliveNavBar(context)
        bar.select(AliveNavBar.Index.STATS, animate = false)

        assertEquals(ui.palette.ink, pillColour(bar, AliveNavBar.Index.STATS))
        listOf(AliveNavBar.Index.HOME, AliveNavBar.Index.QUEST, AliveNavBar.Index.PROFILE)
            .forEach { index ->
                assertNull("key $index still wears a pill", pillColour(bar, index))
            }
        assertTrue(cell(bar, AliveNavBar.Index.STATS).isSelected)
    }

    @Test
    fun `a key chosen before the bar has a width still paints`() {
        // Opening ALIVE from the widget chooses a key before the first measure
        // pass. The dock has no geometry to get wrong, and this keeps it so.
        val bar = AliveNavBar(context)
        bar.select(AliveNavBar.Index.PROFILE)

        assertEquals(ui.palette.ink, pillColour(bar, AliveNavBar.Index.PROFILE))
        assertTrue(cell(bar, AliveNavBar.Index.PROFILE).isSelected)
    }

    @Test
    fun `tapping every key reports it, including the one already chosen`() {
        val bar = AliveNavBar(context)
        val tapped = mutableListOf<Int>()
        bar.onSelect = { tapped += it }

        listOf(
            AliveNavBar.Index.QUEST,
            AliveNavBar.Index.QUEST,
            AliveNavBar.Index.HOME
        ).forEach { index ->
            cell(bar, index).performClick()
        }

        assertEquals(
            listOf(
                AliveNavBar.Index.QUEST,
                AliveNavBar.Index.QUEST,
                AliveNavBar.Index.HOME
            ),
            tapped
        )
        assertEquals(ui.palette.ink, pillColour(bar, AliveNavBar.Index.HOME))
    }
}
