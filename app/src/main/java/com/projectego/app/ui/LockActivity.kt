package com.projectego.app.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.domain.LockSnapshot
import com.projectego.app.model.DayType

/**
 * ALIVE's lock screen.
 *
 * It says three things and nothing else: entertainment time is over, that is on
 * purpose, and there are other things to do. Deliberately not red, not shouting,
 * and never worded as an accusation or as catching anyone out.
 *
 * The back interception and the intent-extra contract with the accessibility
 * service are bypass-prevention, not styling. The extra keys stay literal on
 * both sides: the writer lives in `service/`, and a shared constant would only
 * create a second place for the two ends to drift apart.
 */
class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intercept back button - prevent returning to blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        })

        render()
    }

    /**
     * The lock screen is singleTask, so a second lock does not reach onCreate: it
     * arrives here, on the instance already showing, and without this the screen
     * kept printing the first app's name over the second app. A lock that names
     * somewhere the person already left is a lock that describes the wrong thing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val ui = AliveComponents(this)

        // The guard computed the day's report to decide this lock and handed over
        // the two figures this screen prints; the rest is prefs. Asking UsageStats
        // again inside onCreate cost 353-785 ms of this screen's startup on the
        // phone, for numbers that had just been computed one frame earlier.
        val lockState = LockStateManager(this)
        val numbers = LockSnapshot.of(
            usedMinutes = intent.getLongExtra("EXTRA_USED_MINUTES", LockSnapshot.MISSING),
            limitMinutes = intent.getLongExtra("EXTRA_LIMIT_MINUTES", LockSnapshot.MISSING)
        )

        val lockReason = intent.getStringExtra("EXTRA_LOCK_REASON") ?: "ENTERTAINMENT_EXCEEDED"
        val blockedPkg = intent.getStringExtra("EXTRA_BLOCKED_PACKAGE")
            ?: getString(R.string.lock_blocked_app_unknown)
        val focusActivityName = intent.getStringExtra("EXTRA_FOCUS_ACTIVITY") ?: getString(R.string.focus_heading)

        val isFocusSession = lockReason == "FOCUS_SESSION"
        val isRecoveryDay = lockState.getDayType() == DayType.ZERO_ENTERTAINMENT_DAY

        // Pass 2 stands the lock on deep, with the two facts that explain it as
        // fields: today's allowance and the run it belongs to. No red, no
        // shouting - the door says what happened and offers the way out.
        val paper = ContextCompat.getColor(this, R.color.flat_paper)
        val deep = ContextCompat.getColor(this, R.color.flat_deep)

        val page = ui.stack(ui.dimens.cardGap).apply {
            setPadding(
                ui.dimens.pageHorizontal,
                ui.dimens.pageTop,
                ui.dimens.pageHorizontal,
                ui.dimens.pageBottom
            )
        }

        page.addView(
            TextView(this).apply {
                text = getString(R.string.alive_wordmark)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = ResourcesCompat.getFont(this@LockActivity, R.font.space_grotesk_bold)
                setTextColor(paper)
                includeFontPadding = false
            }
        )

        page.addView(
            ui.h1(
                getString(
                    when {
                        isFocusSession -> R.string.lock_title_focus
                        isRecoveryDay -> R.string.lock_title_zero
                        else -> R.string.lock_title_over
                    }
                )
            ).apply { setTextColor(paper) }
        )
        page.addView(
            ui.note(getString(R.string.lock_intent)).apply {
                setTextColor(ColorUtils.setAlphaComponent(paper, 168))
            }
        )

        val jatah = ui.column().apply {
            background = GradientDrawable().apply {
                cornerRadius = ui.dimens.radiusCard.toFloat()
                setColor(ContextCompat.getColor(this@LockActivity, R.color.flat_field_rose))
            }
            setPadding(ui.dimens.dp(20), ui.dimens.dp(20), ui.dimens.dp(20), ui.dimens.dp(20))
        }
        jatah.addView(ui.kicker(getString(R.string.lock_kicker_allowance)))
        jatah.addView(ui.vgap(ui.dimens.pairGap))
        val used = numbers.usedMinutes
        val limit = numbers.limitMinutes
        jatah.addView(
            if (used != null && limit != null) {
                ui.number(getString(R.string.lock_used_of_limit, used, limit))
            } else {
                ui.lead(
                    getString(if (isRecoveryDay) R.string.lock_zero_body else R.string.lock_over_body)
                )
            }
        )
        jatah.addView(ui.vgap(ui.dimens.lineGap))
        jatah.addView(
            TextView(this).apply {
                text = blockedPkg
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = ResourcesCompat.getFont(this@LockActivity, R.font.space_grotesk_bold)
                setTextColor(ui.palette.ink)
                includeFontPadding = false
                background = GradientDrawable().apply {
                    cornerRadius = ui.dimens.radiusPill.toFloat()
                    setColor(paper)
                }
                setPadding(ui.dimens.dp(12), ui.dimens.dp(6), ui.dimens.dp(12), ui.dimens.dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )
        page.addView(jatah)

        page.addView(
            ui.row().apply {
                background = GradientDrawable().apply {
                    cornerRadius = ui.dimens.radiusCard.toFloat()
                    setColor(ContextCompat.getColor(this@LockActivity, R.color.flat_field_butter))
                }
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dimens.dp(20), ui.dimens.dp(18), ui.dimens.dp(20), ui.dimens.dp(18))
                addView(ui.kicker(getString(R.string.lock_kicker_streak)))
                addView(ui.weighted(ui.column()))
                addView(
                    ui.lead(
                        getString(R.string.lock_streak_days, lockState.getCurrentStreak())
                    )
                )
            }
        )

        if (!isFocusSession) {
            page.addView(
                ui.primary(getString(R.string.lock_action_focus)).apply {
                    layoutParams = matchWidth()
                    setOnClickListener {
                        val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("EXTRA_NAVIGATE_TO_FOCUS", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            )
        }

        page.addView(
            ui.secondary(getString(R.string.lock_action_home)).apply {
                layoutParams = matchWidth()
                setOnClickListener {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
            }
        )

        page.addView(
            ui.quiet(getString(R.string.lock_action_dashboard)).apply {
                setTextColor(paper)
                layoutParams = matchWidth()
                setOnClickListener {
                    val intent = Intent(this@LockActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                    finish()
                }
            }
        )

        page.addView(
            ui.note(getString(R.string.lock_essential)).apply {
                setTextColor(ColorUtils.setAlphaComponent(paper, 168))
            }
        )

        // A scroll container is required: on a short display the buttons
        // otherwise fall below the fold while back is still intercepted.
        setContentView(
            ScrollView(this).apply {
                addView(page)
                setBackgroundColor(deep)
                isFillViewport = true
            }
        )
    }

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
