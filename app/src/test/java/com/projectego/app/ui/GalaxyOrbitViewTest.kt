package com.projectego.app.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.projectego.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class GalaxyOrbitViewTest {

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
    fun `shortestAngleDelta wraps cleanly across -PI and +PI branch cut`() {
        val nearPlusPi = (PI - 0.14).toFloat()
        val nearMinusPi = (-PI + 0.14).toFloat()

        val forward = GalaxyOrbitPhysics.shortestAngleDelta(nearPlusPi, nearMinusPi)
        assertEquals(0.28f, forward, 0.01f)

        val backward = GalaxyOrbitPhysics.shortestAngleDelta(nearMinusPi, nearPlusPi)
        assertEquals(-0.28f, backward, 0.01f)
    }

    @Test
    fun `stepInertia decelerates monotonically and snaps to zero at rest`() {
        var angle = 0f
        var velocity = 8.0f
        var steps = 0
        while (velocity > 0f && steps < 120) {
            val prevAngle = angle
            val prevVel = velocity
            val (nextAngle, nextVel) = GalaxyOrbitPhysics.stepInertia(angle, velocity, 0.032f)
            assertTrue("angle must advance forward while decelerating", nextAngle > prevAngle)
            assertTrue("velocity magnitude must decrease each step", abs(nextVel) < abs(prevVel))
            angle = nextAngle
            velocity = nextVel
            steps++
        }
        assertTrue("inertia must reach rest within 120 steps", steps < 120)
        assertEquals(0f, velocity, 0.0001f)
    }

    @Test
    fun `memberRotationDeg stays upright at rest and clamps during fast spins`() {
        assertEquals(0f, GalaxyOrbitPhysics.memberRotationDeg(0f), 0.0001f)
        assertEquals(0f, GalaxyOrbitPhysics.memberRotationDeg(0.02f), 0.0001f)
        assertTrue(GalaxyOrbitPhysics.memberRotationDeg(50f) >= -10f)
        assertTrue(GalaxyOrbitPhysics.memberRotationDeg(-50f) <= 10f)
    }

    @Test
    fun `entry swirl starts offset and settles at 0 radians cleanly`() {
        val orbit = createAttachedOrbit(size = 400, memberSize = 40, settleEntry = false)
        assertTrue(
            "expected entry swirl to start offset (< -0.2 rad) on attach, got ${orbit.orbitAngleRadians}",
            orbit.orbitAngleRadians < -0.2f
        )

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)
        assertEquals(0f, orbit.orbitAngleRadians, 0.01f)
        val firstMember = orbit.getChildAt(1)
        assertEquals(1f, firstMember.alpha, 0.01f)
        assertEquals(1f, firstMember.scaleX, 0.01f)
        assertEquals(0f, firstMember.rotation, 0.01f)
    }

    @Test
    fun `dragging finger from 12 to 3 o clock rotates orbiters while keeping center fixed`() {
        val orbit = createAttachedOrbit(size = 400, memberSize = 40)
        val center = orbit.getChildAt(0)
        val firstMember = orbit.getChildAt(1)

        val initialMemberX = firstMember.translationX + firstMember.width / 2f
        val initialMemberY = firstMember.translationY + firstMember.height / 2f
        assertEquals(200f, initialMemberX, 3f)
        assertEquals(20f, initialMemberY, 3f)

        val downTime = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 30f, 0)
        )
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, 320f, 80f, 0)
        )
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_MOVE, 370f, 200f, 0)
        )
        // Hold still briefly (>90ms) before release so we measure pure drag without fling travel.
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 150, MotionEvent.ACTION_UP, 370f, 200f, 0)
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals((PI / 2.0).toFloat(), orbit.orbitAngleRadians, 0.12f)
        val rotatedMemberX = firstMember.translationX + firstMember.width / 2f
        val rotatedMemberY = firstMember.translationY + firstMember.height / 2f
        assertEquals(380f, rotatedMemberX, 8f)
        assertEquals(200f, rotatedMemberY, 8f)

        assertEquals(0f, center.rotation, 0.0001f)
        assertEquals(0f, center.translationX, 0.0001f)
        assertEquals(0f, center.translationY, 0.0001f)
    }

    @Test
    fun `dragging across 9 o clock branch cut moves counter-clockwise smoothly without jump`() {
        val orbit = createAttachedOrbit(size = 400, memberSize = 40)
        val downTime = SystemClock.uptimeMillis()

        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 40f, 160f, 0)
        )
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, 30f, 200f, 0)
        )
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_MOVE, 40f, 240f, 0)
        )
        orbit.dispatchTouchEvent(
            MotionEvent.obtain(downTime, downTime + 150, MotionEvent.ACTION_UP, 40f, 240f, 0)
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Moving downward at 9 o'clock is counter-clockwise (~ -0.50 rad), never wrapping +5.7 rad.
        assertTrue(
            "expected small negative counter-clockwise angle, got ${orbit.orbitAngleRadians}",
            orbit.orbitAngleRadians in -0.8f..-0.2f
        )
    }

    @Test
    fun `tapping an orbiter selects it, brings it to 12 o clock, and preserves dormant base alphas`() {
        val orbit = createAttachedOrbit(size = 400, memberSize = 40)
        val alphas = List(17) { idx -> if (idx == 5 || idx == 6) 1.0f else 0.42f }
        orbit.setOrbiterBaseAlphas(alphas)

        val selectedEvents = mutableListOf<Int?>()
        orbit.onOrbiterSelected = { selectedEvents.add(it) }

        val targetSlot = 6
        val targetView = orbit.getChildAt(1 + targetSlot)
        val tapX = targetView.translationX + targetView.width / 2f
        val tapY = targetView.translationY + targetView.height / 2f

        val t0 = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, tapX, tapY, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 30, MotionEvent.ACTION_UP, tapX, tapY, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)

        assertEquals(targetSlot, orbit.selectedIndex)
        assertEquals(listOf<Int?>(targetSlot), selectedEvents)
        assertEquals(GalaxyOrbitView.SELECTED_SCALE, targetView.scaleX, 0.02f)
        assertEquals(1.0f, targetView.alpha, 0.02f)
        // Selected slot should now sit close to 12 o'clock (x ~= 200, y ~= 20).
        val snappedX = targetView.translationX + targetView.width / 2f
        val snappedY = targetView.translationY + targetView.height / 2f
        assertEquals(200f, snappedX, 10f)
        assertEquals(20f, snappedY, 10f)

        // Dormant neighbor keeps its 0.42f alpha, awakened non-selected keeps 1.0f.
        assertEquals(0.42f, orbit.getChildAt(1 + 0).alpha, 0.02f)
        assertEquals(1.0f, orbit.getChildAt(1 + 5).alpha, 0.02f)
    }

    @Test
    fun `tapping already selected orbiter or empty center clears selection`() {
        val orbit = createAttachedOrbit(size = 400, memberSize = 40)
        val events = mutableListOf<Int?>()
        orbit.onOrbiterSelected = { events.add(it) }

        // Select slot 5.
        val slot5 = orbit.getChildAt(1 + 5)
        var t0 = SystemClock.uptimeMillis()
        var tapX = slot5.translationX + slot5.width / 2f
        var tapY = slot5.translationY + slot5.height / 2f
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, tapX, tapY, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 25, MotionEvent.ACTION_UP, tapX, tapY, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)
        assertEquals(5, orbit.selectedIndex)

        // Tap center hub (far outside hit radius) -> clears selection.
        t0 = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 200f, 200f, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 25, MotionEvent.ACTION_UP, 200f, 200f, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500L, TimeUnit.MILLISECONDS)
        assertNull(orbit.selectedIndex)
        assertEquals(1.0f, slot5.scaleX, 0.02f)

        // Select slot 3 then tap slot 3 again -> toggles off.
        val slot3 = orbit.getChildAt(1 + 3)
        tapX = slot3.translationX + slot3.width / 2f
        tapY = slot3.translationY + slot3.height / 2f
        t0 = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, tapX, tapY, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 25, MotionEvent.ACTION_UP, tapX, tapY, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)
        assertEquals(3, orbit.selectedIndex)

        tapX = slot3.translationX + slot3.width / 2f
        tapY = slot3.translationY + slot3.height / 2f
        t0 = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, tapX, tapY, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 25, MotionEvent.ACTION_UP, tapX, tapY, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500L, TimeUnit.MILLISECONDS)
        assertNull(orbit.selectedIndex)
        assertEquals(listOf<Int?>(5, null, 3, null), events)
    }

    @Test
    fun `entry swirl preserves configured dormant base alphas after settling`() {
        val root = FrameLayout(themedContext)
        val orbit = GalaxyOrbitView(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(400, 400)
        }
        orbit.setCenterView(
            TextView(themedContext).apply { text = "Galaksi" },
            FrameLayout.LayoutParams(160, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        orbit.setOrbiters(
            List(17) { ImageView(themedContext).apply { setImageResource(R.drawable.alive_member_study) } },
            40
        )
        orbit.setOrbiterBaseAlphas(List(17) { idx -> if (idx == 5) 1.0f else 0.42f })
        root.addView(orbit)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 400, 400)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)

        assertEquals(0.42f, orbit.getChildAt(1 + 0).alpha, 0.02f)
        assertEquals(1.0f, orbit.getChildAt(1 + 5).alpha, 0.02f)
    }

    private fun createAttachedOrbit(
        size: Int,
        memberSize: Int,
        settleEntry: Boolean = true
    ): GalaxyOrbitView {
        val root = FrameLayout(themedContext)
        val orbit = GalaxyOrbitView(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
        val title = TextView(themedContext).apply { text = "Galaksi" }
        orbit.setCenterView(
            title,
            FrameLayout.LayoutParams(160, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        val orbiters = List(17) {
            ImageView(themedContext).apply {
                setImageResource(R.drawable.alive_member_study)
            }
        }
        orbit.setOrbiters(orbiters, memberSize)
        root.addView(orbit)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, size, size)
        if (settleEntry) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)
        }
        return orbit
    }
}
