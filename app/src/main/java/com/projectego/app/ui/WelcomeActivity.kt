package com.projectego.app.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import com.projectego.app.R
import com.projectego.app.data.WelcomeGate

/**
 * The first-run welcome: three screens, shown once, before the app proper.
 *
 * Screen one is the only place ALIVE allows itself poetry - someone trying to
 * climb out of a relapse loop is being handed a door, not a product tour, and
 * the door deserves to say why it exists. Screens two and three obey the
 * standing clarity rule: the mechanic is stated plainly first (120 minutes,
 * what closes, what pays minutes back) and the voice stays out of the way. The
 * two switches on screen three are the same ones Home keeps asking about
 * afterwards; this is simply the first, calmer ask, and skipping it costs
 * nothing because the Home card is still there.
 *
 * It touches exactly three things: two permission checks and one flag. No
 * engine state - the welcome introduces ALIVE without reading a single number
 * ALIVE keeps, which is also why it can never disagree with Home about them.
 *
 * Back walks backwards through the steps; back on the first screen leaves the
 * welcome unfinished (the flag is only written on Start or Skip), so leaving a
 * screen unread does not count as having read it.
 */
class WelcomeActivity : AppCompatActivity() {

    private companion object {
        const val KEY_STEP = "welcome_step"
        const val LAST_STEP = 2
    }

    private val ui by lazy { AliveComponents(this) }
    private var step = 0
    private lateinit var rootFrame: FrameLayout
    private lateinit var backdrop: FrameLayout
    private lateinit var content: AliveComponents.Stack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt(KEY_STEP) ?: 0

        rootFrame = FrameLayout(this)
        backdrop = FrameLayout(this)
        content = ui.stack(ui.dimens.cardGap).apply {
            setPadding(
                ui.dimens.pageHorizontal,
                ui.dimens.pageTop,
                ui.dimens.pageHorizontal,
                ui.dimens.pageBottom
            )
        }
        rootFrame.addView(
            backdrop,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootFrame.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(content)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(rootFrame)

        // Forward is a button; back is back. Nobody is trapped: leaving from
        // the first screen just postpones this until the next cold start.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 0) {
                    step -= 1
                    renderStep()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * Rendered on every resume, not just the first: this screen sends people
     * to Settings, and the two status lines must be true by the time they
     * come back.
     */
    override fun onResume() {
        super.onResume()
        renderStep()
    }

    private fun renderStep() {
        backdrop.removeAllViews()
        content.removeAllViews()
        val paper = ContextCompat.getColor(this, R.color.flat_paper)
        val deep = ContextCompat.getColor(this, R.color.flat_deep)
        val ground = when (step) {
            0 -> deep
            1 -> ui.palette.groundLav
            else -> ui.palette.groundButter
        }
        rootFrame.setBackgroundColor(ground)
        window.statusBarColor = ground
        window.navigationBarColor = ground
        val lightChrome = step != 0
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightChrome
            isAppearanceLightNavigationBars = lightChrome
        }
        when (step) {
            0 -> buildPhilosophy(paper)
            1 -> buildMechanics()
            else -> buildPermissions()
        }
    }

