// UI-07 evidence report helpers shared by generate-all-evidence.mjs and its Vitest check.
//
// The report is built in two steps:
//   1. scripts/ui07-signup-check.py writes the baseline sections (screens, contrast, layout, focus, signup_flow).
//   2. scripts/generate-all-evidence.mjs --only ui-07 adds the 720px responsive viewport section.
// Step 2 reads the baseline from the report.json in the working tree (never from Git history) and refuses to write
// when a baseline section is missing, so earlier evidence can never be replaced by empty objects.

export const UI07_BASELINE_SECTIONS = ['screens', 'contrast', 'layout', 'focus', 'signup_flow']

export const UI07_VIEWPORT_SECTION = 'responsiveViewport720'

// Keys written by earlier rounds that this section replaces.
//  - zoom200: CSS `zoom: 200%` on a 1440px viewport (rejected in Round 2, not a browser zoom).
//  - reflowEquivalenceZoom200_Round3: same 720px run, labelled with claims it did not prove (Round 4).
export const UI07_SUPERSEDED_KEYS = ['zoom200', 'reflowEquivalenceZoom200_Round3']

// Checks that were not exercised by any script. They stay NOT_RUN until someone runs them.
export const UI07_NOT_RUN = {
  nativeBrowserZoom200: 'NOT_RUN - no run used the browser zoom control (Ctrl +) at 200%',
  wcag1410Reflow320CssPx: 'NOT_RUN - no run at 320 CSS px width; WCAG 1.4.10 conformance is not claimed',
  realBackendRegistration: 'NOT_RUN - TC-14 manual check against the dev backend',
  memberDashboardAfterSignup: 'NOT_RUN - dashboard APIs are not mocked; the /member screenshot may show a dashboard load error',
}

const isEmpty = (value) => {
  if (value === undefined || value === null || value === '') return true
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}

export function missingBaselineSections(report) {
  if (!report || typeof report !== 'object') return [...UI07_BASELINE_SECTIONS]
  return UI07_BASELINE_SECTIONS.filter((key) => isEmpty(report[key]))
}

export function assertUi07Baseline(report, source = 'report.json') {
  const missing = missingBaselineSections(report)
  if (missing.length) {
    throw new Error(
      `UI-07 ${source} is missing baseline sections: ${missing.join(', ')}. ` +
        'Run scripts/ui07-signup-check.py first; refusing to write a report without them.',
    )
  }
}

// Returns a new report: baseline sections untouched, superseded keys removed, viewport section replaced.
// Running it twice with the same section gives the same result, so regeneration is idempotent.
export function mergeUi07Report(existing, viewportSection, screenNames) {
  assertUi07Baseline(existing)
  const merged = structuredClone(existing)
  for (const key of UI07_SUPERSEDED_KEYS) delete merged[key]
  for (const name of screenNames) {
    if (!merged.screens.includes(name)) merged.screens.push(name)
  }
  merged[UI07_VIEWPORT_SECTION] = viewportSection
  return merged
}
