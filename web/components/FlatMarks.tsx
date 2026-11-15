import type { CategoryMark } from '@/lib/hues'

/**
 * Verbatim flat vector marks synchronized from Android `app/src/main/res/drawable/`.
 * Uses table-driven specs for category members, daily states, and Guardian moods
 * so every silhouette, eye capsule, and Pass-2 fill stays faithful to the app.
 */

type MarkProps = {
  size?: number
  className?: string
  label?: string
}

function svgA11y(label?: string) {
  return label
    ? { role: 'img' as const, 'aria-label': label }
    : { 'aria-hidden': true as const }
}

/** Hourglass mark (`ic_hourglass_flat.xml`). */
export function HourglassMark({ size = 24, className = '', label }: MarkProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path d="M8.6,6.2h6.8l-3.4,4.2z" fill="#F7C948" />
      <path d="M9.4,17.4l2.6,-3.2 2.6,3.2z" fill="#F7C948" />
      <path
        d="M6,3h12v2.4l-5.2,6.6 5.2,6.6V21H6v-2.4l5.2,-6.6L6,5.4z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** Outline shield (`ic_shield_flat.xml`). */
export function ShieldMark({ size = 24, className = '', label }: MarkProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path
        d="M12,2.6L20,5.6V12C20,17 16.6,20.4 12,21.8C7.4,20.4 4,17 4,12V5.6Z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** ALIVE main rose companion (`ic_alive_character.xml`). */
export function AliveCompanion({ size = 72, className = '', label }: MarkProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 72 72"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path
        d="M 36 12 C 51 12 56 20 56 32 C 63 32 65 36 65 39 C 65 42 63 45 56 45 C 56 51 54 53 51 54 L 51 59 C 51 62 49 63 46 63 C 43 63 41 62 41 59 L 41 55 C 38 56 34 56 31 55 L 31 59 C 31 62 29 63 26 63 C 23 63 21 62 21 59 L 21 54 C 18 53 16 51 16 45 C 9 45 7 42 7 39 C 7 36 9 32 16 32 C 16 20 21 12 36 12 Z"
        fill="#E58CA4"
      />
      <path
        d="M 28.75 26 C 30.27 26 31.5 27.23 31.5 28.75 L 31.5 34.25 C 31.5 35.77 30.27 37 28.75 37 C 27.23 37 26 35.77 26 34.25 L 26 28.75 C 26 27.23 27.23 26 28.75 26 Z M 43.25 26 C 44.77 26 46 27.23 46 28.75 L 46 34.25 C 46 35.77 44.77 37 43.25 37 C 41.73 37 40.5 35.77 40.5 34.25 L 40.5 28.75 C 40.5 27.23 41.73 26 43.25 26 Z"
        fill="#141414"
      />
    </svg>
  )
}

export type GuardianMood = 'idle' | 'alert' | 'recovery' | 'restored'

const GUARDIAN_SHAPES: Record<
  GuardianMood,
  { body: string; leftEye: string; rightEye: string }
> = {
  idle: {
    body: 'M 36 8 C 44 8 53 12 60 17 L 59 38 C 58 50 48 60 36 66 C 24 60 14 50 13 38 L 12 17 C 19 12 28 8 36 8 Z',
    leftEye:
      'M 27.5 24 C 29.985 24 32 26.015 32 28.5 L 32 40.5 C 32 42.985 29.985 45 27.5 45 C 25.015 45 23 42.985 23 40.5 L 23 28.5 C 23 26.015 25.015 24 27.5 24 Z',
    rightEye:
      'M 44.5 24 C 46.985 24 49 26.015 49 28.5 L 49 40.5 C 49 42.985 46.985 45 44.5 45 C 42.015 45 40 42.985 40 40.5 L 40 28.5 C 40 26.015 42.015 24 44.5 24 Z'
  },
  alert: {
    body: 'M 36 5 C 45 5 54 8 63 12 L 62 34 C 61 48 48 62 36 68 C 24 62 11 48 10 34 L 9 12 C 18 8 27 5 36 5 Z',
    leftEye:
      'M 22.976 19.371 C 25.655 18.703 28.369 20.334 29.037 23.013 L 32.666 37.568 C 33.334 40.247 31.703 42.961 29.024 43.629 C 26.345 44.297 23.631 42.666 22.963 39.987 L 19.334 25.432 C 18.666 22.753 20.297 20.039 22.976 19.371 Z',
    rightEye:
      'M 49.024 19.371 C 51.703 20.039 53.334 22.753 52.666 25.432 L 49.037 39.987 C 48.369 42.666 45.655 44.297 42.976 43.629 C 40.297 42.961 38.666 40.247 39.334 37.568 L 42.963 23.013 C 43.631 20.334 46.345 18.703 49.024 19.371 Z'
  },
  recovery: {
    body: 'M 36 20 C 46 20 56 23 63 27 L 61 45 C 58 54 48 59 36 64 C 24 59 14 54 11 45 L 9 27 C 16 23 26 20 36 20 Z',
    leftEye:
      'M 28.935 34.238 C 31.615 34.906 33.245 37.619 32.577 40.299 L 31.126 46.12 C 30.458 48.8 27.744 50.43 25.065 49.762 C 22.385 49.094 20.755 46.381 21.423 43.701 L 22.874 37.88 C 23.542 35.2 26.256 33.57 28.935 34.238 Z',
    rightEye:
      'M 43.065 34.238 C 45.744 33.57 48.458 35.2 49.126 37.88 L 50.577 43.701 C 51.245 46.381 49.615 49.094 46.935 49.762 C 44.256 50.43 41.542 48.8 40.874 46.12 L 39.423 40.299 C 38.755 37.619 40.385 34.906 43.065 34.238 Z'
  },
  restored: {
    body: 'M 36 5 C 49 5 57 13 57 25 L 56 41 C 55 51 45 61 36 67 C 27 61 17 51 16 41 L 15 25 C 15 13 23 5 36 5 Z',
    leftEye:
      'M 27.5 19 C 29.985 19 32 21.015 32 23.5 L 32 36.5 C 32 38.985 29.985 41 27.5 41 C 25.015 41 23 38.985 23 36.5 L 23 23.5 C 23 21.015 25.015 19 27.5 19 Z',
    rightEye:
      'M 44.5 16 C 46.985 16 49 18.015 49 20.5 L 49 33.5 C 49 35.985 46.985 38 44.5 38 C 42.015 38 40 35.985 40 33.5 L 40 20.5 C 40 18.015 42.015 16 44.5 16 Z'
  }
}

/** Guardian mascot states (`ic_guardian_*.xml`). */
export function GuardianCharacter({
  mood = 'idle',
  size = 72,
  className = '',
  label
}: MarkProps & { mood?: GuardianMood }) {
  const active = GUARDIAN_SHAPES[mood]
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 72 72"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path d={active.body} fill="#D9553F" />
      <path d={active.leftEye} fill="#141414" />
      <path d={active.rightEye} fill="#141414" />
    </svg>
  )
}

