package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R

/**
 * Profil as the approved pass-2 render draws it: the character's card, the
 * bank's card, and three rows that lead to the rest. The settings themselves
 * moved behind those rows - a page that lists every control at once is a page
 * nobody reads.
 */
object ProfilScreen {

    data class Data(
        val level: Int,
        val scaleLine: String,
        val streak: Int,
        val missions: Int,
        val focusSessions: Int,
        val bankMinutes: Long,
        val remindersLine: String,
        val permissionsLine: String,
        val languageLine: String,
        val onGrowth: () -> Unit,
        val onClaim: () -> Unit,
        val onBank: () -> Unit,
        val onReminders: () -> Unit,
        val onPermissions: () -> Unit,
        val onLanguage: () -> Unit
    )

    private const val PAD = 20

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val column = ui.column()

        column.addView(ui.h1(context.getString(R.string.profile_heading)))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(context.getString(R.string.profile_identity_support)))

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(characterCard(context, ui, data))

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(bankCard(context, ui, data))

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(
            row(
                context, ui, R.drawable.ic_drop_flat, ui.palette.flatMint,
                context.getString(R.string.profile_reminders_heading), data.remindersLine,
                data.onReminders
            )
        )
        column.addView(ui.vgap(ui.dimens.dp(10)))
        column.addView(
            row(
                context, ui, R.drawable.ic_shield_flat, ui.palette.fieldLilac,
                context.getString(R.string.profile_permissions_heading), data.permissionsLine,
                data.onPermissions
            )
        )
        column.addView(ui.vgap(ui.dimens.dp(10)))
        column.addView(
            row(
                context, ui, R.drawable.ic_sun_flat, ui.palette.fieldPeach,
                context.getString(R.string.profile_language_pill_title), data.languageLine,
                data.onLanguage
            )
        )

        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(facesRow(context, ui, data))
        return column
    }

    // ---------- the character ----------

    private fun characterCard(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val card = ui.column().apply {
            background = field(ui.palette.fieldBlossom, ui.dimens.radiusCard)
            setPadding(
                ui.dimens.dp(PAD), ui.dimens.dp(PAD), ui.dimens.dp(PAD), ui.dimens.dp(PAD)
            )
            isClickable = true
            setOnClickListener { data.onGrowth() }
        }

        val top = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.alive_member_creative)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(64), ui.dimens.dp(64))
            }
        )
        top.addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                addView(ui.kicker(context.getString(R.string.growth_heading)))
                addView(ui.vgap(ui.dimens.xs))
                addView(ui.headline(context.getString(R.string.growth_level, data.level)))
                addView(ui.vgap(ui.dimens.xs))
                addView(ui.note(data.scaleLine))
            }
        )
        top.addView(
            TextView(context).apply {
                text = "→"
                setTextAppearance(R.style.TextAppearance_ALIVE_Lead)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ui.palette.flatPaper)
                }
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
            }
        )
        card.addView(top)

        card.addView(ui.vgap(ui.dimens.groupGap))
        val chips = ui.row()
        val rows: List<Pair<String, Int>> = listOf(
            context.getString(R.string.profile_chip_days, data.streak) to ui.palette.flatPaper,
            context.getString(R.string.profile_chip_missions, data.missions) to ui.palette.fieldButter,
            context.getString(R.string.profile_chip_focus, data.focusSessions) to ui.palette.fieldSky
        )
        rows.forEachIndexed { index, (label, face) ->
            if (index > 0) chips.addView(ui.hgap(ui.dimens.dp(8)))
            chips.addView(chip(context, ui, label, face))
        }
        card.addView(chips)
        return card
    }

    private fun chip(context: Context, ui: AliveComponents, label: String, face: Int): LinearLayout =
        ui.row().apply {
            background = field(face, ui.dimens.radiusPill)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dimens.dp(12), 0, ui.dimens.dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ui.dimens.dp(32)
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_smiley_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(16), ui.dimens.dp(16)).apply {
                        marginEnd = ui.dimens.dp(6)
                    }
                }
            )
            addView(
                TextView(context).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                    setTextColor(ui.palette.ink)
                }
            )
        }

    // ---------- the bank ----------

    private fun bankCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            background = field(ui.palette.fieldButter, ui.dimens.radiusCard)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                ui.dimens.dp(PAD), ui.dimens.dp(16), ui.dimens.dp(16), ui.dimens.dp(16)
            )
            isClickable = true
            setOnClickListener { data.onBank() }
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_hourglass_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(30), ui.dimens.dp(30))
                }
            )
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                    addView(ui.kicker(context.getString(R.string.time_bank_heading)))
                    addView(ui.vgap(ui.dimens.xs))
                    addView(
                        ui.row().apply {
                            gravity = Gravity.BOTTOM
                            addView(ui.number(context.getString(R.string.stat_minutes, data.bankMinutes)))
                            addView(
                                ui.meta(context.getString(R.string.unit_minutes_suffix)).apply {
                                    setPadding(ui.dimens.xs, 0, 0, ui.dimens.xs)
                                }
                            )
                        }
                    )
                }
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.time_bank_claim_action_off)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                    setTextColor(ui.palette.flatPaper)
                    background = field(ui.palette.ink, ui.dimens.radiusPill)
                    setPadding(ui.dimens.dp(18), 0, ui.dimens.dp(18), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        ui.dimens.dp(44)
                    )
                    isClickable = true
                    setOnClickListener { data.onClaim() }
                }
            )
        }

    // ---------- one row ----------

    private fun row(
        context: Context,
        ui: AliveComponents,
        glyph: Int,
        hue: Int,
        title: String,
        support: String,
        onTap: () -> Unit
    ): LinearLayout = ui.row().apply {
        background = field(ui.palette.tile, ui.dimens.radiusTile)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            ui.dimens.dp(14), ui.dimens.dp(14), ui.dimens.dp(14), ui.dimens.dp(14)
        )
        isClickable = true
        setOnClickListener { onTap() }

        addView(
            ImageView(context).apply {
                setImageResource(glyph)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
                setPadding(ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(hue)
                }
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
            }
        )
        addView(
            ui.column().apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                addView(ui.lead(title))
                addView(ui.vgap(ui.dimens.xs))
                addView(ui.note(support).apply { maxLines = 1 })
            }
        )
        addView(
            TextView(context).apply {
                text = "→"
                setTextAppearance(R.style.TextAppearance_ALIVE_Lead)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ui.palette.ground)
                }
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(34), ui.dimens.dp(34))
            }
        )
    }

    // ---------- the week ----------

    private fun facesRow(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            val hues = listOf(
                ui.palette.flatMint,
                ui.palette.fieldBlossom,
                ui.palette.fieldLilac,
                ui.palette.fieldSky,
                ui.palette.fieldButter,
                ui.palette.fieldAqua,
                ui.palette.fieldRose
            )
            for (day in 0 until 7) {
                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_smiley_flat)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        setPadding(ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(hues[day % hues.size])
                        }
                        alpha = if (day < data.streak) 1f else 0.3f
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(34), ui.dimens.dp(34)).apply {
                            if (day < 6) marginEnd = ui.dimens.dp(6)
                        }
                    }
                )
            }
        }

    private fun field(face: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radiusPx.toFloat()
        setColor(face)
    }
}
