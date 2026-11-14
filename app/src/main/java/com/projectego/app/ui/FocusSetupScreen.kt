package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.addTextChangedListener
import com.projectego.app.R
import com.projectego.app.model.FocusActivityType

/**
 * FOKUS as the approved render draws it: one lilac card holding the ring and the
 * chosen time, three tiles that name the session before it starts, the goal, the
 * four lengths the engine accepts, and the one action.
 *
 * The ring is the whole card's picture: the cream track is the hour, the ink arc
 * is the share of it this session takes. The ring is also the only place the
 * time is written, so a length and its picture can never disagree.
 */
object FocusSetupScreen {

    /** The four lengths the approved setup offers; the engine accepts all of them. */
    val DURATIONS = listOf(15L, 25L, 45L, 60L)

    private const val RING_DP = 210
    private const val RING_STROKE_DP = 14
    private const val TILE_PADDING_DP = 14
    private const val PILL_HEIGHT_DP = 48

    data class Data(
        val durationMinutes: Long,
        val durations: List<Long>,
        val activity: FocusActivityType,
        val activities: List<FocusActivityType>,
        val allowedApps: (FocusActivityType) -> Int,
        val goal: String,
        val onGoal: (String) -> Unit,
        val onDuration: (Long) -> Unit,
        val onActivity: (FocusActivityType) -> Unit,
        val onStart: () -> Unit
    )

    /**
     * The card's ring. [fraction] is the share of an hour the session takes, so
     * the picture answers "how big a block is this" the moment a length changes.
     */
    class Ring(context: Context, ui: AliveComponents) : View(context) {

        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ui.dimens.dp(RING_STROKE_DP).toFloat()
            color = ui.palette.soft
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ui.dimens.dp(RING_STROKE_DP).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = ui.palette.ink
        }
        private val shape = RectF()

        var fraction: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        init {
            layoutParams = LinearLayout.LayoutParams(
                ui.dimens.dp(RING_DP),
                ui.dimens.dp(RING_DP)
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val inset = track.strokeWidth / 2f
            val size = minOf(width, height).toFloat()
            val centreX = width / 2f
            val centreY = height / 2f
            canvas.drawCircle(centreX, centreY, (size - 2f * inset) / 2f, track)
            if (fraction <= 0f) return
            shape.set(
                centreX - size / 2f + inset,
                centreY - size / 2f + inset,
                centreX + size / 2f - inset,
                centreY + size / 2f - inset
            )
            canvas.drawArc(shape, -90f, 360f * fraction, false, arc)
        }
    }

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val column = ui.column()

