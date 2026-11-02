// NX-18 Flow 4 cross-role QA runner for the integrated screens of PR 185 (member, coach and reception training pages).
// Setup (outside the project dependencies): npm install --no-save puppeteer-core axe-core
// Run a dev server of the branch under test (npx vite --port 5175), then: node flow4-qa.mjs <outDir> [baseUrl]
// API answers are mocked in the browser (request interception); no backend is needed.
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const axeSource = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8')

const OUT = process.argv[2]
const BASE = process.argv[3] ?? 'http://localhost:5175'
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

const plan = { id: 1, title: 'Basketball fundamentals', description: 'Ball control.', className: 'Basketball Foundations', coachName: 'Daniel Smith' }
const sess = { id: 900, className: 'Basketball Foundations', date: day(-2), startTime: '09:00:00', endTime: '10:00:00' }
const staffSession = { ...sess, classId: 1, sportId: 1, trainingType: 'COACH_LED' }
const roster = [{ bookingId: 1, memberId: 11, memberName: 'Linh Pham', attendanceId: null, status: null, notes: null }]
function answer(role, path, mode) {
  if (path.startsWith('/users/me')) return [200, users[role]]
  if (mode === 'error') return [503, { status: 503, message: 'The service is temporarily unavailable.' }]
  if (mode === 'forbidden') return [403, { status: 403, message: 'You do not have permission to do this.' }]
  const empty = mode === 'empty'
  const list = (rows) => [200, empty ? [] : rows]
  if (path.startsWith('/member/training/plans/')) return [200, { plan, steps: [{ id: 1, stepOrder: 1, title: 'Warm-up', durationMinutes: 10, instructions: 'Shoulder rotations' }], feedback: [] }]
  if (path.startsWith('/member/training/plans')) return list([plan])
  if (path.startsWith('/member/training/feedback')) return list([{ id: 1, planId: 1, planTitle: 'Basketball fundamentals', coachName: 'Daniel Smith', rating: 4, comments: 'Good progress.', strengths: 'Dribbling', areasForImprovement: 'Shooting form', createdAt: day(-1) + 'T18:00:00' }])
  if (path.startsWith('/member/training/attendance')) return list([{ id: 1, session: sess, status: 'PRESENT', notes: null }, { id: 2, session: sess, status: 'LATE', notes: 'Arrived 5 minutes late' }])
  if (path.startsWith('/member/training/results/')) return [200, { result: { id: 5, session: sess, notes: 'Steady.' }, scores: [{ id: 1, metricName: 'Free throws', description: null, score: 14, maxScore: 20 }] }]
  if (path.startsWith('/member/training/results')) return list([{ id: 5, session: sess, notes: 'Steady.' }])
  if (path.startsWith('/coach/training/plans')) return list([{ ...plan, classId: 1, memberId: 11, memberName: 'Linh Pham' }])
  if (path.startsWith('/coach/training/classes')) return list([{ id: 1, name: 'Basketball Foundations', sportId: 1 }])
  if (path.startsWith('/coach/training/sessions/') && path.endsWith('/members')) return list(roster)
  if (path.startsWith('/coach/training/sessions')) return list([staffSession])
  if (path.startsWith('/coach/training/metrics')) return list([{ id: 1, name: 'Free throws', maxScore: 20 }])
  if (path.startsWith('/reception/training/sessions/') && path.endsWith('/members')) return list(roster)
  if (path.startsWith('/reception/training/sessions')) return list([staffSession])
  return [200, []]
}

const SCREENS = [
  { role: 'coach', name: 'coach-plans', path: '/coach/training/plans' },
  { role: 'coach', name: 'coach-results', path: '/coach/training/results' },
  { role: 'reception', name: 'reception-attendance', path: '/reception/training/attendance' },
  { role: 'member', name: 'member-plans', path: '/member/training/plans' },
  { role: 'member', name: 'member-plan-detail', path: '/member/training/plans/1' },
  { role: 'member', name: 'member-feedback', path: '/member/training/feedback' },
  { role: 'member', name: 'member-attendance', path: '/member/training/attendance' },
  { role: 'member', name: 'member-results', path: '/member/training/results' },
  { role: 'member', name: 'member-result-detail', path: '/member/training/results/5' },
]
const SIZES = { 390: [390, 844], 768: [768, 1024], 1440: [1440, 900] }
const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' }

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true })
const rows = []
for (const role of ['coach', 'reception', 'member']) {
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
    for (const screen of SCREENS.filter((s) => s.role === role)) {
      for (const [w, [vw, vh]] of Object.entries(SIZES)) {
        mode = 'ok'
        consoleErrors.length = 0
        await page.setViewport({ width: vw, height: vh })
        await page.goto(BASE + screen.path, { waitUntil: 'networkidle0' })
        await new Promise((r) => setTimeout(r, 700))
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
fs.writeFileSync(OUT + '/report.json', JSON.stringify(rows, null, 1))
const main = rows.filter((r) => !r.state)
const list = (arr, fn) => arr.map(fn).join(' | ') || 'none'
console.log('captures', main.length, '| overflow:', list(main.filter((r) => r.overflow), (r) => r.screen + '@' + r.width + '/' + r.scheme))
console.log('axe serious/critical:', list(main.filter((r) => r.axeSerious), (r) => r.screen + '@' + r.width + '/' + r.scheme + ':' + r.axeViolations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id).join('+')))
console.log('axe any:', list([...new Set(main.flatMap((r) => r.axeViolations.map((v) => v.id + '(' + v.impact + ')')))], (x) => x))
console.log('invisible focus:', list(main.filter((r) => r.invisibleFocus), (r) => r.screen + '@' + r.width + '/' + r.scheme + ':' + r.invisibleFocus + '/' + r.focusStops))
console.log('console errors:', main.filter((r) => r.consoleErrors).length, '| not exactly one h1:', list(main.filter((r) => r.h1 !== 1), (r) => r.screen + '@' + r.width), '| redirected:', main.filter((r) => r.redirected).length)
const st = rows.filter((r) => r.state)
console.log('error state without alert+retry:', list(st.filter((r) => r.state === 'error' && !(r.alert && r.retry)), (r) => r.screen + '@' + r.width))
console.log('forbidden state without alert:', list(st.filter((r) => r.state === 'forbidden' && !r.alert), (r) => r.screen + '@' + r.width))
console.log('empty state without empty panel:', list(st.filter((r) => r.state === 'empty' && !r.emptyState), (r) => r.screen + '@' + r.width))
