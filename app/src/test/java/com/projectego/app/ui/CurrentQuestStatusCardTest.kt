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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestAttempt
import com.projectego.app.model.QuestAttemptState
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty
import com.projectego.app.model.VerificationMethod
import com.projectego.app.model.VerificationOutcome
import com.projectego.app.model.VerificationResult
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
class CurrentQuestStatusCardTest {

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
    fun `renders Focus-style circular countdown ring and auto-unlocks Saya selesai at 00_00`() {
        val startMs = 1_000_000L
        var nowMs = startMs
        var declared = 0
        var success = 0

        val quest = Quest(
            id = "clean_room",
            title = "Bereskan Kamarmu",
            description = "Rapikan meja dan tempat tidur.",
            category = QuestCategory.CLEANING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 20L
        )
        val attempt = QuestAttempt(
            attemptId = "a-1",
            questId = quest.id,
            method = VerificationMethod.PHYSICAL,
            state = QuestAttemptState.IN_PROGRESS,
            committedTimestamp = startMs,
            committedDurationMinutes = 20L,
            activityStartTimestamp = startMs
        )

        val card = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = attempt,
                quest = quest,
                withHeader = true,
                weight = SurfaceWeight.MAJOR,
                reclaimedAvailableMinutes = 0L,
                claimedTotalMinutes = 20L,
                onDeclareDone = {
                    declared += 1
                    true
                },
                onDeclareSuccess = { success += 1 },
                onDeclareFailed = {},
                onContinue = {},
                onRewardPresented = {},
                nowProvider = { nowMs }
            )
        )

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val host = FrameLayout(idContext).apply { addView(card) }
        controller.get().setContentView(host)
        measureAndLayout(host, 362)

        val ring = collectViews<FocusSetupScreen.Ring>(card).singleOrNull()
        assertNotNull("Expected circular Focus-style ring inside active mission card", ring)
        assertEquals(1.0f, ring!!.fraction, 0.001f)

        val clockTv = collectViews<TextView>(card).first { it.text.toString() == "20:00" }
        val ringParent = ring.parent as View
        val clockCenterX = (clockTv.parent as View).left + (clockTv.left + clockTv.right) / 2
        val ringCenterX = (ring.left + ring.right) / 2
        assertTrue(
            "Clock center $clockCenterX should align within 4dp of ring center $ringCenterX (ringParentW=${ringParent.width})",
            kotlin.math.abs(clockCenterX - ringCenterX) <= ui.dimens.dp(4)
        )
        assertTrue(
            "Clock must sit cleanly inside the circular ring stroke",
            (clockTv.parent as View).left + clockTv.left >= ring.left + ui.dimens.dp(12) &&
                (clockTv.parent as View).left + clockTv.right <= ring.right - ui.dimens.dp(12)
        )

        val initialTexts = allTexts(card)
        assertTrue(initialTexts.contains("20:00"))
        assertTrue(initialTexts.contains(idContext.getString(R.string.quest_countdown_left_short)))
        assertFalse(initialTexts.contains(idContext.getString(R.string.quest_gate_left, 20L)))
        assertFalse(initialTexts.contains(idContext.getString(AliveStates.attemptHeadline(QuestAttemptState.IN_PROGRESS))))
        assertFalse(initialTexts.contains(idContext.getString(R.string.quest_auto_settle_note)))

        val declareBtn = collectViews<AppCompatButton>(card).single()
        assertEquals(idContext.getString(R.string.quest_declare_done), declareBtn.text.toString())
        assertFalse(declareBtn.isEnabled)
        assertTrue(declareBtn.height >= ui.dimens.touchMin)

        // Advance clock by 65 seconds and fire 1s tick
        nowMs = startMs + 65_000L
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000L))
        val midTexts = allTexts(card)
        assertTrue(midTexts.contains("18:55"))
        assertFalse(midTexts.contains(idContext.getString(R.string.quest_gate_left, 19L)))
        assertFalse(declareBtn.isEnabled)

        // Finish full 20 minute window
        nowMs = startMs + 20 * 60_000L
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000L))
        val endTexts = allTexts(card)
        assertTrue(endTexts.contains("00:00"))
        assertTrue(endTexts.contains(idContext.getString(R.string.quest_countdown_ready_short)))
        assertFalse(endTexts.contains(idContext.getString(R.string.quest_gate_open)))
        assertTrue(declareBtn.isEnabled)
        assertEquals(0f, ring.fraction, 0.001f)

        declareBtn.performClick()
        assertEquals(1, declared)
        assertEquals(1, success)
    }

    @Test
    fun `withHeader false hides duplicate eyebrow inside active mission card`() {
        val startMs = 2_000_000L
        val quest = Quest(
            id = "run_card",
            title = "Lari Pagi",
            description = "Lari santai selama 15 menit.",
            category = QuestCategory.EXERCISE,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 15,
            rewardMinutes = 15L
        )
        val attempt = QuestAttempt(
            attemptId = "a-minimal",
            questId = quest.id,
            method = VerificationMethod.PHYSICAL,
            state = QuestAttemptState.IN_PROGRESS,
            committedTimestamp = startMs,
            committedDurationMinutes = 15L,
            activityStartTimestamp = startMs
        )

        val card = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = attempt,
                quest = quest,
                withHeader = false,
                reclaimedAvailableMinutes = 0L,
                claimedTotalMinutes = 15L,
                onDeclareDone = { false },
                onDeclareSuccess = {},
                onDeclareFailed = {},
                onContinue = {},
                onRewardPresented = {},
                nowProvider = { startMs }
            )
        )
        measureAndLayout(card, 362)
        val texts = allTexts(card)
        assertFalse(texts.contains(idContext.getString(R.string.quest_current)))
        assertFalse(texts.contains(idContext.getString(AliveStates.categoryLabel(QuestCategory.EXERCISE))))
        assertTrue(texts.contains("Lari Pagi"))
        assertTrue(texts.contains("15:00"))
    }

    @Test
    fun `digital in-progress mission also renders circular countdown ring and action_continue CTA`() {
        val startMs = 5_000_000L
        val quest = Quest(
            id = "deep_code",
            title = "Fokus Ngoding",
            description = "Bangun fitur tanpa distraksi.",
            category = QuestCategory.CODING,
            difficulty = QuestDifficulty.MEDIUM,
            estimatedDurationMinutes = 25,
            rewardMinutes = 25L
        )
        val attempt = QuestAttempt(
            attemptId = "a-2",
            questId = quest.id,
            method = VerificationMethod.DIGITAL,
            state = QuestAttemptState.IN_PROGRESS,
            committedTimestamp = startMs,
            committedDurationMinutes = 25L,
            activityStartTimestamp = startMs
        )
        var continued = 0

        val card = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = attempt,
                quest = quest,
                withHeader = true,
                reclaimedAvailableMinutes = 10L,
                claimedTotalMinutes = 25L,
                onDeclareDone = { false },
                onDeclareSuccess = {},
                onDeclareFailed = {},
                onContinue = { continued += 1 },
                onRewardPresented = {},
                nowProvider = { startMs }
            )
        )

        measureAndLayout(card, 362)
        assertEquals(1, collectViews<FocusSetupScreen.Ring>(card).size)
        assertTrue(allTexts(card).contains("25:00"))
        val btn = collectViews<AppCompatButton>(card).single()
        assertEquals(idContext.getString(R.string.action_continue), btn.text.toString())
        btn.performClick()
        assertEquals(1, continued)
    }

    @Test
    fun `rewarded and failed cards omit countdown ring and render payout and failure reason`() {
        val quest = Quest(
            id = "walk",
            title = "Jalan Pagi",
            description = "Keluar rumah sebentar tanpa layar.",
            category = QuestCategory.WALKING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 15,
            rewardMinutes = 15L
        )
        var celebrated = 0
        val rewardedCard = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = QuestAttempt(
                    attemptId = "a-3",
                    questId = quest.id,
                    method = VerificationMethod.PHYSICAL,
                    state = QuestAttemptState.REWARDED,
                    committedTimestamp = 100L,
                    committedDurationMinutes = 15L,
                    rewardMinutes = 15L
                ),
                quest = quest,
                withHeader = true,
                reclaimedAvailableMinutes = 35L,
                claimedTotalMinutes = 15L,
                onDeclareDone = { false },
                onDeclareSuccess = {},
                onDeclareFailed = {},
                onContinue = {},
                onRewardPresented = { celebrated += 1 }
            )
        )
        measureAndLayout(rewardedCard, 362)
        assertEquals(0, collectViews<FocusSetupScreen.Ring>(rewardedCard).size)
        assertEquals(1, celebrated)
        assertTrue(allTexts(rewardedCard).contains(idContext.getString(R.string.unit_minutes_gain, 15L)))

        val failedCard = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = QuestAttempt(
                    attemptId = "a-4",
                    questId = quest.id,
                    method = VerificationMethod.PHYSICAL,
                    state = QuestAttemptState.FAILED,
                    committedTimestamp = 100L,
                    committedDurationMinutes = 15L,
                    verificationResult = VerificationResult(
                        outcome = VerificationOutcome.FAIL,
                        reason = "Declared done after 3 of 15 committed minutes",
                        evaluatedTimestamp = 200L
                    )
                ),
                quest = quest,
                withHeader = true,
                reclaimedAvailableMinutes = 0L,
                claimedTotalMinutes = 0L,
                onDeclareDone = { false },
                onDeclareSuccess = {},
                onDeclareFailed = {},
                onContinue = {},
                onRewardPresented = {}
            )
        )
        measureAndLayout(failedCard, 362)
        assertEquals(0, collectViews<FocusSetupScreen.Ring>(failedCard).size)
        assertTrue(allTexts(failedCard).contains(idContext.getString(AliveStates.attemptHeadline(QuestAttemptState.FAILED))))
    }

    @Test
    fun `renders non-blank NATIVE bitmap preview on groundButter`() {
        val startMs = 1_000_000L
        val elapsedNowMs = startMs + 3 * 60_000L + 18_000L // 16:42 remaining
        val quest = Quest(
            id = "clean_room",
            title = "Bereskan Kamarmu",
            description = "Rapikan meja dan tempat tidur.",
            category = QuestCategory.CLEANING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 20L
        )
        val attempt = QuestAttempt(
            attemptId = "a-preview",
            questId = quest.id,
            method = VerificationMethod.PHYSICAL,
            state = QuestAttemptState.IN_PROGRESS,
            committedTimestamp = startMs,
            committedDurationMinutes = 20L,
            activityStartTimestamp = startMs
        )

        val card = CurrentQuestStatusCard.build(
            context = idContext,
            ui = ui,
            data = CurrentQuestStatusCard.Data(
                attempt = attempt,
                quest = quest,
                withHeader = true,
                weight = SurfaceWeight.MAJOR,
                reclaimedAvailableMinutes = 0L,
                claimedTotalMinutes = 20L,
                onDeclareDone = { false },
                onDeclareSuccess = {},
                onDeclareFailed = {},
                onContinue = {},
                onRewardPresented = {},
                nowProvider = { elapsedNowMs }
            )
        )

        val pad = ui.dimens.dp(24)
        val canvasHost = FrameLayout(idContext).apply {
            setBackgroundColor(ui.palette.groundButter)
            setPadding(pad, pad, pad, pad)
            addView(card)
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
        assertTrue(sampled.size > 4)
        assertTrue(ui.palette.groundButter in sampled)
        assertTrue(ui.palette.tile in sampled)
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
