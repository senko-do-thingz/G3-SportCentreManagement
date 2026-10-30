import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = resolve(__dirname, '../..')
const DIST = join(ROOT, 'frontend/dist')
const OUT_DIR = join(ROOT, 'tasks/QR-06 (i)/reports/round-02')
const SCREENSHOTS_DIR = join(OUT_DIR, 'screenshots')
const LOGS_DIR = join(OUT_DIR, 'browser-logs')

mkdirSync(SCREENSHOTS_DIR, { recursive: true })
mkdirSync(LOGS_DIR, { recursive: true })

const MIMES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.jpg': 'image/jpeg',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
}

// Projection scenarios for QR-06 verification
const PROJECTIONS = {
  // Scenario 1: PENDING with active BIDV session (locked bank)
  '101': {
    paymentId: 101,
    paymentCode: 'PAY-101',
    amount: 810000,
    currency: 'VND',
    paymentStatus: 'PENDING',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    activeSession: {
      sessionId: '00000000-0000-4000-8000-000000000001',
      bankCode: 'BIDV_DEMO',
      bankLabel: 'BIDV (demo)',
      state: 'PENDING',
      createdAt: '2026-10-31T03:00:00Z',
      expiresAt: '2026-10-31T03:10:00Z',
      serverTime: '2026-10-31T03:02:00Z',
    },
  },
  // Scenario 2: Settled with valid SIMULATED provenance
  '102': {
    paymentId: 102,
    paymentCode: 'PAY-102',
    amount: 810000,
    currency: 'VND',
    paymentStatus: 'SUCCESS',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: 'SIMULATED',
    settlementProvider: 'SPORTIFY_SIMULATOR',
    simulatedTransactionId: 9001,
    activeSession: {
      sessionId: '00000000-0000-4000-8000-000000000001',
      bankCode: 'BIDV_DEMO',
      bankLabel: 'BIDV (demo)',
      state: 'SETTLED',
      createdAt: '2026-10-31T03:00:00Z',
      expiresAt: '2026-10-31T03:10:00Z',
      serverTime: '2026-10-31T03:04:00Z',
    },
  },
  // Scenario 3: Settled with ACTUAL provenance
  '103': {
    paymentId: 103,
    paymentCode: 'PAY-103',
    amount: 1200000,
    currency: 'VND',
    paymentStatus: 'SUCCESS',
    itemDescription: 'Diamond Annual Pass',
    memberFullName: 'Tran Thi B',
    memberCode: 'MEM-045',
    memberId: 45,
    settlementType: 'ACTUAL',
    settlementProvider: 'DESK_CASH',
    simulatedTransactionId: null,
    activeSession: null,
  },
  // Scenario 4: Settled with legacy NULL provenance
  '104': {
    paymentId: 104,
    paymentCode: 'PAY-104',
    amount: 450000,
    currency: 'VND',
    paymentStatus: 'SUCCESS',
    itemDescription: 'Single Visit Pass',
    memberFullName: 'Le Van C',
    memberCode: 'MEM-012',
    memberId: 12,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    activeSession: null,
  },
  // Scenario 5: Terminal FAILED state
  '105': {
    paymentId: 105,
    paymentCode: 'PAY-105',
    amount: 600000,
    currency: 'VND',
    paymentStatus: 'FAILED',
    itemDescription: 'Badminton Court 2 Hours',
    memberFullName: 'Pham Van D',
    memberCode: 'MEM-099',
    memberId: 99,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    activeSession: null,
  },
  // Scenario 6: PENDING with EXPIRED session
  '106': {
    paymentId: 106,
    paymentCode: 'PAY-106',
    amount: 810000,
    currency: 'VND',
    paymentStatus: 'PENDING',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    activeSession: {
      sessionId: '00000000-0000-4000-8000-000000000002',
      bankCode: 'VIETCOMBANK_DEMO',
      bankLabel: 'Vietcombank (demo)',
      state: 'EXPIRED',
      createdAt: '2026-10-31T02:00:00Z',
      expiresAt: '2026-10-31T02:10:00Z',
      serverTime: '2026-10-31T02:15:00Z',
    },
  },
  // Scenario 7: PENDING with NO session (preview bank dropdown)
  '107': {
    paymentId: 107,
    paymentCode: 'PAY-107',
    amount: 500000,
    currency: 'VND',
    paymentStatus: 'PENDING',
    itemDescription: 'Swimming Pool 10-Session Pass',
    memberFullName: 'Hoang Van E',
    memberCode: 'MEM-110',
    memberId: 110,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    activeSession: null,
  },
}

