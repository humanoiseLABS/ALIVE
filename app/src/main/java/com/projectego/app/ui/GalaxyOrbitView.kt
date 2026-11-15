package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure orbital math and inertia integration for [GalaxyOrbitView]. Kept pure so
 * angular wraparound, exponential fling damping, and detent ticks can be unit-tested
 * without UI clocks.
 */
object GalaxyOrbitPhysics {

    const val DEFAULT_FRICTION = 3.2f
    const val STOP_VELOCITY_RAD_PER_SEC = 0.06f
    const val MAX_VELOCITY_RAD_PER_SEC = 18.0f
    private const val MAX_SWAY_DEG = 10f
    private const val SWAY_GAIN = 1.15f

    /** Signed shortest angular distance from [fromRad] to [toRad] wrapped into (-PI, PI]. */
    fun shortestAngleDelta(fromRad: Float, toRad: Float): Float {
        var delta = (toRad - fromRad).toDouble()
        val twoPi = 2.0 * PI
        while (delta > PI) delta -= twoPi
        while (delta <= -PI) delta += twoPi
        return delta.toFloat()
    }

    /** Polar angle in radians around ([cx], [cy]). */
    fun pointAngle(cx: Float, cy: Float, x: Float, y: Float): Float =
        atan2((y - cy).toDouble(), (x - cx).toDouble()).toFloat()

    /**
     * Advances [angleRad] and [velocityRadPerSec] across [dtSec] using exact exponential
     * deceleration (`omega(t) = omega0 * e^(-friction * t)`).
     */
    fun stepInertia(
        angleRad: Float,
        velocityRadPerSec: Float,
        dtSec: Float,
        friction: Float = DEFAULT_FRICTION
    ): Pair<Float, Float> {
        if (dtSec <= 0f || friction <= 0f || abs(velocityRadPerSec) < STOP_VELOCITY_RAD_PER_SEC) {
            return angleRad to 0f
        }
        val decay = exp((-friction * dtSec).toDouble()).toFloat()
        val nextVel = velocityRadPerSec * decay
        val travel = velocityRadPerSec * (1f - decay) / friction
        val settledVel = if (abs(nextVel) < STOP_VELOCITY_RAD_PER_SEC) 0f else nextVel
        return (angleRad + travel) to settledVel
    }

    /** Integer notch index around the circle so crossing each member step emits a subtle tick. */
    fun slotIndexForAngle(angleRad: Float, count: Int): Int {
        if (count <= 0) return 0
        val step = (2.0 * PI) / count.toDouble()
        return floor(angleRad.toDouble() / step).toInt()
    }

    /** Gentle velocity-proportional tilt while keeping sprites upright when settled. */
    fun memberRotationDeg(angularVelocityRadPerSec: Float): Float {
        if (abs(angularVelocityRadPerSec) < STOP_VELOCITY_RAD_PER_SEC) return 0f
        return (-angularVelocityRadPerSec * SWAY_GAIN).coerceIn(-MAX_SWAY_DEG, MAX_SWAY_DEG)
    }
}

/**
 * Interactive orbital ring holding a stationary centered [centerView] and a ring of
 * orbiting member views draggable 360 degrees with finger tracking and momentum fling.
 */
