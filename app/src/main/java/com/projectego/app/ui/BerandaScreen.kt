package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.R
import com.projectego.app.domain.EntertainmentClaimPolicy
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Beranda as the approved render draws it.
 *
 * Six blocks, in the render's order: the greeting with companion presence, the
 * day's board with animated meter and optional Time Bank shortcut, three facts,
 * the one action, today's focus, and the unified streak card carrying the week's
 * seven faces.
 */
object BerandaScreen {

    data class Data(
        val greeting: String,
        val subLine: String,
        val remainingMinutes: Long,
        val limitMinutes: Long,
        val usedMinutes: Long,
        val missionsDone: Int,
        val missionsTotal: Int,
        val focusMinutes: Long,
        val streak: Int,
        val tomorrowSafe: Boolean,
        val closed: Boolean,
        val closedWord: String,
        val critical: Boolean,
        val focusTitle: String,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
        val onGrowth: () -> Unit,
        val onFocus: () -> Unit,
        val onOpenFocus: (() -> Unit)? = null,
        val onGreetingTap: (() -> Unit)? = null,
        @param:DrawableRes val characterDrawableRes: Int = R.drawable.alive_member_creative,
        val bankMinutes: Long = 0L,
        val onTimeBank: (() -> Unit)? = null
    )

    private const val TILE_PADDING_DP = 16
    private const val BOARD_PADDING_DP = 20
    private const val FIELD_GAP_DP = 16

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val column = ui.column()

        column.addView(header(context, ui, data))

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(board(context, ui, data))

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(facts(context, ui, data))