    /** Screen one: who this is for, in the only voice that gets to be a voice. */
    private fun buildPhilosophy(paper: Int) {
        content.addView(
            TextView(this).apply {
                text = getString(R.string.alive_wordmark)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = ResourcesCompat.getFont(this@WelcomeActivity, R.font.space_grotesk_bold)
                setTextColor(paper)
                includeFontPadding = false
            }
        )
        content.addView(ui.vgap(ui.dimens.lineGap))
        content.addView(
            welcomeChip(
                getString(R.string.welcome_step_badge, step + 1, LAST_STEP + 1),
                ContextCompat.getColor(this, R.color.flat_navy)
            ).apply { setTextColor(paper) }
        )
        content.addView(ui.vgap(ui.dimens.groupGap))
        content.addView(
            ui.h1(getString(R.string.welcome_philosophy_h1)).apply {
                setTextColor(paper)
                setPadding(0, 0, ui.dimens.dp(64), 0)
            }
        )
        content.addView(ui.vgap(ui.dimens.dp(14)))
        content.addView(
            ui.note(getString(R.string.welcome_philosophy_sub)).apply {
                setTextColor(ColorUtils.setAlphaComponent(paper, 175))
                setPadding(0, 0, ui.dimens.dp(44), 0)
            }
        )
        content.addView(ui.vgap(ui.dimens.dp(16)))

        // Ambient off-edge circles sit behind the whole screen while the
        // character stands centered with clear navy space around it.
        backdrop.addView(
            deepCircle(
                hue = ui.palette.fieldLilac,
                size = ui.dimens.dp(156),
                gravity = Gravity.TOP or Gravity.END,
                top = ui.dimens.dp(66),
                right = ui.dimens.dp(-76)
            )
        )
        backdrop.addView(
            deepCircle(
                hue = ui.palette.flatMint,
                size = ui.dimens.dp(184),
                gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                left = ui.dimens.dp(-96),
                top = ui.dimens.dp(66)
            )
        )
        backdrop.addView(
            deepCircle(
                hue = ui.palette.fieldButter,
                size = ui.dimens.dp(154),
                gravity = Gravity.BOTTOM or Gravity.END,
                right = ui.dimens.dp(-54),
                bottom = ui.dimens.dp(-92)
            )
        )

        content.addView(ui.vgap(ui.dimens.dp(12)))
        val hero = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(148)
            )
        }
        hero.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_alive_character)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
                layoutParams = FrameLayout.LayoutParams(ui.dimens.dp(112), ui.dimens.dp(112)).apply {
                    gravity = Gravity.CENTER
                }
            }
        )
        content.addView(hero)
        content.addView(ui.vgap(ui.dimens.groupGap))

        content.addView(
            ui.row().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(welcomeChip(getString(R.string.welcome_chip_minutes), ui.palette.flatMint))
                addView(ui.hgap(ui.dimens.dp(8)))
                addView(welcomeChip(getString(R.string.welcome_chip_closed), ui.palette.fieldPeach))
                addView(ui.hgap(ui.dimens.dp(8)))
                addView(welcomeChip(getString(R.string.welcome_chip_mission), ui.palette.fieldLilac))
            }
        )
        content.addView(ui.vgap(ui.dimens.groupGap))
        content.addView(
            ui.row().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                val hues = listOf(ui.palette.flatMint, ui.palette.fieldButter, ui.palette.fieldLilac, ui.palette.fieldRose, ui.palette.fieldSky)
                hues.forEachIndexed { index, hue ->
                    if (index > 0) addView(ui.hgap(ui.dimens.dp(8)))
                    addView(
                        ImageView(this@WelcomeActivity).apply {
                            setImageResource(R.drawable.ic_smiley_flat)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = null
                            setPadding(ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7), ui.dimens.dp(7))
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(hue)
                            }
                            layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(32), ui.dimens.dp(32))
                        }
                    )
                }
            }
        )
        content.addView(ui.vgap(ui.dimens.groupGap))
        content.addView(
            fullWidth(ui.secondary(getString(R.string.welcome_next))).apply {
                setOnClickListener {
                    step += 1
                    renderStep()
                }
            }
        )
    }

    private fun welcomeChip(text: String, face: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(ui.palette.ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = ResourcesCompat.getFont(this@WelcomeActivity, R.font.space_grotesk_bold)
            background = GradientDrawable().apply {
                cornerRadius = ui.dimens.dp(999).toFloat()
                setColor(face)
            }
            setPadding(ui.dimens.dp(12), ui.dimens.dp(7), ui.dimens.dp(12), ui.dimens.dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

    private fun deepCircle(
        hue: Int,
        size: Int,
        gravity: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): View =
        View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(hue)
            }
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                this.gravity = gravity
                leftMargin = left
                topMargin = top
                rightMargin = right
                bottomMargin = bottom
            }
        }

    /** Screen two: the loop, stated as plainly as the clarity rule demands. */
    private fun buildMechanics() {
        content.addView(
            welcomeChip(
                getString(R.string.welcome_step_badge, step + 1, LAST_STEP + 1),
                ui.palette.fieldLilac
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
        content.addView(ui.h1(getString(R.string.welcome_loop_heading)))
        val stepCards = listOf(
            R.string.welcome_loop_1 to ui.palette.fieldLilac,
            R.string.welcome_loop_2 to ui.palette.flatMint,
            R.string.welcome_loop_3 to ui.palette.fieldSky,
            R.string.welcome_loop_4 to ui.palette.fieldButter
        )
        val groteskBold = ResourcesCompat.getFont(this, R.font.space_grotesk_bold)
        stepCards.forEachIndexed { index, (line, hue) ->
            content.addView(
                ui.row().apply {
                    background = GradientDrawable().apply {
                        cornerRadius = ui.dimens.radiusTile.toFloat()
                        setColor(hue)
                    }
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(ui.dimens.dp(18), ui.dimens.dp(16), ui.dimens.dp(18), ui.dimens.dp(16))
                    addView(
                        TextView(this@WelcomeActivity).apply {
                            text = "%02d".format(index + 1)
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                            typeface = groteskBold
                            includeFontPadding = false
                            setTextColor(ui.palette.ink)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(ui.palette.flatPaper)
                            }
                            layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(32), ui.dimens.dp(32))
                        }
                    )
                    addView(ui.hgap(ui.dimens.dp(12)))
                    addView(ui.weighted(ui.body(getString(line))))
                }
            )
        }
        content.addView(ui.note(getString(R.string.welcome_loop_note)))
        content.addView(stepButton(R.string.welcome_next))
        content.addView(progressRow())
    }

    /** Screen three: the same two switches Home will keep asking about. */
    private fun buildPermissions() {
        content.addView(
            welcomeChip(
                getString(R.string.welcome_step_badge, step + 1, LAST_STEP + 1),
                ui.palette.fieldPeach
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
        content.addView(ui.h1(getString(R.string.welcome_permissions_heading)))
        content.addView(
            permissionBlock(
                label = getString(R.string.welcome_usage_label),
                why = getString(R.string.welcome_usage_why),
                granted = PermissionChecks.hasUsageAccess(this),
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                action = getString(R.string.permissions_usage_action)
            )
        )
        content.addView(
            permissionBlock(
                label = getString(R.string.welcome_accessibility_label),
                why = getString(R.string.permissions_accessibility_why),
                granted = PermissionChecks.isAccessibilityServiceEnabled(this),
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                action = getString(R.string.permissions_accessibility_action)
            )
        )
        content.addView(
            fullWidth(ui.primary(getString(R.string.welcome_start))).apply {
                setOnClickListener { complete() }
            }
        )
        content.addView(
            fullWidth(ui.quiet(getString(R.string.welcome_skip))).apply {
                setOnClickListener { complete() }
            }
        )
        content.addView(progressRow())
    }

    /**
     * One switch: its name with a status word, why the switch exists, and the
     * button that opens the right Settings page. A granted switch keeps its
     * button, disabled - the row still explains itself after the fact instead
     * of disappearing.
     */
    private fun permissionBlock(
        label: String,
        why: String,
        granted: Boolean,
        settingsAction: String,
        action: String
    ): View = ui.column().apply {
        background = GradientDrawable().apply {
            cornerRadius = ui.dimens.radiusCard.toFloat()
            setColor(ui.palette.tile)
        }
        setPadding(ui.dimens.dp(20), ui.dimens.dp(20), ui.dimens.dp(20), ui.dimens.dp(20))
        addView(
            ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.weighted(ui.lead(label)))
                addView(
                    TextView(this@WelcomeActivity).apply {
                        text = getString(
                            if (granted) R.string.permissions_granted
                            else R.string.permissions_not_granted
                        )
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = ResourcesCompat.getFont(this@WelcomeActivity, R.font.space_grotesk_bold)
                        includeFontPadding = false
                        setTextColor(ui.palette.ink)
                        background = GradientDrawable().apply {
                            cornerRadius = ui.dimens.radiusPill.toFloat()
                            setColor(if (granted) ui.palette.flatMint else ui.palette.soft)
                        }
                        setPadding(ui.dimens.dp(12), ui.dimens.dp(5), ui.dimens.dp(12), ui.dimens.dp(5))
                    }
                )
            }
        )
        addView(ui.vgap(ui.dimens.groupGap))
        addView(ui.note(why))
        addView(ui.vgap(ui.dimens.groupGap))
        addView(
            fullWidth(ui.secondary(action)).apply {
                isEnabled = !granted
                if (!granted) setOnClickListener { startActivity(Intent(settingsAction)) }
            }
        )
    }

    private fun stepButton(resId: Int): View =
        fullWidth(ui.primary(getString(resId))).apply {
            setOnClickListener {
                step += 1
                renderStep()
            }
        }

    private fun <T : View> fullWidth(button: T): T = button.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    /** Three squares, one per screen; the one you are on is ink. */
    private fun progressRow(onDeep: Int? = null): View = ui.row().apply {
        gravity = Gravity.CENTER
        for (index in 0..LAST_STEP) {
            if (index > 0) addView(ui.hgap(ui.dimens.s))
            addView(
                View(this@WelcomeActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ui.dimens.stepSize,
                        ui.dimens.stepSize
                    )
                    setBackgroundColor(
                        when {
                            index == step && onDeep != null -> onDeep
                            index == step -> ui.palette.ink
                            onDeep != null -> ColorUtils.setAlphaComponent(onDeep, 60)
                            else -> ui.palette.inkHairline
                        }
                    )
                }
            )
        }
    }

    private fun complete() {
        WelcomeGate.markDone(this)
        finish()
    }
}
