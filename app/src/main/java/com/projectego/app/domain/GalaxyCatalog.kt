package com.projectego.app.domain

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory

/**
 * Authoritative catalog for all 17 flat-family members displayed in the Character Galaxy:
 * 11 work archetypes followed clockwise by 6 day-state mirrors.
 */
object GalaxyCatalog {

    const val DEFAULT_COMPANION_ID = "creative"

    enum class GalaxyMemberKind { WORK, DAY_STATE }

    enum class GalaxyStateKind { NORMAL, NEAR, SPENT, RECOVERY, UNKNOWN, REST }

    sealed interface GalaxyActionTarget {
        data class Focus(val activityType: FocusActivityType) : GalaxyActionTarget
        data class QuestFilter(val category: QuestCategory) : GalaxyActionTarget
        data object CustomQuest : GalaxyActionTarget
        data object TimeBank : GalaxyActionTarget
        data object Permissions : GalaxyActionTarget
        data object Reminders : GalaxyActionTarget
        data object Stats : GalaxyActionTarget
    }

    data class GalaxyMemberSpec(
        val id: String,
        val index: Int,
        val kind: GalaxyMemberKind,
        @param:DrawableRes val drawableRes: Int,
        @param:ColorRes val hueRes: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val lineRes: Int,
        val questCategory: QuestCategory? = null,
        val focusActivity: FocusActivityType? = null,
        val stateKind: GalaxyStateKind? = null,
        val primaryAction: GalaxyActionTarget,
        val secondaryAction: GalaxyActionTarget? = null
    )

