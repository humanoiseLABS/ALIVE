package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Beranda, as the approved pass-2 render states it.
 *
 * The Arcade card it replaced was locked by `ArcadeTodayCardTest`; that file is
 * gone with the card, and these assertions are what keeps the new screen honest:
 * one board, three facts, one action, a closed day that says TUTUP, and a family
 * row that is seven faces rather than seven blobs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BerandaScreenTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private var action = 0
    private var growth = 0
    private var focus = 0

    private fun build(
        remaining: Long = 57L,
        limit: Long = 180L,
        used: Long = 123L,
        missionsDone: Int = 1,
        missionsTotal: Int = 12,
        focusMinutes: Long = 25L,
        streak: Int = 2,
        tomorrowSafe: Boolean = false,
        closed: Boolean = false,
        critical: Boolean = false,
        actionLabel: String? = "Lanjutkan misi"
    ): View = BerandaScreen.build(
        context,
        ui,
        BerandaScreen.Data(
            greeting = "Sore.",
            subLine = "57 menit tersisa sebelum jatah hari ini habis.",
            remainingMinutes = remaining,
            limitMinutes = limit,
            usedMinutes = used,
            missionsDone = missionsDone,
            missionsTotal = missionsTotal,
            focusMinutes = focusMinutes,
            streak = streak,
            tomorrowSafe = tomorrowSafe,
            closed = closed,
            closedWord = "TUTUP",
            critical = critical,
            focusTitle = "Pilih misi hari ini",
            actionLabel = actionLabel,
            onAction = { action += 1 },
            onGrowth = { growth += 1 },
            onFocus = { focus += 1 }
        )
    )

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
        }
    }

    private fun backgrounds(view: View): List<Pair<View, Int>> = buildList {
        (view.background as? GradientDrawable)?.color?.defaultColor?.let { add(view to it) }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(backgrounds(view.getChildAt(index)))
        }
    }

    private fun firstText(view: View, value: String): TextView? {
        if (view is TextView && view.text.toString().contains(value)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                firstText(view.getChildAt(index), value)?.let { return it }
            }
        }
        return null
    }

    private fun allViews(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(allViews(view.getChildAt(index)))
        }
    }

    private fun clickableAncestor(view: View?): View? {
        var current = view
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent as? View
        }
        return null
    }

    @Test
    fun `the page opens with the greeting and the day's sentence`() {
        val page = build()
        val shown = texts(page)
        assertTrue(shown.contains("Sore."))
        assertTrue(shown.any { it.startsWith("57 menit tersisa") })
    }

    @Test
    fun `the board states the day in one figure and two small words`() {
        val page = build()
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains("57"))
        assertTrue(shown.contains(context.getString(R.string.unit_minutes_suffix)))
        assertTrue(shown.contains(context.getString(R.string.beranda_of_board, 180)))
    }

    @Test
    fun `the three facts carry their numbers`() {
        val page = build()
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains("123"))
        assertTrue(shown.contains("1"))
        assertTrue(shown.contains("25"))
        assertTrue(shown.contains(context.getString(R.string.beranda_of_board, 12)))
        assertTrue(
            "the used tile must wear the blossom field",
            backgrounds(page).any { it.second == ui.palette.fieldBlossom }
        )
    }

    @Test
    fun `the one action is a black pill with the day's label`() {
        val page = build()
        val label = firstText(page, "Lanjutkan misi")
        requireNotNull(label)
        val button = clickableAncestor(label)
        requireNotNull(button)
        button.performClick()
        assertEquals(1, action)
    }

    @Test
    fun `a closed day says TUTUP and never prints a share`() {
        val page = build(remaining = 0L, limit = 0L, closed = true, actionLabel = null)
        val shown = texts(page)
        assertTrue(shown.contains("TUTUP"))
        assertTrue("a closed board must not print a figure", shown.none { it == "0" })
    }

    @Test
    fun `a critical stretch wears the red field, a normal day the mint`() {
        val normal = backgrounds(build()).map { it.second }
        val critical = backgrounds(build(critical = true)).map { it.second }
        assertTrue(normal.contains(ui.palette.flatMint))
        assertTrue(critical.contains(ui.palette.shortPale))
    }

    @Test
    fun `the family row is seven faces and one way to the galaxy`() {
        val page = build()
        val row = allViews(page).firstOrNull { view ->
            val group = view as? ViewGroup ?: return@firstOrNull false
            view.isClickable && group.childCount == 7 &&
                (0 until group.childCount).all { group.getChildAt(it) is android.widget.ImageView }
        }
        requireNotNull(row)
        row.performClick()
        assertEquals(1, growth)
    }

    @Test
    fun `the focus card names today's block and opens it`() {
        val page = build()
        val title = firstText(page, "Pilih misi hari ini")
        val card = clickableAncestor(title)
        requireNotNull(card)
        card.performClick()
        assertEquals(1, focus)
    }

    @Test
    fun `the streak is stated in days and tomorrow's word`() {
        val page = build(streak = 2, tomorrowSafe = false)
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains(context.getString(R.string.beranda_streak_days, 2)))
        assertTrue(shown.contains(context.getString(R.string.beranda_tomorrow_open)))
    }
}
