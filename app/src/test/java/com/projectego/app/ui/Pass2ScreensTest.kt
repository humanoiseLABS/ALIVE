package com.projectego.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
 * Unit tests for Pass-2 standalone components: [GuardianScreen],
 * [NightContractSheet], and [TimeBankSheet].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Pass2ScreensTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private fun textViews(root: View): List<TextView> {
        val out = mutableListOf<TextView>()
        if (root is TextView) out += root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                out += textViews(root.getChildAt(i))
            }
        }
        return out
    }

    private fun allText(root: View): List<String> = textViews(root).mapNotNull { it.text?.toString() }

    private fun firstTextViewMatching(root: View, predicate: (TextView) -> Boolean): TextView =
        textViews(root).first(predicate)

    private fun clickableAncestor(view: View): View {
        var cursor: View? = view
        while (cursor != null) {
            if (cursor.isClickable) return cursor
            cursor = cursor.parent as? View
        }
        error("No clickable ancestor for ${(view as? TextView)?.text ?: view}")
    }

    // ---------- GuardianScreen ----------

    @Test
    fun `guardian degraded state renders hero, metrics, action button, and back pill`() {
        var actionClicks = 0
        var backClicks = 0

        val view = GuardianScreen.build(
            context = context,
            ui = ui,
            data = GuardianScreen.Data(
                state = GuardianState.DEGRADED,
                clockLabel = "09.42",
                eyebrow = "PENJAGAAN TIDAK AKTIF",
                headline = "Penjagaan dimatikan",
                body = "Nyalakan lagi di Pengaturan.",
                actionLabel = "Nyalakan penjagaan",
                sinceLabel = "2 mnt",
                closedToday = 1,
                remainingMinutes = 90L,
                note = "Hari ini belum ada catatan.",
                onAction = { actionClicks++ },
                onBack = { backClicks++ }
            )
        )

        val texts = allText(view)
        assertTrue(texts.contains("09.42"))
        assertTrue(texts.contains("PENJAGAAN TIDAK AKTIF"))
        assertTrue(texts.contains("Penjagaan dimatikan"))
        assertTrue(texts.contains("Nyalakan lagi di Pengaturan."))
        assertTrue(texts.contains("2 mnt"))
        assertTrue(texts.contains("1"))
        assertTrue(texts.contains("90"))
        assertTrue(texts.contains("Hari ini belum ada catatan."))

        firstTextViewMatching(view) { it.text?.toString() == "Nyalakan penjagaan" }.performClick()
        assertEquals(1, actionClicks)

        firstTextViewMatching(view) {
            it.text?.toString() == context.getString(R.string.guardian_name)
        }.performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun `guardian active state hides action button when actionLabel is null`() {
        var actionClicks = 0
        val view = GuardianScreen.build(
            context = context,
            ui = ui,
            data = GuardianScreen.Data(
                state = GuardianState.ACTIVE,
                clockLabel = "09.42",
                eyebrow = "AKTIF",
                headline = "Penjagaan aktif",
                body = "Semua aman.",
                actionLabel = null,
                sinceLabel = "0 mnt",
                closedToday = 0,
                remainingMinutes = 120L,
                note = "Tetap terjaga.",
                onAction = { actionClicks++ }
            )
        )

        val texts = allText(view)
        assertTrue(texts.contains("Penjagaan aktif"))
        assertEquals(0, textViews(view).count { it is Button })
        assertEquals(0, actionClicks)
    }

    @Test
    fun `guardian restored state invokes action callback`() {
        var actionClicks = 0
        val view = GuardianScreen.build(
            context = context,
            ui = ui,
            data = GuardianScreen.Data(
                state = GuardianState.RESTORED,
                clockLabel = "20.15",
                eyebrow = "PULIH",
                headline = "Penjagaan tersambung kembali",
                body = "Status kembali normal.",
                actionLabel = "Kembali ke beranda",
                sinceLabel = "Baru saja",
                closedToday = 2,
                remainingMinutes = 45L,
                note = "Perbaikan selesai.",
                onAction = { actionClicks++ }
            )
        )

        firstTextViewMatching(view) { it.text?.toString() == "Kembali ke beranda" }.performClick()
        assertEquals(1, actionClicks)
    }

    // ---------- NightContractSheet ----------

    @Test
    fun `night contract sheet edits allowance, mission, and reason and submits via save`() {
        val q1 = Quest(
            id = "q_read",
            title = "Baca buku 20 menit",
            description = "Buku cetak",
            category = QuestCategory.READING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 15L
        )
        val q2 = Quest(
            id = "q_walk",
            title = "Jalan sore",
            description = "Tanpa gawai",
            category = QuestCategory.WALKING,
            difficulty = QuestDifficulty.EASY,
            estimatedDurationMinutes = 20,
            rewardMinutes = 20L
        )
        var saved: Triple<Long, String?, String>? = null
        var dismissed = 0

        val view = NightContractSheet.build(
            context = context,
            ui = ui,
            data = NightContractSheet.Data(
                allowanceOptions = listOf(45L, 60L, 90L),
                initialAllowance = 60L,
                quests = listOf(q1, q2),
                initialQuestId = null,
                initialReason = "",
                onSave = { allowance, questId, reason ->
                    saved = Triple(allowance, questId, reason)
                },
                onDismiss = { dismissed++ }
            )
        )

        // Open allowance picker and choose 45 minutes
        val allowanceRow = clickableAncestor(
            firstTextViewMatching(view) {
                it.text?.toString() == context.getString(R.string.night_contract_row_allowance)
            }
        )
        allowanceRow.performClick()
        clickableAncestor(
            firstTextViewMatching(view) {
                it.text?.toString() == context.getString(R.string.stat_minutes, 45L)
            }
        ).performClick()

        // Open mission picker and pick q2
        val missionRow = clickableAncestor(
            firstTextViewMatching(view) {
                it.text?.toString() == context.getString(R.string.night_contract_row_mission)
            }
        )
        missionRow.performClick()
        clickableAncestor(
            firstTextViewMatching(view) {
                it.text?.toString()?.contains("Jalan sore") == true
            }
        ).performClick()

        // Fill reason field and press save
        val reasonField = textViews(view).filterIsInstance<EditText>().first()
        reasonField.setText("Besok pagi kuliah")
        firstTextViewMatching(view) {
            it.text?.toString() == context.getString(R.string.night_contract_save)
        }.performClick()

        assertEquals(Triple(45L, "q_walk", "Besok pagi kuliah"), saved)

        view.performClick()
        assertEquals(1, dismissed)
    }

    // ---------- TimeBankSheet ----------

    @Test
    fun `time bank sheet enables claim button when minutes are claimable and invokes callback`() {
        var claims = 0
        var dismissed = 0
        val view = TimeBankSheet.build(
            context = context,
            ui = ui,
            data = TimeBankSheet.Data(
                availableMinutes = 35L,
                earnedMinutes = 50L,
                spentMinutes = 15L,
                claimedToday = 10L,
                dailyCap = 60L,
                claimableMinutes = 35L,
                recoveryDay = false,
                firstSourceLabel = "Baca +20 m",
                secondSourceLabel = "Fokus +30 m",
                streakSafe = true,
                onClaim = { claims++ },
                onDismiss = { dismissed++ }
            )
        )

        val texts = allText(view)
        assertTrue(texts.contains("35"))
        assertTrue(texts.contains("+50"))
        assertTrue(texts.contains("15"))
        assertTrue(texts.contains("Baca +20 m"))
        assertTrue(texts.contains("Fokus +30 m"))

        val claimBtn = firstTextViewMatching(view) {
            it.text?.toString() == context.getString(R.string.time_bank_claim_action, 35L)
        }
        assertTrue(claimBtn.isEnabled)
        claimBtn.performClick()
        assertEquals(1, claims)

        view.performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `time bank sheet disables claim button on recovery day and at daily cap`() {
        var claims = 0

        val recoveryView = TimeBankSheet.build(
            context = context,
            ui = ui,
            data = TimeBankSheet.Data(
                availableMinutes = 25L,
                earnedMinutes = 25L,
                spentMinutes = 0L,
                claimedToday = 0L,
                dailyCap = 60L,
                claimableMinutes = 0L,
                recoveryDay = true,
                firstSourceLabel = null,
                secondSourceLabel = null,
                streakSafe = false,
                onClaim = { claims++ },
                onDismiss = {}
            )
        )
        val recoveryBtn = firstTextViewMatching(recoveryView) {
            it.text?.toString() == context.getString(R.string.time_bank_claim_recovery)
        }
        assertFalse(recoveryBtn.isEnabled)
        recoveryBtn.performClick()
        assertEquals(0, claims)

        val capView = TimeBankSheet.build(
            context = context,
            ui = ui,
            data = TimeBankSheet.Data(
                availableMinutes = 20L,
                earnedMinutes = 80L,
                spentMinutes = 60L,
                claimedToday = 60L,
                dailyCap = 60L,
                claimableMinutes = 0L,
                recoveryDay = false,
                firstSourceLabel = null,
                secondSourceLabel = null,
                streakSafe = true,
                onClaim = { claims++ },
                onDismiss = {}
            )
        )
        val capBtn = firstTextViewMatching(capView) {
            it.text?.toString() == context.getString(R.string.time_bank_claim_full)
        }
        assertFalse(capBtn.isEnabled)
        capBtn.performClick()
        assertEquals(0, claims)
    }

    @Test
    fun `equal row pins mixed stat tiles including stackUnit to the tallest height and re-measures dynamically`() {
        val row = ui.equalRow().apply {
            addView(ui.weighted(ui.statTile("Durasi", "30", ui.palette.tile, unit = "menit")))
            addView(ui.hgap(ui.dimens.pairGap))
            addView(ui.weighted(ui.statTile("Hadiah", "+20", ui.palette.flatMint, unit = "bank", stackUnit = true)))
            addView(ui.hgap(ui.dimens.pairGap))
            addView(ui.weighted(ui.statTile("Tingkat", "2", ui.palette.fieldButter, unit = "dari 3")))
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        row.measure(widthSpec, heightSpec)
        val tiles = listOf(row.getChildAt(0), row.getChildAt(2), row.getChildAt(4))
        val firstPassHeight = tiles[1].measuredHeight
        assertTrue(firstPassHeight > 0)
        tiles.forEach { assertEquals(firstPassHeight, it.measuredHeight) }

        val firstLabel = (tiles[0] as ViewGroup).getChildAt(0) as TextView
        firstLabel.text = "Durasi pengerjaan\nyang sangat panjang\ntiga baris"
        row.measure(widthSpec, heightSpec)
        val secondPassHeight = tiles[0].measuredHeight
        assertTrue("row should grow when a sibling wraps taller", secondPassHeight > firstPassHeight)
        tiles.forEach { assertEquals(secondPassHeight, it.measuredHeight) }
    }
}