class GalaxyOrbitView(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minTrackRadiusPx = 18f * density
    private val orbitEase = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    private val tapBounce = OvershootInterpolator(2.2f)
    private val paper = ContextCompat.getColor(context, R.color.flat_paper)
    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(paper, 44)
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        color = ColorUtils.setAlphaComponent(paper, 224)
    }
    private val auraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val auraStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f * density
    }
    private val companionRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
    }
    private val companionDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val companionDotRadiusPx = 3.1f * density

    private var centerView: View? = null
    private val orbiters = mutableListOf<View>()
    private var baseAlphas: FloatArray = FloatArray(0)
    private var awakenedFlags: BooleanArray = BooleanArray(0)
    private var memberHues: IntArray = IntArray(0)
    private var memberSizePx = 0

    var activeCompanionIndex: Int? = null
        private set

    var selectedIndex: Int? = null
        private set

    var onOrbiterSelected: ((Int?) -> Unit)? = null

    private var orbitAngleRad = 0f
    private var angularVelocityRad = 0f
    private var lastNotchSlot = 0

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastAngleRad = 0f
    private var lastEventTimeMs = 0L
    private var movedPastSlop = false
    private var canTrackAngle = false

    private var flingAnimator: ValueAnimator? = null
    private var entryAnimator: ValueAnimator? = null
    private var entryPending = false

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
    }

    val orbitAngleRadians: Float
        get() = orbitAngleRad

    fun setCenterView(view: View, layoutParams: LayoutParams) {
        centerView?.let { removeView(it) }
        centerView = view
        addView(view, 0, layoutParams)
    }

    fun setOrbiters(views: List<View>, memberSize: Int) {
        orbiters.forEach { removeView(it) }
        orbiters.clear()
        memberSizePx = memberSize
        views.forEach { child ->
            orbiters.add(child)
            addView(child, LayoutParams(memberSize, memberSize))
        }
        baseAlphas = FloatArray(views.size) { 1f }
        awakenedFlags = BooleanArray(views.size)
        memberHues = IntArray(views.size) { paper }
        activeCompanionIndex = activeCompanionIndex?.takeIf { it in views.indices }
        selectedIndex = selectedIndex?.takeIf { it in views.indices }
        lastNotchSlot = GalaxyOrbitPhysics.slotIndexForAngle(orbitAngleRad, orbiters.size)
        entryPending = true
        if (isLaidOut) applyOrbiterTransforms()
    }

    fun setOrbiterBaseAlphas(alphas: List<Float>) {
        baseAlphas = FloatArray(orbiters.size) { index ->
            alphas.getOrNull(index)?.coerceIn(0f, 1f) ?: 1f
        }
        orbiters.forEachIndexed { index, child ->
            child.alpha = if (index == selectedIndex) 1f else baseAlphaOf(index)
        }
        invalidate()
    }

    /**
     * Configures subtle colored auras for awakened members and a prominent dual-ring
     * beacon around [companionIndex] so active progression and the active companion
     * stay readable even while unawakened members keep vivid pastel colors.
     */
    fun setOrbiterAuras(
        awakened: List<Boolean>,
        hues: List<Int>,
        companionIndex: Int?
    ) {
        awakenedFlags = BooleanArray(orbiters.size) { index ->
            awakened.getOrNull(index) == true
        }
        memberHues = IntArray(orbiters.size) { index ->
            hues.getOrNull(index) ?: paper
        }
        activeCompanionIndex = companionIndex?.takeIf { it in orbiters.indices }
        invalidate()
    }

    fun setSelectedSlot(index: Int?, animateToTop: Boolean = true) {
        val next = index?.takeIf { it in orbiters.indices }
        selectedIndex = next
        val animated = AliveMotion.shouldAnimate(this)
        orbiters.forEachIndexed { i, child ->
            val targetScale = if (i == next) SELECTED_SCALE else 1f
            val targetAlpha = if (i == next) 1f else baseAlphaOf(i)
            child.animate().cancel()
            if (animated) {
                if (i == next) {
                    child.animate()
                        .scaleX(1.30f)
                        .scaleY(1.30f)
                        .alpha(targetAlpha)
                        .setDuration(110L)
                        .withEndAction {
                            child.animate()
                                .scaleX(targetScale)
                                .scaleY(targetScale)
                                .setDuration(220L)
                                .setInterpolator(tapBounce)
                                .start()
                        }
                        .start()
                } else {
                    child.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .alpha(targetAlpha)
                        .setDuration(180L)
                        .setInterpolator(orbitEase)
                        .start()
                }
            } else {
                child.scaleX = targetScale
                child.scaleY = targetScale
                child.alpha = targetAlpha
            }
        }

        if (animateToTop && next != null && orbiters.isNotEmpty()) {
            val count = orbiters.size
            val desired = (-next * 2.0 * PI / count).toFloat()
            val delta = GalaxyOrbitPhysics.shortestAngleDelta(
                normalizeRadians(orbitAngleRad),
                normalizeRadians(desired)
            )
            if (animated && abs(delta) > 0.01f) {
                animateOrbitTo(orbitAngleRad + delta)
            } else {
                setOrbitAngleRadians(orbitAngleRad + delta, 0f)
            }
        }
        invalidate()
    }

    fun setOrbitAngleRadians(angleRad: Float, swayVelocityRadPerSec: Float = angularVelocityRad) {
        orbitAngleRad = angleRad
        angularVelocityRad = swayVelocityRadPerSec
        applyOrbiterTransforms()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        // Anchor orbiters at (0, 0) once so rotation uses GPU translations without re-layout.
        orbiters.forEach { child ->
            if (child.visibility != View.GONE) {
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
            }
        }
        applyOrbiterTransforms()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (entryPending && orbiters.isNotEmpty()) {
            entryPending = false
            playEntrySwirl()
        }
    }

    override fun onDetachedFromWindow() {
        cancelAnimators()
        angularVelocityRad = 0f
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimators()
                resetOrbiterScales()
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastEventTimeMs = event.eventTime
                movedPastSlop = false
                angularVelocityRad = 0f

                val radius = hypot(event.x - cx, event.y - cy)
                canTrackAngle = radius >= minTrackRadiusPx
                if (canTrackAngle) {
                    lastAngleRad = GalaxyOrbitPhysics.pointAngle(cx, cy, event.x, event.y)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                if (!movedPastSlop && hypot(x - downX, y - downY) >= touchSlop) {
                    movedPastSlop = true
                }

                val radius = hypot(x - cx, y - cy)
                if (radius < minTrackRadiusPx) {
                    canTrackAngle = false
                    lastEventTimeMs = event.eventTime
                    return true
                }

                val currentAngle = GalaxyOrbitPhysics.pointAngle(cx, cy, x, y)
                if (!canTrackAngle) {
                    canTrackAngle = true
                    lastAngleRad = currentAngle
                    lastEventTimeMs = event.eventTime
                    return true
                }

                val delta = GalaxyOrbitPhysics.shortestAngleDelta(lastAngleRad, currentAngle)
                val dtSec = ((event.eventTime - lastEventTimeMs).coerceIn(1L, 80L)) / 1000f
                val instantVel = (delta / dtSec).coerceIn(
                    -GalaxyOrbitPhysics.MAX_VELOCITY_RAD_PER_SEC,
                    GalaxyOrbitPhysics.MAX_VELOCITY_RAD_PER_SEC
                )
                angularVelocityRad = 0.42f * angularVelocityRad + 0.58f * instantVel

                updateOrbitWithHaptics(orbitAngleRad + delta)
                lastAngleRad = currentAngle
                lastEventTimeMs = event.eventTime
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val isTap = !movedPastSlop
                val ageMs = event.eventTime - lastEventTimeMs
                activePointerId = MotionEvent.INVALID_POINTER_ID

                if (isTap) {
                    handleOrbiterTap(event.x, event.y)
                    return true
                }
                if (ageMs <= 90L && abs(angularVelocityRad) > 0.25f) {
                    startFling()
                } else {
                    settleUpright()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                settleUpright()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        orbiters.forEachIndexed { index, child ->
            val isCompanion = index == activeCompanionIndex
            val isAwakened = awakenedFlags.getOrNull(index) == true
            val isSelected = index == selectedIndex
            if (!isCompanion && !isAwakened && !isSelected) return@forEachIndexed

            val childW = child.width.takeIf { it > 0 } ?: child.measuredWidth.takeIf { it > 0 } ?: memberSizePx
            val childH = child.height.takeIf { it > 0 } ?: child.measuredHeight.takeIf { it > 0 } ?: memberSizePx
            if (childW <= 0 || childH <= 0) return@forEachIndexed

            val cx = child.translationX + childW / 2f
            val cy = child.translationY + childH / 2f
            val baseRadius = (minOf(childW, childH) / 2f) * child.scaleX.coerceAtLeast(1f)
            val hue = memberHues.getOrNull(index) ?: paper

            if (isCompanion) {
                val haloRadius = baseRadius * 1.24f
                auraFillPaint.color = ColorUtils.setAlphaComponent(hue, 56)
                companionRingPaint.color = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(hue, paper, 0.50f),
                    224
                )
                canvas.drawCircle(cx, cy, haloRadius, auraFillPaint)
                canvas.drawCircle(cx, cy, haloRadius, companionRingPaint)

                // Top-right luminous beacon dot marks the active companion unmistakably.
                val angle = -PI / 4.0
                val dotX = cx + (haloRadius * cos(angle)).toFloat()
                val dotY = cy + (haloRadius * sin(angle)).toFloat()
                companionDotPaint.color = paper
                canvas.drawCircle(dotX, dotY, companionDotRadiusPx, companionDotPaint)
            } else if (isAwakened && !isSelected) {
                val auraRadius = baseRadius * 1.14f
                auraFillPaint.color = ColorUtils.setAlphaComponent(hue, 32)
                auraStrokePaint.color = ColorUtils.setAlphaComponent(hue, 118)
                canvas.drawCircle(cx, cy, auraRadius, auraFillPaint)
                canvas.drawCircle(cx, cy, auraRadius, auraStrokePaint)
            }

            if (isSelected) {
                val selRadius = baseRadius * 1.18f
                canvas.drawCircle(cx, cy, selRadius, selectedFillPaint)
                canvas.drawCircle(cx, cy, selRadius, selectedStrokePaint)
            }
        }
        super.dispatchDraw(canvas)
    }

    private fun handleOrbiterTap(x: Float, y: Float) {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val hitRadius = memberSizePx.coerceAtLeast(48) * 0.9f
        var nearestIndex = -1
        var nearestDist = Float.MAX_VALUE
        orbiters.forEachIndexed { index, child ->
            val mx = child.translationX + child.width / 2f
            val my = child.translationY + child.height / 2f
            val dist = hypot(x - mx, y - my)
            if (dist < nearestDist) {
                nearestDist = dist
                nearestIndex = index
            }
        }

        if (nearestIndex >= 0 && nearestDist <= hitRadius) {
            val nextIndex = if (selectedIndex == nearestIndex) null else nearestIndex
            setSelectedSlot(nextIndex, animateToTop = nextIndex != null)
            onOrbiterSelected?.invoke(nextIndex)
        } else if (selectedIndex != null) {
            setSelectedSlot(null, animateToTop = false)
            onOrbiterSelected?.invoke(null)
        } else if (AliveMotion.shouldAnimate(this)) {
            // Subtle tap nudge cues the user that the orbit wheel responds to touch.
            val startAngle = orbitAngleRad
            val endAngle = startAngle + 0.18f
            flingAnimator?.cancel()
            flingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                interpolator = orbitEase
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    angularVelocityRad = (1f - f) * 1.4f
                    updateOrbitWithHaptics(startAngle + (endAngle - startAngle) * f)
                }
                start()
            }
        }
    }

    private fun animateOrbitTo(targetAngleRad: Float) {
        flingAnimator?.cancel()
        val startAngle = orbitAngleRad
        val animated = AliveMotion.shouldAnimate(this)
        if (!animated) {
            setOrbitAngleRadians(targetAngleRad, 0f)
            return
        }
        flingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = orbitEase
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                angularVelocityRad = (1f - f) * 0.8f
                updateOrbitWithHaptics(startAngle + (targetAngleRad - startAngle) * f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    angularVelocityRad = 0f
                    applyOrbiterTransforms()
                }
            })
            start()
        }
    }

    private fun startFling() {
        if (!AliveMotion.shouldAnimate(this)) {
            settleUpright()
            return
        }
        flingAnimator?.cancel()
        var lastFrameNs = System.nanoTime()
        flingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = 0
            addUpdateListener { anim ->
                val nowNs = System.nanoTime()
                val dtSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNs = nowNs

                val (nextAngle, nextVel) = GalaxyOrbitPhysics.stepInertia(
                    orbitAngleRad,
                    angularVelocityRad,
                    dtSec
                )
                angularVelocityRad = nextVel
                updateOrbitWithHaptics(nextAngle)

                if (nextVel == 0f) {
                    anim.cancel()
                }
            }
            start()
        }
    }

    private fun settleUpright() {
        val startRot = GalaxyOrbitPhysics.memberRotationDeg(angularVelocityRad)
        angularVelocityRad = 0f
        if (abs(startRot) < 0.2f || !AliveMotion.shouldAnimate(this)) {
            applyOrbiterTransforms()
            return
        }
        orbiters.forEach { child ->
            child.animate().cancel()
            child.animate().rotation(0f).setDuration(180L).setInterpolator(orbitEase).start()
        }
    }

    private fun playEntrySwirl() {
        if (!AliveMotion.shouldAnimate(this)) {
            orbitAngleRad = 0f
            angularVelocityRad = 0f
            resetOrbiterScales()
            applyOrbiterTransforms()
            return
        }
        val startOffsetRad = -0.40f
        setOrbitAngleRadians(startOffsetRad, 1.8f)
        orbiters.forEachIndexed { index, child ->
            val targetAlpha = if (index == selectedIndex) 1f else baseAlphaOf(index)
            val targetScale = if (index == selectedIndex) SELECTED_SCALE else 1f
            child.alpha = 0f
            child.scaleX = 0.62f
            child.scaleY = 0.62f
            child.animate().cancel()
            child.animate()
                .alpha(targetAlpha)
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setStartDelay(index * 12L)
                .setDuration(280L)
                .setInterpolator(orbitEase)
                .start()
        }
        entryAnimator?.cancel()
        entryAnimator = ValueAnimator.ofFloat(startOffsetRad, 0f).apply {
            duration = 560L
            interpolator = orbitEase
            addUpdateListener { anim ->
                val angle = anim.animatedValue as Float
                val frac = anim.animatedFraction
                angularVelocityRad = (1f - frac) * 1.8f
                setOrbitAngleRadians(angle, angularVelocityRad)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    angularVelocityRad = 0f
                    applyOrbiterTransforms()
                }
            })
            start()
        }
    }

    private fun updateOrbitWithHaptics(newAngleRad: Float) {
        val count = orbiters.size
        val nextSlot = GalaxyOrbitPhysics.slotIndexForAngle(newAngleRad, count)
        if (count > 0 && nextSlot != lastNotchSlot) {
            lastNotchSlot = nextSlot
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        orbitAngleRad = newAngleRad
        applyOrbiterTransforms()
    }

    private fun applyOrbiterTransforms() {
        val count = orbiters.size
        if (count == 0) return
        val w = width.takeIf { it > 0 } ?: layoutParams?.width ?: return
        val h = height.takeIf { it > 0 } ?: layoutParams?.height ?: return
        val effectiveMemberSize = memberSizePx.takeIf { it > 0 } ?: orbiters[0].measuredWidth
        val radius = ((minOf(w, h) - effectiveMemberSize) / 2f).coerceAtLeast(0f)
        val cx = w / 2f
        val cy = h / 2f
        val sway = GalaxyOrbitPhysics.memberRotationDeg(angularVelocityRad)

        orbiters.forEachIndexed { index, child ->
            val theta = -PI / 2.0 + orbitAngleRad + index * 2.0 * PI / count
            val childW = child.measuredWidth.takeIf { it > 0 } ?: effectiveMemberSize
            val childH = child.measuredHeight.takeIf { it > 0 } ?: effectiveMemberSize
            child.translationX = cx + (radius * cos(theta)).toFloat() - childW / 2f
            child.translationY = cy + (radius * sin(theta)).toFloat() - childH / 2f
            child.rotation = sway
        }
        if (selectedIndex != null || activeCompanionIndex != null || awakenedFlags.any { it }) {
            invalidate()
        }
    }

    private fun resetOrbiterScales() {
        orbiters.forEachIndexed { index, child ->
            child.animate().cancel()
            val selected = index == selectedIndex
            child.alpha = if (selected) 1f else baseAlphaOf(index)
            val targetScale = if (selected) SELECTED_SCALE else 1f
            child.scaleX = targetScale
            child.scaleY = targetScale
        }
        invalidate()
    }

    private fun baseAlphaOf(index: Int): Float =
        baseAlphas.getOrNull(index) ?: 1f

    private fun normalizeRadians(rad: Float): Float {
        val twoPi = (2.0 * PI).toFloat()
        var r = rad % twoPi
        if (r > PI.toFloat()) r -= twoPi
        if (r <= -PI.toFloat()) r += twoPi
        return r
    }

    private fun cancelAnimators() {
        flingAnimator?.cancel()
        flingAnimator = null
        entryAnimator?.cancel()
        entryAnimator = null
    }

    companion object {
        const val SELECTED_SCALE = 1.24f
    }
}
