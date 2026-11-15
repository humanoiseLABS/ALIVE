package com.projectego.app.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.projectego.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class InteractiveMotionControlsTest {

    private lateinit var activity: Activity
    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        themedContext = ContextThemeWrapper(activity, R.style.Theme_ProjectEgo)
    }

    @After
    fun tearDown() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `morphPillColors applies target colors immediately when detached`() {
        val pill = TextView(themedContext).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE) }
            setTextColor(Color.BLACK)
        }

        AliveMotion.morphPillColors(pill, Color.BLACK, Color.WHITE)

        val bg = (pill.background as GradientDrawable).color?.defaultColor
        assertEquals(Color.BLACK, bg)
        assertEquals(Color.WHITE, pill.currentTextColor)
    }

    @Test
    fun `morphPillColors transitions cleanly and ends on target colors when attached`() {
        val root = FrameLayout(themedContext)
        val pill = TextView(themedContext).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE) }
            setTextColor(Color.BLACK)
        }
        root.addView(pill)
        activity.setContentView(root)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        AliveMotion.morphPillColors(pill, Color.DKGRAY, Color.LTGRAY, durationMs = 120L)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(300L, TimeUnit.MILLISECONDS)

        val bg = (pill.background as GradientDrawable).color?.defaultColor
        assertEquals(Color.DKGRAY, bg)
        assertEquals(Color.LTGRAY, pill.currentTextColor)
    }

    @Test
    fun `transitionDirectionalSwap resets transforms when detached or zero direction`() {
        val body = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(themedContext).apply { text = "Card 1" })
            addView(TextView(themedContext).apply { text = "Card 2" })
        }

        body.alpha = 0.2f
        body.translationX = 50f
        AliveMotion.transitionDirectionalSwap(body, direction = 1)

        assertEquals(1f, body.alpha, 0.001f)
        assertEquals(0f, body.translationX, 0.001f)
        assertEquals(1f, body.scaleX, 0.001f)
    }

    @Test
    fun `focus setup ring updates logical fraction immediately when attached`() {
        val root = FrameLayout(themedContext)
        val ui = AliveComponents(themedContext)
        val ring = FocusSetupScreen.Ring(themedContext, ui)
        ring.fraction = 25f / 60f
        root.addView(ring)
        activity.setContentView(root)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(600L, TimeUnit.MILLISECONDS)

        ring.fraction = 45f / 60f
        assertEquals(45f / 60f, ring.fraction, 0.001f)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500L, TimeUnit.MILLISECONDS)
        assertEquals(45f / 60f, ring.fraction, 0.001f)
    }

    @Test
    fun `brutalist bar chart reveals from baseline and handles tap selection cleanly while attached`() {
        val root = FrameLayout(themedContext)
        var tappedIndex = -99
        val chart = BrutalistBarChart(themedContext).apply {
            initialRevealDelayMs = 100L
            setData(
                listOf(
                    BrutalistBarChart.Column("S", 30, 0.5f, DayBarKind.SPENT),
                    BrutalistBarChart.Column("R", 50, 0.9f, DayBarKind.SPENT)
                )
            )
            onColumnTapped = { tappedIndex = it }
        }
        root.addView(chart, FrameLayout.LayoutParams(400, 300))
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 400, 300)
        assertEquals(0f, chart.revealFraction, 0.001f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(950L, TimeUnit.MILLISECONDS)
        assertEquals(1f, chart.revealFraction, 0.001f)

        val now = SystemClock.uptimeMillis()
        chart.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 150f, 0))
        chart.dispatchTouchEvent(MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, 100f, 150f, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertEquals(0, chart.selectedIndex)
        assertEquals(0, tappedIndex)
        assertEquals(1f, chart.selectionFraction, 0.001f)

        // Tap same bar again to deselect smoothly.
        val t2 = SystemClock.uptimeMillis()
        chart.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 100f, 150f, 0))
        chart.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 40, MotionEvent.ACTION_UP, 100f, 150f, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(300L, TimeUnit.MILLISECONDS)
        assertEquals(-1, chart.selectedIndex)
    }

    @Test
    fun `swapStageChild cross-slides incoming child and cleans up outgoing child`() {
        val root = FrameLayout(themedContext)
        val stage = FrameLayout(themedContext)
        val first = TextView(themedContext).apply { text = "Hari ini" }
        val second = TextView(themedContext).apply { text = "7 hari" }
        root.addView(stage)
        stage.addView(first)
        activity.setContentView(root)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        AliveMotion.swapStageChild(stage, second, direction = 1, distanceDp = 24f)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(350L, TimeUnit.MILLISECONDS)

        assertEquals(1, stage.childCount)
        assertEquals(second, stage.getChildAt(0))
        assertEquals(1f, second.alpha, 0.001f)
        assertEquals(0f, second.translationX, 0.001f)
    }

    @Test
    fun `startBreathing resets scale and cleans up cleanly on detach`() {
        val root = FrameLayout(themedContext)
        val badge = View(themedContext)
        root.addView(badge)
        AliveMotion.startBreathing(badge, maxScale = 1.06f, periodMs = 400L)
        activity.setContentView(root)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        root.removeView(badge)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(badge.isAttachedToWindow)
        assertTrue(badge.scaleX > 0.99f && badge.scaleX < 1.01f)
        assertEquals(1f, badge.scaleY, 0.001f)
    }
}
