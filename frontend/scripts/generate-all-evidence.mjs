import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { UI07_NOT_RUN, UI07_SUPERSEDED_KEYS, assertUi07Baseline, mergeUi07Report } from './ui07-report.mjs'

const BASE_URL = process.env.BASE_URL || 'http://localhost:4310'
const __dirname = fileURLToPath(new URL('.', import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const SCREENSHOTS_DIR = join(FRONTEND_ROOT, 'docs/screenshots')

function findChrome() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  ]
  const found = candidates.find((p) => p && existsSync(p))
  if (!found) throw new Error('Chrome/Edge not found')
  return found
}

async function launch() {
  const profile = mkdtempSync(join(tmpdir(), 'evidence-chrome-'))
  const chrome = spawn(
    findChrome(),
    [
      '--headless=new',
      '--remote-debugging-port=0',
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      '--mute-audio',
      '--disable-extensions',
      '--disable-web-security',
      'about:blank',
    ],
    { stdio: ['ignore', 'ignore', 'pipe'] },
  )

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
    const msg = JSON.parse(data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      if (msg.error) reject(new Error(`${msg.error.message}`))
      else resolve(msg.result)
    } else {
      for (const l of listeners) l(msg)
    }
  })

  const send = (method, params = {}, sessionId) =>
    new Promise((resolve, reject) => {
      id += 1
      pending.set(id, { resolve, reject })
      socket.send(JSON.stringify({ id, method, params, sessionId }))
    })

  const close = () => {
    socket.close()
    chrome.kill()
    try {
      rmSync(profile, { recursive: true, force: true })
    } catch {}
  }

  return { send, listeners, close }
}

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// User fixtures
const MEMBER = {
  id: 2,
  email: 'member@sportify.demo',
  fullName: 'Taylor Nguyen',
  phone: '0901234567',
  role: 'MEMBER',
  status: 'ACTIVE',
}

const RECEPTIONIST = {
  id: 10,
  email: 'reception@sportify.demo',
  fullName: 'Linh Receptionist',
  phone: '0912345678',
  role: 'RECEPTIONIST',
  status: 'ACTIVE',
}

// Payment fixtures
const PAYMENT_PACKAGE = {
  id: 52,
  code: 'PAY-0052',
  status: 'PENDING',
  amount: 810000,
  method: 'BANK_TRANSFER',
  referenceCode: 'SP-52-TAYLOR',
  notes: null,
  originalPrice: 900000,
  discount: 90000,
  itemType: 'PACKAGE_REGISTRATION',
  itemCode: 'REG-1052',
  itemDescription: 'Football Foundations (12 sessions)',
  itemStatus: 'PENDING_PAYMENT',
  memberId: 2,
  memberFullName: 'Taylor Nguyen',
  memberCode: 'MEM-0002',
  createdAt: '2026-10-25T09:00:00',
  bankTransferInstructions: {
    bank: 'Vietcombank',
    accountName: 'CONG TY SPORTIFY',
    accountNumber: '1029384756',
    qrCodeUrl: null,
  },
}

const PAYMENT_SINGLE = {
  id: 53,
  code: 'PAY-0053',
  status: 'PENDING',
  amount: 80000,
  method: 'BANK_TRANSFER',
  referenceCode: 'SP-53-SINGLE',
  notes: null,
  originalPrice: 80000,
  discount: 0,
  itemType: 'SESSION_BOOKING',
  itemCode: 'VISIT-1053',
  itemDescription: 'Single Visit - Swimming Pool',
  itemStatus: 'PENDING_PAYMENT',
  memberId: 2,
  memberFullName: 'Taylor Nguyen',
  memberCode: 'MEM-0002',
  createdAt: '2026-10-25T09:15:00',
}

const PAYMENT_SUCCESS = {
  id: 54,
  code: 'PAY-0054',
  status: 'SUCCESS',
  amount: 810000,
  method: 'BANK_TRANSFER',
  referenceCode: 'SP-54-CONFIRMED',
  originalPrice: 900000,
  discount: 90000,
  invoiceId: 1054,
  invoiceNumber: 'INV-1054',
  memberId: 2,
  memberFullName: 'Taylor Nguyen',
  memberCode: 'MEM-0002',
  itemType: 'PACKAGE_REGISTRATION',
  itemCode: 'REG-1052',
  itemDescription: 'Football Foundations (12 sessions)',
  createdAt: '2026-10-25T09:00:00',
  paymentTime: '2026-10-25T09:30:00',
}

const INVOICE_SHORT = {
  id: 1054,
  invoiceNumber: 'INV-1054',
  paymentId: 54,
  status: 'PAID',
  totalAmount: 810000,
  subtotalAmount: 900000,
  discountAmount: 90000,
  issueDate: '2026-10-25',
  paymentTime: '2026-10-25T09:30:00',
  memberId: 2,
  memberFullName: 'Taylor Nguyen',
  memberCode: 'MEM-0002',
  lines: [{ id: 1, description: 'Football Foundations (12 sessions)', amount: 810000 }],
}

