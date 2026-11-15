package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.projectego.app.R
import kotlin.math.roundToInt

/**
 * The week's seven faces, as the approved render draws them on every screen
 * that carries a streak: a lived day is a coloured ring around a near-white
 * face, and a day not lived yet is a quiet grey disc with no face at all.
 *
 * One component because Beranda, Misi and Profil all print the same week, and
 * three hand-kept copies of it drifted into three different streaks.
 */
object DayFaces {

    fun row(
        context: Context,
        ui: AliveComponents,
        streak: Int,
        spreadEvenly: Boolean = false,
        @ColorInt unlivedFill: Int = ui.palette.soft,
        @ColorInt unlivedStroke: Int? = null,
        unlivedStrokeWidthDp: Float = 1.25f,
        ringColorOverrides: Map<Int, Int> = emptyMap()
    ): LinearLayout {
        val discPx = ui.dimens.dp(34)
        val container = if (spreadEvenly) {
            SpreadRow(
                context = context,
                discSizePx = discPx,
                minGapPx = ui.dimens.dp(2),
                unspecifiedGapPx = ui.dimens.dp(6)
            )
        } else {
            ui.row()
        }
        val hues = listOf(
            ui.palette.fieldButter,
            ui.palette.flatMint,
            ui.palette.fieldLilac,
            ui.palette.fieldSky,
            ui.palette.fieldRose,
            ui.palette.fieldPeach,
            ui.palette.fieldAqua
        )
        val unlivedStrokePx = (unlivedStrokeWidthDp * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(1)
        return container.apply {
            for (day in 0 until 7) {
                val lived = day < streak
                val rawHue = hues[day % hues.size]
                val ringHue = ringColorOverrides[rawHue] ?: rawHue
                val disc = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (lived) {
                            setColor(ColorUtils.setAlphaComponent(ui.palette.tile, 184))
                            setStroke(ui.dimens.dp(3), ringHue)
                        } else {
                            setColor(unlivedFill)
                            if (unlivedStroke != null) {
                                setStroke(unlivedStrokePx, unlivedStroke)
                            }
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(discPx, discPx).apply {
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

    /**
     * Distributes seven circular day discs evenly across available horizontal width without
     * introducing extra child spacer views so structural child-count contracts stay intact.
     */
    private class SpreadRow(
        context: Context,
        private val discSizePx: Int,
        private val minGapPx: Int,
        private val unspecifiedGapPx: Int
    ) : LinearLayout(context) {

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val count = childCount
            if (count > 1) {
                val gap = if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
                        .coerceAtLeast(0)
                    ((available - count * discSizePx) / (count - 1)).coerceAtLeast(minGapPx)
                } else {
                    unspecifiedGapPx
                }
                for (index in 0 until count) {
                    val lp = getChildAt(index).layoutParams as? LayoutParams ?: continue
                    lp.marginEnd = if (index < count - 1) gap else 0
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}