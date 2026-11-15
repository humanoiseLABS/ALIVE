package com.projectego.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.projectego.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Beranda, as the approved pass-2 render states it.
 *
 * The Arcade card it replaced was locked by `ArcadeTodayCardTest`; that file is
 * gone with the card, and these assertions are what keeps the new screen honest:
 * one board, three facts, one action, a closed day that says TUTUP, and a family
 * row that is seven faces rather than seven blobs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BerandaScreenTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_ProjectEgo
    )
    private val ui = AliveComponents(context)

    private var action = 0
    private var growth = 0
    private var focus = 0
    private var openFocus = 0
    private var timeBank = 0

    private fun build(
        remaining: Long = 57L,
        limit: Long = 180L,
        used: Long = 123L,
        missionsDone: Int = 1,
        missionsTotal: Int = 12,
        focusMinutes: Long = 25L,
        streak: Int = 2,
        tomorrowSafe: Boolean = false,
        closed: Boolean = false,
        critical: Boolean = false,
        actionLabel: String? = "Lanjutkan misi",
        bankMinutes: Long = 0L
    ): View = BerandaScreen.build(
        context,
        ui,
        BerandaScreen.Data(
            greeting = "Sore.",
            subLine = "57 menit tersisa sebelum jatah hari ini habis.",
            remainingMinutes = remaining,
            limitMinutes = limit,
            usedMinutes = used,
            missionsDone = missionsDone,
            missionsTotal = missionsTotal,
            focusMinutes = focusMinutes,
            streak = streak,
            tomorrowSafe = tomorrowSafe,
            closed = closed,
            closedWord = "TUTUP",
            critical = critical,
            focusTitle = "Pilih misi hari ini",
            actionLabel = actionLabel,
            onAction = { action += 1 },
            onGrowth = { growth += 1 },
            onFocus = { focus += 1 },
            onOpenFocus = { openFocus += 1 },
            bankMinutes = bankMinutes,
            onTimeBank = { timeBank += 1 }
        )
    )

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
        }
    }

    private fun backgrounds(view: View): List<Pair<View, Int>> = buildList {
        (view.background as? GradientDrawable)?.color?.defaultColor?.let { add(view to it) }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(backgrounds(view.getChildAt(index)))
        }
    }

    private fun firstText(view: View, value: String): TextView? {
        if (view is TextView && view.text.toString().contains(value)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                firstText(view.getChildAt(index), value)?.let { return it }
            }
        }
        return null
    }

    private fun allViews(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(allViews(view.getChildAt(index)))
        }
    }

    private fun clickableAncestor(view: View?): View? {
        var current = view
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent as? View
        }
        return null
    }

    @Test
    fun `the page opens with the greeting and the day's sentence`() {
        val page = build()
        val shown = texts(page)
        assertTrue(shown.contains("Sore."))
        assertTrue(shown.any { it.startsWith("57 menit tersisa") })
    }

    @Test
    fun `the board states the day in one figure and two small words`() {
        val page = build()
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains("57"))
        assertTrue(shown.contains(context.getString(R.string.unit_minutes_suffix)))
        assertTrue(shown.contains(context.getString(R.string.beranda_of_board, 180)))
    }

    @Test
    fun `the three facts carry their numbers`() {
        val page = build()
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains("123"))
        assertTrue(shown.contains("1"))
        assertTrue(shown.contains("25"))
        assertTrue(shown.contains(context.getString(R.string.beranda_of_board, 12)))
        assertTrue(
            "the used tile must wear the blossom field",
            backgrounds(page).any { it.second == ui.palette.fieldBlossom }
        )
    }

    @Test
    fun `the one action is a black pill with the day's label`() {
        val page = build()
        val label = firstText(page, "Lanjutkan misi")
        requireNotNull(label)
        val button = clickableAncestor(label)
        requireNotNull(button)
        button.performClick()
        assertEquals(1, action)
    }

    @Test
    fun `a closed day says TUTUP and never prints a share`() {
        val page = build(remaining = 0L, limit = 0L, closed = true, actionLabel = null)
        val shown = texts(page)
        assertTrue(shown.contains("TUTUP"))
        assertTrue("a closed board must not print a figure", shown.none { it == "0" })
    }

    @Test
    fun `a critical stretch wears the red field, a normal day the mint`() {
        val normal = backgrounds(build()).map { it.second }
        val critical = backgrounds(build(critical = true)).map { it.second }
        assertTrue(normal.contains(ui.palette.flatMint))
        assertTrue(critical.contains(ui.palette.shortPale))
    }

    @Test
    fun `the family row is seven faces and one way to the galaxy`() {
        val page = build()
        val row = allViews(page).firstOrNull { view ->
            val group = view as? ViewGroup ?: return@firstOrNull false
            view.isClickable && group.childCount == 7 &&
                (0 until group.childCount).all { group.getChildAt(it) is android.widget.FrameLayout }
        }
        requireNotNull(row)
        val group = row as ViewGroup
        val lived = (0 until 7).count { (group.getChildAt(it) as ViewGroup).childCount == 1 }
        assertEquals(2, lived)
        row.performClick()
        assertEquals(1, growth)
    }

    @Test
    fun `the focus card names today's block and opens it`() {
        val page = build()
        val title = firstText(page, "Pilih misi hari ini")
        val card = clickableAncestor(title)
        requireNotNull(card)
        card.performClick()
        assertEquals(1, focus)
    }

    @Test
    fun `the focus fact tile opens focus setup`() {
        val page = build()
        val tile = backgrounds(page).firstOrNull { it.second == ui.palette.fieldAqua }?.first
        requireNotNull(tile)
        assertTrue(tile.isClickable)
        tile.performClick()
        assertEquals(1, openFocus)
    }

    @Test
    fun `the three fact tiles share one equal height`() {
        val page = build()
        page.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val focusTile = backgrounds(page).firstOrNull { it.second == ui.palette.fieldAqua }?.first
        requireNotNull(focusTile)
        val row = focusTile.parent as ViewGroup
        assertTrue(row is AliveComponents.EqualRow)
        val tiles = (0 until row.childCount).map { row.getChildAt(it) }.filter { it is ViewGroup }
        assertEquals(3, tiles.size)
        val expectedHeight = tiles[0].measuredHeight
        assertTrue(expectedHeight > 0)
        tiles.forEach { assertEquals(expectedHeight, it.measuredHeight) }
    }

    @Test
    fun `the streak is stated in days and tomorrow's word`() {
        val page = build(streak = 2, tomorrowSafe = false)
        val shown = texts(page)
        assertTrue("shown=$shown", shown.contains(context.getString(R.string.beranda_streak_days, 2)))
        assertTrue(shown.contains(context.getString(R.string.beranda_tomorrow_open)))
    }

    @Test
    fun `header companion avatar opens growth sheet when tapped`() {
        val page = build() as ViewGroup
        val headerRow = page.getChildAt(0) as ViewGroup
        val avatar = (0 until headerRow.childCount)
            .map { headerRow.getChildAt(it) }
            .filterIsInstance<ImageView>()
            .firstOrNull()
        requireNotNull(avatar)
        assertTrue(avatar.isClickable)
        avatar.performClick()
        assertEquals(1, growth)
    }

    @Test
    fun `closed board shows quiet until-tomorrow cue and bank chip next to meter`() {
        val page = build(remaining = 0L, limit = 0L, closed = true, actionLabel = null, bankMinutes = 15L)
        val shown = texts(page)
        assertTrue(shown.contains(context.getString(R.string.beranda_closed_until_tomorrow)))
        val expectedChip = context.getString(R.string.focus_extend_bank_short, 15L)
        val chip = firstText(page, expectedChip)
        requireNotNull(chip)
        assertTrue(chip.isClickable)
        chip.performClick()
        assertEquals(1, timeBank)
        assertTrue(allViews(page).any { it is BerandaScreen.MeterBar })
    }

    @Test
    fun `unified streak card distributes seven day faces evenly across measured width`() {
        val page = build(streak = 3)
        val widthPx = 1080
        page.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        page.layout(0, 0, page.measuredWidth, page.measuredHeight)
        val row = allViews(page).firstOrNull { view ->
            val group = view as? ViewGroup ?: return@firstOrNull false
            view.isClickable && group.childCount == 7 &&
                (0 until group.childCount).all { group.getChildAt(it) is android.widget.FrameLayout }
        } as ViewGroup
        val firstDisc = row.getChildAt(0)
        val lastDisc = row.getChildAt(6)
        assertTrue("row=${row.width} lastRight=${lastDisc.right}", lastDisc.right > firstDisc.right)
        val firstGap = row.getChildAt(1).left - firstDisc.right
        val lastGap = lastDisc.left - row.getChildAt(5).right
        assertTrue("gaps should spread evenly: first=$firstGap last=$lastGap", kotlin.math.abs(firstGap - lastGap) <= 2)
    }

    @Test
    fun `meter bar animates sheen while attached and draws cleanly through attach and detach`() {
        val activity = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).setup().get()
        val themed = ContextThemeWrapper(activity, R.style.Theme_ProjectEgo)
        val meter = BerandaScreen.MeterBar(
            themed,
            AliveComponents(themed),
            fraction = 0.75f,
            accentColor = AliveComponents(themed).palette.flatMint
        )
        val root = android.widget.FrameLayout(themed)
        root.addView(meter, ViewGroup.LayoutParams(400, 36))
        activity.setContentView(root)
        meter.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(36, View.MeasureSpec.EXACTLY)
        )
        meter.layout(0, 0, 400, 36)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(650L, TimeUnit.MILLISECONDS)
        assertEquals(0.75f, meter.displayFraction, 0.05f)

        val bmp = Bitmap.createBitmap(400, 36, Bitmap.Config.ARGB_8888)
        meter.draw(Canvas(bmp))

        root.removeView(meter)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0.75f, meter.displayFraction, 0.001f)
    }
}