export type DayStateKind = 'normal' | 'near' | 'spent' | 'recovery' | 'rest'

type ShapeSpec = {
  body: string
  fill: string
  eyes: string
  eyeFill: string
  gloss?: string
}

const STATE_SHAPES: Record<DayStateKind, ShapeSpec> = {
  normal: {
    fill: '#28C840',
    body: 'M 36 7 C 48.04 11.58 56 23.12 56 36 C 56 48.88 48.04 60.42 36 65 C 23.96 60.42 16 48.88 16 36 C 16 23.12 23.96 11.58 36 7 Z',
    eyes: 'M 30.5 32 C 32.43 32 34 33.57 34 35.5 L 34 43.5 C 34 45.43 32.43 47 30.5 47 C 28.57 47 27 45.43 27 43.5 L 27 35.5 C 27 33.57 28.57 32 30.5 32 Z M 43.5 32 C 45.43 32 47 33.57 47 35.5 L 47 43.5 C 47 45.43 45.43 47 43.5 47 C 41.57 47 40 45.43 40 43.5 L 40 35.5 C 40 33.57 41.57 32 43.5 32 Z',
    eyeFill: '#141414',
    gloss:
      'M 46.1 22.29 C 47.29 22.68 47.93 23.95 47.55 25.13 L 45.23 32.26 C 44.85 33.44 43.58 34.09 42.4 33.71 C 41.21 33.32 40.57 32.05 40.95 30.87 L 43.27 23.74 C 43.65 22.56 44.92 21.91 46.1 22.29 Z'
  },
  near: {
    fill: '#FEBC2E',
    body: 'M 36 6 C 40 26 46 32 66 36 C 46 40 40 46 36 66 C 32 46 26 40 6 36 C 26 32 32 26 36 6 Z',
    eyes: 'M 33.69 33.21 C 35.3 33.61 36.28 35.24 35.88 36.84 L 33.94 44.61 C 33.54 46.21 31.91 47.19 30.31 46.79 C 28.7 46.39 27.72 44.76 28.12 43.16 L 30.06 35.39 C 30.46 33.79 32.09 32.81 33.69 33.21 Z M 42.69 31.21 C 44.3 31.61 45.28 33.24 44.88 34.84 L 42.94 42.61 C 42.54 44.21 40.91 45.19 39.31 44.79 C 37.7 44.39 36.72 42.76 37.12 41.16 L 39.06 33.39 C 39.46 31.79 41.09 30.81 42.69 31.21 Z',
    eyeFill: '#141414'
  },
  spent: {
    fill: '#FF5F57',
    body: 'M 64.44 46.33 C 64.44 47.26 64.08 48.15 63.42 48.81 L 48.81 63.42 C 48.15 64.08 47.26 64.44 46.33 64.44 L 25.67 64.44 C 24.74 64.44 23.85 64.08 23.19 63.42 L 8.58 48.81 C 7.92 48.15 7.56 47.26 7.56 46.33 L 7.56 25.67 C 7.56 24.74 7.92 23.85 8.58 23.19 L 23.19 8.58 C 23.85 7.92 24.74 7.56 25.67 7.56 L 46.33 7.56 C 47.26 7.56 48.15 7.92 48.81 8.58 L 63.42 23.19 C 64.08 23.85 64.44 24.74 64.44 25.67 Z',
    eyes: 'M 28.5 30 C 30.43 30 32 31.57 32 33.5 L 32 41.5 C 32 43.43 30.43 45 28.5 45 C 26.57 45 25 43.43 25 41.5 L 25 33.5 C 25 31.57 26.57 30 28.5 30 Z M 43.5 37 C 45.43 37 47 38.57 47 40.5 L 47 48.5 C 47 50.43 45.43 52 43.5 52 C 41.57 52 40 50.43 40 48.5 L 40 40.5 C 40 38.57 41.57 37 43.5 37 Z',
    eyeFill: '#141414'
  },
  recovery: {
    fill: '#9FD98A',
    body: 'M 36 9 C 40.97 9 45 13.03 45 18 L 45 54 C 45 58.97 40.97 63 36 63 C 31.03 63 27 58.97 27 54 L 27 18 C 27 13.03 31.03 9 36 9 Z M 18 27 L 54 27 C 58.97 27 63 31.03 63 36 C 63 40.97 58.97 45 54 45 L 18 45 C 13.03 45 9 40.97 9 36 C 9 31.03 13.03 27 18 27 Z',
    eyes: 'M 29.5 29 C 31.43 29 33 30.57 33 32.5 L 33 39.5 C 33 41.43 31.43 43 29.5 43 C 27.57 43 26 41.43 26 39.5 L 26 32.5 C 26 30.57 27.57 29 29.5 29 Z M 43.5 29 C 45.43 29 47 30.57 47 32.5 L 47 39.5 C 47 41.43 45.43 43 43.5 43 C 41.57 43 40 41.43 40 39.5 L 40 32.5 C 40 30.57 41.57 29 43.5 29 Z',
    eyeFill: '#141414',
    gloss:
      'M 41.1 14.29 C 42.29 14.68 42.93 15.95 42.55 17.13 L 40.23 24.26 C 39.85 25.44 38.58 26.09 37.4 25.71 C 36.21 25.32 35.57 24.05 35.95 22.87 L 38.27 15.74 C 38.65 14.56 39.92 13.91 41.1 14.29 Z'
  },
  rest: {
    fill: '#141414',
    body: 'M 58.83 51.42 C 51.99 62.24 38.49 66.78 26.51 62.28 C 14.52 57.77 7.34 45.47 9.32 32.82 C 11.31 20.17 21.9 10.66 34.69 10.03 C 24.12 17.08 20.87 31.13 27.27 42.09 C 33.67 53.06 47.5 57.15 58.83 51.42 Z',
    eyes: 'M 17.68 48.15 L 18.4 48.98 L 19.21 49.73 L 20.07 50.36 L 20.99 50.84 L 21.98 51.14 L 23 51.25 L 24.02 51.14 L 25.01 50.84 L 25.93 50.36 L 26.79 49.73 L 27.6 48.98 L 28.32 48.15 L 25.68 45.85 C 26.32 45.12 27.42 45.05 28.15 45.68 C 28.88 46.32 28.95 47.42 28.32 48.15 L 25.07 46.55 L 24.55 47.04 L 24.07 47.39 L 23.66 47.61 L 23.31 47.72 L 23 47.75 L 22.69 47.72 L 22.34 47.61 L 21.93 47.39 L 21.45 47.04 L 20.93 46.55 L 20.32 45.85 L 17.68 48.15 C 17.05 47.42 17.12 46.32 17.85 45.68 C 18.58 45.05 19.68 45.12 20.32 45.85 Z M 23.68 55.15 L 24.4 55.98 L 25.21 56.73 L 26.07 57.36 L 26.99 57.84 L 27.98 58.14 L 29 58.25 L 30.02 58.14 L 31.01 57.84 L 31.93 57.36 L 32.79 56.73 L 33.6 55.98 L 34.32 55.15 L 31.68 52.85 C 32.32 52.12 33.42 52.05 34.15 52.68 C 34.88 53.32 34.95 54.42 34.32 55.15 L 31.07 53.55 L 30.55 54.04 L 30.07 54.39 L 29.66 54.61 L 29.31 54.72 L 29 54.75 L 28.69 54.72 L 28.34 54.61 L 27.93 54.39 L 27.45 54.04 L 26.93 53.55 L 26.32 52.85 L 23.68 55.15 C 23.27 54.68 23.14 54.03 23.34 53.43 C 23.55 52.84 24.05 52.4 24.66 52.28 C 25.28 52.16 25.91 52.38 26.32 52.85 Z',
    eyeFill: '#FFFFFF'
  }
}

