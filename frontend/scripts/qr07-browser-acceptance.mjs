import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = resolve(__dirname, '../..')
const DIST = join(ROOT, 'frontend/dist')
const OUT_DIR = join(ROOT, 'tasks/QR-07 (i)/reports/round-01')
const SCREENSHOTS_DIR = join(OUT_DIR, 'screenshots')
const LOGS_DIR = join(OUT_DIR, 'browser-logs')
const A11Y_DIR = join(OUT_DIR, 'accessibility')
const TOOLING_DIR = join(OUT_DIR, 'tooling')

mkdirSync(SCREENSHOTS_DIR, { recursive: true })
mkdirSync(LOGS_DIR, { recursive: true })
mkdirSync(A11Y_DIR, { recursive: true })
mkdirSync(TOOLING_DIR, { recursive: true })

// Copy axe.min.js to tooling directory for offline reproducibility
const SOURCE_AXE = join(ROOT, 'docs/audit/qr04-r3-private-qa/axe.min.js')
const TARGET_AXE = join(TOOLING_DIR, 'axe.min.js')
if (existsSync(SOURCE_AXE) && !existsSync(TARGET_AXE)) {
  copyFileSync(SOURCE_AXE, TARGET_AXE)
}
const AXE_SOURCE_PATH = existsSync(TARGET_AXE) ? TARGET_AXE : SOURCE_AXE
const AXE_SCRIPT = existsSync(AXE_SOURCE_PATH) ? readFileSync(AXE_SOURCE_PATH, 'utf-8') : ''

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

