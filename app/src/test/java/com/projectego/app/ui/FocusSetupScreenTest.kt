package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FOKUS as the approved render draws it: a lilac card with the ring and the
 * chosen time, three tiles that name the session before it starts, the goal,
 * four lengths, and the one action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusSetupScreenTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private val allowed = mapOf(
        FocusActivityType.STUDY to 4,
        FocusActivityType.WORK to 2,
        FocusActivityType.CODING to 3,
        FocusActivityType.CREATIVE to 3,
        FocusActivityType.WRITING to 2
    )

    private var chosenDuration = 0L
    private var chosenActivity: FocusActivityType? = null
    private var typedGoal = ""
    private var started = 0

    private fun build(
        duration: Long = 25L,
        activity: FocusActivityType = FocusActivityType.STUDY,
        goal: String = ""
    ): View = FocusSetupScreen.build(
        context,
        ui,
        FocusSetupScreen.Data(
            durationMinutes = duration,
            durations = listOf(15L, 25L, 45L, 60L),
            activity = activity,
            activities = FocusActivityType.values().toList(),
            allowedApps = { type -> allowed[type] ?: 0 },
            goal = goal,
            onGoal = { typedGoal = it },
            onDuration = { chosenDuration = it },
            onActivity = { chosenActivity = it },
            onStart = { started += 1 }
        )
    )

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
        }
    }

    private fun firstText(view: View, value: String): TextView? {
        if (view is TextView && view.text.toString() == value) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                firstText(view.getChildAt(index), value)?.let { return it }
            }
        }
        return null
    }

    private fun ring(view: View): FocusSetupScreen.Ring? {
        if (view is FocusSetupScreen.Ring) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                ring(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** A kicker sits inside a clickable field, and the field is what opens. */
    private fun clickFieldAround(view: View): Boolean {
        var node: View? = view
        while (node != null) {
            if (node.isClickable) return node.performClick()
            node = node.parent as? View
        }
        return false
    }

    private fun fillOf(view: View): Int {
        val drawable = view.background as GradientDrawable
        return drawable.color?.defaultColor ?: 0
    }

    /**
     * Whether a view is folded away: it or any ancestor is not VISIBLE.
     * [View.isShown] needs a window, and these trees are built detached.
     */
    private fun folded(view: View): Boolean {
        var node: View? = view
        while (node != null) {
            if (node.visibility != View.VISIBLE) return true
            node = node.parent as? View
        }
        return false
    }

    @Test
    fun `the ring states the chosen duration`() {
        val root = build(duration = 25L)

        val time = firstText(root, context.getString(R.string.focus_ring_time, 25L))
        assertNotNull("the ring's own figure", time)
        assertNotNull(
            "the figure says what it counts",
            firstText(root, context.getString(R.string.unit_minutes_suffix))
        )
    }

    @Test
    fun `the ring fills as much of the hour as the session takes`() {
        val root = build(duration = 25L)

        val ring = ring(root)
        assertNotNull("a ring, not a card of type", ring)
        assertEquals(25f / 60f, ring!!.fraction, 0.001f)

        clickFieldAround(firstText(root, context.getString(R.string.focus_duration_value, 45))!!)

        assertEquals(45f / 60f, ring.fraction, 0.001f)
        assertNotNull(firstText(root, context.getString(R.string.focus_ring_time, 45L)))
    }

    @Test
    fun `the tiles name the activity, what opens, and messaging`() {
        val root = build()
        val shown = texts(root)

        assertTrue(shown.contains(context.getString(R.string.focus_kicker_activity)))
        assertTrue(shown.contains(context.getString(R.string.focus_activity_study)))
        assertTrue(shown.contains(context.getString(R.string.focus_kicker_allowed)))
        assertTrue(
            shown.contains(context.getString(R.string.focus_allowed_count, 4))
        )
        assertTrue(shown.contains(context.getString(R.string.focus_kicker_message)))
        assertTrue(shown.contains(context.getString(R.string.focus_message_value)))
    }

    @Test
    fun `the activity tile offers the other activities and rewrites the tiles`() {
        val root = build()
        val coding = context.getString(R.string.focus_activity_coding)
        val optionText = context.getString(
            R.string.focus_activity_option,
            coding,
            allowed.getValue(FocusActivityType.CODING)
        )

        val option = firstText(root, optionText)
        assertNotNull("every activity is offered", option)
        assertTrue("the choices stay folded until asked", folded(option!!))

        assertTrue(clickFieldAround(firstText(root, context.getString(R.string.focus_kicker_activity))!!))

        assertFalse("the choices unfold", folded(option))
        option.performClick()

        assertEquals(FocusActivityType.CODING, chosenActivity)
        val shown = texts(root)
        assertTrue("the tile follows the pick", shown.contains(coding))
        assertTrue(
            shown.contains(
                context.getString(R.string.focus_allowed_count, allowed.getValue(FocusActivityType.CODING))
            )
        )
        assertTrue("the choices fold again once answered", folded(option))
    }

    @Test
    fun `re-picking the current activity still folds the choices`() {
        val root = build()
        val study = context.getString(
            R.string.focus_activity_option,
            context.getString(R.string.focus_activity_study),
            allowed.getValue(FocusActivityType.STUDY)
        )

        assertTrue(clickFieldAround(firstText(root, context.getString(R.string.focus_kicker_activity))!!))
        val option = firstText(root, study)!!
        assertFalse(folded(option))

        option.performClick()

        assertTrue("an answer is an answer, even the same one", folded(option))
    }

    @Test
    fun `the four offered lengths are the render's, and only the chosen one is ink`() {
        val root = build(duration = 25L)
        val four = listOf(15L, 25L, 45L, 60L).map {
            firstText(root, context.getString(R.string.focus_duration_value, it))
        }
        four.forEachIndexed { index, pill -> assertNotNull("length $index", pill) }

        assertEquals(ui.palette.ink, fillOf(four[1]!!))
        assertNotEquals(ui.palette.ink, fillOf(four[0]!!))
        assertNotEquals(ui.palette.ink, fillOf(four[2]!!))
        assertNotEquals(ui.palette.ink, fillOf(four[3]!!))

        four[2]!!.performClick()

        assertEquals(45L, chosenDuration)
        assertEquals(ui.palette.ink, fillOf(four[2]!!))
        assertNotEquals(ui.palette.ink, fillOf(four[1]!!))
    }

    @Test
    fun `an unchosen length still reads at arm's length`() {
        val root = build(duration = 25L)
        val pill = firstText(root, context.getString(R.string.focus_duration_value, 15))!!
        val tile = fillOf(pill)
        val onTile = ColorUtils.compositeColors(pill.currentTextColor, tile)
        assertTrue(
            "unchosen pill text keeps 4.5:1 on its tile",
            ColorUtils.calculateContrast(onTile, tile) >= 4.5
        )
    }

    @Test
    fun `the goal starts from the draft and reports what is typed`() {
        val root = build(goal = "Selesaikan bab empat")

        val field = firstText(root, "Selesaikan bab empat")
        assertNotNull("the draft is on the field", field)
        assertTrue("the field is writable", field is EditText)

        (field as EditText).setText("Bab lima")

        assertEquals("Bab lima", typedGoal)
    }

    @Test
    fun `the three info tiles share one equal height even when one wraps`() {
        val root = build()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val row = firstText(root, context.getString(R.string.focus_kicker_activity))!!
            .parent!! // tile
            .parent as ViewGroup // EqualRow
        assertTrue(row is AliveComponents.EqualRow)
        val tiles = (0 until row.childCount)
            .map { row.getChildAt(it) }
            .filter { it is ViewGroup }
        assertEquals(3, tiles.size)
        val firstHeight = tiles[0].measuredHeight
        assertTrue("measured height must be positive", firstHeight > 0)
        tiles.forEachIndexed { idx, tile ->
            assertEquals("tile $idx height", firstHeight, tile.measuredHeight)
        }
    }

    @Test
    fun `the one action reports the press`() {
        val root = build()

        clickFieldAround(firstText(root, context.getString(R.string.focus_start))!!)

        assertEquals(1, started)
    }
}