/** Daily operational state companions (`alive_state_*.xml`). */
export function StateCharacter({
  kind,
  size = 80,
  className = '',
  label
}: MarkProps & { kind: DayStateKind }) {
  const shape = STATE_SHAPES[kind]
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 72 72"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path d={shape.body} fill={shape.fill} />
      <path d={shape.eyes} fill={shape.eyeFill} />
      {shape.gloss ? <path d={shape.gloss} fill="#FFFFFF" fillOpacity="0.55" /> : null}
    </svg>
  )
}

export type MemberKind =
  | 'study'
  | 'reading'
  | 'exercise'
  | 'walking'
  | 'cleaning'
  | 'creative'
  | 'coding'
  | 'writing'
  | 'mindfulness'
  | 'social'
  | 'work'

const MEMBER_SHAPES: Record<MemberKind, ShapeSpec> = {
  study: {
    fill: '#F5CE7E',
    body: 'M 36 7 C 36 7 58 30 58 44 C 58 58 48 66 36 66 C 24 66 14 58 14 44 C 14 30 36 7 36 7 Z',
    eyes: 'M 30.5 32 C 32.43 32 34 33.57 34 35.5 L 34 43.5 C 34 45.43 32.43 47 30.5 47 C 28.57 47 27 45.43 27 43.5 L 27 35.5 C 27 33.57 28.57 32 30.5 32 Z M 43.5 32 C 45.43 32 47 33.57 47 35.5 L 47 43.5 C 47 45.43 45.43 47 43.5 47 C 41.57 47 40 45.43 40 43.5 L 40 35.5 C 40 33.57 41.57 32 43.5 32 Z',
    eyeFill: '#141414',
    gloss:
      'M 46.1 24.29 C 47.29 24.68 47.93 25.95 47.55 27.13 L 45.23 34.26 C 44.85 35.44 43.58 36.09 42.4 35.71 C 41.21 35.32 40.57 34.05 40.95 32.87 L 43.27 25.74 C 43.65 24.56 44.92 23.91 46.1 24.29 Z'
  },
  reading: {
    fill: '#9CC9E8',
    body: 'M 11 55 C 11 31 22 15 36 15 C 50 15 61 31 61 55 Z',
    eyes: 'M 28.5 30 C 30.43 30 32 31.57 32 33.5 L 32 41.5 C 32 43.43 30.43 45 28.5 45 C 26.57 45 25 43.43 25 41.5 L 25 33.5 C 25 31.57 26.57 30 28.5 30 Z M 43.5 37 C 45.43 37 47 38.57 47 40.5 L 47 48.5 C 47 50.43 45.43 52 43.5 52 C 41.57 52 40 50.43 40 48.5 L 40 40.5 C 40 38.57 41.57 37 43.5 37 Z',
    eyeFill: '#141414'
  },
  exercise: {
    fill: '#63C98A',
    body: 'M 63 37 C 63 51.91 50.91 64 36 64 C 21.09 64 9 51.91 9 37 C 9 22.09 21.09 10 36 10 C 50.91 10 63 22.09 63 37 Z',
    eyes: 'M 24 33 C 26.21 33 28 34.79 28 37 L 28 45 C 28 47.21 26.21 49 24 49 C 21.79 49 20 47.21 20 45 L 20 37 C 20 34.79 21.79 33 24 33 Z M 49 33 C 51.21 33 53 34.79 53 37 L 53 45 C 53 47.21 51.21 49 49 49 C 46.79 49 45 47.21 45 45 L 45 37 C 45 34.79 46.79 33 49 33 Z',
    eyeFill: '#141414',
    gloss:
      'M 50.1 18.29 C 51.29 18.68 51.93 19.95 51.55 21.13 L 49.23 28.26 C 48.85 29.44 47.58 30.09 46.4 29.71 C 45.21 29.32 44.57 28.05 44.95 26.87 L 47.27 19.74 C 47.65 18.56 48.92 17.91 50.1 18.29 Z'
  },
  walking: {
    fill: '#B7E06B',
    body: 'M 18 46 C 12 32 24 16 40 18 C 56 20 64 34 58 48 C 52 62 26 62 18 46 Z',
    eyes: 'M 31.31 33.22 C 33.19 33.69 34.33 35.59 33.86 37.47 L 31.93 45.23 C 31.46 47.1 29.56 48.24 27.69 47.78 C 25.81 47.31 24.67 45.41 25.14 43.53 L 27.07 35.77 C 27.54 33.9 29.44 32.76 31.31 33.22 Z M 45.31 31.22 C 47.19 31.69 48.33 33.59 47.86 35.47 L 45.93 43.23 C 45.46 45.1 43.56 46.24 41.69 45.78 C 39.81 45.31 38.67 43.41 39.14 41.53 L 41.07 33.77 C 41.54 31.9 43.44 30.76 45.31 31.22 Z',
    eyeFill: '#141414'
  },
  cleaning: {
    fill: '#C9CDD2',
    body: 'M 28 13 L 44 13 C 53.39 13 61 20.61 61 30 L 61 44 C 61 53.39 53.39 61 44 61 L 28 61 C 18.61 61 11 53.39 11 44 L 11 30 C 11 20.61 18.61 13 28 13 Z',
    eyes: 'M 30.5 32 C 32.43 32 34 33.57 34 35.5 L 34 43.5 C 34 45.43 32.43 47 30.5 47 C 28.57 47 27 45.43 27 43.5 L 27 35.5 C 27 33.57 28.57 32 30.5 32 Z M 43.5 32 C 45.43 32 47 33.57 47 35.5 L 47 43.5 C 47 45.43 45.43 47 43.5 47 C 41.57 47 40 45.43 40 43.5 L 40 35.5 C 40 33.57 41.57 32 43.5 32 Z',
    eyeFill: '#141414'
  },
  creative: {
    fill: '#F58FA6',
    body: 'M 38 40 C 38 47.73 31.73 54 24 54 C 16.27 54 10 47.73 10 40 C 10 32.27 16.27 26 24 26 C 31.73 26 38 32.27 38 40 Z M 54 29 C 54 37.84 46.84 45 38 45 C 29.16 45 22 37.84 22 29 C 22 20.16 29.16 13 38 13 C 46.84 13 54 20.16 54 29 Z M 65 40 C 65 47.18 59.18 53 52 53 C 44.82 53 39 47.18 39 40 C 39 32.82 44.82 27 52 27 C 59.18 27 65 32.82 65 40 Z M 26 40 L 46 40 C 51.52 40 56 44.48 56 50 C 56 55.52 51.52 60 46 60 L 26 60 C 20.48 60 16 55.52 16 50 C 16 44.48 20.48 40 26 40 Z',
    eyes: 'M 28.5 30 C 30.43 30 32 31.57 32 33.5 L 32 41.5 C 32 43.43 30.43 45 28.5 45 C 26.57 45 25 43.43 25 41.5 L 25 33.5 C 25 31.57 26.57 30 28.5 30 Z M 43.5 37 C 45.43 37 47 38.57 47 40.5 L 47 48.5 C 47 50.43 45.43 52 43.5 52 C 41.57 52 40 50.43 40 48.5 L 40 40.5 C 40 38.57 41.57 37 43.5 37 Z',
    eyeFill: '#141414',
    gloss:
      'M 48.1 20.29 C 49.29 20.68 49.93 21.95 49.55 23.13 L 47.23 30.26 C 46.85 31.44 45.58 32.09 44.4 31.71 C 43.21 31.32 42.57 30.05 42.95 28.87 L 45.27 21.74 C 45.65 20.56 46.92 19.91 48.1 20.29 Z'
  },
  coding: {
    fill: '#A99AF2',
    body: 'M 34.12 6.05 C 35.27 5.32 36.73 5.32 37.88 6.05 L 59.88 20.05 C 60.89 20.69 61.5 21.8 61.5 23 L 61.5 49 C 61.5 50.2 60.89 51.31 59.88 51.95 L 37.88 65.95 C 36.73 66.68 35.27 66.68 34.12 65.95 L 12.12 51.95 C 11.11 51.31 10.5 50.2 10.5 49 L 10.5 23 C 10.5 21.8 11.11 20.69 12.12 20.05 Z',
    eyes: 'M 31.31 33.22 C 33.19 33.69 34.33 35.59 33.86 37.47 L 31.93 45.23 C 31.46 47.1 29.56 48.24 27.69 47.78 C 25.81 47.31 24.67 45.41 25.14 43.53 L 27.07 35.77 C 27.54 33.9 29.44 32.76 31.31 33.22 Z M 45.31 31.22 C 47.19 31.69 48.33 33.59 47.86 35.47 L 45.93 43.23 C 45.46 45.1 43.56 46.24 41.69 45.78 C 39.81 45.31 38.67 43.41 39.14 41.53 L 41.07 33.77 C 41.54 31.9 43.44 30.76 45.31 31.22 Z',
    eyeFill: '#141414'
  },
  writing: {
    fill: '#F4B168',
    body: 'M 31.57 11.18 C 32.44 9.53 34.14 8.5 36 8.5 C 37.86 8.5 39.56 9.53 40.43 11.18 L 61.93 52.18 C 62.74 53.73 62.68 55.59 61.78 57.09 C 60.87 58.58 59.25 59.5 57.5 59.5 L 14.5 59.5 C 12.75 59.5 11.13 58.58 10.22 57.09 C 9.32 55.59 9.26 53.73 10.07 52.18 Z',
    eyes: 'M 30.5 32 C 32.43 32 34 33.57 34 35.5 L 34 43.5 C 34 45.43 32.43 47 30.5 47 C 28.57 47 27 45.43 27 43.5 L 27 35.5 C 27 33.57 28.57 32 30.5 32 Z M 43.5 32 C 45.43 32 47 33.57 47 35.5 L 47 43.5 C 47 45.43 45.43 47 43.5 47 C 41.57 47 40 45.43 40 43.5 L 40 35.5 C 40 33.57 41.57 32 43.5 32 Z',
    eyeFill: '#141414',
    gloss:
      'M 44.1 32.29 C 45.29 32.68 45.93 33.95 45.55 35.13 L 43.23 42.26 C 42.85 43.44 41.58 44.09 40.4 43.71 C 39.21 43.32 38.57 42.05 38.95 40.87 L 41.27 33.74 C 41.65 32.56 42.92 31.91 44.1 32.29 Z'
  },
  mindfulness: {
    fill: '#C9A3DE',
    body: 'M 27 17 L 45 17 C 56.05 17 65 25.95 65 37 C 65 48.05 56.05 57 45 57 L 27 57 C 15.95 57 7 48.05 7 37 C 7 25.95 15.95 17 27 17 Z',
    eyes: 'M 24 33 C 26.21 33 28 34.79 28 37 L 28 45 C 28 47.21 26.21 49 24 49 C 21.79 49 20 47.21 20 45 L 20 37 C 20 34.79 21.79 33 24 33 Z M 49 33 C 51.21 33 53 34.79 53 37 L 53 45 C 53 47.21 51.21 49 49 49 C 46.79 49 45 47.21 45 45 L 45 37 C 45 34.79 46.79 33 49 33 Z',
    eyeFill: '#141414'
  },
  social: {
    fill: '#77C9B6',
    body: 'M 33.88 5.21 C 35.13 4.26 36.87 4.26 38.12 5.21 L 63.12 24.21 C 64.28 25.1 64.77 26.61 64.35 28.02 L 54.35 61.02 C 53.9 62.49 52.54 63.5 51 63.5 L 21 63.5 C 19.46 63.5 18.1 62.49 17.65 61.02 L 7.65 28.02 C 7.23 26.61 7.72 25.1 8.88 24.21 Z',
    eyes: 'M 28.5 30 C 30.43 30 32 31.57 32 33.5 L 32 41.5 C 32 43.43 30.43 45 28.5 45 C 26.57 45 25 43.43 25 41.5 L 25 33.5 C 25 31.57 26.57 30 28.5 30 Z M 43.5 37 C 45.43 37 47 38.57 47 40.5 L 47 48.5 C 47 50.43 45.43 52 43.5 52 C 41.57 52 40 50.43 40 48.5 L 40 40.5 C 40 38.57 41.57 37 43.5 37 Z',
    eyeFill: '#141414',
    gloss:
      'M 50.1 18.29 C 51.29 18.68 51.93 19.95 51.55 21.13 L 49.23 28.26 C 48.85 29.44 47.58 30.09 46.4 29.71 C 45.21 29.32 44.57 28.05 44.95 26.87 L 47.27 19.74 C 47.65 18.56 48.92 17.91 50.1 18.29 Z'
  },
  work: {
    fill: '#7FA8CB',
    body: 'M 36 8 C 51 8 58 22 56 38 C 54 56 47 66 36 66 C 25 66 18 56 16 38 C 14 22 21 8 36 8 Z',
    eyes: 'M 31.31 33.22 C 33.19 33.69 34.33 35.59 33.86 37.47 L 31.93 45.23 C 31.46 47.1 29.56 48.24 27.69 47.78 C 25.81 47.31 24.67 45.41 25.14 43.53 L 27.07 35.77 C 27.54 33.9 29.44 32.76 31.31 33.22 Z M 45.31 31.22 C 47.19 31.69 48.33 33.59 47.86 35.47 L 45.93 43.23 C 45.46 45.1 43.56 46.24 41.69 45.78 C 39.81 45.31 38.67 43.41 39.14 41.53 L 41.07 33.77 C 41.54 31.9 43.44 30.76 45.31 31.22 Z',
    eyeFill: '#141414'
  }
}

