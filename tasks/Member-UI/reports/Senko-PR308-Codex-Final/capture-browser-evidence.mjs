import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:4311'
const ROOT = resolve(process.cwd())
const OUT = join(ROOT, 'tasks/Member-UI/reports/Senko-PR308-Codex-Final')
const SHOTS = join(OUT, 'screenshots')
const CAPTURE_SHA = process.env.CAPTURE_SHA || 'WORKTREE_UNCOMMITTED'
const INTEGRATION_SHA = process.env.INTEGRATION_SHA || ''
const JOINT_SMOKE = process.env.JOINT_SMOKE === '1'
const wait = (ms) => new Promise((resolveWait) => setTimeout(resolveWait, ms))

const MEMBER = { id: 2, email: 'member.fixture@sportify.local', fullName: 'Taylor Nguyen', phone: '0900000002', role: 'MEMBER', status: 'ACTIVE', dateOfBirth: '2004-06-15', address: 'Ho Chi Minh City', mainGoal: 'IMPROVE_FITNESS', currentLevel: 'BEGINNER', memberCode: 'MEM-FIXTURE-02' }
const BOOKING = { id: 21, bookingCode: 'BK-FIXTURE-21', sessionId: 5, sessionCode: '201126BK01', sessionDate: '2026-11-20', startTime: '17:00:00', endTime: '18:00:00', sportName: 'Basketball', sportId: 3, classId: 7, className: 'Basketball Foundations', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MEM-FIXTURE-02', memberFullName: 'Taylor Nguyen', packageRegistrationId: 11, packageId: 4, packageName: 'Basketball 10 sessions', status: 'CONFIRMED', bookedAt: '2026-11-10T10:00:00', cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null }
const CANCELLED = { ...BOOKING, status: 'CANCELLED', cancelledAt: '2026-11-11T10:30:00', cancelledByName: 'Taylor Nguyen', cancelledByRole: 'MEMBER' }
const REGISTRATION = { id: 11, registrationCode: 'REG-FIXTURE-11', memberId: 2, packageId: 4, packageCode: 'PKG-BASKETBALL-10', packageName: 'Basketball 10 sessions', sportName: 'Basketball', trainingFormat: 'COACH_LED', channel: 'ONLINE', originalPrice: 1000000, discountPercentage: 10, paidAmount: 900000, totalSessions: 10, remainingSessions: 5, startDate: '2026-10-01', endDate: '2026-12-31', status: 'ACTIVE', paymentId: 54, paymentCode: 'PAY-FIXTURE-54', activatedAt: '2026-10-01T09:00:00', createdAt: '2026-09-30T09:00:00' }
const SESSION = { session: { id: 5, sessionId: '201126BK01', sportId: 3, sportName: 'Basketball', classId: 7, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', sessionDate: '2026-11-20', startTime: '17:00:00', endTime: '18:00:00', capacity: 20, bookedCount: 3, freeSeats: 17, full: false, status: 'PUBLISHED' }, memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: true, reason: null, suitableRegistrations: [{ registrationId: 11, registrationCode: 'REG-FIXTURE-11', packageId: 4, packageName: 'Basketball 10 sessions', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-31', remainingSessions: 5 }] }
const WALLET = { availableBalance: 650000, reservedBalance: 200000, demoAvailableBalance: 50000, demoReservedBalance: 0, currency: 'VND', updatedAt: '2026-11-11T07:00:00', activityCount: 3, notice: 'Local fixture: demo funds are not real money and cannot be withdrawn to a bank.', recentActivity: [
  { id: 3, entryType: 'WITHDRAWAL_RESERVE', pool: 'ACTUAL', amount: 200000, direction: 'DEBIT', availableAfter: 650000, reservedAfter: 200000, sourceType: 'WALLET_WITHDRAWAL', sourceId: 3, description: 'Withdrawal WD-FIXTURE-03 requested', createdAt: '2026-11-11T06:30:00' },
  { id: 2, entryType: 'TOPUP_CREDIT', pool: 'SIMULATED', amount: 50000, direction: 'CREDIT', availableAfter: 50000, reservedAfter: 0, sourceType: 'WALLET_TOPUP', sourceId: 7, description: 'Demo top-up TU-FIXTURE-07', createdAt: '2026-11-10T09:00:00' },
  { id: 1, entryType: 'PURCHASE_DEBIT', pool: 'ACTUAL', amount: 350000, direction: 'DEBIT', availableAfter: 850000, reservedAfter: 0, sourceType: 'PAYMENT', sourceId: 54, description: 'Package payment', createdAt: '2026-11-09T08:00:00' },
] }
const TOPUP = { id: 7, code: 'TU-FIXTURE-07', memberId: 2, memberCode: 'MEM-FIXTURE-02', memberName: 'Taylor Nguyen', mode: 'ACTUAL', amount: 200000, status: 'PENDING', transferReference: 'SPW-FIXTURE-07', receivedAmount: null, bankReference: null, reviewNote: null, reviewedByName: null, reviewedAt: null, ledgerEntryId: null, createdAt: '2026-11-11T06:00:00', instructions: { payable: true, notice: 'Local fixture only. The wallet remains pending until Reception verifies the transfer.', bankBin: '970436', bankName: 'Vietcombank', accountNumber: '0011000000000', accountName: 'SPORTIFY CENTER FIXTURE', amount: 200000, transferReference: 'SPW-FIXTURE-07', qrPayload: 'LOCAL-FIXTURE-NOT-A-REAL-TRANSFER' } }
const WITHDRAWAL = { id: 3, code: 'WD-FIXTURE-03', memberId: 2, memberCode: 'MEM-FIXTURE-02', memberName: 'Taylor Nguyen', pool: 'ACTUAL', amount: 200000, status: 'PENDING', bankName: 'Vietcombank', accountNumber: '****0000', accountHolder: 'TAYLOR NGUYEN', reviewNote: null, reviewedByName: null, reviewedAt: null, payoutReference: null, completedByName: null, completedAt: null, createdAt: '2026-11-11T06:30:00' }
const HISTORY_ROWS = [
  { type: 'TOPUP', id: 7, reference: 'TU-FIXTURE-07', createdAt: '2026-11-11T06:00:00', amount: 200000, direction: 'NONE', status: 'PENDING', pool: 'ACTUAL', availableAfter: null, detail: 'Awaiting Reception verification', paymentId: null, bankReference: null, settledAt: null },
  { type: 'WITHDRAWAL', id: 3, reference: 'WD-FIXTURE-03', createdAt: '2026-11-11T06:30:00', amount: 200000, direction: 'DEBIT', status: 'APPROVED', pool: 'ACTUAL', availableAfter: 650000, detail: 'Approved, bank transfer not completed', paymentId: null, bankReference: null, settledAt: null },
  { type: 'WALLET_PAYMENT', id: 54, reference: 'PAY-FIXTURE-54', createdAt: '2026-11-09T08:00:00', amount: 350000, direction: 'DEBIT', status: 'PAID', pool: 'ACTUAL', availableAfter: 850000, detail: 'Basketball package payment', paymentId: 54, bankReference: null, settledAt: '2026-11-09T08:00:00' },
]
const PAYMENT_PENDING = { id: 52, code: 'PAY-FIXTURE-52', status: 'PENDING', amount: 810000, method: 'BANK_TRANSFER', itemDescription: 'Football Foundations (12 sessions)', originalPrice: 900000, discount: 90000, paymentTime: null, memberId: 2, memberCode: 'MEM-FIXTURE-02', createdAt: '2026-11-11T05:00:00', referenceCode: 'SP-FIXTURE-52', bankTransferInstructions: { bank: 'Vietcombank', accountName: 'SPORTIFY CENTER FIXTURE', accountNumber: '0011000000000', transferContent: 'SP-FIXTURE-52' } }
const PAYMENT_SUCCESS = { ...PAYMENT_PENDING, id: 54, code: 'PAY-FIXTURE-54', status: 'SUCCESS', amount: 350000, originalPrice: 350000, discount: 0, paymentTime: '2026-11-09T08:00:00', referenceCode: 'BANK-FIXTURE-54', invoiceId: 1054, invoiceNumber: 'INV-FIXTURE-1054', bankTransferInstructions: null }
const PAYMENT_SIMULATED = { ...PAYMENT_SUCCESS, id: 56, code: 'PAY-FIXTURE-56', amount: 125000, referenceCode: 'SIM-FIXTURE-56', invoiceId: 1056, invoiceNumber: 'INV-FIXTURE-1056', settlementType: 'SIMULATED', settlementProvider: 'SPORTIFY_SIMULATOR', simulatedTransactionId: 56 }
const INVOICE = { id: 1054, invoiceNumber: 'INV-FIXTURE-1054', paymentId: 54, paymentCode: 'PAY-FIXTURE-54', paymentMethod: 'BANK_TRANSFER', paymentReference: 'BANK-FIXTURE-54', paymentTime: '2026-11-09T08:00:00', memberId: 2, memberCode: 'MEM-FIXTURE-02', memberFullName: 'Taylor Nguyen', subtotalAmount: 350000, discountAmount: 0, taxAmount: 0, totalAmount: 350000, status: 'PAID', issueDate: '2026-11-09', settlementType: 'ACTUAL', settlementProvider: 'RECEPTION_VERIFIED', simulatedTransactionId: null, lines: [{ id: 1, itemType: 'SPORT_PACKAGE', description: 'Basketball package', quantity: 1, unitPrice: 350000, lineTotal: 350000 }] }
const CARD = { id: 8, cardCode: 'CARD-FIXTURE-08', memberId: 2, memberCode: 'MEM-FIXTURE-02', memberFullName: 'Taylor Nguyen', tierId: 2, tierCode: 'SILVER', tierName: 'Silver', tierRank: 2, upgradeRequest: false, replacesCardId: null, discountPercentage: 5, startDate: '2026-10-01', endDate: '2027-09-30', status: 'ACTIVE', pricePaid: 500000, paymentId: 54, paymentCode: 'PAY-FIXTURE-54', createdAt: '2026-09-30T08:00:00' }
const TIERS = [
  { id: 1, code: 'STANDARD', name: 'Standard', tierRank: 1, price: 0, durationMonths: 0, discountPercentage: 0, description: 'Free account tier', isActive: true },
  { id: 2, code: 'SILVER', name: 'Silver', tierRank: 2, price: 500000, durationMonths: 12, discountPercentage: 5, description: 'Everyday training savings', isActive: true },
  { id: 3, code: 'GOLD', name: 'Gold', tierRank: 3, price: 900000, durationMonths: 12, discountPercentage: 10, description: 'Frequent training savings', isActive: true },
]
const HEALTH = { version: '2026.1', questions: [{ key: 'heart', text: 'Has a doctor ever advised you to limit exercise?' }, { key: 'pain', text: 'Do you feel chest pain during physical activity?' }, { key: 'balance', text: 'Have you recently experienced dizziness or loss of balance?' }], answers: { heart: 'NO', pain: 'PREFER_NOT_TO_ANSWER' }, outcome: 'INCOMPLETE', shareWithCoach: false, saved: true, updatedAt: '2026-11-10T10:00:00', consentUpdatedAt: null, disclaimer: 'This questionnaire is advisory only and does not provide medical clearance.' }

