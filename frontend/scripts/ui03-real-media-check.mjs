#!/usr/bin/env node
/*
 * UI-03 real media, dark theme and layout shift check (Node 22 and an installed Chrome or Edge, no npm packages).
 * Complements scripts/ui03-media-check.py, which uses solid placeholder photos in light theme only.
 *
 * Drives the browser through the Chrome DevTools Protocol and records, for two running builds:
 *   1. parity  - Home at 390, 768 and 1440 px and Photo credits at 390 and 1440 px in light and dark, before vs after,
 *                with deterministic placeholder photos (one SVG color per photo id) and reduced motion: full-page
 *                screenshots compared pixel by pixel (before/after sheets saved as JPEG).
 *   2. real    - Home and member Find Classes (session cards) with the real Unsplash photos over the real network,
 *                light and dark, 390, 768 and 1440 px and a 390 px 3x phone: broken and upscaled images, cumulative
 *                layout shift (CLS) on load and after scrolling through every lazy image, LCP, and the bytes actually
 *                transferred for images (encodedDataLength, cache disabled). Screenshots saved as JPEG.
 *   3. bytes   - Home in light theme, real network, before vs after, three runs each: median image bytes, image
 *                requests and LCP, so the savings are reported in transferred bytes, not only requested pixel area.
 *   4. stress  - Home and Find Classes at 390 and 1440 px with a slow network (150 ms, 1.6 Mbit/s) and with the photo
 *                CDN blocked (bundled fallbacks): CLS and broken images.
 * The API is answered with fixed JSON (same fixtures as ui03-media-check.py) and the hero video CDN is blocked.
 *
 * Usage (PowerShell, from frontend/; full steps in docs/UI-03-test-cases.md):
 *   $env:VITE_API_BASE_URL='http://localhost:8080/api/v1'; npm run build -- --outDir ../../dist-after
 *   (the merge base built the same way into ../../dist-before), then in two other windows:
 *   npx vite preview --outDir ../../dist-before --port 4301 --strictPort
 *   npx vite preview --outDir ../../dist-after --port 4302 --strictPort
 *   node scripts/ui03-real-media-check.mjs --before http://localhost:4301 --after http://localhost:4302 --out docs/screenshots/ui-03/real
 * Set CHROME_PATH when Chrome or Edge is not in a standard location. Exit code 0 when every check passes.
 */

import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CLS_LIMIT = 0.1 // "good" threshold of Core Web Vitals
const VIEWPORTS = [
  { name: '390', width: 390, height: 844, dpr: 1 },
  { name: '768', width: 768, height: 1024, dpr: 1 },
  { name: '1440', width: 1440, height: 900, dpr: 1 },
]
const PHONE_3X = { name: '390-3x', width: 390, height: 844, dpr: 3 }
const THEMES = ['light', 'dark']
const SLOW = { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 }

const MEMBER = { id: 2, email: 'member@sportify.demo', fullName: 'Taylor Nguyen', phone: '0901234567', role: 'MEMBER', status: 'ACTIVE' }
const SPORT_NAMES = ['Football', 'Badminton', 'Basketball', 'Volleyball', 'Swimming', 'Tennis']
const SPORTS = SPORT_NAMES.map((name, index) => ({ id: index + 1, code: name.toUpperCase(), name }))
const SESSIONS = SPORT_NAMES.map((name, index) => ({
  id: index + 1, sportId: index + 1, sportName: name, classId: null, className: `${name} Foundations`, trainingFormat: 'COACH_LED',
  level: 'BEGINNER', coachId: null, coachName: 'Quan Coach', facilityId: null, facilityName: 'Court A', sessionDate: '2026-10-30',
  startTime: '16:00:00', endTime: '17:00:00', capacity: 20, bookedCount: 2, freeSeats: 18, full: false, status: 'PUBLISHED',
}))
const FIXTURES = {
  '/users/me': MEMBER,
  '/sports': SPORTS,
  '/packages': [{ id: 81, name: 'Football Independent', sportId: 1, sportName: 'Football', trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, priceAmount: 500000, isActive: true }],
  '/membership-cards/tiers': [{ id: 1, name: 'Gold', discountPercentage: 5, isActive: true }],
  '/coaches/public': [{ id: 22, fullName: 'Le Hoang Nam', headline: null, specialties: null, yearsExperience: 4, sportIds: [1] }],
  '/facilities/public': SPORT_NAMES.map((name, index) => ({ id: 61 + index, name: `${name} Court`, facilityType: name === 'Swimming' ? 'POOL' : 'COURT', sportId: index + 1, capacity: 8 })),
  '/sessions': { content: SESSIONS, page: 0, size: 20, totalElements: SESSIONS.length, totalPages: 1 },
}

