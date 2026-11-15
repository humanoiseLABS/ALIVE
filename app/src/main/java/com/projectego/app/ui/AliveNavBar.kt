package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat
import com.projectego.app.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Floating Nimbus "Waterline" Liquid Glass dock for ALIVE's four destinations:
 * Home, Quest, Stats, and Profile.
 *
 * Keeps the child-view layout contract intact ([getChildAt](0) is the pill dock
 * [LinearLayout], its children 0..3 are the four tab cells) while drawing:
 * 1. Frosted translucent glass shell with soft water-blue ambient shadows and
 *    specular rim highlights in [onDraw].
 * 2. Asymmetric two-body spring + vertical squash (`sy`) + surface-tension waist
 *    thinning + transient splitting satellite droplets + tap ripples + pointer
 *    meniscus pull (via AGSL [RuntimeShader] on API 33+ hardware canvases and a
 *    high-craft geometric water Canvas path fallback on API 26-32 / software).
 * 3. Per-tab micro-animations (glowing doorway hop on Home, flag wave on Quest,
 *    liquid bounce on Stats, friendly wave on Profile).
 */
class AliveNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    object Index {
        const val HOME = 0
        const val QUEST = 1
        const val STATS = 2
        const val PROFILE = 3

        fun indexForTab(tab: Int): Int = when (tab) {
            0 -> HOME
            1 -> QUEST
            else -> PROFILE
        }
    }

    enum class Tone { LIGHT, DARK }

    /** Reports whichever tab was tapped, including re-taps on the current one. */
    var onSelect: ((Int) -> Unit)? = null

    var tone: Tone = Tone.LIGHT
        private set

    val activeIndex: Int
        get() = selected

    private val ui = AliveComponents(context)
    private val dimens = ui.dimens
    private val density = resources.displayMetrics.density

    private data class Slot(@param:StringRes val label: Int, @param:DrawableRes val glyph: Int)

    private val slots = listOf(
        Slot(R.string.nav_home, R.drawable.ic_dock_home),
        Slot(R.string.nav_quest, R.drawable.ic_dock_quest),
        Slot(R.string.nav_stats, R.drawable.ic_dock_stats),
        Slot(R.string.nav_profile, R.drawable.ic_dock_profile)
    )

    private val dock: LinearLayout
    private val cells = mutableListOf<LinearLayout>()
    private val glyphs = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var homeDoorDrawable: HomeDoorGlowDrawable? = null

    private var selected = -1

    // Layout measurements in AliveNavBar's own coordinate space
    private val pillPadPx = dpF(6f)
    private val bleedPadPx = dpF(14f)
    private val outerSideMarginPx = dpF(14f)
    private val outerTopMarginPx = dpF(8f)
    private val outerBottomMarginPx = dpF(12f)
    private val pillRect = RectF()
    private var geometryReady = false

    // Two-body fluid spring state (left/right endcap centers in AliveNavBar X coords)
    private var leftCapX = 0f
    private var leftCapV = 0f
    private var rightCapX = 0f
    private var rightCapV = 0f
    private var sy = 1f
    private var syV = 0f
    private var settledSpan = 0f
    private var dropHalfHeight = 0f
    private var settleRippleArmed = false
    private var ambientTailSeconds = 0f

    // Satellite droplets that split during long jumps and pull back into head
    private data class Droplet(
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var r: Float = 0f,
        var targetR: Float = 0f,
        var pullK: Float = 90f,
        var active: Boolean = false
    )

    private val droplets = Array(3) { Droplet() }

    // Expanding concentric ripples inside the pill
    private data class Ripple(
        var x: Float = 0f,
        var y: Float = 0f,
        var age: Float = 0f,
        var amp: Float = 0f,
        var active: Boolean = false
    )

    private val ripples = Array(3) { Ripple() }

    // Touch meniscus attraction
    private var touchX = 0f
    private var touchY = 0f
    private var touchDown = false
    private var mouseStrength = 0f

    // Continuous time for caustic shimmer
    private var timeSeconds = 0f
    private var lastFrameNanos = 0L
    private var frameLoopRunning = false

    // Active icon animators & duplicate-select debounce
    private val iconAnimators = mutableListOf<Animator>()
    private var doorAnimator: ValueAnimator? = null
    private var lastAnimatedIndex = -1
    private var lastAnimatedUptimeMs = 0L

    // Reusable paints & paths (zero allocation during draw)
    private val shadowAmbientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(dpF(10f), BlurMaskFilter.Blur.NORMAL)
    }
    private val shadowContactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(dpF(3f), BlurMaskFilter.Blur.NORMAL)
    }
    private val glassContourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(0.8f)
    }
    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassRefractPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1f)
    }
    private val glassTopRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.25f)
    }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropSpecPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.1f)
    }
    private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val dropPath = Path()
    private val clipPillPath = Path()
    private val rimPath = Path()
    private val specPath = Path()
    private val tmpRect = RectF()
    private var cachedGradientWidth = -1f
    private var cachedGradientHeight = -1f
    private var cachedGradientTone: Tone? = null

    private var waterlineShader: Any? = null // android.graphics.RuntimeShader on API 33+
    private var shaderInitAttempted = false

    // Real live backdrop blur behind the floating pill (API 31+ RenderNode + RenderEffect)
    private var backdropNode: Any? = null // android.graphics.RenderNode on API 31+
    private var backdropBlurPadPx = 0f
    private var hasRecordedBackdrop = false
    private var isRecordingBackdrop = false
    private var observersAttached = false

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isAttachedToWindow &&
            isHardwareAccelerated &&
            visibility == View.VISIBLE &&
            geometryReady
        ) {
            recordBackdropApi31()
        }
        true
    }

    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isAttachedToWindow &&
            isHardwareAccelerated &&
            visibility == View.VISIBLE
        ) {
            invalidate()
        }
    }

    private val mediumFace = ResourcesCompat.getFont(context, R.font.manrope_medium)
    private val boldFace = ResourcesCompat.getFont(context, R.font.manrope_bold)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning || !isAttachedToWindow) {
                frameLoopRunning = false
                return
            }
            val dt = if (lastFrameNanos == 0L) {
                FRAME_DT_DEFAULT
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 144f, 1f / 24f)
            }
            lastFrameNanos = frameTimeNanos
            stepPhysics(dt)
            invalidate()

            if (hasActiveDynamics() && animatorsAllowed()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                frameLoopRunning = false
                lastFrameNanos = 0L
            }
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setPadding(
            outerSideMarginPx.toInt(),
            outerTopMarginPx.toInt(),
            outerSideMarginPx.toInt(),
            outerBottomMarginPx.toInt()
        )

        val cellHeight = dpI(52)
        val gapPx = dpI(2)
        dock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(pillPadPx.toInt(), pillPadPx.toInt(), pillPadPx.toInt(), pillPadPx.toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        slots.forEachIndexed { index, slot ->
            val iconDrawable = ContextCompat.getDrawable(context, slot.glyph)?.mutate()
            val finalDrawable = if (index == Index.HOME && iconDrawable != null) {
                HomeDoorGlowDrawable(iconDrawable).also { homeDoorDrawable = it }
            } else {
                iconDrawable
            }

            val glyph = ImageView(context).apply {
                setImageDrawable(finalDrawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dpI(22), dpI(22))
            }
            val label = TextView(context).apply {
                text = context.getString(slot.label)
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_ALIVE_NavLabel)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                letterSpacing = -0.01f
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, dpI(2), 0, 0)
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dimens.touchMin
                isClickable = true
                isFocusable = true
                clipChildren = false
                clipToPadding = false
                setPadding(dpI(4), dpI(4), dpI(4), dpI(4))
                contentDescription = context.getString(slot.label)
                layoutParams = LinearLayout.LayoutParams(0, cellHeight, 1f).apply {
                    if (index > 0) marginStart = gapPx
                }
                addView(glyph)
                addView(label)
                @Suppress("ClickableViewAccessibility")
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            if (animatorsAllowed()) {
                                v.animate().cancel()
                                v.animate()
                                    .scaleX(0.92f)
                                    .scaleY(0.92f)
                                    .setDuration(90L)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            v.animate().cancel()
                            if (animatorsAllowed()) {
                                v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(220L)
                                    .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                                    .start()
                            } else {
                                v.scaleX = 1f
                                v.scaleY = 1f
                            }
                        }
                    }
                    false
                }
                setOnClickListener {
                    select(index, animate = true)
                    onSelect?.invoke(index)
                }
            }
            cells += cell
            glyphs += glyph
            labels += label
            dock.addView(cell)
        }

        addView(dock)
        paintCells()
    }

    fun setTone(newTone: Tone) {
        if (tone == newTone) return
        tone = newTone
        cachedGradientTone = null
        paintCells()
        invalidate()
    }

    fun select(index: Int, animate: Boolean = true) {
        if (index !in slots.indices) return
        val previous = selected
        selected = index
        paintCells()

        if (!geometryReady) {
            invalidate()
            return
        }

        val target = targetCapsFor(index)
        val currentCenter = (leftCapX + rightCapX) * 0.5f
        val targetCenter = (target.first + target.second) * 0.5f

        if (!animate || previous < 0 || !animatorsAllowed()) {
            leftCapX = target.first
            rightCapX = target.second
            leftCapV = 0f
            rightCapV = 0f
            sy = 1f
            syV = 0f
            settledSpan = target.second - target.first
            settleRippleArmed = false
            clearDroplets()
            if (index == Index.HOME) {
                homeDoorDrawable?.progress = 1f
                homeDoorDrawable?.warmFlash = 0f
            } else {
                homeDoorDrawable?.progress = 0f
            }
            invalidate()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (previous == index && previous == lastAnimatedIndex && now - lastAnimatedUptimeMs < 80L) {
            return
        }
        lastAnimatedIndex = index
        lastAnimatedUptimeMs = now

        if (previous != index) {
            val avgCellWidth = dock.width / slots.size.toFloat()
            val travel = abs(targetCenter - currentCenter)
            if (travel > avgCellWidth * 1.15f) {
                spawnDroplets(currentCenter, targetCenter)
            } else {
                clearDroplets()
            }
            settleRippleArmed = true
            ambientTailSeconds = 1.6f
        } else {
            // Re-tapping active tab sends a crisp water pulse + wobbles sy slightly
            syV -= 1.8f
            ambientTailSeconds = 1.2f
        }

        pushRipple(targetCenter, pillRect.centerY(), amp = 1.0f)
        playIconFx(index)
        startFrameLoopIfNeeded()
        invalidate()
    }

    /**
     * Current bounding rectangle `(left, top, right, bottom)` of the active water drop in local
     * view pixels. Exposed for layout/motion tests.
     */
    fun liquidDropBounds(): RectF {
        val ax = min(leftCapX, rightCapX)
        val bx = max(leftCapX, rightCapX)
        val cy = pillRect.centerY()
        val halfY = dropHalfHeight * sy
        return RectF(
            ax - dropHalfHeight,
            cy - halfY,
            bx + dropHalfHeight,
            cy + halfY
        )
    }

    /** Number of currently flying satellite droplets split off from the main drop. */
    fun activeDropletCount(): Int = droplets.count { it.active }

    /** Number of currently expanding water ripples inside the pill. */
    fun activeRippleCount(): Int = ripples.count { it.active }

    /** Deterministically advances liquid physics by [dtSeconds] (primarily for unit tests). */
    fun advanceForTest(dtSeconds: Float) {
        val step = 1f / 60f
        var remaining = dtSeconds.coerceAtLeast(0f)
        while (remaining > 0.0001f) {
            val dt = min(step, remaining)
            stepPhysics(dt)
            remaining -= dt
        }
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (dock.width <= 0 || dock.height <= 0 || cells.isEmpty()) return

        pillRect.set(
            dock.left.toFloat(),
            dock.top.toFloat(),
            dock.right.toFloat(),
            dock.bottom.toFloat()
        )
        dropHalfHeight = ((pillRect.height() - pillPadPx * 2f) * 0.5f).coerceAtLeast(dpF(12f))

        clipPillPath.reset()
        val pillR = pillRect.height() * 0.5f
        clipPillPath.addRoundRect(pillRect, pillR, pillR, Path.Direction.CW)

        val activeIdx = if (selected in slots.indices) selected else Index.HOME
        val caps = targetCapsFor(activeIdx)
        settledSpan = (caps.second - caps.first).coerceAtLeast(0f)

        if (!geometryReady) {
            geometryReady = true
            leftCapX = caps.first
            rightCapX = caps.second
            leftCapV = 0f
            rightCapV = 0f
            sy = 1f
            syV = 0f
            if (selected == Index.HOME) {
                homeDoorDrawable?.progress = 1f
            }
        } else if (changed && !hasActiveDynamics()) {
            leftCapX = caps.first
            rightCapX = caps.second
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touchX = ev.x.coerceIn(pillRect.left, pillRect.right)
                touchY = ev.y.coerceIn(pillRect.top, pillRect.bottom)
                if (!touchDown) {
                    touchDown = true
                    startFrameLoopIfNeeded()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                if (ev.actionMasked == MotionEvent.ACTION_UP && ev.x in pillRect.left..pillRect.right) {
                    pushRipple(ev.x, pillRect.centerY(), amp = 0.65f)
                    startFrameLoopIfNeeded()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !observersAttached) {
            viewTreeObserver.addOnPreDrawListener(preDrawListener)
            viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
            observersAttached = true
        }
        if (hasActiveDynamics() && animatorsAllowed()) {
            startFrameLoopIfNeeded()
        }
    }

    override fun onDetachedFromWindow() {
        if (observersAttached && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
        }
        observersAttached = false
        hasRecordedBackdrop = false
        frameLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        iconAnimators.forEach { it.cancel() }
        iconAnimators.clear()
        doorAnimator?.cancel()
        doorAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (pillRect.width() <= 0f || pillRect.height() <= 0f) {
            super.onDraw(canvas)
            return
        }

        ensureGlassGradients()

        // z = -2 : Soft ambient water-blue shadows + translucent Nimbus frosted glass shell
        drawGlassShell(canvas)

        // z = -1 : Nimbus "Waterline" shader (API 33+ hardware) or Canvas metaball fallback
        drawWaterLayer(canvas)

        super.onDraw(canvas)
    }

    // ------------------------------------------------------------------
    // PHYSICS & INTERACTION
    // ------------------------------------------------------------------

    private fun targetCapsFor(index: Int): Pair<Float, Float> {
        if (cells.isEmpty()) return 0f to 0f
        val cell = cells[index.coerceIn(cells.indices)]
        val dockX = dock.left.toFloat()
        val cellLeft = dockX + cell.left
        val cellRight = dockX + cell.right
        val cellCenter = (cellLeft + cellRight) * 0.5f
        val cellWidth = (cellRight - cellLeft).coerceAtLeast(dropHalfHeight * 2f)
        val halfSpan = ((cellWidth - dropHalfHeight * 2f) * 0.5f).coerceAtLeast(0f)
        return (cellCenter - halfSpan) to (cellCenter + halfSpan)
    }

    private fun stepPhysics(dt: Float) {
        timeSeconds += dt
        if (ambientTailSeconds > 0f) {
            ambientTailSeconds = (ambientTailSeconds - dt).coerceAtLeast(0f)
        }

        // Meniscus touch pull
        val targetMouse = if (touchDown) 1f else 0f
        mouseStrength += (targetMouse - mouseStrength) * min(1f, dt * 16f)

        if (!geometryReady) return

        val idx = if (selected in slots.indices) selected else Index.HOME
        val (targetL, targetR) = targetCapsFor(idx)
        val currentCenter = (leftCapX + rightCapX) * 0.5f
        val targetCenter = (targetL + targetR) * 0.5f
        val movingRight = targetCenter > currentCenter + 0.5f
        val movingLeft = targetCenter < currentCenter - 0.5f

        val kL: Float
        val cL: Float
        val kR: Float
        val cR: Float
        when {
            movingRight -> {
                kR = LEAD_K; cR = LEAD_C
                kL = TAIL_K; cL = TAIL_C
            }
            movingLeft -> {
                kL = LEAD_K; cL = LEAD_C
                kR = TAIL_K; cR = TAIL_C
            }
            else -> {
                kL = SETTLE_K; cL = SETTLE_C
                kR = SETTLE_K; cR = SETTLE_C
            }
        }

        val accL = kL * (targetL - leftCapX) - cL * leftCapV
        val accR = kR * (targetR - rightCapX) - cR * rightCapV
        leftCapV += accL * dt
        rightCapV += accR * dt
        leftCapX += leftCapV * dt
        rightCapX += rightCapV * dt

        // Bounce cleanly off inner ends of the pill
        val trackMinX = pillRect.left + pillPadPx + dropHalfHeight
        val trackMaxX = pillRect.right - pillPadPx - dropHalfHeight
        if (leftCapX < trackMinX) {
            leftCapX = trackMinX
            if (leftCapV < 0f) {
                leftCapV *= -0.28f
                syV -= 1.2f
            }
        }
        if (rightCapX > trackMaxX) {
            rightCapX = trackMaxX
            if (rightCapV > 0f) {
                rightCapV *= -0.28f
                syV -= 1.2f
            }
        }

        // Vertical squash proportional to speed + horizontal neck stretch
        val speedNorm = (hypot(leftCapV.toDouble(), rightCapV.toDouble()).toFloat() / (1400f * density))
            .coerceIn(0f, 1f)
        val span = max(0f, max(leftCapX, rightCapX) - min(leftCapX, rightCapX) - settledSpan)
        val stretchNorm = (span / max(dropHalfHeight * 3f, 1f)).coerceIn(0f, 1f)
        val targetSy = (1f - 0.16f * speedNorm - 0.14f * stretchNorm).coerceIn(0.72f, 1.0f)

        val syAcc = SY_K * (targetSy - sy) - SY_C * syV
        syV += syAcc * dt
        sy = (sy + syV * dt).coerceIn(0.68f, 1.16f)

        // Satellite droplets pull toward settled drop center
        val cy = pillRect.centerY()
        droplets.forEach { d ->
            if (!d.active) return@forEach
            val dx = targetCenter - d.x
            val dy = cy - d.y
            val ax = d.pullK * dx - 11f * d.vx
            val ay = d.pullK * dy - 11f * d.vy
            d.vx += ax * dt
            d.vy += ay * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.r += (d.targetR - d.r) * min(1f, dt * 14f)
            if (hypot(dx.toDouble(), dy.toDouble()) < dropHalfHeight * 0.42f &&
                abs(d.x - targetCenter) < settledSpan * 0.5f + dropHalfHeight * 0.35f
            ) {
                d.targetR = 0f
                d.r -= dropHalfHeight * 2.4f * dt
                if (d.r <= dpF(1.2f)) {
                    d.active = false
                }
            }
        }

        // Expanding water ripples
        ripples.forEach { r ->
            if (!r.active) return@forEach
            r.age += dt
            if (r.age >= RIPPLE_DURATION) {
                r.active = false
            }
        }

        // Trigger settle ripple once tail catches up
        val settled = abs(targetL - leftCapX) < dpF(2.5f) &&
            abs(targetR - rightCapX) < dpF(2.5f) &&
            abs(leftCapV) < dpF(25f) &&
            abs(rightCapV) < dpF(25f)
        if (settled) {
            leftCapX = targetL
            rightCapX = targetR
            leftCapV = 0f
            rightCapV = 0f
            if (settleRippleArmed) {
                settleRippleArmed = false
                pushRipple(targetCenter, cy, amp = 0.48f)
            }
        }
    }

    private fun spawnDroplets(fromX: Float, toX: Float) {
        val cy = pillRect.centerY()
        val q = dropHalfHeight
        val fracs = floatArrayOf(0.28f, 0.52f, 0.74f)
        val yOffsets = floatArrayOf(-0.24f * q, 0.28f * q, -0.16f * q)
        val targetRadii = floatArrayOf(0.25f * q, 0.33f * q, 0.21f * q)
        val dir = if (toX >= fromX) 1f else -1f

        for (i in droplets.indices) {
            val f = fracs[i]
            val startX = fromX + (toX - fromX) * f
            droplets[i].apply {
                x = startX
                y = cy + yOffsets[i]
                vx = dir * dpF(45f) * (1f - f)
                vy = yOffsets[i] * 2.8f
                r = targetRadii[i] * 0.45f
                targetR = targetRadii[i]
                pullK = 62f + i * 24f
                active = true
            }
        }
    }

    private fun clearDroplets() {
        droplets.forEach { it.active = false }
    }

    private fun pushRipple(x: Float, y: Float, amp: Float) {
        var slot = ripples.firstOrNull { !it.active }
        if (slot == null) {
            slot = ripples.maxByOrNull { it.age } ?: ripples[0]
        }
        slot.x = x
        slot.y = y
        slot.age = 0f
        slot.amp = amp
        slot.active = true
    }

    private fun hasActiveDynamics(): Boolean {
        if (touchDown || mouseStrength > 0.01f || ambientTailSeconds > 0.01f) return true
        if (droplets.any { it.active } || ripples.any { it.active }) return true
        if (!geometryReady || selected !in slots.indices) return false
        val (tL, tR) = targetCapsFor(selected)
        return abs(tL - leftCapX) > 0.35f ||
            abs(tR - rightCapX) > 0.35f ||
            abs(leftCapV) > 1f ||
            abs(rightCapV) > 1f ||
            abs(1f - sy) > 0.005f ||
            abs(syV) > 0.01f
    }

    private fun startFrameLoopIfNeeded() {
        if (!isAttachedToWindow || !animatorsAllowed() || frameLoopRunning) return
        frameLoopRunning = true
        lastFrameNanos = SystemClock.elapsedRealtimeNanos()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun animatorsAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled() else true

    // ------------------------------------------------------------------
    // ICON & LABEL RENDERING / FX
    // ------------------------------------------------------------------

    private fun paintCells() {
        val activeInk = if (tone == Tone.LIGHT) FG_LIGHT else FG_DARK
        val softInk = if (tone == Tone.LIGHT) FG_LIGHT_SOFT else FG_DARK_SOFT

        cells.forEachIndexed { index, cell ->
            val active = index == selected
            cell.isSelected = active
            cell.setBackgroundColor(Color.TRANSPARENT)

            val tint = if (active) activeInk else softInk
            val glyph = glyphs[index]
            if (index == Index.HOME) {
                homeDoorDrawable?.strokeTint = tint
                if (!active) homeDoorDrawable?.progress = 0f
            } else {
                glyph.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            }
            if (!animatorsAllowed() || !isAttachedToWindow) {
                glyph.scaleX = if (active) 1.06f else 1f
                glyph.scaleY = if (active) 1.06f else 1f
                glyph.translationY = if (active) -dpF(1f) else 0f
                glyph.rotation = 0f
            }

            labels[index].apply {
                setTextColor(tint)
                typeface = if (active) (boldFace ?: mediumFace) else mediumFace
            }
        }
    }

    private fun playIconFx(selectedIndex: Int) {
        iconAnimators.forEach { it.cancel() }
        iconAnimators.clear()

        // Gently relax any previously lifted icons back to rest
        glyphs.forEachIndexed { i, view ->
            if (i != selectedIndex && (view.scaleX != 1f || view.translationY != 0f || view.rotation != 0f)) {
                view.animate().cancel()
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .rotation(0f)
                    .setDuration(220L)
                    .start()
            }
        }

        val targetView = glyphs[selectedIndex]
        targetView.animate().cancel()

        when (selectedIndex) {
            Index.HOME -> {
                // Nimbus "glow": house hop + doorway illuminates warm gold -> aqua
                targetView.pivotX = targetView.width * 0.5f
                targetView.pivotY = targetView.height * 1f
                val anim = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.TRANSLATION_Y,
                            0f, -dpF(5f), 0f, -dpF(2f), -dpF(1f)
                        ),
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.SCALE_X,
                            1f, 1.05f, 1.12f, 0.98f, 1.06f
                        ),
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.SCALE_Y,
                            1f, 0.94f, 0.88f, 1.08f, 1.06f
                        )
                    )
                    duration = 560L
                }
                anim.start()
                iconAnimators += anim

                doorAnimator?.cancel()
                doorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 820L
                    addUpdateListener { va ->
                        val p = va.animatedFraction
                        homeDoorDrawable?.apply {
                            warmFlash = if (p < 0.45f) p / 0.45f else (1f - (p - 0.45f) / 0.55f).coerceAtLeast(0f)
                            progress = p
                        }
                        targetView.invalidate()
                    }
                    start()
                }
            }
            Index.QUEST -> {
                // Flag flutter wave + crisp pop
                targetView.pivotX = targetView.width * 0.35f
                targetView.pivotY = targetView.height * 0.85f
                val anim = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.ROTATION,
                            0f, -14f, 10f, -5f, 2f, 0f
                        ),
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.SCALE_X,
                            0.88f, 1.18f, 1.06f
                        ),
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.SCALE_Y,
                            0.88f, 1.18f, 1.06f
                        ),
                        ObjectAnimator.ofFloat(targetView, View.TRANSLATION_Y, 0f, -dpF(2f), -dpF(1f))
                    )
                    duration = 520L
                }
                anim.start()
                iconAnimators += anim
            }
            Index.STATS -> {
                // Nimbus "pop": snappy elastic scale-in
                targetView.pivotX = targetView.width * 0.5f
                targetView.pivotY = targetView.height * 0.5f
                val anim = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(targetView, View.SCALE_X, 0.72f, 1.22f, 1.06f),
                        ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 0.72f, 1.22f, 1.06f),
                        ObjectAnimator.ofFloat(targetView, View.TRANSLATION_Y, dpF(1f), -dpF(2f), -dpF(1f))
                    )
                    duration = 460L
                    interpolator = OvershootInterpolator(1.6f)
                }
                anim.start()
                iconAnimators += anim
            }
            Index.PROFILE -> {
                // Nimbus "wave": warm rock from base
                targetView.pivotX = targetView.width * 0.5f
                targetView.pivotY = targetView.height * 0.92f
                val anim = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(
                            targetView,
                            View.ROTATION,
                            0f, -14f, 11f, -7f, 3f, 0f
                        ),
                        ObjectAnimator.ofFloat(targetView, View.SCALE_X, 1f, 1.12f, 1.06f),
                        ObjectAnimator.ofFloat(targetView, View.SCALE_Y, 1f, 1.12f, 1.06f),
                        ObjectAnimator.ofFloat(targetView, View.TRANSLATION_Y, 0f, -dpF(1f))
                    )
                    duration = 560L
                }
                anim.start()
                iconAnimators += anim
            }
        }
    }

    // ------------------------------------------------------------------
    // LIQUID GLASS SHELL DRAWING
    // ------------------------------------------------------------------

    private fun ensureGlassGradients() {
        val w = pillRect.width()
        val h = pillRect.height()
        if (cachedGradientWidth == w && cachedGradientHeight == h && cachedGradientTone == tone) return
        cachedGradientWidth = w
        cachedGradientHeight = h
        cachedGradientTone = tone

        if (tone == Tone.LIGHT) {
            val topA = if (hasRecordedBackdrop) 0xA8FFFFFF.toInt() else 0xDAFFFFFF.toInt()
            val midA = if (hasRecordedBackdrop) 0x8CF7F9FC.toInt() else 0xC6F7F9FC.toInt()
            val botA = if (hasRecordedBackdrop) 0x9EFFFFFF.toInt() else 0xD0FFFFFF.toInt()
            glassFillPaint.shader = LinearGradient(
                0f, pillRect.top,
                0f, pillRect.bottom,
                intArrayOf(topA, midA, botA),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            glassRefractPaint.shader = LinearGradient(
                pillRect.left, pillRect.top,
                pillRect.right, pillRect.bottom,
                intArrayOf(0x10D8C8FF, 0x00FFFFFF, 0x10B8DCFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            glassStrokePaint.color = 0x92FFFFFF.toInt()
            glassTopRimPaint.shader = LinearGradient(
                0f, pillRect.top,
                0f, pillRect.top + h * 0.48f,
                intArrayOf(0xF2FFFFFF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            glassFillPaint.shader = LinearGradient(
                0f, pillRect.top,
                0f, pillRect.bottom,
                intArrayOf(0x700D1834, 0x580A1228, 0x640F2046),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            glassRefractPaint.shader = LinearGradient(
                pillRect.left, pillRect.top,
                pillRect.right, pillRect.bottom,
                intArrayOf(0x227EE8FF, 0x00FFFFFF, 0x1A00A2FF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            glassStrokePaint.color = 0x45FFFFFF
            glassTopRimPaint.shader = LinearGradient(
                0f, pillRect.top,
                0f, pillRect.top + h * 0.45f,
                intArrayOf(0x8CFFFFFF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun drawGlassShell(canvas: Canvas) {
        val pillR = pillRect.height() * 0.5f

        // Smooth Gaussian outer water-blue shadows clipped strictly OUTSIDE the pill so the
        // frosted glass interior stays bright, crystal-clear, and lets scrolling cards shine.
        canvas.save()
        canvas.clipOutPath(clipPillPath)
        shadowAmbientPaint.color = if (tone == Tone.LIGHT) 0x3000285A else 0x52000814
        tmpRect.set(pillRect)
        tmpRect.offset(0f, dpF(4f))
        canvas.drawRoundRect(tmpRect, pillR, pillR, shadowAmbientPaint)

        shadowContactPaint.color = if (tone == Tone.LIGHT) 0x1800285A else 0x36000814
        tmpRect.set(pillRect)
        tmpRect.offset(0f, dpF(1.2f))
        canvas.drawRoundRect(tmpRect, pillR, pillR, shadowContactPaint)
        canvas.restore()

        // Live GPU-blurred backdrop behind the pill on API 31+ (RenderEffect blur + saturate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated &&
            hasRecordedBackdrop
        ) {
            drawBackdropApi31(canvas)
        }

        // Frosted glass tint + cool aqua iridescence glaze
        canvas.drawRoundRect(pillRect, pillR, pillR, glassFillPaint)
        canvas.drawRoundRect(pillRect, pillR, pillR, glassRefractPaint)

        // Subtle outer contour hairline so light glass reads cleanly even over bright white cards
        if (tone == Tone.LIGHT) {
            glassContourPaint.color = 0x10122038
            canvas.drawRoundRect(pillRect, pillR, pillR, glassContourPaint)
        }

        // Outer glass edge + bright inner top specular crescent rim
        tmpRect.set(pillRect)
        tmpRect.inset(dpF(0.5f), dpF(0.5f))
        canvas.drawRoundRect(tmpRect, pillR - dpF(0.5f), pillR - dpF(0.5f), glassStrokePaint)

        tmpRect.set(pillRect)
        tmpRect.inset(dpF(1.1f), dpF(1.1f))
        canvas.drawRoundRect(tmpRect, pillR - dpF(1.1f), pillR - dpF(1.1f), glassTopRimPaint)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun recordBackdropApi31() {
        if (isRecordingBackdrop || pillRect.width() <= 0f || pillRect.height() <= 0f) return
        val container = parent as? ViewGroup ?: return
        val contentLayer = container.findViewById<View>(R.id.tabContent) ?: return
        if (contentLayer.width <= 0 || contentLayer.height <= 0) return

        val node = (backdropNode as? RenderNode) ?: RenderNode("AliveNavBackdrop").also {
            val blurRadius = dpF(16f)
            val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
            val sat = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.18f) })
            )
            it.setRenderEffect(RenderEffect.createChainEffect(blur, sat))
            backdropNode = it
        }

        val pad = dpI(24)
        backdropBlurPadPx = pad.toFloat()
        val recW = pillRect.width().toInt() + pad * 2
        val recH = pillRect.height().toInt() + pad * 2
        if (recW <= 0 || recH <= 0) return

        isRecordingBackdrop = true
        try {
            node.setPosition(0, 0, recW, recH)
            val rc = node.beginRecording(recW, recH)
            try {
                val originX = left + pillRect.left - pad
                val originY = top + pillRect.top - pad
                rc.save()
                rc.translate(-originX, -originY)
                container.background?.draw(rc)
                rc.translate(contentLayer.left.toFloat(), contentLayer.top.toFloat())
                contentLayer.draw(rc)
                rc.restore()
            } finally {
                node.endRecording()
            }
            if (!hasRecordedBackdrop) {
                hasRecordedBackdrop = true
                cachedGradientTone = null
            }
        } catch (_: Throwable) {
            hasRecordedBackdrop = false
        } finally {
            isRecordingBackdrop = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBackdropApi31(canvas: Canvas) {
        val node = backdropNode as? RenderNode ?: return
        if (!node.hasDisplayList()) return
        canvas.save()
        canvas.clipPath(clipPillPath)
        canvas.translate(pillRect.left - backdropBlurPadPx, pillRect.top - backdropBlurPadPx)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    // ------------------------------------------------------------------
    // WATER LAYER: AGSL RUNTIMESHADER (API 33+) + CANVAS FALLBACK
    // ------------------------------------------------------------------

    private fun drawWaterLayer(canvas: Canvas) {
        if (!geometryReady) return
        val ax = min(leftCapX, rightCapX)
        val bx = max(leftCapX, rightCapX)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canvas.isHardwareAccelerated) {
            val shader = getOrInitAgslShader()
            if (shader != null) {
                if (drawWaterAgsl(canvas, shader, ax, bx)) return
            }
        }
        drawWaterCanvas(canvas, ax, bx)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getOrInitAgslShader(): RuntimeShader? {
        if (!shaderInitAttempted) {
            shaderInitAttempted = true
            waterlineShader = try {
                RuntimeShader(WATERLINE_AGSL)
            } catch (_: Throwable) {
                null
            }
        }
        return waterlineShader as? RuntimeShader
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawWaterAgsl(canvas: Canvas, shader: RuntimeShader, ax: Float, bx: Float): Boolean {
        return try {
            val stretch = ((bx - ax - settledSpan) / max(dropHalfHeight * 2.2f, 1f)).coerceIn(0f, 1f)
            shader.setFloatUniform(
                "uPill",
                pillRect.centerX(),
                pillRect.centerY(),
                pillRect.width() * 0.5f,
                pillRect.height() * 0.5f
            )
            shader.setFloatUniform("uCaps", ax, bx, pillRect.centerY(), dropHalfHeight)
            shader.setFloatUniform("uMorph", sy, stretch, dpF(230f), density)

            val d0 = droplets[0]
            val d1 = droplets[1]
            val d2 = droplets[2]
            shader.setFloatUniform("uDrop0", d0.x, d0.y, d0.r, if (d0.active) 1f else 0f)
            shader.setFloatUniform("uDrop1", d1.x, d1.y, d1.r, if (d1.active) 1f else 0f)
            shader.setFloatUniform("uDrop2", d2.x, d2.y, d2.r, if (d2.active) 1f else 0f)

            val r0 = ripples[0]
            val r1 = ripples[1]
            val r2 = ripples[2]
            shader.setFloatUniform("uRip0", r0.x, r0.y, r0.age, if (r0.active) r0.amp else 0f)
            shader.setFloatUniform("uRip1", r1.x, r1.y, r1.age, if (r1.active) r1.amp else 0f)
            shader.setFloatUniform("uRip2", r2.x, r2.y, r2.age, if (r2.active) r2.amp else 0f)

            shader.setFloatUniform("uMouse", touchX, touchY, mouseStrength, timeSeconds)
            shader.setFloatUniform(
                "uTone",
                if (tone == Tone.DARK) 1f else 0f,
                if (ambientTailSeconds > 0f) min(1f, ambientTailSeconds) else 0f
            )

            shaderPaint.shader = shader
            canvas.save()
            canvas.clipPath(clipPillPath)
            canvas.drawRect(
                pillRect.left - dpF(2f),
                pillRect.top - dpF(2f),
                pillRect.right + dpF(2f),
                pillRect.bottom + dpF(2f),
                shaderPaint
            )
            canvas.restore()
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun drawWaterCanvas(canvas: Canvas, ax: Float, bx: Float) {
        val cy = pillRect.centerY()
        val q = dropHalfHeight
        val ry = q * sy
        val stretch = ((bx - ax - settledSpan) / max(q * 2.2f, 1f)).coerceIn(0f, 1f)

        canvas.save()
        canvas.clipPath(clipPillPath)

        // Concentric tap ripples inside the pill
        ripples.forEach { r ->
            if (!r.active || r.age >= RIPPLE_DURATION) return@forEach
            val p = r.age / RIPPLE_DURATION
            val radius = q * 0.45f + r.age * dpF(230f)
            val alpha = ((1f - p) * (1f - p) * r.amp * (if (tone == Tone.LIGHT) 105f else 125f))
                .toInt().coerceIn(0, 255)
            if (alpha > 1) {
                rippleStrokePaint.strokeWidth = dpF(1.4f - 0.7f * p)
                rippleStrokePaint.color = (alpha shl 24) or (if (tone == Tone.LIGHT) 0xD2C4FF else 0x8CE8FF)
                canvas.drawCircle(r.x, r.y, radius, rippleStrokePaint)
            }
        }

        // Build hourglass pinched capsule path when stretched across multiple tabs
        dropPath.reset()
        if (stretch > 0.04f && bx - ax > dpF(6f)) {
            val midX = (ax + bx) * 0.5f
            val neckHalf = ry * (1f - 0.44f * stretch)
            tmpRect.set(ax - q, cy - ry, ax + q, cy + ry)
            dropPath.arcTo(tmpRect, 90f, 180f, true)
            dropPath.cubicTo(
                ax + (midX - ax) * 0.45f, cy - ry,
                midX - (midX - ax) * 0.35f, cy - neckHalf,
                midX, cy - neckHalf
            )
            dropPath.cubicTo(
                midX + (bx - midX) * 0.35f, cy - neckHalf,
                bx - (bx - midX) * 0.45f, cy - ry,
                bx, cy - ry
            )
            tmpRect.set(bx - q, cy - ry, bx + q, cy + ry)
            dropPath.arcTo(tmpRect, 270f, 180f, false)
            dropPath.cubicTo(
                bx - (bx - midX) * 0.45f, cy + ry,
                midX + (bx - midX) * 0.35f, cy + neckHalf,
                midX, cy + neckHalf
            )
            dropPath.cubicTo(
                midX - (midX - ax) * 0.35f, cy + neckHalf,
                ax + (midX - ax) * 0.45f, cy + ry,
                ax, cy + ry
            )
            dropPath.close()
        } else {
            tmpRect.set(ax - q, cy - ry, bx + q, cy + ry)
            dropPath.addRoundRect(tmpRect, ry, ry, Path.Direction.CW)
        }

        // Subtle floating shadow below water capsule (matches 21st.dev elevated drop)
        canvas.save()
        canvas.translate(0f, dpF(2.6f))
        dropShadowPaint.color = if (tone == Tone.LIGHT) 0x24101E38 else 0x48000E24
        canvas.drawPath(dropPath, dropShadowPaint)
        canvas.restore()

        // Crystal-clear frosted white drop fill
        dropBodyPaint.shader = LinearGradient(
            0f, cy - ry,
            0f, cy + ry,
            if (tone == Tone.LIGHT) {
                intArrayOf(0xFAFFFFFF.toInt(), 0xEEFBFDFF.toInt(), 0xF2EEF3FA.toInt())
            } else {
                intArrayOf(0x7088EAFF, 0x4A30A8FF, 0x621E78C8)
            },
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(dropPath, dropBodyPaint)

        // Satellite water droplets
        droplets.forEach { d ->
            if (!d.active || d.r <= 0.5f) return@forEach
            dropBodyPaint.shader = null
            dropBodyPaint.color = if (tone == Tone.LIGHT) 0xEBFFFFFF.toInt() else 0x7A7EE8FF
            canvas.drawCircle(d.x, d.y, d.r, dropBodyPaint)
            dropRimPaint.color = if (tone == Tone.LIGHT) 0x78C8B4FF else 0xAA9CEBFF.toInt()
            canvas.drawCircle(d.x, d.y, d.r, dropRimPaint)
        }

        // Bright top meniscus highlight inside drop + delicate chromatic dispersion rim
        specPath.reset()
        val inset = dpF(3f)
        val topH = ry * 0.44f
        if (bx + q - (ax - q) > inset * 2f) {
            tmpRect.set(
                ax - q + inset,
                cy - ry + dpF(1.5f),
                bx + q - inset,
                cy - ry + topH
            )
            specPath.addRoundRect(tmpRect, topH * 0.5f, topH * 0.5f, Path.Direction.CW)
            dropSpecPaint.shader = LinearGradient(
                0f, tmpRect.top,
                0f, tmpRect.bottom,
                intArrayOf(if (tone == Tone.LIGHT) 0xF4FFFFFF.toInt() else 0x80FFFFFF.toInt(), 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(specPath, dropSpecPaint)
        }

        if (tone == Tone.LIGHT) {
            dropRimPaint.strokeWidth = dpF(1.8f)
            dropRimPaint.color = 0x56C7B2FF
            canvas.drawPath(dropPath, dropRimPaint)
            dropRimPaint.strokeWidth = dpF(1.0f)
            dropRimPaint.color = 0xEEFFFFFF.toInt()
            canvas.drawPath(dropPath, dropRimPaint)
        } else {
            dropRimPaint.strokeWidth = dpF(1.1f)
            dropRimPaint.color = 0x88A0ECFF.toInt()
            canvas.drawPath(dropPath, dropRimPaint)
        }

        // Finger meniscus micro-glint
        if (mouseStrength > 0.03f) {
            val mAlpha = (mouseStrength * 90f).toInt().coerceIn(0, 255)
            dropBodyPaint.shader = null
            dropBodyPaint.color = (mAlpha shl 24) or 0xFFFFFF
            canvas.drawCircle(touchX, touchY.coerceIn(cy - q * 0.6f, cy + q * 0.6f), dpF(10f) * mouseStrength, dropBodyPaint)
        }

        canvas.restore()
    }

    private fun dpF(value: Float): Float = value * density
    private fun dpI(value: Int): Int = (value * density + 0.5f).toInt()

    /**
     * Wraps [R.drawable.ic_dock_home] and illuminates its doorway (`[9.5..14.5, 15..20]`
     * in 24x24 coordinates) from warm gold (`#FFF3A8`) on tap to translucent aqua (`#997EE8FF`)
     * while Home is selected — replicating Nimbus `.nv-door` glow natively.
     */
    private class HomeDoorGlowDrawable(private val base: Drawable) : Drawable() {
        var progress: Float = 0f
        var warmFlash: Float = 0f
        var strokeTint: Int = FG_LIGHT_SOFT
            set(value) {
                field = value
                DrawableCompat.setTint(base, value)
            }

        init {
            DrawableCompat.setTint(base, strokeTint)
        }

        private val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val doorRect = RectF()

        override fun onBoundsChange(bounds: Rect) {
            base.bounds = bounds
        }

        override fun draw(canvas: Canvas) {
            if (warmFlash > 0.02f) {
                val b = bounds
                val sx = b.width() / 24f
                val sy = b.height() / 24f
                doorRect.set(
                    b.left + 9.5f * sx,
                    b.top + 15.0f * sy,
                    b.left + 14.5f * sx,
                    b.top + 20.0f * sy
                )
                val warm = 0xE8FFF3A8.toInt()
                val alpha = (Color.alpha(warm) * warmFlash.coerceIn(0f, 1f)).toInt()
                doorPaint.color = ColorUtils.setAlphaComponent(warm, alpha)
                canvas.drawRoundRect(doorRect, 1.2f * sx, 1.2f * sy, doorPaint)
            }
            base.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            base.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            base.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Android Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val FG_LIGHT = 0xFF0B132B.toInt()
        const val FG_LIGHT_SOFT = 0x9E0B132B.toInt()
        const val FG_DARK = 0xFFF1F7FF.toInt()
        const val FG_DARK_SOFT = 0xA8F1F7FF.toInt()

        private const val FRAME_DT_DEFAULT = 1f / 60f
        private const val RIPPLE_DURATION = 0.72f

        // Two-body spring constants (Nimbus Waterline feel)
        private const val LEAD_K = 240f
        private const val LEAD_C = 25.5f
        private const val TAIL_K = 80f
        private const val TAIL_C = 13.8f
        private const val SETTLE_K = 190f
        private const val SETTLE_C = 23f
        private const val SY_K = 285f
        private const val SY_C = 10.5f

        /**
         * AGSL ("Waterline") shader for API 33+ hardware-accelerated rendering.
         * Uses scalar vec uniforms (zero uniform array indexing) for strict Mali/Adreno driver safety
         * and returns premultiplied half4 output for Skia compositing.
         */
        private const val WATERLINE_AGSL = """
            uniform float4 uPill;
            uniform float4 uCaps;
            uniform float4 uMorph;
            uniform float4 uDrop0;
            uniform float4 uDrop1;
            uniform float4 uDrop2;
            uniform float4 uRip0;
            uniform float4 uRip1;
            uniform float4 uRip2;
            uniform float4 uMouse;
            uniform float2 uTone;

            float sdRB(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + float2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            float smin(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            float dropSdf(float2 p) {
                float ax = uCaps.x;
                float bx = uCaps.y;
                float cy = uCaps.z;
                float q = max(uCaps.w, 4.0);
                float sy = max(uMorph.x, 0.55);
                float stretch = uMorph.y;
                float dpr = max(uMorph.w, 1.0);

                float span = max(bx - ax, 0.001);
                float u = clamp((p.x - ax) / span, 0.0, 1.0);
                float waist = 4.0 * u * (1.0 - u);
                float qEff = q * (1.0 - 0.44 * stretch * waist);

                float cx = clamp(p.x, ax, bx);
                float2 rel = float2(p.x - cx, (p.y - cy) / sy);
                float d = length(rel) - qEff;

                if (uDrop0.w > 0.5) {
                    d = smin(d, length(p - uDrop0.xy) - uDrop0.z, 7.0 * dpr);
                }
                if (uDrop1.w > 0.5) {
                    d = smin(d, length(p - uDrop1.xy) - uDrop1.z, 7.0 * dpr);
                }
                if (uDrop2.w > 0.5) {
                    d = smin(d, length(p - uDrop2.xy) - uDrop2.z, 7.0 * dpr);
                }
                if (uMouse.z > 0.02) {
                    float dm = length(p - uMouse.xy) - 6.0 * dpr;
                    d = smin(d, dm, 9.0 * dpr * uMouse.z);
                }
                return d;
            }

            float rippleWave(float2 p, float4 rip, float speed, float dpr) {
                if (rip.w <= 0.001 || rip.z >= 0.72) return 0.0;
                float dist = length(p - rip.xy);
                float wavePos = dist - rip.z * speed;
                float decay = exp(-abs(wavePos) / (11.0 * dpr)) * exp(-dist / (150.0 * dpr));
                float fade = 1.0 - rip.z / 0.72;
                return sin(wavePos * (0.24 / dpr)) * decay * fade * fade * rip.w;
            }

            half4 main(float2 p) {
                float dpr = max(uMorph.w, 1.0);
                float speed = uMorph.z;
                float q = max(uCaps.w, 4.0);
                float isDark = uTone.x;

                float dPill = sdRB(p - uPill.xy, uPill.zw, uPill.w);
                float pillMask = smoothstep(1.2, -1.2, dPill);
                if (pillMask <= 0.001) return half4(0.0);

                float d = dropSdf(p);
                float eps = 1.6 * dpr;
                float gx = dropSdf(p + float2(eps, 0.0)) - d;
                float gy = dropSdf(p + float2(0.0, eps)) - d;
                float2 n = float2(gx, gy) / max(length(float2(gx, gy)), 0.0001);

                float rip = rippleWave(p, uRip0, speed, dpr)
                          + rippleWave(p, uRip1, speed, dpr)
                          + rippleWave(p, uRip2, speed, dpr);

                // Soft elevated contact shadow biased slightly beneath active drop (21st.dev look)
                float shadowDy = clamp((p.y - uPill.y) / q, -0.5, 1.0);
                float outside = max(d - shadowDy * (1.2 * dpr), 0.0);
                float shadow = exp(-outside / (3.8 * dpr)) * smoothstep(-0.4, 1.0, d)
                             * mix(0.16 * (0.65 + 0.35 * max(shadowDy, 0.0)), 0.32, isDark);

                // Subtle pearlescent tap ripple across pill glass
                float ripGlow = clamp(abs(rip) * 0.72, 0.0, 0.28);
                float3 ripCol = mix(float3(0.78, 0.74, 0.99), float3(0.76, 0.90, 1.0), 0.5 + 0.5 * rip);

                float mask = smoothstep(1.0 * dpr, -0.8 * dpr, d);
                float yn = clamp((p.y - uPill.y) / q, -1.0, 1.0);

                // Thin chromatic dispersion / iridescent meniscus strictly around capsule edge
                float rimBand = smoothstep(-3.2 * dpr, -0.2 * dpr, d);
                float prismPhase = 0.5 + 0.5 * sin(n.x * 2.2 + n.y * 1.4 + uMouse.w * 0.8);
                float3 prismCol = mix(float3(0.77, 0.69, 0.99), float3(0.68, 0.86, 1.0), prismPhase);

                // Crisp top white glass specular meniscus + soft crystal body
                float specTop = smoothstep(-0.18, -0.88, yn - n.y * 0.20)
                              * smoothstep(0.4 * dpr, -5.0 * dpr, d)
                              * smoothstep(-q * 0.76, -q * 0.12, d);
                float caus = 0.5 + 0.5 * sin(p.x / (24.0 * dpr) + uMouse.w * 2.2 + sin(p.y / (15.0 * dpr) - uMouse.w * 1.4));
                caus *= uTone.y * 0.08 + 0.02;

                float3 bodyLight = mix(float3(0.995, 0.997, 1.0), float3(0.935, 0.955, 0.985), smoothstep(-0.1, 0.95, yn));
                bodyLight = mix(bodyLight, prismCol, rimBand * 0.42);
                bodyLight += float3(1.0) * (specTop * 0.36 + rimBand * (1.0 - max(yn, 0.0)) * 0.18) + float3(0.82, 0.88, 1.0) * caus;

                float3 bodyDark = mix(float3(0.48, 0.88, 1.0), float3(0.12, 0.46, 0.82), smoothstep(-0.5, 0.9, yn));
                bodyDark = mix(bodyDark, float3(0.76, 0.86, 1.0), rimBand * 0.65);
                bodyDark += float3(1.0) * (specTop * 0.38);

                float3 waterCol = clamp(mix(bodyLight, bodyDark, isDark), 0.0, 1.0);
                float waterAlpha = mix(0.88, 0.42, isDark) + rimBand * 0.06 + specTop * 0.05;
                waterAlpha = clamp(waterAlpha, 0.0, 0.95);

                // Combine outer shadow/ripples with crystal drop
                float3 bgCol = mix(float3(0.06, 0.11, 0.22), float3(0.0, 0.05, 0.14), isDark);
                float outA = max(shadow, ripGlow * 0.55) * (1.0 - mask);
                float3 outRgb = mix(bgCol, ripCol, clamp(ripGlow / max(outA, 0.001), 0.0, 1.0));

                float finalA = mask * waterAlpha + outA * (1.0 - mask * waterAlpha);
                float3 finalRgb = mask * waterCol * waterAlpha + outRgb * outA * (1.0 - mask * waterAlpha);
                finalA *= pillMask;
                finalRgb *= pillMask;
                return half4(half3(finalRgb), half(finalA));
            }
        """
    }
}
