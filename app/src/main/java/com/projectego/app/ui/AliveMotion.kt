package com.projectego.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.annotation.ColorInt
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Cohesive liquid-spring motion choreography for tabs, detail overlays, modal sheets, and card stacks.
 */
object AliveMotion {

    private const val TAB_IN_MS = 280L
    private const val TAB_OUT_MS = 180L
    private const val OVERLAY_IN_MS = 280L
    private const val OVERLAY_OUT_MS = 190L
    private const val SHEET_IN_MS = 300L
    private const val SHEET_OUT_MS = 180L
    private const val CARD_ENTER_MS = 280L
    private const val DEFAULT_MAX_CASCADE = 10

    private val liquidEase = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    private val exitEase = PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
    private val cascadeSpring = OvershootInterpolator(1.05f)
    private val sheetSpring = OvershootInterpolator(0.85f)

    private var tokenSeq = 0L
    private val viewTokens = WeakHashMap<View, Long>()

    /** Returns true only when [view] is attached to a window and system animators are enabled. */
    fun shouldAnimate(view: View): Boolean {
        if (!view.isAttachedToWindow) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    /**
     * Collects visible, non-spacer content views under [root] by unwrapping outer ScrollView and
     * single-child vertical wrapper LinearLayouts.
     */
    fun collectCascadeTargets(root: View, maxItems: Int = DEFAULT_MAX_CASCADE): List<View> {
        if (maxItems <= 0) return emptyList()
        val container = unwrapScreenContainer(root)
        val group = container as? ViewGroup
            ?: return if (isSpacerView(container)) emptyList() else listOf(container)
        val result = ArrayList<View>(maxItems.coerceAtMost(group.childCount.coerceAtLeast(1)))
        for (i in 0 until group.childCount) {
            if (result.size >= maxItems) break
            val child = group.getChildAt(i)
            if (!isSpacerView(child)) {
                result.add(child)
            }
        }
        return result
    }

    /** Animates direct content items under [root] with a soft upward spring slide and fade cascade. */
    fun cascadeCards(
        root: View,
        baseDelayMs: Long = 28L,
        staggerMs: Long = 34L,
        maxItems: Int = DEFAULT_MAX_CASCADE
    ) {
        val targets = collectCascadeTargets(root, maxItems)
        if (targets.isEmpty()) return
        if (!shouldAnimate(root)) {
            targets.forEach(::resetTransforms)
            return
        }
        val liftPx = dp(root.context, 20f)
        targets.forEachIndexed { index, child ->
            bumpToken(child)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = liftPx
            child.scaleX = 0.97f
            child.scaleY = 0.97f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(baseDelayMs + index * staggerMs)
                .setDuration(CARD_ENTER_MS)
                .setInterpolator(cascadeSpring)
                .start()
        }
    }

    /** Reveals [tab] cleanly (cancelling any stale transforms) and cascades its cards. */
    fun revealTab(tab: View, baseDelayMs: Long = 36L) {
        bumpToken(tab)
        tab.animate().cancel()
        tab.visibility = View.VISIBLE
        resetTransforms(tab)
        cascadeCards(tab, baseDelayMs = baseDelayMs, staggerMs = 34L, maxItems = DEFAULT_MAX_CASCADE)
    }

    /**
     * Transitions between [outgoing] and [incoming] tab pages with directional liquid spring motion
     * matching the navbar water drop travel ([direction]: +1 right, -1 left, 0 instant).
     * Never calls bringToFront() so FrameLayout sibling Z-order and overlay layering stay intact.
     */
    fun transitionTabs(
        outgoing: View?,
        incoming: View,
        direction: Int,
        onBeforeShow: (() -> Unit)? = null
    ) {
        val outToken = outgoing?.let { bumpToken(it) }
        val inToken = bumpToken(incoming)
        outgoing?.animate()?.cancel()
        incoming.animate().cancel()

        onBeforeShow?.invoke()

        if (outgoing === incoming || direction == 0 || !shouldAnimate(incoming)) {
            if (outgoing != null && outgoing !== incoming) {
                outgoing.visibility = View.GONE
                resetTransforms(outgoing)
            }
            incoming.visibility = View.VISIBLE
            resetTransforms(incoming)
            return
        }

        val slidePx = dp(incoming.context, 34f)
        val dir = if (direction > 0) 1f else -1f

        if (outgoing != null) {
            outgoing.visibility = View.VISIBLE
            outgoing.translationZ = 0f
            outgoing.animate()
                .alpha(0f)
                .translationX(-dir * slidePx * 0.45f)
                .scaleX(0.965f)
                .scaleY(0.965f)
                .setDuration(TAB_OUT_MS)
                .setInterpolator(exitEase)
                .withEndAction {
                    if (viewTokens[outgoing] == outToken) {
                        outgoing.visibility = View.GONE
                        resetTransforms(outgoing)
                    }
                }
                .start()
        }

        incoming.visibility = View.VISIBLE
        incoming.translationZ = 1f
        incoming.alpha = 0f
        incoming.translationX = dir * slidePx
        incoming.translationY = 0f
        incoming.scaleX = 0.97f
        incoming.scaleY = 0.97f
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(TAB_IN_MS)
            .setInterpolator(liquidEase)
            .withEndAction {
                if (viewTokens[incoming] == inToken) {
                    resetTransforms(incoming)
                }
            }
            .start()

        cascadeCards(incoming, baseDelayMs = 45L, staggerMs = 34L, maxItems = DEFAULT_MAX_CASCADE)
    }

    /**
     * Enters a detail overlay cleanly edge-to-edge (fading the container backdrop without shrinking
     * its bounds, while gliding its inner content upward and coordinating [navBar]).
     */
    fun enterOverlay(
        overlayContainer: View,
        navBar: View?,
        keepNav: Boolean
    ) {
        val token = bumpToken(overlayContainer)
        val navToken = navBar?.let { bumpToken(it) }
        val inner = (overlayContainer as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0)
        overlayContainer.animate().cancel()
        inner?.animate()?.cancel()
        navBar?.animate()?.cancel()

        resetTransforms(overlayContainer)
        inner?.let(::resetTransforms)

        if (!shouldAnimate(overlayContainer)) {
            overlayContainer.visibility = View.VISIBLE
            if (navBar != null) {
                navBar.visibility = if (keepNav) View.VISIBLE else View.GONE
                resetTransforms(navBar)
            }
            return
        }

        if (navBar != null) {
            if (keepNav) {
                navBar.visibility = View.VISIBLE
                navBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(OVERLAY_IN_MS)
                    .setInterpolator(liquidEase)
                    .withEndAction {
                        if (viewTokens[navBar] == navToken) {
                            resetTransforms(navBar)
                        }
                    }
                    .start()
            } else if (navBar.visibility == View.VISIBLE) {
                val hideOffset = navBar.height.takeIf { it > 0 }?.toFloat() ?: dp(navBar.context, 84f)
                navBar.animate()
                    .alpha(0f)
                    .translationY(hideOffset)
                    .setDuration(OVERLAY_OUT_MS)
                    .setInterpolator(exitEase)
                    .withEndAction {
                        if (viewTokens[navBar] == navToken) {
                            navBar.visibility = View.GONE
                            resetTransforms(navBar)
                        }
                    }
                    .start()
            }
        }

        overlayContainer.visibility = View.VISIBLE
        overlayContainer.alpha = 0f
        overlayContainer.animate()
            .alpha(1f)
            .setDuration(OVERLAY_IN_MS)
            .setInterpolator(liquidEase)
            .withEndAction {
                if (viewTokens[overlayContainer] == token) {
                    resetTransforms(overlayContainer)
                }
            }
            .start()

        if (inner != null) {
            inner.translationY = dp(overlayContainer.context, 22f)
            inner.scaleX = 0.98f
            inner.scaleY = 0.98f
            inner.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(OVERLAY_IN_MS)
                .setInterpolator(liquidEase)
                .withEndAction {
                    if (viewTokens[overlayContainer] == token) {
                        resetTransforms(inner)
                    }
                }
                .start()
        }

        cascadeCards(overlayContainer, baseDelayMs = 45L, staggerMs = 34L, maxItems = DEFAULT_MAX_CASCADE)
    }

    /** Exits [overlayContainer], optionally restoring [navBar], then invokes [onComplete]. */
    fun exitOverlay(
        overlayContainer: View,
        navBar: View?,
        shouldRestoreNav: Boolean = true,
        onComplete: () -> Unit
    ) {
        val token = bumpToken(overlayContainer)
        val navToken = navBar?.let { bumpToken(it) }
        val inner = (overlayContainer as? ViewGroup)?.takeIf { it.childCount > 0 }?.getChildAt(0)
        overlayContainer.animate().cancel()
        inner?.animate()?.cancel()
        navBar?.animate()?.cancel()

        if (!shouldAnimate(overlayContainer)) {
            overlayContainer.visibility = View.GONE
            resetTransforms(overlayContainer)
            inner?.let(::resetTransforms)
            if (shouldRestoreNav && navBar != null) {
                navBar.visibility = View.VISIBLE
                resetTransforms(navBar)
            }
            onComplete()
            return
        }

        if (shouldRestoreNav && navBar != null) {
            val needsRestore = navBar.visibility != View.VISIBLE ||
                navBar.alpha < 0.99f ||
                navBar.translationY != 0f
            if (needsRestore) {
                if (navBar.visibility != View.VISIBLE) {
                    navBar.visibility = View.VISIBLE
                    navBar.alpha = 0f
                    navBar.translationY = dp(navBar.context, 56f)
                }
                navBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(OVERLAY_IN_MS)
                    .setInterpolator(liquidEase)
                    .withEndAction {
                        if (viewTokens[navBar] == navToken) {
                            resetTransforms(navBar)
                        }
                    }
                    .start()
            }
        }

        if (inner != null) {
            inner.animate()
                .translationY(dp(overlayContainer.context, 18f))
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(OVERLAY_OUT_MS)
                .setInterpolator(exitEase)
                .start()
        }

        overlayContainer.animate()
            .alpha(0f)
            .setDuration(OVERLAY_OUT_MS)
            .setInterpolator(exitEase)
            .withEndAction {
                if (viewTokens[overlayContainer] == token) {
                    overlayContainer.visibility = View.GONE
                    resetTransforms(overlayContainer)
                    inner?.let(::resetTransforms)
                    onComplete()
                }
            }
            .start()
    }

    /** Springs a bottom-sheet modal into view (scrim fade + bottom sheet lift). */
    fun enterSheet(sheetContainer: View) {
        val token = bumpToken(sheetContainer)
        sheetContainer.animate().cancel()

        val (scrim, card) = resolveSheetParts(sheetContainer)
        scrim?.animate()?.cancel()
        card?.animate()?.cancel()

        sheetContainer.visibility = View.VISIBLE
        resetTransforms(sheetContainer)

        if (!shouldAnimate(sheetContainer)) {
            scrim?.let(::resetTransforms)
            card?.let(::resetTransforms)
            return
        }

        if (scrim != null) {
            scrim.alpha = 0f
            scrim.animate()
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(liquidEase)
                .start()
        }
        val target = card ?: scrim ?: sheetContainer
        target.alpha = 0f
        target.translationY = dp(sheetContainer.context, 56f)
        target.scaleX = 0.965f
        target.scaleY = 0.965f
        target.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SHEET_IN_MS)
            .setInterpolator(sheetSpring)
            .withEndAction {
                if (viewTokens[sheetContainer] == token) {
                    resetTransforms(target)
                }
            }
            .start()
    }

