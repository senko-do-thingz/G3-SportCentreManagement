// NX-18 Flow 4 cross-role QA runner.
// Setup (outside the project dependencies): npm install --no-save puppeteer-core axe-core
// Run a dev server of the branch under test (npx vite --port 5175), then:
//   node flow4-qa.mjs <outDir> [baseUrl] [--inject-defect=<name>]
// API answers are mocked in the browser (request interception); no backend is needed.
//
// Exit code: 0 only when every required check passes (see evaluate.mjs); 1 when any required check
// fails or a required capture is missing. BLOCKED work is printed on every run and is never counted
// as passed. --inject-defect breaks the page on purpose (negative control); the run must then exit 1.
// Names: overflow, h1, axe, focus, console, error-alert, forbidden-alert, empty-panel.
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import { BLOCKED, evaluate } from './evaluate.mjs'
const require = createRequire(import.meta.url)
const axeSource = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')

const flags = process.argv.slice(2).filter((a) => a.startsWith('--'))
const args = process.argv.slice(2).filter((a) => !a.startsWith('--'))
const INJECT = (flags.find((f) => f.startsWith('--inject-defect=')) ?? '').split('=')[1] ?? null
const OUT = args[0]
const BASE = args[1] ?? 'http://localhost:5175'

/** Deliberate defects for the negative control. They only run when --inject-defect is given. */
async function injectCapture(page) {
  if (INJECT === 'overflow') await page.addStyleTag({ content: 'body::after{content:"";display:block;width:3000px;height:1px}' })
  if (INJECT === 'h1') await page.evaluate(() => document.querySelectorAll('h1').forEach((e) => e.remove()))
  if (INJECT === 'axe') await page.evaluate(() => { document.body.insertAdjacentHTML('beforeend', '<img src="x.png">') })
  if (INJECT === 'focus') await page.addStyleTag({ content: '*:focus,*:focus-visible{outline:none!important;box-shadow:none!important}' })
  if (INJECT === 'console') await page.evaluate(() => console.error('injected console error'))
}
async function injectState(page, state) {
  if (INJECT === 'error-alert' && state === 'error') await page.evaluate(() => document.querySelectorAll('[role=alert]').forEach((e) => e.remove()))
  if (INJECT === 'forbidden-alert' && state === 'forbidden') await page.evaluate(() => document.querySelectorAll('[role=alert]').forEach((e) => e.remove()))
  if (INJECT === 'empty-panel' && state === 'empty') await page.evaluate(() => document.querySelectorAll('.state--panel, .state--plain').forEach((e) => e.remove()))
}
fs.mkdirSync(OUT, { recursive: true })
const CHROME = process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const today = new Date()
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const day = (n) => iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() + n))
const users = {
  member: { id: 2, fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', role: 'MEMBER', status: 'ACTIVE' },
  coach: { id: 3, fullName: 'Daniel Smith', email: 'daniel@sportify.demo', phone: '0901234568', role: 'COACH', status: 'ACTIVE' },
  reception: { id: 5, fullName: 'Jamie Tran', email: 'jamie@sportify.demo', phone: '0901234570', role: 'RECEPTIONIST', status: 'ACTIVE' },
}
const sessionRow = (i) => ({ id: 800 + i, classId: 1, className: 'Basketball Foundations', sportId: 1, sportName: 'Basketball', coachId: 3, coachName: 'Daniel Smith', facilityId: 1, facilityName: 'Training Court A', sessionDate: day(i), startTime: '09:00:00', endTime: '10:00:00', capacity: 16, bookedCount: 4, status: 'PUBLISHED' })
const label = { sessionDate: day(-2), startTime: '09:00:00', endTime: '10:00:00', sportName: 'Basketball', className: 'Basketball Foundations', coachName: 'Daniel Smith', facilityName: 'Court A' }
const pageOf = (content) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 })

function answer(role, path, mode) {
  if (path.startsWith('/users/me')) return [200, users[role]]
  if (mode === 'error') return [503, { status: 503, message: 'The service is temporarily unavailable.' }]
  if (mode === 'forbidden') return [403, { status: 403, message: 'You do not have permission to do this.' }]
  const empty = mode === 'empty'
  if (path.startsWith('/coach/sessions/') && path.endsWith('/roster')) {
    return [200, empty ? [] : [{ bookingId: 1, memberCode: 'MB-001', memberName: 'Linh Pham', memberPhone: '0902000001', packageRegistrationId: 1, packageId: 1, packageName: 'Basketball 30-day', bookedAt: day(-3) + 'T10:00:00' }]]
  }
  if (path.startsWith('/coach/sessions')) return [200, empty ? [] : [0, 1, 2].map(sessionRow)]
  if (path.startsWith('/members?')) {
    return [200, pageOf(empty ? [] : [{ userId: 11, memberCode: 'MB-001', fullName: 'Linh Pham', email: 'linh@sportify.demo', phone: '0902000001', currentLevel: 'BEGINNER', status: null }])]
  }
  if (path.startsWith('/check-ins')) return [200, []]
  return [200, []]
}