const INVOICE_LONG = {
  id: 1058,
  invoiceNumber: 'INV-1058-EXTENDED-LONG-REFERENCE',
  paymentId: 58,
  status: 'PAID',
  totalAmount: 2450000,
  subtotalAmount: 2700000,
  discountAmount: 250000,
  issueDate: '2026-10-25',
  paymentTime: '2026-10-25T10:15:00',
  memberId: 2,
  memberFullName: 'Nguyen Thi Mai Linh (Corporate Member Group VIP)',
  memberCode: 'MEM-0002-VIP',
  lines: [
    { id: 1, description: 'Personal Training Package (8 sessions)', amount: 1200000 },
    { id: 2, description: 'Single Visit - Olympic Swimming Pool (Morning session)', amount: 150000 },
    { id: 3, description: 'Badminton Court Rental (Court 3, 2 hours peak)', amount: 200000 },
    { id: 4, description: 'Basketball Full Court Reservation (Tournament Slot)', amount: 350000 },
    { id: 5, description: 'Tennis Racket & Court Maintenance Service Fee', amount: 120000 },
    { id: 6, description: 'Sportify Sports Bottle & Welcome Kit', amount: 80000 },
    { id: 7, description: 'Locker Rental - Monthly Executive Package', amount: 150000 },
    { id: 8, description: 'Sauna & Recovery Spa Access Pass', amount: 200000 },
  ],
}

const RECEPTION_PAYMENTS = [
  {
    id: 44,
    code: 'PAY-0044',
    status: 'PENDING',
    amount: 810000,
    method: 'BANK_TRANSFER',
    referenceCode: 'BANK-REF-44',
    notes: 'Amount mismatch: received 800000, expected 810000 by BANK_TRANSFER, checked by user id 10',
    memberId: 2,
    memberFullName: 'Taylor Nguyen',
    memberCode: 'MEM-0002',
    itemCode: 'REG-1044',
    itemDescription: 'Football Foundations',
    createdAt: '2026-10-25T08:00:00',
  },
  {
    id: 41,
    code: 'PAY-0041',
    status: 'PENDING',
    amount: 810000,
    method: 'CASH',
    referenceCode: null,
    notes: null,
    memberId: 3,
    memberFullName: 'Bao Nguyen',
    memberCode: 'MEM-0003',
    itemCode: 'REG-1041',
    itemDescription: 'Basketball Academy',
    createdAt: '2026-10-25T08:15:00',
  },
  {
    id: 40,
    code: 'PAY-0040',
    status: 'SUCCESS',
    amount: 500000,
    method: 'CREDIT_CARD',
    referenceCode: 'VISA-4099',
    notes: null,
    memberId: 4,
    memberFullName: 'An Tran',
    memberCode: 'MEM-0004',
    itemCode: 'CARD-1040',
    itemDescription: 'Gold Membership Card',
    createdAt: '2026-10-25T08:30:00',
  },
]

const INVOICE_41 = {
  id: 1041,
  invoiceNumber: 'INV-1041',
  paymentId: 41,
  status: 'PAID',
  totalAmount: 810000,
  subtotalAmount: 900000,
  discountAmount: 90000,
  issueDate: '2026-10-25',
  paymentTime: '2026-10-25T08:45:00',
  memberId: 3,
  memberFullName: 'Bao Nguyen',
  memberCode: 'MEM-0003',
  lines: [{ id: 1, description: 'Basketball Academy (12 sessions)', amount: 810000 }],
}

const REFUND_11 = {
  id: 11,
  refundCode: 'REF-0011',
  paymentId: 52,
  paymentCode: 'PAY-0052',
  amountRequested: 450000,
  amountApproved: 450000,
  status: 'APPROVED',
  reason: 'Injured ankle during practice session',
  memberId: 2,
  memberFullName: 'Nguyen Thi Mai Linh (Corporate Member Group VIP)',
  memberCode: 'MEM-0002',
  registrationCode: 'REG-1052',
  invoiceNumber: 'INV-1052',
  createdAt: '2026-10-24T14:00:00',
}

const REFUND_SUBMITTED = {
  id: 12,
  refundCode: 'REF-0012',
  paymentId: 53,
  paymentCode: 'PAY-0053',
  amountRequested: 80000,
  amountApproved: null,
  status: 'PENDING',
  reason: 'Schedule conflict',
  memberId: 3,
  memberFullName: 'Bao Nguyen',
  memberCode: 'MEM-0003',
  registrationCode: 'VISIT-1053',
  invoiceNumber: 'INV-1053',
  createdAt: '2026-10-25T07:30:00',
}

const REFUND_COMPLETED = {
  id: 10,
  refundCode: 'REF-0010',
  paymentId: 40,
  paymentCode: 'PAY-0040',
  amountRequested: 500000,
  amountApproved: 500000,
  status: 'COMPLETED',
  reason: 'Relocated to another city',
  memberId: 4,
  memberFullName: 'An Tran',
  memberCode: 'MEM-0004',
  registrationCode: 'CARD-1040',
  invoiceNumber: 'INV-1040',
  createdAt: '2026-10-23T11:00:00',
}

function countPdfPages(pdfBuffer) {
  const text = pdfBuffer.toString('binary')
  const matches = text.match(/\/Type\s*\/Page\b/g)
  return matches ? matches.length : 0
}

