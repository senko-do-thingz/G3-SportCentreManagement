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

for (const scheme of ['light', 'dark']) {
  const page = await open('member', scheme)
  for (const [w, [vw, vh]] of Object.entries(SIZES)) {
    await page.setViewport({ width: vw, height: vh })
    await page.evaluate(() => localStorage.removeItem('sportify.assistant.optIn.2'))
    await page.goto(BASE + '/member/profile', { waitUntil: 'networkidle0' }); await settle(page)
    await page.screenshot({ path: OUT + '/profile-entry_' + w + '_' + scheme + '.png', fullPage: true })
    checks.push({ step: 'profile entry', w, scheme, overflow: await overflow(page), entry: !!(await page.$('section[aria-label=Assistant]')) })
    await page.click('section[aria-label=Assistant] a')
    await page.waitForSelector('[aria-label="Before you start"]')
    await page.screenshot({ path: OUT + '/assistant-consent_' + w + '_' + scheme + '.png', fullPage: true })
    checks.push({ step: 'consent', w, scheme, overflow: await overflow(page) })
    // return navigation: Back goes to the page the member came from
    await page.evaluate(() => [...document.querySelectorAll('a')].find((a) => a.textContent === 'Back').click())
    await page.waitForFunction(() => location.pathname === '/member/profile')
    checks.push({ step: 'back returns to profile', w, scheme, ok: true })
    await page.click('section[aria-label=Assistant] a')
    await page.waitForSelector('[aria-label="Before you start"]')
    await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => /Turn on/.test(b.textContent)).click())
    await page.waitForSelector('[data-slot=assistant-chat]')
    await page.screenshot({ path: OUT + '/assistant-on_' + w + '_' + scheme + '.png', fullPage: true })
    checks.push({ step: 'assistant on', w, scheme, overflow: await overflow(page) })
    await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => /Turn off/.test(b.textContent)).click())
    await page.waitForFunction(() => location.pathname === '/member/profile')
    await settle(page)
    checks.push({ step: 'turned off hides entry', w, scheme, entryGone: !(await page.$('section[aria-label=Assistant]')) })
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
console.log('overflow:', checks.filter((c) => c.overflow).length, '| entry shown:', checks.filter((c) => c.step === 'profile entry' && c.entry).length + '/' + checks.filter((c) => c.step === 'profile entry').length, '| back ok:', checks.filter((c) => c.step.startsWith('back')).length, '| off hides entry:', checks.filter((c) => c.entryGone).length)
console.log(JSON.stringify(checks.filter((c) => c.denied !== undefined || c.path !== undefined)))
