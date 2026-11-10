package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import com.projectego.app.R
import com.projectego.app.model.Quest
import com.projectego.app.model.QuestCategory

/**
 * Misi as the approved pass-2 render draws it.
 *
 * One sentence, one filter row, and a list of white rows: each mission is its
 * category's hue on a circular glyph, its words, and what it pays. The grid of
 * cards it replaces asked the eye to compare nine tiles at once; a list is read
 * top to bottom, and the filter is what makes a short list of twelve.
 */
object MisiScreen {

    data class Data(
        val quests: List<Quest>,
        val selected: QuestCategory?,
        val hasCustomQuest: Boolean,
        val onFilter: (QuestCategory?) -> Unit,
        val onOpen: (String) -> Unit,
        val onCustom: () -> Unit
    )

    private val categoryOrder = listOf(
        QuestCategory.WALKING,
        QuestCategory.STUDY,
        QuestCategory.READING,
        QuestCategory.EXERCISE,
        QuestCategory.CLEANING,
        QuestCategory.CREATIVE,
        QuestCategory.CODING,
        QuestCategory.WRITING,
        QuestCategory.MINDFULNESS,
        QuestCategory.SOCIAL,
        QuestCategory.OTHER
    )

    private const val ROW_PADDING_DP = 16
    private const val CHIP_HEIGHT_DP = 34

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val column = ui.column()

        column.addView(ui.h1(context.getString(R.string.quest_heading)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(context.getString(R.string.quest_subhead)))

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(filters(context, ui, data))

        val shown = data.quests.filter { data.selected == null || it.category == data.selected }
        column.addView(ui.vgap(ui.dimens.dp(16)))
        if (shown.isEmpty()) {
            column.addView(
                ui.emptyState(
                    context.getString(R.string.empty_heading),
                    context.getString(R.string.empty_body),
                    context.getString(R.string.empty_body_second)
                )
            )
        } else {
            shown.forEachIndexed { index, quest ->
                if (index > 0) column.addView(ui.vgap(ui.dimens.dp(10)))
                column.addView(row(context, ui, quest) { data.onOpen(quest.id) })
            }
        }

        if (!data.hasCustomQuest) {
            column.addView(ui.vgap(ui.dimens.dp(16)))
            column.addView(customCard(context, ui, data))
        }

        return column
    }

    // ---------- filters ----------

    private fun filters(context: Context, ui: AliveComponents, data: Data): View {
        val row = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(chip(context, ui, context.getString(R.string.quest_filter_today), null, data))
        categoryOrder
            .filter { category -> data.quests.any { it.category == category } }
            .forEach { category ->
                row.addView(ui.hgap(ui.dimens.lineGap))
                row.addView(
                    chip(
                        context,
                        ui,
                        context.getString(AliveStates.categoryLabel(category)),
                        category,
                        data
                    )
                )
            }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        }
    }

    private fun chip(
        context: Context,
        ui: AliveComponents,
        label: String,
        category: QuestCategory?,
        data: Data
    ): TextView {
        val selected = data.selected == category
        val face = if (selected) {
            ui.palette.ink
        } else if (category == null) {
            ui.palette.rewardPale
        } else {
            ContextCompat.getColor(context, QuestHues.quest(category))
        }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            includeFontPadding = false
            setTextColor(if (selected) ui.palette.flatPaper else ui.palette.ink)
            background = pill(face, ui)
            setPadding(ui.dimens.dp(14), 0, ui.dimens.dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ui.dimens.dp(CHIP_HEIGHT_DP)
            )
            isClickable = true
            setOnClickListener { data.onFilter(category) }
        }
    }

    // ---------- one mission ----------

    private fun row(
        context: Context,
        ui: AliveComponents,
        quest: Quest,
        onOpen: () -> Unit
    ): LinearLayout {
        val hue = ContextCompat.getColor(context, QuestHues.quest(quest.category))
        return ui.row().apply {
            background = field(ui.palette.tile, ui.dimens.radiusTile)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ui.dimens.dp(ROW_PADDING_DP),
                ui.dimens.dp(ROW_PADDING_DP),
                ui.dimens.dp(ROW_PADDING_DP),
                ui.dimens.dp(ROW_PADDING_DP)
            )
            isClickable = true
            setOnClickListener { onOpen() }

            addView(
                ImageView(context).apply {
                    setImageResource(PixelMarks.quest(quest.category))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(ui.dimens.dp(11), ui.dimens.dp(11), ui.dimens.dp(11), ui.dimens.dp(11))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(hue)
                    }
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(44), ui.dimens.dp(44))
                }
            )
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                    addView(ui.lead(quest.title))
                    addView(ui.vgap(ui.dimens.xs))
                    addView(
                        ui.note(quest.description).apply {
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                    )
                }
            )
            addView(rewardChip(context, ui, quest.rewardMinutes, hue))
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_smiley_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(18), ui.dimens.dp(18)).apply {
                        marginStart = ui.dimens.lineGap
                    }
                }
            )
        }
    }

    private fun rewardChip(
        context: Context,
        ui: AliveComponents,
        minutes: Long,
        hue: Int
    ): TextView = TextView(context).apply {
        text = context.getString(R.string.quest_reward_chip, minutes)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
        includeFontPadding = false
        setTextColor(ui.palette.ink)
        background = pill(hue, ui)
        setPadding(ui.dimens.dp(12), 0, ui.dimens.dp(12), 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            ui.dimens.dp(CHIP_HEIGHT_DP)
        )
    }

    // ---------- your own mission ----------

    private fun customCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            background = field(ui.palette.fieldBlossom, ui.dimens.radiusTile)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ui.dimens.dp(ROW_PADDING_DP),
                ui.dimens.dp(14),
                ui.dimens.dp(14),
                ui.dimens.dp(14)
            )
            isClickable = true
            setOnClickListener { data.onCustom() }

            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.alive_member_creative)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
                }
            )
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                    addView(ui.kicker(context.getString(R.string.quest_custom_kicker)))
                    addView(ui.vgap(ui.dimens.xs))
                    addView(ui.lead(context.getString(R.string.quest_custom_title)))
                }
            )
            addView(
                TextView(context).apply {
                    text = "+"
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                    setTextColor(ui.palette.flatPaper)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ui.palette.ink)
                    }
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(44), ui.dimens.dp(44))
                }
            )
        }

    private fun pill(face: Int, ui: AliveComponents): GradientDrawable = GradientDrawable().apply {
        cornerRadius = ui.dimens.radiusPill.toFloat()
        setColor(face)
    }

    private fun field(face: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radiusPx.toFloat()
        setColor(face)
    }
}
