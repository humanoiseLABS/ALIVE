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
 * Guardian status page (`p2_10_guardian.png` pass-2 flat parity).
 *
 * Pure UI builder: receives already-resolved state and callbacks so tests and
 * screenshot renders can construct it without any Android service or store.
 */
object GuardianScreen {

    data class Data(
        val state: GuardianState,
        val clockLabel: String,
        val eyebrow: String,
        val headline: String,
        val body: String,
        val actionLabel: String?,
        val sinceLabel: String,
        val closedToday: Int,
        val remainingMinutes: Long,
        val note: String,
        val onAction: () -> Unit,
        val onBack: (() -> Unit)? = null
    )

    fun build(context: Context, ui: AliveComponents, data: Data): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(ui.palette.groundButter)
            setPadding(
                ui.dimens.pageHorizontal,
                ui.dimens.pageTop,
                ui.dimens.pageHorizontal,
                ui.dimens.pageBottom
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val groteskBold = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
        val manrope = ResourcesCompat.getFont(context, R.font.manrope_medium)

        // Top pill row: "Guardian" black capsule + muted timestamp.
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(context).apply {
                        text = context.getString(R.string.guardian_name)
                        setTextColor(ui.palette.paper)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        typeface = groteskBold
                        includeFontPadding = false
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = ui.dimens.radiusPill.toFloat()
                            setColor(ui.palette.ink)
                        }
                        setPadding(ui.dimens.dp(14), ui.dimens.dp(6), ui.dimens.dp(14), ui.dimens.dp(6))
                        if (data.onBack != null) {
                            setOnClickListener { data.onBack.invoke() }
                        }
                    }
                )
                addView(ui.hgap(ui.dimens.dp(10)))
                addView(
                    TextView(context).apply {
                        text = data.clockLabel
                        setTextColor(ui.palette.inkMuted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        typeface = manrope
                        includeFontPadding = false
                    }
                )
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    }
                )
            }
        )

        root.addView(ui.vgap(ui.dimens.dp(16)))

        // Hero Peach (when degraded/recovery) or Mint/Sky (when whole) status card.
        val heroHue = when (data.state) {
            GuardianState.DEGRADED, GuardianState.RECOVERY_REQUIRED -> ui.palette.fieldPeach
            GuardianState.RESTORED -> ui.palette.flatMint
            GuardianState.ACTIVE -> ui.palette.fieldSky
        }
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = field(heroHue, ui.dimens.radiusCard)
                val pad = ui.dimens.dp(20)
                setPadding(pad, pad, pad, pad)

                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            ImageView(context).apply {
                                setImageResource(R.drawable.ic_shield_flat)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                contentDescription = null
                                setPadding(ui.dimens.dp(13), ui.dimens.dp(13), ui.dimens.dp(13), ui.dimens.dp(13))
                                background = oval(ui.palette.tile)
                                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(52), ui.dimens.dp(52)).apply {
                                    marginEnd = ui.dimens.dp(14)
                                }
                            }
                        )
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                addView(ui.kicker(data.eyebrow))
                                addView(ui.vgap(ui.dimens.dp(4)))
                                addView(
                                    ui.headline(data.headline).apply {
                                        setTextColor(ui.palette.ink)
                                    }
                                )
                            }
                        )
                    }
                )
                addView(ui.vgap(ui.dimens.dp(14)))
                addView(
                    ui.note(data.body).apply {
                        setTextColor(ui.palette.inkSecondary)
                    }
                )
            }
        )

        if (data.actionLabel != null) {
            root.addView(ui.vgap(ui.dimens.dp(16)))
            root.addView(
                ui.primary(data.actionLabel).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        ui.dimens.dp(52)
                    )
                    setOnClickListener { data.onAction() }
                }
            )
        }

        root.addView(ui.vgap(ui.dimens.dp(16)))

        // Three equal fact tiles: Sejak | Hari ini | Sisa jatah.
        val tiles = ui.equalRow().apply {
            addView(
                factTile(
                    context = context,
                    ui = ui,
                    hue = ui.palette.tile,
                    kicker = context.getString(R.string.guardian_tile_since),
                    figure = data.sinceLabel,
                    unit = null
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                factTile(
                    context = context,
                    ui = ui,
                    hue = ui.palette.flatMint,
                    kicker = context.getString(R.string.guardian_tile_today),
                    figure = data.closedToday.toString(),
                    unit = context.getString(R.string.guardian_tile_closed_unit)
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                factTile(
                    context = context,
                    ui = ui,
                    hue = ui.palette.fieldLilac,
                    kicker = context.getString(R.string.guardian_tile_left),
                    figure = data.remainingMinutes.toString(),
                    unit = " " + context.getString(R.string.unit_minutes_short)
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(tiles)

        root.addView(ui.vgap(ui.dimens.dp(16)))

        // Honest sky footnote with the flat pink companion centered beside the text.
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = field(ui.palette.fieldSky, ui.dimens.radiusCard)
                setPadding(ui.dimens.dp(18), ui.dimens.dp(16), ui.dimens.dp(18), ui.dimens.dp(16))

                addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_alive_character)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(46), ui.dimens.dp(46)).apply {
                            marginEnd = ui.dimens.dp(14)
                        }
                    }
                )
                addView(
                    ui.note(data.note).apply {
                        setTextColor(ui.palette.inkSecondary)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                )
            }
        )

        return root
    }

    private fun factTile(
        context: Context,
        ui: AliveComponents,
        hue: Int,
        kicker: String,
        figure: String,
        unit: String?
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = field(hue, ui.dimens.radiusTile)
        val pad = ui.dimens.dp(14)
        setPadding(pad, pad, pad, pad)
        addView(ui.kicker(kicker))
        addView(ui.vgap(ui.dimens.dp(6)))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(
                    TextView(context).apply {
                        text = figure
                        setTextAppearance(R.style.TextAppearance_ALIVE_Number)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                        setTextColor(ui.palette.ink)
                        includeFontPadding = false
                    }
                )
                if (!unit.isNullOrEmpty()) {
                    addView(
                        TextView(context).apply {
                            text = unit
                            setTextAppearance(R.style.TextAppearance_ALIVE_MetaTiny)
                            setTextColor(ui.palette.inkSecondary)
                            includeFontPadding = false
                            setPadding(0, 0, 0, ui.dimens.dp(2))
                        }
                    )
                }
            }
        )
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
