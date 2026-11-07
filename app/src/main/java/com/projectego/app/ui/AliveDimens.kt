package com.projectego.app.ui

import android.content.Context
import androidx.annotation.DimenRes
import com.projectego.app.R

/**
 * ALIVE's spacing scale, in pixels, converted from dp.
 *
 * `setPadding()` and `LayoutParams` take pixels, so a bare integer makes the
 * layout density-dependent: the same number is generous on xxhdpi and cramped on
 * mdpi. Every gap in ALIVE comes from here instead.
 */
class AliveDimens(context: Context) {

    private val resources = context.resources
    private val density = resources.displayMetrics.density

    /** 4dp */
    val xs = px(R.dimen.space_xs)

    /** 8dp */
    val s = px(R.dimen.space_s)

    /** 12dp */
    val m = px(R.dimen.space_m)

    /** 16dp */
    val l = px(R.dimen.space_l)

    /** 24dp */
    val xl = px(R.dimen.space_xl)

    /** 32dp */
    val xxl = px(R.dimen.space_xxl)

    /** 48dp */
    val xxxl = px(R.dimen.space_xxxl)

    val pageHorizontal = px(R.dimen.screen_h_padding)
    val pageTop = px(R.dimen.screen_top_padding)
    val pageBottom = px(R.dimen.screen_bottom_padding)

    /**
     * Rhythm, one value per relationship. See res/values/dimens.xml for why
     * `cardGap` is as wide as it is and why nothing inside a card reaches it.
     */
    val cardGap = px(R.dimen.card_gap)
    val groupGap = px(R.dimen.group_gap)
    val lineGap = px(R.dimen.line_gap)
    val pairGap = px(R.dimen.pair_gap)
    val cardInset = px(R.dimen.card_inset)
    val ledgerInset = px(R.dimen.ledger_inset)

    val touchMin = px(R.dimen.touch_min)

    /**
     * The corner of each object family, in pixels. A pill is 999dp wide on
     * purpose: half of it is always more than its own height, so it stays a
     * capsule whatever text it ends up holding.
     */
    val radiusCard = px(R.dimen.radius_card)
    val radiusField = px(R.dimen.radius_field)
    val radiusTile = px(R.dimen.radius_tile)
    val radiusStep = px(R.dimen.radius_step)
    val radiusPill = px(R.dimen.radius_pill)

    /** One block of a meter. Square, because a stretched pixel is not a pixel. */
    val stepSize = px(R.dimen.step_size)
    val stepGap = px(R.dimen.step_gap)

    /** A full-size sprite, at the size the dimen says. */
    val character = sprite(R.dimen.character_size)
    val characterLarge = sprite(R.dimen.character_size_large)

    /** A category mark beside a list row, and the smaller one in the history. */
    val markSize = sprite(R.dimen.mark_size)
    val markSmallSize = sprite(R.dimen.mark_small_size)

    /** The bottom navigation icon, which used to be 24dp on a 16 grid. */
    val navIcon = sprite(R.dimen.nav_icon_size)

    /** The mark beside the wordmark. It is flat curves now, so the size is
     *  simply the size: nothing about it needs to land on a whole cell. */
    val brandMarkSize = sprite(R.dimen.brand_mark_size)

    /**
     * A sprite size in pixels.
     *
     * This used to round to whole 16 px cells, because a stretched pixel is not
     * a pixel. The flat family is curves and the approved render states a
     * legibility ladder of 24 / 32 / 48 / 72 / 104 px - three of which are not
     * multiples of 16 - so the rule it existed for is gone and the rounding goes
     * with it. Sizes are now exactly what the dimen says.
     */
    fun sprite(@DimenRes id: Int): Int = px(id)

    fun spriteDp(value: Int): Int = dp(value)

    /** For one-off measurements that do not deserve their own dimen resource. */
    fun dp(value: Int): Int = (value * density).toInt()

    private fun px(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)
}
