package com.projectego.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActiveFocusScreenTest {

    private lateinit var idContext: Context
    private lateinit var ui: AliveComponents

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("in"))
        }
        idContext = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_ProjectEgo)
        ui = AliveComponents(idContext)
    }

    @Test
    fun `renders live countdown ring, goal capsule, permission tiles, and drops essay bloat`() {
        val startMs = 1_000_000L
        var nowMs = startMs
        var extended = 0

        val screen = ActiveFocusScreen.build(
            context = idContext,
            ui = ui,
            data = ActiveFocusScreen.Data(
                activityType = FocusActivityType.CODING,
                goal = "mengembangkan ALIVE",
                durationMinutes = 15L,
                startTimestampMs = startMs,
                endTimestampMs = startMs + 15 * 60_000L,
                allowedAppCount = 4,
                bankMinutes = 30L,
                clockMillis = { nowMs },
                animate = false,
                onExtend = { extended += 1 }
            )
        )

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(screen)
        measureAndLayout(screen, 362)

        val ring = collectViews<FocusSetupScreen.Ring>(screen).single()
        assertEquals(1f, ring.fraction, 0.001f)

        val clockTv = collectViews<TextView>(screen).first { it.text.toString() == "15:00" }
        val clockCenterX = (clockTv.parent as View).left + (clockTv.left + clockTv.right) / 2
        val ringCenterX = (ring.left + ring.right) / 2
        assertTrue(
            "Clock center $clockCenterX should align within 4dp of ring center $ringCenterX",
            kotlin.math.abs(clockCenterX - ringCenterX) <= ui.dimens.dp(4)
        )
        assertTrue(
            "Clock must sit cleanly inside the circular ring stroke",
            (clockTv.parent as View).left + clockTv.left >= ring.left + ui.dimens.dp(14) &&
                (clockTv.parent as View).left + clockTv.right <= ring.right - ui.dimens.dp(14)
        )

        val texts = allTexts(screen)
        assertTrue(texts.contains("15:00"))
        assertTrue(texts.contains(idContext.getString(R.string.quest_countdown_left_short)))
        assertTrue(texts.contains("mengembangkan ALIVE"))
        assertTrue(texts.contains("15 ${idContext.getString(R.string.unit_minutes_short)}"))
        assertTrue(texts.contains(idContext.getString(R.string.focus_allowed_count, 4)))
        assertTrue(texts.contains(idContext.getString(R.string.focus_message_value)))
        assertTrue(texts.contains(idContext.getString(R.string.focus_extend_bank_short, 30L)))

        // Redundant explanatory prose must stay removed
        assertFalse(texts.contains(idContext.getString(R.string.focus_allowed_heading)))
        assertFalse(texts.contains(idContext.getString(R.string.focus_monitors)))
        assertFalse(texts.contains(idContext.getString(R.string.focus_next)))
        assertFalse(texts.contains(idContext.getString(R.string.focus_extend_support)))

        val extendBtn = collectViews<AppCompatButton>(screen).single()
        assertEquals(idContext.getString(R.string.focus_extend), extendBtn.text.toString())
        assertTrue(extendBtn.isEnabled)
        extendBtn.performClick()
        assertEquals(1, extended)

        // Advance clock by 65 seconds and fire 1s tick
        nowMs = startMs + 65_000L
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000L))
        assertEquals("13:55", clockTv.text.toString())
        assertEquals((15 * 60_000L - 65_000L).toFloat() / (15 * 60_000L).toFloat(), ring.fraction, 0.01f)

        // Reach expiry cleanly
        nowMs = startMs + 15 * 60_000L
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000L))
        assertEquals("00:00", clockTv.text.toString())
        assertEquals(0f, ring.fraction, 0.001f)
        assertTrue(allTexts(screen).contains(idContext.getString(R.string.quest_countdown_ready_short)))
    }

    @Test
    fun `extend button disables below 15 min bank and every focus type maps to curved alive_member vector`() {
        val screen = ActiveFocusScreen.build(
            context = idContext,
            ui = ui,
            data = ActiveFocusScreen.Data(
                activityType = FocusActivityType.STUDY,
                goal = "Baca bab tiga",
                durationMinutes = 25L,
                startTimestampMs = 1_000_000L,
                endTimestampMs = 1_000_000L + 25 * 60_000L,
                allowedAppCount = 3,
                bankMinutes = 10L,
                clockMillis = { 1_000_000L },
                animate = false
            )
        )
        measureAndLayout(screen, 362)
        val extendBtn = collectViews<AppCompatButton>(screen).single()
        assertFalse(extendBtn.isEnabled)
        assertTrue(allTexts(screen).contains(idContext.getString(R.string.focus_extend_bank_short, 10L)))

        val expected = mapOf(
            FocusActivityType.STUDY to R.drawable.alive_member_study,
            FocusActivityType.WORK to R.drawable.alive_member_work,
            FocusActivityType.CODING to R.drawable.alive_member_coding,
            FocusActivityType.CREATIVE to R.drawable.alive_member_creative,
            FocusActivityType.WRITING to R.drawable.alive_member_writing
        )
        for ((type, resId) in expected) {
            assertEquals(resId, ActiveFocusScreen.memberVectorFor(type))
        }
    }

    @Test
    fun `renders non-blank NATIVE bitmap preview on groundSky`() {
        val startMs = 1_000_000L
        val screen = ActiveFocusScreen.build(
            context = idContext,
            ui = ui,
            data = ActiveFocusScreen.Data(
                activityType = FocusActivityType.CODING,
                goal = "mengembangkan ALIVE",
                durationMinutes = 15L,
                startTimestampMs = startMs,
                endTimestampMs = startMs + 15 * 60_000L,
                allowedAppCount = 4,
                bankMinutes = 30L,
                clockMillis = { startMs + 7 * 60_000L },
                animate = false
            )
        )
        val pad = ui.dimens.dp(24)
        val canvasHost = FrameLayout(idContext).apply {
            setBackgroundColor(ui.palette.groundSky)
            setPadding(pad, pad, pad, pad)
            addView(screen)
        }
        measureAndLayout(canvasHost, 411)

        val bmp = Bitmap.createBitmap(canvasHost.measuredWidth, canvasHost.measuredHeight, Bitmap.Config.ARGB_8888)
        canvasHost.draw(Canvas(bmp))
        assertTrue(bmp.width > 0 && bmp.height > 0)

        val sampled = HashSet<Int>()
        for (y in 0 until bmp.height step 2) {
            for (x in 0 until bmp.width step 2) {
                sampled.add(bmp.getPixel(x, y))
            }
        }
        assertTrue(ui.palette.groundSky in sampled)
        assertTrue(ui.palette.fieldLilac in sampled)
        assertTrue(ui.palette.tile in sampled)
        assertTrue(ui.palette.flatMint in sampled)
        assertTrue(ui.palette.fieldButter in sampled)
        assertTrue(ui.palette.ink in sampled)
    }

    private fun measureAndLayout(view: View, widthDp: Int) {
        val density = idContext.resources.displayMetrics.density
        val wPx = (widthDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private inline fun <reified T : View> collectViews(root: View): List<T> =
        collectViewsOfType(root, T::class.java)

    private fun <T : View> collectViewsOfType(root: View, clazz: Class<T>): List<T> {
        val result = mutableListOf<T>()
        fun walk(v: View) {
            if (clazz.isInstance(v)) {
                @Suppress("UNCHECKED_CAST")
                result.add(v as T)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        return result
    }

    private fun allTexts(root: View): List<String> =
        collectViews<TextView>(root).map { it.text.toString() }
}