    /** Dismisses [sheetContainer] smoothly and invokes [onComplete] once finished. */
    fun exitSheet(sheetContainer: View, onComplete: () -> Unit) {
        val token = bumpToken(sheetContainer)
        sheetContainer.animate().cancel()

        val (scrim, card) = resolveSheetParts(sheetContainer)
        scrim?.animate()?.cancel()
        card?.animate()?.cancel()

        if (!shouldAnimate(sheetContainer)) {
            sheetContainer.visibility = View.GONE
            resetTransforms(sheetContainer)
            scrim?.let(::resetTransforms)
            card?.let(::resetTransforms)
            onComplete()
            return
        }

        if (scrim != null && scrim !== card) {
            scrim.animate()
                .alpha(0f)
                .setDuration(SHEET_OUT_MS)
                .setInterpolator(exitEase)
                .start()
        }
        val target = card ?: scrim ?: sheetContainer
        target.animate()
            .alpha(0f)
            .translationY(dp(sheetContainer.context, 52f))
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(SHEET_OUT_MS)
            .setInterpolator(exitEase)
            .withEndAction {
                if (viewTokens[sheetContainer] == token) {
                    sheetContainer.visibility = View.GONE
                    resetTransforms(sheetContainer)
                    scrim?.let(::resetTransforms)
                    card?.let(::resetTransforms)
                    onComplete()
                }
            }
            .start()
    }

