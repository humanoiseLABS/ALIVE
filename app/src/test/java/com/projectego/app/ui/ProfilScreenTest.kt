package com.projectego.app.ui

import android.content.Context
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
 * Profil, as the approved pass-2 render states it: the character's card, the
 * bank's card, and three rows that lead to the rest. The rows are the whole
 * information architecture of this page now, so each one is pressed here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilScreenTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private var growth = 0
    private var claim = 0
    private var bank = 0
    private var reminders = 0
    private var permissions = 0
    private var language = 0

    private fun build(): View = ProfilScreen.build(
        context,
        ui,
        ProfilScreen.Data(
            level = 3,
            scaleLine = "18 dari 70 poin",
            streak = 2,
            missions = 1,
            focusSessions = 3,
            bankMinutes = 135L,
            remindersLine = "Air 90 mnt",
            permissionsLine = "2 izin aktif",
            languageLine = "Bahasa Indonesia · cahaya biru",
            onGrowth = { growth += 1 },
            onClaim = { claim += 1 },
            onBank = { bank += 1 },
            onReminders = { reminders += 1 },
            onPermissions = { permissions += 1 },
            onLanguage = { language += 1 }
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
    fun `the page names itself and the character's standing`() {
        val shown = texts(build())
        assertTrue(shown.contains(context.getString(R.string.profile_heading)))
        assertTrue(shown.contains(context.getString(R.string.growth_level, 3)))
        assertTrue(shown.contains("18 dari 70 poin"))
        assertTrue(shown.contains(context.getString(R.string.profile_chip_days, 2)))
        assertTrue(shown.contains(context.getString(R.string.profile_chip_missions, 1)))
        assertTrue(shown.contains(context.getString(R.string.profile_chip_focus, 3)))
    }

    @Test
    fun `the bank card states the balance and keeps its two actions apart`() {
        val page = build()
        assertTrue(texts(page).contains("135"))

        val pill = firstText(page, context.getString(R.string.time_bank_claim_action_off))
        val claimButton = clickableAncestor(pill)
        requireNotNull(claimButton)
        claimButton.performClick()
        assertEquals(1, claim)

        val title = firstText(page, context.getString(R.string.time_bank_heading))
        val card = clickableAncestor(title)
        requireNotNull(card)
        card.performClick()
        assertEquals("the card opens the bank page, the pill claims", 1, bank)
        assertEquals("one tap must not do both", 1, claim)
    }

    @Test
    fun `the three rows lead to their own pages`() {
        val page = build()
        listOf(
            context.getString(R.string.profile_reminders_heading) to { reminders },
            context.getString(R.string.profile_permissions_heading) to { permissions },
            context.getString(R.string.profile_language_pill_title) to { language }
        ).forEach { (label, value) ->
            val title = firstText(page, label)
            requireNotNull(title)
            clickableAncestor(title)?.performClick()
            assertEquals(1, value())
        }
    }

    @Test
    fun `the character card opens the galaxy`() {
        val page = build()
        val level = firstText(page, context.getString(R.string.growth_level, 3))
        clickableAncestor(level)?.performClick()
        assertEquals(1, growth)
    }

    @Test
    fun `the rows say what sits behind them`() {
        val shown = texts(build())
        assertTrue(shown.contains("Air 90 mnt"))
        assertTrue(shown.contains("2 izin aktif"))
        assertTrue(shown.contains("Bahasa Indonesia · cahaya biru"))
    }
}
