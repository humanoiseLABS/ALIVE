package com.projectego.app.ui

import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The exit is a 48dp tile, and the title beside it is centred on the page.
 *
 * The second half is geometry, not taste: the header puts a spacer of the same
 * width opposite the button, so the title's centre is the page's centre only if
 * the button really measures one touch target. It did not - the platform button
 * style's own 88dp minimum won, which pushed every page title 20dp right of
 * centre and was measured from a screenshot on 2026-10-21.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackButtonTest {

    private val ui = AliveComponents(
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_ProjectEgo
        )
    )

    @Test
    fun `the exit measures exactly one touch target`() {
        val back = ui.backButton("Back")
        val limit = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
        back.measure(limit, limit)

        assertEquals(ui.dimens.touchMin, back.measuredWidth)
        assertEquals(ui.dimens.touchMin, back.measuredHeight)
    }

    @Test
    fun `the arrow sits on the middle of the face, not the bounds`() {
        val back = ui.backButton("Back")
        val limit = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
        back.measure(limit, limit)

        val face = ui.dimens.touchMin
        val arrow = back.measuredWidth - back.paddingLeft - back.paddingRight
        val arrowCentre = back.paddingLeft + arrow / 2f
        assertEquals(face / 2f, arrowCentre, 0.51f)
        // The third rule this test used to carry - `arrow % 16 == 0` - was a
        // pixel-grid rule, and Batch 3 removed the grid with the sprite snap.
        // Centring is what keeps the title on the page's axis; whole sprite
        // cells are nothing the flat family can be asked to land on.
    }
}