    private fun unwrapScreenContainer(root: View): View {
        var current = root
        if (current is FrameLayout && current.childCount == 1 && current.getChildAt(0) is ViewGroup) {
            current = current.getChildAt(0)
        }
        while (true) {
            current = when {
                current is ScrollView && current.childCount == 1 -> current.getChildAt(0)
                current is LinearLayout &&
                    current.orientation == LinearLayout.VERTICAL &&
                    current.background == null &&
                    current.childCount == 1 -> {
                    val sole = current.getChildAt(0)
                    if (sole is LinearLayout &&
                        sole.orientation == LinearLayout.VERTICAL &&
                        sole.background == null
                    ) {
                        sole
                    } else {
                        break
                    }
                }
                else -> break
            }
        }
        return current
    }

    private fun resolveSheetParts(sheetContainer: View): Pair<View?, View?> {
        val group = sheetContainer as? ViewGroup ?: return null to null
        val scrim = group.takeIf { it.childCount > 0 }?.getChildAt(0)
        val scrimGroup = scrim as? ViewGroup
        val card = scrimGroup?.takeIf { it.childCount > 0 }?.let { it.getChildAt(it.childCount - 1) }
        return scrim to card
    }

    private fun isSpacerView(view: View): Boolean {
        if (view.visibility != View.VISIBLE) return true
        if (view is Space) return true
        if (view.javaClass == View::class.java && view.background == null) return true
        val lp = view.layoutParams
        if (lp != null && lp.height == 0) {
            val weight = (lp as? LinearLayout.LayoutParams)?.weight ?: 0f
            if (weight == 0f) return true
        }
        return false
    }

