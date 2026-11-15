package com.projectego.app.domain

import com.projectego.app.domain.CompanionSynergy.BondTier
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSynergyTest {

    @Test
    fun `quest match requires work companion with matching category`() {
        val creative = GalaxyCatalog.findById("creative")!!
        val coding = GalaxyCatalog.findById("coding")!!
        val normalState = GalaxyCatalog.findById("normal")!!

        assertTrue(CompanionSynergy.isQuestMatch(creative, QuestCategory.CREATIVE))
        assertFalse(CompanionSynergy.isQuestMatch(creative, QuestCategory.READING))
        assertTrue(CompanionSynergy.isQuestMatch(coding, QuestCategory.CODING))
        assertFalse(CompanionSynergy.isQuestMatch(normalState, QuestCategory.STUDY))
    }

    @Test
    fun `focus match requires clean unlinked session of at least 10 minutes and matching activity`() {
        val creative = GalaxyCatalog.findById("creative")!!
        val reading = GalaxyCatalog.findById("reading")!!

        assertTrue(
            CompanionSynergy.isFocusMatch(
                companion = creative,
                activityType = FocusActivityType.CREATIVE,
                linkedQuestAttemptId = null,
                violationCount = 0,
                durationMinutes = 10L
            )
        )
        assertFalse(
            CompanionSynergy.isFocusMatch(
                companion = creative,
                activityType = FocusActivityType.STUDY,
                linkedQuestAttemptId = null,
                violationCount = 0,
                durationMinutes = 25L
            )
        )
        assertFalse(
            CompanionSynergy.isFocusMatch(
                companion = creative,
                activityType = FocusActivityType.CREATIVE,
                linkedQuestAttemptId = "attempt-123",
                violationCount = 0,
                durationMinutes = 25L
            )
        )
        assertFalse(
            CompanionSynergy.isFocusMatch(
                companion = creative,
                activityType = FocusActivityType.CREATIVE,
                linkedQuestAttemptId = null,
                violationCount = 1,
                durationMinutes = 25L
            )
        )
        assertFalse(
            CompanionSynergy.isFocusMatch(
                companion = creative,
                activityType = FocusActivityType.CREATIVE,
                linkedQuestAttemptId = null,
                violationCount = 0,
                durationMinutes = 9L
            )
        )
        // Work members without a focusActivity can never match a Focus session.
        assertFalse(
            CompanionSynergy.isFocusMatch(
                companion = reading,
                activityType = FocusActivityType.STUDY,
                linkedQuestAttemptId = null,
                violationCount = 0,
                durationMinutes = 25L
            )
        )
    }

    @Test
    fun `focus eligibility enforces clean unlinked and minimum 10 minute floor`() {
        assertTrue(CompanionSynergy.isFocusEligible(null, 0, 10L))
        assertFalse(CompanionSynergy.isFocusEligible("att-1", 0, 25L))
        assertFalse(CompanionSynergy.isFocusEligible(null, 2, 25L))
        assertFalse(CompanionSynergy.isFocusEligible(null, 0, 1L))
        assertFalse(CompanionSynergy.isFocusEligible(null, 0, 0L))
    }

    @Test
    fun `reward multipliers return expected bonus minutes and bond xp`() {
        assertEquals(2L, CompanionSynergy.synergyBonusMinutes(synergyMatched = true))
        assertEquals(0L, CompanionSynergy.synergyBonusMinutes(synergyMatched = false))
        assertEquals(2, CompanionSynergy.bondDelta(synergyMatched = true))
        assertEquals(1, CompanionSynergy.bondDelta(synergyMatched = false))
    }

    @Test
    fun `bond ladder boundaries advance accurately across all four tiers`() {
        assertEquals(BondTier.PEMULA, CompanionSynergy.tierFor(-5))
        assertEquals(1, CompanionSynergy.rankFor(0))
        assertEquals(0 to 5, CompanionSynergy.progressWithin(0))
        assertEquals(0f, CompanionSynergy.shareToNext(0), 0.001f)

        assertEquals(BondTier.PEMULA, CompanionSynergy.tierFor(4))
        assertEquals(4 to 5, CompanionSynergy.progressWithin(4))
        assertEquals(0.8f, CompanionSynergy.shareToNext(4), 0.001f)

        assertEquals(BondTier.SELARAS, CompanionSynergy.tierFor(5))
        assertEquals(2, CompanionSynergy.rankFor(5))
        assertEquals(0 to 10, CompanionSynergy.progressWithin(5))

        assertEquals(BondTier.SELARAS, CompanionSynergy.tierFor(14))
        assertEquals(9 to 10, CompanionSynergy.progressWithin(14))

        assertEquals(BondTier.AKRAB, CompanionSynergy.tierFor(15))
        assertEquals(3, CompanionSynergy.rankFor(15))
        assertEquals(0 to 20, CompanionSynergy.progressWithin(15))

        assertEquals(BondTier.AKRAB, CompanionSynergy.tierFor(34))
        assertEquals(19 to 20, CompanionSynergy.progressWithin(34))

        assertEquals(BondTier.MASTER, CompanionSynergy.tierFor(35))
        assertEquals(4, CompanionSynergy.rankFor(35))
        assertNull(CompanionSynergy.progressWithin(35))
        assertNull(CompanionSynergy.progressWithin(100))
        assertEquals(1f, CompanionSynergy.shareToNext(35), 0.001f)
    }
}