        val ring = Ring(context, ui).apply {
            fraction = data.durationMinutes.toFloat() / 60f
        }
        val time = ui.timer(
            context.getString(R.string.focus_ring_time, data.durationMinutes)
        ).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        }

        column.addView(
            FrameLayout(context).apply {
                background = field(ui.palette.fieldLilac, ui.dimens.radiusCard)
                setPadding(ui.dimens.dp(20), ui.dimens.dp(24), ui.dimens.dp(20), ui.dimens.dp(24))
                addView(
                    ring,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
                addView(
                    ui.column().apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(time)
                        addView(ui.kicker(context.getString(R.string.unit_minutes_suffix)))
                    },
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
        )

        column.addView(ui.vgap(ui.dimens.dp(16)))

        // The three tiles: what you are doing, what stays open, and the one
        // promise that does not bend. The first tile is the picker.
        val activityValue = ui.lead(activityLabel(context, data.activity))
        val allowedValue = ui.lead(
            context.getString(R.string.focus_allowed_count, data.allowedApps(data.activity))
        )

        val activities = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            visibility = View.GONE
            setPadding(0, ui.dimens.pairGap, 0, 0)
        }
        data.activities.forEachIndexed { index, type ->
            activities.addView(
                ui.tileOption(
                    context.getString(
                        R.string.focus_activity_option,
                        activityLabel(context, type),
                        data.allowedApps(type)
                    ),
                    PixelMarks.focus(type),
                    ContextCompat.getColor(context, QuestHues.focus(type))
                ).apply {
                    id = 100 + index
                    isChecked = type == data.activity
                    // Picking the activity that is already picked changes no
                    // radio, so the group's listener would never hear it. The
                    // answer still has to fold the list.
                    setOnClickListener { activities.visibility = View.GONE }
                    layoutParams = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = ui.dimens.pairGap
                    }
                }
            )
        }

        val tiles = ui.row()
        tiles.addView(
            tile(
                context, ui,
                kicker = context.getString(R.string.focus_kicker_activity),
                value = activityValue,
                face = ui.palette.flatMint
            ).apply {
                isClickable = true
                setOnClickListener {
                    activities.visibility =
                        if (activities.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        )
        tiles.addView(ui.hgap(ui.dimens.dp(10)))
        tiles.addView(
            tile(
                context, ui,
                kicker = context.getString(R.string.focus_kicker_allowed),
                value = allowedValue,
                face = ui.palette.fieldSky
            )
        )
        tiles.addView(ui.hgap(ui.dimens.dp(10)))
        tiles.addView(
            tile(
                context, ui,
                kicker = context.getString(R.string.focus_kicker_message),
                value = ui.lead(context.getString(R.string.focus_message_value)),
                face = ui.palette.fieldButter
            )
        )
        column.addView(tiles)
        column.addView(activities)

        activities.setOnCheckedChangeListener { _, checkedId ->
            val type = data.activities.getOrNull(checkedId - 100) ?: return@setOnCheckedChangeListener
            activityValue.text = activityLabel(context, type)
            allowedValue.text =
                context.getString(R.string.focus_allowed_count, data.allowedApps(type))
            activities.visibility = View.GONE
            data.onActivity(type)
        }

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(ui.kicker(context.getString(R.string.focus_goal_label)))
        column.addView(ui.vgap(ui.dimens.lineGap))

        val goal = ui.input(context.getString(R.string.focus_goal_hint)).apply {
            setText(data.goal)
            addTextChangedListener(afterTextChanged = { text ->
                data.onGoal(text?.toString()?.trim().orEmpty())
            })
        }
        column.addView(goal)

        column.addView(ui.vgap(ui.dimens.dp(16)))

        val pills = data.durations.map { minutes ->
            pill(context, ui, minutes, minutes == data.durationMinutes)
        }
        val pillRow = ui.row()
        pills.forEachIndexed { index, pill ->
            pillRow.addView(
                pill.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ui.dimens.dp(PILL_HEIGHT_DP), 1f
                    ).apply {
                        if (index > 0) marginStart = ui.dimens.dp(10)
                    }
                }
            )
        }
        column.addView(pillRow)

        pills.zip(data.durations).forEach { (pill, minutes) ->
            pill.setOnClickListener {
                pillRow.children().forEach { other ->
                    paintPill(other as TextView, ui, (other.tag as Long) == minutes)
                }
                time.text = context.getString(R.string.focus_ring_time, minutes)
                ring.fraction = minutes.toFloat() / 60f
                data.onDuration(minutes)
            }
        }

        column.addView(ui.vgap(ui.dimens.dp(16)))
        column.addView(
            ui.primary(context.getString(R.string.focus_start)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { data.onStart() }
            }
        )

        return column
    }

    private fun tile(
        context: Context,
        ui: AliveComponents,
        kicker: String,
        value: TextView,
        face: Int
    ): LinearLayout = ui.column().apply {
        background = field(face, ui.dimens.radiusTile)
        setPadding(
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP),
            ui.dimens.dp(TILE_PADDING_DP)
        )
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(ui.kicker(kicker))
        addView(ui.vgap(ui.dimens.xs))
        addView(value)
    }

    private fun pill(
        context: Context,
        ui: AliveComponents,
        minutes: Long,
        chosen: Boolean
    ): TextView = ui.lead(context.getString(R.string.focus_duration_value, minutes)).apply {
        tag = minutes
        gravity = Gravity.CENTER
        isClickable = true
        paintPill(this, ui, chosen)
    }

    private fun paintPill(pill: TextView, ui: AliveComponents, chosen: Boolean) {
        pill.background = field(if (chosen) ui.palette.ink else ui.palette.tile, ui.dimens.dp(999))
        pill.setTextColor(
            if (chosen) ui.palette.paper else ColorUtils.setAlphaComponent(ui.palette.ink, 0x99)
        )
    }

    private fun activityLabel(context: Context, type: FocusActivityType): String =
        context.getString(AliveStates.focusActivityLabel(type))

    private fun field(face: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        setColor(face)
        cornerRadius = radiusPx.toFloat()
    }

    private fun LinearLayout.children(): List<View> =
        (0 until childCount).map { getChildAt(it) }
}
