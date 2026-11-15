package com.projectego.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.projectego.app.R
import kotlin.math.PI

/**
 * Crystal-clear Auralys / Apple-inspired Liquid Glass card container with:
 * - True optical edge refraction & chromatic aberration on API 33+ via
 *   [RenderEffect.createRuntimeShaderEffect] chained over a live GPU-blurred
 *   [RenderNode] backdrop (`uBackdrop`).
 * - Translucent crystal interior (low frosted mist + subtle studio directional
 *   fresnel highlight, letting background liquid waves shine straight through).
 * - Iridescent living ALIVE family prism dispersion woven delicately into the
 *   curved bevel rim + matched deterministic Canvas fallback for API 26–32 and
 *   Robolectric tests.
 */
class AliveLiquidGlassCard(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density
    private fun dpInt(value: Float): Int = (value * density + 0.5f).toInt()

    private val cardRadiusPx =
        context.resources.getDimensionPixelSize(R.dimen.radius_card).toFloat()

    /**
     * Closed-loop ALIVE family prism palette used for bevel chromatic dispersion
     * and fallback edge caustics.
     */
    private val prismColors = intArrayOf(
        ContextCompat.getColor(context, R.color.alive_fam_coding),
        ContextCompat.getColor(context, R.color.alive_fam_creative),
        ContextCompat.getColor(context, R.color.alive_fam_writing),
        ContextCompat.getColor(context, R.color.alive_fam_study),
        ContextCompat.getColor(context, R.color.alive_fam_walking),
        ContextCompat.getColor(context, R.color.alive_fam_exercise),
        ContextCompat.getColor(context, R.color.alive_fam_social),
        ContextCompat.getColor(context, R.color.alive_fam_reading),
        ContextCompat.getColor(context, R.color.alive_fam_coding)
    )

    private val prismStops = floatArrayOf(
        0.00f, 0.125f, 0.25f, 0.375f, 0.50f, 0.625f, 0.75f, 0.875f, 1.00f
    )

    private val cardRect = RectF()
    private val strokeRect = RectF()
    private val innerBevelRect = RectF()
    private val cardPath = Path()
    private val tmpRect = RectF()

    private var backdropPainter: ((Canvas, Int, Int) -> Unit)? = null
    private var backdropSibling: View? = null
    private var backdropOriginView: View? = null

    private var cachedGradW = -1f
    private var cachedGradH = -1f
    private var cachedGradBackdrop = false

    private val shadowAmbientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4405081E
    }
    private val shadowContactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x2605081E
    }
    private val crystalMistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val crystalDepthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val innerBevelDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(9f)
        color = 0x16091032
    }
    private val prismBevelCausticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5.0f)
        alpha = 112
    }
    private val prismRimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
        alpha = 195
    }
    private val specularRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.15f)
    }

    private var prismSweep: SweepGradient? = null
    private val sweepMatrix = Matrix()

    // Hardware optical refraction shader (API 33+)
    private var refractShader: Any? = null // android.graphics.RuntimeShader on API 33+
    private var refractInitAttempted = false
    private var blurEffectCache: Any? = null // android.graphics.RenderEffect on API 31+

    // Live GPU backdrop blur state (API 31+)
    private var backdropNode: Any? = null
    private var backdropBlurPadPx = 0f
    private var backdropRecW = 0
    private var backdropRecH = 0
    private var hasRecordedBackdrop = false
    private var hasRefractionInBackdrop = false
    private var isRecordingBackdrop = false
    private var observersAttached = false

    // Slow continuous living-prism phase [0, 2*PI)
    private var prismPhaseRad = 0f
    private var prismAnimator: ValueAnimator? = null

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isAttachedToWindow &&
            isHardwareAccelerated &&
            visibility == View.VISIBLE &&
            cardRect.width() > 0f &&
            cardRect.height() > 0f
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

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setPadding(dpInt(18f), dpInt(16f), dpInt(18f), dpInt(16f))
    }

    /**
     * Registers behind-card scenery sources (aurora ribbon painter + optional sibling view
     * such as [GalaxyOrbitView]) so API 31/33+ optical blur and lensing can refract live
     * waves and orbiting glyphs through the glass without recursing into self.
     */
    fun attachBackdropSources(
        originView: View,
        siblingView: View?,
        groundPainter: (Canvas, Int, Int) -> Unit
    ) {
        this.backdropOriginView = originView
        this.backdropSibling = siblingView
        this.backdropPainter = groundPainter
        hasRecordedBackdrop = false
        invalidate()
    }

    fun currentPhaseRadians(): Float = prismPhaseRad

    fun setPhaseForTesting(radians: Float) {
        prismPhaseRad = radians
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val outerMargin = dp(2.5f)
        cardRect.set(outerMargin, outerMargin, w - outerMargin, h - outerMargin)
        cardPath.reset()
        cardPath.addRoundRect(cardRect, cardRadiusPx, cardRadiusPx, Path.Direction.CW)

        strokeRect.set(cardRect)
        strokeRect.inset(dp(0.8f), dp(0.8f))

        innerBevelRect.set(cardRect)
        innerBevelRect.inset(dp(3.0f), dp(3.0f))

        cachedGradW = -1f
        hasRecordedBackdrop = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !observersAttached) {
            viewTreeObserver.addOnPreDrawListener(preDrawListener)
            viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
            observersAttached = true
        }
        startPrismLoopIfNeeded()
    }

    override fun onDetachedFromWindow() {
        if (observersAttached && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
        }
        observersAttached = false
        hasRecordedBackdrop = false
        stopPrismLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isShown) {
            startPrismLoopIfNeeded()
        } else {
            stopPrismLoop()
        }
    }

    private fun shouldRunPrismLoop(): Boolean =
        isAttachedToWindow && visibility == View.VISIBLE && AliveMotion.shouldAnimate(this)

    private fun startPrismLoopIfNeeded() {
        if (prismAnimator != null || !shouldRunPrismLoop()) return
        val startFraction = ((prismPhaseRad % TWO_PI) / TWO_PI).coerceIn(0f, 0.999f)
        prismAnimator = ValueAnimator.ofFloat(0f, TWO_PI).apply {
            duration = PRISM_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (!shouldRunPrismLoop()) {
                    stopPrismLoop()
                    return@addUpdateListener
                }
                prismPhaseRad = anim.animatedValue as Float
                invalidate()
            }
            if (startFraction > 0f) {
                currentPlayTime = (startFraction * PRISM_CYCLE_MS).toLong()
            }
            start()
        }
    }

    private fun stopPrismLoop() {
        prismAnimator?.cancel()
        prismAnimator = null
    }

    override fun draw(canvas: Canvas) {
        if (isRecordingBackdrop) return
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        if (cardRect.width() <= 0f || cardRect.height() <= 0f) {
            super.onDraw(canvas)
            return
        }

        ensureGradients()
        drawOuterShadow(canvas)

        val drewBackdrop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated &&
            hasRecordedBackdrop
        if (drewBackdrop) {
            drawBackdropApi31(canvas)
        }

        // Subtle crystal mist & depth floor (transparent when live backdrop blur is active,
        // richer translucent sapphire tint on software Canvas / API < 31).
        drawCrystalBody(canvas, hasLiveBackdrop = drewBackdrop)

        // When hardware optical refraction shader handles the bevel inside backdropNode, keep
        // only crisp outer specular polish; otherwise draw the full bevel + rotating ALIVE prism rim.
        if (!(drewBackdrop && hasRefractionInBackdrop)) {
            drawCrystalBevelAndPrismFallback(canvas)
        }

        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (cardRect.width() <= 0f || cardRect.height() <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(cardPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    private fun ensureGradients() {
        val w = cardRect.width()
        val h = cardRect.height()
        if (cachedGradW == w && cachedGradH == h && cachedGradBackdrop == hasRecordedBackdrop) return
        cachedGradW = w
        cachedGradH = h
        cachedGradBackdrop = hasRecordedBackdrop

        // Directional misty frost diagonal: soft pearlescent fog top-left -> crystal clear center/bottom-right
        val mistTopLeft = if (hasRecordedBackdrop) 0x36FFFFFF else 0x52C8D8FF
        val mistMid = if (hasRecordedBackdrop) 0x16E8F0FF else 0x362C3B88
        val mistBottomRight = if (hasRecordedBackdrop) 0x12FFFFFF else 0x281B2560
        crystalMistPaint.shader = LinearGradient(
            cardRect.left, cardRect.top,
            cardRect.right, cardRect.bottom,
            intArrayOf(mistTopLeft, mistMid, mistBottomRight),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )

        // Gentle vertical readability anchor (only thin sapphire depth haze, preserving transparency)
        val topAnchor = if (hasRecordedBackdrop) 0x18090F32 else 0x5C121944
        val midAnchor = if (hasRecordedBackdrop) 0x100B133D else 0x4E162159
        val botAnchor = if (hasRecordedBackdrop) 0x26080E2E else 0x6E0E1642
        crystalDepthPaint.shader = LinearGradient(
            0f, cardRect.top,
            0f, cardRect.bottom,
            intArrayOf(topAnchor, midAnchor, botAnchor),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )

        // Directional Fresnel studio white rim peaking at top-left and bottom-right curves
        specularRimPaint.shader = LinearGradient(
            cardRect.left, cardRect.top,
            cardRect.right, cardRect.bottom,
            intArrayOf(0xE8FFFFFF.toInt(), 0x48FFFFFF, 0x32FFFFFF, 0xCCFFFFFF.toInt()),
            floatArrayOf(0f, 0.34f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )

        prismSweep = SweepGradient(cardRect.centerX(), cardRect.centerY(), prismColors, prismStops)
    }

    private fun drawOuterShadow(canvas: Canvas) {
        canvas.save()
        canvas.clipOutPath(cardPath)
        tmpRect.set(cardRect)
        tmpRect.offset(0f, dp(7f))
        canvas.drawRoundRect(tmpRect, cardRadiusPx, cardRadiusPx, shadowAmbientPaint)

        tmpRect.set(cardRect)
        tmpRect.offset(0f, dp(2f))
        canvas.drawRoundRect(tmpRect, cardRadiusPx, cardRadiusPx, shadowContactPaint)
        canvas.restore()
    }

    private fun drawCrystalBody(canvas: Canvas, hasLiveBackdrop: Boolean) {
        canvas.save()
        canvas.clipPath(cardPath)
        canvas.drawRoundRect(cardRect, cardRadiusPx, cardRadiusPx, crystalDepthPaint)
        canvas.drawRoundRect(cardRect, cardRadiusPx, cardRadiusPx, crystalMistPaint)
        if (!hasLiveBackdrop) {
            canvas.drawRoundRect(cardRect, cardRadiusPx, cardRadiusPx, innerBevelDarkPaint)
        }
        canvas.restore()
    }

    private fun drawCrystalBevelAndPrismFallback(canvas: Canvas) {
        val sweep = prismSweep ?: return
        val phaseDeg = Math.toDegrees(prismPhaseRad.toDouble()).toFloat()
        sweepMatrix.reset()
        sweepMatrix.setRotate(phaseDeg, cardRect.centerX(), cardRect.centerY())
        sweep.setLocalMatrix(sweepMatrix)

        // Inner iridescent bevel dispersion just inside the curved lip
        canvas.save()
        canvas.clipPath(cardPath)
        prismBevelCausticPaint.shader = sweep
        prismBevelCausticPaint.alpha = 112
        val bevelR = (cardRadiusPx - dp(3.0f)).coerceAtLeast(1f)
        canvas.drawRoundRect(innerBevelRect, bevelR, bevelR, prismBevelCausticPaint)
        canvas.restore()

        // Thin living ALIVE chromatic rim + crisp studio white Fresnel edge overlay
        prismRimStrokePaint.shader = sweep
        prismRimStrokePaint.alpha = 195
        val rimR = (cardRadiusPx - dp(0.8f)).coerceAtLeast(1f)
        canvas.drawRoundRect(strokeRect, rimR, rimR, prismRimStrokePaint)
        canvas.drawRoundRect(strokeRect, rimR, rimR, specularRimPaint)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getOrInitRefractShader(): RuntimeShader? {
        if (!refractInitAttempted) {
            refractInitAttempted = true
            refractShader = try {
                RuntimeShader(LIQUID_GLASS_REFRACT_AGSL)
            } catch (_: Throwable) {
                null
            }
        }
        return refractShader as? RuntimeShader
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBackdropEffectsApi31(node: RenderNode) {
        val blur = (blurEffectCache as? RenderEffect) ?: run {
            val blurPx = dp(10f)
            val blurFX = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            val satFX = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.16f) })
            )
            RenderEffect.createChainEffect(satFX, blurFX).also { blurEffectCache = it }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = getOrInitRefractShader()
            if (shader != null && backdropRecW > 0 && backdropRecH > 0) {
                try {
                    shader.setFloatUniform(
                        "uCard",
                        backdropBlurPadPx + cardRect.width() * 0.5f,
                        backdropBlurPadPx + cardRect.height() * 0.5f,
                        cardRect.width() * 0.5f,
                        cardRect.height() * 0.5f
                    )
                    shader.setFloatUniform(
                        "uBounds",
                        cardRadiusPx,
                        density,
                        backdropRecW.toFloat(),
                        backdropRecH.toFloat()
                    )
                    shader.setFloatUniform("uMeta", prismPhaseRad, 0f, 0f, 0f)
                    val glassEffect = RenderEffect.createRuntimeShaderEffect(shader, "uBackdrop")
                    node.setRenderEffect(RenderEffect.createChainEffect(glassEffect, blur))
                    hasRefractionInBackdrop = true
                    return
                } catch (_: Throwable) {
                    hasRefractionInBackdrop = false
                }
            }
        }
        hasRefractionInBackdrop = false
        node.setRenderEffect(blur)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun recordBackdropApi31() {
        val origin = backdropOriginView ?: return
        val painter = backdropPainter ?: return
        if (isRecordingBackdrop || cardRect.width() <= 0f || cardRect.height() <= 0f) return
        if (origin.width <= 0 || origin.height <= 0) return

        val node = (backdropNode as? RenderNode)
            ?: RenderNode("AliveCrystalGlassBackdrop").also { backdropNode = it }

        val pad = dpInt(26f)
        backdropBlurPadPx = pad.toFloat()
        val recW = cardRect.width().toInt() + pad * 2
        val recH = cardRect.height().toInt() + pad * 2
        if (recW <= 0 || recH <= 0) return
        backdropRecW = recW
        backdropRecH = recH

        val selfLoc = IntArray(2)
        val originLoc = IntArray(2)
        getLocationInWindow(selfLoc)
        origin.getLocationInWindow(originLoc)
        val relX = (selfLoc[0] - originLoc[0]).toFloat()
        val relY = (selfLoc[1] - originLoc[1]).toFloat()

        isRecordingBackdrop = true
        try {
            node.setPosition(0, 0, recW, recH)
            val rc = node.beginRecording(recW, recH)
            try {
                rc.save()
                rc.translate(-(relX + cardRect.left - pad), -(relY + cardRect.top - pad))
                painter(rc, origin.width, origin.height)
                backdropSibling?.let { sibling ->
                    if (sibling.visibility == View.VISIBLE && sibling.width > 0 && sibling.height > 0) {
                        val sibLoc = IntArray(2)
                        sibling.getLocationInWindow(sibLoc)
                        rc.save()
                        rc.translate(
                            (sibLoc[0] - originLoc[0]).toFloat(),
                            (sibLoc[1] - originLoc[1]).toFloat()
                        )
                        sibling.draw(rc)
                        rc.restore()
                    }
                }
                rc.restore()
            } finally {
                node.endRecording()
            }
            applyBackdropEffectsApi31(node)
            if (!hasRecordedBackdrop) {
                hasRecordedBackdrop = true
                cachedGradW = -1f
            }
        } catch (_: Throwable) {
            hasRecordedBackdrop = false
            hasRefractionInBackdrop = false
        } finally {
            isRecordingBackdrop = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBackdropApi31(canvas: Canvas) {
        val node = backdropNode as? RenderNode ?: return
        if (!node.hasDisplayList()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasRefractionInBackdrop) {
            applyBackdropEffectsApi31(node)
        }
        canvas.save()
        canvas.clipPath(cardPath)
        canvas.translate(cardRect.left - backdropBlurPadPx, cardRect.top - backdropBlurPadPx)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    companion object {
        private const val TWO_PI = (2.0 * PI).toFloat()
        private const val PRISM_CYCLE_MS = 12_000L

        /**
         * API 33+ AGSL Liquid Glass optical lens shader sampling blurred `uBackdrop`:
         * - Bends & magnifies behind-card ripples along the 18dp curved edge bevel
         *   with per-channel chromatic aberration.
         * - Adds delicate top-left misty frost glaze while preserving ultra-clear center.
         * - Renders studio directional Fresnel white rim + iridescent ALIVE family prism
         *   caustics flowing gently along the beveled edge.
         */
        private const val LIQUID_GLASS_REFRACT_AGSL = """
            uniform shader uBackdrop;
            uniform float4 uCard;   // centerX, centerY, halfW, halfH
            uniform float4 uBounds; // radius, density, recW, recH
            uniform float4 uMeta;   // phaseRad, unused, unused, unused

            float sdRoundBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + float2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            float3 alivePrism(float t) {
                float3 c0 = float3(0.663, 0.604, 0.949); // Coding lilac
                float3 c1 = float3(0.961, 0.561, 0.651); // Creative blossom
                float3 c2 = float3(0.957, 0.694, 0.408); // Writing peach
                float3 c3 = float3(0.961, 0.808, 0.494); // Study butter
                float3 c4 = float3(0.718, 0.878, 0.420); // Walking lime
                float3 c5 = float3(0.388, 0.788, 0.541); // Exercise mint
                float3 c6 = float3(0.467, 0.788, 0.714); // Social aqua
                float3 c7 = float3(0.612, 0.788, 0.910); // Reading sky

                float x = fract(t) * 8.0;
                float idx = floor(x);
                float f = smoothstep(0.0, 1.0, fract(x));

                if (idx < 0.5) return mix(c0, c1, f);
                if (idx < 1.5) return mix(c1, c2, f);
                if (idx < 2.5) return mix(c2, c3, f);
                if (idx < 3.5) return mix(c3, c4, f);
                if (idx < 4.5) return mix(c4, c5, f);
                if (idx < 5.5) return mix(c5, c6, f);
                if (idx < 6.5) return mix(c6, c7, f);
                return mix(c7, c0, f);
            }

            float2 clampSample(float2 pt) {
                return clamp(pt, float2(1.0), uBounds.zw - float2(1.0));
            }

            half4 main(float2 pos) {
                float dpr = max(uBounds.y, 1.0);
                float radius = uBounds.x;
                float2 p = pos - uCard.xy;
                float d = sdRoundBox(p, uCard.zw, radius);

                if (d > 2.0 * dpr) {
                    return uBackdrop.eval(clampSample(pos));
                }

                // Outward edge normal via SDF gradient
                float eps = 1.4 * dpr;
                float dx = sdRoundBox(p + float2(eps, 0.0), uCard.zw, radius) - d;
                float dy = sdRoundBox(p + float2(0.0, eps), uCard.zw, radius) - d;
                float2 grad = float2(dx, dy) / max(length(float2(dx, dy)), 0.0001);

                // Thick curved optical glass bevel width (~20dp)
                float bevelW = 20.0 * dpr;
                float edgeRatio = clamp(1.0 + d / bevelW, 0.0, 1.0);
                float lensCurve = pow(edgeRatio, 2.0);

                // Optical lensing pull wrapping exterior ribbons along the curved edge
                float2 refractedPos = pos + grad * (lensCurve * 15.0 * dpr);
                float disp = lensCurve * 4.5 * dpr;

                half cr = uBackdrop.eval(clampSample(refractedPos + grad * disp)).r;
                half cg = uBackdrop.eval(clampSample(refractedPos)).g;
                half cb = uBackdrop.eval(clampSample(refractedPos - grad * disp)).b;
                float3 bgCol = float3(cr, cg, cb);

                // Subtle top-left misty frost veil transitioning cleanly to crystal bottom-right
                float diagMist = clamp(
                    0.5 - 0.35 * (p.x / max(uCard.z, 1.0) + p.y / max(uCard.w, 1.0)),
                    0.0,
                    1.0
                );
                bgCol = mix(bgCol, float3(0.84, 0.89, 0.99), diagMist * 0.16);

                // Delicate ALIVE palette optical caustics refracting inside the curved bevel lip
                float angle = atan(p.y, p.x);
                float3 prismCol = alivePrism((angle + uMeta.x) / 6.2831853 + 0.5);
                float bevelCaustic = smoothstep(-10.0 * dpr, -0.6 * dpr, d) * smoothstep(1.2 * dpr, -0.6 * dpr, d);
                bgCol = mix(bgCol, prismCol, bevelCaustic * 0.42);

                // Studio directional Fresnel highlight (top-left & bottom-right catch light)
                float fresnel = 0.30 + 0.70 * pow(abs(cos(angle + 0.7854)), 1.6);
                float outerHairline = smoothstep(-2.4 * dpr, -0.7 * dpr, d)
                                    * smoothstep(0.9 * dpr, -0.2 * dpr, d);
                float innerGlow = pow(edgeRatio, 4.2) * 0.18;

                bgCol += float3(1.0) * (outerHairline * (0.48 + 0.42 * fresnel) + innerGlow * fresnel);

                // Faint crisp rim chromatic tint modulation around exact lip
                float rimBand = smoothstep(-2.0 * dpr, -0.8 * dpr, d) * smoothstep(0.6 * dpr, -0.4 * dpr, d);
                bgCol = mix(bgCol, mix(prismCol, float3(1.0), 0.38 * fresnel), rimBand * 0.52);

                return half4(half3(clamp(bgCol, 0.0, 1.0)), half(1.0));
            }
        """
    }
}
