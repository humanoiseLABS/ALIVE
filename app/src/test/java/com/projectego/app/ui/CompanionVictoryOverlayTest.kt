package com.projectego.app.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.projectego.app.R
import com.projectego.app.data.CharacterFamilyRepository.VictoryEvent
import com.projectego.app.data.CharacterFamilyRepository.VictorySource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionVictoryOverlayTest {

    private lateinit var activity: Activity
    private lateinit var themedContext: ContextThemeWrapper
    private lateinit var ui: AliveComponents
    private lateinit var host: FrameLayout

    private var originalDurationScale = 1f

    @Before
    fun setUp() {
        originalDurationScale = durationScale()
        durationScale(1f)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        themedContext = ContextThemeWrapper(activity, R.style.Theme_ProjectEgo)
        Settings.Global.putFloat(themedContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        ui = AliveComponents(themedContext)
        host = FrameLayout(themedContext)
        activity.setContentView(host)
        layoutHost(widthPx = 1080, heightPx = 2200)
    }

    @After
    fun tearDown() {
        durationScale(originalDurationScale)
        Settings.Global.putFloat(themedContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        host.removeAllViews()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `quest synergy ceremony renders milestone flares sub-tiles bond progress and queued cta`() {
        val event = VictoryEvent(
            eventId = "quest:q1:a1",
            source = VictorySource.QUEST,
            memberId = "creative",
            companionId = "creative",
            title = "Sketch 15 min",
            baseMinutes = 15L,
            synergyBonusMinutes = 2L,
            durationMinutes = 0L,
            synergyMatched = true,
            newlyAwakened = true,
            bondDelta = 2,
            bondXpBefore = 4,
            bondXpAfter = 6,
            rankBefore = 1,
            rankAfter = 2,
            growthStageUp = true,
            growthLevelAfter = 2
        )

        val overlay = CompanionVictoryOverlay(themedContext, ui)
        val dismissed = mutableListOf<VictoryEvent>()
        overlay.show(host, event, remainingQueuedCount = 2) { dismissed.add(it) }
        layoutHost(widthPx = 1800, heightPx = 2200)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)

        val texts = collectTexts(overlay)
        assertTrue(texts.contains(themedContext.getString(R.string.victory_kicker_quest)))
        assertTrue(texts.contains("Sketch 15 min"))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_flare_awakened)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_flare_rank_up, 2)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_flare_level_up, 2)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_synergy_active, "Creative")))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_bank_label)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_minutes_gain, 17)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_synergy_note, 2)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_bond_label, "Creative")))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_bond_delta, 2)))
        assertTrue(texts.contains(themedContext.getString(R.string.bond_rank_line, 2, themedContext.getString(R.string.bond_rank_2_name), 1, 10)))

        val scrim = overlay.getChildAt(0)
        assertEquals("scrim must span full overlay width", overlay.width, scrim.width)
        assertEquals("scrim must span full overlay height", overlay.height, scrim.height)

        val card = overlay.findViewWithTag<View>(CompanionVictoryOverlay.TAG_CARD)
        assertNotNull(card)
        assertTrue(card is AliveLiquidGlassCard)
        val maxPx = (360f * themedContext.resources.displayMetrics.density).toInt() + 2
        assertTrue("victory card width (${card!!.width}) must cap at 360dp ($maxPx)", card.width in 1..maxPx)

        val cta = findTextView(overlay, themedContext.getString(R.string.victory_cta_next, 2))
        assertNotNull(cta)
        assertEquals(CompanionVictoryOverlay.TAG_CTA, cta!!.tag)

        assertEquals(0.1f, overlay.currentBondProgressShare(), 0.02f)

        // Rapid double click fires onDismiss strictly once and detaches overlay.
        cta.performClick()
        cta.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertEquals(listOf(event), dismissed)
        assertNull(overlay.parent)
        assertNull(overlay.currentEvent)
    }

    @Test
    fun `non-synergy focus renders duration without flares or synergy pill and supports done cta`() {
        val event = VictoryEvent(
            eventId = "focus:12345",
            source = VictorySource.FOCUS,
            memberId = "coding",
            companionId = "creative",
            title = "Refactor repository",
            baseMinutes = 0L,
            synergyBonusMinutes = 0L,
            durationMinutes = 25L,
            synergyMatched = false,
            newlyAwakened = false,
            bondDelta = 1,
            bondXpBefore = 1,
            bondXpAfter = 2,
            rankBefore = 1,
            rankAfter = 1,
            growthStageUp = false,
            growthLevelAfter = 1
        )

        val overlay = CompanionVictoryOverlay(themedContext, ui)
        val dismissed = mutableListOf<VictoryEvent>()
        overlay.show(host, event, remainingQueuedCount = 0) { dismissed.add(it) }
        layoutHost(widthPx = 1080, heightPx = 2200)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)

        val texts = collectTexts(overlay)
        assertTrue(texts.contains(themedContext.getString(R.string.victory_kicker_focus)))
        assertTrue(texts.contains("Refactor repository"))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_focus_label)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_minutes_duration, 25)))
        assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_focus_note, 25)))
        assertFalse(texts.contains(themedContext.getString(R.string.victory_flare_awakened)))
        assertFalse(texts.any { it.startsWith("RANK UP") })
        assertFalse(texts.any { it.startsWith("LEVEL ") })
        assertFalse(texts.any { it.startsWith("SYNERGY ACTIVE") })

        val cta = findTextView(overlay, themedContext.getString(R.string.victory_cta_done))
        assertNotNull(cta)
        assertEquals(0.4f, overlay.currentBondProgressShare(), 0.02f)

        // Tapping outer scrim dismisses cleanly once.
        overlay.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)
        assertEquals(listOf(event), dismissed)
        assertNull(overlay.parent)
    }

    @Test
    fun `reduced motion snaps bond bar immediately and master rank renders full progress`() {
        durationScale(0f)
        Settings.Global.putFloat(themedContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        val masterEvent = VictoryEvent(
            eventId = "quest:q2:a2",
            source = VictorySource.QUEST,
            memberId = "creative",
            companionId = "creative",
            title = "Final mural",
            baseMinutes = 15L,
            synergyBonusMinutes = 2L,
            durationMinutes = 0L,
            synergyMatched = true,
            newlyAwakened = false,
            bondDelta = 2,
            bondXpBefore = 34,
            bondXpAfter = 36,
            rankBefore = 3,
            rankAfter = 4,
            growthStageUp = false,
            growthLevelAfter = 3
        )

        val overlay = CompanionVictoryOverlay(themedContext, ui)
        var dismissCount = 0
        overlay.show(host, masterEvent) { dismissCount++ }
        layoutHost(widthPx = 1080, heightPx = 2200)

        // Immediately at full Master share before advancing any animator frame.
        assertEquals(1.0f, overlay.currentBondProgressShare(), 0.001f)
        val texts = collectTexts(overlay)
        assertTrue(texts.contains(themedContext.getString(R.string.bond_rank_master_line, 4, themedContext.getString(R.string.bond_rank_4_name))))

        overlay.dismiss(animated = false)
        assertEquals(1, dismissCount)
        assertNull(overlay.parent)
    }

    @Test
    fun `detaching overlay mid-ceremony cancels animators and beat-2 haptic runnable safely`() {
        val event = VictoryEvent(
            eventId = "quest:q3:a3",
            source = VictorySource.QUEST,
            memberId = "creative",
            companionId = "creative",
            title = "Interrupted celebration",
            baseMinutes = 10L,
            synergyBonusMinutes = 2L,
            durationMinutes = 0L,
            synergyMatched = true,
            newlyAwakened = true,
            bondDelta = 2,
            bondXpBefore = 0,
            bondXpAfter = 2,
            rankBefore = 1,
            rankAfter = 1,
            growthStageUp = false,
            growthLevelAfter = 1
        )

        val overlay = CompanionVictoryOverlay(themedContext, ui)
        var dismissed = 0
        overlay.show(host, event) { dismissed++ }
        layoutHost(widthPx = 1080, heightPx = 2200)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(80L, TimeUnit.MILLISECONDS)

        // Detach abruptly before 260ms beat-2 haptic and 650ms bar sweep finish.
        host.removeAllViews()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1000L, TimeUnit.MILLISECONDS)

        assertEquals(0, dismissed)
        assertFalse(overlay.hasPendingTimersOrAnimatorsForTesting())
    }

    @Test
    fun `card wraps content tightly without full-height stretch and confetti layer stays non-clickable`() {
        val event = VictoryEvent(
            eventId = "quest:compact-bounds",
            source = VictorySource.QUEST,
            memberId = "mindfulness",
            companionId = "creative",
            title = "Meditasi 10 Menit",
            baseMinutes = 10L,
            synergyBonusMinutes = 2L,
            durationMinutes = 0L,
            synergyMatched = true,
            newlyAwakened = true,
            bondDelta = 2,
            bondXpBefore = 1,
            bondXpAfter = 3,
            rankBefore = 1,
            rankAfter = 1,
            growthStageUp = false,
            growthLevelAfter = 1
        )

        val overlay = CompanionVictoryOverlay(themedContext, ui)
        overlay.show(host, event, remainingQueuedCount = 0) {}
        layoutHost(widthPx = 1080, heightPx = 2400)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(900L, TimeUnit.MILLISECONDS)

        val card = overlay.findViewWithTag<View>(CompanionVictoryOverlay.TAG_CARD)
        assertNotNull(card)
        assertTrue(
            "victory card (${card!!.height}px) must wrap content instead of stretching toward host height (${host.height}px)",
            card.height in 1..(host.height * 3 / 4)
        )

        val bar = overlay.findViewWithTag<View>(CompanionVictoryOverlay.TAG_BOND_BAR)
        assertNotNull(bar)
        val expectedBarMinPx = (10f * themedContext.resources.displayMetrics.density).toInt()
        assertTrue("liquid bond bar height (${bar!!.height}) must be >= $expectedBarMinPx", bar.height >= expectedBarMinPx)

        val confetti = overlay.getChildAt(overlay.childCount - 1)
        assertEquals(overlay.width, confetti.width)
        assertEquals(overlay.height, confetti.height)
        assertFalse("confetti overlay must not steal clicks", confetti.isClickable)
        assertFalse("confetti overlay must not take focus", confetti.isFocusable)
    }

    @Test
    fun `ceremony animates mid-flight progression and settles cleanly without leaked timers`() {
        val wasPaused = ShadowChoreographer.isPaused()
        val prevDelay = ShadowChoreographer.getFrameDelay()
        try {
            layoutHost(widthPx = 1080, heightPx = 2400)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            ShadowChoreographer.setPaused(true)
            ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))

            val event = VictoryEvent(
                eventId = "quest:mid-flight",
                source = VictorySource.QUEST,
                memberId = "mindfulness",
                companionId = "creative",
                title = "Meditasi 10 Menit",
                baseMinutes = 10L,
                synergyBonusMinutes = 2L,
                durationMinutes = 0L,
                synergyMatched = true,
                newlyAwakened = true,
                bondDelta = 2,
                bondXpBefore = 1,
                bondXpAfter = 3,
                rankBefore = 1,
                rankAfter = 1,
                growthStageUp = false,
                growthLevelAfter = 1
            )

            val overlay = CompanionVictoryOverlay(themedContext, ui)
            overlay.show(host, event, remainingQueuedCount = 0) {}

            // Step 22 frames (~352ms): bar is mid-sweep (0.2 -> 0.6) and confetti is active
            repeat(22) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
            }
            val midShare = overlay.currentBondProgressShare()
            assertTrue("bar share mid-flight ($midShare) should be progressing between 0.2 and 0.6", midShare in 0.21f..0.59f)
            assertTrue("confetti/aura animators should still be running at 352ms", overlay.hasPendingTimersOrAnimatorsForTesting())

            // Advance past full ceremony duration (~2.56s) and verify full clean settlement
            repeat(150) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(16L, TimeUnit.MILLISECONDS)
            }
            assertEquals(0.6f, overlay.currentBondProgressShare(), 0.02f)
            assertFalse("all finite ceremony animators must settle cleanly", overlay.hasPendingTimersOrAnimatorsForTesting())
            val texts = collectTexts(overlay)
            assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_minutes_gain, 12)))
            assertTrue(texts.contains(themedContext.getString(R.string.victory_tile_bond_delta, 2)))
        } finally {
            ShadowChoreographer.setPaused(wasPaused)
            ShadowChoreographer.setFrameDelay(prevDelay)
        }
    }

    private fun layoutHost(widthPx: Int, heightPx: Int) {
        host.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, widthPx, heightPx)
    }

    private fun collectTexts(view: View): List<String> = buildList {
        if (view is TextView && view.text != null) add(view.text.toString())
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) addAll(collectTexts(view.getChildAt(i)))
        }
    }

    private fun findTextView(view: View, text: String): TextView? {
        if (view is TextView && view.text?.toString() == text) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findTextView(view.getChildAt(i), text)?.let { return it }
            }
        }
        return null
    }

    private fun durationScale(): Float =
        ValueAnimator::class.java.getDeclaredMethod("getDurationScale")
            .apply { isAccessible = true }.invoke(null) as Float

    private fun durationScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(null, scale)
    }
}