function startStaticServer(port = 4567) {
  return new Promise((resolve) => {
    const server = createServer((req, res) => {
      const url = new URL(req.url, `http://localhost:${port}`)
      const pathname = url.pathname

      // API mock endpoints for desk projection and auth
      if (pathname.startsWith('/api/v1/payments/simulated-qr/desk-projection/')) {
        const id = pathname.split('/').pop()
        const proj = PROJECTIONS[id]
        if (proj) {
          res.writeHead(200, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify(proj))
          return
        }
        res.writeHead(404, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ code: 'PAYMENT_NOT_FOUND', message: 'Payment not found' }))
        return
      }

      if (pathname === '/api/v1/users/me' || pathname === '/api/v1/auth/me') {
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(
          JSON.stringify({
            id: 10,
            email: 'receptionist@sportify.demo',
            fullName: 'Linh Receptionist',
            role: 'RECEPTIONIST',
            status: 'ACTIVE',
          }),
        )
        return
      }

      if (pathname === '/api/v1/payments/simulated-qr/capability') {
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(
          JSON.stringify({
            enabled: true,
            settlementEnabled: true,
            contractVersion: 1,
          }),
        )
        return
      }

      // Serve built frontend assets
      let filePath = join(DIST, pathname)
      if (!existsSync(filePath) || pathname === '/' || !extname(pathname)) {
        filePath = join(DIST, 'index.html')
      }
      try {
        const content = readFileSync(filePath)
        const mime = MIMES[extname(filePath)] || 'application/octet-stream'
        res.writeHead(200, { 'Content-Type': mime })
        res.end(content)
      } catch {
        res.writeHead(404)
        res.end('Not found')
      }
    })

    server.listen(port, () => resolve(server))
  })
}

function findEdge() {
  const candidates = [
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
  ]
  return candidates.find((p) => existsSync(p))
}

async function launchBrowser() {
  const profile = mkdtempSync(join(tmpdir(), 'edge-evidence-'))
  const edge = spawn(
    findEdge(),
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
    edge.stderr.on('data', (chunk) => {
      text += chunk
      const match = /DevTools listening on (ws:\/\/\S+)/.exec(text)
      if (match) resolve(match[1])
    })
    edge.on('exit', () => reject(new Error(`Edge exited prematurely: ${text}`)))
  })

  const socket = new WebSocket(endpoint)
  await new Promise((resolve) => socket.addEventListener('open', resolve, { once: true }))

  let msgId = 0
  const pending = new Map()
  const listeners = new Set()

  socket.addEventListener('message', ({ data }) => {
    const msg = JSON.parse(data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      if (msg.error) reject(new Error(msg.error.message))
      else resolve(msg.result)
    } else {
      for (const l of listeners) l(msg)
    }
  })

  const send = (method, params = {}, sessionId) =>
    new Promise((resolve, reject) => {
      msgId += 1
      pending.set(msgId, { resolve, reject })
      socket.send(JSON.stringify({ id: msgId, method, params, sessionId }))
    })

  const close = () => {
    socket.close()
    edge.kill()
    try {
      rmSync(profile, { recursive: true, force: true })
    } catch {}
  }

  return { send, listeners, close }
}

