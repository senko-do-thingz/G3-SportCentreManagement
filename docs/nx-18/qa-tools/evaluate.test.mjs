// Negative control for the NX-18 runner rules. Run: node --test docs/nx-18/qa-tools/evaluate.test.mjs
// A runner that cannot fail proves nothing, so each test breaks one rule on purpose and expects a failure.
import test from 'node:test'
import assert from 'node:assert/strict'
import { BLOCKED, SIZES, STATES, THEMES, evaluate } from './evaluate.mjs'

const SCREENS = ['screen-a', 'screen-b']

function goodRows() {
  const rows = []
  for (const screen of SCREENS) {
    for (const width of SIZES) {
      for (const scheme of THEMES) {
        rows.push({ screen, width, scheme, h1: 1, overflow: false, redirected: false, consoleErrors: 0, axeSerious: 0, axeViolations: [], focusStops: 6, invisibleFocus: 0 })
      }
    }
    for (const width of ['390', '1440']) {
      for (const scheme of THEMES) {
        for (const state of STATES) {
          rows.push({ screen, width, scheme, state, overflow: false, alert: state !== 'empty', retry: state !== 'empty', emptyState: state === 'empty' })
        }
      }
    }
  }
  return rows
}
const run = (mutate, options = {}) => {
  const rows = goodRows()
  mutate(rows)
  return evaluate(rows, { screens: SCREENS, ...options })
}
const capture = (rows, i = 0) => rows.filter((r) => !r.state)[i]
const stateRow = (rows, state) => rows.find((r) => r.state === state)

test('a clean run has no failures', () => {
  const result = evaluate(goodRows(), { screens: SCREENS })
  assert.deepEqual(result.failures, [])
  assert.equal(result.captures, SCREENS.length * SIZES.length * THEMES.length)
})

test('an empty run fails: nothing captured is not a pass', () => {
  const result = evaluate([], { screens: SCREENS })
  assert.ok(result.failures.length > 0)
  assert.ok(result.failures.some((f) => f.startsWith('missing capture')))
})

test('a missing capture fails', () => {
  const rows = goodRows().filter((r) => !(r.screen === 'screen-b' && r.width === '768' && r.scheme === 'dark' && !r.state))
  assert.ok(evaluate(rows, { screens: SCREENS }).failures.some((f) => f.includes('missing capture screen-b@768/dark')))
})

test('a missing state check fails', () => {
  const rows = goodRows().filter((r) => !(r.state === 'forbidden' && r.width === '390' && r.scheme === 'light' && r.screen === 'screen-a'))
  assert.ok(evaluate(rows, { screens: SCREENS }).failures.some((f) => f.includes('missing forbidden check screen-a@390/light')))
})

test('horizontal overflow fails', () => {
  assert.ok(run((rows) => { capture(rows).overflow = true }).failures.some((f) => f.startsWith('horizontal overflow')))
})

test('a page without exactly one h1 fails', () => {
  assert.ok(run((rows) => { capture(rows).h1 = 0 }).failures.some((f) => f.includes('exactly one h1')))
  assert.ok(run((rows) => { capture(rows).h1 = 2 }).failures.some((f) => f.includes('exactly one h1')))
})

test('a redirect to login or 403 fails', () => {
  assert.ok(run((rows) => { capture(rows).redirected = true }).failures.some((f) => f.includes('redirected')))
})

test('a console error fails', () => {
  assert.ok(run((rows) => { capture(rows).consoleErrors = 1 }).failures.some((f) => f.includes('console error')))
})

test('a serious or critical axe violation fails', () => {
  const failures = run((rows) => {
    const r = capture(rows)
    r.axeSerious = 1
    r.axeViolations = [{ id: 'image-alt', impact: 'critical', nodes: 1 }]
  }).failures
  assert.ok(failures.some((f) => f.includes('axe serious or critical: image-alt')))
})

test('a minor axe violation does not fail by itself', () => {
  const result = run((rows) => { capture(rows).axeViolations = [{ id: 'region', impact: 'moderate', nodes: 1 }] })
  assert.deepEqual(result.failures, [])
})

test('no keyboard stop and invisible focus both fail', () => {
  assert.ok(run((rows) => { capture(rows).focusStops = 0 }).failures.some((f) => f.includes('keyboard reached no control')))
  assert.ok(run((rows) => { capture(rows).invisibleFocus = 2 }).failures.some((f) => f.includes('without a visible ring')))
})

test('an error state without alert or retry fails', () => {
  assert.ok(run((rows) => { stateRow(rows, 'error').alert = false }).failures.some((f) => f.includes('error state without alert and retry')))
  assert.ok(run((rows) => { stateRow(rows, 'error').retry = false }).failures.some((f) => f.includes('error state without alert and retry')))
})

test('a forbidden state without an alert, or shown as an empty list, fails', () => {
  assert.ok(run((rows) => { stateRow(rows, 'forbidden').alert = false }).failures.some((f) => f.includes('forbidden state without alert')))
  assert.ok(run((rows) => { stateRow(rows, 'forbidden').emptyState = true }).failures.some((f) => f.includes('shown as an empty list')))
})

test('a server error shown as an empty list fails', () => {
  assert.ok(run((rows) => { stateRow(rows, 'error').emptyState = true }).failures.some((f) => f.includes('server error shown as an empty list')))
})

test('an empty answer without an empty panel fails unless the screen is exempt', () => {
  const mutate = (rows) => { stateRow(rows, 'empty').emptyState = false }
  assert.ok(run(mutate).failures.some((f) => f.includes('empty state without an empty panel')))
  assert.deepEqual(run(mutate, { emptyExempt: ['screen-a'] }).failures, [])
})

test('blocked work is always listed and names the missing pieces', () => {
  assert.ok(BLOCKED.length >= 4)
  assert.ok(BLOCKED.some((item) => item.includes('NX-11')))
  assert.ok(BLOCKED.some((item) => item.includes('contract')))
})