// Only screens that exist on main. The Member training screens are not on main yet (see BLOCKED).
const SCREENS = [
  { role: 'coach', name: 'coach-schedule', path: '/coach' },
  { role: 'coach', name: 'coach-roster', path: '/coach/roster' },
  { role: 'reception', name: 'reception-member-search', path: '/reception?q=an' },
  { role: 'reception', name: 'reception-check-in', path: '/reception/check-in' },
]
// --only=<screen name> narrows the run (used by the negative control, which needs one screen).
const ONLY = (flags.find((f) => f.startsWith('--only=')) ?? '').split('=')[1] ?? null
const ACTIVE = ONLY ? SCREENS.filter((s) => s.name === ONLY) : SCREENS
if (ONLY && ACTIVE.length === 0) { console.log('FAIL unknown screen for --only: ' + ONLY); process.exit(1) }
const SIZES = { 390: [390, 844], 768: [768, 1024], 1440: [1440, 900] }
const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' }

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true })
const rows = []
for (const role of [...new Set(ACTIVE.map((s) => s.role))]) {
  for (const scheme of ['light', 'dark']) {
    const ctx = await browser.createBrowserContext()
    const page = await ctx.newPage()
    let mode = 'ok'
    const consoleErrors = []
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) consoleErrors.push(m.text().slice(0, 160)) })
    await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: scheme }])
    await page.setRequestInterception(true)
    page.on('request', (req) => {
      const u = req.url()
      if (!u.includes('localhost:8080/api/v1')) return req.continue()
      if (req.method() === 'OPTIONS') return req.respond({ status: 204, headers: cors })
      const [status, body] = answer(role, u.split('/api/v1')[1], mode)
      req.respond({ status, headers: { ...cors, 'content-type': 'application/json' }, body: JSON.stringify(body) })
    })
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
    await page.evaluate(() => { localStorage.setItem('sportify.accessToken', 'x'); localStorage.setItem('sportify.refreshToken', 'y'); localStorage.setItem('sportify.sessionId', 'z') })
    for (const screen of ACTIVE.filter((s) => s.role === role)) {
      for (const [w, [vw, vh]] of Object.entries(SIZES)) {
        mode = 'ok'
        consoleErrors.length = 0
        await page.setViewport({ width: vw, height: vh })
        await page.goto(BASE + screen.path, { waitUntil: 'networkidle0' })
        await new Promise((r) => setTimeout(r, 700))
        await injectCapture(page)
        const base = await page.evaluate(() => ({ path: location.pathname, h1: document.querySelectorAll('h1').length, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth, redirected: location.pathname === '/403' || location.pathname === '/login' }))
        await page.evaluate(axeSource)
        const axe = await page.evaluate(async () => { const r = await window.axe.run(document, { resultTypes: ['violations'] }); return r.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })) })
        let focusStops = 0
        let invisibleFocus = 0
        await page.evaluate(() => document.activeElement && document.activeElement.blur())
        for (let i = 0; i < 40; i++) {
          await page.keyboard.press('Tab')
          const f = await page.evaluate(() => {
            const e = document.activeElement
            if (!e || e === document.body) return null
            const s = getComputedStyle(e)
            return { ring: (s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0) || (s.boxShadow && s.boxShadow !== 'none') }
          })
          if (!f) break
          focusStops++
          if (!f.ring) invisibleFocus++
        }
        rows.push({ role, screen: screen.name, scheme, width: w, ...base, axeViolations: axe, axeSerious: axe.filter((v) => v.impact === 'serious' || v.impact === 'critical').length, focusStops, invisibleFocus, consoleErrors: consoleErrors.length })
        if (scheme === 'light' || w === '1440') await page.screenshot({ path: OUT + '/' + screen.name + '_' + w + '_' + scheme + '.png', fullPage: true })
        if (w === '1440' || w === '390') {
          for (const m of ['empty', 'error', 'forbidden']) {
            mode = m
            await page.goto(BASE + screen.path, { waitUntil: 'networkidle0' })
            await new Promise((r) => setTimeout(r, 700))
            await injectState(page, m)
            const st = await page.evaluate(() => ({ alert: !!document.querySelector('[role=alert]'), retry: [...document.querySelectorAll('button')].some((b) => /(try again|retry)/i.test(b.textContent)), emptyState: !!document.querySelector('.state--panel, .state--plain'), text: (document.querySelector('[role=alert]')?.textContent || '').slice(0, 90), overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth }))
            rows.push({ role, screen: screen.name, scheme, width: w, state: m, ...st })
            if (scheme === 'light') await page.screenshot({ path: OUT + '/' + screen.name + '_' + w + '_' + m + '.png', fullPage: true })
          }
        }
      }
    }
    await ctx.close()
  }
}
await browser.close()
const result = evaluate(rows, { screens: ACTIVE.map((s) => s.name) })
fs.writeFileSync(OUT + '/report.json', JSON.stringify({ injectedDefect: INJECT, failures: result.failures, blocked: BLOCKED, rows }, null, 1))
console.log(`captures: ${result.captures}, state checks: ${result.stateChecks}${INJECT ? ', injected defect: ' + INJECT : ''}`)
for (const failure of result.failures.slice(0, 40)) console.log('FAIL', failure)
if (result.failures.length > 40) console.log(`... and ${result.failures.length - 40} more`)
console.log('BLOCKED (not covered by this run, never counted as passed):')
for (const item of BLOCKED) console.log(' -', item)
console.log(result.failures.length
  ? `RESULT: FAIL, ${result.failures.length} required check(s) failed`
  : 'RESULT: all required checks passed. NX-18 is NOT complete: see BLOCKED.')
process.exitCode = result.failures.length ? 1 : 0