    private fun resetTransforms(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.translationZ = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private val pressSpring = OvershootInterpolator(2.0f)
    private val pillAnimators = WeakHashMap<View, ValueAnimator>()
    private val heightAnimators = WeakHashMap<View, ValueAnimator>()
    private val exitingViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val breathingAnimators = WeakHashMap<View, ValueAnimator>()
    private val breathingListeners = WeakHashMap<View, View.OnAttachStateChangeListener>()

    /**
     * Attaches non-consuming touch press-and-release spring physics onto clickable
     * pills, tiles, and CTA buttons so clicks trigger natively alongside haptic feedback.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachPressSpring(
        view: View,
        pressedScale: Float = 0.95f,
        hapticOnDown: Boolean = false
    ) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (hapticOnDown) {
                        target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    if (shouldAnimate(target)) {
                        target.animate().cancel()
                        target.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .setDuration(90L)
                            .setInterpolator(liquidEase)
                            .start()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    target.animate().cancel()
                    if (shouldAnimate(target)) {
                        target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(210L)
                            .setInterpolator(pressSpring)
                            .start()
                    } else {
                        target.scaleX = 1f
                        target.scaleY = 1f
                    }
                }
            }
            false
        }
    }

    /** Crisp two-step bounce used when selecting pills, updating totals, or extending time. */
    fun pulsePop(view: View, dipScale: Float = 0.91f, durationMs: Long = 220L) {
        if (!shouldAnimate(view) || !view.isAttachedToWindow) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val token = bumpToken(view)
        view.animate().cancel()
        view.scaleX = dipScale
        view.scaleY = dipScale
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(pressSpring)
            .withEndAction {
                if (viewTokens[view] == token) {
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
            }
            .start()
    }

    /**
     * Smoothly transitions a pill's rounded fill and label colour between states when attached,
     * falling back to an immediate synchronous write when detached or animators are disabled.
     */
    fun morphPillColors(
        pill: TextView,
        @ColorInt targetBg: Int,
        @ColorInt targetFg: Int,
        durationMs: Long = 210L
    ) {
        pillAnimators.remove(pill)?.cancel()
        val drawable = pill.background as? GradientDrawable
        if (!pill.isAttachedToWindow || !shouldAnimate(pill) || drawable == null) {
            drawable?.setColor(targetBg)
            pill.setTextColor(targetFg)
            return
        }
        val startBg = drawable.color?.defaultColor ?: targetBg
        val startFg = pill.currentTextColor
        if (startBg == targetBg && startFg == targetFg) {
            drawable.setColor(targetBg)
            pill.setTextColor(targetFg)
            return
        }
        val evaluator = ArgbEvaluator()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = liquidEase
            addUpdateListener { anim ->
                val frac = anim.animatedValue as Float
                drawable.setColor(evaluator.evaluate(frac, startBg, targetBg) as Int)
                pill.setTextColor(evaluator.evaluate(frac, startFg, targetFg) as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    drawable.setColor(targetBg)
                    pill.setTextColor(targetFg)
                    pillAnimators.remove(pill)
                }

                override fun onAnimationCancel(animation: Animator) {
                    drawable.setColor(targetBg)
                    pill.setTextColor(targetFg)
                    pillAnimators.remove(pill)
                }
            })
        }
        pillAnimators[pill] = animator
        animator.start()
    }

