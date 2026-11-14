package com.projectego.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The week's faces, shared by Beranda, Misi and Profil: a lived day is a ring
 * around a near-white face, a day not lived yet is a quiet grey disc with no
 * face. Three screens print this row, so the row is locked once, here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DayFacesTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    @Test
    fun `seven discs, a face only on the days already lived`() {
        val row = DayFaces.row(context, ui, 3)
        assertEquals(7, row.childCount)
        for (day in 0 until 7) {
            val disc = row.getChildAt(day) as FrameLayout
            val face = disc.background as GradientDrawable
            val fill = face.color!!.defaultColor
            if (day < 3) {
                assertEquals("day $day carries a face", 1, disc.childCount)
                assertEquals(0xB8FFFFFF.toInt(), fill)
            } else {
                assertEquals("day $day is still quiet", 0, disc.childCount)
                assertEquals(ui.palette.soft, fill)
            }
        }
    }

    @Test
    fun `a streak longer than the week still prints seven faces`() {
        val row = DayFaces.row(context, ui, 9)
        assertEquals(7, row.childCount)
        for (day in 0 until 7) {
            assertEquals(1, (row.getChildAt(day) as FrameLayout).childCount)
        }
    }
}