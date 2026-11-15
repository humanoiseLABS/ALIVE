package com.projectego.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projectego.app.R
import com.projectego.app.data.CharacterFamilyRepository.MemberStatus
import com.projectego.app.domain.CharacterGrowth
import com.projectego.app.domain.CompanionSynergy
import com.projectego.app.domain.GalaxyCatalog.GalaxyActionTarget
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberKind
import com.projectego.app.domain.GalaxyCatalog.GalaxyMemberSpec
import java.util.Locale

/**
 * Character Galaxy: interactive 17-member orbit wheel paired with a single
 * minimalist crystal-clear Auralys Liquid Glass card (OVERVIEW level summary
 * <-> inline character INSPECTOR) refracting sweeping fluid waves through a
 * living ALIVE prism bevel rim.
 */
object GalaxyScreen {

    data class Data(
        val statuses: List<MemberStatus>,
        val activeCompanion: GalaxyMemberSpec,
        val level: Int,
        val points: Int,
        val streak: Int,
        val missionsCompleted: Int,
        val focusMinutesToday: Long,
        val questCountedToday: Boolean,
        val focusCountedToday: Boolean,
        val promiseCountedToday: Boolean,
        val questDaysTotal: Int,
        val focusDaysTotal: Int,
        val promiseDaysTotal: Int,
        val initialSelectedId: String? = null,
        val onBack: () -> Unit,
        val onClose: () -> Unit,
        val onSelectCompanion: (GalaxyMemberSpec) -> Boolean,
        val onTriggerAction: (GalaxyActionTarget) -> Unit
    )

    private const val DORMANT_ALPHA = 0.78f
    private const val TEXT_ON_GLASS = 0xFFFFFFFF.toInt()
    private const val MUTED_ON_GLASS = 0xDDE8F2FF.toInt()
    private const val BODY_ON_GLASS = 0xF2F2F7FF.toInt()
    private const val GLASS_PRIMARY_PILL_FILL = 0xF2FFFFFF.toInt()
    private const val GLASS_PILL_FILL = 0x26FFFFFF
    private const val GLASS_PILL_STROKE = 0x76FFFFFF
    private const val GLASS_SUB_TILE_STROKE = 0x62FFFFFF
    private const val GLASS_METER_TRACK = 0x38FFFFFF
    private const val GLASS_METER_FILL = 0xF5FFFFFF.toInt()
    private const val GLASS_TEXT_SHADOW = 0x52040822

