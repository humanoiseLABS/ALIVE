package com.projectego.app.ui

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AliveMotionTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun collectCascadeTargetsUnwrapsScrollAndScreenWrapperAndExcludesSpacers() {
        val screenColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val firstCard = TextView(context).apply { text = "One" }
        val plainSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
        }
        val zeroHeightSpacer = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0)
        }
        val spaceWidget = Space(context)
        val hiddenView = TextView(context).apply { visibility = View.GONE }
        val secondCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val thirdCard = TextView(context).apply { text = "Three" }

        screenColumn.addView(firstCard)
        screenColumn.addView(plainSpacer)
        screenColumn.addView(zeroHeightSpacer)
        screenColumn.addView(spaceWidget)
        screenColumn.addView(hiddenView)
        screenColumn.addView(secondCard)
        screenColumn.addView(thirdCard)

        val outerPageColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(screenColumn)
        }
        val scroll = ScrollView(context).apply {
            addView(outerPageColumn)
        }

        val allTargets = AliveMotion.collectCascadeTargets(scroll, maxItems = 8)
        assertEquals(listOf(firstCard, secondCard, thirdCard), allTargets)

        val limited = AliveMotion.collectCascadeTargets(scroll, maxItems = 2)
        assertEquals(listOf(firstCard, secondCard), limited)
    }

    @Test
    fun collectCascadeTargetsUnwrapsOverlayFrameLayoutContainer() {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = TextView(context)
        val body = TextView(context)
        inner.addView(header)
        inner.addView(body)
        val scroll = ScrollView(context).apply { addView(inner) }
        overlay.addView(scroll)

        val targets = AliveMotion.collectCascadeTargets(overlay, maxItems = 8)
        assertEquals(listOf(header, body), targets)
    }

    @Test
    fun transitionTabsDetachedSwitchesVisibilitySynchronouslyAndRunsBeforeShow() {
        val outgoing = FrameLayout(context).apply {
            visibility = View.VISIBLE
            alpha = 0.5f
            translationX = 40f
        }
        val incoming = FrameLayout(context).apply {
            visibility = View.GONE
            alpha = 0f
            translationX = -80f
        }
        assertFalse(AliveMotion.shouldAnimate(incoming))

        var beforeShowCalled = false
        AliveMotion.transitionTabs(
            outgoing = outgoing,
            incoming = incoming,
            direction = 1
        ) {
            beforeShowCalled = true
        }

        assertTrue(beforeShowCalled)
        assertEquals(View.GONE, outgoing.visibility)
        assertEquals(1f, outgoing.alpha)
        assertEquals(0f, outgoing.translationX)

        assertEquals(View.VISIBLE, incoming.visibility)
        assertEquals(1f, incoming.alpha)
        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.scaleX)
        assertEquals(1f, incoming.scaleY)
    }

    @Test
    fun transitionTabsAttachedPreservesParentChildOrderAndOverlayLayering() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val tabContent = FrameLayout(activity)
        val home = FrameLayout(activity).apply { visibility = View.VISIBLE }
        val quest = FrameLayout(activity).apply { visibility = View.GONE }
        val profile = FrameLayout(activity).apply { visibility = View.GONE }
        val overlay = FrameLayout(activity).apply { visibility = View.GONE }
        tabContent.addView(home)
        tabContent.addView(quest)
        tabContent.addView(profile)
        tabContent.addView(overlay)
        activity.setContentView(tabContent)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(AliveMotion.shouldAnimate(quest))

        AliveMotion.transitionTabs(home, quest, direction = 1)
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        AliveMotion.transitionTabs(quest, profile, direction = 1)
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        AliveMotion.transitionTabs(profile, home, direction = -1)
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        // Child positions must stay fixed so overlayContainer never drops below tab views.
        assertSame(home, tabContent.getChildAt(0))
        assertSame(quest, tabContent.getChildAt(1))
        assertSame(profile, tabContent.getChildAt(2))
        assertSame(overlay, tabContent.getChildAt(3))

        assertEquals(View.VISIBLE, home.visibility)
        assertEquals(1f, home.alpha)
        assertEquals(0f, home.translationZ)
        assertEquals(View.GONE, quest.visibility)
        assertEquals(View.GONE, profile.visibility)
    }

    @Test
    fun revealTabResetsStaleTransformsAndRestoresVisibility() {
        val tab = FrameLayout(context).apply {
            visibility = View.GONE
            alpha = 0.25f
            translationX = 48f
            translationZ = 2f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        AliveMotion.revealTab(tab)
        assertEquals(View.VISIBLE, tab.visibility)
        assertEquals(1f, tab.alpha)
        assertEquals(0f, tab.translationX)
        assertEquals(0f, tab.translationZ)
        assertEquals(1f, tab.scaleX)
        assertEquals(1f, tab.scaleY)
    }

    @Test
    fun enterAndExitOverlayDetachedTogglesVisibilityAndNavBarAndRunsCallback() {
        val overlay = FrameLayout(context).apply { visibility = View.GONE }
        val navBar = FrameLayout(context).apply { visibility = View.VISIBLE }

        AliveMotion.enterOverlay(overlay, navBar, keepNav = false)
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(View.GONE, navBar.visibility)

        var completed = false
        AliveMotion.exitOverlay(overlay, navBar, shouldRestoreNav = true) {
            completed = true
        }
        assertTrue(completed)
        assertEquals(View.GONE, overlay.visibility)
        assertEquals(View.VISIBLE, navBar.visibility)

        AliveMotion.enterOverlay(overlay, navBar, keepNav = true)
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(View.VISIBLE, navBar.visibility)
    }

    @Test
    fun enterAndExitOverlayAttachedKeepsBackdropFullBleedAndRestoresPartialNav() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val inner = ScrollView(activity).apply { addView(TextView(activity)) }
        val overlay = FrameLayout(activity).apply {
            visibility = View.GONE
            addView(inner)
        }
        val navBar = FrameLayout(activity).apply {
            visibility = View.VISIBLE
            alpha = 0.4f
            translationY = 30f
        }
        root.addView(overlay)
        root.addView(navBar)
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()

        AliveMotion.enterOverlay(overlay, navBar, keepNav = false)
        assertEquals(1f, overlay.scaleX)
        assertEquals(1f, overlay.scaleY)
        assertEquals(0f, overlay.translationY)
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        var completed = false
        AliveMotion.exitOverlay(overlay, navBar, shouldRestoreNav = true) {
            completed = true
        }
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        assertTrue(completed)
        assertEquals(View.GONE, overlay.visibility)
        assertEquals(View.VISIBLE, navBar.visibility)
        assertEquals(1f, navBar.alpha)
        assertEquals(0f, navBar.translationY)
        assertEquals(0f, inner.translationY)
        assertEquals(1f, inner.scaleX)
        assertEquals(1f, inner.scaleY)
    }

    @Test
    fun enterAndExitSheetDetachedTogglesVisibilityAndRunsCallback() {
        val container = FrameLayout(context).apply { visibility = View.GONE }
        val scrim = FrameLayout(context).apply {
            addView(LinearLayout(context))
        }
        container.addView(scrim)

        AliveMotion.enterSheet(container)
        assertEquals(View.VISIBLE, container.visibility)

        var dismissed = false
        AliveMotion.exitSheet(container) {
            dismissed = true
        }
        assertTrue(dismissed)
        assertEquals(View.GONE, container.visibility)
    }
}
