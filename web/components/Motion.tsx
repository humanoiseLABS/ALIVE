'use client'

import { useEffect, useRef, useState } from 'react'

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Smoothly eases numeric changes (`cubic-out`) for interactive calculators; snaps cleanly if reduced-motion is set. */
export function useCountAnimation(target: number, durationMs = 280): number {
  const [display, setDisplay] = useState(target)
  const currentRef = useRef(target)

  useEffect(() => {
    if (prefersReducedMotion()) {
      currentRef.current = target
      setDisplay(target)
      return
    }

    const startValue = currentRef.current
    const delta = target - startValue
    if (delta === 0) return

    let frameId = 0
    const startTime = performance.now()

    const step = (now: number) => {
      const progress = Math.min(1, (now - startTime) / durationMs)
      const eased = 1 - Math.pow(1 - progress, 3)
      const next = Math.round(startValue + delta * eased)
      currentRef.current = next
      setDisplay(next)

      if (progress < 1) {
        frameId = requestAnimationFrame(step)
      } else {
        currentRef.current = target
        setDisplay(target)
      }
    }

    frameId = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frameId)
  }, [target, durationMs])

  return display
}

export type RevealState = 'idle' | 'waiting' | 'in'

/**
 * One-shot scroll reveal matching ALIVE's below-the-fold cadence.
 * Starts `idle` on SSR/initial paint so content never gets trapped invisible if scripts stall,
 * only staging `waiting` for elements currently below the viewport fold.
 */
export function useScrollReveal<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [state, setState] = useState<RevealState>('idle')

  useEffect(() => {
    const el = ref.current
    if (!el) return

    if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
      setState('in')
      return
    }

    const rect = el.getBoundingClientRect()
    if (rect.top <= window.innerHeight * 0.94) {
      setState('in')
      return
    }

    setState('waiting')

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setState('in')
            observer.disconnect()
            break
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -32px 0px' }
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return { ref, state }
}
