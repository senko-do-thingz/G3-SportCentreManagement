// Pass or fail rules of the NX-18 QA runner. Pure functions, so they can be tested without a browser.
// A run passes only when every REQUIRED check below holds for every capture. BLOCKED items are never
// reported as passed: they are printed on every run, whatever the exit code is.

export const SIZES = ['390', '768', '1440']
export const THEMES = ['light', 'dark']
export const STATES = ['empty', 'error', 'forbidden']

/** Work that this runner cannot cover. Printed on every run; NX-18 is not complete while any remain. */
export const BLOCKED = [
  'NX-11, NX-12, NX-13: the Coach plan editor, outcomes and feedback screens and the attendance UI are not on main',
  'Member training screens (NX-14, NX-15) are not on main; they are checked against PR 185 by a separate run',
  'FE/BE contract compatibility: docs/nx-03 is an unsigned draft and no end-to-end run against a backend was made',
  'Cross-role workflows (a coach records an outcome and the member sees it) need the backend',
  'Sign-off by Hung and Phat is not given by this tool',
]

/**
 * @param rows runner rows: captures (no `state`) and state checks (`state` set)
 * @param expected { screens: string[], sizes?: string[], emptyExempt?: string[] } screens and viewport widths that must be present (sizes defaults to all three)
 * @returns { failures: string[], captures: number, stateChecks: number }
 */
export function evaluate(rows, expected) {
  const failures = []
  const captures = rows.filter((r) => !r.state)
  const states = rows.filter((r) => r.state)
  const sizes = expected.sizes ?? SIZES
  const where = (r) => `${r.screen}@${r.width}/${r.scheme}`

  // Coverage first: a run that captured nothing must not pass.
  for (const screen of expected.screens) {
    for (const width of sizes) {
      for (const scheme of THEMES) {
        if (!captures.some((r) => r.screen === screen && r.width === width && r.scheme === scheme)) {
          failures.push(`missing capture ${screen}@${width}/${scheme}`)
        }
      }
    }
    for (const width of sizes.filter((w) => w === '390' || w === '1440')) {
      for (const scheme of THEMES) {
        for (const state of STATES) {
          if (!states.some((r) => r.screen === screen && r.width === width && r.scheme === scheme && r.state === state)) {
            failures.push(`missing ${state} check ${screen}@${width}/${scheme}`)
          }
        }
      }
    }
  }

  for (const r of captures) {
    if (r.overflow) failures.push(`horizontal overflow ${where(r)}`)
    if (r.h1 !== 1) failures.push(`expected exactly one h1, found ${r.h1} ${where(r)}`)
    if (r.redirected) failures.push(`redirected away from the screen (login or 403) ${where(r)}`)
    if (r.consoleErrors) failures.push(`${r.consoleErrors} console error(s) ${where(r)}`)
    if (r.axeSerious) {
      const ids = (r.axeViolations ?? []).filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id)
      failures.push(`axe serious or critical: ${ids.join(', ')} ${where(r)}`)
    }
    if (!r.focusStops) failures.push(`keyboard reached no control ${where(r)}`)
    if (r.invisibleFocus) failures.push(`${r.invisibleFocus} focus stop(s) without a visible ring ${where(r)}`)
  }

  const exempt = new Set(expected.emptyExempt ?? [])
  for (const r of states) {
    const at = `${r.screen}@${r.width}/${r.scheme}`
    if (r.overflow) failures.push(`horizontal overflow in ${r.state} state ${at}`)
    if (r.state === 'error' && !(r.alert && r.retry)) failures.push(`error state without alert and retry ${at}`)
    if (r.state === 'forbidden' && !r.alert) failures.push(`forbidden state without alert ${at}`)
    if (r.state === 'forbidden' && r.emptyState) failures.push(`forbidden answer shown as an empty list ${at}`)
    if (r.state === 'error' && r.emptyState) failures.push(`server error shown as an empty list ${at}`)
    if (r.state === 'empty' && !r.emptyState && !exempt.has(r.screen)) failures.push(`empty state without an empty panel ${at}`)
  }

  return { failures, captures: captures.length, stateChecks: states.length }
}
