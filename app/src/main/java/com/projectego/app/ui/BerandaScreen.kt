package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.projectego.app.R

/**
 * Beranda as the approved render draws it.
 *
 * Six blocks, in the render's order: the greeting, the day's board, three facts,
 * the one action, today's focus, and the streak - then the family's row. Every
 * block is a colour field; the only ink on the page is type and the one action,
 * and the only picture is the flame and the family's own sprites.
 *
 * This replaced the Arcade card's 2x2 grid. What it keeps from it: a closed day
 * still says TUTUP instead of printing a zero, the last minutes still turn the
 * board red, and the streak's marks are progress, never XP.
 */
object BerandaScreen {

    data class Data(
        val greeting: String,
        val subLine: String,
        val remainingMinutes: Long,
        val limitMinutes: Long,
        val usedMinutes: Long,
        val missionsDone: Int,
        val missionsTotal: Int,
        val focusMinutes: Long,
        val streak: Int,
        val tomorrowSafe: Boolean,
        val closed: Boolean,
        val closedWord: String,
        val critical: Boolean,
        val focusTitle: String,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
        val onGrowth: () -> Unit,
        val onFocus: () -> Unit
    )

    private const val TILE_PADDING_DP = 16
    private const val BOARD_PADDING_DP = 20
    private const val FIELD_GAP_DP = 16

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val column = ui.column()

        column.addView(ui.h1(data.greeting))
        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(ui.note(data.subLine))

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(board(context, ui, data))

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(facts(context, ui, data))