/** Category family companions (`alive_member_*.xml`). */
export function MemberBadge({
  kind,
  size = 56,
  className = '',
  label
}: MarkProps & { kind: MemberKind }) {
  const shape = MEMBER_SHAPES[kind]
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 72 72"
      fill="none"
      focusable="false"
      className={className}
      {...svgA11y(label)}
    >
      <path d={shape.body} fill={shape.fill} />
      <path d={shape.eyes} fill={shape.eyeFill} />
      {shape.gloss ? <path d={shape.gloss} fill="#FFFFFF" fillOpacity="0.55" /> : null}
    </svg>
  )
}

/** Verbatim Pass-2 line icons (`ic_line_*.xml`). */
export function LineIcon({
  name,
  size = 20,
  className = ''
}: {
  name: CategoryMark | 'clock' | 'moon'
  size?: number
  className?: string
}) {
  switch (name) {
    case 'walker':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M13.4,6.8A2.2,2.2 0 1 0 13.4,2.4A2.2,2.2 0 1 0 13.4,6.8Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M12.8,9.4L11.6,13.6L8.8,16.8L7.4,20.4M11.6,13.6L14.2,16.4L14.6,20.6M12.8,9.4L15.4,11.4L17.8,12M12.8,9.4L10.2,11.6L8.6,14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'book':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M12,6.6C10.4,5.2 8,4.6 4.8,4.6V17.6C8,17.6 10.4,18.2 12,19.6C13.6,18.2 16,17.6 19.2,17.6V4.6C16,4.6 13.6,5.2 12,6.6Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M12,6.6V19.6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'books':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M4.4,5H9V19.4H4.4ZM9,5H13.6V19.4H9Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M15.4,5.8L19.6,6.8L16.4,19.4L12.2,18.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'dumbbell':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M7.6,8.8V15.2M4.4,10.2V13.8M16.4,8.8V15.2M19.6,10.2V13.8M7.6,12H16.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'broom':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M16.4,3.4L12.4,10.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M12.4,10.2C9.8,10.8 7.8,12.6 7,15.2L9.8,20.6C12.6,20 14.8,18.2 15.6,15.6C15.2,13.2 14.2,11.4 12.4,10.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M9.6,14.6L10.8,17.6M12.4,13.4L13.4,16.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'brush':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M19.6,4.4L12.8,11.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M12.8,11.2C11.2,10.4 9.2,10.8 8,12.2C6.8,13.6 7,15.2 5.6,16.4C4.8,17.1 3.8,17.4 3,17.4C4.2,19.4 6.8,20.4 9.2,19.8C11.6,19.2 13,17.2 12.8,15C12.7,13.6 12.8,12.4 12.8,11.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'terminal':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M4,5.6H20V18.4H4Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M7.6,9.6L10.2,12L7.6,14.4M12.8,14.4H16.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'pen':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M16.8,3.6L20.4,7.2L9.6,18L4.8,19.2L6,14.4Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M14.4,6L18,9.6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'lotus':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M12,5.2C10.2,7.4 9.4,9.8 9.4,12.2C9.4,14.6 10.4,16.6 12,18C13.6,16.6 14.6,14.6 14.6,12.2C14.6,9.8 13.8,7.4 12,5.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M4.4,9.4C4.6,13.8 7.4,17.2 12,18C16.6,17.2 19.4,13.8 19.6,9.4C17.4,10 15.6,11 14.2,12.4M9.8,12.4C8.4,11 6.6,10 4.4,9.4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'briefcase':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M4,8.4H20V18.8H4Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M9.2,8.4V6.4C9.2,5.6 9.8,5 10.6,5H13.4C14.2,5 14.8,5.6 14.8,6.4V8.4M4,12.6H20" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'clock':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M12,3A9,9 0 1,1 3,12A9,9 0 0,1 12,3ZM12,7V12L15.4,13.8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'moon':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M20,13.2A8.6,8.6 0 1,1 10.8,4A7,7 0 0,0 20,13.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
    case 'social':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M9.2,11.4A3.2,3.2 0 1 0 9.2,5A3.2,3.2 0 1 0 9.2,11.4Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M3.6,20C3.6,16.4 6.1,14.2 9.2,14.2C12.3,14.2 14.8,16.4 14.8,20" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <path d="M15.6,11.2A2.4,2.4 0 1 0 15.6,6.4A2.4,2.4 0 1 0 15.6,11.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M16.4,14.4C18.9,14.4 20.8,16.3 20.8,19.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      )
    case 'star':
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" focusable="false" aria-hidden="true" className={className}>
          <path d="M12,3.6L14.3,8.6L19.8,9.3L15.8,13.1L16.8,18.5L12,15.9L7.2,18.5L8.2,13.1L4.2,9.3L9.7,8.6Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )
  }
}