function apiBody(url) {
  const bare = url.split('/api/v1')[1].split('?')[0]
  if (bare in FIXTURES) return { status: 200, body: FIXTURES[bare] }
  if (bare.startsWith('/coaches')) return { status: 200, body: [] }
  return { status: 404, body: { message: 'Not found' } }
}

/** SVG of the requested size whose color depends only on the photo id, so every width of a photo looks the same. */
function placeholder(url) {
  const photo = /photo-[\w-]+/.exec(url)?.[0] ?? url
  const width = Number(/[?&]w=(\d+)/.exec(url)?.[1] ?? 800)
  const height = Number(/[?&]h=(\d+)/.exec(url)?.[1] ?? Math.round((width * 2) / 3))
  const color = createHash('sha1').update(photo).digest('hex').slice(0, 6)
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="#${color}"/></svg>`
}

// Runs in the page before any app code: theme, member session, and layout shift and LCP observers.
const INIT = (theme, member) => `try {
  localStorage.setItem('sportify.themePreference', '${theme}')
  ${member ? "localStorage.setItem('sportify.accessToken', 'ui03-access'); localStorage.setItem('sportify.refreshToken', 'ui03-refresh')" : ''}
} catch {}
window.__ui03 = { cls: 0, clsOnLoad: null, shifts: [], lcp: null }
const describe = (node) => node ? (node.getAttribute?.('class') || node.nodeName) : null
new PerformanceObserver((list) => { for (const entry of list.getEntries()) if (!entry.hadRecentInput) {
  window.__ui03.cls += entry.value
  window.__ui03.shifts.push({ value: Number(entry.value.toFixed(4)), at: Math.round(entry.startTime), sources: (entry.sources || []).map((s) => describe(s.node)).slice(0, 3) })
} }).observe({ type: 'layout-shift', buffered: true })
new PerformanceObserver((list) => { const last = list.getEntries().at(-1)
  window.__ui03.lcp = { at: Math.round(last.startTime), element: describe(last.element), url: (last.url || '').replace(/[?].*/, '') }
}).observe({ type: 'largest-contentful-paint', buffered: true })`

// Same rule as ui03-media-check.py: the file width comes from the URL (srcset w-descriptor), upscaled below 75%.
const AUDIT = (dpr) => `[...document.images].filter((img) => img.getBoundingClientRect().width > 0).map((img) => {
  const rect = img.getBoundingClientRect(); const src = img.currentSrc || img.src
  const fileWidth = Number(/[?&]w=(\\d+)/.exec(src)?.[1]) || img.naturalWidth
  return { src: src.replace(location.origin, '').replace(/[?].*$/, ''), rendered: [Math.round(rect.width), Math.round(rect.height)], fileWidth,
    broken: img.complete && img.naturalWidth === 0, upscaled: fileWidth > 0 && fileWidth < Math.round(rect.width * ${dpr} * 0.75) }
})`

const SCROLL = `(async () => {
  const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
  window.__ui03.clsOnLoad = Number(window.__ui03.cls.toFixed(4))
  for (let y = 0; y < document.documentElement.scrollHeight; y += innerHeight / 2) { scrollTo(0, y); await pause(120) }
  await pause(600); scrollTo(0, 0); await pause(400)
})()`

// ---- Chrome and the DevTools Protocol -------------------------------------------------------------------------

function findChrome() {
  const candidates = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser']
  const found = candidates.find((path) => path && existsSync(path))
  if (!found) throw new Error('Chrome or Edge not found; set CHROME_PATH')
  return found
}

async function launch() {
  const profile = mkdtempSync(join(tmpdir(), 'ui03-chrome-'))
  // The fixed API answers come from the protocol, not a server, so CORS is off in this throwaway profile (the app calls
  // the API on another port with an Authorization header; Playwright adds the CORS headers itself, the protocol does not).
  const chrome = spawn(findChrome(), ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run',
    '--no-default-browser-check', '--hide-scrollbars', '--mute-audio', '--disable-extensions', '--disable-web-security', 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] })
  const endpoint = await new Promise((resolve, reject) => {
    let text = ''
    chrome.stderr.on('data', (chunk) => {
      text += chunk
      const match = /DevTools listening on (ws:\/\/\S+)/.exec(text)
      if (match) resolve(match[1])
    })
    chrome.on('exit', () => reject(new Error(`Chrome exited: ${text}`)))
  })
  const socket = new WebSocket(endpoint)
  await new Promise((resolve) => socket.addEventListener('open', resolve, { once: true }))
  let id = 0
  const pending = new Map()
  const listeners = new Set()
  socket.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data)
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) reject(new Error(`${message.error.message} (${message.error.data ?? ''})`))
      else resolve(message.result)
    } else for (const listener of listeners) listener(message)
  })
  const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
    id += 1
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params, sessionId }))
  })
  const close = () => { socket.close(); chrome.kill(); try { rmSync(profile, { recursive: true, force: true }) } catch { /* profile still locked */ } }
  return { send, listeners, close }
}

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** Opens one page in a fresh browser context and returns what was measured. */
async function visit(browser, { base, path, viewport, theme, member = false, photos = 'real', slow = false, reduceMotion = false, screenshot = null }) {
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })
  const send = (method, params) => browser.send(method, params, sessionId)
  const inflight = new Map()
  const types = new Map()
  const net = { imageBytes: 0, imageRequests: 0, videos: [] }
  let lastActivity = Date.now()
  let loaded = false

  const listener = async ({ method, params, sessionId: from }) => {
    if (from !== sessionId) return
    if (method === 'Page.loadEventFired') loaded = true
    if (method === 'Network.requestWillBeSent' && !params.request.url.startsWith('data:')) { inflight.set(params.requestId, params.request.url); lastActivity = Date.now() }
    if (method === 'Network.responseReceived') types.set(params.requestId, params.type)
    if (method === 'Network.loadingFinished' || method === 'Network.loadingFailed') {
      if (method === 'Network.loadingFinished' && types.get(params.requestId) === 'Image') { net.imageBytes += params.encodedDataLength; net.imageRequests += 1 }
      inflight.delete(params.requestId)
      lastActivity = Date.now()
    }
    if (method === 'Fetch.requestPaused') {
      const { requestId, request } = params
      try {
        if (request.url.includes('/api/v1/')) {
          const { status, body } = apiBody(request.url)
          await send('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
            body: Buffer.from(JSON.stringify(body)).toString('base64') })
        } else if (request.url.includes('mixkit.co')) {
          net.videos.push(request.url.split('/').pop())
          await send('Fetch.failRequest', { requestId, errorReason: 'BlockedByClient' })
        } else if (photos === 'offline') {
          await send('Fetch.failRequest', { requestId, errorReason: 'InternetDisconnected' })
        } else {
          await send('Fetch.fulfillRequest', { requestId, responseCode: 200, responseHeaders: [{ name: 'Content-Type', value: 'image/svg+xml' }],
            body: Buffer.from(placeholder(request.url)).toString('base64') })
        }
      } catch { /* target closed */ }
    }
  }
  browser.listeners.add(listener)
  try {
    await send('Page.enable')
    await send('Network.enable')
    await send('Network.setCacheDisabled', { cacheDisabled: true })
    if (slow) await send('Network.emulateNetworkConditions', SLOW)
    await send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: viewport.dpr, mobile: false })
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: theme }, { name: 'prefers-reduced-motion', value: reduceMotion ? 'reduce' : 'no-preference' }] })
    await send('Page.addScriptToEvaluateOnNewDocument', { source: INIT(theme, member) })
    const patterns = [{ urlPattern: '*/api/v1/*' }, { urlPattern: '*mixkit.co*' }]
    if (photos !== 'real') patterns.push({ urlPattern: '*images.unsplash.com*' })
    await send('Fetch.enable', { patterns })
    await send('Page.navigate', { url: base + path })
    const deadline = Date.now() + (slow ? 90000 : 30000)
    const idle = async () => { while (Date.now() < deadline && (!loaded || inflight.size > 0 || Date.now() - lastActivity < 700)) await pause(100) }
    await idle()
    const evaluate = async (expression) => (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result.value
    await evaluate('document.fonts.ready.then(() => true)')
    await evaluate(SCROLL)
    await idle()
    await pause(300)
    const measured = await evaluate(`({ ...window.__ui03, images: ${AUDIT(viewport.dpr)}, theme: document.documentElement.dataset.theme ?? null })`)
    if (screenshot) {
      await evaluate(`(() => { const style = document.createElement('style'); style.textContent = '*, *::before, *::after { animation: none !important; transition: none !important; caret-color: transparent !important; } video { display: none !important; }'; document.head.append(style); return true })()`)
      await pause(250)
      const { cssContentSize } = await send('Page.getLayoutMetrics')
      const shot = await send('Page.captureScreenshot', { format: screenshot.format, quality: screenshot.format === 'jpeg' ? 55 : undefined, captureBeyondViewport: true,
        clip: { x: 0, y: 0, width: viewport.width, height: Math.ceil(cssContentSize.height), scale: 1 } })
      measured.screenshot = shot.data
    }
    return { ...measured, cls: Number(measured.cls.toFixed(4)), net, timedOut: Date.now() >= deadline }
  } finally {
    browser.listeners.delete(listener)
    await browser.send('Target.disposeBrowserContext', { browserContextId }).catch(() => {})
  }
}

/** Pixel comparison of two PNG screenshots in a blank page, plus a before | after JPEG sheet. */
async function compareShots(browser, before, after) {
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })
  try {
    const expression = `(async () => {
      const load = (data) => new Promise((resolve, reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = 'data:image/png;base64,' + data })
      const [a, b] = await Promise.all([load('${before}'), load('${after}')])
      const pixels = (image) => { const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height
        const context = canvas.getContext('2d'); context.drawImage(image, 0, 0); return context.getImageData(0, 0, image.width, image.height).data }
      let changed = null
      if (a.width === b.width && a.height === b.height) {
        const pa = pixels(a); const pb = pixels(b); changed = 0
        for (let i = 0; i < pa.length; i += 4) if (pa[i] !== pb[i] || pa[i + 1] !== pb[i + 1] || pa[i + 2] !== pb[i + 2]) changed += 1
      }
      const scale = Math.min(1, 1600 / (a.width + b.width + 24))
      const sheet = document.createElement('canvas'); sheet.width = Math.round((a.width + b.width + 24) * scale); sheet.height = Math.round(Math.max(a.height, b.height) * scale)
      const context = sheet.getContext('2d'); context.fillStyle = '#808080'; context.fillRect(0, 0, sheet.width, sheet.height)
      context.drawImage(a, 0, 0, a.width * scale, a.height * scale); context.drawImage(b, (a.width + 24) * scale, 0, b.width * scale, b.height * scale)
      return { identical: changed === 0, changed_pixels: changed, size: [b.width, b.height], before_size: [a.width, a.height], sheet: sheet.toDataURL('image/jpeg', 0.6).split(',')[1] }
    })()`
    return (await browser.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }, sessionId)).result.value
  } finally {
    await browser.send('Target.disposeBrowserContext', { browserContextId }).catch(() => {})
  }
}

const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]

async function main() {
  const option = (name) => { const index = process.argv.indexOf(`--${name}`); return index > 0 ? process.argv[index + 1] : undefined }
  const builds = { before: option('before')?.replace(/\/$/, ''), after: option('after')?.replace(/\/$/, '') }
  const out = option('out')
  if (!builds.before || !builds.after || !out) throw new Error('usage: --before URL --after URL --out DIR')
  mkdirSync(out, { recursive: true })
  const report = { environment: {}, parity: [], real: [], bytes: {}, stress: [], failures: [] }
  const fail = (text) => report.failures.push(text)
  const browser = await launch()
  try {
    report.environment = { browser: (await browser.send('Browser.getVersion')).product, node: process.version, cls_limit: CLS_LIMIT, slow_network: SLOW }

    // 1. parity: light and dark, placeholder photos, reduced motion
    const parityScreens = [...VIEWPORTS.map((viewport) => ({ page: 'home', path: '/', viewport })),
      ...[VIEWPORTS[0], VIEWPORTS[2]].map((viewport) => ({ page: 'credits', path: '/credits', viewport }))]
    for (const theme of THEMES) {
      for (const { page, path, viewport } of parityScreens) {
        const shots = {}
        for (const [build, base] of Object.entries(builds)) {
          const result = await visit(browser, { base, path, viewport, theme, photos: 'placeholder', reduceMotion: true, screenshot: { format: 'png' } })
          shots[build] = result.screenshot
          if (result.theme !== theme) fail(`parity ${page}-${theme}-${viewport.name} ${build}: data-theme is ${result.theme}`)
        }
        const { sheet, ...comparison } = await compareShots(browser, shots.before, shots.after)
        const name = `${page}-${theme}-${viewport.name}`
        writeFileSync(join(out, `parity-${name}-before-after.jpg`), Buffer.from(sheet, 'base64'))
        report.parity.push({ screen: name, ...comparison })
        if (!comparison.identical) fail(`parity ${name}: screenshot differs (${comparison.changed_pixels ?? 'size'} pixels)`)
        console.log(`parity ${name}: ${comparison.identical ? 'identical' : `differs (${comparison.changed_pixels})`}`)
      }
    }

    // 2. real photos, real network, light and dark, CLS and LCP
    for (const { page, path, member } of [{ page: 'home', path: '/' }, { page: 'find-classes', path: '/member/classes', member: true }]) {
      for (const theme of THEMES) {
        for (const viewport of [...VIEWPORTS, PHONE_3X]) {
          const name = `${page}-${theme}-${viewport.name}`
          const result = await visit(browser, { base: builds.after, path, viewport, theme, member, screenshot: viewport.dpr === 1 ? { format: 'jpeg' } : null })
          if (result.screenshot) writeFileSync(join(out, `real-${name}.jpg`), Buffer.from(result.screenshot, 'base64'))
          const broken = result.images.filter((image) => image.broken).map((image) => image.src)
          const upscaled = result.images.filter((image) => image.upscaled).map((image) => `${image.src} (${image.fileWidth} px file in ${image.rendered[0]} px slot)`)
          const row = { screen: name, theme: result.theme, images: result.images.length, broken, upscaled, cls_on_load: result.clsOnLoad, cls_after_scroll: result.cls,
            shifts: result.shifts, lcp: result.lcp, image_bytes: result.net.imageBytes, image_requests: result.net.imageRequests, timed_out: result.timedOut }
          report.real.push(row)
          if (result.theme !== theme) fail(`real ${name}: data-theme is ${result.theme}`)
          if (!result.images.length) fail(`real ${name}: no images rendered`)
          if (broken.length) fail(`real ${name}: broken ${broken.join(', ')}`)
          if (upscaled.length) fail(`real ${name}: upscaled ${upscaled.join(', ')}`)
          if (result.cls > CLS_LIMIT) fail(`real ${name}: CLS ${result.cls} above ${CLS_LIMIT}`)
          console.log(`real ${name}: ${result.images.length} images, CLS ${result.cls}, LCP ${result.lcp?.at} ms, ${Math.round(result.net.imageBytes / 1024)} KiB images`)
        }
      }
    }

    // 3. transferred image bytes and LCP on Home, before vs after, median of three runs
    for (const viewport of [...VIEWPORTS, PHONE_3X]) {
      const row = {}
      for (const [build, base] of Object.entries(builds)) {
        const runs = []
        for (let run = 0; run < 3; run += 1) runs.push(await visit(browser, { base, path: '/', viewport, theme: 'light' }))
        row[build] = { image_bytes: median(runs.map((r) => r.net.imageBytes)), image_requests: median(runs.map((r) => r.net.imageRequests)),
          lcp_ms: median(runs.map((r) => r.lcp?.at ?? 0)), runs: runs.map((r) => ({ image_bytes: r.net.imageBytes, lcp_ms: r.lcp?.at ?? null })) }
      }
      row.image_bytes_change = `${Math.round(((row.after.image_bytes - row.before.image_bytes) / row.before.image_bytes) * 100)}%`
      report.bytes[viewport.name] = row
      if (row.after.image_bytes > row.before.image_bytes) fail(`bytes ${viewport.name}: after transfers more image bytes than before`)
      console.log(`bytes home-${viewport.name}: ${Math.round(row.before.image_bytes / 1024)} -> ${Math.round(row.after.image_bytes / 1024)} KiB (${row.image_bytes_change}), LCP ${row.before.lcp_ms} -> ${row.after.lcp_ms} ms`)
    }

    // 4. slow network and blocked photo CDN: layout shift and fallbacks
    for (const { page, path, member } of [{ page: 'home', path: '/' }, { page: 'find-classes', path: '/member/classes', member: true }]) {
      for (const viewport of [VIEWPORTS[0], VIEWPORTS[2]]) {
        for (const mode of ['slow', 'offline']) {
          const name = `${page}-${viewport.name}-${mode}`
          const result = await visit(browser, { base: builds.after, path, viewport, theme: 'light', member, slow: mode === 'slow', photos: mode === 'offline' ? 'offline' : 'real' })
          const broken = result.images.filter((image) => image.broken).map((image) => image.src)
          report.stress.push({ screen: name, images: result.images.length, broken, cls_on_load: result.clsOnLoad, cls_after_scroll: result.cls, shifts: result.shifts, lcp: result.lcp, timed_out: result.timedOut })
          if (!result.images.length || broken.length) fail(`stress ${name}: broken ${broken.join(', ') || 'no images'}`)
          if (result.cls > CLS_LIMIT) fail(`stress ${name}: CLS ${result.cls} above ${CLS_LIMIT}`)
          console.log(`stress ${name}: ${result.images.length} images, CLS ${result.cls}${result.timedOut ? ' (timed out waiting for idle)' : ''}`)
        }
      }
    }
  } finally {
    browser.close()
  }
  writeFileSync(join(out, 'report.json'), `${JSON.stringify(report, null, 2)}\n`)
  for (const line of report.failures) console.log('FAIL', line)
  console.log(`${report.parity.length} parity pairs, ${report.real.length} real-network screens, ${report.stress.length} stress runs, ${report.failures.length} failures`)
  return report.failures.length ? 1 : 0
}

main().then((code) => process.exit(code), (error) => { console.error(error); process.exit(2) })