    /**
     * Glides rebuilt body content in along [direction] (+1 forward / right-to-left,
     * -1 backward / left-to-right) and cascades cards cleanly inside.
     */
    fun transitionDirectionalSwap(
        container: View,
        direction: Int,
        distanceDp: Float = 24f,
        durationMs: Long = 240L
    ) {
        val token = bumpToken(container)
        container.animate().cancel()
        if (!container.isAttachedToWindow || !shouldAnimate(container) || direction == 0) {
            resetTransforms(container)
            return
        }
        val offsetPx = direction.coerceIn(-1, 1) * dp(container.context, distanceDp)
        container.alpha = 0f
        container.translationX = offsetPx
        container.scaleX = 0.985f
        container.scaleY = 0.985f
        container.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(liquidEase)
            .withEndAction {
                if (viewTokens[container] == token) {
                    resetTransforms(container)
                }
            }
            .start()
        cascadeCards(container, baseDelayMs = 12L, staggerMs = 34L)
    }

    /** Staggers non-spacer direct children inside a card (e.g. app usage rows or picker options). */
    fun cascadeChildren(
        parent: ViewGroup,
        startIndex: Int = 0,
        baseDelayMs: Long = 36L,
        staggerMs: Long = 26L,
        maxItems: Int = DEFAULT_MAX_CASCADE
    ) {
        val children = (startIndex.coerceAtLeast(0) until parent.childCount)
            .map { parent.getChildAt(it) }
            .filterNot { isSpacerView(it) }
            .take(maxItems)
        if (children.isEmpty()) return

        if (!shouldAnimate(parent)) {
            children.forEach { resetTransforms(it) }
            return
        }
        val shift = dp(parent.context, 12f)
        children.forEachIndexed { idx, child ->
            val token = bumpToken(child)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = shift
            child.scaleX = 0.97f
            child.scaleY = 0.97f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(baseDelayMs + idx * staggerMs)
                .setDuration(220L)
                .setInterpolator(liquidEase)
                .withEndAction {
                    if (viewTokens[child] == token) {
                        resetTransforms(child)
                    }
                }
                .start()
        }
    }

