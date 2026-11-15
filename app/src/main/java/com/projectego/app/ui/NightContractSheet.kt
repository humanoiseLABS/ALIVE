package com.projectego.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R
import com.projectego.app.model.Quest

/**
 * Tomorrow's promise modal bottom sheet (`p2_11_janji_besok.png` pass-2 parity).
 *
 * Renders the dimmed scrim + rounded ground sheet containing header icon row,
 * expandable allowance row, expandable single-mission row, editable sky reason
 * bar, primary black save pill, and honest footer note.
 */
object NightContractSheet {

    data class Data(
        val allowanceOptions: List<Long>,
        val initialAllowance: Long,
        val quests: List<Quest>,
        val initialQuestId: String?,
        val initialReason: String,
        val onSave: (allowanceMinutes: Long, questId: String?, reason: String) -> Unit,
        val onDismiss: () -> Unit
    )

    fun build(context: Context, ui: AliveComponents, data: Data): View {
        var chosenAllowance = data.initialAllowance
        var chosenQuestId = data.initialQuestId

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.flat_scrim))
            isClickable = true
            setOnClickListener { data.onDismiss() }
        }

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                val r = ui.dimens.dp(32).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(ui.palette.ground)
            }
            setPadding(ui.dimens.dp(24), ui.dimens.dp(14), ui.dimens.dp(24), ui.dimens.dp(28))
        }

        // Drag handle (#E2DED8, 38x5dp).
        sheet.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = ui.dimens.dp(3).toFloat()
                    setColor(ContextCompat.getColor(context, R.color.flat_sheet_handle))
                }
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(38), ui.dimens.dp(5)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = ui.dimens.dp(16)
                }
            }
        )

        // Header: 44dp lilac moon disc + MALAM · 21.00 / Janji besok + 40dp mint smiley disc.
        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_line_moon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        setPadding(ui.dimens.dp(11), ui.dimens.dp(11), ui.dimens.dp(11), ui.dimens.dp(11))
                        background = oval(ui.palette.fieldLilac)
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(44), ui.dimens.dp(44)).apply {
                            marginEnd = ui.dimens.dp(12)
                        }
                    }
                )
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(ui.kicker(context.getString(R.string.night_contract_eyebrow)))
                        addView(ui.vgap(ui.dimens.dp(2)))
                        addView(ui.headline(context.getString(R.string.night_contract_heading)))
                    }
                )
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_smiley_flat)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        setPadding(ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10))
                        background = oval(ui.palette.flatMint)
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
                    }
                )
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(10)))
        sheet.addView(ui.note(context.getString(R.string.night_contract_sub)))
        sheet.addView(ui.vgap(ui.dimens.dp(14)))

        val groteskBold = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)

        // Row 1: Jatah besok (Butter #FFE9A8, expandable inline selector).
        val allowanceValueView = TextView(context).apply {
            text = context.getString(R.string.night_contract_allowance_value, chosenAllowance)
            setTextColor(ui.palette.ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = groteskBold
            includeFontPadding = false
        }
        val allowanceChoices = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, ui.dimens.dp(10), 0, 0)
        }
        val allowancePills = mutableListOf<Pair<Long, TextView>>()
        fun repaintAllowancePills() {
            allowancePills.forEach { (minutes, pill) ->
                val active = minutes == chosenAllowance
                pill.background = GradientDrawable().apply {
                    cornerRadius = ui.dimens.radiusPill.toFloat()
                    setColor(if (active) ui.palette.ink else ui.palette.tile)
                }
                pill.setTextColor(if (active) ui.palette.paper else ui.palette.ink)
            }
        }
        data.allowanceOptions.forEachIndexed { index, minutes ->
            val pill = TextView(context).apply {
                text = context.getString(R.string.stat_minutes, minutes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = groteskBold
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(0, ui.dimens.dp(10), 0, ui.dimens.dp(10))
                setOnClickListener {
                    chosenAllowance = minutes
                    allowanceValueView.text = context.getString(R.string.night_contract_allowance_value, minutes)
                    repaintAllowancePills()
                    allowanceChoices.visibility = View.GONE
                }
            }
            allowancePills += minutes to pill
            allowanceChoices.addView(
                pill,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = ui.dimens.dp(6)
                }
            )
        }
        repaintAllowancePills()

        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = field(ui.palette.fieldButter, ui.dimens.radiusTile)
                setPadding(ui.dimens.dp(18), ui.dimens.dp(16), ui.dimens.dp(18), ui.dimens.dp(16))
                setOnClickListener {
                    allowanceChoices.visibility =
                        if (allowanceChoices.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = context.getString(R.string.night_contract_row_allowance)
                                setTextColor(ui.palette.ink)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                typeface = groteskBold
                                includeFontPadding = false
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                        )
                        addView(allowanceValueView)
                    }
                )
                addView(allowanceChoices)
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(10)))

        // Row 2: Satu misi (Mint #B9F0A6, expandable mission list).
        fun missionSummary(): String {
            val picked = data.quests.find { it.id == chosenQuestId }
            return picked?.let { context.getString(R.string.quest_reward_chip, it.rewardMinutes) + " " + context.getString(R.string.unit_minutes_short) }
                ?: context.getString(R.string.night_contract_quest_none)
        }
        val missionValueView = TextView(context).apply {
            text = missionSummary()
            setTextColor(ui.palette.ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = groteskBold
            includeFontPadding = false
        }
        val missionChoicesScroll = ScrollView(context).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ui.dimens.dp(10)
            }
        }
        val missionChoices = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val missionRows = mutableListOf<Pair<String?, TextView>>()
        fun repaintMissionPills() {
            missionRows.forEach { (id, pill) ->
                val active = id == chosenQuestId
                pill.background = GradientDrawable().apply {
                    cornerRadius = ui.dimens.radiusPill.toFloat()
                    setColor(if (active) ui.palette.ink else ui.palette.tile)
                }
                pill.setTextColor(if (active) ui.palette.paper else ui.palette.ink)
            }
        }
        val entries: List<Pair<String?, String>> = listOf(null to context.getString(R.string.night_contract_quest_none)) +
            data.quests.take(5).map { q -> q.id to "${q.title} · +${q.rewardMinutes} ${context.getString(R.string.unit_minutes_short)}" }
        entries.forEachIndexed { index, (id, label) ->
            val pill = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = groteskBold
                includeFontPadding = false
                setPadding(ui.dimens.dp(14), ui.dimens.dp(10), ui.dimens.dp(14), ui.dimens.dp(10))
                setOnClickListener {
                    chosenQuestId = id
                    missionValueView.text = missionSummary()
                    repaintMissionPills()
                    missionChoicesScroll.visibility = View.GONE
                }
            }
            missionRows += id to pill
            missionChoices.addView(
                pill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = ui.dimens.dp(6)
                }
            )
        }
        repaintMissionPills()
        missionChoicesScroll.addView(missionChoices)

        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = field(ui.palette.flatMint, ui.dimens.radiusTile)
                setPadding(ui.dimens.dp(18), ui.dimens.dp(16), ui.dimens.dp(18), ui.dimens.dp(16))
                setOnClickListener {
                    missionChoicesScroll.visibility =
                        if (missionChoicesScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = context.getString(R.string.night_contract_row_mission)
                                setTextColor(ui.palette.ink)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                typeface = groteskBold
                                includeFontPadding = false
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                        )
                        addView(missionValueView)
                    }
                )
                addView(missionChoicesScroll)
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(10)))

        // Row 3: Kenapa angka ini? (Sky #BFD9FF, editable inline).
        val reasonInput = EditText(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = field(ui.palette.fieldSky, ui.dimens.radiusTile)
            setPadding(ui.dimens.dp(18), ui.dimens.dp(16), ui.dimens.dp(18), ui.dimens.dp(16))
            hint = context.getString(R.string.night_contract_reason_label)
            setText(data.initialReason)
            setTextColor(ui.palette.ink)
            setHintTextColor(Color.parseColor("#546273"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            minHeight = ui.dimens.dp(52)
            maxLines = 3
        }
        sheet.addView(reasonInput)

        sheet.addView(ui.vgap(ui.dimens.dp(16)))

        sheet.addView(
            ui.primary(context.getString(R.string.night_contract_save)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ui.dimens.dp(52)
                )
                setOnClickListener {
                    data.onSave(chosenAllowance, chosenQuestId, reasonInput.text?.toString().orEmpty())
                }
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(12)))
        sheet.addView(
            TextView(context).apply {
                text = context.getString(R.string.night_contract_footer)
                setTextAppearance(R.style.TextAppearance_ALIVE_MetaSmall)
                setTextColor(ui.palette.inkMuted)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(sheet)
        }

        scrim.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        return scrim
    }

    private fun field(colour: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radiusPx.toFloat()
        setColor(colour)
    }

    private fun oval(colour: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colour)
    }
}
