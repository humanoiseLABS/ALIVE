package com.projectego.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class CriticalTimeCardTest {

    private lateinit var themedContext: Context
    private lateinit var ui: AliveComponents

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(base.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        themedContext = ContextThemeWrapper(base, R.style.Theme_ALIVE_CriticalAlert)
        ui = AliveComponents(themedContext)
    }

    @Test
    fun `renders CriticalTimeCard with primary button, quota pill, capsule bar, and no decorative accents`() {
        var dismissed = 0
        val card = CriticalTimeCard.build(
            context = themedContext,
            ui = ui,
            remainingMinutes = 28L,
            limitMinutes = 200L
        ) { dismissed += 1 }

        val measured = measureAndLayout(card, widthDp = 362)
        val texts = allTexts(measured)
        assertTrue(texts.contains(themedContext.getString(R.string.chart_alert_eyebrow)))
        assertTrue(texts.contains("14%"))
        assertTrue(texts.contains("28"))
        assertTrue(texts.contains(themedContext.getString(AliveStates.alertHeadline(28L), 28L)))
        assertTrue(texts.contains(themedContext.getString(R.string.alert_support)))

        val bars = collectViews<CriticalCardChrome.CapsuleProgressBar>(measured)
        assertEquals(1, bars.size)
        assertEquals(28f / 200f, bars.first().fraction, 0.001f)
        assertTrue(bars.first().height > 0)

        val images = collectViews<ImageView>(measured)
        assertEquals("Only companion mark + hourglass icon should render (no decorative accents)", 2, images.size)

        val buttons = collectViews<AppCompatButton>(measured)
        assertEquals(1, buttons.size)
        val cta = buttons.first()
        assertEquals(themedContext.getString(R.string.action_back), cta.text.toString())
        assertTrue("CTA height ${cta.height} must satisfy touchMin ${ui.dimens.touchMin}", cta.height >= ui.dimens.touchMin)
        cta.performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `warning card header keeps eyebrow visible and countdown pill unclipped on narrow Indonesian modal`() {
        val idContext = localizedContext("in")
        val idUi = AliveComponents(idContext)
        val warning = CriticalWarningCard.build(
            context = idContext,
            ui = idUi,
            remainingMinutes = 28L
        ) {}

        // 88% of 360dp compact phone width = ~316dp
        val measured = measureAndLayout(warning, widthDp = 316, context = idContext)
        val eyebrowText = idContext.getString(R.string.alert_warning_eyebrow)
        val countdownLabel = idContext.getString(R.string.alert_warning_countdown)
        val textViews = collectViews<TextView>(measured)
        val eyebrow = textViews.first { it.text.toString() == eyebrowText }
        val countdownUnit = textViews.first { it.text.toString() == countdownLabel }
        val pill = countdownUnit.parent as View

        assertTrue("Eyebrow width ${eyebrow.width} must stay visible next to countdown pill", eyebrow.width > 0)
        assertEquals("Eyebrow should stay single-line on 316dp width", 1, eyebrow.lineCount)
        assertTrue("Countdown pill right ${pill.right} must not clip past plate width", pill.width < measured.width / 2)
        assertEquals("Countdown label must stay single-line", 1, countdownUnit.lineCount)

        val images = collectViews<ImageView>(measured)
        assertEquals("Only companion mark + hourglass icon should render (no decorative accents)", 2, images.size)
    }

    @Test
    fun `hides quota pill and meter when limit is non-positive`() {
        val card = CriticalTimeCard.build(
            context = themedContext,
            ui = ui,
            remainingMinutes = 15L,
            limitMinutes = 0L
        ) {}

        val measured = measureAndLayout(card, widthDp = 362)
        val texts = allTexts(measured)
        assertFalse(texts.any { it.endsWith("%") })
        assertEquals(0, collectViews<CriticalCardChrome.CapsuleProgressBar>(measured).size)
    }

    @Test
    fun `warning card steps countdown bar and finishes cleanly when attached with reduced motion`() {
        Settings.Global.putFloat(themedContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val uiReduced = AliveComponents(themedContext)
        assertFalse(uiReduced.animationsAllowed)

        var continued = 0
        val warning = CriticalWarningCard.build(
            context = themedContext,
            ui = uiReduced,
            remainingMinutes = 28L
        ) { continued += 1 }

        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val host = FrameLayout(themedContext).apply { addView(warning) }
        controller.get().setContentView(host)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000L))
        val afterOneSec = collectViews<TextView>(warning).map { it.text.toString() }
        assertTrue(afterOneSec.contains("4"))
        val bar = collectViews<CriticalCardChrome.CapsuleProgressBar>(warning).singleOrNull()
        assertNotNull(bar)
        assertEquals(0.8f, bar!!.fraction, 0.001f)
        assertEquals(0, continued)

        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis((CriticalWarningCard.LOCK_SECONDS - 1) * 1_000L + 100L)
        )
        assertEquals(1, continued)
        assertEquals(0f, bar.fraction, 0.001f)
    }

    @Test
    fun `renders non-blank NATIVE bitmaps for warning and critical cards`() {
        val idContext = localizedContext("in")
        val idUi = AliveComponents(idContext)

        val warning = CriticalWarningCard.build(
            context = idContext,
            ui = idUi,
            remainingMinutes = 28L
        ) {}
        val warningBitmap = renderToBitmap(warning, widthDp = 316, context = idContext, uiRef = idUi)
        assertTrue(warningBitmap.width > 0 && warningBitmap.height > 0)
        assertTrue(
            "Warning bitmap should paint the urgent shortMid border",
            containsColor(warningBitmap, idUi.palette.shortMid)
        )

        val card = CriticalTimeCard.build(
            context = idContext,
            ui = idUi,
            remainingMinutes = 28L,
            limitMinutes = 200L
        ) {}
        val cardBitmap = renderToBitmap(card, widthDp = 316, context = idContext, uiRef = idUi)
        assertTrue(cardBitmap.width > 0 && cardBitmap.height > 0)
        assertTrue(
            "CriticalTimeCard bitmap should paint white card surface",
            containsColor(cardBitmap, idUi.palette.white)
        )
        assertTrue(
            "CriticalTimeCard bitmap should paint shortPale hero board",
            containsColor(cardBitmap, idUi.palette.shortPale)
        )
    }

    private fun containsColor(bitmap: Bitmap, targetColor: Int): Boolean {
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                if (bitmap.getPixel(x, y) == targetColor) return true
            }
        }
        return false
    }

    private fun localizedContext(lang: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_ALIVE_CriticalAlert)
    }

    private fun measureAndLayout(view: View, widthDp: Int, context: Context = themedContext): View {
        val density = context.resources.displayMetrics.density
        val wPx = (widthDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun renderToBitmap(
        view: View,
        widthDp: Int,
        context: Context = themedContext,
        uiRef: AliveComponents = ui
    ): Bitmap {
        val pad = uiRef.dimens.dp(24)
        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(122, 0, 0, 0))
            setPadding(pad, pad, pad, pad)
            addView(view)
        }
        measureAndLayout(backdrop, widthDp + 48, context)
        val bmp = Bitmap.createBitmap(backdrop.measuredWidth, backdrop.measuredHeight, Bitmap.Config.ARGB_8888)
        backdrop.draw(Canvas(bmp))
        return bmp
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