// Canonical payment and session test fixtures
const PAYMENTS = {
  '101': {
    id: 101,
    code: 'PAY-101',
    amount: 810000,
    currency: 'VND',
    status: 'PENDING',
    paymentStatus: 'PENDING',
    method: 'ONLINE',
    description: 'Gold Membership (1 Month)',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T03:00:00Z',
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
  '102': {
    id: 102,
    code: 'PAY-102',
    amount: 810000,
    currency: 'VND',
    status: 'SUCCESS',
    paymentStatus: 'SUCCESS',
    method: 'ONLINE',
    description: 'Gold Membership (1 Month)',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: 'SIMULATED',
    settlementProvider: 'SPORTIFY_SIMULATOR',
    simulatedTransactionId: 9001,
    referenceCode: 'TXN-9001',
    createdAt: '2026-10-31T03:00:00Z',
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
  '103': {
    id: 103,
    code: 'PAY-103',
    amount: 1200000,
    currency: 'VND',
    status: 'SUCCESS',
    paymentStatus: 'SUCCESS',
    method: 'CASH',
    description: 'Diamond Annual Pass',
    itemDescription: 'Diamond Annual Pass',
    memberFullName: 'Tran Thi B',
    memberCode: 'MEM-042',
    memberId: 42,
    settlementType: 'ACTUAL',
    settlementProvider: 'FRONT_DESK_CASH',
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T03:00:00Z',
    activeSession: null,
  },
  '104': {
    id: 104,
    code: 'PAY-104',
    amount: 500000,
    currency: 'VND',
    status: 'SUCCESS',
    paymentStatus: 'SUCCESS',
    method: 'ONLINE',
    description: 'Badminton Court Rental',
    itemDescription: 'Badminton Court Rental',
    memberFullName: 'Le Van C',
    memberCode: 'MEM-088',
    memberId: 88,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T03:00:00Z',
    activeSession: null,
  },
  '105': {
    id: 105,
    code: 'PAY-105',
    amount: 350000,
    currency: 'VND',
    status: 'FAILED',
    paymentStatus: 'FAILED',
    method: 'ONLINE',
    description: 'Single Day Pass',
    itemDescription: 'Single Day Pass',
    memberFullName: 'Pham Van D',
    memberCode: 'MEM-099',
    memberId: 99,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T03:00:00Z',
    activeSession: null,
  },
  '106': {
    id: 106,
    code: 'PAY-106',
    amount: 810000,
    currency: 'VND',
    status: 'PENDING',
    paymentStatus: 'PENDING',
    method: 'ONLINE',
    description: 'Gold Membership (1 Month)',
    itemDescription: 'Gold Membership (1 Month)',
    memberFullName: 'Nguyen Van A',
    memberCode: 'MEM-030',
    memberId: 30,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T02:00:00Z',
    activeSession: {
      sessionId: '00000000-0000-4000-8000-000000000001',
      bankCode: 'BIDV_DEMO',
      bankLabel: 'BIDV (demo)',
      state: 'EXPIRED',
      createdAt: '2026-10-31T02:00:00Z',
      expiresAt: '2026-10-31T02:10:00Z',
      serverTime: '2026-10-31T02:15:00Z',
    },
  },
  '107': {
    id: 107,
    code: 'PAY-107',
    amount: 950000,
    currency: 'VND',
    status: 'PENDING',
    paymentStatus: 'PENDING',
    method: 'ONLINE',
    description: 'Swimming Pass (1 Month)',
    itemDescription: 'Swimming Pass (1 Month)',
    memberFullName: 'Hoang Van E',
    memberCode: 'MEM-112',
    memberId: 112,
    settlementType: null,
    settlementProvider: null,
    simulatedTransactionId: null,
    referenceCode: null,
    createdAt: '2026-10-31T03:00:00Z',
    activeSession: null,
  },
}

// 43-character base64url confirmation token matching DEMO_TOKEN regexp
const TEST_TOKEN = 'evidence-valid-member-token-00123456789012'

function startStaticServer(port) {
  return new Promise((resolve) => {
    const server = createServer((req, res) => {
      const url = new URL(req.url, `http://localhost:${port}`)
      const pathname = url.pathname

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
  const profile = mkdtempSync(join(tmpdir(), 'edge-evidence-qr07-'))
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
  const port = 4589
  const server = await startStaticServer(port)
  console.log(`[QR-07] Acceptance test server running at http://localhost:${port}`)

  const browser = await launchBrowser()
  console.log('[QR-07] Microsoft Edge launched via Chrome DevTools Protocol')

  const consoleLogs = []
  const networkRequests = []
  const accessibilityResults = []
  const axeCoreResults = []
  const layoutGeometryResults = []
  const keyboardSteps = []
  const colorContrastResults = []

  // Track dynamic state mutation for the integrated journey
  let dynamicPayment101Settled = false

  // Helper to create and setup a page in a specific browser context
  async function createPageSession(role, userProfile) {
    const { browserContextId } = await browser.send('Target.createBrowserContext')
    const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank', browserContextId })
    const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true })

    const sendPage = (method, params = {}) => browser.send(method, params, sessionId)

    browser.listeners.add(async (msg) => {
      if (msg.sessionId !== sessionId) return

      if (msg.method === 'Runtime.consoleAPICalled') {
        const args = (msg.params.args || []).map((a) => a.value ?? a.description ?? '').join(' ')
        consoleLogs.push(`[${role}] [${msg.params.type}] ${args}`)
      }

      if (msg.method === 'Fetch.requestPaused') {
        const { requestId, request } = msg.params
        try {
          const url = new URL(request.url)
          const pathname = url.pathname

          if (request.method === 'OPTIONS') {
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 204,
              responseHeaders: [
                { name: 'Access-Control-Allow-Origin', value: '*' },
                { name: 'Access-Control-Allow-Headers', value: '*' },
                { name: 'Access-Control-Allow-Methods', value: 'GET,POST,OPTIONS' },
              ],
            })
            return
          }

          // Auth verification endpoints
          if (pathname === '/api/v1/users/me' || pathname === '/api/v1/auth/me') {
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: request.method,
              status: 200,
              type: 'AUTH_ME',
              timestamp: new Date().toISOString(),
            })
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(JSON.stringify(userProfile)).toString('base64'),
            })
            return
          }

          // Capability endpoint
          if (pathname === '/api/v1/payments/simulated-qr/capability') {
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: request.method,
              status: 200,
              type: 'CAPABILITY',
              timestamp: new Date().toISOString(),
            })
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(
                JSON.stringify({ enabled: true, settlementEnabled: true, contractVersion: 1 }),
              ).toString('base64'),
            })
            return
          }

          // Member registrations and cards
          if (pathname === '/api/v1/members/me/registrations' || pathname === '/api/v1/members/me/cards') {
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: request.method,
              status: 200,
              type: 'MEMBER_ITEMS',
              timestamp: new Date().toISOString(),
            })
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(JSON.stringify([])).toString('base64'),
            })
            return
          }

          // Payment details endpoint
          if (pathname.startsWith('/api/v1/payments/')) {
            const sub = pathname.replace('/api/v1/payments/', '')
            if (/^\d+$/.test(sub)) {
              let payment = PAYMENTS[sub]
              if (sub === '101' && dynamicPayment101Settled) {
                payment = {
                  ...PAYMENTS['101'],
                  status: 'SUCCESS',
                  paymentStatus: 'SUCCESS',
                  settlementType: 'SIMULATED',
                  settlementProvider: 'SPORTIFY_SIMULATOR',
                  simulatedTransactionId: 9001,
                  referenceCode: 'TXN-9001',
                  activeSession: {
                    ...PAYMENTS['101'].activeSession,
                    state: 'SETTLED',
                  },
                }
              }
              if (payment) {
                networkRequests.push({
                  harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
                  contextRole: role,
                  url: request.url,
                  method: request.method,
                  status: 200,
                  type: 'PAYMENT_DETAIL',
                  timestamp: new Date().toISOString(),
                })
                await sendPage('Fetch.fulfillRequest', {
                  requestId,
                  responseCode: 200,
                  responseHeaders: [
                    { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                    { name: 'Access-Control-Allow-Origin', value: '*' },
                  ],
                  body: Buffer.from(JSON.stringify(payment)).toString('base64'),
                })
                return
              }
            }
          }

          // Member QR session creation: POST /api/v1/payments/simulated-qr/sessions
          if (pathname === '/api/v1/payments/simulated-qr/sessions' && request.method === 'POST') {
            const postData = request.postData ? JSON.parse(request.postData) : {}
            const bankCode = postData.bankCode || 'BIDV_DEMO'
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: 'POST',
              status: 200,
              type: 'CREATE_SESSION',
              timestamp: new Date().toISOString(),
            })
            const sessionResp = {
              session: {
                id: '00000000-0000-4000-8000-000000000001',
                paymentId: 101,
                reference: 'DEMO-101',
                amount: 810000,
                currency: 'VND',
                bankCode: 'bidv',
                bankLabel: 'BIDV (demo)',
                createdAt: '2026-10-31T03:00:00Z',
                expiresAt: '2026-10-31T03:10:00Z',
                serverTime: '2026-10-31T03:00:00Z',
                state: 'PENDING',
                mode: 'NON_PAYABLE',
              },
              confirmationUrl: `https://localhost:${port}/demo/qr/confirm#token=${TEST_TOKEN}`,
            }
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(JSON.stringify(sessionResp)).toString('base64'),
            })
            return
          }

          // Member session preview: POST /api/v1/payments/simulated-qr/sessions/preview
          if (pathname === '/api/v1/payments/simulated-qr/sessions/preview' && request.method === 'POST') {
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: 'POST',
              status: 200,
              type: 'PREVIEW_SESSION',
              timestamp: new Date().toISOString(),
            })
            const previewResp = {
              id: '00000000-0000-4000-8000-000000000001',
              paymentId: 101,
              reference: 'DEMO-101',
              amount: 810000,
              currency: 'VND',
              bankCode: 'bidv',
              bankLabel: 'BIDV (demo)',
              createdAt: '2026-10-31T03:00:00Z',
              expiresAt: '2026-10-31T03:10:00Z',
              serverTime: '2026-10-31T03:01:00Z',
              state: dynamicPayment101Settled ? 'SETTLED' : 'PENDING',
              mode: 'NON_PAYABLE',
              settlementType: dynamicPayment101Settled ? 'SIMULATED' : null,
              settlementProvider: dynamicPayment101Settled ? 'SPORTIFY_SIMULATOR' : null,
              simulatedTransactionId: dynamicPayment101Settled ? 9001 : null,
              transactionCode: dynamicPayment101Settled ? 'TXN-9001' : null,
              settledAt: dynamicPayment101Settled ? '2026-10-31T03:02:00Z' : null,
            }
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(JSON.stringify(previewResp)).toString('base64'),
            })
            return
          }

          // Member session confirm: POST /api/v1/payments/simulated-qr/sessions/confirm
          if (pathname === '/api/v1/payments/simulated-qr/sessions/confirm' && request.method === 'POST') {
            dynamicPayment101Settled = true
            networkRequests.push({
              harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
              contextRole: role,
              url: request.url,
              method: 'POST',
              status: 200,
              type: 'CONFIRM_SESSION',
              timestamp: new Date().toISOString(),
            })
            const confirmResp = {
              id: '00000000-0000-4000-8000-000000000001',
              paymentId: 101,
              reference: 'DEMO-101',
              amount: 810000,
              currency: 'VND',
              bankCode: 'bidv',
              bankLabel: 'BIDV (demo)',
              createdAt: '2026-10-31T03:00:00Z',
              expiresAt: '2026-10-31T03:10:00Z',
              serverTime: '2026-10-31T03:02:00Z',
              state: 'SETTLED',
              mode: 'NON_PAYABLE',
              settlementType: 'SIMULATED',
              settlementProvider: 'SPORTIFY_SIMULATOR',
              simulatedTransactionId: 9001,
              transactionCode: 'TXN-9001',
              settledAt: '2026-10-31T03:02:00Z',
            }
            await sendPage('Fetch.fulfillRequest', {
              requestId,
              responseCode: 200,
              responseHeaders: [
                { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                { name: 'Access-Control-Allow-Origin', value: '*' },
              ],
              body: Buffer.from(JSON.stringify(confirmResp)).toString('base64'),
            })
            return
          }

          // Receptionist desk projection
          if (pathname.startsWith('/api/v1/payments/simulated-qr/desk-projection/')) {
            const paymentId = pathname.split('/').pop()
            let projection = PAYMENTS[paymentId]

            if (paymentId === '101' && dynamicPayment101Settled) {
              projection = {
                ...PAYMENTS['101'],
                paymentStatus: 'SUCCESS',
                settlementType: 'SIMULATED',
                settlementProvider: 'SPORTIFY_SIMULATOR',
                simulatedTransactionId: 9001,
                activeSession: {
                  ...PAYMENTS['101'].activeSession,
                  state: 'SETTLED',
                },
              }
            }

            if (projection) {
              networkRequests.push({
                harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
                contextRole: role,
                url: request.url,
                method: request.method,
                status: 200,
                type: 'DESK_PROJECTION',
                timestamp: new Date().toISOString(),
              })
              await sendPage('Fetch.fulfillRequest', {
                requestId,
                responseCode: 200,
                responseHeaders: [
                  { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                  { name: 'Access-Control-Allow-Origin', value: '*' },
                ],
                body: Buffer.from(JSON.stringify(projection)).toString('base64'),
              })
            } else {
              await sendPage('Fetch.fulfillRequest', {
                requestId,
                responseCode: 404,
                responseHeaders: [
                  { name: 'Content-Type', value: 'application/json; charset=utf-8' },
                  { name: 'Access-Control-Allow-Origin', value: '*' },
                ],
                body: Buffer.from(JSON.stringify({ code: 'PAYMENT_NOT_FOUND', message: 'Payment not found' })).toString(
                  'base64',
                ),
              })
            }
            return
          }

          // Pass through static frontend bundle assets
          await sendPage('Fetch.continueRequest', { requestId })
        } catch {
          // Ignore aborted requests
        }
      }
    })

    await sendPage('Fetch.enable', { patterns: [{ urlPattern: '*' }] })
    await sendPage('Page.enable')
    await sendPage('Runtime.enable')

    // Seed authentic tokenStorage in localStorage
    await sendPage('Page.navigate', { url: `http://localhost:${port}/` })
    await new Promise((r) => setTimeout(r, 600))
    await sendPage('Runtime.evaluate', {
      expression: `
        localStorage.setItem('sportify.accessToken', 'evidence-mock-access-token-qr07');
        localStorage.setItem('sportify.refreshToken', 'evidence-mock-refresh-token-qr07');
        localStorage.setItem('sportify.sessionId', 'evidence-mock-session-id-qr07');
      `,
    })

    return { sessionId, sendPage }
  }

  // Define User Profiles
  const RECEPTIONIST_USER = {
    id: 10,
    email: 'receptionist@sportify.demo',
    fullName: 'Linh Receptionist',
    role: 'RECEPTIONIST',
    status: 'ACTIVE',
  }

  const MEMBER_USER = {
    id: 2,
    email: 'member@sportify.demo',
    fullName: 'Taylor Nguyen',
    role: 'MEMBER',
    status: 'ACTIVE',
  }

  // Create Context 1 (Member Desktop & Receptionist Desktop)
  console.log('[QR-07] Creating Browser Context 1 (Primary Desktop Journey)...')
  const ctx1 = await createPageSession('DESKTOP_PRIMARY', RECEPTIONIST_USER)

  // Create Context 2 (Isolated Member Confirmation Context for Mobile Scan simulation)
  console.log('[QR-07] Creating Browser Context 2 (Isolated Member Confirmation Context)...')
  const ctx2 = await createPageSession('MOBILE_CONFIRM_CONTEXT2', MEMBER_USER)

  // Capture helper function
  async function captureView(ctx, url, filename, { width, height, theme = 'light', zoom = 1, readySelector, assertNoLogin = true }) {
    await ctx.sendPage('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: width < 600,
    })

    await ctx.sendPage('Page.navigate', { url })

    // Wait until target selector mounts
    let ready = false
    for (let i = 0; i < 60; i++) {
      const check = await ctx.sendPage('Runtime.evaluate', {
        expression: `({
          isReady: !!document.querySelector('${readySelector}'),
          isLogin: document.querySelector('h1')?.textContent?.includes('Welcome back') || document.title.includes('Log in')
        })`,
        returnByValue: true,
      })
      if (check.result?.value?.isReady) {
        ready = true
        break
      }
      if (assertNoLogin && check.result?.value?.isLogin) {
        throw new Error(`CRITICAL VERIFICATION ERROR: View redirected to login page for ${filename}!`)
      }
      await new Promise((resolve) => setTimeout(resolve, 100))
    }

    if (!ready) {
      throw new Error(`Timeout waiting for selector "${readySelector}" to mount on view ${filename}`)
    }

    // Apply theme and optional zoom
    await ctx.sendPage('Runtime.evaluate', {
      expression: `
        document.documentElement.setAttribute('data-theme', '${theme}');
        ${zoom !== 1 ? `document.body.style.zoom = '${zoom}';` : ''}
      `,
    })

    // Compute bounding geometry
    const geomRes = await ctx.sendPage('Runtime.evaluate', {
      expression: `(() => {
        function getBox(el) {
          if (!el) return null;
          const r = el.getBoundingClientRect();
          return { top: Math.round(r.top), left: Math.round(r.left), width: Math.round(r.width), height: Math.round(r.height) };
        }
        return {
          hero: getBox(document.querySelector('.payment-hero') || document.querySelector('.home-header')),
          heroTitle: getBox(document.querySelector('.payment-hero__title') || document.querySelector('h1')),
          qrCard: getBox(document.querySelector('.qr-demo-card') || document.querySelector('.reception-grid')),
          statusCard: getBox(document.querySelector('.reception-status-header') || document.querySelector('.card')),
          viewport: { width: window.innerWidth, height: window.innerHeight },
          scrollWidth: document.documentElement.scrollWidth,
        };
      })()`,
      returnByValue: true,
    })
    const geom = geomRes.result.value
    layoutGeometryResults.push({ view: filename, viewport: `${width}x${height}`, theme, geometry: geom })

    // Inject and execute axe-core audit
    let axeData = null
    if (AXE_SCRIPT) {
      await ctx.sendPage('Runtime.evaluate', { expression: AXE_SCRIPT })
      const axeRun = await ctx.sendPage('Runtime.evaluate', {
        expression: `
          (async () => {
            const res = await window.axe.run(document, {
              runOnly: {
                type: 'tag',
                values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']
              }
            });
            return {
              violationsCount: res.violations.length,
              violations: res.violations.map(v => ({
                id: v.id,
                impact: v.impact,
                description: v.description,
                help: v.help,
                nodesCount: v.nodes.length,
              })),
              passesCount: res.passes.length,
              incompleteCount: res.incomplete.length,
              incomplete: res.incomplete.map(inc => ({
                id: inc.id,
                impact: inc.impact,
                description: inc.description,
                help: inc.help,
                nodesCount: inc.nodes.length,
              })),
              inapplicableCount: res.inapplicable.length,
              timestamp: new Date().toISOString()
            };
          })()
        `,
        awaitPromise: true,
        returnByValue: true,
      })
      axeData = axeRun.result?.value
    }

    const domAudit = await ctx.sendPage('Runtime.evaluate', {
      expression: `({
        title: document.title,
        heading: document.querySelector('h1')?.textContent?.trim(),
        isLoginPage: document.querySelector('h1')?.textContent?.includes('Welcome back') || document.title.includes('Log in'),
        noHorizontalOverflow: document.documentElement.scrollWidth <= window.innerWidth,
      })`,
      returnByValue: true,
    })

    accessibilityResults.push({
      harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
      view: filename,
      viewport: `${width}x${height}`,
      theme,
      zoom: `${zoom * 100}%`,
      audit: domAudit.result.value,
      axeSummary: axeData
        ? {
            violationsCount: axeData.violationsCount,
            passesCount: axeData.passesCount,
            incompleteCount: axeData.incompleteCount,
            inapplicableCount: axeData.inapplicableCount,
          }
        : 'axe-core not executed',
    })

    if (axeData) {
      axeCoreResults.push({
        harness: 'MOCK BROWSER UI (Deterministic Fixtures)',
        view: filename,
        viewport: `${width}x${height}`,
        theme,
        axe: axeData,
      })
    }

    const screenshotData = await ctx.sendPage('Page.captureScreenshot', { format: 'png' })
    const buffer = Buffer.from(screenshotData.data, 'base64')
    const filePath = join(SCREENSHOTS_DIR, filename)
    writeFileSync(filePath, buffer)
    console.log(`[QR-07] Saved: ${filename} (${width}x${height}, ${theme}, noOverflow=${domAudit.result.value.noHorizontalOverflow}, axeViolations=${axeData?.violationsCount ?? 0})`)
  }

  // ==========================================
  // STEP 1: Member Desktop Journey (Context 1 switched to Member)
  // ==========================================
  console.log('\n--- EXECUTING MEMBER JOURNEY ---')
  const ctxMember = await createPageSession('MEMBER_JOURNEY', MEMBER_USER)

  // 1.1 Member Payment Summary (1280x800 Light & Dark)
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101`,
    'member-1280-light-payment-summary.png',
    { width: 1280, height: 800, theme: 'light', readySelector: '.payment-hero' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101`,
    'member-1280-dark-payment-summary.png',
    { width: 1280, height: 800, theme: 'dark', readySelector: '.payment-hero' },
  )

  // 1.2 Member Simulated QR Demo Page (1280, 1440, 1920, 390 reflow)
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1280-light-qr-demo.png',
    { width: 1280, height: 800, theme: 'light', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1280-dark-qr-demo.png',
    { width: 1280, height: 800, theme: 'dark', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1440-light-qr-demo.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1440-dark-qr-demo.png',
    { width: 1440, height: 900, theme: 'dark', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1920-light-qr-demo.png',
    { width: 1920, height: 1080, theme: 'light', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-390-light-qr-demo.png',
    { width: 390, height: 844, theme: 'light', readySelector: '.qr-demo-card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-390-dark-qr-demo.png',
    { width: 390, height: 844, theme: 'dark', readySelector: '.qr-demo-card' },
  )

  // ==========================================
  // STEP 2: Second Authenticated Browser Context (Member Confirmation Link)
  // ==========================================
  console.log('\n--- EXECUTING CONTEXT 2 (MEMBER CONFIRMATION LINK) ---')
  const confirmUrl = `http://localhost:${port}/demo/qr/confirm#token=${TEST_TOKEN}`

  // 2.1 Preview session in Context 2 (1280, 1440, 390)
  await captureView(
    ctx2,
    confirmUrl,
    'confirm-context2-1280-light-preview.png',
    { width: 1280, height: 800, theme: 'light', readySelector: '.demo-confirm-page' },
  )
  await captureView(
    ctx2,
    confirmUrl,
    'confirm-context2-1280-dark-preview.png',
    { width: 1280, height: 800, theme: 'dark', readySelector: '.demo-confirm-page' },
  )
  await captureView(
    ctx2,
    confirmUrl,
    'confirm-context2-1440-light-preview.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.demo-confirm-page' },
  )
  await captureView(
    ctx2,
    confirmUrl,
    'confirm-context2-390-light-preview.png',
    { width: 390, height: 844, theme: 'light', readySelector: '.demo-confirm-page' },
  )

  // 2.2 Trigger explicit Member confirmation button in Context 2
  console.log('[QR-07] Context 2 clicking "Confirm Simulated Payment"...')
  await ctx2.sendPage('Runtime.evaluate', {
    expression: `(() => {
      const btn = [...document.querySelectorAll('button')].find(b => b.textContent?.includes('Confirm Simulated Payment'));
      if (btn) btn.click();
    })()`,
  })
  await new Promise((r) => setTimeout(r, 800))

  // Capture confirmed state in Context 2
  await captureView(
    ctx2,
    `http://localhost:${port}/demo/qr/confirm`,
    'confirm-context2-1440-light-confirmed.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.demo-confirm-page' },
  )

  // 2.3 Member Demo QR view reloaded after settlement (displays Paid - Simulated)
  console.log('[QR-07] Verifying Member view after settlement commit...')
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1440-light-settled-simulated.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.card' },
  )
  await captureView(
    ctxMember,
    `http://localhost:${port}/member/payments/101/qr-demo`,
    'member-1440-dark-settled-simulated.png',
    { width: 1440, height: 900, theme: 'dark', readySelector: '.card' },
  )

  // ==========================================
  // STEP 3: Receptionist Desk Projection (Context 1)
  // ==========================================
  console.log('\n--- EXECUTING RECEPTIONIST DESK PROJECTION ---')

  // Reset dynamic state to test pending view first
  dynamicPayment101Settled = false

  // 3.1 Receptionist PENDING state (1280, 1440, 1920, 768 reflow)
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1280-light-pending.png',
    { width: 1280, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1280-dark-pending.png',
    { width: 1280, height: 900, theme: 'dark', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1440-light-pending.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1440-dark-pending.png',
    { width: 1440, height: 900, theme: 'dark', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1920-light-pending.png',
    { width: 1920, height: 1080, theme: 'light', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-768-light-pending.png',
    { width: 768, height: 1024, theme: 'light', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-768-dark-pending.png',
    { width: 768, height: 1024, theme: 'dark', readySelector: '.reception-grid' },
  )

  // 3.2 Receptionist Settled SIMULATED (Scenario 102)
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/102/qr-display`,
    'reception-1440-light-settled-simulated.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/102/qr-display`,
    'reception-1440-dark-settled-simulated.png',
    { width: 1440, height: 900, theme: 'dark', readySelector: '.reception-grid' },
  )

  // 3.3 Receptionist Settled ACTUAL (Scenario 103: Front desk cash, banner omitted)
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/103/qr-display`,
    'reception-1440-light-settled-actual.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )

  // 3.4 Receptionist Settled LEGACY UNVERIFIED (Scenario 104)
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/104/qr-display`,
    'reception-1440-light-legacy-unverified.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )

  // 3.5 Receptionist Terminal FAILED (Scenario 105)
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/105/qr-display`,
    'reception-1440-light-failed-terminal.png',
    { width: 1440, height: 900, theme: 'light', readySelector: '.reception-grid' },
  )

  // 3.6 200% Zoom Check on Receptionist Desk
  await captureView(
    ctx1,
    `http://localhost:${port}/reception/payments/101/qr-display`,
    'reception-1440-light-200percent-zoom.png',
    { width: 720, height: 450, theme: 'light', zoom: 1, readySelector: '.reception-grid' },
  )

  // ==========================================
  // STEP 4: Real Keyboard Navigation Verification
  // ==========================================
  console.log('\n--- EXECUTING REAL KEYBOARD NAVIGATION (CDP TAB KEYSTROKES) ---')
  await ctx1.sendPage('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  await ctx1.sendPage('Page.navigate', { url: `http://localhost:${port}/reception/payments/101/qr-display` })
  await new Promise((r) => setTimeout(r, 800))

  for (let tabIndex = 1; tabIndex <= 20; tabIndex++) {
    await ctx1.sendPage('Input.dispatchKeyEvent', { type: 'rawKeyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 })
    await ctx1.sendPage('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 })
    await new Promise((r) => setTimeout(r, 100))

    const activeEl = await ctx1.sendPage('Runtime.evaluate', {
      expression: `(() => {
        const el = document.activeElement;
        if (!el || el === document.body) return null;
        const rect = el.getBoundingClientRect();
        const style = window.getComputedStyle(el);
        const isReceptionAction = !!el.closest('.reception-actions');
        return {
          tabIndex: ${tabIndex},
          tagName: el.tagName,
          className: el.className,
          id: el.id || null,
          text: el.textContent?.trim()?.slice(0, 60),
          href: el.getAttribute('href') || null,
          rect: { top: Math.round(rect.top), left: Math.round(rect.left), width: Math.round(rect.width), height: Math.round(rect.height) },
          isReceptionAction,
        };
      })()`,
      returnByValue: true,
    })

    const elData = activeEl.result?.value
    if (elData) {
      keyboardSteps.push(elData)
      if (elData.isReceptionAction) {
        console.log(`[QR-07] Keyboard Tab reached reception action at Tab #${tabIndex}: <${elData.tagName}> "${elData.text}"`)
        const focusScreenshot = await ctx1.sendPage('Page.captureScreenshot', { format: 'png' })
        writeFileSync(join(SCREENSHOTS_DIR, 'reception-1440-light-keyboard-focus.png'), Buffer.from(focusScreenshot.data, 'base64'))
        break
      }
    }
  }

  // ==========================================
  // STEP 5: Write Artifacts
  // ==========================================
  console.log('\n--- WRITING BROWSER LOGS AND AUDIT ARTIFACTS ---')

  // Console log
  writeFileSync(join(LOGS_DIR, 'browser-console.log'), consoleLogs.join('\n'))

  // Network requests
  writeFileSync(join(LOGS_DIR, 'network-requests.json'), JSON.stringify(networkRequests, null, 2))

  // Layout geometry
  writeFileSync(join(LOGS_DIR, 'layout-geometry.json'), JSON.stringify(layoutGeometryResults, null, 2))

  // Keyboard navigation
  writeFileSync(join(LOGS_DIR, 'keyboard-navigation.json'), JSON.stringify(keyboardSteps, null, 2))

  // Accessibility audit
  writeFileSync(
    join(A11Y_DIR, 'axe-core-audit.json'),
    JSON.stringify(
      {
        timestamp: new Date().toISOString(),
        totalViewsAudited: axeCoreResults.length,
        wcagStandardsChecked: ['WCAG 2.0 Level A', 'WCAG 2.0 Level AA', 'WCAG 2.1 Level A', 'WCAG 2.1 Level AA'],
        results: axeCoreResults,
      },
      null,
      2,
    ),
  )

  console.log(`[QR-07] Browser acceptance run completed successfully!`)
  console.log(`Total screenshots saved: ${layoutGeometryResults.length}`)
  console.log(`Total network requests recorded: ${networkRequests.length}`)
  console.log(`Total keyboard steps verified: ${keyboardSteps.length}`)

  // Teardown
  browser.close()
  server.close()
}

run().catch((err) => {
  console.error('[QR-07] FATAL ERROR in browser acceptance harness:', err)
  process.exit(1)
})
