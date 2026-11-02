// NX-31 screenshot and flow check. Setup outside the project: npm install --no-save puppeteer-core
// Start the app with the flag on:  VITE_ASSISTANT_ENABLED=true npx vite --port 5175
// Run: node assistant-screens.mjs <outDir> [baseUrl]. API answers are mocked in the browser.
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'

const OUT = process.argv[2]
const BASE = process.argv[3] ?? 'http://localhost:5175'
fs.mkdirSync(OUT, { recursive: true })
const CHROME = process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const users = {
  member: { id: 2, fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', role: 'MEMBER', status: 'ACTIVE' },
  coach: { id: 3, fullName: 'Daniel Smith', email: 'daniel@sportify.demo', phone: '0901234568', role: 'COACH', status: 'ACTIVE' },
}
const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' }
const SIZES = { 390: [390, 844], 768: [768, 1024], 1440: [1440, 900] }
const FROM = '/member/training/plans?view=week'
const checks = []
const browser = await puppeteer.launch({ executablePath: CHROME, headless: true })

async function open(role, scheme, signedIn = true) {
  const ctx = await browser.createBrowserContext()
  const page = await ctx.newPage()
  await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: scheme }])
  await page.setRequestInterception(true)
  page.on('request', (req) => {
    const u = req.url()
    if (!u.includes('localhost:8080/api/v1')) return req.continue()
    if (req.method() === 'OPTIONS') return req.respond({ status: 204, headers: cors })
    const path = u.split('/api/v1')[1]
    const body = path.startsWith('/users/me') ? users[role] : []
    req.respond({ status: 200, headers: { ...cors, 'content-type': 'application/json' }, body: JSON.stringify(body) })
  })
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  await page.evaluate((on) => {
    localStorage.clear()
    if (on) { localStorage.setItem('sportify.accessToken', 'x'); localStorage.setItem('sportify.refreshToken', 'y'); localStorage.setItem('sportify.sessionId', 'z') }
  }, signedIn)
  return page
}
const settle = (page) => page.waitForNetworkIdle({ idleTime: 500, timeout: 8000 }).catch(() => {})
const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
const clickText = (page, tag, re) => page.evaluate((t, source) => { const rx = new RegExp(source); const el = [...document.querySelectorAll(t)].find((e) => rx.test(e.textContent)); if (!el) throw new Error('missing ' + source); el.click() }, tag, re.source)

for (const scheme of ['light', 'dark']) {
  const page = await open('member', scheme)
  for (const [w, [vw, vh]] of Object.entries(SIZES)) {
    await page.setViewport({ width: vw, height: vh })
    await page.evaluate(() => localStorage.removeItem('sportify.assistant.optIn.2'))
    // 1. the header link exists on a member page (menu button on small screens)
    await page.goto(BASE + FROM, { waitUntil: 'networkidle0' }); await settle(page)
    const menu = await page.$('button[aria-label="Open member navigation"]')
    if (menu) await menu.click()
    const linkShown = await page.evaluate(() => [...document.querySelectorAll('a')].some((a) => /Open AI Assistant/.test(a.textContent)))
    checks.push({ step: 'header link shown', w, scheme, linkShown })
    // 2. open it from the page: consent first
    await clickText(page, 'a', /Open AI Assistant/)
    await page.waitForSelector('[aria-label="Before you start"]')
    await page.screenshot({ path: OUT + '/assistant-consent_' + w + '_' + scheme + '.png', fullPage: true })
    checks.push({ step: 'consent shown', w, scheme, overflow: await overflow(page) })
    // 3. Not now returns to the origin page with its query
    await clickText(page, 'a', /^Not now$/)
    await page.waitForFunction((from) => location.pathname + location.search === from, {}, FROM)
    checks.push({ step: 'Not now returns to origin', w, scheme, ok: true })
    // 4. turn on, chat slot, then turn off returns to origin
    await page.goto(BASE + '/member/assistant?returnTo=' + encodeURIComponent(FROM), { waitUntil: 'networkidle0' }); await settle(page)
    await clickText(page, 'button', /Turn on the assistant/)
    await page.waitForFunction(() => /Chat is not connected yet/.test(document.body.textContent))
    await page.screenshot({ path: OUT + '/assistant-on_' + w + '_' + scheme + '.png', fullPage: true })
    checks.push({ step: 'assistant on', w, scheme, overflow: await overflow(page) })
    await clickText(page, 'button', /Turn off the assistant/)
    await page.waitForFunction((from) => location.pathname + location.search === from, {}, FROM)
    checks.push({ step: 'Turn off returns to origin', w, scheme, ok: true })
  }
  await page.close()
}
// wrong role and signed out (light, 1440)
const coach = await open('coach', 'light')
await coach.setViewport({ width: 1440, height: 900 })
await coach.goto(BASE + '/member/assistant', { waitUntil: 'networkidle0' }); await settle(coach)
await coach.screenshot({ path: OUT + '/wrong-role-coach_1440_light.png' })
checks.push({ step: 'coach denied', denied: await coach.evaluate(() => document.querySelector('h1')?.textContent) })
const anon = await open('member', 'light', false)
await anon.setViewport({ width: 1440, height: 900 })
await anon.goto(BASE + '/member/assistant', { waitUntil: 'networkidle0' }); await settle(anon)
await anon.screenshot({ path: OUT + '/signed-out_1440_light.png' })
checks.push({ step: 'signed out', path: await anon.evaluate(() => location.pathname) })
await browser.close()
fs.writeFileSync(OUT + '/report.json', JSON.stringify(checks, null, 1))
const n = (s, k) => checks.filter((c) => c.step === s && (k ? c[k] : true)).length
console.log('overflow:', checks.filter((c) => c.overflow).length, '| header link:', n('header link shown', 'linkShown') + '/6', '| consent:', n('consent shown') + '/6', '| Not now ok:', n('Not now returns to origin', 'ok') + '/6', '| Turn off ok:', n('Turn off returns to origin', 'ok') + '/6')
console.log(JSON.stringify(checks.filter((c) => c.denied !== undefined || c.path !== undefined)))
