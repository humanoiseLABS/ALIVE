/**
 * Pass-2 Flat category, field, and state color registry.
 *
 * Literal Tailwind class strings keep Tailwind's scanner and `scripts/guard.mjs`
 * aligned with `app/tokens.css`.
 */

export type CategoryMark =
  | 'book'
  | 'books'
  | 'dumbbell'
  | 'walker'
  | 'broom'
  | 'brush'
  | 'terminal'
  | 'pen'
  | 'lotus'
  | 'social'
  | 'briefcase'
  | 'star'

export const fills = {
  gold: 'bg-gold-mid',
  goldDeep: 'bg-gold-dark',
  red: 'bg-red-mid',
  redDeep: 'bg-red-dark',
  blue: 'bg-blue-mid',
  blueDeep: 'bg-blue-dark',
  forest: 'bg-forest',
  resting: 'bg-soft-forest',
  folded: 'bg-warm-gray',
  amber: 'bg-amber',
  critter: 'bg-pink',
  ink: 'bg-flat-ink',
  paper: 'bg-flat-tile'
} as const

export const fields = {
  mint: 'bg-field-mint',
  blossom: 'bg-field-blossom',
  butter: 'bg-field-butter',
  aqua: 'bg-field-aqua',
  peach: 'bg-field-peach',
  lilac: 'bg-field-lilac',
  sky: 'bg-field-sky',
  rose: 'bg-field-rose',
  sun: 'bg-field-sun'
} as const

export const grounds = {
  default: 'bg-flat-ground',
  lav: 'bg-g-lav',
  mint: 'bg-g-mint',
  blush: 'bg-g-blush',
  butter: 'bg-g-butter',
  sky: 'bg-g-sky',
  peach: 'bg-g-peach'
} as const

export const categories: Record<
  string,
  { fill: string; mark: CategoryMark }
> = {
  study: { fill: 'bg-cat-study', mark: 'book' },
  reading: { fill: 'bg-cat-reading', mark: 'books' },
  exercise: { fill: 'bg-cat-exercise', mark: 'dumbbell' },
  walking: { fill: 'bg-cat-walking', mark: 'walker' },
  cleaning: { fill: 'bg-cat-cleaning', mark: 'broom' },
  creative: { fill: 'bg-cat-creative', mark: 'brush' },
  coding: { fill: 'bg-cat-coding', mark: 'terminal' },
  writing: { fill: 'bg-cat-writing', mark: 'pen' },
  mindfulness: { fill: 'bg-cat-mindfulness', mark: 'lotus' },
  social: { fill: 'bg-cat-social', mark: 'social' },
  work: { fill: 'bg-cat-work', mark: 'briefcase' },
  other: { fill: 'bg-cat-other', mark: 'star' }
}
