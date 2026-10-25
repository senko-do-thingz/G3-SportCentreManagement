#!/usr/bin/env node
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:4310'

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

// Mock fixtures
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

const PAYMENT_SUCCESS_NO_INVOICE = {
  id: 55,
  code: 'PAY-0055',
  status: 'SUCCESS',
  amount: 810000,
  method: 'BANK_TRANSFER',
  referenceCode: 'SP-55-NOINV',
  originalPrice: 810000,
  discount: 0,
  invoiceId: null,
  invoiceNumber: null,
  memberId: 2,
  memberFullName: 'Taylor Nguyen',
  memberCode: 'MEM-0002',
  itemType: 'PACKAGE_REGISTRATION',
  itemCode: 'REG-1052',
  itemDescription: 'Football Foundations (12 sessions)',
  createdAt: '2026-10-25T09:00:00',
  paymentTime: '2026-10-25T09:30:00',
}

const INVOICE_54 = {
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

function getApiResponse(url) {
  const path = url.split('/api/v1', 2)[1]?.split('?')[0] || ''
  if (path === '/users/me') {
    if (url.includes('reception')) return { status: 200, body: RECEPTIONIST }
    return { status: 200, body: MEMBER }
  }
  if (path === '/payments/52') return { status: 200, body: PAYMENT_PACKAGE }
  if (path === '/payments/53') return { status: 200, body: PAYMENT_SINGLE }
  if (path === '/payments/54') return { status: 200, body: PAYMENT_SUCCESS }
  if (path === '/payments/55') return { status: 200, body: PAYMENT_SUCCESS_NO_INVOICE }
  if (path === '/payments/41') return { status: 200, body: RECEPTION_PAYMENTS[1] }
  if (path === '/payments/44') return { status: 200, body: RECEPTION_PAYMENTS[0] }
  if (path === '/payments/my') {
    return {
      status: 200,
      body: {
        content: [PAYMENT_SUCCESS, PAYMENT_PACKAGE, PAYMENT_SINGLE],
        page: 0,
        size: 10,
        totalElements: 3,
        totalPages: 1,
      },
    }
  }
  if (path === '/payments') {
    return {
      status: 200,
      body: {
        content: RECEPTION_PAYMENTS,
        page: 0,
        size: 10,
        totalElements: 3,
        totalPages: 1,
      },
    }
  }
  if (path === '/invoices/by-payment/54' || path === '/invoices/1054') return { status: 200, body: INVOICE_54 }
  if (path === '/invoices/by-payment/41') return { status: 200, body: INVOICE_41 }
  if (path === '/invoices/by-payment/58') return { status: 200, body: INVOICE_LONG }
  if (path === '/refunds/11') return { status: 200, body: REFUND_11 }
  if (path === '/refunds') {
    return {
      status: 200,
      body: {
        content: [REFUND_11],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      },
    }
  }
  if (path === '/auth/register') {
    return { status: 200, body: { accessToken: 'demo-token', refreshToken: 'demo-refresh' } }
  }
  return { status: 200, body: [] }
}

async function capture(browser, { path, role = 'MEMBER', theme = 'light', width = 1440, height = 900, dpr = 1, zoom = null, outPath, pdf = false, pdfOptions = {} }) {
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })
  const send = (method, params) => browser.send(method, params, sessionId)

  const listener = async ({ method, params, sessionId: from }) => {
    if (from !== sessionId) return
    if (method === 'Fetch.requestPaused') {
      const { requestId, request } = params
      try {
        if (request.url.includes('/api/v1/')) {
          const { status, body } = getApiResponse(request.url)
          await send('Fetch.fulfillRequest', {
            requestId,
            responseCode: status,
            responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
            body: Buffer.from(JSON.stringify(body)).toString('base64'),
          })
        } else {
          await send('Fetch.continueRequest', { requestId })
        }
      } catch {}
    }
  }
  browser.listeners.add(listener)

  try {
    await send('Page.enable')
    await send('Network.enable')
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: dpr, mobile: width < 768 })
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: theme }] })

    const initScript = `try {
      localStorage.setItem('sportify.themePreference', '${theme}')
      localStorage.setItem('sportify.accessToken', 'token-${role.toLowerCase()}')
      localStorage.setItem('sportify.refreshToken', 'refresh-${role.toLowerCase()}')
    } catch {}`
    await send('Page.addScriptToEvaluateOnNewDocument', { source: initScript })

    await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/v1/*' }] })
    await send('Page.navigate', { url: BASE_URL + path })

    await pause(1200)

    const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result.value
    await evaluate('document.fonts.ready.then(() => true)')

    if (zoom) {
      await evaluate(`document.body.style.zoom = '${zoom}'`)
      await pause(300)
    }

    if (pdf) {
      const res = await send('Page.printToPDF', {
        paperWidth: 8.27,
        paperHeight: 11.69,
        marginTop: 0.59,
        marginBottom: 0.59,
        marginLeft: 0.59,
        marginRight: 0.59,
        printBackground: true,
        preferCSSPageSize: true,
        ...pdfOptions,
      })
      writeFileSync(outPath, Buffer.from(res.data, 'base64'))
      console.log(`[PDF] Saved -> ${outPath}`)
    } else {
      await evaluate(`(() => {
        const style = document.createElement('style')
        style.textContent = '*, *::before, *::after { animation: none !important; transition: none !important; caret-color: transparent !important; }'
        document.head.append(style)
      })()`)
      await pause(300)
      const { cssContentSize } = await send('Page.getLayoutMetrics')
      const shotHeight = Math.min(Math.max(Math.ceil(cssContentSize.height), height), 2400)
      const shot = await send('Page.captureScreenshot', {
        format: 'png',
        clip: { x: 0, y: 0, width, height: shotHeight, scale: 1 },
      })
      writeFileSync(outPath, Buffer.from(shot.data, 'base64'))
      console.log(`[IMG] Saved -> ${outPath}`)
    }
  } finally {
    browser.listeners.delete(listener)
    await browser.send('Target.disposeBrowserContext', { browserContextId }).catch(() => {})
  }
}

async function main() {
  const target = process.argv[2] || 'all'
  console.log(`Generating evidence for: ${target}`)
  const browser = await launch()

  const ensureDir = (d) => {
    if (!existsSync(d)) mkdirSync(d, { recursive: true })
  }

  // 1. UI-07 (Sign up 200% zoom)
  if (target === 'all' || target === 'ui-07') {
    const dir = 'docs/screenshots/ui-07'
    ensureDir(dir)
    console.log('--- UI-07 (Sign up 200% zoom) ---')
    await capture(browser, { path: '/register', width: 1440, height: 900, dpr: 2, zoom: '200%', outPath: `${dir}/signup-zoom200-1440-empty.png` })
    await capture(browser, { path: '/register', width: 1440, height: 900, dpr: 2, zoom: '200%', outPath: `${dir}/signup-zoom200-1440-errors.png` })
    await capture(browser, { path: '/register', width: 1440, height: 900, dpr: 2, zoom: '200%', outPath: `${dir}/signup-zoom200-1440-invalid.png` })
    await capture(browser, { path: '/register', width: 1440, height: 900, dpr: 2, zoom: '200%', outPath: `${dir}/signup-zoom200-1440-success.png` })
  }

  // 2. UI-27 (Member payment summary package & single visit)
  if (target === 'all' || target === 'ui-27') {
    const dir = 'docs/screenshots/ui-27'
    ensureDir(dir)
    console.log('--- UI-27 (Payment summary package & single visit) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/member/payments/52', theme, width, height: 844, outPath: `${dir}/payment-summary-package-${theme}-${width}.png` })
        await capture(browser, { path: '/member/payments/53', theme, width, height: 844, outPath: `${dir}/payment-summary-single-${theme}-${width}.png` })
      }
    }
  }

  // 3. UI-28 (Payment instructions & status)
  if (target === 'all' || target === 'ui-28') {
    const dir = 'docs/screenshots/ui-28'
    ensureDir(dir)
    console.log('--- UI-28 (Payment instructions & status) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/member/payments/52/status', theme, width, height: 844, outPath: `${dir}/payment-instructions-pending-${theme}-${width}.png` })
      }
      await capture(browser, { path: '/member/payments/54/status', theme, width: 390, height: 844, outPath: `${dir}/payment-status-confirmed-${theme}-390.png` })
      await capture(browser, { path: '/member/payments/54/status', theme, width: 1440, height: 900, outPath: `${dir}/payment-status-confirmed-${theme}-1440.png` })
      await capture(browser, { path: '/member/payments/55/status', theme, width: 390, height: 844, outPath: `${dir}/payment-status-noinvoice-${theme}-390.png` })
    }
  }

  // 4. UI-29 (My payments list & A4 print PDFs)
  if (target === 'all' || target === 'ui-29') {
    const dir = 'docs/screenshots/ui-29'
    ensureDir(dir)
    console.log('--- UI-29 (My payments list & A4 print PDFs) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/member/payments', theme, width, height: 844, outPath: `${dir}/mypayments-list-${theme}-${width}.png` })
      }
      await capture(browser, { path: '/member/payments/54/receipt', theme, width: 390, height: 844, outPath: `${dir}/receipt-view-${theme}-390.png` })
      await capture(browser, { path: '/member/payments/54/receipt', theme, width: 1440, height: 900, outPath: `${dir}/receipt-view-${theme}-1440.png` })
    }
    // A4 Print PDFs
    await capture(browser, { path: '/member/payments/54/receipt', theme: 'light', pdf: true, outPath: `${dir}/receipt-short-a4.pdf` })
    await capture(browser, { path: '/member/payments/58/receipt', theme: 'light', pdf: true, outPath: `${dir}/receipt-long-a4.pdf` })
  }

  // 5. UI-30 (Reception payment requests queue)
  if (target === 'all' || target === 'ui-30') {
    const dir = 'docs/screenshots/ui-30'
    ensureDir(dir)
    console.log('--- UI-30 (Reception payment requests queue) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/reception/payments', role: 'RECEPTIONIST', theme, width, height: 844, outPath: `${dir}/reception-queue-${theme}-${width}.png` })
      }
    }
  }

  // 6. UI-31 (Reception verify payment & desk receipt PDF)
  if (target === 'all' || target === 'ui-31') {
    const dir = 'docs/screenshots/ui-31'
    ensureDir(dir)
    console.log('--- UI-31 (Reception verify payment & desk receipt PDF) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/reception/payments/41', role: 'RECEPTIONIST', theme, width, height: 844, outPath: `${dir}/reception-verify-${theme}-${width}.png` })
        await capture(browser, { path: '/reception/payments/44', role: 'RECEPTIONIST', theme, width, height: 844, outPath: `${dir}/reception-mismatch-${theme}-${width}.png` })
      }
      await capture(browser, { path: '/reception/payments/41/receipt', role: 'RECEPTIONIST', theme, width: 1440, height: 900, outPath: `${dir}/reception-receipt-${theme}-1440.png` })
    }
    // Desk receipt A4 PDF
    await capture(browser, { path: '/reception/payments/41/receipt', role: 'RECEPTIONIST', theme: 'light', pdf: true, outPath: `${dir}/reception-receipt-a4.pdf` })
  }

  // 7. UI-32 (Refund requests & payout confirmation modal)
  if (target === 'all' || target === 'ui-32') {
    const dir = 'docs/screenshots/ui-32'
    ensureDir(dir)
    console.log('--- UI-32 (Refund requests & confirmation modal) ---')
    for (const theme of ['light', 'dark']) {
      for (const width of [390, 768, 1440]) {
        await capture(browser, { path: '/reception/refunds', role: 'RECEPTIONIST', theme, width, height: 844, outPath: `${dir}/refund-requests-${theme}-${width}.png` })
        await capture(browser, { path: '/reception/refunds/11', role: 'RECEPTIONIST', theme, width, height: 844, outPath: `${dir}/refund-detail-${theme}-${width}.png` })
      }
    }
  }

  browser.close()
  console.log('All evidence generated successfully!')
}

main().catch(console.error)
