package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation dock contract and Nimbus "Waterline" Liquid Glass physics checks:
 * selection state, active/inactive ink, tap callbacks (including re-taps),
 * elastic water stretch + satellite droplets + tap ripples, and Canvas rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveNavBarTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_ProjectEgo
        )
    }

    private fun dock(bar: AliveNavBar): LinearLayout = bar.getChildAt(0) as LinearLayout

    private fun cell(bar: AliveNavBar, index: Int): LinearLayout =
        dock(bar).getChildAt(index) as LinearLayout

    private fun labelOf(bar: AliveNavBar, index: Int): TextView =
        cell(bar, index).getChildAt(1) as TextView

    private fun layoutBar(bar: AliveNavBar, widthPx: Int = 1080, heightPx: Int = 240) {
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST)
        bar.measure(wSpec, hSpec)
        bar.layout(0, 0, bar.measuredWidth, bar.measuredHeight)
    }

    @Test
    fun `selecting a destination highlights it with active ink and dims the others`() {
        val bar = AliveNavBar(context)
        layoutBar(bar)

        bar.select(AliveNavBar.Index.STATS, animate = false)

        assertEquals(AliveNavBar.Index.STATS, bar.activeIndex)
        assertTrue(cell(bar, AliveNavBar.Index.STATS).isSelected)
        assertEquals(AliveNavBar.FG_LIGHT, labelOf(bar, AliveNavBar.Index.STATS).currentTextColor)
        listOf(AliveNavBar.Index.HOME, AliveNavBar.Index.QUEST, AliveNavBar.Index.PROFILE)
            .forEach { other ->
                assertFalse(cell(bar, other).isSelected)
                assertEquals(AliveNavBar.FG_LIGHT_SOFT, labelOf(bar, other).currentTextColor)
            }
    }

    @Test
    fun `selecting before layout snaps water drop onto chosen cell once laid out`() {
        val bar = AliveNavBar(context)
        bar.select(AliveNavBar.Index.PROFILE, animate = false)

        layoutBar(bar)

        assertTrue(cell(bar, AliveNavBar.Index.PROFILE).isSelected)
        val drop = bar.liquidDropBounds()
        val profileCell = cell(bar, AliveNavBar.Index.PROFILE)
        val dockX = dock(bar).left.toFloat()
        val profileCenterX = dockX + (profileCell.left + profileCell.right) * 0.5f
        assertEquals(profileCenterX, drop.centerX(), 2f)
    }

    @Test
    fun `tapping a destination reports every tap including re-taps`() {
        val bar = AliveNavBar(context)
        layoutBar(bar)
        val seen = mutableListOf<Int>()
        bar.onSelect = { seen += it }

        cell(bar, AliveNavBar.Index.QUEST).performClick()
        cell(bar, AliveNavBar.Index.QUEST).performClick()
        cell(bar, AliveNavBar.Index.HOME).performClick()

        assertEquals(
            listOf(
                AliveNavBar.Index.QUEST,
                AliveNavBar.Index.QUEST,
                AliveNavBar.Index.HOME
            ),
            seen
        )
        assertTrue(cell(bar, AliveNavBar.Index.HOME).isSelected)
    }

    @Test
    fun `long jump stretches liquid drop, splits droplets, emits ripples, and settles on target`() {
        val bar = AliveNavBar(context)
        layoutBar(bar)
        bar.select(AliveNavBar.Index.HOME, animate = false)
        val homeDrop = bar.liquidDropBounds()

        bar.select(AliveNavBar.Index.PROFILE, animate = true)

        assertTrue("Long jump should spawn splitting water droplets", bar.activeDropletCount() >= 2)
        assertTrue("Tap should emit at least one water ripple", bar.activeRippleCount() >= 1)

        // Mid-flight: two-body asymmetric spring stretches the water drop wider than a single cell
        bar.advanceForTest(0.07f)
        val stretchedDrop = bar.liquidDropBounds()
        assertTrue(
            "Mid-flight drop width (${stretchedDrop.width()}) should stretch past resting width (${homeDrop.width()})",
            stretchedDrop.width() > homeDrop.width() * 1.15f
        )

        // After settling (~1.4s): droplets merge back and drop centers neatly on PROFILE
        bar.advanceForTest(1.4f)
        val settledDrop = bar.liquidDropBounds()
        val profileCell = cell(bar, AliveNavBar.Index.PROFILE)
        val profileCenterX = dock(bar).left + (profileCell.left + profileCell.right) * 0.5f
        assertEquals(0, bar.activeDropletCount())
        assertEquals(profileCenterX, settledDrop.centerX(), 4f)
        assertEquals(homeDrop.width(), settledDrop.width(), 4f)
    }

    @Test
    fun `draws light and dark liquid glass tones cleanly onto software canvas`() {
        val bar = AliveNavBar(context)
        layoutBar(bar)
        bar.select(AliveNavBar.Index.QUEST, animate = false)

        val bitmap = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        bar.draw(canvas)

        bar.setTone(AliveNavBar.Tone.DARK)
        assertEquals(AliveNavBar.FG_DARK, labelOf(bar, AliveNavBar.Index.QUEST).currentTextColor)
        bar.select(AliveNavBar.Index.STATS, animate = true)
        bar.advanceForTest(0.05f)
        bar.draw(canvas)
        bitmap.recycle()
    }
}
