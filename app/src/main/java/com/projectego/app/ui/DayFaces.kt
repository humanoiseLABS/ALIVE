package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.projectego.app.R

/**
 * The week's seven faces, as the approved render draws them on every screen
 * that carries a streak: a lived day is a coloured ring around a near-white
 * face, and a day not lived yet is a quiet grey disc with no face at all.
 *
 * One component because Beranda, Misi and Profil all print the same week, and
 * three hand-kept copies of it drifted into three different streaks.
 */
object DayFaces {

    fun row(context: Context, ui: AliveComponents, streak: Int): LinearLayout =
        ui.row().apply {
            val hues = listOf(
                ui.palette.fieldButter,
                ui.palette.flatMint,
                ui.palette.fieldLilac,
                ui.palette.fieldSky,
                ui.palette.fieldRose,
                ui.palette.fieldPeach,
                ui.palette.fieldAqua
            )
            for (day in 0 until 7) {
                val lived = day < streak
                val disc = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (lived) {
                            setColor(ColorUtils.setAlphaComponent(ui.palette.tile, 184))
                            setStroke(ui.dimens.dp(3), hues[day % hues.size])
                        } else {
                            setColor(ui.palette.soft)
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(34), ui.dimens.dp(34)).apply {
                        if (day < 6) marginEnd = ui.dimens.dp(6)
                    }
                }
                if (lived) {
                    disc.addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_smiley_flat)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = null
                            layoutParams = FrameLayout.LayoutParams(ui.dimens.dp(18), ui.dimens.dp(18), Gravity.CENTER)
                        }
                    )
                }
                addView(disc)
            }
        }
}