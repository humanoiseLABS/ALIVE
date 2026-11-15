package com.projectego.app.domain

import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberKind
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberSpec
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory

/**
 * Pure synergy, eligibility, and Bond progression math for the 11 active work companions.
 */
object CompanionSynergy {

    const val SYNERGY_BONUS_MINUTES = 2L
    const val MIN_ELIGIBLE_FOCUS_MINUTES = 10L
    const val BASE_BOND_XP = 1
    const val SYNERGY_BOND_XP = 2
    const val MAX_RANK = 4

    val RANK_THRESHOLDS: List<Int> = listOf(5, 15, 35)

    enum class BondTier(val rank: Int) {
        PEMULA(1),
        SELARAS(2),
        AKRAB(3),
        MASTER(4)
    }

    fun isQuestMatch(companion: GalaxyMemberSpec, category: QuestCategory): Boolean =
        companion.kind == GalaxyMemberKind.WORK && companion.questCategory == category

    fun isFocusEligible(
        linkedQuestAttemptId: String?,
        violationCount: Int,
        durationMinutes: Long
    ): Boolean = linkedQuestAttemptId.isNullOrBlank() &&
        violationCount <= 0 &&
        durationMinutes >= MIN_ELIGIBLE_FOCUS_MINUTES

    fun isFocusMatch(
        companion: GalaxyMemberSpec,
        activityType: FocusActivityType,
        linkedQuestAttemptId: String?,
        violationCount: Int,
        durationMinutes: Long
    ): Boolean = isFocusEligible(linkedQuestAttemptId, violationCount, durationMinutes) &&
        companion.kind == GalaxyMemberKind.WORK &&
        companion.focusActivity == activityType

    fun bondDelta(synergyMatched: Boolean): Int =
        if (synergyMatched) SYNERGY_BOND_XP else BASE_BOND_XP

    fun synergyBonusMinutes(synergyMatched: Boolean): Long =
        if (synergyMatched) SYNERGY_BONUS_MINUTES else 0L

    fun tierFor(xp: Int): BondTier {
        val safe = xp.coerceAtLeast(0)
        return when {
            safe >= RANK_THRESHOLDS[2] -> BondTier.MASTER
            safe >= RANK_THRESHOLDS[1] -> BondTier.AKRAB
            safe >= RANK_THRESHOLDS[0] -> BondTier.SELARAS
            else -> BondTier.PEMULA
        }
    }

    fun rankFor(xp: Int): Int = tierFor(xp).rank

    /** Progress inside the current tier as (earnedInTier, tierSpan), or null when Master. */
    fun progressWithin(xp: Int): Pair<Int, Int>? {
        val safe = xp.coerceAtLeast(0)
        return when (tierFor(safe)) {
            BondTier.PEMULA -> safe to RANK_THRESHOLDS[0]
            BondTier.SELARAS -> (safe - RANK_THRESHOLDS[0]) to (RANK_THRESHOLDS[1] - RANK_THRESHOLDS[0])
            BondTier.AKRAB -> (safe - RANK_THRESHOLDS[1]) to (RANK_THRESHOLDS[2] - RANK_THRESHOLDS[1])
            BondTier.MASTER -> null
        }
    }

    fun shareToNext(xp: Int): Float {
        val window = progressWithin(xp) ?: return 1f
        return (window.first.toFloat() / window.second.toFloat()).coerceIn(0f, 1f)
    }
}