async function run() {
  const port = 4567
  const server = await startStaticServer(port)
  console.log(`Test server running at http://localhost:${port}`)

  const browser = await launchBrowser()
  console.log('Microsoft Edge launched via Chrome DevTools Protocol')

  const consoleLogs = []
  const networkRequests = []
  const accessibilityResults = []

  // Create browser context and page target session
  const { browserContextId } = await browser.send('Target.createBrowserContext')
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })

  const sendPage = (method, params = {}) => browser.send(method, params, sessionId)

  browser.listeners.add((msg) => {
    if (msg.sessionId !== sessionId) return
    if (msg.method === 'Runtime.consoleAPICalled') {
      const args = (msg.params.args || []).map((a) => a.value ?? a.description ?? '').join(' ')
      consoleLogs.push(`[${msg.params.type}] ${args}`)
    }
    if (msg.method === 'Network.requestWillBeSent') {
      networkRequests.push({
        url: msg.params.request.url,
        method: msg.params.request.method,
        timestamp: new Date().toISOString(),
      })
    }
  })

  await sendPage('Network.enable')
  await sendPage('Page.enable')
  await sendPage('Runtime.enable')

  // Set up initial localStorage on origin
  await sendPage('Page.navigate', { url: `http://localhost:${port}/` })
  await new Promise((r) => setTimeout(r, 600))
  await sendPage('Runtime.evaluate', {
    expression: `
      localStorage.setItem('auth.user', JSON.stringify({
        id: 10,
        email: 'receptionist@sportify.demo',
        fullName: 'Linh Receptionist',
        role: 'RECEPTIONIST',
        status: 'ACTIVE'
      }));
      localStorage.setItem('auth.token', 'mock-receptionist-token-round-02');
    `,
  })

  async function captureView(paymentId, filename, { width, height, theme = 'light', zoom = 1, focusSelector = null }) {
    await sendPage('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: width < 600,
    })

    const targetUrl = `http://localhost:${port}/reception/payments/${paymentId}/qr-display`
    await sendPage('Page.navigate', { url: targetUrl })

    // Wait for content render
    await new Promise((resolve) => setTimeout(resolve, 1500))

    // Apply theme, zoom, focus
    await sendPage('Runtime.evaluate', {
      expression: `
        document.documentElement.setAttribute('data-theme', '${theme}');
        ${zoom !== 1 ? `document.body.style.zoom = '${zoom}';` : ''}
        ${focusSelector ? `document.querySelector('${focusSelector}')?.focus();` : ''}
      `,
    })

    await new Promise((resolve) => setTimeout(resolve, 600))

    const domAudit = await sendPage('Runtime.evaluate', {
      expression: `({
        title: document.title,
        heading: document.querySelector('h1')?.textContent?.trim(),
        cardTitles: Array.from(document.querySelectorAll('.card__title')).map(el => el.textContent.trim()),
        statusBadge: document.querySelector('.reception-status-header .badge')?.textContent?.trim(),
        hasInactiveNotice: !!document.querySelector('.qr-demo-card__body [role="status"]'),
        copyButtonsCount: document.querySelectorAll('.qr-demo-card__copy-actions button').length,
        hasNoMoneyWarning: document.body.textContent.includes('Simulated QR Payment - No real money is transferred.'),
        noHorizontalOverflow: document.documentElement.scrollWidth <= window.innerWidth
      })`,
      returnByValue: true,
    })

    accessibilityResults.push({
      view: filename,
      viewport: `${width}x${height}`,
      theme,
      zoom: `${zoom * 100}%`,
      audit: domAudit.result.value,
    })

    const screenshotData = await sendPage('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: false,
    })

    const buffer = Buffer.from(screenshotData.data, 'base64')
    const filePath = join(SCREENSHOTS_DIR, filename)
    writeFileSync(filePath, buffer)
    console.log(`Saved screenshot: ${filename} (${width}x${height}, ${theme}, noOverflow=${domAudit.result.value?.noHorizontalOverflow})`)
  }

  console.log('Capturing desktop and responsive viewports across themes...')

  // Viewport 1280 (Light & Dark)
  await captureView('101', 'desktop-1280-light-pending.png', { width: 1280, height: 900, theme: 'light' })
  await captureView('101', 'desktop-1280-dark-pending.png', { width: 1280, height: 900, theme: 'dark' })

  // Viewport 1440 (Light & Dark)
  await captureView('101', 'desktop-1440-light-pending.png', { width: 1440, height: 900, theme: 'light' })
  await captureView('101', 'desktop-1440-dark-pending.png', { width: 1440, height: 900, theme: 'dark' })

  // Viewport 1920 (Light & Dark)
  await captureView('101', 'desktop-1920-light-pending.png', { width: 1920, height: 1080, theme: 'light' })
  await captureView('101', 'desktop-1920-dark-pending.png', { width: 1920, height: 1080, theme: 'dark' })

  // Settled SIMULATED (Paid - Simulated, Inactive QR, View receipt)
  await captureView('102', 'desktop-1440-light-settled-simulated.png', { width: 1440, height: 900, theme: 'light' })
  await captureView('102', 'desktop-1440-dark-settled-simulated.png', { width: 1440, height: 900, theme: 'dark' })

  // Settled ACTUAL (Paid - Actual)
  await captureView('103', 'desktop-1440-light-settled-actual.png', { width: 1440, height: 900, theme: 'light' })

  // Settled Legacy unverified (Paid - Legacy unverified)
  await captureView('104', 'desktop-1440-light-legacy-unverified.png', { width: 1440, height: 900, theme: 'light' })

  // Terminal FAILED state (Inactive QR, Failed status)
  await captureView('105', 'desktop-1440-light-failed-inactive.png', { width: 1440, height: 900, theme: 'light' })

  // PENDING with EXPIRED session
  await captureView('106', 'desktop-1440-light-expired-session.png', { width: 1440, height: 900, theme: 'light' })

  // PENDING with NO session (preview bank dropdown)
  await captureView('107', 'desktop-1440-light-no-session.png', { width: 1440, height: 900, theme: 'light' })

  // 200% zoom check at 1440px
  await captureView('101', 'desktop-1440-light-200percent-zoom.png', { width: 1440, height: 900, theme: 'light', zoom: 2 })

  // Keyboard navigation / focus indicator
  await captureView('101', 'desktop-1440-light-keyboard-focus.png', {
    width: 1440,
    height: 900,
    theme: 'light',
    focusSelector: '.reception-actions .btn--secondary',
  })

  // Responsive smoke checks (768px tablet, 390px mobile)
  await captureView('101', 'responsive-768-light.png', { width: 768, height: 1024, theme: 'light' })
  await captureView('101', 'responsive-390-light.png', { width: 390, height: 844, theme: 'light' })

  // Write log files
  writeFileSync(join(LOGS_DIR, 'browser-console.log'), consoleLogs.join('\n') || '[INFO] No console errors or warnings recorded.')
  writeFileSync(join(LOGS_DIR, 'network-requests.json'), JSON.stringify(networkRequests, null, 2))
  writeFileSync(join(LOGS_DIR, 'accessibility-audit.json'), JSON.stringify(accessibilityResults, null, 2))

  console.log('Finished capturing all screenshots and logs.')

  browser.close()
  server.close()
}

run().catch((err) => {
  console.error('Evidence generation failed:', err)
  process.exit(1)
})