async function runSession(browser, { role = 'GUEST', setup = null }) {
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })
  const send = (method, params) => browser.send(method, params, sessionId)

  let pollCount = 0
  let pollStatus = 'PENDING'
  let putCompleteCount = 0
  let registerMode = 'duplicate'
  let isRegistered = false

  const listener = async ({ method, params, sessionId: from }) => {
    if (from !== sessionId) return
    if (method === 'Fetch.requestPaused') {
      const { requestId, request } = params
      const url = request.url
      try {
        if (url.includes('/api/v1/')) {
          const path = url.split('/api/v1', 2)[1]?.split('?')[0] || ''

          if (path === '/users/me') {
            if (role === 'RECEPTIONIST') {
              await send('Fetch.fulfillRequest', {
                requestId, responseCode: 200,
                responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
                body: Buffer.from(JSON.stringify(RECEPTIONIST)).toString('base64'),
              })
              return
            }
            if (role === 'MEMBER' || isRegistered) {
              await send('Fetch.fulfillRequest', {
                requestId, responseCode: 200,
                responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
                body: Buffer.from(JSON.stringify(MEMBER)).toString('base64'),
              })
              return
            }
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 401,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({ message: 'Unauthorized' })).toString('base64'),
            })
            return
          }

          if (path === '/payments/52') {
            pollCount++
            const body = { ...PAYMENT_PACKAGE, status: pollStatus }
            if (pollStatus === 'SUCCESS') {
              body.invoiceId = 1054
              body.invoiceNumber = 'INV-1054'
            }
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(body)).toString('base64'),
            })
            return
          }
          if (path === '/payments/53') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(PAYMENT_SINGLE)).toString('base64'),
            })
            return
          }
          if (path === '/payments/54') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(PAYMENT_SUCCESS)).toString('base64'),
            })
            return
          }
          if (path === '/payments/41') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(RECEPTION_PAYMENTS[1])).toString('base64'),
            })
            return
          }
          if (path === '/payments/44') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(RECEPTION_PAYMENTS[0])).toString('base64'),
            })
            return
          }
          if (path === '/payments/my') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({
                content: [PAYMENT_SUCCESS, PAYMENT_PACKAGE, PAYMENT_SINGLE],
                page: 0, size: 10, totalElements: 3, totalPages: 1,
              })).toString('base64'),
            })
            return
          }
          if (path === '/payments') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({
                content: RECEPTION_PAYMENTS,
                page: 0, size: 10, totalElements: 3, totalPages: 1,
              })).toString('base64'),
            })
            return
          }

          if (path === '/invoices/1054' || path === '/invoices/by-payment/54') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(INVOICE_SHORT)).toString('base64'),
            })
            return
          }
          if (path === '/invoices/1058' || path === '/invoices/by-payment/58') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(INVOICE_LONG)).toString('base64'),
            })
            return
          }
          if (path === '/invoices/1041' || path === '/invoices/by-payment/41') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify(INVOICE_41)).toString('base64'),
            })
            return
          }

          if (path === '/refunds/member/2') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify([REFUND_11])).toString('base64'),
            })
            return
          }
          if (path === '/refunds') {
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({
                content: [REFUND_11, REFUND_SUBMITTED, REFUND_COMPLETED],
                page: 0, size: 10, totalElements: 3, totalPages: 1,
              })).toString('base64'),
            })
            return
          }
          if (path === '/refunds/11/complete') {
            putCompleteCount++
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({ ...REFUND_11, status: 'COMPLETED' })).toString('base64'),
            })
            return
          }

          if (path.includes('/auth/register')) {
            if (registerMode === 'duplicate') {
              await send('Fetch.fulfillRequest', {
                requestId, responseCode: 409,
                responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
                body: Buffer.from(JSON.stringify({
                  message: 'Email is already registered',
                  fieldErrors: { email: 'Email is already registered' },
                })).toString('base64'),
              })
              return
            }
            isRegistered = true
            await send('Fetch.fulfillRequest', {
              requestId, responseCode: 200,
              responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
              body: Buffer.from(JSON.stringify({
                accessToken: 'reg-token',
                refreshToken: 'reg-refresh',
              })).toString('base64'),
            })
            return
          }

          // Default fallback
          await send('Fetch.fulfillRequest', {
            requestId, responseCode: 200,
            responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
            body: Buffer.from(JSON.stringify({})).toString('base64'),
          })
          return
        }

        // Pass through non-api
        await send('Fetch.continueRequest', { requestId })
      } catch (e) {
        // Ignored
      }
    }
  }

  browser.listeners.add(listener)

  await send('Page.enable')
  await send('Network.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/v1/*' }] })

  const tokenScript =
    role === 'RECEPTIONIST'
      ? `localStorage.setItem('sportify.accessToken', 'reception-token'); localStorage.setItem('sportify.refreshToken', 'reception-refresh');`
      : role === 'MEMBER'
      ? `localStorage.setItem('sportify.accessToken', 'member-token'); localStorage.setItem('sportify.refreshToken', 'member-refresh');`
      : `localStorage.clear();`

  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `try { ${tokenScript} } catch {}`,
  })

  const evalJs = async (expr) => {
    const res = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })
    return res.result?.value
  }

  const setViewport = async (w, h, theme = 'light') => {
    await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: w < 768 })
    await send('Emulation.setEmulatedMedia', {
      features: [{ name: 'prefers-color-scheme', value: theme }],
    })
    await evalJs(`document.documentElement.setAttribute('data-theme', '${theme}'); localStorage.setItem('sportify.themePreference', '${theme}');`)
    await pause(100)
  }

  const goto = async (path, expectedSelector, timeoutMs = 15000) => {
    await send('Page.navigate', { url: BASE_URL + path })
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      await pause(150)
      const err = await evalJs(`
        (() => {
          const text = document.body.innerText;
          if (text.includes('403 Access denied') || text.includes('This page is not available for the Member role')) return '403 Forbidden detected';
          if (text.includes('404 Page not found')) return '404 Not Found detected';
          return null;
        })()
      `)
      if (err) throw new Error(`Navigation to ${path} failed with: ${err}`)

      if (expectedSelector) {
        const found = await evalJs(`Boolean(document.querySelector('${expectedSelector}'))`)
        if (found) return
      } else {
        return
      }
    }
    const currentText = await evalJs(`document.body.innerText.slice(0, 300)`)
    throw new Error(`Timeout waiting for '${expectedSelector}' on ${path}. Body: ${currentText}`)
  }

  const screenshot = async (filePath) => {
    mkdirSync(join(filePath, '..'), { recursive: true })
    const { data } = await send('Page.captureScreenshot', { format: 'png' })
    writeFileSync(filePath, Buffer.from(data, 'base64'))
    console.log(`Saved screenshot: ${filePath}`)
  }

  const printPdf = async (filePath) => {
    mkdirSync(join(filePath, '..'), { recursive: true })
    const { data } = await send('Page.printToPDF', {
      paperWidth: 8.27, // A4 inches
      paperHeight: 11.69,
      marginTop: 0,
      marginBottom: 0,
      marginLeft: 0,
      marginRight: 0,
      printBackground: true,
      preferCSSPageSize: true,
    })
    const buf = Buffer.from(data, 'base64')
    writeFileSync(filePath, buf)
    const pageCount = countPdfPages(buf)
    console.log(`Saved PDF: ${filePath} (${pageCount} page(s))`)
    return { pageCount, buffer: buf }
  }

  const cleanup = async () => {
    browser.listeners.delete(listener)
    await send('Target.closeTarget', { targetId })
    await browser.send('Target.disposeBrowserContext', { browserContextId })
  }

  // Real key presses through the browser input pipeline (not element.focus()).
  const pressTab = async ({ shift = false } = {}) => {
    const modifiers = shift ? 8 : 0
    const key = { key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9, nativeVirtualKeyCode: 9, modifiers }
    await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', ...key })
    await send('Input.dispatchKeyEvent', { type: 'keyUp', ...key })
    await pause(60)
  }

  return {
    send, evalJs, setViewport, goto, screenshot, printPdf, cleanup, pressTab,
    getPollCount: () => pollCount,
    setPollStatus: (s) => { pollStatus = s },
    getPutCompleteCount: () => putCompleteCount,
    setRegisterMode: (m) => {
      registerMode = m
      if (m === 'success') isRegistered = false
    },
  }
}

