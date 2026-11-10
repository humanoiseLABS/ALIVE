package com.projectego.app.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.projectego.app.R

/**
 * Every color ALIVE draws with, resolved from res/values/colors.xml.
 *
 * Three families, and the difference between them is what a colour is allowed to
 * mean (Constitution sec.07):
 *
 *  - The constitution's six: paper, ink, forest, soft forest, warm gray, white.
 *    Structure and identity.
 *  - Three role hues - gold is reward, red is time running out, blue is the work
 *    itself - plus `amber`, which is the colour of an offer: the one thing on a
 *    screen you are being invited to press, and `pinkSoft`, the critter's own
 *    rose, measured to hold ink the way the pales do.
 *  - Nine category hues in `QuestHues`. Those are not status and not text: a
 *    category hue lives on a card's tab and the tile its mark sits in, nowhere
 *    else, which is what lets nine of them exist without a legend.
 *
 * A missing tone is added as the ink hue with alpha, never as a new color - so
 * `inkSecondary`, `inkMuted` and `inkHairline` are all still ink.
 *
 * `warmGray` is a surface color only. At #D8D2C7 on paper it sits around 1.2:1
 * contrast and must never be used for text.
 */
class AlivePalette(context: Context) {

    private val appContext = context.applicationContext

    /**
     * The flat surfaces. The field is the structure now - a card is the colour it
     * is - so these are the four every surface answers to: the ground a page
     * stands on, the white a field wears, the paper a dark field's text is
     * written in, and the ink everything else reads in.
     */
    val ground = color(R.color.flat_ground)
    val tile = color(R.color.flat_tile)
    val flatPaper = color(R.color.flat_paper)
    val flatInk = color(R.color.flat_ink)

    /**
     * The varied page grounds from the approved render: the six screens do not
     * all stand on the same cream, and that sameness is what made the first flat
     * pass read as "cuma dipoles".
     */
    val groundLav = color(R.color.flat_g_lav)
    val groundMint = color(R.color.flat_g_mint)
    val groundBlush = color(R.color.flat_g_blush)
    val groundButter = color(R.color.flat_g_butter)
    val groundSky = color(R.color.flat_g_sky)
    val groundPeach = color(R.color.flat_g_peach)

    /** The mint a landed field wears: quiet, and not one of the family's status hues. */
    val flatMint = color(R.color.flat_field_mint)

    /** The three field hues Beranda's fact tiles wear in the approved render. */
    val fieldRose = color(R.color.flat_field_rose)
    val fieldButter = color(R.color.flat_field_butter)
    val fieldAqua = color(R.color.flat_field_aqua)
    val fieldBlossom = color(R.color.flat_field_blossom)
    val fieldSky = color(R.color.flat_field_sky)
    val fieldLilac = color(R.color.flat_field_lilac)
    val fieldPeach = color(R.color.flat_field_peach)
    val fieldCoral = color(R.color.flat_field_coral)

    val paper = color(R.color.flat_ground)
    val canvas = color(R.color.flat_ground)
    val ink = color(R.color.flat_ink)
    val forest = color(R.color.alive_forest)
    val softForest = color(R.color.alive_soft_forest)
    val warmGray = color(R.color.alive_warm_gray)
    val white = color(R.color.flat_tile)

    val inkSecondary = color(R.color.alive_ink_secondary)
    val inkMuted = color(R.color.alive_ink_muted)
    val inkDisabled = color(R.color.alive_ink_disabled)
    val inkHairline = color(R.color.alive_ink_hairline)
    val forestSoft = color(R.color.alive_forest_soft)

    /**
     * Three game roles, each with one job. Read the role, not the colour: if a
     * call site wants gold for something that is not a reward, it is using the
     * wrong token.
     *
     * `*_dark` carries text or draws an edge and is measured to clear 4.5:1 on
     * every ground (see `AlivePaletteTest`). `*_mid` is a fill only and never
     * carries text - white on gold mid is 2.2:1. `*_pale` is a surface to read
     * ink on.
     */
    val rewardDark = color(R.color.alive_gold_reward_dark)
    val rewardMid = color(R.color.alive_gold_reward_mid)
    val rewardPale = color(R.color.alive_gold_reward_pale)

    val shortDark = color(R.color.alive_red_short_dark)
    val shortMid = color(R.color.alive_red_short_mid)
    val shortPale = color(R.color.alive_red_short_pale)

    val workDark = color(R.color.alive_blue_work_dark)
    val workMid = color(R.color.alive_blue_work_mid)

    /** A card face, not a fill: the one blue that carries a caption's secondary ink. */
    val workSoft = color(R.color.alive_blue_work_soft)

    val workPale = color(R.color.alive_blue_work_pale)

    /** "Do this." Brighter and flatter than reward gold, and never on the same object. */
    val amber = color(R.color.alive_amber_action)

    /**
     * The critter's own rose - one flat pink, no outline. Like the role pales it
     * is a surface ink can read on (`AlivePaletteTest` measures the pair), which
     * is what lets a tile wear it.
     */
    val pinkSoft = color(R.color.alive_pink_soft)

    private fun color(id: Int): Int = ContextCompat.getColor(appContext, id)
}
