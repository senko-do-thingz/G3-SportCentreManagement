import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  UI07_BASELINE_SECTIONS,
  UI07_SUPERSEDED_KEYS,
  UI07_VIEWPORT_SECTION,
  assertUi07Baseline,
  mergeUi07Report,
  missingBaselineSections,
} from './ui07-report.mjs'

// Reads the committed file from the working tree, so it passes in a shallow or fresh clone (no Git history needed).
const reportPath = resolve(process.cwd(), 'docs/screenshots/ui-07/report.json')
const committed = JSON.parse(readFileSync(reportPath, 'utf8'))

const baseline = () => ({
  screens: ['signup-light-390-empty'],
  contrast: { 'signup-light-390-empty': [{ name: 'heading', ratio: 12, needed: 3 }] },
  layout: { 'signup-light-390-empty': { overflow: 0 } },
  focus: { 'light-390': { 'name field': true } },
  signup_flow: '/member',
  failures: [],
})
const section = { verifiedAt: 'fixed', outcome: 'PASS' }

describe('UI-07 evidence report', () => {
  it('committed report.json keeps every baseline section with data', () => {
    expect(missingBaselineSections(committed)).toEqual([])
  })

  it('committed report.json scopes the 720px run and keeps unrun checks NOT_RUN', () => {
    const viewport = committed[UI07_VIEWPORT_SECTION]
    expect(viewport.scope).toMatch(/720px responsive viewport equivalence/)
    expect(viewport.scope).toMatch(/Not a native browser zoom/)
    expect(viewport.measuredAssertions).not.toHaveProperty('keyboardReachabilityVerified')
    expect(viewport.measuredAssertions.programmaticFocusabilityVerified).toBe(true)
    expect(viewport.keyboardTraversal.method).toMatch(/Input\.dispatchKeyEvent Tab/)
    expect(viewport.mockRegistrationRedirect.dashboardVerified).toBe(false)
    for (const value of Object.values(viewport.notRun)) expect(value).toMatch(/^NOT_RUN/)
    for (const key of UI07_SUPERSEDED_KEYS) expect(committed).not.toHaveProperty(key)
  })

  it('refuses to merge when a baseline section is missing or empty', () => {
    for (const key of UI07_BASELINE_SECTIONS) {
      const broken = { ...baseline(), [key]: Array.isArray(baseline()[key]) ? [] : {} }
      expect(() => mergeUi07Report(broken, section, [])).toThrow(key)
    }
    expect(() => assertUi07Baseline({})).toThrow(/missing baseline sections/)
  })

  it('merge is idempotent and leaves baseline evidence untouched', () => {
    const start = { ...baseline(), zoom200: { empty: {} }, reflowEquivalenceZoom200_Round3: {} }
    const once = mergeUi07Report(start, section, ['signup-zoom200-1440-empty'])
    const twice = mergeUi07Report(once, section, ['signup-zoom200-1440-empty'])
    expect(twice).toEqual(once)
    expect(once.contrast).toEqual(baseline().contrast)
    expect(once.focus).toEqual(baseline().focus)
    expect(once.screens).toEqual(['signup-light-390-empty', 'signup-zoom200-1440-empty'])
    expect(once).not.toHaveProperty('zoom200')
    expect(once).not.toHaveProperty('reflowEquivalenceZoom200_Round3')
  })
})
