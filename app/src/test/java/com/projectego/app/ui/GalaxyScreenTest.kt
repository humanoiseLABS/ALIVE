package com.projectego.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.projectego.app.R
import com.projectego.app.data.CharacterFamilyRepository.MemberStatus
import com.projectego.app.domain.GalaxyCatalog
import com.projectego.app.domain.GalaxyCatalog.GalaxyActionTarget
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalaxyScreenTest {

    private lateinit var activity: Activity
    private lateinit var themedContext: ContextThemeWrapper
    private lateinit var ui: AliveComponents
    private var mountedContainer: FrameLayout? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        themedContext = ContextThemeWrapper(activity, R.style.Theme_ProjectEgo)
        ui = AliveComponents(themedContext)
    }

    @After
    fun tearDown() {
        mountedContainer?.removeAllViews()
        mountedContainer = null
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleStatuses(
        companionId: String = "creative",
        awakenedIds: Set<String> = setOf("creative", "coding", "recovery"),
        bondXpById: Map<String, Int> = emptyMap()
    ): List<MemberStatus> = GalaxyCatalog.members.map { spec ->
        val count = when (spec.id) {
            "creative" -> 3
            "coding" -> 4
            "recovery" -> 2
            else -> if (spec.id in awakenedIds) 1 else 0
        }
        MemberStatus(
            spec = spec,
            awakened = spec.id == "creative" || spec.id in awakenedIds,
            count = count,
            focusMinutes = if (spec.id == "coding") 90L else 0L,
            isCompanion = spec.id == companionId,
            bondXp = bondXpById[spec.id] ?: 0
        )
    }

    private fun mountScreen(
        statuses: List<MemberStatus> = sampleStatuses(),
        initialSelectedId: String? = null,
        onSelectCompanion: (GalaxyCatalog.GalaxyMemberSpec) -> Boolean = { true },
        onTriggerAction: (GalaxyActionTarget) -> Unit = {},
        onClose: () -> Unit = {}
    ): View {
        val root = GalaxyScreen.build(
            themedContext,
            ui,
            GalaxyScreen.Data(
                statuses = statuses,
                activeCompanion = statuses.first { it.isCompanion }.spec,
                level = 2,
                points = 10,
                streak = 4,
                missionsCompleted = 2,
                focusMinutesToday = 45L,
                questCountedToday = true,
                focusCountedToday = false,
                promiseCountedToday = true,
                questDaysTotal = 5,
                focusDaysTotal = 3,
                promiseDaysTotal = 2,
                initialSelectedId = initialSelectedId,
                onBack = onClose,
                onClose = onClose,
                onSelectCompanion = onSelectCompanion,
                onTriggerAction = onTriggerAction
            )
        )
        val container = FrameLayout(themedContext).apply { addView(root) }
        mountedContainer = container
        activity.setContentView(container)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.UNSPECIFIED)
        )
        container.layout(0, 0, 1080, container.measuredHeight.coerceAtLeast(1920))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800L, TimeUnit.MILLISECONDS)
        return root
    }

    @Test
    fun `overview displays absorbed growth summary, daily checkmarks, and orbiter base alphas`() {
        var closed = 0
        val root = mountScreen(onClose = { closed++ })
        val allTexts = collectTexts(root)

        assertTrue(allTexts.any { it.contains("Creative") })
        assertTrue(allTexts.contains("Level 2"))
        assertTrue(allTexts.contains("3 of 23 points"))
        assertTrue(allTexts.contains("Quests ✓"))
        assertTrue(allTexts.contains("Focus"))
        assertTrue(allTexts.contains("Promises ✓"))

        val orbit = findOrbitView(root)
        assertNotNull(orbit)
        orbit!!
        val expectedAvailWidth = themedContext.resources.displayMetrics.widthPixels -
            (12f * themedContext.resources.displayMetrics.density).toInt()
        val maxRingWidth = (420f * themedContext.resources.displayMetrics.density).toInt()
        assertTrue(orbit.layoutParams.width >= minOf(maxRingWidth, expectedAvailWidth) - 2)
        assertEquals(17, orbit.childCount - 1) // 1 center hub + 17 orbiters
        assertEquals(5, orbit.activeCompanionIndex) // creative is active companion
        assertEquals(0.78f, orbit.getChildAt(1 + 1).alpha, 0.02f) // dormant reading keeps vivid color
        assertEquals(1.0f, orbit.getChildAt(1 + 5).alpha, 0.02f)  // creative
        assertEquals(1.0f, orbit.getChildAt(1 + 6).alpha, 0.02f)  // coding

        val continueBtn = findTextView(root, themedContext.getString(R.string.galaxy_continue))
        assertNotNull(continueBtn)
        continueBtn!!.performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `tapping dormant member hides companion selection yet allows shortcut dispatch`() {
        val triggered = mutableListOf<GalaxyActionTarget>()
        val root = mountScreen(onTriggerAction = { triggered.add(it) })
        val orbit = findOrbitView(root)!!

        // Index 1 = reading (dormant, primaryAction = QuestFilter(READING))
        tapOrbiter(orbit, 1)

        val texts = collectTexts(root)
        assertTrue(texts.contains("Reading"))
        assertTrue(texts.contains(themedContext.getString(R.string.galaxy_companion_locked_hint)))
        assertNull(findTextView(root, themedContext.getString(R.string.galaxy_set_companion)))

        val cta = findTextView(root, "Find reading missions →")
        assertNotNull(cta)
        cta!!.performClick()
        assertEquals(listOf<GalaxyActionTarget>(GalaxyActionTarget.QuestFilter(QuestCategory.READING)), triggered)
    }

    @Test
    fun `tapping awakened coding lets user set active companion and updates back to overview`() {
        val selected = mutableListOf<String>()
        val triggered = mutableListOf<GalaxyActionTarget>()
        val root = mountScreen(
            onSelectCompanion = { spec ->
                selected.add(spec.id)
                true
            },
            onTriggerAction = { triggered.add(it) }
        )
        val orbit = findOrbitView(root)!!

        // Index 6 = coding (awakened, not current companion)
        tapOrbiter(orbit, 6)
        assertTrue(collectTexts(root).contains("4 completed · 90 min focus"))

        val setBtn = findTextView(root, themedContext.getString(R.string.galaxy_set_companion))
        assertNotNull(setBtn)
        setBtn!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("coding"), selected)
        assertEquals(6, orbit.activeCompanionIndex)
        assertTrue(collectTexts(root).contains(themedContext.getString(R.string.galaxy_companion_active_pill)))
        assertNull(findTextView(root, themedContext.getString(R.string.galaxy_set_companion)))

        // Orbit dispatchDraw paints companion ring, awakened aura, and selected reticle safely.
        val orbitBmp = Bitmap.createBitmap(
            orbit.width.coerceAtLeast(320),
            orbit.height.coerceAtLeast(320),
            Bitmap.Config.ARGB_8888
        )
        orbit.draw(Canvas(orbitBmp))
        orbitBmp.recycle()

        findTextView(root, "Start coding focus →")!!.performClick()
        findTextView(root, themedContext.getString(R.string.galaxy_action_custom_quest))!!.performClick()
        assertEquals(
            listOf<GalaxyActionTarget>(
                GalaxyActionTarget.Focus(FocusActivityType.CODING),
                GalaxyActionTarget.CustomQuest
            ),
            triggered
        )

        // Tap Summary pill -> returns to overview with Coding now listed as active companion.
        findTextView(root, themedContext.getString(R.string.galaxy_back_overview))!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val afterOverview = collectTexts(root)
        assertTrue(afterOverview.any { it.contains("Coding") })
    }

    @Test
    fun `tapping day-state mirrors never offers companion selection and triggers proper routes`() {
        val triggered = mutableListOf<GalaxyActionTarget>()
        val root = mountScreen(onTriggerAction = { triggered.add(it) })
        val orbit = findOrbitView(root)!!

        // 14 = recovery -> TimeBank
        tapOrbiter(orbit, 14)
        assertTrue(collectTexts(root).contains(themedContext.getString(R.string.galaxy_state_no_companion_hint)))
        assertNull(findTextView(root, themedContext.getString(R.string.galaxy_set_companion)))
        findTextView(root, themedContext.getString(R.string.galaxy_action_bank))!!.performClick()

        // 15 = unknown -> Permissions
        tapOrbiter(orbit, 15)
        findTextView(root, themedContext.getString(R.string.galaxy_action_permissions))!!.performClick()

        // 16 = rest -> Reminders
        tapOrbiter(orbit, 16)
        findTextView(root, themedContext.getString(R.string.galaxy_action_reminders))!!.performClick()

        // 11 = normal -> Stats
        tapOrbiter(orbit, 11)
        findTextView(root, themedContext.getString(R.string.galaxy_action_stats))!!.performClick()

        assertEquals(
            listOf<GalaxyActionTarget>(
                GalaxyActionTarget.TimeBank,
                GalaxyActionTarget.Permissions,
                GalaxyActionTarget.Reminders,
                GalaxyActionTarget.Stats
            ),
            triggered
        )
    }

    @Test
    fun `liquid glass card persists across stage swaps and renders prism canvas fallback`() {
        val root = mountScreen()
        val glassCard = findLiquidGlassCard(root)
        assertNotNull(glassCard)
        glassCard!!

        // Minimalist overview integrates the Continue pill inside the single glass card.
        assertNotNull(findTextView(glassCard, themedContext.getString(R.string.galaxy_continue)))

        // Swapping to inspector and back reuses the exact same glass card container.
        val orbit = findOrbitView(root)!!
        tapOrbiter(orbit, 6)
        assertTrue(findLiquidGlassCard(root) === glassCard)
        findTextView(root, themedContext.getString(R.string.galaxy_back_overview))!!.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(findLiquidGlassCard(root) === glassCard)

        // Phase advances deterministically and Canvas fallback renders crystal translucent body + rotating prism rim.
        val density = themedContext.resources.displayMetrics.density
        val sampleEdgePx = (3.3f * density + 0.5f).toInt()
        val w = glassCard.width.coerceAtLeast(320)
        val h = glassCard.height.coerceAtLeast(200)

        fun renderCardPixel(phaseRad: Float, x: Int, y: Int): Int {
            glassCard.setPhaseForTesting(phaseRad)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            glassCard.draw(Canvas(bmp))
            val px = bmp.getPixel(x, y)
            bmp.recycle()
            return px
        }

        val centerPixel = renderCardPixel(0f, w / 2, h / 2)
        val centerAlpha = centerPixel ushr 24
        val rimBefore = renderCardPixel(0f, sampleEdgePx, h / 2)
        val rimAfter = renderCardPixel(1.25f, sampleEdgePx, h / 2)

        assertEquals(1.25f, glassCard.currentPhaseRadians(), 0.001f)
        assertTrue(
            "crystal center must stay translucent (not milky opaque): alpha=$centerAlpha",
            centerAlpha in 35..145
        )
        assertTrue("rim must be drawn at stroke edge", (rimBefore ushr 24) > 120)
        assertTrue("rim color must rotate cleanly with prism phase", rimBefore != rimAfter)
    }

    @Test
    fun `work member inspector displays bond tier progress and companion synergy rule`() {
        val root = mountScreen(
            statuses = sampleStatuses(
                companionId = "creative",
                bondXpById = mapOf("creative" to 6, "coding" to 40)
            )
        )
        val orbit = findOrbitView(root)!!

        val creativeIndex = GalaxyCatalog.members.indexOfFirst { it.id == "creative" }
        tapOrbiter(orbit, creativeIndex)

        val creativeTexts = collectTexts(root)
        val rank2Name = themedContext.getString(R.string.bond_rank_2_name)
        assertTrue(creativeTexts.contains(themedContext.getString(R.string.bond_rank_line, 2, rank2Name, 1, 10)))
        assertTrue(creativeTexts.contains(themedContext.getString(R.string.galaxy_synergy_rule)))

        val codingIndex = GalaxyCatalog.members.indexOfFirst { it.id == "coding" }
        tapOrbiter(orbit, codingIndex)

        val codingTexts = collectTexts(root)
        val masterName = themedContext.getString(R.string.bond_rank_4_name)
        assertTrue(codingTexts.contains(themedContext.getString(R.string.bond_rank_master_line, 4, masterName)))
    }

    @Test
    fun `day state member inspector omits work bond tier and synergy hint`() {
        val root = mountScreen(
            statuses = sampleStatuses(bondXpById = mapOf("creative" to 6))
        )
        val orbit = findOrbitView(root)!!

        val recoveryIndex = GalaxyCatalog.members.indexOfFirst { it.id == "recovery" }
        tapOrbiter(orbit, recoveryIndex)

        val texts = collectTexts(root)
        assertTrue(texts.none { it == themedContext.getString(R.string.galaxy_synergy_rule) })
        assertTrue(texts.none { it.startsWith("Rank ") || it.startsWith("Peringkat ") })
    }

    private fun tapOrbiter(orbit: GalaxyOrbitView, index: Int) {
        val child = orbit.getChildAt(1 + index)
        val x = child.translationX + child.width / 2f
        val y = child.translationY + child.height / 2f
        val t0 = SystemClock.uptimeMillis()
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0))
        orbit.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 25, MotionEvent.ACTION_UP, x, y, 0))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(700L, TimeUnit.MILLISECONDS)
    }

    private fun findOrbitView(view: View): GalaxyOrbitView? {
        if (view is GalaxyOrbitView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findOrbitView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findLiquidGlassCard(view: View): AliveLiquidGlassCard? {
        if (view is AliveLiquidGlassCard) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findLiquidGlassCard(view.getChildAt(i))?.let { return it }
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
}