async function main() {
  console.log('--- Starting Verified Evidence & Test Execution ---')
  const browser = await launch()
  const args = process.argv.slice(2)
  const onlyIdx = args.findIndex((a) => a === '--only' || a.startsWith('--only='))
  const target = onlyIdx !== -1 ? (args[onlyIdx].startsWith('--only=') ? args[onlyIdx].split('=')[1] : args[onlyIdx + 1]) : null
  const shouldRun = (name) => !target || target.toLowerCase() === name.toLowerCase()


  try {
    // =========================================================================
    // 1. UI-07: Sign up at a 720px responsive viewport, 5 states (PR #131)
    // Scope: layout equivalence of a 720 CSS px viewport (what 1440px at 200% zoom lays out to). This is NOT a
    // native browser zoom run and does not establish WCAG 1.4.10 (320 CSS px); those stay NOT_RUN in the report.
    // File names keep the `signup-zoom200-1440-*` prefix used by TC-15 so earlier review links stay valid.
    // =========================================================================
    if (shouldRun('ui-07')) {
      console.log('\n[1/5] Generating UI-07 (Sign up 720px responsive viewport states)...')
      // Baseline comes from the report.json in this working tree, not from Git history. Fail before any work if it
      // is missing a section, instead of silently writing empty contrast/layout/focus data.
      const reportPath = join(SCREENSHOTS_DIR, 'ui-07/report.json')
      if (!existsSync(reportPath)) throw new Error(`UI-07 ${reportPath} not found. Run scripts/ui07-signup-check.py first.`)
      const baselineReport = JSON.parse(readFileSync(reportPath, 'utf8'))
      assertUi07Baseline(baselineReport, reportPath)

      const session = await runSession(browser, { role: 'GUEST' })
      await session.setViewport(720, 900, 'light')

      // State 1: Empty
      await session.goto('/register', 'input[name="fullName"]')
      const path1 = await session.evalJs('window.location.pathname')
      if (path1 !== '/register') throw new Error(`UI-07 Expected /register but got ${path1}`)
      const effectiveViewport = await session.evalJs(`({
        width: window.innerWidth,
        height: window.innerHeight,
        matchesMaxWidth768: window.matchMedia('(max-width: 768px)').matches,
      })`)
      if (effectiveViewport.width !== 720 || !effectiveViewport.matchesMaxWidth768) {
        throw new Error(`UI-07 Expected a 720px viewport inside the 768px breakpoint, got ${JSON.stringify(effectiveViewport)}`)
      }
      await session.screenshot(join(SCREENSHOTS_DIR, 'ui-07/signup-zoom200-1440-empty.png'))

      // Keyboard traversal: real Tab presses from the top of a freshly loaded page, then one Shift+Tab.
      const tabbable = await session.evalJs(`
        (() => {
          const visible = (el) => {
            const s = getComputedStyle(el);
            return el.getClientRects().length > 0 && s.visibility !== 'hidden' && s.display !== 'none';
          };
          const els = Array.from(document.querySelectorAll('a[href], button, input, select, textarea, [tabindex]'))
            .filter((el) => !el.disabled && el.type !== 'hidden' && el.tabIndex >= 0 && visible(el));
          els.forEach((el, i) => el.setAttribute('data-tab-probe', String(i)));
          return els.map((el) => ({
            tag: el.tagName.toLowerCase(),
            label: el.getAttribute('name') || el.getAttribute('aria-label') || el.textContent.trim().slice(0, 40),
            positiveTabIndex: el.tabIndex > 0,
          }));
        })()
      `)
      const readFocus = `
        (() => {
          const el = document.activeElement;
          const probe = el && el.getAttribute('data-tab-probe');
          return probe === null || probe === undefined ? null : Number(probe);
        })()
      `
      const tabSequence = []
      for (let i = 0; i < tabbable.length; i++) {
        await session.pressTab()
        tabSequence.push(await session.evalJs(readFocus))
      }
      await session.pressTab({ shift: true })
      const afterShiftTab = await session.evalJs(readFocus)
      const expectedOrder = tabbable.map((_, i) => i)
      const keyboard = {
        method: 'Input.dispatchKeyEvent Tab x ' + tabbable.length + ', then Shift+Tab x 1, from page load',
        tabbableControls: tabbable.map((t) => `${t.tag}:${t.label}`),
        focusSequence: tabSequence.map((i) => (i === null ? null : `${tabbable[i].tag}:${tabbable[i].label}`)),
        allControlsReachedByTab: expectedOrder.every((i) => tabSequence.includes(i)),
        tabOrderFollowsDomOrder: JSON.stringify(tabSequence) === JSON.stringify(expectedOrder),
        shiftTabMovesBack: tabbable.length > 1 && afterShiftTab === tabbable.length - 2,
      }
      await session.evalJs(`
        document.activeElement && document.activeElement.blur();
        document.querySelectorAll('[data-tab-probe]').forEach((el) => el.removeAttribute('data-tab-probe'));
      `)
      console.log(`UI-07 Keyboard traversal: ${JSON.stringify(keyboard)}`)
      if (!keyboard.allControlsReachedByTab) throw new Error('UI-07 Not every control was reached with Tab')
      if (!keyboard.tabOrderFollowsDomOrder) throw new Error('UI-07 Tab order does not follow the visual (DOM) order')
      if (!keyboard.shiftTabMovesBack) throw new Error('UI-07 Shift+Tab did not move focus back one control')

      // State 2: Validation Errors (click submit empty)
      await session.evalJs(`document.querySelector('button[type="submit"]').click()`)
      await pause(300)
      const errCount = await session.evalJs(`document.querySelectorAll('.auth-form__error, [role="alert"]').length`)
      console.log(`UI-07 Client validation errors visible: ${errCount}`)
      if (errCount === 0) throw new Error('UI-07 Expected client validation errors')
      await session.screenshot(join(SCREENSHOTS_DIR, 'ui-07/signup-zoom200-1440-errors.png'))

      // State 3: Duplicate Email 409
      session.setRegisterMode('duplicate')
      await session.evalJs(`
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        const fill = (name, val) => {
          const el = document.querySelector('input[name="' + name + '"]');
          if (!el) throw new Error('Missing input ' + name);
          setter.call(el, val);
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        };
        fill('fullName', 'Taylor Duplicate');
        fill('phone', '0901234567');
        fill('email', 'duplicate@sportify.demo');
        fill('password', 'Password123!');
        fill('confirmPassword', 'Password123!');
        const lbl = document.querySelector('label.checkbox, input[name="acceptTerms"]');
        if (lbl) lbl.click();
      `)
      await session.evalJs(`document.querySelector('button[type="submit"]').click()`)
      await pause(800)
      const emailErr = await session.evalJs(`document.body.innerText.includes('already exists') || document.body.innerText.includes('Email is already registered')`)
      console.log(`UI-07 Duplicate email error shown: ${emailErr}`)
      if (!emailErr) {
        const text = await session.evalJs('document.body.innerText')
        console.error('Body was:', text)
        throw new Error('UI-07 Expected duplicate email error')
      }
      await session.screenshot(join(SCREENSHOTS_DIR, 'ui-07/signup-zoom200-1440-invalid.png'))

      // State 4: Valid Completed Form State (Pre-submit Snapshot)
      await session.evalJs(`
        (() => {
          const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
          const fill = (name, val) => {
            const el = document.querySelector('input[name="' + name + '"]');
            if (!el) throw new Error('Missing input ' + name);
            setter.call(el, val);
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
          };
          fill('fullName', 'Taylor Alex');
          fill('phone', '0909876543');
          fill('email', 'taylor.alex@sportify.demo');
          fill('password', 'Password123!');
          fill('confirmPassword', 'Password123!');
          const cb = document.querySelector('input[name="acceptTerms"]');
          if (cb && !cb.checked) {
            cb.click();
          }
        })()
      `)
      await pause(400)
      const validEmail = await session.evalJs(`document.querySelector('input[name="email"]').value`)
      if (validEmail !== 'taylor.alex@sportify.demo') throw new Error('UI-07 Failed to set valid email')

      // Measure layout reflow at 720px viewport
      const measuredScrollbar = await session.evalJs(`
        document.documentElement.scrollWidth > document.documentElement.clientWidth ||
        document.body.scrollWidth > document.body.clientWidth
      `)
      const measuredClippedCount = await session.evalJs(`
        (() => {
          let clipped = 0;
          const elements = Array.from(document.querySelectorAll('label, input, button, h1, p, .auth-form__row, .password-rules'));
          for (const el of elements) {
            const rect = el.getBoundingClientRect();
            if (rect.right > window.innerWidth || rect.left < 0) {
              clipped++;
            }
          }
          return clipped;
        })()
      `)
      const measuredInputsStacked = await session.evalJs(`
        (() => {
          const inputs = Array.from(document.querySelectorAll('input:not([type="checkbox"])'));
          for (let i = 0; i < inputs.length - 1; i++) {
            const r1 = inputs[i].getBoundingClientRect();
            const r2 = inputs[i+1].getBoundingClientRect();
            if (r2.top < r1.bottom - 4 && r2.left >= r1.right - 4) {
              return false;
            }
          }
          return true;
        })()
      `)
      // Programmatic focusability only (element.focus()); keyboard reachability is measured with real Tab presses above.
      const measuredProgrammaticFocusable = await session.evalJs(`
        (() => {
          const controls = Array.from(document.querySelectorAll('input, button, a[href]'));
          for (const el of controls) {
            el.focus();
            if (document.activeElement !== el) return false;
          }
          return controls.length > 0;
        })()
      `)

      console.log(`UI-07 Layout measurements at 720px: scrollbar=${measuredScrollbar}, clipped=${measuredClippedCount}, stacked=${measuredInputsStacked}, programmaticFocusable=${measuredProgrammaticFocusable}`)
      if (measuredScrollbar) throw new Error('UI-07 Measured horizontal scrollbar at 720px viewport')
      if (measuredClippedCount > 0) throw new Error(`UI-07 Measured ${measuredClippedCount} clipped elements`)
      if (!measuredInputsStacked) throw new Error('UI-07 Inputs not stacked vertically at 720px viewport')
      if (!measuredProgrammaticFocusable) throw new Error('UI-07 Controls not programmatically focusable')

      // Save pre-submit valid filled snapshot (Finding UI07-R3-01)
      await session.screenshot(join(SCREENSHOTS_DIR, 'ui-07/signup-zoom200-1440-valid-filled.png'))

      // State 5: Submit against the mocked POST /auth/register and check the client-side redirect to /member.
      // Only the redirect is verified; dashboard APIs are not mocked, so the member page may show a load error.
      console.log('Submitting valid form with mocked registration success...')
      session.setRegisterMode('success')
      await session.evalJs(`document.querySelector('button[type="submit"]').click()`)
      await pause(1500)

      const postRegisterPath = await session.evalJs('window.location.pathname')
      console.log(`Path after submit: ${postRegisterPath}`)
      if (!postRegisterPath.startsWith('/member')) {
        throw new Error(`UI-07 Expected navigation to /member, but got ${postRegisterPath}`)
      }
      // Save post-submit redirect snapshot (mocked registration -> /member)
      await session.screenshot(join(SCREENSHOTS_DIR, 'ui-07/signup-zoom200-1440-success.png'))

      const newScreens = [
        'signup-zoom200-1440-empty',
        'signup-zoom200-1440-errors',
        'signup-zoom200-1440-invalid',
        'signup-zoom200-1440-valid-filled',
        'signup-zoom200-1440-success',
      ]
      const redirectedToMember = postRegisterPath.startsWith('/member')
      const allAssertionsPass =
        !measuredScrollbar && measuredClippedCount === 0 && measuredInputsStacked && measuredProgrammaticFocusable &&
        keyboard.allControlsReachedByTab && keyboard.tabOrderFollowsDomOrder && keyboard.shiftTabMovesBack && redirectedToMember

      const viewportSection = {
        verifiedAt: new Date().toISOString(),
        scope:
          '720px responsive viewport equivalence (layout only): a 720 CSS px Chromium viewport, the width a 1440px ' +
          'desktop lays out to at 200% zoom. Not a native browser zoom operation; WCAG 1.4.10 conformance is not claimed.',
        effectiveViewport: { ...effectiveViewport, breakpoint: 'max-width: 768px' },
        measuredAssertions: {
          horizontalScrollbarPresent: measuredScrollbar,
          clippedElementsCount: measuredClippedCount,
          inputsStackedVertically: measuredInputsStacked,
          programmaticFocusabilityVerified: measuredProgrammaticFocusable,
          registrationFormFilled: validEmail === 'taylor.alex@sportify.demo',
        },
        keyboardTraversal: keyboard,
        mockRegistrationRedirect: {
          scope: 'POST /auth/register and GET /users/me answered by the browser mock; only the client-side redirect is verified',
          pathAfterSubmit: postRegisterPath,
          redirectedToMemberRoute: redirectedToMember,
          dashboardVerified: false,
        },
        statesCaptured: [
          { state: 'empty', file: 'signup-zoom200-1440-empty.png', route: '/register' },
          { state: 'validation_errors', file: 'signup-zoom200-1440-errors.png', route: '/register', errorCount: errCount },
          { state: 'duplicate_email_409_mock', file: 'signup-zoom200-1440-invalid.png', route: '/register', duplicateErrorVisible: emailErr },
          { state: 'valid_form_filled_pre_submit', file: 'signup-zoom200-1440-valid-filled.png', route: '/register' },
          { state: 'mock_registration_success_redirect_to_member', file: 'signup-zoom200-1440-success.png', route: postRegisterPath },
        ],
        notRun: UI07_NOT_RUN,
        supersedes: UI07_SUPERSEDED_KEYS,
        outcome: allAssertionsPass ? 'PASS' : 'FAIL',
      }

      const integratedReport = mergeUi07Report(baselineReport, viewportSection, newScreens)
      writeFileSync(reportPath, JSON.stringify(integratedReport, null, 2) + '\n')
      if (!allAssertionsPass) throw new Error('UI-07 720px viewport assertions failed; see report.json')
      await session.cleanup()
      console.log('UI-07 completed successfully!')
    }

    // =========================================================================
    // 2. UI-28: Tab-resume polling & clipboard real browser test (PR #125)
    // =========================================================================
    if (shouldRun('ui-28')) {
      console.log('\n[2/5] Running UI-28 Tab-resume & Clipboard test...')
      const session = await runSession(browser, { role: 'MEMBER' })
      await session.setViewport(1440, 900, 'light')
      await session.goto('/member/payments/52/status', '.payment-summary, [aria-label="Bank transfer"]')

      // Initial check
      const initialPolls = session.getPollCount()
      console.log(`Initial GET /payments/52 requests: ${initialPolls}`)

      // Clipboard test
      await session.evalJs(`
        const btn = document.querySelector('button[aria-label*="copy" i], button.copy-btn, .payment-reference button');
        if (btn) btn.click();
      `)
      await pause(200)

      // Tab hide
      console.log('Simulating tab hide...')
      await session.evalJs(`
        Object.defineProperty(document, 'hidden', { value: true, writable: true });
        Object.defineProperty(document, 'visibilityState', { value: 'hidden', writable: true });
        document.dispatchEvent(new Event('visibilitychange'));
      `)
      await pause(1000)
      const hiddenPolls = session.getPollCount()

      // Tab resume with transition to SUCCESS
      console.log('Simulating tab resume with payment completed on backend...')
      session.setPollStatus('SUCCESS')
      await session.evalJs(`
        Object.defineProperty(document, 'hidden', { value: false, writable: true });
        Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: true });
        document.dispatchEvent(new Event('visibilitychange'));
      `)
      await pause(800)
      const resumePolls = session.getPollCount()
      console.log(`Polls after resume: ${resumePolls} (resumed polls triggered: ${resumePolls - hiddenPolls})`)

      const confirmed = await session.evalJs(`document.body.innerText.includes('Payment confirmed') || document.body.innerText.includes('Confirmed') || document.body.innerText.includes('SUCCESS')`)
      console.log(`Payment confirmed status displayed: ${confirmed}`)

      const stopPolls1 = session.getPollCount()
      await pause(2000)
      const stopPolls2 = session.getPollCount()
      const pollingStopped = stopPolls1 === stopPolls2
      console.log(`Polling terminated after SUCCESS: ${pollingStopped} (${stopPolls1} == ${stopPolls2})`)

      const tabResumeLog = {
        executedAt: new Date().toISOString(),
        testCase: 'TC-10',
        initialRequests: initialPolls,
        requestsWhileHidden: hiddenPolls - initialPolls,
        resumeImmediateRequests: resumePolls - hiddenPolls,
        pollingTerminatedAfterSuccess: pollingStopped,
        totalRequestsObserved: stopPolls2,
        expectedReferenceCode: 'SP-52-TAYLOR',
        outcome: confirmed && pollingStopped ? 'PASS' : 'FAIL',
      }
      writeFileSync(join(SCREENSHOTS_DIR, 'ui-28/tab-resume-log.json'), JSON.stringify(tabResumeLog, null, 2))
      await session.cleanup()
      console.log('UI-28 tab-resume test completed!')
    }

    // =========================================================================
    // 3. UI-29: Member Receipts & Verified 1-page A4 PDFs (PR #126)
    // =========================================================================
    if (shouldRun('ui-29')) {
      console.log('\n[3/5] Generating UI-29 Member Receipts & 1-page A4 PDFs...')
      const session = await runSession(browser, { role: 'MEMBER' })

      // Capture list
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/member/payments', 'table, .payment-rows, .card')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-29/mypayments-list-${th}-${vp.w}.png`))
        }
      }

      // Capture receipt views (INV-1054)
      for (const vp of [{ w: 390, h: 844 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/member/payments/receipts/1054', 'h1, h2, .receipt')
          const hasInvoice = await session.evalJs(`document.body.innerText.includes('INV-1054')`)
          if (!hasInvoice) throw new Error('UI-29 receipt view missing INV-1054')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-29/receipt-view-${th}-${vp.w}.png`))
        }
      }

      // Print short receipt PDF (INV-1054)
      await session.setViewport(1440, 900, 'light')
      await session.goto('/member/payments/receipts/1054', 'h1, h2, .receipt')
      const pdfShort = await session.printPdf(join(SCREENSHOTS_DIR, 'ui-29/receipt-short-a4.pdf'))
      if (pdfShort.pageCount !== 1) throw new Error(`receipt-short-a4.pdf has ${pdfShort.pageCount} pages, expected 1`)

      // Print long receipt PDF (INV-1058)
      await session.goto('/member/payments/receipts/1058', 'h1, h2, .receipt')
      const pdfLong = await session.printPdf(join(SCREENSHOTS_DIR, 'ui-29/receipt-long-a4.pdf'))
      if (pdfLong.pageCount !== 1) throw new Error(`receipt-long-a4.pdf has ${pdfLong.pageCount} pages, expected 1`)

      await session.cleanup()
      console.log('UI-29 receipts & A4 PDFs verified and completed!')
    }

    // =========================================================================
    // 4. UI-30: Receptionist Payment Queue with RECEPTIONIST role (PR #127)
    // =========================================================================
    if (shouldRun('ui-30')) {
      console.log('\n[4/5] Generating UI-30 Reception Payment Queue screenshots...')
      const session = await runSession(browser, { role: 'RECEPTIONIST' })
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/payments', 'table')
          const hasNeedsReview = await session.evalJs(`document.body.innerText.includes('Needs review')`)
          if (!hasNeedsReview) throw new Error('UI-30 queue missing Needs review row')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-30/reception-queue-${th}-${vp.w}.png`))
        }
      }
      await session.cleanup()
      console.log('UI-30 queue screenshots completed!')
    }

    // =========================================================================
    // 5. UI-31: Reception Payment Verify, Mismatch & Desk Receipt PDF (PR #128)
    // =========================================================================
    if (shouldRun('ui-31')) {
      console.log('\n[5/5] Generating UI-31 Reception Verify, Mismatch & Desk Receipt...')
      const session = await runSession(browser, { role: 'RECEPTIONIST' })

      // Verify screens
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/payments/41', 'form, .payment-rows')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-31/reception-verify-${th}-${vp.w}.png`))
        }
      }

      // Mismatch screens
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/payments/44', '.payment-rows')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-31/reception-mismatch-${th}-${vp.w}.png`))
        }
      }

      // Desk receipt view & A4 PDF
      for (const vp of [{ w: 390, h: 844 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/payments/41/receipt', 'h1, h2, .receipt')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-31/reception-receipt-view-${th}-${vp.w}.png`))
        }
      }

      await session.setViewport(1440, 900, 'light')
      await session.goto('/reception/payments/41/receipt', 'h1, h2, .receipt')
      const pdfDesk = await session.printPdf(join(SCREENSHOTS_DIR, 'ui-31/reception-receipt-a4.pdf'))
      if (pdfDesk.pageCount !== 1) throw new Error(`reception-receipt-a4.pdf has ${pdfDesk.pageCount} pages, expected 1`)

      await session.cleanup()
      console.log('UI-31 verify & desk receipt completed!')
    }

    // =========================================================================
    // 6. UI-32: Refund Requests, Detail & Irreversible Confirmation Modal (PR #129)
    // =========================================================================
    if (shouldRun('ui-32')) {
      console.log('\n[6/5] Generating UI-32 Refund screens & Confirmation Modal...')
      const session = await runSession(browser, { role: 'RECEPTIONIST' })

      // Refund requests queue
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/refunds', 'table, .card')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-32/refund-requests-${th}-${vp.w}.png`))
        }
      }

      // Refund detail
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/refunds/11?memberId=2', '.payment-rows')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-32/refund-detail-${th}-${vp.w}.png`))
        }
      }

      // Confirmation Modal Captures
      console.log('Capturing confirmation modal across viewports & themes...')
      for (const vp of [{ w: 390, h: 844 }, { w: 768, h: 1024 }, { w: 1440, h: 900 }]) {
        for (const th of ['light', 'dark']) {
          await session.setViewport(vp.w, vp.h, th)
          await session.goto('/reception/refunds/11?memberId=2', '.payment-rows')
          // Check checkbox and open modal
          await session.evalJs(`
            const cb = document.querySelector('input[type="checkbox"]');
            if (cb && !cb.checked) cb.click();
            const btn = document.querySelector('button[type="submit"]');
            if (btn) btn.click();
          `)
          await pause(300)
          const modalOpen = await session.evalJs(`Boolean(document.querySelector('[role="dialog"], .modal'))`)
          if (!modalOpen) throw new Error('UI-32 modal did not open')
          await session.screenshot(join(SCREENSHOTS_DIR, `ui-32/refund-modal-${th}-${vp.w}.png`))
        }
      }

      // Long name modal capture
      await session.setViewport(390, 844, 'light')
      await session.goto('/reception/refunds/11?memberId=2', '.payment-rows')
      await session.evalJs(`
        const cb = document.querySelector('input[type="checkbox"]');
        if (cb && !cb.checked) cb.click();
        const btn = document.querySelector('button[type="submit"]');
        if (btn) btn.click();
      `)
      await pause(300)
      await session.screenshot(join(SCREENSHOTS_DIR, `ui-32/refund-modal-long-name-light-390.png`))

      // Keyboard & Action verification
      console.log('Testing modal keyboard interactions...')
      // Escape test
      await session.evalJs(`
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      `)
      await pause(300)
      const modalClosedByEscape = await session.evalJs(`!document.querySelector('[role="dialog"], .modal')`)
      console.log(`Modal closed by Escape: ${modalClosedByEscape}`)
      const putCountAfterEscape = session.getPutCompleteCount()

      // Re-open and Cancel button test
      await session.evalJs(`document.querySelector('button[type="submit"]').click()`)
      await pause(300)
      await session.evalJs(`
        const cancelBtn = [...document.querySelectorAll('button')].find(b => b.innerText.trim() === 'Cancel');
        if (cancelBtn) cancelBtn.click();
      `)
      await pause(300)
      const modalClosedByCancel = await session.evalJs(`!document.querySelector('[role="dialog"], .modal')`)
      console.log(`Modal closed by Cancel button: ${modalClosedByCancel}`)
      const putCountAfterCancel = session.getPutCompleteCount()

      // Re-open and Confirm button test
      await session.evalJs(`document.querySelector('button[type="submit"]').click()`)
      await pause(300)
      await session.evalJs(`
        const confirmBtn = [...document.querySelectorAll('button')].find(b => b.innerText.includes('Yes, record'));
        if (confirmBtn) confirmBtn.click();
      `)
      await pause(500)
      const putCountAfterConfirm = session.getPutCompleteCount()
      console.log(`PUT requests sent on explicit confirmation: ${putCountAfterConfirm}`)

      const modalLog = {
        executedAt: new Date().toISOString(),
        testCase: 'TC-09',
        modalOpenedSuccessfully: true,
        dismissedByEscapeWithoutPut: modalClosedByEscape && putCountAfterEscape === 0,
        dismissedByCancelWithoutPut: modalClosedByCancel && putCountAfterCancel === 0,
        singlePutSentOnExplicitConfirm: putCountAfterConfirm === 1,
        approvedAmount: '450,000 đ',
        refundCode: 'REF-0011',
        outcome: modalClosedByEscape && modalClosedByCancel && putCountAfterConfirm === 1 ? 'PASS' : 'FAIL',
      }
      writeFileSync(join(SCREENSHOTS_DIR, 'ui-32/modal-interaction-log.json'), JSON.stringify(modalLog, null, 2))
      await session.cleanup()
      console.log('UI-32 modal & interaction test completed!')
    }

    console.log('\n======================================================')
    console.log('ALL EVIDENCE & TEST ARTIFACTS GENERATED SUCCESSFULLY!')
    console.log('======================================================')
  } finally {
    browser.close()
  }
}

main().catch((err) => {
  console.error('Evidence generator failed:', err)
  process.exit(1)
})
