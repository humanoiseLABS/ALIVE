'use client'

import { useState, type CSSProperties } from 'react'
import {
  AliveCompanion,
  GuardianCharacter,
  HourglassMark,
  LineIcon,
  MemberBadge,
  ShieldMark,
  StateCharacter,
  type DayStateKind
} from '@/components/FlatMarks'
import { useCopy } from '@/components/LangProvider'
import { useCountAnimation, useScrollReveal } from '@/components/Motion'
import { categories } from '@/lib/hues'
import { apkUrl, basePath, contactEmail, releasePageUrl } from '@/lib/site'

const METER_COLORS = ['#141414', '#E0A526', '#4E86B4'] as const
const SCROLL_PRESETS = [45, 90, 150, 213] as const
const LIMIT_PRESETS = [30, 60, 90] as const
const DAY_STATES: DayStateKind[] = ['normal', 'near', 'spent', 'recovery', 'rest']

function stagger(index: number): CSSProperties {
  return { '--stagger': index } as CSSProperties
}

function formatMinutes(minutes: number, minUnit: string, hrUnit: string): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} ${minUnit}`
  if (m === 0) return `${h} ${hrUnit}`
  return `${h}${hrUnit} ${m}${minUnit}`
}

function resolveDayState(scrollMin: number, limitMin: number, previewRest: boolean): DayStateKind {
  if (previewRest) return 'rest'
  if (scrollMin <= limitMin * 0.5) return 'normal'
  if (scrollMin <= limitMin) return 'near'
  if (scrollMin <= limitMin * 2) return 'spent'
  return 'recovery'
}

/** Canvas 1: Asymmetric Typographic Statement paired with free-canvas clickable ALIVE companions. */
export function SectionHero() {
  const copy = useCopy()
  const [selectedIdx, setSelectedIdx] = useState(0)
  const activeMember = copy.hero.members[selectedIdx] ?? copy.hero.members[0]

  return (
    <section id="top" className="bg-flat-ground px-4 pb-16 pt-10 sm:px-6 md:pb-24 md:pt-14 lg:px-10">
      <div className="mx-auto w-full max-w-6xl">
        <p
          className="alive-hero-enter font-head text-xs font-bold uppercase tracking-[0.08em] text-ink-secondary"
          style={stagger(0)}
        >
          {copy.hero.eyebrow}
        </p>

        <h1
          className="alive-hero-enter mt-4 max-w-4xl font-display font-bold leading-[0.98] tracking-[-0.04em] text-flat-ink"
          style={{ fontSize: 'clamp(2.55rem, 6.2vw, 4.75rem)', ...stagger(1) }}
        >
          <span className="block">{copy.hero.title[0]}</span>{' '}
          <span className="block md:pl-14">{copy.hero.title[1]}</span>
        </h1>

        <div className="mt-10 grid items-end gap-12 lg:grid-cols-12">
          <div className="alive-hero-enter lg:col-span-6" style={stagger(2)}>
            <p className="font-head text-xl font-semibold leading-snug text-flat-ink">
              {copy.hero.lead}
            </p>
            <p className="mt-4 max-w-lg text-base leading-relaxed text-ink-secondary">
              {copy.hero.body}
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <a href="#download" className="pill-btn pill-btn-primary">
                {copy.hero.cta}
              </a>
              <a href="#privacy" className="pill-btn pill-btn-quiet">
                {copy.hero.secondaryCta}
              </a>
            </div>

            <p className="mt-4 text-xs font-semibold text-ink-secondary">{copy.hero.ctaNote}</p>
          </div>

          {/* Open character playground directly on the ground canvas (no container card box). */}
          <div className="alive-hero-enter lg:col-span-6" style={stagger(3)}>
            <div className="flex items-end gap-6">
              <div className="shrink-0">
                <AliveCompanion size={116} label={copy.hero.companionAlt} />
              </div>

              <div className="min-w-0 flex-1 border-b border-black/10 pb-4">
                <p className="text-xs font-semibold text-ink-secondary">{copy.hero.familyHint}</p>
                <div
                  key={activeMember.kind}
                  className="alive-swap mt-1.5 flex items-baseline gap-2.5"
                >
                  <span className="font-display text-lg font-bold text-flat-ink">
                    {activeMember.name}
                  </span>
                  <span className="text-sm text-ink-secondary">{activeMember.note}</span>
                </div>
              </div>
            </div>

            <div
              className="mt-5 grid grid-cols-3 gap-2.5 sm:grid-cols-6"
              role="group"
              aria-label={copy.hero.familyHint}
            >
              {copy.hero.members.map((member, idx) => {
                const selected = idx === selectedIdx
                return (
                  <button
                    key={member.kind}
                    type="button"
                    onClick={() => setSelectedIdx(idx)}
                    aria-pressed={selected}
                    aria-label={member.name}
                    className={`toy-character-btn flex min-h-[68px] flex-col items-center justify-center gap-1 p-2 ${
                      selected
                        ? 'bg-flat-tile ring-2 ring-flat-ink'
                        : 'bg-transparent hover:bg-flat-soft'
                    }`}
                  >
                    <span className={selected ? 'alive-pop inline-flex' : 'inline-flex'}>
                      <MemberBadge kind={member.kind} size={42} />
                    </span>
                    <span className="font-head text-[11px] font-bold text-flat-ink">
                      {member.name}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

/** Canvas 2: Interactive Time Toybox (`bg-g-lav`) + Open Typographic Spec Ledger (`bg-flat-ground`). */
export function SectionTimeToy() {
  const copy = useCopy()
  const tb = copy.toybox
  const sl = copy.specLedger

  const [scrollMin, setScrollMin] = useState<number>(213)
  const [limitMin, setLimitMin] = useState<number>(60)
  const [previewRest, setPreviewRest] = useState<boolean>(false)

  const toyReveal = useScrollReveal<HTMLDivElement>()
  const ledgerReveal = useScrollReveal<HTMLDivElement>()

  const stateKey = resolveDayState(scrollMin, limitMin, previewRest)
  const activeState = tb.states[stateKey]

  const excessDailyMin = Math.max(0, scrollMin - limitMin)
  const monthlySavedHours = Math.round((excessDailyMin * 30) / 60)
  const animatedSavedHours = useCountAnimation(monthlySavedHours, 280)

  const barMax = Math.max(scrollMin, limitMin, 120)
  const scrollPct = Math.min(100, Math.round((scrollMin / barMax) * 100))
  const limitPct = Math.min(100, Math.round((limitMin / barMax) * 100))

  const jumpToState = (kind: DayStateKind) => {
    if (kind === 'rest') {
      setPreviewRest(true)
      return
    }
    setPreviewRest(false)
    if (kind === 'normal') setScrollMin(Math.floor(limitMin / 2))
    else if (kind === 'near') setScrollMin(limitMin)
    else if (kind === 'spent') setScrollMin(Math.min(240, limitMin + 45))
    else setScrollMin(Math.min(240, Math.max(limitMin * 2 + 15, 213)))
  }

  const paletteEntries = Object.entries(categories)
  const barTransition: CSSProperties = {
    transitionProperty: 'width',
    transitionDuration: '360ms',
    transitionTimingFunction: 'cubic-bezier(0.22, 1, 0.36, 1)'
  }

  return (
    <section id="time-toy">
      {/* Interactive Time & State Toybox */}
      <div className="bg-g-lav px-4 py-16 sm:px-6 md:py-24 lg:px-10">
        <div
          ref={toyReveal.ref}
          data-reveal={toyReveal.state}
          className="mx-auto grid w-full max-w-6xl items-center gap-10 lg:grid-cols-12 lg:gap-12"
        >
          <div className="reveal-child lg:col-span-5" style={stagger(0)}>
            <span className="eyebrow-pill bg-field-lilac text-flat-ink">{tb.badge}</span>
            <h2 className="mt-4 font-display text-display-sm text-flat-ink">{tb.title}</h2>
            <p className="mt-3 text-base leading-relaxed text-ink-secondary">{tb.lead}</p>

            {/* Slider + nightly scroll presets */}
            <div className="mt-8">
              <div className="flex items-baseline justify-between gap-2">
                <label
                  htmlFor="night-scroll-range"
                  className="font-head text-sm font-bold text-flat-ink"
                >
                  {tb.scrollLabel}
                </label>
                <span className="font-display text-lg font-bold text-flat-ink">
                  {formatMinutes(scrollMin, tb.minutesUnit, tb.hoursUnit)}
                </span>
              </div>

              <input
                id="night-scroll-range"
                type="range"
                min={15}
                max={240}
                step={3}
                value={scrollMin}
                onChange={(e) => {
                  setPreviewRest(false)
                  setScrollMin(Number(e.target.value))
                }}
                className="toy-slider mt-3"
              />

              <div className="mt-3 flex flex-wrap gap-2">
                {SCROLL_PRESETS.map((preset) => {
                  const selected = scrollMin === preset && !previewRest
                  const label = formatMinutes(preset, tb.minutesUnit, tb.hoursUnit)
                  return (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => {
                        setPreviewRest(false)
                        setScrollMin(preset)
                      }}
                      aria-pressed={selected}
                      className={`min-h-[44px] rounded-pill px-3.5 py-2 text-xs font-bold transition-[background-color,transform] duration-200 active:scale-95 ${
                        selected
                          ? 'bg-flat-ink text-flat-tile'
                          : 'bg-flat-tile text-flat-ink hover:bg-flat-soft'
                      }`}
                    >
                      {label}
                      {preset === 213 ? ` (${tb.realTag})` : ''}
                    </button>
                  )
                })}
              </div>
            </div>

            {/* Daily limit presets */}
            <div className="mt-7">
              <p className="font-head text-sm font-bold text-flat-ink">{tb.limitLabel}</p>
              <div className="mt-2.5 flex flex-wrap gap-2">
                {LIMIT_PRESETS.map((cap) => {
                  const selected = limitMin === cap
                  return (
                    <button
                      key={cap}
                      type="button"
                      onClick={() => {
                        setPreviewRest(false)
                        setLimitMin(cap)
                      }}
                      aria-pressed={selected}
                      className={`min-h-[44px] rounded-pill px-4 py-2 text-xs font-bold transition-[background-color,transform] duration-200 active:scale-95 ${
                        selected
                          ? 'bg-flat-ink text-flat-tile'
                          : 'bg-flat-tile text-flat-ink hover:bg-flat-soft'
                      }`}
                    >
                      {cap} {tb.minutesUnit}
                    </button>
                  )
                })}

                <button
                  type="button"
                  onClick={() => setPreviewRest((v) => !v)}
                  aria-pressed={previewRest}
                  className={`min-h-[44px] rounded-pill px-4 py-2 text-xs font-bold transition-[background-color,transform] duration-200 active:scale-95 ${
                    previewRest
                      ? 'bg-flat-ink text-flat-tile'
                      : 'border border-black/15 bg-transparent text-flat-ink hover:bg-flat-tile'
                  }`}
                >
                  {tb.restToggle}
                </button>
              </div>
            </div>
          </div>

          {/* Reactive State & Arithmetic Artifact */}
          <div className="reveal-child lg:col-span-7" style={stagger(1)}>
            <div className="flat-card bg-flat-tile p-6 sm:p-8">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span
                  key={`badge-${stateKey}`}
                  className={`alive-swap rounded-pill px-3.5 py-1 font-head text-xs font-bold uppercase tracking-wide ${activeState.pillClass}`}
                >
                  {activeState.code}
                </span>
                <span className="text-xs font-semibold text-ink-secondary">
                  {activeState.label}
                </span>
              </div>

              <div className="mt-6 flex flex-col items-start gap-6 sm:flex-row sm:items-center">
                <div key={stateKey} className="alive-pop shrink-0">
                  <StateCharacter kind={stateKey} size={96} label={activeState.label} />
                </div>

                <div className="min-w-0 flex-1">
                  {excessDailyMin > 0 ? (
                    <>
                      <p
                        className="font-display font-bold leading-none tracking-[-0.03em] text-flat-ink"
                        style={{ fontSize: 'clamp(2.25rem, 4.8vw, 3.5rem)', fontVariantNumeric: 'tabular-nums' }}
                      >
                        +{animatedSavedHours} {tb.hoursUnit}
                      </p>
                      <p className="mt-1.5 font-head text-sm font-bold text-flat-ink">
                        {tb.perMonthSuffix}
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="alive-swap font-display text-2xl font-bold text-flat-ink">
                        {tb.zeroResultTitle}
                      </p>
                      <p className="mt-1 text-sm text-ink-secondary">{tb.zeroResultSub}</p>
                    </>
                  )}
                  <p
                    key={`body-${stateKey}`}
                    className="alive-swap mt-3 text-sm leading-relaxed text-ink-secondary"
                  >
                    {activeState.body}
                  </p>
                </div>
              </div>

              {/* Direct proportion bars (scroll vs allowance) */}
              <div className="mt-7 space-y-3 border-t border-black/10 pt-5">
                <div>
                  <div className="flex justify-between text-xs font-bold text-flat-ink">
                    <span>{tb.barScrollLabel}</span>
                    <span>{formatMinutes(scrollMin, tb.minutesUnit, tb.hoursUnit)}</span>
                  </div>
                  <div className="mt-1.5 h-3 w-full overflow-hidden rounded-step bg-flat-soft">
                    <div
                      className="h-full rounded-step bg-flat-ink"
                      style={{ width: `${scrollPct}%`, ...barTransition }}
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-xs font-bold text-ink-secondary">
                    <span>{tb.barLimitLabel}</span>
                    <span>
                      {limitMin} {tb.minutesUnit}
                    </span>
                  </div>
                  <div className="mt-1.5 h-3 w-full overflow-hidden rounded-step bg-flat-soft">
                    <div
                      className="h-full rounded-step bg-field-mint"
                      style={{ width: `${limitPct}%`, ...barTransition }}
                    />
                  </div>
                </div>
              </div>

              {/* Clickable 5-state shape strip */}
              <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-black/10 pt-4">
                <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label={tb.statePanelLabel}>
                  {DAY_STATES.map((kind) => {
                    const current = kind === stateKey
                    return (
                      <button
                        key={kind}
                        type="button"
                        onClick={() => jumpToState(kind)}
                        aria-pressed={current}
                        aria-label={tb.states[kind].label}
                        title={tb.states[kind].label}
                        className={`toy-character-btn grid h-11 w-11 place-items-center ${
                          current ? 'bg-flat-soft ring-2 ring-flat-ink' : 'bg-transparent hover:bg-flat-soft'
                        }`}
                      >
                        <StateCharacter kind={kind} size={28} />
                      </button>
                    )
                  })}
                </div>
                <p className="text-xs text-ink-secondary">{tb.mathNote}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Open Typographic Spec Ledger (breaks away from boxed cards) */}
      <div className="bg-flat-ground px-4 py-16 sm:px-6 md:py-24 lg:px-10">
        <div
          ref={ledgerReveal.ref}
          data-reveal={ledgerReveal.state}
          className="mx-auto grid w-full max-w-6xl gap-12 lg:grid-cols-12"
        >
          {/* Left 4 col: Concrete real evening breakdown */}
          <div className="reveal-child lg:col-span-4" style={stagger(0)}>
            <p className="font-head text-xs font-bold uppercase tracking-[0.08em] text-ink-secondary">
              {sl.proofLabel}
            </p>
            <p className="mt-3 font-display text-metric font-bold text-flat-ink">
              {sl.proofFigure}
            </p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-flat-ink">
              <span className="rounded-pill bg-red-pale px-3 py-1">{sl.proofAllowance}</span>
              <span className="rounded-pill bg-flat-soft px-3 py-1">{sl.proofScreen}</span>
            </div>

            <div
              aria-hidden="true"
              className="reveal-bar mt-6 flex h-4 w-full gap-1 overflow-hidden rounded-step bg-flat-soft p-0.5"
            >
              {sl.apps.map((app, idx) => (
                <span
                  key={app.name}
                  className="h-full rounded-step"
                  style={{ width: `${app.share}%`, backgroundColor: METER_COLORS[idx] }}
                />
              ))}
            </div>

            <ul className="mt-4 divide-y divide-black/10 border-t border-black/10">
              {sl.apps.map((app, idx) => (
                <li
                  key={app.name}
                  className="flex items-center justify-between gap-2 py-2.5 text-sm"
                >
                  <span className="flex items-center gap-2 font-bold text-flat-ink">
                    <span
                      aria-hidden="true"
                      className="h-2.5 w-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: METER_COLORS[idx] }}
                    />
                    {app.name}
                  </span>
                  <span className="font-medium text-ink-secondary">{app.minutes}</span>
                </li>
              ))}
            </ul>

            <p className="mt-4 text-xs leading-relaxed text-ink-secondary">{sl.proofNote}</p>
          </div>

          {/* Right 8 col: Open numbered rows (`01`..`04`) */}
          <div className="lg:col-span-8">
            <h3
              className="reveal-child font-display text-2xl font-bold tracking-tight text-flat-ink sm:text-3xl"
              style={stagger(1)}
            >
              {sl.ledgerTitle}
            </h3>

            <ol className="mt-6 divide-y divide-black/10 border-y border-black/10">
              {sl.rows.map((row, idx) => (
                <li
                  key={row.num}
                  style={stagger(idx + 2)}
                  className="reveal-child grid gap-3 py-5 sm:grid-cols-12 sm:items-baseline"
                >
                  <span className="font-display text-sm font-bold text-ink-secondary sm:col-span-2">
                    {row.num}
                  </span>
                  <div className="sm:col-span-10">
                    <p className="font-head text-lg font-bold text-flat-ink">{row.name}</p>
                    <p className="mt-1 text-sm leading-relaxed text-ink-secondary">{row.body}</p>
                  </div>
                </li>
              ))}
            </ol>

            {/* 12 Android category colors strip */}
            <div className="reveal-child mt-6 flex flex-wrap items-center gap-2.5" style={stagger(6)}>
              <span className="mr-1 text-xs font-semibold text-ink-secondary">{sl.catLabel}:</span>
              <div className="flex flex-wrap gap-1.5" aria-hidden="true">
                {paletteEntries.map(([name, entry]) => (
                  <span
                    key={name}
                    className={`grid h-8 w-8 place-items-center rounded-full transition-transform duration-200 hover:-translate-y-1 ${entry.fill}`}
                  >
                    <LineIcon name={entry.mark} size={15} />
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

/** Canvas 3: Dark Indigo Cosmos Monument (`bg-deep`) with Guardian Crimson & open numbered list. */
export function SectionGuardian() {
  const copy = useCopy()
  const gb = copy.guardianBand
  const [poseIdx, setPoseIdx] = useState(0)
  const guardianReveal = useScrollReveal<HTMLDivElement>()
  const activePose = gb.poses[poseIdx] ?? gb.poses[0]

  return (
    <section
      id="guardian"
      data-surface="dark"
      className="bg-deep px-4 py-16 text-flat-tile sm:px-6 md:py-24 lg:px-10"
    >
      <div
        ref={guardianReveal.ref}
        data-reveal={guardianReveal.state}
        className="mx-auto grid w-full max-w-6xl items-center gap-12 lg:grid-cols-12"
      >
        {/* Monumental White Capsule Stage inside Deep Indigo */}
        <div className="reveal-child lg:col-span-5" style={stagger(0)}>
          <div className="flat-card bg-deep2 p-6 sm:p-8">
            <div className="flex flex-col items-center rounded-[32px] bg-flat-tile px-6 py-8 text-center text-flat-ink">
              <span
                key={`gbadge-${activePose.mood}`}
                className="alive-swap rounded-pill bg-field-peach px-3.5 py-1 font-head text-xs font-bold uppercase tracking-wide text-flat-ink"
              >
                {activePose.code}
              </span>

              <div key={activePose.mood} className="alive-pop my-6">
                <GuardianCharacter
                  mood={activePose.mood}
                  size={152}
                  label={`${gb.altPrefix} (${activePose.label})`}
                />
              </div>

              <div key={`gcopy-${activePose.mood}`} className="alive-swap">
                <p className="font-head text-lg font-bold text-flat-ink">{activePose.title}</p>
                <p className="mt-1.5 max-w-xs text-sm leading-relaxed text-ink-secondary">
                  {activePose.body}
                </p>
              </div>
            </div>

            <div
              className="mt-5 grid grid-cols-2 gap-2 sm:grid-cols-4"
              role="group"
              aria-label={gb.poseGroupLabel}
            >
              {gb.poses.map((pose, idx) => {
                const selected = idx === poseIdx
                return (
                  <button
                    key={pose.mood}
                    type="button"
                    onClick={() => setPoseIdx(idx)}
                    aria-pressed={selected}
                    className={`min-h-[44px] rounded-pill px-3 py-2 text-xs font-bold transition-[background-color,transform] duration-200 active:scale-95 ${
                      selected
                        ? 'bg-flat-tile text-flat-ink'
                        : 'bg-deep text-flat-tile hover:bg-navy'
                    }`}
                  >
                    {pose.label}
                  </button>
                )
              })}
            </div>
          </div>
        </div>

        {/* Open Numbered Typography List directly on Dark Indigo (no white subcards) */}
        <div className="lg:col-span-7">
          <div className="reveal-child" style={stagger(1)}>
            <span className="eyebrow-pill bg-field-peach text-flat-ink">{gb.badge}</span>

            <h2 className="mt-4 font-display text-display-sm text-flat-tile">
              <span className="block">{gb.title[0]}</span>{' '}
              <span className="block">{gb.title[1]}</span>
            </h2>

            <p className="mt-4 max-w-xl text-base leading-relaxed text-white/85">{gb.lead}</p>

            <p className="mt-8 font-head text-xs font-bold uppercase tracking-[0.08em] text-white/75">
              {gb.stepsTitle}
            </p>
          </div>

          <ol className="mt-3 divide-y divide-white/15 border-y border-white/15">
            {gb.places.map((place, idx) => (
              <li
                key={place.name}
                style={stagger(idx + 2)}
                className="reveal-child grid gap-2 py-4 sm:grid-cols-12 sm:items-baseline"
              >
                <span className="font-display text-sm font-bold text-field-peach sm:col-span-2">
                  0{idx + 1}
                </span>
                <div className="sm:col-span-10">
                  <p className="font-head text-base font-bold text-flat-tile">{place.name}</p>
                  <p className="mt-1 text-sm text-white/85">{place.body}</p>
                </div>
              </li>
            ))}
          </ol>

          <p
            className="reveal-child mt-6 text-sm font-semibold text-white/90"
            style={stagger(5)}
          >
            {gb.honest}
          </p>
        </div>
      </div>
    </section>
  )
}

/** Canvas 4: Two-tone Split Closing (`bg-g-mint` Privacy + `bg-field-butter` Download & Permissions). */
export function SectionPrivacyDownload() {
  const copy = useCopy()
  const pd = copy.privacyDownload
  const closingReveal = useScrollReveal<HTMLDivElement>()
  const downloadHref = apkUrl ?? releasePageUrl

  return (
    <section
      id="privacy"
      className="bg-flat-ground px-4 py-16 sm:px-6 md:py-24 lg:px-10"
    >
      <div
        ref={closingReveal.ref}
        data-reveal={closingReveal.state}
        className="mx-auto grid w-full max-w-6xl gap-5 lg:grid-cols-12"
      >
        {/* Left field: Offline Privacy (`bg-g-mint`) */}
        <div
          style={stagger(0)}
          className="reveal-child flat-card flex flex-col justify-between bg-g-mint p-7 sm:p-10 lg:col-span-6"
        >
          <div>
            <div className="flex items-center justify-between gap-4">
              <span className="eyebrow-pill bg-field-mint text-flat-ink">{pd.privacyBadge}</span>
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-field-mint">
                <ShieldMark size={24} />
              </span>
            </div>

            <h2 className="mt-5 font-display text-display-sm text-flat-ink">{pd.privacyTitle}</h2>
            <p className="mt-3 text-base leading-relaxed text-ink-secondary">{pd.privacyBody}</p>

            <div className="mt-6 flex flex-wrap gap-2">
              {pd.privacyMarks.map((mark) => (
                <span
                  key={mark}
                  className="inline-flex items-center gap-2 rounded-pill bg-flat-tile px-3.5 py-1.5 text-xs font-bold text-flat-ink"
                >
                  <span aria-hidden="true" className="h-2 w-2 rounded-full bg-forest" />
                  {mark}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-8">
            <a href={`${basePath}/privacy/`} className="pill-btn pill-btn-quiet text-sm">
              {pd.privacyLink}
            </a>
          </div>
        </div>

        {/* Right field: APK Download + compact permission lines (`bg-field-butter`) */}
        <div
          id="download"
          style={stagger(1)}
          className="reveal-child flat-card flex flex-col justify-between bg-field-butter p-7 sm:p-10 lg:col-span-6"
        >
          <div>
            <span className="eyebrow-pill bg-field-sun text-flat-ink">{pd.downloadBadge}</span>
            <h2 className="mt-5 font-display text-display-sm text-flat-ink">{pd.title}</h2>

            <div className="mt-4 flex flex-wrap items-center gap-3">
              <span className="font-display text-xl font-bold text-flat-ink">{pd.file.name}</span>
              <span className="rounded-pill bg-flat-tile px-3 py-1 font-head text-xs font-bold text-flat-ink">
                {pd.file.size}
              </span>
              <span className="text-xs font-semibold text-flat-ink">{pd.file.requires}</span>
            </div>

            <div className="mt-6">
              <a
                href={downloadHref}
                download={apkUrl ? true : undefined}
                className="pill-btn pill-btn-primary"
              >
                {pd.cta}
              </a>
            </div>

            <p className="mt-4 text-xs leading-relaxed text-flat-ink">{pd.honest}</p>
          </div>

          <ul className="mt-8 divide-y divide-black/15 border-t border-black/15">
            {pd.permissions.map((perm) => (
              <li
                key={perm.name}
                className="flex flex-col gap-0.5 py-3 text-xs sm:flex-row sm:items-baseline sm:justify-between sm:gap-4"
              >
                <span className="font-head font-bold text-flat-ink">{perm.name}</span>
                <span className="text-flat-ink/85 sm:text-right">{perm.body}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}

export function SiteFooter() {
  const copy = useCopy()

  return (
    <footer className="border-t border-black/[0.07] bg-flat-tile px-4 py-12 sm:px-6 lg:px-10">
      <div className="mx-auto grid w-full max-w-6xl gap-8 md:grid-cols-2">
        <div>
          <div className="flex items-center gap-2.5">
            <HourglassMark size={22} />
            <span className="font-display text-lg font-bold text-flat-ink">{copy.footer.line}</span>
          </div>
          <p className="mt-3 max-w-xs text-sm text-ink-secondary">{copy.footer.sub}</p>
          <p className="mt-1.5 max-w-xs text-sm text-ink-secondary">{copy.footer.promise}</p>
        </div>

        <nav aria-label={copy.footer.links} className="md:justify-self-end md:text-right">
          <h2 className="font-head text-xs font-bold uppercase tracking-wider text-ink-secondary">
            {copy.footer.links}
          </h2>
          <ul className="mt-2 space-y-1 text-sm">
            <li>
              <a href={`${basePath}/privacy/`} className="footer-link">
                {copy.footer.privacy}
              </a>
            </li>
            <li>
              <span className="text-ink-secondary">{copy.footer.contact}:</span>{' '}
              <a href={`mailto:${contactEmail}`} className="footer-link">
                {contactEmail}
              </a>
            </li>
            <li>
              <a href="#top" className="footer-link">
                {copy.meta.toTop}
              </a>
            </li>
          </ul>
        </nav>
      </div>

      <div className="mx-auto mt-8 w-full max-w-6xl border-t border-black/[0.06] pt-5">
        <p className="text-xs text-ink-secondary">{copy.footer.legal}</p>
      </div>
    </footer>
  )
}
