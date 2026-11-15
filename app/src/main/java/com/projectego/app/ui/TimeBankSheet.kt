package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projectego.app.R

/**
 * Time Bank modal bottom sheet (`p2_12_bank_waktu.png` pass-2 parity).
 */
object TimeBankSheet {

    data class Data(
        val availableMinutes: Long,
        val earnedMinutes: Long,
        val spentMinutes: Long,
        val claimedToday: Long,
        val dailyCap: Long,
        val claimableMinutes: Long,
        val recoveryDay: Boolean,
        val firstSourceLabel: String?,
        val secondSourceLabel: String?,
        val streakSafe: Boolean,
        val onClaim: () -> Unit,
        val onDismiss: () -> Unit
    )

    fun build(context: Context, ui: AliveComponents, data: Data): View {
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

        val groteskBold = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)

        // Butter balance card (#FFE9A8): headline number + hourglass + 12dp meter bar.
        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = field(ui.palette.fieldButter, ui.dimens.radiusCard)
                val pad = ui.dimens.dp(20)
                setPadding(pad, pad, pad, pad)

                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                addView(ui.kicker(context.getString(R.string.time_bank_heading)))
                                addView(ui.vgap(ui.dimens.dp(6)))
                                addView(
                                    LinearLayout(context).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        gravity = Gravity.CENTER_VERTICAL
                                        addView(
                                            TextView(context).apply {
                                                text = data.availableMinutes.toString()
                                                setTextAppearance(R.style.TextAppearance_ALIVE_Timer)
                                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                                                setTextColor(ui.palette.ink)
                                                includeFontPadding = false
                                            }
                                        )
                                        addView(ui.hgap(ui.dimens.dp(10)))
                                        addView(
                                            TextView(context).apply {
                                                text = context.getString(R.string.time_bank_minutes_stacked)
                                                setTextAppearance(R.style.TextAppearance_ALIVE_MetaSmall)
                                                setTextColor(ui.palette.inkSecondary)
                                                includeFontPadding = false
                                            }
                                        )
                                    }
                                )
                            }
                        )
                        addView(
                            ImageView(context).apply {
                                setImageResource(R.drawable.ic_hourglass_flat)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                contentDescription = null
                                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(30), ui.dimens.dp(30))
                            }
                        )
                    }
                )
                addView(ui.vgap(ui.dimens.dp(14)))
                val fraction = when {
                    data.availableMinutes <= 0L -> 0f
                    data.earnedMinutes > 0L -> (data.availableMinutes.toFloat() / maxOf(data.earnedMinutes, 60L)).coerceIn(0.15f, 1f)
                    else -> 1f
                }
                addView(BankMeter(context, ui, fraction))
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(14)))

        val actionText = when {
            data.claimableMinutes > 0L -> context.getString(R.string.time_bank_claim_action, data.claimableMinutes)
            data.recoveryDay -> context.getString(R.string.time_bank_claim_recovery)
            data.claimedToday >= data.dailyCap -> context.getString(R.string.time_bank_claim_full)
            else -> context.getString(R.string.time_bank_claim_action_off)
        }
        sheet.addView(
            ui.primary(actionText).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ui.dimens.dp(52)
                )
                val enabled = data.claimableMinutes > 0L
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.45f
                setOnClickListener {
                    if (enabled) data.onClaim()
                }
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(14)))

        // Three tiles: MASUK | KELUAR | KLAIM HARI INI.
        sheet.addView(
            ui.equalRow().apply {
                addView(
                    bankTile(
                        context = context,
                        ui = ui,
                        hue = ui.palette.flatMint,
                        kicker = context.getString(R.string.time_bank_tile_in),
                        value = "+${data.earnedMinutes}"
                    ),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(ui.hgap(ui.dimens.dp(10)))
                addView(
                    bankTile(
                        context = context,
                        ui = ui,
                        hue = ui.palette.fieldButter,
                        kicker = context.getString(R.string.time_bank_tile_out),
                        value = data.spentMinutes.toString()
                    ),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(ui.hgap(ui.dimens.dp(10)))
                addView(
                    bankTile(
                        context = context,
                        ui = ui,
                        hue = ui.palette.fieldRose,
                        kicker = context.getString(R.string.time_bank_tile_claimed),
                        value = context.getString(
                            R.string.time_bank_claimed_fraction,
                            data.claimedToday,
                            data.dailyCap
                        )
                    ),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
        )

        sheet.addView(ui.vgap(ui.dimens.dp(14)))

        // Bottom source pills + trailing face circle.
        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val chip1 = data.firstSourceLabel ?: context.getString(R.string.beranda_missions)
                val chip2 = data.secondSourceLabel ?: context.getString(R.string.beranda_focus)
                addView(faceChip(context, ui, groteskBold, chip1, ui.palette.tile, R.drawable.ic_smiley_flat))
                addView(ui.hgap(ui.dimens.dp(8)))
                addView(faceChip(context, ui, groteskBold, chip2, ui.palette.fieldSky, R.drawable.ic_smiley_flat))
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    }
                )
                addView(
                    ImageView(context).apply {
                        setImageResource(if (data.streakSafe) R.drawable.ic_smiley_flat else R.drawable.ic_face_neutral)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                        setPadding(ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7))
                        background = oval(ui.palette.tile)
                        layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(34), ui.dimens.dp(34))
                    }
                )
            }
        )

        scrim.addView(
            sheet,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        return scrim
    }

    private fun bankTile(
        context: Context,
        ui: AliveComponents,
        hue: Int,
        kicker: String,
        value: String
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = field(hue, ui.dimens.radiusTile)
        val pad = ui.dimens.dp(14)
        setPadding(pad, pad, pad, pad)
        addView(ui.kicker(kicker))
        addView(ui.vgap(ui.dimens.dp(6)))
        addView(
            TextView(context).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_ALIVE_Number)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                setTextColor(ui.palette.ink)
                includeFontPadding = false
            }
        )
    }

    private fun faceChip(
        context: Context,
        ui: AliveComponents,
        typeface: android.graphics.Typeface?,
        label: String,
        hue: Int,
        faceRes: Int
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = field(hue, ui.dimens.radiusPill)
        setPadding(ui.dimens.dp(6), ui.dimens.dp(4), ui.dimens.dp(12), ui.dimens.dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            ui.dimens.dp(34)
        )
        addView(
            ImageView(context).apply {
                setImageResource(faceRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
                setPadding(ui.dimens.dp(5), ui.dimens.dp(5), ui.dimens.dp(5), ui.dimens.dp(5))
                background = oval(0xB8FFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(24), ui.dimens.dp(24)).apply {
                    marginEnd = ui.dimens.dp(7)
                }
            }
        )
        addView(
            TextView(context).apply {
                text = label
                this.typeface = typeface
                setTextColor(ui.palette.ink)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                includeFontPadding = false
                maxLines = 1
            }
        )
    }

    private class BankMeter(
        context: Context,
        ui: AliveComponents,
        fraction: Float
    ) : View(context) {
        private val share = fraction.coerceIn(0f, 1f)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.palette.tile
            alpha = 185
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.palette.ink
        }
        private val shape = RectF()

        init {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(12)
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0) return
            val radius = height / 2f
            shape.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(shape, radius, radius, track)
            if (share <= 0f) return
            shape.set(0f, 0f, width * share, height.toFloat())
            canvas.drawRoundRect(shape, radius, radius, fill)
        }
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