function chromePath() {
  const paths = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe']
  const found = paths.find((path) => path && existsSync(path))
  if (!found) throw new Error('Chrome or Edge was not found')
  return found
}

async function launchBrowser() {
  const profile = mkdtempSync(join(tmpdir(), 'pr308-browser-'))
  const chrome = spawn(chromePath(), ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', '--mute-audio', '--disable-extensions', 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] })
  const endpoint = await new Promise((resolveEndpoint, reject) => {
    let stderr = ''
    chrome.stderr.on('data', (chunk) => {
      stderr += chunk
      const match = /DevTools listening on (ws:\/\/\S+)/.exec(stderr)
      if (match) resolveEndpoint(match[1])
    })
    chrome.on('exit', () => reject(new Error(`Browser exited before CDP was ready: ${stderr}`)))
  })
  const socket = new WebSocket(endpoint)
  await new Promise((resolveOpen) => socket.addEventListener('open', resolveOpen, { once: true }))
  let nextId = 0
  const pending = new Map()
  const listeners = new Set()
  socket.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data)
    if (message.id && pending.has(message.id)) {
      const waiter = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) waiter.reject(new Error(message.error.message))
      else waiter.resolve(message.result)
    } else listeners.forEach((listener) => listener(message))
  })
  const send = (method, params = {}, sessionId) => new Promise((resolveSend, reject) => {
    nextId += 1
    pending.set(nextId, { resolve: resolveSend, reject })
    socket.send(JSON.stringify({ id: nextId, method, params, sessionId }))
  })
  return { send, listeners, close: () => {
    socket.close()
    chrome.kill()
    const tempRoot = resolve(tmpdir())
    const resolved = resolve(profile)
    if (resolved.startsWith(`${tempRoot}\\`) && resolved.includes('pr308-browser-')) {
      try { rmSync(resolved, { recursive: true, force: true }) } catch { /* Chrome may still hold profile files while exiting. */ }
    }
  } }
}

