// QA helper (NX-18). Usage: npm install --no-save puppeteer-core; vite --mode manager-preview --port 5175; node member-screens.mjs <outDir>
import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
const OUT = process.argv[2]; fs.mkdirSync(OUT, { recursive: true })
const base = 'http://localhost:5175'
const sizes = { 390: [390, 844], 768: [768, 1024], 1440: [1440, 900] }
const pages = { plans: '/member/training', history: '/member/training/history', results: '/member/training/history?tab=results' }
const browser = await puppeteer.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const report = []
for (const scheme of ['light', 'dark']) {
  const ctx = await browser.createBrowserContext(); const page = await ctx.newPage()
  await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: scheme }])
  await page.setViewport({ width: 1440, height: 900 })
  await page.goto(base + '/login', { waitUntil: 'networkidle0' })
  const btns = await page.$$('button')
  for (const b of btns) { if (/member browse preview/.test(await b.evaluate(e => e.textContent))) { await b.click(); break } }
  await page.waitForFunction(() => location.pathname.startsWith('/member'), { timeout: 15000 })
  const shoot = async (name, url, w, vw, vh, extra = '') => {
    await page.setViewport({ width: vw, height: vh })
    await page.goto(base + url, { waitUntil: 'networkidle0' }); await new Promise(r => setTimeout(r, 900))
    const m = await page.evaluate(() => ({ over: document.documentElement.scrollWidth > document.documentElement.clientWidth, h1: document.querySelector('h1')?.textContent }))
    report.push({ scheme, name, w, extra, ...m })
    await page.screenshot({ path: `${OUT}/${name}${extra}_${w}_${scheme}.png`, fullPage: true })
  }
  for (const [name, url] of Object.entries(pages)) for (const [w, [vw, vh]] of Object.entries(sizes)) await shoot(name, url, w, vw, vh)
  for (const state of ['empty', 'error']) for (const [name, url] of Object.entries(pages)) {
    const sep = url.includes('?') ? '&' : '?'
    await shoot(name, `${url}${sep}previewState=${state}`, 1440, 1440, 900, `_${state}`)
    await shoot(name, `${url}${sep}previewState=${state}`, 390, 390, 844, `_${state}`)
  }
  // keyboard: focus on tabs
  await page.setViewport({ width: 1440, height: 900 })
  await page.goto(base + '/member/training/history', { waitUntil: 'networkidle0' }); await new Promise(r => setTimeout(r, 900))
  await page.focus('[role=tab]'); await page.keyboard.press('ArrowRight'); await new Promise(r => setTimeout(r, 600))
  const sel = await page.evaluate(() => document.querySelector('[role=tab][aria-selected=true]')?.textContent)
  report.push({ scheme, name: 'keyboard-tab-arrow', selected: sel })
  await page.screenshot({ path: `${OUT}/history_keyboard_focus_1440_${scheme}.png` })
  await ctx.close()
}
await browser.close(); fs.writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 1))
console.log(JSON.stringify(report.filter(r => r.over || r.selected !== undefined || !r.h1)))
