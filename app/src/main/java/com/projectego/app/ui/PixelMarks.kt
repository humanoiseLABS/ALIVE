package com.projectego.app.ui

import androidx.annotation.DrawableRes
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType
import com.projectego.app.model.QuestCategory

/**
 * Which mark stands for which kind of doing.
 *
 * A quest list and a focus form are both lists of verbs, and a verb reads faster
 * as a shape than as a word: the book says "reading" before the title finishes
 * rendering. The marks are one line hand - the same stroke the dock draws with -
 * because the approved pass-2 list reads as outline glyphs on pastel discs, and
 * a pixel mark beside a line mark is two apps in one row.
 *
 * `OTHER` gets a line star of its own: the filled pixel accent is a celebration
 * shape, and upscaling it into a line row would be two apps in one row.
 */
object PixelMarks {

    @DrawableRes
    fun focus(type: FocusActivityType): Int = when (type) {
        FocusActivityType.STUDY -> R.drawable.ic_line_book
        FocusActivityType.WORK -> R.drawable.ic_line_briefcase
        FocusActivityType.CODING -> R.drawable.ic_line_terminal
        FocusActivityType.CREATIVE -> R.drawable.ic_line_brush
        FocusActivityType.WRITING -> R.drawable.ic_line_pen
    }

    @DrawableRes
    fun quest(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.drawable.ic_line_dumbbell
        QuestCategory.STUDY -> R.drawable.ic_line_book
        QuestCategory.READING -> R.drawable.ic_line_books
        QuestCategory.WALKING -> R.drawable.ic_line_walker
        QuestCategory.CLEANING -> R.drawable.ic_line_broom
        QuestCategory.CREATIVE -> R.drawable.ic_line_brush
        QuestCategory.CODING -> R.drawable.ic_line_terminal
        QuestCategory.WRITING -> R.drawable.ic_line_pen
        QuestCategory.MINDFULNESS -> R.drawable.ic_line_lotus
        QuestCategory.SOCIAL -> R.drawable.ic_dock_profile
        QuestCategory.OTHER -> R.drawable.ic_line_star
    }

    /** Spoken description, so the mark is never the only carrier of meaning. */
    fun description(category: QuestCategory): Int = when (category) {
        QuestCategory.EXERCISE -> R.string.cd_mark_exercise
        QuestCategory.STUDY -> R.string.cd_mark_study
        QuestCategory.READING -> R.string.cd_mark_reading
        QuestCategory.WALKING -> R.string.cd_mark_walking
        QuestCategory.CLEANING -> R.string.cd_mark_cleaning
        QuestCategory.CREATIVE -> R.string.cd_mark_creative
        QuestCategory.CODING -> R.string.cd_mark_coding
        QuestCategory.WRITING -> R.string.cd_mark_writing
        QuestCategory.MINDFULNESS -> R.string.cd_mark_mindfulness
        QuestCategory.SOCIAL -> R.string.cd_mark_social
        QuestCategory.OTHER -> R.string.cd_mark_other
    }
}