    fun build(context: Context, ui: AliveComponents, data: Data): LinearLayout {
        val paper = ui.palette.flatPaper
        val mutedPaper = ColorUtils.setAlphaComponent(paper, 196)
        val subtleOnDeep = 0x24FFFFFF
        val hPad = ui.dimens.pageHorizontal

        var currentStatuses = data.statuses
        var activeCompanion = data.activeCompanion
        var selectedIndex = data.initialSelectedId?.let { targetId ->
            currentStatuses.indexOfFirst { it.spec.id == targetId }.takeIf { it >= 0 }
        }

        val root = GalaxyBackdropLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dimens.pageTop, 0, ui.dimens.dp(28))
            clipChildren = false
            clipToPadding = false
        }

        val header = ui.column().apply {
            setPadding(hPad, 0, hPad, 0)
            addView(deepBackRow(context, ui, paper, subtleOnDeep, data.onBack))
            addView(ui.vgap(ui.dimens.dp(10)))
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.alive_wordmark)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    setTextColor(paper)
                    includeFontPadding = false
                }
            )
        }
        root.addView(header)
        root.addView(ui.vgap(ui.dimens.dp(4)))

        val availWidth = context.resources.displayMetrics.widthPixels - ui.dimens.dp(12)
        val ringSize = minOf(ui.dimens.dp(420), availWidth.coerceAtLeast(ui.dimens.dp(280)))
        val memberSize = ui.dimens.dp(40)
        val centerWidth = minOf(
            ui.dimens.dp(200),
            (ringSize - memberSize * 2 - ui.dimens.dp(24)).coerceAtLeast(ui.dimens.dp(150))
        )

        val hubKicker = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            setTextColor(mutedPaper)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val hubTitle = ui.h1("").apply {
            setTextColor(paper)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setLineSpacing(0f, 0.96f)
            gravity = Gravity.CENTER
        }
        val hubSub = ui.note("").apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(mutedPaper)
            gravity = Gravity.CENTER
        }

        val centerColumn = ui.column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(hubKicker)
            addView(ui.vgap(ui.dimens.xs))
            addView(hubTitle)
            addView(ui.vgap(ui.dimens.xs))
            addView(hubSub)
        }

        val ring = GalaxyOrbitView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            contentDescription = context.getString(R.string.galaxy_heading)
        }
        ring.setCenterView(
            centerColumn,
            FrameLayout.LayoutParams(
                centerWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val orbiterViews = currentStatuses.map { status ->
            ImageView(context).apply {
                setImageResource(status.spec.drawableRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(status.spec.titleRes)
            }
        }
        ring.setOrbiters(orbiterViews, memberSize)
        val memberHues = currentStatuses.map { ContextCompat.getColor(context, it.spec.hueRes) }
        fun syncOrbitStates() {
            ring.setOrbiterBaseAlphas(currentStatuses.map { if (it.awakened) 1f else DORMANT_ALPHA })
            ring.setOrbiterAuras(
                awakened = currentStatuses.map { it.awakened },
                hues = memberHues,
                companionIndex = currentStatuses.indexOfFirst { it.isCompanion }.takeIf { it >= 0 }
            )
        }
        syncOrbitStates()
        root.addView(ring)
        root.addView(ui.vgap(ui.dimens.dp(10)))

        val stageHost = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
        }

        val glassCard = AliveLiquidGlassCard(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(stageHost)
        }
        glassCard.attachBackdropSources(root, ring) { canvas, w, h ->
            root.drawAuroraBackdrop(canvas, w, h)
        }

        val bottomWrap = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(hPad, 0, hPad, 0)
            clipChildren = false
            clipToPadding = false
            addView(glassCard)
        }
        root.addView(bottomWrap)

        fun updateCenterHub() {
            val status = selectedIndex?.let { currentStatuses.getOrNull(it) }
            if (status == null) {
                val companionTitle = context.getString(activeCompanion.titleRes)
                hubKicker.text = context.getString(R.string.galaxy_companion_kicker, companionTitle)
                hubTitle.text = context.getString(R.string.galaxy_heading)
                hubSub.text = context.getString(R.string.galaxy_sub)
            } else {
                hubKicker.text = when {
                    status.isCompanion -> context.getString(R.string.galaxy_badge_companion)
                    status.awakened -> context.getString(R.string.galaxy_badge_awakened)
                    else -> context.getString(R.string.galaxy_badge_dormant)
                }
                hubTitle.text = context.getString(status.spec.titleRes)
                hubSub.text = when {
                    status.count > 0 -> context.getString(R.string.galaxy_hub_count, status.count)
                    status.awakened -> context.getString(R.string.galaxy_trail_work, 0, 0)
                    else -> context.getString(R.string.galaxy_hub_dormant)
                }
            }
        }

        lateinit var showStage: (Int?, Int) -> Unit

        fun buildOverviewContent(): LinearLayout {
            val panel = ui.column()
            val companionHue = ContextCompat.getColor(context, activeCompanion.hueRes)

            val topRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            topRow.addView(
                ImageView(context).apply {
                    setImageResource(activeCompanion.drawableRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = context.getString(activeCompanion.titleRes)
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(36), ui.dimens.dp(36))
                }
            )
            topRow.addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ui.dimens.dp(10)
                        marginEnd = ui.dimens.dp(8)
                    }
                    addView(
                        TextView(context).apply {
                            text = context.getString(
                                R.string.galaxy_overview_companion,
                                context.getString(activeCompanion.titleRes)
                            )
                            setTextColor(MUTED_ON_GLASS)
                            applyGlassShadow(this, 2.5f)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            includeFontPadding = false
                        }
                    )
                    addView(ui.vgap(ui.dimens.dp(2)))
                    addView(
                        ui.headline(context.getString(R.string.growth_level, data.level)).apply {
                            setTextColor(TEXT_ON_GLASS)
                            applyGlassShadow(this, 3.5f)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                        }
                    )
                }
            )
            topRow.addView(
                glassPillButton(
                    context = context,
                    label = context.getString(R.string.galaxy_continue),
                    fillColor = GLASS_PRIMARY_PILL_FILL,
                    strokeColor = TEXT_ON_GLASS,
                    textColor = ui.palette.ink,
                    compact = true,
                    onClick = { data.onClose() }
                )
            )
            panel.addView(topRow)

            panel.addView(ui.vgap(ui.dimens.dp(10)))
            panel.addView(glassProgressBar(context, CharacterGrowth.shareToNext(data.points), GLASS_METER_FILL))

            panel.addView(ui.vgap(ui.dimens.dp(6)))
            val progress = CharacterGrowth.progressWithin(data.points)
            val scaleLine = if (progress == null) {
                context.getString(R.string.growth_max)
            } else {
                context.getString(R.string.growth_progress, progress.first, progress.second)
            }
            val stageOrdinal = CharacterGrowth.stageFor(data.points).ordinal
            val xpMetaRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            xpMetaRow.addView(
                TextView(context).apply {
                    text = scaleLine
                    setTextColor(MUTED_ON_GLASS)
                    applyGlassShadow(this, 2.5f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            xpMetaRow.addView(stageStepDots(context, stageOrdinal, companionHue))
            panel.addView(xpMetaRow)

            panel.addView(ui.vgap(ui.dimens.dp(10)))
            val statsRow = ui.equalRow()
            listOf(
                Triple(R.string.growth_chip_quest, data.questDaysTotal, data.questCountedToday to ui.palette.workPale),
                Triple(R.string.growth_chip_focus, data.focusDaysTotal, data.focusCountedToday to ui.palette.rewardPale),
                Triple(R.string.growth_chip_promise, data.promiseDaysTotal, data.promiseCountedToday to ui.palette.pinkSoft)
            ).forEachIndexed { index, (labelRes, count, stateAndTint) ->
                if (index > 0) statsRow.addView(ui.hgap(ui.dimens.dp(6)))
                val (countedToday, tintColor) = stateAndTint
                val label = context.getString(labelRes) + if (countedToday) " ✓" else ""
                statsRow.addView(ui.weighted(glassStatTile(context, ui, label, count.toString(), tintColor)))
            }
            panel.addView(statsRow)

            return panel
        }

        fun buildInspectorContent(status: MemberStatus): LinearLayout {
            val spec = status.spec
            val hue = ContextCompat.getColor(context, spec.hueRes)
            val panel = ui.column()

            val headerRow = ui.row().apply { gravity = Gravity.CENTER_VERTICAL }
            headerRow.addView(
                ImageView(context).apply {
                    setImageResource(spec.drawableRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = context.getString(spec.titleRes)
                    layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(38), ui.dimens.dp(38))
                }
            )
            headerRow.addView(
                ui.column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ui.dimens.dp(10)
                        marginEnd = ui.dimens.dp(8)
                    }
                    addView(
                        ui.headline(context.getString(spec.titleRes)).apply {
                            setTextColor(TEXT_ON_GLASS)
                            applyGlassShadow(this, 3.5f)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                        }
                    )
                    addView(ui.vgap(ui.dimens.dp(2)))
                    val trailText = when (spec.kind) {
                        GalaxyMemberKind.WORK ->
                            context.getString(R.string.galaxy_trail_work, status.count, status.focusMinutes)
                        GalaxyMemberKind.DAY_STATE ->
                            context.getString(R.string.galaxy_trail_state_days, status.count)
                    }
                    addView(
                        TextView(context).apply {
                            text = trailText
                            setTextColor(MUTED_ON_GLASS)
                            applyGlassShadow(this, 2.5f)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            includeFontPadding = false
                        }
                    )
                }
            )
            headerRow.addView(
                glassPillButton(
                    context = context,
                    label = context.getString(R.string.galaxy_back_overview),
                    fillColor = GLASS_PILL_FILL,
                    strokeColor = GLASS_PILL_STROKE,
                    textColor = TEXT_ON_GLASS,
                    compact = true,
                    onClick = {
                        val prev = selectedIndex
                        ring.setSelectedSlot(null, animateToTop = false)
                        val dir = if (prev != null) -1 else 0
                        showStage(null, dir)
                    }
                )
            )
            panel.addView(headerRow)

            if (status.isCompanion) {
                panel.addView(ui.vgap(ui.dimens.dp(8)))
                panel.addView(
                    pillBadge(
                        context = context,
                        label = context.getString(R.string.galaxy_companion_active_pill),
                        face = ColorUtils.setAlphaComponent(ui.palette.flatMint, 78),
                        strokeColor = ColorUtils.setAlphaComponent(ui.palette.flatMint, 195),
                        textColor = TEXT_ON_GLASS
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                )
            }

            panel.addView(ui.vgap(ui.dimens.dp(8)))
            panel.addView(
                TextView(context).apply {
                    text = context.getString(spec.lineRes)
                    setTextColor(BODY_ON_GLASS)
                    applyGlassShadow(this, 2.5f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
                    setLineSpacing(0f, 1.15f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            )

            if (spec.kind == GalaxyMemberKind.WORK) {
                panel.addView(ui.vgap(ui.dimens.dp(10)))
                panel.addView(
                    TextView(context).apply {
                        text = formatBondProgressLine(context, status.bondXp)
                        setTextColor(TEXT_ON_GLASS)
                        applyGlassShadow(this, 2f)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                        includeFontPadding = false
                    }
                )
                panel.addView(ui.vgap(ui.dimens.dp(5)))
                panel.addView(
                    glassProgressBar(
                        context = context,
                        share = CompanionSynergy.shareToNext(status.bondXp),
                        fillColor = ColorUtils.setAlphaComponent(
                            ColorUtils.blendARGB(0xFFFFFFFF.toInt(), hue, 0.25f),
                            240
                        ),
                        heightDp = 5f
                    )
                )
                panel.addView(ui.vgap(ui.dimens.dp(6)))
                panel.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.galaxy_synergy_rule)
                        setTextColor(MUTED_ON_GLASS)
                        applyGlassShadow(this, 2f)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
                        setLineSpacing(0f, 1.12f)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                    }
                )
            }

            val canSetCompanion =
                spec.kind == GalaxyMemberKind.WORK && status.awakened && !status.isCompanion

            when {
                spec.kind == GalaxyMemberKind.WORK && !status.awakened -> {
                    panel.addView(ui.vgap(ui.dimens.dp(6)))
                    panel.addView(glassHintLine(context, R.string.galaxy_companion_locked_hint))
                }
                spec.kind == GalaxyMemberKind.DAY_STATE -> {
                    panel.addView(ui.vgap(ui.dimens.dp(6)))
                    panel.addView(glassHintLine(context, R.string.galaxy_state_no_companion_hint))
                }
            }

            val secondary = spec.secondaryAction
            panel.addView(ui.vgap(ui.dimens.dp(10)))

            if (canSetCompanion) {
                val companionPill = glassPillButton(
                    context = context,
                    label = context.getString(R.string.galaxy_set_companion),
                    fillColor = ColorUtils.setAlphaComponent(hue, 72),
                    strokeColor = ColorUtils.setAlphaComponent(
                        ColorUtils.blendARGB(TEXT_ON_GLASS, hue, 0.45f),
                        160
                    ),
                    textColor = TEXT_ON_GLASS,
                    compact = false,
                    onClick = {
                        if (data.onSelectCompanion(spec)) {
                            activeCompanion = spec
                            currentStatuses = currentStatuses.map { item ->
                                item.copy(isCompanion = item.spec.id == spec.id)
                            }
                            syncOrbitStates()
                            showStage(selectedIndex, 0)
                        }
                    }
                )
                if (secondary != null) {
                    val utilityRow = ui.equalRow()
                    utilityRow.addView(ui.weighted(companionPill))
                    utilityRow.addView(ui.hgap(ui.dimens.dp(8)))
                    utilityRow.addView(
                        ui.weighted(
                            glassPillButton(
                                context = context,
                                label = actionLabel(context, secondary, spec),
                                fillColor = GLASS_PILL_FILL,
                                strokeColor = GLASS_PILL_STROKE,
                                textColor = TEXT_ON_GLASS,
                                compact = false,
                                onClick = { data.onTriggerAction(secondary) }
                            )
                        )
                    )
                    panel.addView(utilityRow)
                } else {
                    panel.addView(
                        companionPill.apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                    )
                }
                panel.addView(ui.vgap(ui.dimens.dp(8)))
            }

            panel.addView(
                glassPillButton(
                    context = context,
                    label = actionLabel(context, spec.primaryAction, spec),
                    fillColor = GLASS_PRIMARY_PILL_FILL,
                    strokeColor = TEXT_ON_GLASS,
                    textColor = ui.palette.ink,
                    compact = false,
                    minHeightDp = 44f,
                    onClick = { data.onTriggerAction(spec.primaryAction) }
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
            )

            if (!canSetCompanion && secondary != null) {
                panel.addView(ui.vgap(ui.dimens.dp(8)))
                panel.addView(
                    glassPillButton(
                        context = context,
                        label = actionLabel(context, secondary, spec),
                        fillColor = GLASS_PILL_FILL,
                        strokeColor = GLASS_PILL_STROKE,
                        textColor = TEXT_ON_GLASS,
                        compact = false,
                        onClick = { data.onTriggerAction(secondary) }
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                )
            }

            return panel
        }

        showStage = { index, direction ->
            selectedIndex = index?.takeIf { it in currentStatuses.indices }
            updateCenterHub()
            val nextView = selectedIndex?.let { idx ->
                buildInspectorContent(currentStatuses[idx])
            } ?: buildOverviewContent()
            AliveMotion.swapStageChild(stageHost, nextView, direction)
        }

        ring.onOrbiterSelected = { nextIndex ->
            val prev = selectedIndex
            val direction = when {
                prev == nextIndex -> 0
                nextIndex == null -> -1
                prev == null -> +1
                nextIndex > prev -> +1
                else -> -1
            }
            showStage(nextIndex, direction)
        }

        if (selectedIndex != null) {
            ring.setSelectedSlot(selectedIndex, animateToTop = true)
        }
        showStage(selectedIndex, 0)

        return root
    }

    /**
     * Galaxy backdrop [LinearLayout] that pairs deep cosmos indigo behind the orbit
     * wheel with sculpted sweeping fluid ribbons and luminous wave crests across the
     * lower glass card zone so API 33+ optical lensing visibly bends curved geometry
     * through the translucent crystal bevel.
     */
    private class GalaxyBackdropLayout(context: Context) : LinearLayout(context) {
        private val density = context.resources.displayMetrics.density
        private fun dp(value: Float): Float = value * density

        private val deep = ContextCompat.getColor(context, R.color.flat_deep)
        private val deep2 = ContextCompat.getColor(context, R.color.flat_deep2)
        private val codingTint = ContextCompat.getColor(context, R.color.alive_fam_coding)
        private val creativeTint = ContextCompat.getColor(context, R.color.alive_fam_creative)
        private val readingTint = ContextCompat.getColor(context, R.color.alive_fam_reading)

        private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orbPaintTop = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ribbonBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ribbonFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val cornerMistGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val crestGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(10f)
            color = 0x3878C4FF
        }
        private val crestCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(1.8f)
        }

        private val ribbonBackPath = Path()
        private val ribbonFrontPath = Path()
        private val crestPathA = Path()
        private val crestPathB = Path()

        private var cachedW = -1
        private var cachedH = -1

        init {
            setWillNotDraw(false)
        }

        private fun ensureShaders(w: Int, h: Int) {
            if (w <= 0 || h <= 0 || (cachedW == w && cachedH == h)) return
            cachedW = w
            cachedH = h
            val wf = w.toFloat()
            val hf = h.toFloat()

            groundPaint.shader = LinearGradient(
                0f, 0f, 0f, hf,
                intArrayOf(deep, deep, deep2),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )
            orbPaintTop.shader = RadialGradient(
                wf * 0.50f, hf * 0.30f, (wf * 0.62f).coerceAtLeast(1f),
                intArrayOf(ColorUtils.setAlphaComponent(codingTint, 46), 0x00000000),
                null,
                Shader.TileMode.CLAMP
            )

            // Back sweeping sapphire-cobalt S-ribbon intersecting upper & right bevels of the glass card.
            ribbonBackPath.reset()
            ribbonBackPath.moveTo(-wf * 0.12f, hf * 0.78f)
            ribbonBackPath.cubicTo(
                wf * 0.24f, hf * 0.46f,
                wf * 0.64f, hf * 0.74f,
                wf * 1.12f, hf * 0.48f
            )
            ribbonBackPath.lineTo(wf * 1.12f, hf * 1.06f)
            ribbonBackPath.lineTo(-wf * 0.12f, hf * 1.06f)
            ribbonBackPath.close()

            ribbonBackPaint.shader = LinearGradient(
                0f, hf * 0.48f, wf, hf * 0.98f,
                intArrayOf(
                    0xFF152778.toInt(),
                    0xFF2352D0.toInt(),
                    0xFF347AE0.toInt(),
                    ColorUtils.blendARGB(0xFF2855C8.toInt(), codingTint, 0.38f),
                    0xFF141E64.toInt()
                ),
                floatArrayOf(0f, 0.32f, 0.58f, 0.80f, 1f),
                Shader.TileMode.CLAMP
            )

            // Foreground overlapping counter-wave crossing behind mid/bottom curved card walls.
            ribbonFrontPath.reset()
            ribbonFrontPath.moveTo(-wf * 0.12f, hf * 0.58f)
            ribbonFrontPath.cubicTo(
                wf * 0.30f, hf * 0.82f,
                wf * 0.66f, hf * 0.54f,
                wf * 1.12f, hf * 0.78f
            )
            ribbonFrontPath.lineTo(wf * 1.12f, hf * 1.06f)
            ribbonFrontPath.lineTo(-wf * 0.12f, hf * 1.06f)
            ribbonFrontPath.close()

            ribbonFrontPaint.shader = LinearGradient(
                0f, hf * 0.56f, wf, hf,
                intArrayOf(
                    0xB8182C86.toInt(),
                    0xCC2C66DE.toInt(),
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(0xFF244BB8.toInt(), readingTint, 0.35f), 204),
                    0xCC17215C.toInt()
                ),
                floatArrayOf(0f, 0.38f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )

            // Warm top-left / cool prism corner wash evocative of sunrise refraction over cobalt liquid.
            cornerMistGlowPaint.shader = RadialGradient(
                wf * 0.16f, hf * 0.64f, (wf * 0.46f).coerceAtLeast(1f),
                intArrayOf(
                    ColorUtils.setAlphaComponent(creativeTint, 56),
                    ColorUtils.setAlphaComponent(readingTint, 34),
                    0x00000000
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )

            // Twin luminous crest curves so edge bevel lens visibly bends crisp diagonal highlights.
            crestPathA.reset()
            crestPathA.moveTo(-wf * 0.10f, hf * 0.78f)
            crestPathA.cubicTo(
                wf * 0.24f, hf * 0.46f,
                wf * 0.64f, hf * 0.74f,
                wf * 1.10f, hf * 0.48f
            )

            crestPathB.reset()
            crestPathB.moveTo(-wf * 0.10f, hf * 0.58f)
            crestPathB.cubicTo(
                wf * 0.30f, hf * 0.82f,
                wf * 0.66f, hf * 0.54f,
                wf * 1.10f, hf * 0.78f
            )

            crestCorePaint.shader = LinearGradient(
                0f, 0f, wf, 0f,
                intArrayOf(
                    0x448ED2FF,
                    0xDDECF7FF.toInt(),
                    ColorUtils.setAlphaComponent(codingTint, 210),
                    0xBBE8F6FF.toInt(),
                    0x447CC8FF
                ),
                floatArrayOf(0f, 0.28f, 0.56f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        fun drawAuroraBackdrop(canvas: Canvas, w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            ensureShaders(w, h)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), groundPaint)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), orbPaintTop)
            canvas.drawPath(ribbonBackPath, ribbonBackPaint)
            canvas.drawPath(ribbonFrontPath, ribbonFrontPaint)
            canvas.drawRect(0f, h * 0.40f, w.toFloat(), h.toFloat(), cornerMistGlowPaint)
            canvas.drawPath(crestPathA, crestGlowPaint)
            canvas.drawPath(crestPathB, crestGlowPaint)
            canvas.drawPath(crestPathA, crestCorePaint)
            canvas.drawPath(crestPathB, crestCorePaint)
        }

        override fun onDraw(canvas: Canvas) {
            drawAuroraBackdrop(canvas, width, height)
            super.onDraw(canvas)
        }
    }

    private fun applyGlassShadow(tv: TextView, radiusDp: Float = 3f) {
        val d = tv.resources.displayMetrics.density
        tv.setShadowLayer(radiusDp * d, 0f, 1f * d, GLASS_TEXT_SHADOW)
    }

    private fun deepBackRow(
        context: Context,
        ui: AliveComponents,
        paper: Int,
        subtleOnDeep: Int,
        onBack: () -> Unit
    ): LinearLayout = ui.row().apply {
        gravity = Gravity.CENTER_VERTICAL
        contentDescription = context.getString(R.string.action_back)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onBack() }
        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_line_back)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
                setColorFilter(paper)
                setPadding(ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9), ui.dimens.dp(9))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(subtleOnDeep)
                }
                layoutParams = LinearLayout.LayoutParams(ui.dimens.dp(34), ui.dimens.dp(34)).apply {
                    marginEnd = ui.dimens.dp(10)
                }
            }
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.profile_back_label)
                setTextColor(paper)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                includeFontPadding = false
            }
        )
    }

    private fun glassProgressBar(
        context: Context,
        share: Float,
        @ColorInt fillColor: Int,
        heightDp: Float = 6f
    ): View {
        val dm = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dm + 0.5f).toInt()
        val track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
            background = GradientDrawable().apply {
                setColor(GLASS_METER_TRACK)
                cornerRadius = dp(999f).toFloat()
            }
        }
        val clamped = share.coerceIn(0f, 1f)
        if (clamped > 0f) {
            track.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, clamped)
                    background = GradientDrawable().apply {
                        setColor(fillColor)
                        cornerRadius = dp(999f).toFloat()
                    }
                }
            )
        }
        if (clamped < 1f) {
            track.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - clamped)
                }
            )
        }
        return track
    }

    private fun stageStepDots(
        context: Context,
        stageOrdinal: Int,
        @ColorInt hue: Int
    ): LinearLayout {
        val dm = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dm + 0.5f).toInt()
        val reachedTint = ColorUtils.blendARGB(0xD8FFFFFF.toInt(), hue, 0.35f)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            repeat(4) { index ->
                if (index > 0) {
                    addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(4f), 1) })
                }
                val active = index == stageOrdinal
                val reached = index <= stageOrdinal
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dp(if (active) 14f else 6f),
                            dp(5f)
                        )
                        background = GradientDrawable().apply {
                            cornerRadius = dp(999f).toFloat()
                            setColor(
                                when {
                                    active -> TEXT_ON_GLASS
                                    reached -> reachedTint
                                    else -> GLASS_METER_TRACK
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private fun glassStatTile(
        context: Context,
        ui: AliveComponents,
        label: String,
        value: String,
        @ColorInt faceTint: Int
    ): LinearLayout {
        val dm = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dm + 0.5f).toInt()
        val crystalTileTint = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(0xFFFFFFFF.toInt(), faceTint, 0.45f),
            40
        )
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(crystalTileTint)
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f), GLASS_SUB_TILE_STROKE)
            }
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            addView(
                TextView(context).apply {
                    text = label
                    setTextColor(MUTED_ON_GLASS)
                    applyGlassShadow(this, 2f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                }
            )
            addView(ui.vgap(dp(2f)))
            addView(
                TextView(context).apply {
                    text = value
                    setTextColor(TEXT_ON_GLASS)
                    applyGlassShadow(this, 3f)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
                    includeFontPadding = false
                }
            )
        }
    }

    private fun glassPillButton(
        context: Context,
        label: CharSequence,
        @ColorInt fillColor: Int,
        @ColorInt strokeColor: Int?,
        @ColorInt textColor: Int,
        compact: Boolean,
        minHeightDp: Float = if (compact) 34f else 40f,
        onClick: () -> Unit
    ): TextView {
        val dm = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dm + 0.5f).toInt()
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            if (textColor == TEXT_ON_GLASS) {
                applyGlassShadow(this, 2f)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 11.5f else 12.5f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.04f)
            minHeight = dp(minHeightDp)
            background = GradientDrawable().apply {
                setColor(fillColor)
                cornerRadius = dp(999f).toFloat()
                if (strokeColor != null) {
                    setStroke(dp(1f), strokeColor)
                }
            }
            val hPad = dp(if (compact) 12f else 14f)
            val vPad = dp(if (compact) 6f else 9f)
            setPadding(hPad, vPad, hPad, vPad)
            isClickable = true
            isFocusable = true
            AliveMotion.attachPressSpring(this, pressedScale = 0.96f, hapticOnDown = true)
            setOnClickListener { onClick() }
        }
    }

    private fun pillBadge(
        context: Context,
        label: CharSequence,
        @ColorInt face: Int,
        @ColorInt strokeColor: Int,
        @ColorInt textColor: Int
    ): TextView {
        val dm = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * dm + 0.5f).toInt()
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            applyGlassShadow(this, 2f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_bold)
            includeFontPadding = false
            background = GradientDrawable().apply {
                setColor(face)
                cornerRadius = dp(999f).toFloat()
                setStroke(dp(1f), strokeColor)
            }
            setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        }
    }

    private fun glassHintLine(context: Context, textRes: Int): TextView = TextView(context).apply {
        text = context.getString(textRes)
        setTextColor(MUTED_ON_GLASS)
        applyGlassShadow(this, 2.5f)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = ResourcesCompat.getFont(context, R.font.manrope_medium)
        includeFontPadding = false
    }

    private fun actionLabel(
        context: Context,
        target: GalaxyActionTarget,
        spec: GalaxyMemberSpec
    ): String {
        val memberLabel = context.getString(spec.titleRes).lowercase(Locale.getDefault())
        return when (target) {
            is GalaxyActionTarget.Focus -> context.getString(R.string.galaxy_action_focus, memberLabel)
            is GalaxyActionTarget.QuestFilter -> context.getString(R.string.galaxy_action_quest, memberLabel)
            GalaxyActionTarget.CustomQuest -> context.getString(R.string.galaxy_action_custom_quest)
            GalaxyActionTarget.TimeBank -> context.getString(R.string.galaxy_action_bank)
            GalaxyActionTarget.Permissions -> context.getString(R.string.galaxy_action_permissions)
            GalaxyActionTarget.Reminders -> context.getString(R.string.galaxy_action_reminders)
            GalaxyActionTarget.Stats -> context.getString(R.string.galaxy_action_stats)
        }
    }
}
