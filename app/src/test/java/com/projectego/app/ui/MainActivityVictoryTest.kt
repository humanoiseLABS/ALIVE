package com.projectego.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.CharacterFamilyRepository
import com.projectego.app.data.TimeBankRepository
import com.projectego.app.data.WelcomeGate
import com.projectego.app.domain.CharacterGrowth
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
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
import org.robolectric.shadows.ShadowAppOpsManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityVictoryTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        WelcomeGate.markDone(appContext)
    }

    @Test
    fun `expired clean creative focus settles in onResume without usage access and consumes once`() {
        val now = System.currentTimeMillis()
        seedFocusPrefs(
            activityType = FocusActivityType.CREATIVE,
            goal = "UI motion pass",
            durationMinutes = 25L,
            startTimestamp = now - (25L * 60_000L) - 5_000L,
            endTimestamp = now - 5_000L,
            focusSessionId = "focus-test-1"
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)

        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertEquals(2L, TimeBankRepository(appContext).getTimeBank().earnedMinutes)
        assertEquals(2, CharacterFamilyRepository(appContext).bondXp("creative"))

        val overlay = findVictoryOverlay(content)
        assertNotNull("CompanionVictoryOverlay should appear after Focus expiration onResume", overlay)
        overlay!!
        val texts = collectTexts(overlay)
        assertTrue(texts.contains("UI motion pass"))
        assertTrue(texts.contains(activity.getString(R.string.victory_tile_minutes_gain, 2)))
        assertTrue(texts.contains(activity.getString(R.string.victory_tile_bond_delta, 2)))

        val cta = findTextView(overlay, activity.getString(R.string.victory_cta_done))
        assertNotNull(cta)
        cta!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertNull(findVictoryOverlay(content))
        assertEquals(0, CharacterFamilyRepository(appContext).pendingVictoryCount())

        // Subsequent resume does not re-present consumed celebration.
        controller.pause().resume()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)
        assertNull(findVictoryOverlay(content))
    }

    @Test
    fun `queued pending victories chain sequentially on cta dismiss`() {
        val family = CharacterFamilyRepository(appContext)
        family.recordQuestCompletionLive(
            attemptId = "q1-a1",
            category = QuestCategory.CODING,
            questTitle = "Build module",
            baseRewardMinutes = 10L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE
        )
        family.recordFocusCompletionLive(
            dedupKey = "focus:f1",
            activityType = FocusActivityType.CREATIVE,
            durationMinutes = 25L,
            goalTitle = "Paint cover",
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE,
            eligible = true
        )
        assertEquals(2, family.pendingVictoryCount())

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        val firstOverlay = findVictoryOverlay(content)
        assertNotNull(firstOverlay)
        assertTrue(collectTexts(firstOverlay!!).contains("Build module"))
        val nextBtn = findTextView(firstOverlay, activity.getString(R.string.victory_cta_next, 1))
        assertNotNull(nextBtn)
        nextBtn!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        val secondOverlay = findVictoryOverlay(content)
        assertNotNull("Second queued victory must present immediately after first dismisses", secondOverlay)
        assertTrue(collectTexts(secondOverlay!!).contains("Paint cover"))
        val doneBtn = findTextView(secondOverlay, activity.getString(R.string.victory_cta_done))
        assertNotNull(doneBtn)
        doneBtn!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertNull(findVictoryOverlay(content))
        assertEquals(0, CharacterFamilyRepository(appContext).pendingVictoryCount())
    }

    @Test
    fun `back press dismisses victory overlay without closing underlying overlay or finishing activity`() {
        grantUsageAccess()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        // Open underlying Start Focus overlay via Beranda's Today's Focus card.
        assertTrue(clickClickableAncestorWithText(content, activity.getString(R.string.beranda_focus_start)))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        val overlayContainer = readField<View>(activity, "overlayContainer")
        assertEquals(View.VISIBLE, overlayContainer.visibility)

        // Enqueue victory while RESUMED -> live listener surfaces CompanionVictoryOverlay on top.
        CharacterFamilyRepository(appContext).recordQuestCompletionLive(
            attemptId = "back-1",
            category = QuestCategory.CREATIVE,
            questTitle = "Sketch wireframe",
            baseRewardMinutes = 10L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE
        )
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertNotNull(findVictoryOverlay(content))

        // First Back dismisses victory overlay only; underlying panel and Activity stay open.
        activity.onBackPressedDispatcher.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertNull(findVictoryOverlay(content))
        assertEquals(0, CharacterFamilyRepository(appContext).pendingVictoryCount())
        assertEquals(View.VISIBLE, overlayContainer.visibility)
        assertFalse(activity.isFinishing)

        // Second Back closes underlying overlay without finishing Activity.
        activity.onBackPressedDispatcher.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)
        assertTrue(overlayContainer.visibility != View.VISIBLE)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `active focus session expiring in foreground closes FocusSessionPanel and surfaces victory card`() {
        grantUsageAccess()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200L, TimeUnit.MILLISECONDS)

        // Pause after cold init so setup overhead never eats into the foreground expiry window.
        controller.pause()
        val now = System.currentTimeMillis()
        seedFocusPrefs(
            activityType = FocusActivityType.CREATIVE,
            goal = "Finish shader",
            durationMinutes = 15L,
            startTimestamp = now - (15L * 60_000L) + 3_000L,
            endTimestamp = now + 3_000L,
            focusSessionId = "focus-fg-1"
        )
        controller.resume()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(150L, TimeUnit.MILLISECONDS)

        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        // Open FocusSessionPanel via Beranda's active Focus card while session is running.
        assertTrue(clickClickableAncestorWithText(content, "Finish shader"))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(150L, TimeUnit.MILLISECONDS)

        val overlayContainer = readField<View>(activity, "overlayContainer")
        assertEquals(View.VISIBLE, overlayContainer.visibility)
        assertNull(findVictoryOverlay(content))

        // FocusSession.isActive reads java.lang.System.currentTimeMillis(), which Robolectric's Looper clock does not advance.
        // Push persisted end_timestamp behind wall clock right before firing the scheduled foreground handler check.
        appContext.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit()
            .putLong("key_end_timestamp", System.currentTimeMillis() - 100L)
            .commit()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(3_200L, TimeUnit.MILLISECONDS)

        assertTrue("FocusSessionPanel must close when focus ends", overlayContainer.visibility != View.VISIBLE)
        val victory = findVictoryOverlay(content)
        assertNotNull("CompanionVictoryOverlay must surface when active Focus expires in foreground", victory)
        assertTrue(collectTexts(victory!!).contains("Finish shader"))
        assertEquals(2L, TimeBankRepository(appContext).getTimeBank().earnedMinutes)
    }

    @Test
    fun `dev preview victory button shows ceremony overlay without mutating prefs`() {
        grantUsageAccess()
        val family = CharacterFamilyRepository(appContext)
        // Seed baseline 3 XP directly without leaving pending victory.
        appContext.getSharedPreferences(CharacterFamilyRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(CharacterFamilyRepository.PREFIX_BOND_XP + "creative", 3).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(300L, TimeUnit.MILLISECONDS)
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        // Turn on debug Dev panel and re-render dashboard.
        activity.javaClass.getDeclaredField("showDevPanel").apply {
            isAccessible = true
            setBoolean(activity, true)
        }
        activity.javaClass.getDeclaredMethod("renderDashboard").apply {
            isAccessible = true
            invoke(activity)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val previewBtn = findTextView(content, activity.getString(R.string.dev_preview_victory))
        assertNotNull("Dev panel must offer Preview Victory Card action", previewBtn)
        previewBtn!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(600L, TimeUnit.MILLISECONDS)

        val overlay = findVictoryOverlay(content)
        assertNotNull(overlay)
        val cta = findTextView(overlay!!, activity.getString(R.string.victory_cta_done))
        assertNotNull(cta)
        cta!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)

        assertNull(findVictoryOverlay(content))
        assertEquals(3, family.bondXp("creative"))
        assertEquals(0, family.pendingVictoryCount())
        assertEquals(0L, TimeBankRepository(appContext).getTimeBank().earnedMinutes)
    }

    private fun grantUsageAccess() {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        (Shadows.shadowOf(appOps) as ShadowAppOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
            AppOpsManager.MODE_ALLOWED
        )
    }

    private fun seedFocusPrefs(
        activityType: FocusActivityType,
        goal: String,
        durationMinutes: Long,
        startTimestamp: Long,
        endTimestamp: Long,
        focusSessionId: String
    ) {
        appContext.getSharedPreferences("project_ego_focus_session", Context.MODE_PRIVATE).edit()
            .putBoolean("key_is_active", true)
            .putString("key_activity_type", activityType.name)
            .putString("key_goal", goal)
            .putLong("key_duration_minutes", durationMinutes)
            .putLong("key_start_timestamp", startTimestamp)
            .putLong("key_end_timestamp", endTimestamp)
            .putString("key_focus_session_id", focusSessionId)
            .putInt("key_violation_count", 0)
            .putString("key_linked_quest_attempt_id", "")
            .commit()
    }

    private fun findVictoryOverlay(view: View): CompanionVictoryOverlay? {
        if (view is CompanionVictoryOverlay) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findVictoryOverlay(view.getChildAt(i))?.let { return it }
            }
        }
        return null
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

    private fun clickClickableAncestorWithText(root: View, text: String): Boolean {
        val tv = findTextView(root, text) ?: return false
        var cursor: View? = tv
        while (cursor != null) {
            if (cursor.isClickable) {
                cursor.performClick()
                return true
            }
            cursor = cursor.parent as? View
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readField(target: Any, name: String): T =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T
}