function responseFor(url, method, scenario, state) {
  const parsed = new URL(url)
  const path = parsed.pathname.split('/api/v1')[1] || parsed.pathname
  const when = parsed.searchParams.get('when')
  if (path === '/users/me') return [200, MEMBER]
  if (path === '/sessions/5') return [200, scenario === 'booking-stale' ? { ...SESSION, eligible: false, reason: 'NO_SESSIONS_LEFT', suitableRegistrations: [] } : SESSION]
  if (path === '/bookings/my') {
    if (scenario === 'booking-error') return [503, { message: 'Booking history is temporarily unavailable.' }]
    if (scenario === 'cancel-uncertain' && state.cancelTriggered) return [503, { message: 'Booking status could not be checked.' }]
    return [200, when === 'PAST' ? [CANCELLED] : [BOOKING]]
  }
  if (path === '/bookings/21' && method === 'DELETE') { state.cancelTriggered = true; return [502, { message: 'Cancellation response was lost.' }] }
  if (path === '/packages/registrations/my') return [200, [REGISTRATION]]
  if (path === '/membership-cards/my') return [200, [CARD]]
  if (path === '/membership-cards/tiers') return [200, TIERS]
  if (path === '/member/wallet') return [200, WALLET]
  if (path === '/member/wallet/topups') return [200, { simulatedTopUpsEnabled: true, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: [TOPUP] }]
  if (path === '/member/wallet/payout-account') return [200, { bankBin: '970436', bankName: 'Vietcombank', maskedAccountNumber: '****0000', accountHolder: 'TAYLOR NGUYEN', updatedAt: '2026-11-10T08:00:00' }]
  if (path === '/payments/vietqr-banks') return [200, { banks: [{ bin: '970436', code: 'VCB', name: 'Vietcombank', transferSupported: true }] }]
  if (path === '/member/wallet/withdrawals') return [200, [WITHDRAWAL]]
  if (path === '/member/wallet/history') return [200, { page: { content: HISTORY_ROWS, page: 0, size: 10, totalElements: 3, totalPages: 1 }, availableBalance: 650000, reservedBalance: 200000, ledgerAvailable: scenario === 'history-warning' ? 640000 : 650000, ledgerReserved: 200000, reconciled: scenario !== 'history-warning' }]
  if (path === '/payments/my') return [200, { content: [PAYMENT_PENDING, PAYMENT_SUCCESS, PAYMENT_SIMULATED], page: 0, size: 20, totalElements: 3, totalPages: 1 }]
  if (path === '/payments/52') return [200, PAYMENT_PENDING]
  if (path === '/payments/54') return [200, PAYMENT_SUCCESS]
  if (path === '/payments/56') return [200, PAYMENT_SIMULATED]
  if (path === '/payments/local-demo/capability') return [200, { contractVersion: 1, mode: 'LOCAL_DEMO', enabled: true }]
  if (path === '/invoices/1054') return [200, INVOICE]
  if (path === '/member/health-assessment') return [200, HEALTH]
  if (path === '/member/check-in-pass' && method === 'POST') return [200, { memberCode: 'MEM-FIXTURE-02', token: `SCIN1.${'A'.repeat(43)}`, serverTime: '2026-11-11T07:00:00Z', expiresAt: '2026-11-11T07:02:00Z' }]
  return [404, { message: `No local fixture for ${method} ${path}` }]
}