    val members: List<GalaxyMemberSpec> = listOf(
        GalaxyMemberSpec(
            id = "study",
            index = 0,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_study,
            hueRes = R.color.alive_fam_study,
            titleRes = R.string.galaxy_member_study_title,
            lineRes = R.string.galaxy_member_study_line,
            questCategory = QuestCategory.STUDY,
            focusActivity = FocusActivityType.STUDY,
            primaryAction = GalaxyActionTarget.Focus(FocusActivityType.STUDY),
            secondaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.STUDY)
        ),
        GalaxyMemberSpec(
            id = "reading",
            index = 1,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_reading,
            hueRes = R.color.alive_fam_reading,
            titleRes = R.string.galaxy_member_reading_title,
            lineRes = R.string.galaxy_member_reading_line,
            questCategory = QuestCategory.READING,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.READING)
        ),
        GalaxyMemberSpec(
            id = "exercise",
            index = 2,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_exercise,
            hueRes = R.color.alive_fam_exercise,
            titleRes = R.string.galaxy_member_exercise_title,
            lineRes = R.string.galaxy_member_exercise_line,
            questCategory = QuestCategory.EXERCISE,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.EXERCISE)
        ),
        GalaxyMemberSpec(
            id = "walking",
            index = 3,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_walking,
            hueRes = R.color.alive_fam_walking,
            titleRes = R.string.galaxy_member_walking_title,
            lineRes = R.string.galaxy_member_walking_line,
            questCategory = QuestCategory.WALKING,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.WALKING)
        ),
        GalaxyMemberSpec(
            id = "cleaning",
            index = 4,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_cleaning,
            hueRes = R.color.alive_fam_cleaning,
            titleRes = R.string.galaxy_member_cleaning_title,
            lineRes = R.string.galaxy_member_cleaning_line,
            questCategory = QuestCategory.CLEANING,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.CLEANING)
        ),
        GalaxyMemberSpec(
            id = "creative",
            index = 5,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_creative,
            hueRes = R.color.alive_fam_creative,
            titleRes = R.string.galaxy_member_creative_title,
            lineRes = R.string.galaxy_member_creative_line,
            questCategory = QuestCategory.CREATIVE,
            focusActivity = FocusActivityType.CREATIVE,
            primaryAction = GalaxyActionTarget.Focus(FocusActivityType.CREATIVE),
            secondaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.CREATIVE)
        ),
        GalaxyMemberSpec(
            id = "coding",
            index = 6,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_coding,
            hueRes = R.color.alive_fam_coding,
            titleRes = R.string.galaxy_member_coding_title,
            lineRes = R.string.galaxy_member_coding_line,
            questCategory = QuestCategory.CODING,
            focusActivity = FocusActivityType.CODING,
            primaryAction = GalaxyActionTarget.Focus(FocusActivityType.CODING),
            secondaryAction = GalaxyActionTarget.CustomQuest
        ),
        GalaxyMemberSpec(
            id = "writing",
            index = 7,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_writing,
            hueRes = R.color.alive_fam_writing,
            titleRes = R.string.galaxy_member_writing_title,
            lineRes = R.string.galaxy_member_writing_line,
            questCategory = QuestCategory.WRITING,
            focusActivity = FocusActivityType.WRITING,
            primaryAction = GalaxyActionTarget.Focus(FocusActivityType.WRITING),
            secondaryAction = GalaxyActionTarget.CustomQuest
        ),
        GalaxyMemberSpec(
            id = "mindfulness",
            index = 8,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_mindfulness,
            hueRes = R.color.alive_fam_mindfulness,
            titleRes = R.string.galaxy_member_mindfulness_title,
            lineRes = R.string.galaxy_member_mindfulness_line,
            questCategory = QuestCategory.MINDFULNESS,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.MINDFULNESS)
        ),
        GalaxyMemberSpec(
            id = "social",
            index = 9,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_social,
            hueRes = R.color.alive_fam_social,
            titleRes = R.string.galaxy_member_social_title,
            lineRes = R.string.galaxy_member_social_line,
            questCategory = QuestCategory.SOCIAL,
            primaryAction = GalaxyActionTarget.QuestFilter(QuestCategory.SOCIAL)
        ),
        GalaxyMemberSpec(
            id = "work",
            index = 10,
            kind = GalaxyMemberKind.WORK,
            drawableRes = R.drawable.alive_member_work,
            hueRes = R.color.alive_fam_work,
            titleRes = R.string.galaxy_member_work_title,
            lineRes = R.string.galaxy_member_work_line,
            questCategory = QuestCategory.OTHER,
            focusActivity = FocusActivityType.WORK,
            primaryAction = GalaxyActionTarget.Focus(FocusActivityType.WORK),
            secondaryAction = GalaxyActionTarget.CustomQuest
        ),
        GalaxyMemberSpec(
            id = "normal",
            index = 11,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_normal,
            hueRes = R.color.alive_traffic_green,
            titleRes = R.string.galaxy_state_normal_title,
            lineRes = R.string.galaxy_state_normal_line,
            stateKind = GalaxyStateKind.NORMAL,
            primaryAction = GalaxyActionTarget.Stats
        ),
        GalaxyMemberSpec(
            id = "near",
            index = 12,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_near,
            hueRes = R.color.alive_traffic_yellow,
            titleRes = R.string.galaxy_state_near_title,
            lineRes = R.string.galaxy_state_near_line,
            stateKind = GalaxyStateKind.NEAR,
            primaryAction = GalaxyActionTarget.Stats
        ),
        GalaxyMemberSpec(
            id = "spent",
            index = 13,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_spent,
            hueRes = R.color.alive_traffic_red,
            titleRes = R.string.galaxy_state_spent_title,
            lineRes = R.string.galaxy_state_spent_line,
            stateKind = GalaxyStateKind.SPENT,
            primaryAction = GalaxyActionTarget.Stats
        ),
        GalaxyMemberSpec(
            id = "recovery",
            index = 14,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_recovery,
            hueRes = R.color.flat_field_leaf,
            titleRes = R.string.galaxy_state_recovery_title,
            lineRes = R.string.galaxy_state_recovery_line,
            stateKind = GalaxyStateKind.RECOVERY,
            primaryAction = GalaxyActionTarget.TimeBank
        ),
        GalaxyMemberSpec(
            id = "unknown",
            index = 15,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_unknown,
            hueRes = R.color.alive_fam_cleaning,
            titleRes = R.string.galaxy_state_unknown_title,
            lineRes = R.string.galaxy_state_unknown_line,
            stateKind = GalaxyStateKind.UNKNOWN,
            primaryAction = GalaxyActionTarget.Permissions
        ),
        GalaxyMemberSpec(
            id = "rest",
            index = 16,
            kind = GalaxyMemberKind.DAY_STATE,
            drawableRes = R.drawable.alive_state_rest,
            hueRes = R.color.flat_ink,
            titleRes = R.string.galaxy_state_rest_title,
            lineRes = R.string.galaxy_state_rest_line,
            stateKind = GalaxyStateKind.REST,
            primaryAction = GalaxyActionTarget.Reminders
        )
    )

    private val byIdMap: Map<String, GalaxyMemberSpec> = members.associateBy { it.id }

    fun defaultCompanion(): GalaxyMemberSpec = byIdMap.getValue(DEFAULT_COMPANION_ID)

    fun findById(id: String?): GalaxyMemberSpec? = if (id == null) null else byIdMap[id]

    fun forQuestCategory(category: QuestCategory): GalaxyMemberSpec = when (category) {
        QuestCategory.STUDY -> byIdMap.getValue("study")
        QuestCategory.READING -> byIdMap.getValue("reading")
        QuestCategory.EXERCISE -> byIdMap.getValue("exercise")
        QuestCategory.WALKING -> byIdMap.getValue("walking")
        QuestCategory.CLEANING -> byIdMap.getValue("cleaning")
        QuestCategory.CREATIVE -> byIdMap.getValue("creative")
        QuestCategory.CODING -> byIdMap.getValue("coding")
        QuestCategory.WRITING -> byIdMap.getValue("writing")
        QuestCategory.MINDFULNESS -> byIdMap.getValue("mindfulness")
        QuestCategory.SOCIAL -> byIdMap.getValue("social")
        QuestCategory.OTHER -> byIdMap.getValue("work")
    }

    fun forFocusActivity(activityType: FocusActivityType): GalaxyMemberSpec = when (activityType) {
        FocusActivityType.STUDY -> byIdMap.getValue("study")
        FocusActivityType.WORK -> byIdMap.getValue("work")
        FocusActivityType.CODING -> byIdMap.getValue("coding")
        FocusActivityType.CREATIVE -> byIdMap.getValue("creative")
        FocusActivityType.WRITING -> byIdMap.getValue("writing")
    }
}