        if (data.actionLabel != null && data.onAction != null) {
            column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
            column.addView(
                ui.primary("${data.actionLabel} →").apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { data.onAction.invoke() }
                }
            )
        }

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(focusCard(context, ui, data))

        column.addView(ui.vgap(ui.dimens.cardGap))
        column.addView(streakCard(context, ui, data))

        column.addView(ui.vgap(ui.dimens.lineGap))
        column.addView(familyRow(context, ui, data))

        return column
    }

    // ---------- the day's board ----------

    private fun board(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val face = if (data.critical && !data.closed) ui.palette.shortPale else ui.palette.flatMint
        val board = ui.column().apply {
            background = field(face, ui.dimens.radiusCard)
            setPadding(
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP)
            )
        }

        board.addView(ui.kicker(context.getString(R.string.home_remaining_label)))
        board.addView(ui.vgap(ui.dimens.pairGap))

        val line = ui.row().apply { gravity = Gravity.BOTTOM }
        if (data.closed) {
            line.addView(
                ui.scoreStatus(data.closedWord).apply {
                    isAllCaps = true
                }
            )
            line.addView(spacer(context))
            line.addView(hourglass(context, ui))
        } else {
            line.addView(ui.timer(context.getString(R.string.stat_minutes, data.remainingMinutes)))
            line.addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
                    addView(ui.meta(context.getString(R.string.unit_minutes_suffix)))
                    addView(ui.meta(context.getString(R.string.beranda_of_board, data.limitMinutes)))
                }
            )
            line.addView(spacer(context))
            line.addView(hourglass(context, ui))
        }
        board.addView(line)

        board.addView(ui.vgap(ui.dimens.groupGap))
        board.addView(
            MeterBar(
                context,
                ui,
                fraction = if (data.limitMinutes > 0L) {
                    data.remainingMinutes.toFloat() / data.limitMinutes.toFloat()
                } else {
                    0f
                },
                visible = !data.closed
            )
        )
        return board
    }

    private fun hourglass(context: Context, ui: AliveComponents): ImageView =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_hourglass_flat)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(30), ui.dimens.dp(30))
        }

    // ---------- the three facts ----------

    private fun facts(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            addView(
                fact(
                    context, ui, R.string.stat_used, data.usedMinutes,
                    context.getString(R.string.unit_minutes_suffix), ui.palette.fieldBlossom
                )
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                fact(
                    context, ui, R.string.beranda_missions,
                    data.missionsDone.toLong(),
                    context.getString(R.string.beranda_of_board, data.missionsTotal),
                    ui.palette.fieldButter
                )
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                fact(
                    context, ui, R.string.beranda_focus, data.focusMinutes,
                    context.getString(R.string.unit_minutes_suffix), ui.palette.fieldAqua
                )
            )
        }

    private fun fact(
        context: Context,
        ui: AliveComponents,
        label: Int,
        value: Long,
        unit: String,
        face: Int
    ): LinearLayout = ui.column().apply {
        background = field(face, ui.dimens.radiusTile)
        setPadding(
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP)
        )
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(ui.kicker(context.getString(label)))
        addView(ui.vgap(ui.dimens.pairGap))
        val line = ui.row().apply { gravity = Gravity.BOTTOM }
        line.addView(ui.number(context.getString(R.string.stat_minutes, value)))
        line.addView(
            ui.meta(unit).apply {
                setPadding(ui.dimens.xs, 0, 0, ui.dimens.xs)
                maxLines = 1
            }
        )
        addView(line)
    }

    // ---------- today's focus ----------

    private fun focusCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            background = field(ui.palette.tile, ui.dimens.radiusCard)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dimens.dp(BOARD_PADDING_DP), ui.dimens.dp(18), ui.dimens.dp(18), ui.dimens.dp(18))
            isClickable = true
            setOnClickListener { data.onFocus() }

            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_sprout_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    setPadding(ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10))
                    background = field(ui.palette.fieldSky, ui.dimens.dp(14))
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(44), ui.dimens.dp(44))
                }
            )
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                    addView(ui.kicker(context.getString(R.string.beranda_focus_today)))
                    addView(ui.vgap(ui.dimens.xs))
                    addView(ui.lead(data.focusTitle))
                }
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_smiley_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    setPadding(ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ui.palette.flatMint)
                    }
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
                }
            )
        }

    // ---------- the streak ----------

    private fun streakCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            background = field(ui.palette.fieldButter, ui.dimens.radiusCard)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dimens.dp(BOARD_PADDING_DP), ui.dimens.dp(16), ui.dimens.dp(BOARD_PADDING_DP), ui.dimens.dp(16))
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.alive_member_creative)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(26), ui.dimens.dp(26)).apply {
                        marginEnd = ui.dimens.dp(10)
                    }
                }
            )
            addView(
                ui.kicker(
                    context.getString(R.string.beranda_streak_days, data.streak)
                )
            )
            addView(spacer(context))
            addView(
                ui.lead(
                    context.getString(
                        if (data.tomorrowSafe) R.string.beranda_tomorrow_safe
                        else R.string.beranda_tomorrow_open
                    )
                ).apply {
                    isAllCaps = false
                    alpha = if (data.tomorrowSafe) 1f else 0.55f
                }
            )
        }

    private fun familyRow(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { data.onGrowth() }
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

    // ---------- pieces ----------

    private fun spacer(context: Context): View = View(context)
        .apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

    private fun field(face: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        setColor(face)
        cornerRadius = radiusPx.toFloat()
    }

    /**
     * The day as one bar: paper inside a mint field, ink for what is left.
     *
     * A continuous bar rather than the ten blocks the Arcade card drew - the
     * approved render measures the day's remainder as a share of the field, and
     * a closed day draws no bar at all.
     */
    private class MeterBar(
        context: Context,
        private val ui: AliveComponents,
        fraction: Float,
        private val visible: Boolean
    ) : View(context) {

        private val share = fraction.coerceIn(0f, 1f)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.palette.tile
            alpha = 175
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ui.palette.ink }
        private val shape = RectF()

        init {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(10)
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (!visible || width == 0) return
            val radius = height / 2f
            shape.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(shape, radius, radius, track)
            if (share <= 0f) return
            shape.set(0f, 0f, width * share, height.toFloat())
            canvas.drawRoundRect(shape, radius, radius, fill)
        }
    }
}

