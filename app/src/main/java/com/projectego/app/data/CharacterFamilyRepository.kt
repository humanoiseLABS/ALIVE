package com.projectego.app.data

import android.content.Context
import android.content.SharedPreferences
import com.projectego.app.domain.CharacterGrowth
import com.projectego.app.domain.CompanionSynergy
import com.projectego.app.domain.GalaxyCatalog
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberKind
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberSpec
import com.projectego.app.domain.GalaxyCatalog.GalaxyStateKind
import com.projectego.app.model.DayType
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory
import com.projectego.app.model.TimeStatus
import com.projectego.app.ui.AliveSignals
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Persistent habit trail, soft-awakening state, companion Bond progression,
 * active companion selection, and single-shot pending Victory queue for the
 * 17-character family. Isolated in its own SharedPreferences file so
 * day-rollover locks never depend on character history.
 */
class CharacterFamilyRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class VictorySource { QUEST, FOCUS }

    data class VictoryEvent(
        val eventId: String,
        val source: VictorySource,
        val memberId: String,
        val companionId: String,
        val title: String,
        val baseMinutes: Long,
        val synergyBonusMinutes: Long,
        val durationMinutes: Long,
        val synergyMatched: Boolean,
        val newlyAwakened: Boolean,
        val bondDelta: Int,
        val bondXpBefore: Int,
        val bondXpAfter: Int,
        val rankBefore: Int,
        val rankAfter: Int,
        val growthStageUp: Boolean,
        val growthLevelAfter: Int
    )

    data class SettlementOutcome(
        val recorded: Boolean,
        val synergyBonusMinutes: Long,
        val event: VictoryEvent?
    )

    data class MemberStatus(
        val spec: GalaxyMemberSpec,
        val awakened: Boolean,
        val count: Int,
        val focusMinutes: Long,
        val isCompanion: Boolean,
        val bondXp: Int = 0
    )

    companion object {
        const val PREFS_NAME = "project_ego_character_family"
        const val PREFIX_BOND_XP = "work_bond_xp_"
        const val KEY_PENDING_VICTORIES = "key_pending_victories"
        const val KEY_SEEN_VICTORY_IDS = "key_seen_victory_ids"
        const val MAX_PENDING_VICTORIES = 3

        private const val KEY_ACTIVE_COMPANION = "key_active_companion"
        private const val KEY_BACKFILL_V1 = "key_backfill_v1"
        private const val KEY_RECORDED_QUEST_ATTEMPTS = "key_recorded_quest_attempts"
        private const val KEY_RECORDED_FOCUS_KEYS = "key_recorded_focus_keys"
        private const val PREFIX_COUNT = "work_count_"
        private const val PREFIX_FOCUS_MINUTES = "work_focus_min_"
        private const val PREFIX_AWAKENED = "work_awakened_"
        private const val CRITICAL_ZONE_THRESHOLD_MINUTES = 30L
        private const val MAX_SEEN_IDS = 512

        fun focusDeduplicationKey(
            startTimestamp: Long,
            endTimestamp: Long,
            activityType: FocusActivityType
        ): String = "win_${startTimestamp}_${endTimestamp}_${activityType.name}"
    }

    @Synchronized
    fun activeCompanionId(): String {
        val stored = prefs.getString(KEY_ACTIVE_COMPANION, null)
        val spec = GalaxyCatalog.findById(stored)
        return if (spec != null && spec.kind == GalaxyMemberKind.WORK && isWorkAwakened(spec.id)) {
            spec.id
        } else {
            GalaxyCatalog.DEFAULT_COMPANION_ID
        }
    }

    fun activeCompanionSpec(): GalaxyMemberSpec =
        GalaxyCatalog.findById(activeCompanionId()) ?: GalaxyCatalog.defaultCompanion()

    @Synchronized
    fun setActiveCompanion(id: String): Boolean {
        val spec = GalaxyCatalog.findById(id) ?: return false
        if (spec.kind != GalaxyMemberKind.WORK) return false
        if (!isWorkAwakened(spec.id)) return false
        prefs.edit().putString(KEY_ACTIVE_COMPANION, spec.id).apply()
        return true
    }

    @Synchronized
    fun bondXp(memberId: String): Int {
        val spec = GalaxyCatalog.findById(memberId) ?: return 0
        if (spec.kind != GalaxyMemberKind.WORK) return 0
        return prefs.getInt(PREFIX_BOND_XP + spec.id, 0).coerceAtLeast(0)
    }

    @Synchronized
    fun peekPendingVictory(): VictoryEvent? = loadPendingVictoriesLocked().firstOrNull()

    @Synchronized
    fun pendingVictoryCount(): Int = loadPendingVictoriesLocked().size

    @Synchronized
    fun hasSeenOrPendingVictory(eventId: String): Boolean {
        if (eventId.isBlank()) return false
        if (loadPendingVictoriesLocked().any { it.eventId == eventId }) return true
        val seen = prefs.getStringSet(KEY_SEEN_VICTORY_IDS, emptySet()) ?: emptySet()
        return seen.contains(eventId)
    }

    @Synchronized
    fun consumePendingVictory(eventId: String): VictoryEvent? {
        val current = loadPendingVictoriesLocked()
        val remaining = current.filterNot { it.eventId == eventId }
        if (remaining.size != current.size) {
            prefs.edit().putString(KEY_PENDING_VICTORIES, encodeVictoryEvents(remaining)).apply()
        }
        return remaining.firstOrNull()
    }

    @Synchronized
    fun recordQuestRewarded(attemptId: String, category: QuestCategory): Boolean {
        if (attemptId.isBlank()) return false
        return recordQuestWithKey("attempt_$attemptId", category)
    }

    @Synchronized
    fun recordQuestCompletionLive(
        attemptId: String,
        category: QuestCategory,
        questTitle: String,
        baseRewardMinutes: Long,
        growthStageBefore: CharacterGrowth.Stage?,
        growthStageAfter: CharacterGrowth.Stage?
    ): SettlementOutcome {
        if (attemptId.isBlank()) {
            return SettlementOutcome(recorded = false, synergyBonusMinutes = 0L, event = null)
        }
        val seenAttempts = loadSeenSet(KEY_RECORDED_QUEST_ATTEMPTS)
        if (!addWithTrim(seenAttempts, "attempt_$attemptId")) {
            return SettlementOutcome(recorded = false, synergyBonusMinutes = 0L, event = null)
        }

        val spec = GalaxyCatalog.forQuestCategory(category)
        val companion = activeCompanionSpec()
        val matched = CompanionSynergy.isQuestMatch(companion, category)
        val wasAwakened = isWorkAwakened(spec.id)
        val prevCount = prefs.getInt(PREFIX_COUNT + spec.id, 0)

        val editor = prefs.edit()
            .putStringSet(KEY_RECORDED_QUEST_ATTEMPTS, seenAttempts)
            .putInt(PREFIX_COUNT + spec.id, prevCount + 1)
            .putBoolean(PREFIX_AWAKENED + spec.id, true)

        return applyLiveSynergyAndQueueLocked(
            editor = editor,
            eventId = "quest_$attemptId",
            source = VictorySource.QUEST,
            spec = spec,
            companion = companion,
            title = questTitle,
            baseMinutes = baseRewardMinutes.coerceAtLeast(0L),
            durationMinutes = 0L,
            synergyMatched = matched,
            newlyAwakened = !wasAwakened,
            growthStageBefore = growthStageBefore,
            growthStageAfter = growthStageAfter
        )
    }

    @Synchronized
    fun recordFocusCompleted(
        dedupKey: String,
        activityType: FocusActivityType,
        durationMinutes: Long
    ): Boolean {
        if (dedupKey.isBlank()) return false
        val seen = loadSeenSet(KEY_RECORDED_FOCUS_KEYS)
        if (!addWithTrim(seen, dedupKey)) return false
        val spec = GalaxyCatalog.forFocusActivity(activityType)
        val prevCount = prefs.getInt(PREFIX_COUNT + spec.id, 0)
        val prevMinutes = prefs.getLong(PREFIX_FOCUS_MINUTES + spec.id, 0L)
        prefs.edit()
            .putStringSet(KEY_RECORDED_FOCUS_KEYS, seen)
            .putInt(PREFIX_COUNT + spec.id, prevCount + 1)
            .putLong(PREFIX_FOCUS_MINUTES + spec.id, prevMinutes + durationMinutes.coerceAtLeast(0L))
            .putBoolean(PREFIX_AWAKENED + spec.id, true)
            .apply()
        return true
    }

    @Synchronized
    fun recordFocusCompletionLive(
        dedupKey: String,
        activityType: FocusActivityType,
        durationMinutes: Long,
        goalTitle: String,
        growthStageBefore: CharacterGrowth.Stage?,
        growthStageAfter: CharacterGrowth.Stage?,
        eligible: Boolean
    ): SettlementOutcome {
        if (!eligible || dedupKey.isBlank()) {
            return SettlementOutcome(recorded = false, synergyBonusMinutes = 0L, event = null)
        }
        val seenFocus = loadSeenSet(KEY_RECORDED_FOCUS_KEYS)
        if (!addWithTrim(seenFocus, dedupKey)) {
            return SettlementOutcome(recorded = false, synergyBonusMinutes = 0L, event = null)
        }

        val safeMinutes = durationMinutes.coerceAtLeast(0L)
        val spec = GalaxyCatalog.forFocusActivity(activityType)
        val companion = activeCompanionSpec()
        val matched = CompanionSynergy.isFocusMatch(
            companion = companion,
            activityType = activityType,
            linkedQuestAttemptId = null,
            violationCount = 0,
            durationMinutes = safeMinutes
        )
        val wasAwakened = isWorkAwakened(spec.id)
        val prevCount = prefs.getInt(PREFIX_COUNT + spec.id, 0)
        val prevMinutes = prefs.getLong(PREFIX_FOCUS_MINUTES + spec.id, 0L)

        val editor = prefs.edit()
            .putStringSet(KEY_RECORDED_FOCUS_KEYS, seenFocus)
            .putInt(PREFIX_COUNT + spec.id, prevCount + 1)
            .putLong(PREFIX_FOCUS_MINUTES + spec.id, prevMinutes + safeMinutes)
            .putBoolean(PREFIX_AWAKENED + spec.id, true)

        return applyLiveSynergyAndQueueLocked(
            editor = editor,
            eventId = "focus_$dedupKey",
            source = VictorySource.FOCUS,
            spec = spec,
            companion = companion,
            title = goalTitle,
            baseMinutes = 0L,
            durationMinutes = safeMinutes,
            synergyMatched = matched,
            newlyAwakened = !wasAwakened,
            growthStageBefore = growthStageBefore,
            growthStageAfter = growthStageAfter
        )
    }

    @Synchronized
    fun syncFromExistingStores(
        questRepository: QuestRepository,
        questHistory: QuestHistoryRepository,
        focusSessionManager: FocusSessionManager,
        todayKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!prefs.getBoolean(KEY_BACKFILL_V1, false)) {
            val categoryByQuestId = questRepository.getAllQuests().associate { it.id to it.category }
            questHistory.recent(todayKey).forEach { day ->
                day.completions.forEach { completion ->
                    val category = categoryByQuestId[completion.questId] ?: QuestCategory.OTHER
                    recordQuestWithKey(
                        "hist_${completion.questId}_${completion.completedAt}",
                        category
                    )
                }
            }
            prefs.edit().putBoolean(KEY_BACKFILL_V1, true).apply()
        }

        focusSessionManager.getTodayWindows()
            .filter { it.end <= nowMillis && it.end > it.start }
            .forEach { window ->
                val minutes = ((window.end - window.start) / 60_000L).coerceAtLeast(1L)
                recordFocusCompleted(
                    focusDeduplicationKey(window.start, window.end, window.activityType),
                    window.activityType,
                    minutes
                )
            }
    }

    fun snapshot(
        usageHistory: UsageHistoryRepository,
        growthLedger: CharacterGrowthRepository.Ledger,
        signals: AliveSignals?,
        activePermissions: Int,
        todayKey: String
    ): List<MemberStatus> {
        val companionId = activeCompanionId()
        val stateCounts = computeStateCounts(
            usageHistory = usageHistory,
            growthLedger = growthLedger,
            signals = signals,
            activePermissions = activePermissions,
            todayKey = todayKey
        )

        return GalaxyCatalog.members.map { spec ->
            when (spec.kind) {
                GalaxyMemberKind.WORK -> {
                    val count = prefs.getInt(PREFIX_COUNT + spec.id, 0)
                    val focusMinutes = prefs.getLong(PREFIX_FOCUS_MINUTES + spec.id, 0L)
                    MemberStatus(
                        spec = spec,
                        awakened = isWorkAwakened(spec.id),
                        count = count,
                        focusMinutes = focusMinutes,
                        isCompanion = spec.id == companionId,
                        bondXp = bondXp(spec.id)
                    )
                }

                GalaxyMemberKind.DAY_STATE -> {
                    val count = stateCounts[spec.stateKind] ?: 0
                    MemberStatus(
                        spec = spec,
                        awakened = count > 0,
                        count = count,
                        focusMinutes = 0L,
                        isCompanion = false,
                        bondXp = 0
                    )
                }
            }
        }
    }

    private fun isWorkAwakened(id: String): Boolean {
        if (id == GalaxyCatalog.DEFAULT_COMPANION_ID) return true
        return prefs.getBoolean(PREFIX_AWAKENED + id, false) ||
            prefs.getInt(PREFIX_COUNT + id, 0) > 0
    }

    private fun recordQuestWithKey(dedupKey: String, category: QuestCategory): Boolean {
        val seen = loadSeenSet(KEY_RECORDED_QUEST_ATTEMPTS)
        if (!addWithTrim(seen, dedupKey)) return false
        val spec = GalaxyCatalog.forQuestCategory(category)
        val prevCount = prefs.getInt(PREFIX_COUNT + spec.id, 0)
        prefs.edit()
            .putStringSet(KEY_RECORDED_QUEST_ATTEMPTS, seen)
            .putInt(PREFIX_COUNT + spec.id, prevCount + 1)
            .putBoolean(PREFIX_AWAKENED + spec.id, true)
            .apply()
        return true
    }

    private fun applyLiveSynergyAndQueueLocked(
        editor: SharedPreferences.Editor,
        eventId: String,
        source: VictorySource,
        spec: GalaxyMemberSpec,
        companion: GalaxyMemberSpec,
        title: String,
        baseMinutes: Long,
        durationMinutes: Long,
        synergyMatched: Boolean,
        newlyAwakened: Boolean,
        growthStageBefore: CharacterGrowth.Stage?,
        growthStageAfter: CharacterGrowth.Stage?
    ): SettlementOutcome {
        val bondBefore = bondXp(spec.id)
        val bondDelta = CompanionSynergy.bondDelta(synergyMatched)
        val bondAfter = bondBefore + bondDelta
        val bonusMinutes = CompanionSynergy.synergyBonusMinutes(synergyMatched)
        val growthStageUp = growthStageBefore != null &&
            growthStageAfter != null &&
            growthStageAfter.ordinal > growthStageBefore.ordinal
        val growthLevelAfter = (growthStageAfter?.ordinal ?: growthStageBefore?.ordinal ?: 0) + 1

        editor.putInt(PREFIX_BOND_XP + spec.id, bondAfter)

        val seenVictories = loadSeenSet(KEY_SEEN_VICTORY_IDS)
        val queuedEvent = if (addWithTrim(seenVictories, eventId)) {
            val event = VictoryEvent(
                eventId = eventId,
                source = source,
                memberId = spec.id,
                companionId = companion.id,
                title = title,
                baseMinutes = baseMinutes,
                synergyBonusMinutes = bonusMinutes,
                durationMinutes = durationMinutes,
                synergyMatched = synergyMatched,
                newlyAwakened = newlyAwakened,
                bondDelta = bondDelta,
                bondXpBefore = bondBefore,
                bondXpAfter = bondAfter,
                rankBefore = CompanionSynergy.rankFor(bondBefore),
                rankAfter = CompanionSynergy.rankFor(bondAfter),
                growthStageUp = growthStageUp,
                growthLevelAfter = growthLevelAfter
            )
            val updatedQueue = (loadPendingVictoriesLocked() + event).let { queue ->
                if (queue.size > MAX_PENDING_VICTORIES) queue.takeLast(MAX_PENDING_VICTORIES) else queue
            }
            editor
                .putStringSet(KEY_SEEN_VICTORY_IDS, seenVictories)
                .putString(KEY_PENDING_VICTORIES, encodeVictoryEvents(updatedQueue))
            event
        } else {
            null
        }

        editor.apply()
        return SettlementOutcome(
            recorded = true,
            synergyBonusMinutes = bonusMinutes,
            event = queuedEvent
        )
    }

    private fun loadSeenSet(key: String): HashSet<String> =
        HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())

    private fun addWithTrim(seen: HashSet<String>, id: String): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > MAX_SEEN_IDS) {
            val trimmed = seen.toList().takeLast(MAX_SEEN_IDS / 2).toHashSet()
            seen.clear()
            seen.addAll(trimmed)
        }
        return true
    }

    private fun loadPendingVictoriesLocked(): List<VictoryEvent> {
        val raw = prefs.getString(KEY_PENDING_VICTORIES, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    decodeVictoryEvent(obj)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeVictoryEvents(events: List<VictoryEvent>): String {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject().apply {
                    put("eventId", event.eventId)
                    put("source", event.source.name)
                    put("memberId", event.memberId)
                    put("companionId", event.companionId)
                    put("title", event.title)
                    put("baseMinutes", event.baseMinutes)
                    put("synergyBonusMinutes", event.synergyBonusMinutes)
                    put("durationMinutes", event.durationMinutes)
                    put("synergyMatched", event.synergyMatched)
                    put("newlyAwakened", event.newlyAwakened)
                    put("bondDelta", event.bondDelta)
                    put("bondXpBefore", event.bondXpBefore)
                    put("bondXpAfter", event.bondXpAfter)
                    put("rankBefore", event.rankBefore)
                    put("rankAfter", event.rankAfter)
                    put("growthStageUp", event.growthStageUp)
                    put("growthLevelAfter", event.growthLevelAfter)
                }
            )
        }
        return array.toString()
    }

    private fun decodeVictoryEvent(obj: JSONObject): VictoryEvent? {
        val eventId = obj.optString("eventId", "")
        val memberId = obj.optString("memberId", "")
        val companionId = obj.optString("companionId", "")
        val source = runCatching { VictorySource.valueOf(obj.optString("source", "")) }.getOrNull()
        if (eventId.isBlank() || memberId.isBlank() || companionId.isBlank() || source == null) return null
        return VictoryEvent(
            eventId = eventId,
            source = source,
            memberId = memberId,
            companionId = companionId,
            title = obj.optString("title", ""),
            baseMinutes = obj.optLong("baseMinutes", 0L),
            synergyBonusMinutes = obj.optLong("synergyBonusMinutes", 0L),
            durationMinutes = obj.optLong("durationMinutes", 0L),
            synergyMatched = obj.optBoolean("synergyMatched", false),
            newlyAwakened = obj.optBoolean("newlyAwakened", false),
            bondDelta = obj.optInt("bondDelta", 0),
            bondXpBefore = obj.optInt("bondXpBefore", 0),
            bondXpAfter = obj.optInt("bondXpAfter", 0),
            rankBefore = obj.optInt("rankBefore", 1),
            rankAfter = obj.optInt("rankAfter", 1),
            growthStageUp = obj.optBoolean("growthStageUp", false),
            growthLevelAfter = obj.optInt("growthLevelAfter", 1)
        )
    }

    private fun computeStateCounts(
        usageHistory: UsageHistoryRepository,
        growthLedger: CharacterGrowthRepository.Ledger,
        signals: AliveSignals?,
        activePermissions: Int,
        todayKey: String
    ): Map<GalaxyStateKind, Int> {
        var normal = 0
        var near = 0
        var spent = 0
        var recovery = 0
        var unknown = 0

        val today = runCatching { LocalDate.parse(todayKey) }.getOrNull()
        val oldest = usageHistory.oldestRecordedDate()?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

        if (today != null && oldest != null && !oldest.isAfter(today)) {
            val maxLookback = today.minusDays(UsageHistoryRepository.MAX_RETAINED_DAYS - 1)
            val start = if (oldest.isAfter(maxLookback)) oldest else maxLookback
            val spanDays = ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
            val pastKeys = (0 until spanDays).map { start.plusDays(it.toLong()).toString() }
            val records = if (pastKeys.isEmpty()) emptyMap() else usageHistory.daysFor(pastKeys)

            pastKeys.forEach { key ->
                val day = records[key]
                when {
                    day == null -> unknown++
                    day.limitMinutes <= 0L -> recovery++
                    day.totalMinutes >= day.limitMinutes -> spent++
                    day.limitMinutes - day.totalMinutes <= CRITICAL_ZONE_THRESHOLD_MINUTES -> near++
                    else -> normal++
                }
            }
        }

        val permissionsIncomplete = activePermissions < 2
        if (permissionsIncomplete) {
            unknown++
        }

        val todayRecord = usageHistory.day(todayKey)
        when {
            signals?.dayType == DayType.ZERO_ENTERTAINMENT_DAY ||
                signals?.timeStatus == TimeStatus.ZERO_ENTERTAINMENT_DAY ||
                (todayRecord != null && todayRecord.limitMinutes <= 0L) -> recovery++

            signals?.timeStatus == TimeStatus.LOCKED ||
                (todayRecord != null && todayRecord.limitMinutes > 0L && todayRecord.totalMinutes >= todayRecord.limitMinutes) -> spent++

            signals?.timeStatus == TimeStatus.CRITICAL ||
                (signals != null && signals.criticalZoneTier in 1..4) ||
                (todayRecord != null && todayRecord.limitMinutes > 0L &&
                    todayRecord.limitMinutes - todayRecord.totalMinutes <= CRITICAL_ZONE_THRESHOLD_MINUTES) -> near++

            signals?.timeStatus == TimeStatus.ACTIVE || todayRecord != null -> {
                if (!permissionsIncomplete || todayRecord != null) normal++
            }
        }

        val rest = growthLedger.promiseDays + if (signals?.restingToday == true) 1 else 0

        return mapOf(
            GalaxyStateKind.NORMAL to normal,
            GalaxyStateKind.NEAR to near,
            GalaxyStateKind.SPENT to spent,
            GalaxyStateKind.RECOVERY to recovery,
            GalaxyStateKind.UNKNOWN to unknown,
            GalaxyStateKind.REST to rest
        )
    }
}
