package com.projectego.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.data.CharacterFamilyRepository.VictorySource
import com.projectego.app.domain.CharacterGrowth
import com.projectego.app.domain.GalaxyCatalog
import com.projectego.app.domain.GalaxyCatalog.GalaxyStateKind
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.TimeStatus
import com.projectego.app.ui.AliveSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CharacterFamilyRepositoryTest {

    private lateinit var context: Context
    private lateinit var familyRepo: CharacterFamilyRepository
    private lateinit var usageHistory: UsageHistoryRepository
    private lateinit var growthRepo: CharacterGrowthRepository
    private lateinit var questHistory: QuestHistoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(
            CharacterFamilyRepository.PREFS_NAME,
            UsageHistoryRepository.PREFS_NAME,
            QuestHistoryRepository.PREFS_NAME,
            "project_ego_character_growth",
            "project_ego_quests",
            "project_ego_time_bank",
            "project_ego_focus_session"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        familyRepo = CharacterFamilyRepository(context)
        usageHistory = UsageHistoryRepository(context)
        growthRepo = CharacterGrowthRepository(context)
        questHistory = QuestHistoryRepository(context)
    }

    @Test
    fun `creative starts awakened as default companion while dormant and state members cannot be picked`() {
        assertEquals(GalaxyCatalog.DEFAULT_COMPANION_ID, familyRepo.activeCompanionId())

        assertFalse(familyRepo.setActiveCompanion("coding"))
        assertFalse(familyRepo.setActiveCompanion("normal"))
        assertFalse(familyRepo.setActiveCompanion("rest"))
        assertEquals("creative", familyRepo.activeCompanionId())

        val statuses = familyRepo.snapshot(
            usageHistory = usageHistory,
            growthLedger = growthRepo.ledger("2026-11-15"),
            signals = null,
            activePermissions = 2,
            todayKey = "2026-11-15"
        )
        val creative = statuses.first { it.spec.id == "creative" }
        val coding = statuses.first { it.spec.id == "coding" }
        assertTrue(creative.awakened)
        assertTrue(creative.isCompanion)
        assertFalse(coding.awakened)
        assertFalse(coding.isCompanion)
    }

    @Test
    fun `quest and focus recordings awaken members, aggregate counts cleanly, and deduplicate replays`() {
        assertTrue(familyRepo.recordQuestRewarded("attempt-1", QuestCategory.READING))
        assertFalse(familyRepo.recordQuestRewarded("attempt-1", QuestCategory.READING))

        assertTrue(familyRepo.recordFocusCompleted("win_1_2_CODING", FocusActivityType.CODING, 25L))
        assertFalse(familyRepo.recordFocusCompleted("win_1_2_CODING", FocusActivityType.CODING, 25L))
        assertTrue(familyRepo.recordFocusCompleted("win_3_4_CODING", FocusActivityType.CODING, 30L))

        assertTrue(familyRepo.setActiveCompanion("coding"))
        assertEquals("coding", familyRepo.activeCompanionId())

        val statuses = familyRepo.snapshot(
            usageHistory = usageHistory,
            growthLedger = growthRepo.ledger("2026-11-15"),
            signals = null,
            activePermissions = 2,
            todayKey = "2026-11-15"
        )
        val reading = statuses.first { it.spec.id == "reading" }
        val coding = statuses.first { it.spec.id == "coding" }

        assertTrue(reading.awakened)
        assertEquals(1, reading.count)
        assertEquals(0L, reading.focusMinutes)

        assertTrue(coding.awakened)
        assertTrue(coding.isCompanion)
        assertEquals(2, coding.count)
        assertEquals(55L, coding.focusMinutes)
    }

    @Test
    fun `syncFromExistingStores backfills quest history and today focus windows once without doubling`() {
        val timeBank = TimeBankRepository(context)
        val questRepo = QuestRepository(context, timeBank)
        val focusManager = FocusSessionManager(context, timeBank)
        val walkingQuest = questRepo.getAllQuests().first { it.category == QuestCategory.WALKING }

        val todayKey = LockStateManager.dayKeyOf(System.currentTimeMillis())
        questHistory.recordCompletion(
            dayKey = todayKey,
            attemptId = "hist-att-1",
            questId = walkingQuest.id,
            title = walkingQuest.title,
            minutes = 15L,
            completedAt = 1_700_000_000_000L
        )
        assertTrue(focusManager.startFocusSession(FocusActivityType.WRITING, "Draft outline", 30L))
        val pastEnd = System.currentTimeMillis() + 31L * 60_000L

        familyRepo.syncFromExistingStores(
            questRepository = questRepo,
            questHistory = questHistory,
            focusSessionManager = focusManager,
            todayKey = todayKey,
            nowMillis = pastEnd
        )
        // Re-running sync must remain idempotent.
        familyRepo.syncFromExistingStores(
            questRepository = questRepo,
            questHistory = questHistory,
            focusSessionManager = focusManager,
            todayKey = todayKey,
            nowMillis = pastEnd
        )

        val statuses = familyRepo.snapshot(
            usageHistory = usageHistory,
            growthLedger = growthRepo.ledger(todayKey),
            signals = null,
            activePermissions = 2,
            todayKey = todayKey
        )
        val walking = statuses.first { it.spec.id == "walking" }
        val writing = statuses.first { it.spec.id == "writing" }

        assertEquals(1, walking.count)
        assertTrue(walking.awakened)
        assertEquals(1, writing.count)
        assertEquals(30L, writing.focusMinutes)
        assertTrue(writing.awakened)
        assertEquals(0L, timeBank.getTimeBank().earnedMinutes)
        assertEquals(0, familyRepo.bondXp("walking"))
        assertEquals(0, familyRepo.bondXp("writing"))
        assertEquals(0, walking.bondXp)
        assertEquals(0, writing.bondXp)
        assertNull(familyRepo.peekPendingVictory())
        assertEquals(0, familyRepo.pendingVictoryCount())
    }

    @Test
    fun `live quest completion matching companion grants +2m synergy, 2x bond xp, and queues once`() {
        val outcome = familyRepo.recordQuestCompletionLive(
            attemptId = "att-match-1",
            category = QuestCategory.CREATIVE,
            questTitle = "Sketsa bebas",
            baseRewardMinutes = 15L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE
        )

        assertTrue(outcome.recorded)
        assertEquals(2L, outcome.synergyBonusMinutes)
        assertEquals(2, familyRepo.bondXp("creative"))
        val event = outcome.event
        assertNotNull(event)
        assertEquals("quest_att-match-1", event!!.eventId)
        assertEquals(VictorySource.QUEST, event.source)
        assertEquals("creative", event.memberId)
        assertEquals("creative", event.companionId)
        assertTrue(event.synergyMatched)
        assertFalse(event.newlyAwakened)
        assertEquals(2, event.bondDelta)
        assertEquals(0, event.bondXpBefore)
        assertEquals(2, event.bondXpAfter)
        assertEquals(1, event.rankBefore)
        assertEquals(1, event.rankAfter)
        assertFalse(event.growthStageUp)
        assertEquals(1, familyRepo.pendingVictoryCount())

        val replay = familyRepo.recordQuestCompletionLive(
            attemptId = "att-match-1",
            category = QuestCategory.CREATIVE,
            questTitle = "Sketsa bebas",
            baseRewardMinutes = 15L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE
        )
        assertFalse(replay.recorded)
        assertEquals(0L, replay.synergyBonusMinutes)
        assertNull(replay.event)
        assertEquals(2, familyRepo.bondXp("creative"))
        assertEquals(1, familyRepo.pendingVictoryCount())
    }

    @Test
    fun `live non-matching quest awakens member, records +1 bond xp, and flags stage level up`() {
        val outcome = familyRepo.recordQuestCompletionLive(
            attemptId = "att-read-1",
            category = QuestCategory.READING,
            questTitle = "Baca buku",
            baseRewardMinutes = 15L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.TWO
        )

        assertTrue(outcome.recorded)
        assertEquals(0L, outcome.synergyBonusMinutes)
        assertEquals(1, familyRepo.bondXp("reading"))
        val event = outcome.event!!
        assertEquals("reading", event.memberId)
        assertTrue(event.newlyAwakened)
        assertFalse(event.synergyMatched)
        assertEquals(1, event.bondDelta)
        assertTrue(event.growthStageUp)
        assertEquals(2, event.growthLevelAfter)
    }

    @Test
    fun `live focus completion respects eligibility flag and awards 2x rank-up cleanly`() {
        val ineligible = familyRepo.recordFocusCompletionLive(
            dedupKey = "win_short_1",
            activityType = FocusActivityType.CREATIVE,
            durationMinutes = 1L,
            goalTitle = "Test 15s Goal",
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE,
            eligible = false
        )
        assertFalse(ineligible.recorded)
        assertEquals(0L, ineligible.synergyBonusMinutes)
        assertNull(ineligible.event)
        assertEquals(0, familyRepo.bondXp("creative"))
        assertEquals(0, familyRepo.pendingVictoryCount())

        // Warm creative to 4 XP via two matched quests, then rank up to Rank 2 (5+ XP) via Focus.
        familyRepo.recordQuestCompletionLive("q-w1", QuestCategory.CREATIVE, "A", 15L, null, null)
        familyRepo.recordQuestCompletionLive("q-w2", QuestCategory.CREATIVE, "B", 15L, null, null)
        assertEquals(4, familyRepo.bondXp("creative"))

        val focusOutcome = familyRepo.recordFocusCompletionLive(
            dedupKey = "win_focus_1",
            activityType = FocusActivityType.CREATIVE,
            durationMinutes = 25L,
            goalTitle = "Ngerjain vektor",
            growthStageBefore = CharacterGrowth.Stage.TWO,
            growthStageAfter = CharacterGrowth.Stage.TWO,
            eligible = true
        )
        assertTrue(focusOutcome.recorded)
        assertEquals(2L, focusOutcome.synergyBonusMinutes)
        assertEquals(6, familyRepo.bondXp("creative"))
        val event = focusOutcome.event!!
        assertEquals(VictorySource.FOCUS, event.source)
        assertEquals(0L, event.baseMinutes)
        assertEquals(25L, event.durationMinutes)
        assertEquals(4, event.bondXpBefore)
        assertEquals(6, event.bondXpAfter)
        assertEquals(1, event.rankBefore)
        assertEquals(2, event.rankAfter)
    }

    @Test
    fun `pending victory queue enforces FIFO cap of 3 and permanent seen dedup across restarts`() {
        repeat(4) { index ->
            familyRepo.recordQuestCompletionLive(
                attemptId = "q-${index + 1}",
                category = QuestCategory.STUDY,
                questTitle = "Belajar ${index + 1}",
                baseRewardMinutes = 15L,
                growthStageBefore = CharacterGrowth.Stage.ONE,
                growthStageAfter = CharacterGrowth.Stage.ONE
            )
        }

        assertEquals(3, familyRepo.pendingVictoryCount())
        val first = familyRepo.peekPendingVictory()
        assertNotNull(first)
        assertEquals("quest_q-2", first!!.eventId)

        val second = familyRepo.consumePendingVictory("quest_q-2")
        assertEquals("quest_q-3", second?.eventId)
        val third = familyRepo.consumePendingVictory("quest_q-3")
        assertEquals("quest_q-4", third?.eventId)
        val afterAll = familyRepo.consumePendingVictory("quest_q-4")
        assertNull(afterAll)
        assertEquals(0, familyRepo.pendingVictoryCount())

        val recreated = CharacterFamilyRepository(context)
        assertEquals(0, recreated.pendingVictoryCount())
        assertTrue(recreated.hasSeenOrPendingVictory("quest_q-2"))
        assertEquals(4, recreated.bondXp("study"))
        val replay = recreated.recordQuestCompletionLive(
            attemptId = "q-2",
            category = QuestCategory.STUDY,
            questTitle = "Belajar 2",
            baseRewardMinutes = 15L,
            growthStageBefore = CharacterGrowth.Stage.ONE,
            growthStageAfter = CharacterGrowth.Stage.ONE
        )
        assertFalse(replay.recorded)
        assertNull(replay.event)
        assertEquals(0, recreated.pendingVictoryCount())
    }

    @Test
    fun `snapshot accurately counts all 6 day-state mirrors across history gaps, permissions, and today telemetry`() {
        val minuteMs = 60_000L
        // Span 2026-11-10 -> 2026-11-15:
        // 11-10 normal (30/120), 11-11 near (95/120, rem=25<=30), 11-12 spent (120/120),
        // 11-13 MISSING -> unknown, 11-14 recovery (limit=0), 11-15 today + missing perm + resting + promise.
        usageHistory.recordDay("2026-11-10", 30 * minuteMs, 120L, emptyMap(), 60 * minuteMs)
        usageHistory.recordDay("2026-11-11", 95 * minuteMs, 120L, emptyMap(), 100 * minuteMs)
        usageHistory.recordDay("2026-11-12", 120 * minuteMs, 120L, emptyMap(), 130 * minuteMs)
        usageHistory.recordDay("2026-11-14", 0L, 0L, emptyMap(), 20 * minuteMs)
        usageHistory.recordDay("2026-11-15", 20 * minuteMs, 120L, emptyMap(), 40 * minuteMs)

        growthRepo.recordPromiseDay("2026-11-14")
        val ledger = growthRepo.ledger("2026-11-15")
        val signals = AliveSignals(
            timeStatus = TimeStatus.ACTIVE,
            dayType = DayType.NORMAL_DAY,
            criticalZoneTier = 0,
            remainingMinutes = 100L,
            currentStreak = 3,
            reclaimedAvailableMinutes = 15L,
            attemptState = null,
            focusActive = false,
            restingToday = true
        )

        val statuses = familyRepo.snapshot(
            usageHistory = usageHistory,
            growthLedger = ledger,
            signals = signals,
            activePermissions = 1,
            todayKey = "2026-11-15"
        )
        fun countOf(kind: GalaxyStateKind): Int =
            statuses.first { it.spec.stateKind == kind }.count

        assertEquals(2, countOf(GalaxyStateKind.NORMAL))   // 11-10 + today 11-15
        assertEquals(1, countOf(GalaxyStateKind.NEAR))     // 11-11
        assertEquals(1, countOf(GalaxyStateKind.SPENT))    // 11-12
        assertEquals(1, countOf(GalaxyStateKind.RECOVERY)) // 11-14
        assertEquals(2, countOf(GalaxyStateKind.UNKNOWN))  // missing 11-13 + missing permission today
        assertEquals(2, countOf(GalaxyStateKind.REST))     // 1 promise day + restingToday
    }
}
