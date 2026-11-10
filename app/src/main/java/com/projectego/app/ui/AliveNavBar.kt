package com.projectego.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.ImageViewCompat
import com.projectego.app.R

/**
 * ALIVE's bottom bar: one white dock, four keys, and the chosen key is an ink
 * pill inside it - the approved `nav.dock` from flat-system.css.
 *
 * The dock floats on the page: 24dp of air on each side and 26dp under it, so
 * the page's own ground (lavender on Beranda, butter on Misi, mint on Profil)
 * stays visible around it. Nothing is cut out of the strip and nothing rises
 * above it: the earlier notch and the floating circle belonged to a bordered
 * strip, and the flat language has neither.
 *
 * The keys keep their words. An icon alone would be a guess, and the dock is the
 * only map of the app.
 */
class AliveNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Where the bar's four positions are, in tap order. */
    object Index {
        const val HOME = 0
        const val QUEST = 1
        const val STATS = 2
        const val PROFILE = 3

        /** The position that stands for a tab. */
        fun indexForTab(tab: Int): Int = when (tab) {
            0 -> HOME
            1 -> QUEST
            else -> PROFILE
        }
    }

    /** Called on every tap, including a re-tap of the key already chosen. */
    var onSelect: ((Int) -> Unit)? = null

    private val ui = AliveComponents(context)
    private val palette = ui.palette
    private val dimens = ui.dimens

    private data class Slot(@param:StringRes val label: Int, @param:DrawableRes val glyph: Int)

    private val slots = listOf(
        Slot(R.string.nav_home, R.drawable.ic_dock_home),
        Slot(R.string.nav_quest, R.drawable.ic_dock_quest),
        Slot(R.string.nav_stats, R.drawable.ic_dock_stats),
        Slot(R.string.nav_profile, R.drawable.ic_dock_profile)
    )

    private val cells = mutableListOf<LinearLayout>()
    private val glyphs = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var selected = -1

    init {
        // Air around the dock, measured from the page, not from the bar.
        setPadding(dimens.dp(24), 0, dimens.dp(24), dimens.dp(26))

        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(palette.tile)
            setPadding(dimens.dp(6), 0, dimens.dp(6), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dimens.dp(64))
        }

        slots.forEachIndexed { index, slot ->
            val glyph = ImageView(context).apply {
                setImageResource(slot.glyph)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(dimens.dp(18), dimens.dp(18))
            }
            val label = TextView(context).apply {
                text = context.getString(slot.label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                includeFontPadding = false
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dimens.dp(6) }
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(slot.label)
                layoutParams = LinearLayout.LayoutParams(0, dimens.dp(48), 1f).apply {
                    if (index > 0) marginStart = dimens.dp(2)
                }
                addView(glyph)
                addView(label)
                setOnClickListener {
                    select(index)
                    onSelect?.invoke(index)
                }
            }
            cells += cell
            glyphs += glyph
            labels += label
            dock.addView(cell)
        }

        addView(dock)
        paint()
    }

    /**
     * Choose a key. There is no travel to animate: the approved dock swaps the
     * pill in place, so [animate] is kept only for the callers that restored a
     * tab and used to ask for stillness.
     */
    fun select(index: Int, animate: Boolean = true) {
        if (index !in slots.indices) return
        selected = index
        paint()
    }

    private fun paint() {
        cells.forEachIndexed { index, cell ->
            val on = index == selected
            cell.background = if (on) pill(palette.ink) else null
            cell.isSelected = on
            labels[index].setTextColor(if (on) palette.flatPaper else palette.inkDisabled)
            ImageViewCompat.setImageTintList(
                glyphs[index],
                ColorStateList.valueOf(if (on) palette.flatPaper else palette.inkDisabled)
            )
            glyphs[index].alpha = if (on) 1f else 0.5f
        }
    }

    private fun pill(@ColorInt face: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(face)
        cornerRadius = dimens.radiusPill.toFloat()
    }
}
