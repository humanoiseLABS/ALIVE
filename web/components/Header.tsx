'use client'

import { HourglassMark } from './FlatMarks'
import { useCopy, useLang } from './LangProvider'
import { langs } from '@/lib/copy'

/** Pass-2 sticky top bar with direct section links, download pill, and ID/EN toggle. */
export default function Header() {
  const copy = useCopy()
  const { lang, setLang } = useLang()

  return (
    <header className="sticky top-0 z-50 border-b border-black/[0.06] bg-flat-ground/95 backdrop-blur-sm">
      <div className="mx-auto flex w-full max-w-6xl items-center gap-3 px-4 py-2.5 sm:px-6 lg:px-10">
        <a
          href="#top"
          className="inline-flex min-h-[44px] shrink-0 items-center gap-2.5 rounded-pill px-2 transition-opacity hover:opacity-80"
        >
          <HourglassMark size={22} />
          <span className="font-display text-lg font-bold tracking-[-0.03em] text-flat-ink">
            {copy.meta.brand}
          </span>
        </a>

        <span className="hidden text-sm font-medium text-ink-secondary xl:inline">
          {copy.meta.tagline}
        </span>

        <div className="ml-auto flex items-center gap-2 sm:gap-3">
          <nav aria-label={copy.nav.menuLabel} className="flex items-center gap-1">
            <a href="#time-toy" className="nav-link hidden md:inline-flex">
              {copy.nav.toy}
            </a>
            <a href="#guardian" className="nav-link hidden md:inline-flex">
              {copy.nav.guardian}
            </a>
            <a href="#privacy" className="nav-link hidden lg:inline-flex">
              {copy.nav.privacy}
            </a>
            <a href="#download" className="pill-btn pill-btn-primary ml-1 px-4 py-2 text-sm">
              {copy.nav.download}
            </a>
          </nav>

          <div
            className="flex shrink-0 items-center rounded-pill border border-black/[0.08] bg-flat-tile p-0.5"
            role="group"
            aria-label={copy.nav.langLabel}
          >
            {langs.map((option) => {
              const active = option.code === lang
              return (
                <button
                  key={option.code}
                  type="button"
                  onClick={() => setLang(option.code)}
                  aria-pressed={active}
                  className={`min-h-[40px] min-w-[40px] rounded-pill px-2.5 font-head text-xs font-bold transition-colors sm:min-h-[44px] sm:min-w-[44px] ${
                    active
                      ? 'bg-flat-ink text-flat-tile'
                      : 'bg-transparent text-ink-secondary hover:bg-flat-soft hover:text-flat-ink'
                  }`}
                >
                  {option.label}
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </header>
  )
}
