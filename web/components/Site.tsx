'use client'

import Header from './Header'
import {
  SectionGuardian,
  SectionHero,
  SectionPrivacyDownload,
  SectionTimeToy,
  SiteFooter
} from './sections/Beats'
import { useCopy } from './LangProvider'

/**
 * Editorial Toybox & Spec Artifact landing page aligned with Android Pass-2 ALIVE.
 */
export default function Site() {
  const copy = useCopy()

  return (
    <>
      <a href="#main-content" className="skip-link">
        {copy.meta.skip}
      </a>

      <Header />

      <main id="main-content" tabIndex={-1} className="outline-none">
        <SectionHero />
        <SectionTimeToy />
        <SectionGuardian />
        <SectionPrivacyDownload />
      </main>

      <SiteFooter />
    </>
  )
}
