package com.projectego.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.QuestDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Misi, held to the approved pass-2 render: one sentence, a row of filters, and
 * a list of white rows - not the grid of nine cards it replaced. The filter and
 * the row are the two taps this screen owns, so both are pressed here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MisiScreenTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private var filtered: QuestCategory? = QuestCategory.OTHER
    private var opened: String? = null
    private var custom = 0

    private val walking = Quest(
        id = "walk",
        title = "Jalan 20 Menit",
        description = "Jalan cepat tanpa melihat media sosial.",
        category = QuestCategory.WALKING,
        difficulty = QuestDifficulty.EASY,
        estimatedDurationMinutes = 20,
        rewardMinutes = 15L
    )
    private val reading = Quest(
        id = "read",
        title = "Membaca 30 Menit",
        description = "Buku fisik, e-reader, atau artikel panjang.",
        category = QuestCategory.READING,
        difficulty = QuestDifficulty.MEDIUM,
        estimatedDurationMinutes = 30,
        rewardMinutes = 20L
    )

    private fun build(
        selected: QuestCategory? = null,
        hasCustomQuest: Boolean = false
    ): View = MisiScreen.build(
        context,
        ui,
        MisiScreen.Data(
            quests = listOf(walking, reading),
            selected = selected,
            hasCustomQuest = hasCustomQuest,
            onFilter = { filtered = it },
            onOpen = { opened = it },
            onCustom = { custom += 1 }
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

    private fun clickableAncestor(view: View?): View? {
        var current = view
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent as? View
        }
        return null
    }

    @Test
    fun `the page opens with one sentence and its question`() {
        val shown = texts(build())
        assertTrue(shown.contains(context.getString(R.string.quest_heading)))
        assertTrue(shown.contains(context.getString(R.string.quest_subhead)))
    }

    @Test
    fun `the list names every mission on the board with what it pays`() {
        val shown = texts(build())
        assertTrue(shown.contains(walking.title))
        assertTrue(shown.contains(reading.title))
        assertTrue(shown.contains(context.getString(R.string.quest_reward_chip, 15L)))
        assertTrue(shown.contains(context.getString(R.string.quest_reward_chip, 20L)))
    }

    @Test
    fun `a filter narrows the list to its own category`() {
        val shown = texts(build(selected = QuestCategory.WALKING))
        assertTrue(shown.contains(walking.title))
        assertFalse(shown.contains(reading.title))
    }

    @Test
    fun `tapping a row opens that mission`() {
        val page = build()
        val title = firstText(page, walking.title)
        val row = clickableAncestor(title)
        requireNotNull(row)
        row.performClick()
        assertEquals("walk", opened)
    }

    @Test
    fun `tapping a category chip reports it`() {
        val page = build()
        val chip = firstText(page, context.getString(R.string.category_walking))
        requireNotNull(chip)
        assertTrue("the chip itself must be the tap target", chip.isClickable)
        chip.performClick()
        assertEquals(QuestCategory.WALKING, filtered)
    }

    @Test
    fun `your own mission is offered until one exists`() {
        val offered = build(hasCustomQuest = false)
        val title = context.getString(R.string.quest_custom_title)
        assertTrue(texts(offered).contains(title))
        val card = clickableAncestor(firstText(offered, title))
        requireNotNull(card)
        card.performClick()
        assertEquals(1, custom)

        assertFalse(texts(build(hasCustomQuest = true)).contains(title))
    }
}
