import type { Config } from 'tailwindcss'

/**
 * ALIVE's Pass-2 Flat design tokens, synced directly from Android `colors.xml`
 * and `dimens.xml` via `app/tokens.css`.
 */
const config: Config = {
  content: ['./{app,components,lib}/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Core Pass-2 Flat surfaces & ink
        'flat-ground': 'var(--alive-flat_ground)',
        'flat-tile': 'var(--alive-flat_tile)',
        'flat-paper': 'var(--alive-flat_paper)',
        'flat-ink': 'var(--alive-flat_ink)',
        'flat-soft': 'var(--alive-flat_soft)',
        'flat-handle': 'var(--alive-flat_sheet_handle)',

        // Ground & ink aliases
        paper: 'var(--alive-flat_paper)',
        canvas: 'var(--alive-flat_ground)',
        ink: 'var(--alive-flat_ink)',
        'ink-secondary': 'var(--alive-ink_secondary)',
        'ink-muted': 'var(--alive-ink_muted)',
        'ink-hairline': 'var(--alive-ink_hairline)',
        white: 'var(--alive-flat_tile)',

        // Pass-2 Pastel Field fills
        'field-peach': 'var(--alive-flat_field_peach)',
        'field-lilac': 'var(--alive-flat_field_lilac)',
        'field-mint': 'var(--alive-flat_field_mint)',
        'field-sky': 'var(--alive-flat_field_sky)',
        'field-butter': 'var(--alive-flat_field_butter)',
        'field-rose': 'var(--alive-flat_field_rose)',
        'field-lime': 'var(--alive-flat_field_lime)',
        'field-coral': 'var(--alive-flat_field_coral)',
        'field-grape': 'var(--alive-flat_field_grape)',
        'field-aqua': 'var(--alive-flat_field_aqua)',
        'field-blossom': 'var(--alive-flat_field_blossom)',
        'field-leaf': 'var(--alive-flat_field_leaf)',
        'field-sun': 'var(--alive-flat_field_sun)',
        'field-clay': 'var(--alive-flat_field_clay)',

        // Pass-2 Tinted Screen Grounds
        'g-lav': 'var(--alive-flat_g_lav)',
        'g-mint': 'var(--alive-flat_g_mint)',
        'g-blush': 'var(--alive-flat_g_blush)',
        'g-butter': 'var(--alive-flat_g_butter)',
        'g-sky': 'var(--alive-flat_g_sky)',
        'g-peach': 'var(--alive-flat_g_peach)',

        // Deep Indigo Cosmos & Guardian Surface
        deep: 'var(--alive-flat_deep)',
        deep2: 'var(--alive-flat_deep2)',
        navy: 'var(--alive-flat_navy)',

        // Role & Family tones
        forest: 'var(--alive-forest)',
        'forest-soft': 'var(--alive-forest_soft)',
        'soft-forest': 'var(--alive-soft_forest)',
        'warm-gray': 'var(--alive-warm_gray)',
        'gold-dark': 'var(--alive-gold_reward_dark)',
        'gold-mid': 'var(--alive-gold_reward_mid)',
        'gold-pale': 'var(--alive-gold_reward_pale)',
        'red-dark': 'var(--alive-red_short_dark)',
        'red-mid': 'var(--alive-red_short_mid)',
        'red-pale': 'var(--alive-red_short_pale)',
        'blue-dark': 'var(--alive-blue_work_dark)',
        'blue-mid': 'var(--alive-blue_work_mid)',
        'blue-soft': 'var(--alive-blue_work_soft)',
        'blue-pale': 'var(--alive-blue_work_pale)',
        amber: 'var(--alive-amber_action)',
        pink: 'var(--alive-pink_soft)',

        // Category hues (synced with Android Pass-2)
        'cat-study': 'var(--alive-cat_study)',
        'cat-reading': 'var(--alive-cat_reading)',
        'cat-exercise': 'var(--alive-cat_exercise)',
        'cat-walking': 'var(--alive-cat_walking)',
        'cat-cleaning': 'var(--alive-cat_cleaning)',
        'cat-creative': 'var(--alive-cat_creative)',
        'cat-coding': 'var(--alive-cat_coding)',
        'cat-writing': 'var(--alive-cat_writing)',
        'cat-mindfulness': 'var(--alive-cat_mindfulness)',
        'cat-social': 'var(--alive-cat_social)',
        'cat-work': 'var(--alive-cat_work)',
        'cat-other': 'var(--alive-cat_other)'
      },
      borderRadius: {
        step: '4px',
        tile: '22px',
        field: '22px',
        card: '28px',
        pill: '999px'
      },
      fontFamily: {
        display: ['var(--font-display)', 'sans-serif'],
        head: ['var(--font-head)', 'sans-serif'],
        body: ['var(--font-body)', 'sans-serif']
      },
      fontSize: {
        display: [
          'clamp(2.35rem, 4.8vw, 3.65rem)',
          { lineHeight: '1.04', letterSpacing: '-0.035em', fontWeight: '700' }
        ],
        'display-sm': [
          'clamp(1.75rem, 3.2vw, 2.5rem)',
          { lineHeight: '1.1', letterSpacing: '-0.03em', fontWeight: '700' }
        ],
        metric: [
          'clamp(2.25rem, 4vw, 3.25rem)',
          { lineHeight: '1', letterSpacing: '-0.04em', fontWeight: '700' }
        ]
      }
    }
  },
  plugins: []
}

export default config