    /**
     * Gentle breathing scale loop while attached (e.g. active Focus session mascot),
     * automatically cancelled and reset on detach.
     */
    fun startBreathing(view: View, maxScale: Float = 1.05f, periodMs: Long = 2800L) {
        breathingListeners.remove(view)?.let { view.removeOnAttachStateChangeListener(it) }
        breathingAnimators.remove(view)?.cancel()

        fun launchIfReady() {
            breathingAnimators.remove(view)?.cancel()
            if (!view.isAttachedToWindow || !shouldAnimate(view)) {
                view.scaleX = 1f
                view.scaleY = 1f
                return
            }
            val animator = ValueAnimator.ofFloat(1f, maxScale).apply {
                duration = periodMs / 2L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = PathInterpolator(0.45f, 0f, 0.55f, 1f)
                addUpdateListener { anim ->
                    val s = anim.animatedValue as Float
                    view.scaleX = s
                    view.scaleY = s
                }
            }
            breathingAnimators[view] = animator
            animator.start()
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                launchIfReady()
            }

            override fun onViewDetachedFromWindow(v: View) {
                breathingAnimators.remove(v)?.cancel()
                v.scaleX = 1f
                v.scaleY = 1f
            }
        }
        breathingListeners[view] = listener
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) {
            launchIfReady()
        }
    }

    /**
     * Cross-slides a dynamic stage container's child along [direction] (+1 right-to-left,
     * -1 left-to-right) without tearing down or blinking the surrounding card container.
     */
    fun swapStageChild(
        stage: FrameLayout,
        incoming: View,
        direction: Int,
        distanceDp: Float = 26f,
        inDurationMs: Long = 240L,
        outDurationMs: Long = 160L
    ) {
        val stageToken = bumpToken(stage)
        val existing = (0 until stage.childCount).map { stage.getChildAt(it) }
        val previous = existing.lastOrNull { it !in exitingViews && it !== incoming }
        existing.forEach { child ->
            if (child !== previous && child !== incoming) {
                child.animate().cancel()
                exitingViews.remove(child)
                stage.removeView(child)
            }
        }

        if (!stage.isAttachedToWindow || !shouldAnimate(stage) || direction == 0 || previous == null) {
            if (previous != null) {
                previous.animate().cancel()
                exitingViews.remove(previous)
                stage.removeView(previous)
            }
            resetTransforms(incoming)
            if (incoming.parent !== stage) {
                stage.addView(incoming)
            }
            return
        }

        val offsetPx = direction.coerceIn(-1, 1) * dp(stage.context, distanceDp)
        exitingViews.add(previous)
        previous.animate().cancel()
        previous.animate()
            .alpha(0f)
            .translationX(-offsetPx * 0.85f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(outDurationMs)
            .setInterpolator(exitEase)
            .withEndAction {
                exitingViews.remove(previous)
                resetTransforms(previous)
                if (previous.parent === stage) {
                    stage.removeView(previous)
                }
            }
            .start()

        exitingViews.remove(incoming)
        if (incoming.parent !== stage) {
            stage.addView(incoming)
        }
        incoming.animate().cancel()
        incoming.alpha = 0.08f
        incoming.translationX = offsetPx
        incoming.translationY = 0f
        incoming.scaleX = 0.985f
        incoming.scaleY = 0.985f
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(inDurationMs)
            .setInterpolator(liquidEase)
            .withEndAction {
                if (viewTokens[stage] == stageToken) {
                    resetTransforms(incoming)
                }
            }
            .start()
    }

    /**
     * Smoothly morphs [view]'s height from its current measured height to [targetHeight],
     * restoring `WRAP_CONTENT` cleanly at completion.
     */
    fun animateHeightTo(
        view: View,
        targetHeight: Int,
        durationMs: Long = 240L
    ) {
        heightAnimators.remove(view)?.cancel()
        val lp = view.layoutParams ?: return
        val startHeight = view.height
        if (!view.isAttachedToWindow || !shouldAnimate(view) || startHeight <= 0 || targetHeight <= 0 ||
            abs(targetHeight - startHeight) <= 2
        ) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams = lp
            return
        }
        val token = bumpToken(view)
        val animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = durationMs
            interpolator = liquidEase
            addUpdateListener { anim ->
                val cur = view.layoutParams ?: return@addUpdateListener
                cur.height = anim.animatedValue as Int
                view.layoutParams = cur
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heightAnimators.remove(view)
                    if (viewTokens[view] == token) {
                        val cur = view.layoutParams ?: return
                        cur.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        view.layoutParams = cur
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    heightAnimators.remove(view)
                    if (viewTokens[view] == token) {
                        val cur = view.layoutParams ?: return
                        cur.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        view.layoutParams = cur
                    }
                }
            })
        }
        heightAnimators[view] = animator
        animator.start()
    }

    private fun bumpToken(view: View): Long {
        val next = ++tokenSeq
        viewTokens[view] = next
        return next
    }

    private fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