        if (data.actionLabel != null && data.onAction != null) {
            val actionText = if (data.actionLabel.endsWith("→")) {
                data.actionLabel
            } else {
                "${data.actionLabel} →"
            }
            column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
            column.addView(
                ui.primary(actionText).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener { data.onAction.invoke() }
                }
            )
        }

        column.addView(ui.vgap(ui.dimens.dp(FIELD_GAP_DP)))
        column.addView(focusCard(context, ui, data))

        column.addView(ui.vgap(ui.dimens.cardGap))
        column.addView(streakCard(context, ui, data))

        return column
    }

    // ---------- header with companion ----------

    private fun header(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(
                        ui.h1(data.greeting).apply {
                            data.onGreetingTap?.let { handler -> setOnClickListener { handler() } }
                        }
                    )
                    addView(ui.vgap(ui.dimens.lineGap))
                    addView(ui.note(data.subLine))
                }
            )
            addView(
                ImageView(context).apply {
                    setImageResource(data.characterDrawableRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { data.onGrowth() }
                    AliveMotion.attachPressSpring(this, pressedScale = 0.94f)
                    AliveMotion.startBreathing(this, maxScale = 1.045f, periodMs = 3400L)
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(50), ui.dimens.dp(50)).apply {
                        marginStart = ui.dimens.dp(14)
                    }
                }
            )
        }

    // ---------- the day's board ----------

    private fun board(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val face = if (data.critical && !data.closed) ui.palette.shortPale else ui.palette.flatMint
        val board = ui.column().apply {
            background = field(face, ui.dimens.radiusCard)
            setPadding(
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP),
                ui.dimens.dp(BOARD_PADDING_DP)
            )
        }

        val topArea = ui.row().apply { gravity = Gravity.TOP }
        val statusCol = ui.column().apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(ui.kicker(context.getString(R.string.home_remaining_label)))
            addView(ui.vgap(ui.dimens.pairGap))

            val line = ui.row().apply { gravity = Gravity.BOTTOM }
            if (data.closed) {
                line.addView(
                    ui.scoreStatus(data.closedWord).apply {
                        isAllCaps = true
                    }
                )
                line.addView(
                    ui.meta(context.getString(R.string.beranda_closed_until_tomorrow)).apply {
                        setPadding(ui.dimens.dp(10), 0, 0, ui.dimens.dp(5))
                        maxLines = 1
                    }
                )
            } else {
                line.addView(ui.timer(context.getString(R.string.stat_minutes, data.remainingMinutes)))
                line.addView(
                    ui.column().apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(ui.dimens.s, 0, 0, ui.dimens.xs)
                        addView(ui.meta(context.getString(R.string.unit_minutes_suffix)))
                        addView(ui.meta(context.getString(R.string.beranda_of_board, data.limitMinutes)))
                    }
                )
            }
            addView(line)
        }
        topArea.addView(statusCol)
        topArea.addView(
            hourglass(context, ui).apply {
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(30), ui.dimens.dp(30)).apply {
                    marginStart = ui.dimens.dp(12)
                }
            }
        )
        board.addView(topArea)

        board.addView(ui.vgap(ui.dimens.groupGap))

        val meterFraction = when {
            data.closed && data.bankMinutes > 0L ->
                (data.bankMinutes.toFloat() / EntertainmentClaimPolicy.DAILY_CAP_MINUTES.toFloat()).coerceIn(0.22f, 1f)
            data.closed -> 0.24f
            data.limitMinutes > 0L -> data.remainingMinutes.toFloat() / data.limitMinutes.toFloat()
            else -> 0f
        }
        val shimmerAccent = when {
            data.critical && !data.closed -> ui.palette.fieldSun
            data.closed -> ui.palette.fieldAqua
            else -> ui.palette.flatMint
        }

        val barRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
        barRow.addView(
            MeterBar(context, ui, fraction = meterFraction, accentColor = shimmerAccent).apply {
                layoutParams = LinearLayout.LayoutParams(0, ui.dimens.dp(12), 1f)
            }
        )
        if (data.bankMinutes > 0L) {
            barRow.addView(bankChip(context, ui, data))
        }
        board.addView(barRow)
        return board
    }

    private fun bankChip(context: Context, ui: AliveComponents, data: Data): TextView =
        TextView(context).apply {
            text = context.getString(R.string.focus_extend_bank_short, data.bankMinutes)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ui.palette.ink)
            includeFontPadding = false
            maxLines = 1
            background = field(ui.palette.tile, ui.dimens.radiusPill)
            setPadding(ui.dimens.dp(12), ui.dimens.dp(6), ui.dimens.dp(12), ui.dimens.dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = ui.dimens.dp(10)
            }
            data.onTimeBank?.let { openBank ->
                isClickable = true
                isFocusable = true
                AliveMotion.attachPressSpring(this, pressedScale = 0.95f)
                setOnClickListener { openBank() }
            }
        }

    private fun hourglass(context: Context, ui: AliveComponents): ImageView =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_hourglass_flat)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(30), ui.dimens.dp(30))
        }

    // ---------- the three facts ----------

    private fun facts(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.equalRow().apply {
            addView(
                fact(
                    context, ui, R.string.stat_used, data.usedMinutes,
                    context.getString(R.string.unit_minutes_short), ui.palette.fieldBlossom
                )
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                fact(
                    context, ui, R.string.beranda_missions,
                    data.missionsDone.toLong(),
                    context.getString(R.string.beranda_of_board, data.missionsTotal),
                    ui.palette.fieldButter
                )
            )
            addView(ui.hgap(ui.dimens.dp(10)))
            addView(
                fact(
                    context, ui, R.string.beranda_focus, data.focusMinutes,
                    context.getString(R.string.unit_minutes_short), ui.palette.fieldAqua
                ).apply {
                    data.onOpenFocus?.let { openFocus ->
                        isClickable = true
                        isFocusable = true
                        AliveMotion.attachPressSpring(this, pressedScale = 0.97f)
                        setOnClickListener { openFocus() }
                    }
                }
            )
        }

    private fun fact(
        context: Context,
        ui: AliveComponents,
        label: Int,
        value: Long,
        unit: String,
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
        addView(ui.kicker(context.getString(label)))
        addView(ui.vgap(ui.dimens.pairGap))
        val line = ui.row().apply { gravity = Gravity.BOTTOM }
        line.addView(ui.number(context.getString(R.string.stat_minutes, value)))
        line.addView(
            ui.meta(unit).apply {
                setPadding(ui.dimens.xs, 0, 0, ui.dimens.xs)
                maxLines = 1
            }
        )
        addView(line)
    }

    // ---------- today's focus ----------

    private fun focusCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.row().apply {
            background = field(ui.palette.tile, ui.dimens.radiusCard)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dimens.dp(BOARD_PADDING_DP), ui.dimens.dp(18), ui.dimens.dp(18), ui.dimens.dp(18))
            isClickable = true
            AliveMotion.attachPressSpring(this, pressedScale = 0.98f)
            setOnClickListener { data.onFocus() }

            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_line_sprout)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    setPadding(ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10), ui.dimens.dp(10))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ui.palette.fieldSky)
                    }
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
                }
            )
            addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(ui.dimens.groupGap, 0, ui.dimens.groupGap, 0)
                    addView(ui.kicker(context.getString(R.string.beranda_focus_today)))
                    addView(ui.vgap(ui.dimens.xs))
                    addView(ui.lead(data.focusTitle))
                }
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_smiley_flat)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                    setPadding(ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ui.palette.flatMint)
                    }
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(40), ui.dimens.dp(40))
                }
            )
        }

    // ---------- unified streak & week card ----------

    private fun streakCard(context: Context, ui: AliveComponents, data: Data): LinearLayout =
        ui.column().apply {
            background = field(ui.palette.fieldButter, ui.dimens.radiusCard)
            setPadding(ui.dimens.dp(18), ui.dimens.dp(18), ui.dimens.dp(18), ui.dimens.dp(18))

            addView(
                ui.row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { data.onGrowth() }
                    addView(
                        ImageView(context).apply {
                            setImageResource(
                                if (data.streak > 0) R.drawable.ic_flame_flat else R.drawable.ic_face_neutral
                            )
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = null
                            setPadding(ui.dimens.dp(5), ui.dimens.dp(5), ui.dimens.dp(5), ui.dimens.dp(5))
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(ColorUtils.setAlphaComponent(ui.palette.tile, 195))
                            }
                            layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(28), ui.dimens.dp(28)).apply {
                                marginEnd = ui.dimens.dp(10)
                            }
                        }
                    )
                    addView(
                        ui.kicker(
                            context.getString(R.string.beranda_streak_days, data.streak)
                        )
                    )
                    addView(spacer(context))
                    addView(
                        ui.lead(
                            context.getString(
                                if (data.tomorrowSafe) R.string.beranda_tomorrow_safe
                                else R.string.beranda_tomorrow_open
                            )
                        ).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            isAllCaps = false
                            alpha = if (data.tomorrowSafe) 1f else 0.58f
                        }
                    )
                }
            )

            addView(ui.vgap(ui.dimens.dp(14)))
            addView(
                DayFaces.row(
                    context = context,
                    ui = ui,
                    streak = data.streak,
                    spreadEvenly = true,
                    unlivedFill = ColorUtils.setAlphaComponent(ui.palette.tile, 138),
                    unlivedStroke = ColorUtils.setAlphaComponent(ui.palette.ink, 88),
                    unlivedStrokeWidthDp = 1.25f,
                    ringColorOverrides = mapOf(ui.palette.fieldButter to ui.palette.fieldSun)
                ).apply {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { data.onGrowth() }
                }
            )
        }

    // ---------- pieces ----------

    private fun spacer(context: Context): View = View(context)
        .apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

    private fun field(face: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
        setColor(face)
        cornerRadius = radiusPx.toFloat()
    }

    /**
     * Liquid meter bar inside the hero board: translucent frosted track with an
     * ink capsule carrying a traveling AGSL liquid sheen (and deterministic Canvas
     * LinearGradient fallback) plus a gentle breathing meniscus gleam at the active tip.
     */
    internal class MeterBar(
        context: Context,
        private val ui: AliveComponents,
        fraction: Float,
        @param:ColorInt private val accentColor: Int
    ) : View(context) {

        private val share = fraction.coerceIn(0f, 1f)
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.palette.tile
            alpha = 152
        }
        private val trackBaseAlpha = trackPaint.alpha

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.palette.ink
        }
        private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 140
        }
        private val gleamBaseAlpha = gleamPaint.alpha

        private val shape = RectF()
        private val fillShape = RectF()
        private val revealEase = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
        private val linear = LinearInterpolator()
        private val gradientMatrix = Matrix()
        private val tipMatrix = Matrix()

        private var cachedBandSpan = 0f
        private var cachedShaderHeight = 0f
        private var fallbackShader: LinearGradient? = null
        private var tipGlowShader: RadialGradient? = null
        private var agslShader: Any? = null // android.graphics.RuntimeShader on API 33+
        private var shaderInitAttempted = false

        private var revealAnimator: ValueAnimator? = null
        private var shimmerAnimator: ValueAnimator? = null

        var displayFraction: Float = share
            private set

        private var shimmerPhase: Float = 0.25f

        init {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dimens.dp(12)
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredW = ui.dimens.dp(160)
            val desiredH = ui.dimens.dp(12)
            setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
            )
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            startEffects()
        }

        override fun onDetachedFromWindow() {
            cancelEffects()
            displayFraction = share
            shimmerPhase = 0.25f
            super.onDetachedFromWindow()
        }

        private fun startEffects() {
            cancelEffects()
            if (!AliveMotion.shouldAnimate(this) || share <= 0f) {
                displayFraction = share
                invalidate()
                return
            }
            displayFraction = 0f
            revealAnimator = ValueAnimator.ofFloat(0f, share).apply {
                startDelay = 60L
                duration = 520L
                interpolator = revealEase
                addUpdateListener { anim ->
                    displayFraction = anim.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        displayFraction = share
                        invalidate()
                    }
                })
                start()
            }
            shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = linear
                addUpdateListener { anim ->
                    shimmerPhase = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun cancelEffects() {
            revealAnimator?.cancel()
            revealAnimator = null
            shimmerAnimator?.cancel()
            shimmerAnimator = null
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val h = height.toFloat()
            val radius = h / 2f

            trackPaint.alpha = trackBaseAlpha
            shape.set(0f, 0f, width.toFloat(), h)
            canvas.drawRoundRect(shape, radius, radius, trackPaint)

            if (displayFraction <= 0.002f) return
            val fillWidth = (width * displayFraction).coerceIn(minOf(width.toFloat(), h), width.toFloat())
            fillShape.set(0f, 0f, fillWidth, h)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
                val shader = getOrInitAgslShader()
                if (shader != null && drawFillAgsl(canvas, shader, fillWidth, h, radius)) {
                    return
                }
            }
            drawFillFallback(canvas, fillWidth, h, radius)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun getOrInitAgslShader(): RuntimeShader? {
            if (!shaderInitAttempted) {
                shaderInitAttempted = true
                agslShader = try {
                    RuntimeShader(LIQUID_METER_AGSL)
                } catch (_: Throwable) {
                    null
                }
            }
            return agslShader as? RuntimeShader
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun drawFillAgsl(
            canvas: Canvas,
            shader: RuntimeShader,
            fillWidth: Float,
            heightPx: Float,
            radius: Float
        ): Boolean = try {
            shader.setFloatUniform("uSize", fillWidth, heightPx)
            shader.setFloatUniform("uPhase", shimmerPhase)
            shader.setFloatUniform(
                "uAccent",
                Color.red(accentColor) / 255f,
                Color.green(accentColor) / 255f,
                Color.blue(accentColor) / 255f
            )
            shader.setFloatUniform("uAccentStrength", 1.0f)
            shaderPaint.shader = shader
            canvas.drawRoundRect(fillShape, radius, radius, shaderPaint)
            true
        } catch (_: Throwable) {
            false
        }

        private fun drawFillFallback(canvas: Canvas, fillWidth: Float, heightPx: Float, radius: Float) {
            val bandSpan = maxOf(heightPx * 4.0f, ui.dimens.dp(76).toFloat())
            if (fallbackShader == null || tipGlowShader == null ||
                cachedBandSpan != bandSpan || cachedShaderHeight != heightPx
            ) {
                cachedBandSpan = bandSpan
                cachedShaderHeight = heightPx
                val deepTint = ColorUtils.blendARGB(ui.palette.ink, accentColor, 0.22f)
                val softGlaze = ColorUtils.blendARGB(ui.palette.ink, accentColor, 0.56f)
                val pearlCore = ColorUtils.blendARGB(accentColor, Color.WHITE, 0.42f)
                fallbackShader = LinearGradient(
                    0f,
                    0f,
                    bandSpan,
                    heightPx * 0.45f,
                    intArrayOf(
                        ui.palette.ink,
                        deepTint,
                        softGlaze,
                        pearlCore,
                        softGlaze,
                        deepTint,
                        ui.palette.ink
                    ),
                    floatArrayOf(0f, 0.24f, 0.39f, 0.5f, 0.61f, 0.76f, 1f),
                    Shader.TileMode.CLAMP
                )
                val haloColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(accentColor, Color.WHITE, 0.45f),
                    175
                )
                val haloEdge = ColorUtils.setAlphaComponent(accentColor, 0)
                tipGlowShader = RadialGradient(
                    0f,
                    0f,
                    heightPx * 0.85f,
                    intArrayOf(haloColor, haloEdge),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            val sweepCenter = (-0.28f + 1.60f * shimmerPhase) * fillWidth
            gradientMatrix.setTranslate(sweepCenter - bandSpan * 0.5f, 0f)
            fallbackShader?.setLocalMatrix(gradientMatrix)
            fillPaint.shader = fallbackShader
            canvas.drawRoundRect(fillShape, radius, radius, fillPaint)
            fillPaint.shader = null

            val tipPulse = 0.55f + 0.45f * sin(shimmerPhase * 2.0 * PI).toFloat()
            val tipCx = (fillWidth - radius * 0.75f).coerceAtLeast(radius)
            tipMatrix.setTranslate(tipCx, radius)
            tipGlowShader?.setLocalMatrix(tipMatrix)
            gleamPaint.shader = tipGlowShader
            gleamPaint.alpha = (gleamBaseAlpha * tipPulse).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(fillShape, radius, radius, gleamPaint)
            gleamPaint.shader = null
            gleamPaint.alpha = gleamBaseAlpha
        }

        companion object {
            private const val LIQUID_METER_AGSL = """
                uniform float2 uSize;
                uniform float uPhase;
                uniform float3 uAccent;
                uniform float uAccentStrength;

                half4 main(float2 fragCoord) {
                    float w = max(uSize.x, 1.0);
                    float h = max(uSize.y, 1.0);
                    float u = clamp(fragCoord.x / w, 0.0, 1.0);
                    float v = clamp(fragCoord.y / h, 0.0, 1.0);

                    float3 ink = float3(0.078, 0.078, 0.078);
                    float edgeRamp = smoothstep(0.35, 1.0, u);
                    float3 base = mix(ink, mix(ink, uAccent, 0.22), edgeRamp * uAccentStrength);

                    float sweepCenter = mix(-0.28, 1.32, uPhase);
                    float diag = u - (v - 0.5) * (h / w) * 0.85;
                    float bandDist = abs(diag - sweepCenter);
                    float shimmerBand = exp(-bandDist * bandDist * 96.0);
                    float fineCore = exp(-bandDist * bandDist * 320.0);

                    float tipDist = max(w - fragCoord.x, 0.0) / h;
                    float tipPulse = 0.55 + 0.45 * sin(uPhase * 6.2831853);
                    float meniscus = smoothstep(1.3, 0.0, tipDist) * 0.34 * tipPulse;

                    float3 col = base;
                    col = mix(col, uAccent, clamp(shimmerBand * 0.52 + meniscus, 0.0, 0.74));
                    col += float3(1.0) * (fineCore * 0.30 + smoothstep(0.55, 0.0, tipDist) * 0.14 * tipPulse);

                    return half4(half3(clamp(col, 0.0, 1.0)), half(1.0));
                }
            """
        }
    }
}