async function createSession(browser, authenticated = true) {
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })
  const send = (method, params = {}) => browser.send(method, params, sessionId)
  let scenario = 'default'
  const state = { cancelTriggered: false }
  const listener = async ({ method, params, sessionId: source }) => {
    if (source !== sessionId || method !== 'Fetch.requestPaused') return
    const { requestId, request } = params
    try {
      if (!request.url.includes('/api/v1/')) return void await send('Fetch.continueRequest', { requestId })
      const corsHeaders = [
        { name: 'Access-Control-Allow-Origin', value: BASE_URL },
        { name: 'Access-Control-Allow-Headers', value: 'Authorization, Content-Type, Idempotency-Key, X-Correlation-Id' },
        { name: 'Access-Control-Allow-Methods', value: 'GET, POST, PUT, DELETE, OPTIONS' },
        { name: 'Content-Type', value: 'application/json' },
      ]
      if (request.method === 'OPTIONS') {
        await send('Fetch.fulfillRequest', { requestId, responseCode: 204, responseHeaders: corsHeaders })
        return
      }
      const [status, body] = responseFor(request.url, request.method, scenario, state)
      await send('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: corsHeaders, body: Buffer.from(JSON.stringify(body)).toString('base64') })
    } catch (error) {
      await send('Fetch.failRequest', { requestId, errorReason: 'Failed' }).catch(() => {})
      if (!String(error).includes('Invalid InterceptionId')) console.error(`Fixture interception failed for ${request.method} ${request.url}:`, error)
    }
  }
  browser.listeners.add(listener)
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/v1/*' }] })
  if (authenticated) await send('Page.addScriptToEvaluateOnNewDocument', { source: `localStorage.setItem('sportify.accessToken','fixture-token'); localStorage.setItem('sportify.refreshToken','fixture-refresh'); localStorage.setItem('sportify.sessionId','fixture-session');` })
  const evaluate = async (expression) => (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value
  const viewport = async (width, height) => send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 768 })
  const goto = async (route) => {
    await send('Page.navigate', { url: BASE_URL + route })
    const start = Date.now()
    while (Date.now() - start < 15000) {
      await wait(150)
      if (await evaluate(`Boolean(document.querySelector('.member-commerce'))`)) break
    }
    await wait(500)
  }
  const theme = async (value) => {
    await evaluate(`document.documentElement.setAttribute('data-theme', ${JSON.stringify(value)}); localStorage.setItem('sportify.themePreference', ${JSON.stringify(value)});`)
    await wait(100)
  }
  const media = (type) => send('Emulation.setEmulatedMedia', { media: type, features: [{ name: 'prefers-color-scheme', value: 'light' }] })
  const screenshot = async (file) => {
    const { data } = await send('Page.captureScreenshot', { format: 'png' })
    const buffer = Buffer.from(data, 'base64')
    mkdirSync(dirname(file), { recursive: true })
    writeFileSync(file, buffer)
    return { byteSize: buffer.length, sha256: createHash('sha256').update(buffer).digest('hex') }
  }
  return { send, evaluate, viewport, goto, theme, media, screenshot, setScenario: (value) => { scenario = value; state.cancelTriggered = false }, close: async () => { browser.listeners.delete(listener); await browser.send('Target.closeTarget', { targetId }); await browser.send('Target.disposeBrowserContext', { browserContextId }) } }
}

async function main() {
  mkdirSync(SHOTS, { recursive: true })
  const browser = await launchBrowser()
  const session = await createSession(browser)
  const manifest = []
  const capture = async ({ name, route, width = 1440, height = 900, theme = 'light', scenario = 'default', state = 'rendered', action, print = false }) => {
    session.setScenario(scenario)
    await session.viewport(width, height)
    await session.media(print ? 'print' : 'screen')
    await session.goto(route)
    await session.theme(theme)
    if (action) await action(session)
    const layout = await session.evaluate(`({ overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth, path: location.pathname, text: document.body.innerText.slice(0, 180) })`)
    if (layout.overflow && width >= 1440) throw new Error(`Horizontal overflow on ${route}: ${JSON.stringify(layout)}`)
    const file = join(SHOTS, name)
    const hash = await session.screenshot(file)
    manifest.push({ filename: name, route, viewport: `${width}x${height}`, theme, dataProvenance: 'LOCAL_FIXTURE', capturedState: state, byteSize: hash.byteSize, sha256: hash.sha256, captureRevision: CAPTURE_SHA, ...(INTEGRATION_SHA ? { integrationDependency: INTEGRATION_SHA } : {}), printMedia: print })
  }
  try {
    if (JOINT_SMOKE) {
      await capture({ name: 'joint-bookings-1440x900-light.png', route: '/member/bookings?when=UPCOMING', state: 'PR #308 bookings inside PR #309 shell' })
      await capture({ name: 'joint-wallet-1920x1080-dark.png', route: '/member/wallet', width: 1920, height: 1080, theme: 'dark', state: 'PR #308 wallet inside PR #309 shell' })
      await capture({ name: 'joint-receipt-1440x900-light.png', route: '/member/payments/receipts/1054', state: 'PR #308 receipt and payment header inside PR #309 shell' })
      await capture({ name: 'joint-profile-1920x1080-dark.png', route: '/member/profile', width: 1920, height: 1080, theme: 'dark', state: 'PR #308 profile inside PR #309 shell' })
      const manifestPath = join(OUT, 'SCREENSHOT_MANIFEST.json')
      const existing = existsSync(manifestPath) ? JSON.parse(readFileSync(manifestPath, 'utf8')) : []
      writeFileSync(manifestPath, `${JSON.stringify([...existing.filter((item) => !String(item.filename).startsWith('joint-')), ...manifest], null, 2)}\n`)
      console.log(`Captured ${manifest.length} joint PR #308/#309 browser screenshots.`)
      return
    }
    await capture({ name: 'bookings-upcoming-1440x900-light.png', route: '/member/bookings?when=UPCOMING', state: 'upcoming populated' })
    await capture({ name: 'bookings-past-1440x900-dark.png', route: '/member/bookings?when=PAST', theme: 'dark', state: 'past cancelled' })
    await capture({ name: 'bookings-error-1440x900-light.png', route: '/member/bookings?when=UPCOMING', scenario: 'booking-error', state: 'API error and retry' })
    await capture({ name: 'booking-review-1440x900-light.png', route: '/member/bookings/review?sessionId=5&packageRegistrationId=11', state: 'eligible review' })
    await capture({ name: 'booking-review-stale-1440x900-dark.png', route: '/member/bookings/review?sessionId=5&packageRegistrationId=11', theme: 'dark', scenario: 'booking-stale', state: 'eligibility changed' })
    await capture({ name: 'booking-cancel-confirm-1440x900-light.png', route: '/member/bookings/21/cancel', state: 'cancellation confirmation' })
    await capture({ name: 'booking-cancel-uncertain-1440x900-dark.png', route: '/member/bookings/21/cancel', theme: 'dark', scenario: 'cancel-uncertain', state: 'uncertain cancellation outcome', action: async (page) => { await page.evaluate(`[...document.querySelectorAll('button')].find((button) => button.textContent.includes('Confirm cancellation'))?.click()`); await wait(1000); await page.evaluate(`window.scrollTo(0, document.body.scrollHeight)`) } })
    await capture({ name: 'checkin-not-issued-1440x900-light.png', route: '/member/check-in-pass', state: 'pass not issued' })
    await capture({ name: 'checkin-valid-fixture-1440x900-dark.png', route: '/member/check-in-pass', theme: 'dark', state: 'synthetic valid pass with synthetic QR', action: async (page) => { await page.evaluate(`[...document.querySelectorAll('button')].find((button) => button.textContent.includes('Generate check-in pass'))?.click()`); await wait(800) } })
    await capture({ name: 'wallet-overview-1440x900-light.png', route: '/member/wallet', state: 'actual reserved and demo balances' })
    await capture({ name: 'wallet-overview-1920x1080-dark.png', route: '/member/wallet', width: 1920, height: 1080, theme: 'dark', state: 'desktop dark populated' })
    await capture({ name: 'wallet-topup-pending-1440x900-light.png', route: '/member/wallet/topup', state: 'pending transfer instructions' })
    await capture({ name: 'wallet-withdraw-pending-1440x900-light.png', route: '/member/wallet/withdraw', state: 'masked payout account and pending request' })
    await capture({ name: 'wallet-withdraw-1920x1080-dark.png', route: '/member/wallet/withdraw', width: 1920, height: 1080, theme: 'dark', state: 'desktop dark form' })
    await capture({ name: 'wallet-history-warning-1440x900-light.png', route: '/member/wallet/history', scenario: 'history-warning', state: 'server reconciliation warning' })
    await capture({ name: 'payments-list-1440x900-light.png', route: '/member/payments', state: 'pending actual and simulated rows' })
    await capture({ name: 'payment-status-1440x900-dark.png', route: '/member/payments/52/status', theme: 'dark', state: 'pending transfer polling' })
    await capture({ name: 'qr-demo-completed-1440x900-light.png', route: '/member/payments/56/qr-demo', state: 'Paid - Simulated warning' })
    await capture({ name: 'receipt-screen-1440x900-light.png', route: '/member/payments/receipts/1054', state: 'authorized receipt screen' })
    await capture({ name: 'receipt-print-a4-1440x900-light.png', route: '/member/payments/receipts/1054', state: 'receipt print media', print: true })
    await capture({ name: 'profile-1440x900-light.png', route: '/member/profile', state: 'profile and security forms' })
    await capture({ name: 'profile-1920x1080-dark.png', route: '/member/profile', width: 1920, height: 1080, theme: 'dark', state: 'desktop dark profile' })
    await capture({ name: 'health-consent-off-1440x900-light.png', route: '/member/profile/health', state: 'optional questionnaire consent off' })
    await capture({ name: 'membership-packages-1440x900-light.png', route: '/member/membership', state: 'active package' })
    await capture({ name: 'membership-cards-1440x900-dark.png', route: '/member/membership/cards', theme: 'dark', state: 'dynamic tier catalog' })
    await capture({ name: 'wallet-mobile-smoke-390x844-light.png', route: '/member/wallet', width: 390, height: 844, state: 'narrow responsive smoke' })
    const anonymous = await createSession(browser, false)
    try {
      await anonymous.viewport(1440, 900)
      await anonymous.media('print')
      await anonymous.send('Page.navigate', { url: `${BASE_URL}/login` })
      for (let attempt = 0; attempt < 50; attempt += 1) {
        if (await anonymous.evaluate(`Boolean(document.querySelector('.auth-form button'))`)) break
        await wait(200)
      }
      const loginPrint = await anonymous.evaluate(`(() => { const button = document.querySelector('.auth-form button'); return { route: location.pathname, visibleButton: Boolean(button) && getComputedStyle(button).display !== 'none', memberScope: Boolean(document.querySelector('.member-commerce')) } })()`)
      if (loginPrint.route !== '/login' || !loginPrint.visibleButton || loginPrint.memberScope) throw new Error(`Non-Member print isolation failed: ${JSON.stringify(loginPrint)}`)
      const loginName = 'non-member-login-print-smoke-1440x900-light.png'
      const loginHash = await anonymous.screenshot(join(SHOTS, loginName))
      manifest.push({ filename: loginName, route: '/login', viewport: '1440x900', theme: 'light', dataProvenance: 'LOCAL_FIXTURE', capturedState: 'non-Member print isolation; login action remains visible', byteSize: loginHash.byteSize, sha256: loginHash.sha256, captureRevision: CAPTURE_SHA, printMedia: true })
    } finally {
      await anonymous.close()
    }
    writeFileSync(join(OUT, 'SCREENSHOT_MANIFEST.json'), `${JSON.stringify(manifest, null, 2)}\n`)
    console.log(`Captured ${manifest.length} browser screenshots.`)
  } finally {
    await session.close()
    browser.close()
  }
}

main().catch((error) => { console.error(error); process.exitCode = 1 })
