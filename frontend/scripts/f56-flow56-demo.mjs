// Flow 5-6 committee demo fixture and rehearsal (F56-06).
//
// Creates a NEW uniquely named SQL Server database (sportify_f56_demo_<timestamp>), boots the
// packaged backend with the repository demo seed (profiles dev,demo), books one coached badminton
// session for the demo Member through the real booking API and verifies RULE_BASED drafts and
// KNOWLEDGE_BASED answers. It never reuses, drops or cleans a database and refuses protected names.
//
//   node frontend/scripts/f56-flow56-demo.mjs prepare            # fixture + API prechecks, then stop
//   node frontend/scripts/f56-flow56-demo.mjs serve              # fixture + keep backend and frontend running
//   node frontend/scripts/f56-flow56-demo.mjs rehearse [out]     # fixture + timed browser rehearsal + restart
//
// Required environment: F56_SQL_USER, F56_SQL_PASSWORD (local SQL login), JWT_SECRET (Base64, 32+ bytes).
// Prerequisites: Java 25, sqlcmd, Node 22+, Microsoft Edge (rehearse only), `mvn -B -ntp package -DskipTests`
// in backend/ and `npm ci` in frontend/. No Docker, Python, model provider or API key is used.
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, openSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const API_PORT = 18080, WEB_PORT = 15173
const API = `http://127.0.0.1:${API_PORT}/api/v1`, WEB = `http://127.0.0.1:${WEB_PORT}`
const PASSWORD = 'SportifyDemo2026!' // Public password of the repository demo seed (backend db/demo V28).
const COACH = 'quan.coach@example.com', MEMBER = 'mai.standard@example.com', PEER = 'an.gold@example.com'
const CLASS_CODE = 'DEMO-CL-BAD-BEG'
const QUESTIONS = { sessions: 'Which classes are available in the next two weeks?', packages: 'What are the package prices?',
  booking: 'How do I book a session?', faq: 'How should I warm up before practice?', refusal: 'I have knee pain after practice, what should I do?' }
const sleep = ms => new Promise(done => setTimeout(done, ms))
const [mode = 'prepare', outArg] = process.argv.slice(2)
if (!['prepare', 'serve', 'rehearse'].includes(mode)) throw new Error('Mode must be prepare, serve or rehearse')
for (const name of ['F56_SQL_USER', 'F56_SQL_PASSWORD', 'JWT_SECRET']) if (!process.env[name]) throw new Error(name + ' is required')
const stamp = new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 19).replace(/[-:T]/g, '')
const DB = `sportify_f56_demo_${stamp}`
// Logs, screenshots and the manifest stay outside the repository by default.
const OUT = resolve(outArg ?? join(tmpdir(), 'f56-demo', DB))
mkdirSync(OUT, { recursive: true })

function guard(db) {
  if (['sportify', 'sportify_demo', 'sportify_qr_demo'].includes(db.toLowerCase()) || !/^sportify_f56_demo_[0-9]{14}$/.test(db)) throw new Error('Refusing database ' + db)
}
function sql(query, db = DB) {
  if (db !== 'master') guard(db)
  const run = spawnSync('sqlcmd', ['-S', '127.0.0.1,1433', '-U', process.env.F56_SQL_USER, '-C', '-I', '-d', db, '-b', '-h', '-1', '-y', '8000', '-Q', 'SET NOCOUNT ON; ' + query],
    { encoding: 'utf8', env: { ...process.env, SQLCMDPASSWORD: process.env.F56_SQL_PASSWORD } })
  if (run.status !== 0) throw new Error('SQL failed: ' + (run.stdout + run.stderr).slice(0, 600))
  return run.stdout.trim()
}
const one = query => JSON.parse(sql(query + ' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER').replace(/\r?\n/g, '') || 'null')
async function api(path, { body, token, method } = {}) {
  const response = await fetch(API + path, { method: method ?? (body === undefined ? 'GET' : 'POST'),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await response.text()
  return { status: response.status, body: text ? JSON.parse(text) : null }
}
async function login(email) {
  const result = await api('/auth/login', { body: { email, password: PASSWORD } })
  if (result.status !== 200) throw new Error('Login failed for ' + email)
  return result.body.accessToken
}
function stop(child) { if (child && child.exitCode === null) spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F']) }
async function startBackend(extra = []) {
  const jar = join(ROOT, 'backend/target/G3-SportCentreManagement-0.0.1-SNAPSHOT.jar')
  if (!existsSync(jar)) throw new Error('Package the backend first: ' + jar)
  const log = openSync(join(OUT, `backend-${Date.now()}.log`), 'w')
  const child = spawn('java', ['-Xmx768m', '-jar', jar, '--spring.profiles.active=dev,demo', `--server.port=${API_PORT}`, '--server.address=127.0.0.1', ...extra], {
    stdio: ['ignore', log, log], env: { ...process.env, DB_URL: `jdbc:sqlserver://127.0.0.1:1433;databaseName=${DB};encrypt=false;trustServerCertificate=true`,
      DB_USERNAME: process.env.F56_SQL_USER, DB_PASSWORD: process.env.F56_SQL_PASSWORD, CORS_ALLOWED_ORIGINS: WEB,
      SPORTIFY_PAYMENT_ACCOUNT_NAME: 'Demo center', SPORTIFY_PAYMENT_ACCOUNT_NUMBER: 'NOT_AVAILABLE', SPORTIFY_PAYMENT_BANK: 'Demo only' } })
  for (let i = 0; i < 240; i++) {
    if (child.exitCode !== null) throw new Error('Backend exited; see ' + OUT)
    try { if ((await api('/auth/login', { body: { email: 'invalid@example.test', password: 'invalid' } })).status === 401) return child } catch { /* starting */ }
    await sleep(1000)
  }
  throw new Error('Backend not ready')
}
async function startFrontend() {
  const frontend = join(ROOT, 'frontend'), dist = join(OUT, 'dist')
  const build = spawnSync(process.execPath, [join(frontend, 'node_modules/vite/bin/vite.js'), 'build', '--outDir', dist, '--emptyOutDir'],
    { cwd: frontend, encoding: 'utf8', env: { ...process.env, VITE_API_BASE_URL: API } })
  if (build.status !== 0) throw new Error('Frontend build failed: ' + build.stderr.slice(0, 400))
  const log = openSync(join(OUT, 'frontend.log'), 'w')
  const child = spawn(process.execPath, [join(frontend, 'node_modules/vite/bin/vite.js'), 'preview', '--host', '127.0.0.1', '--port', String(WEB_PORT), '--strictPort', '--outDir', dist], { cwd: frontend, stdio: ['ignore', log, log] })
  for (let i = 0; i < 60; i++) { try { if ((await fetch(WEB + '/login')).status === 200) return child } catch { /* starting */ } await sleep(500) }
  throw new Error('Frontend not ready')
}
const snapshot = () => one(`SELECT (SELECT COUNT(*) FROM booking) bookings,(SELECT SUM(booked_count) FROM class_session) seats,
  (SELECT SUM(remaining_sessions) FROM sport_package_registration) credits,(SELECT COUNT(*) FROM payment) payments,(SELECT COUNT(*) FROM invoice) invoices`)

const checks = []
function check(id, name, ok, evidence) { checks.push({ id, name, result: ok ? 'PASS' : 'FAIL', evidence }); console.log((ok ? 'PASS ' : 'FAIL ') + id + ' ' + name); if (!ok) throw new Error('Precheck failed: ' + name) }

async function prepare() {
  sql(`CREATE DATABASE [${DB}] COLLATE Latin1_General_CI_AS; ALTER DATABASE [${DB}] SET AUTO_CLOSE OFF;`, 'master')
  const backend = await startBackend()
  check('T06-01', 'Fresh owned database migrated by Flyway without failures', sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0') === '0')
  const accounts = one(`SELECT (SELECT COUNT(*) FROM user_account u JOIN role r ON r.id=u.role_id WHERE u.email='${COACH}' AND r.code='COACH' AND u.status='ACTIVE') coach,
    (SELECT COUNT(*) FROM user_account u JOIN role r ON r.id=u.role_id WHERE u.email IN ('${MEMBER}','${PEER}') AND r.code='MEMBER' AND u.status='ACTIVE') members`)
  check('T06-02', 'Demo Coach and two Members are ACTIVE with the right roles', accounts.coach === 1 && accounts.members === 2, accounts)
  const coach = await login(COACH), member = await login(MEMBER)
  const session = one(`SELECT TOP(1) s.id sessionId,c.id classId,c.name className,CONVERT(varchar(10),s.session_date,23) date,CONVERT(varchar(5),s.start_time,108) startTime
    FROM class_session s JOIN sport_class c ON c.id=s.class_id WHERE c.code='${CLASS_CODE}' AND s.training_type='COACH_LED' AND s.status='PUBLISHED' AND s.booked_count<s.capacity
    AND (s.session_date>CAST(DATEADD(hour,7,SYSUTCDATETIME()) AS date) OR (s.session_date=CAST(DATEADD(hour,7,SYSUTCDATETIME()) AS date) AND s.start_time>CAST(DATEADD(hour,7,SYSUTCDATETIME()) AS time)))
    ORDER BY s.session_date,s.start_time`)
  const booked = await api('/bookings', { body: { sessionId: session.sessionId }, token: member })
  check('T06-03', 'Demo Member books the coached badminton session through the booking API', booked.status === 201, { status: booked.status })
  const before = snapshot()
  const drafts = await api(`/coach/training/bookings/${booked.body.id}/drill-suggestions`, { token: coach })
  check('T06-03', 'Assigned Coach receives at least two RULE_BASED drafts and nothing is saved', drafts.status === 200 && drafts.body.mode === 'RULE_BASED' && drafts.body.persisted === false
    && drafts.body.drafts.length >= 2 && sql(`SELECT COUNT(*) FROM session_plan WHERE booking_id=${booked.body.id}`) === '0', { drafts: drafts.body?.drafts?.map(d => d.title) })
  const answers = {}
  for (const [topic, question] of Object.entries(QUESTIONS)) answers[topic] = (await api('/member/training/consultation', { body: { question, optIn: true }, token: member })).body
  check('T06-05', 'Consultation answers sessions, packages, booking and FAQ from live rows or the curated FAQ',
    ['sessions', 'packages', 'booking', 'faq'].every(topic => answers[topic].mode === 'KNOWLEDGE_BASED' && answers[topic].status === 'ANSWERED'),
    Object.fromEntries(Object.entries(answers).map(([topic, a]) => [topic, `${a.status} ${a.sources.map(s => s.path).join(' ')}`])))
  check('T06-06', 'Medical question receives NO_ANSWER with the human coach source', answers.refusal.status === 'NO_ANSWER' && answers.refusal.sources[0]?.kind === 'HUMAN_COACH')
  check('T06-03', 'Prechecks changed no booking, seat, credit, payment or invoice', JSON.stringify(snapshot()) === JSON.stringify(before))
  const manifest = { database: DB, createdAt: new Date().toISOString(), frontend: WEB, backend: API, password: 'public demo seed password (backend db/demo V28)',
    coach: COACH, member: MEMBER, peer: PEER, className: session.className, sessionId: session.sessionId, sessionDate: session.date, startTime: session.startTime,
    bookingId: booked.body.id, draftTitles: drafts.body.drafts.map(d => d.title), questions: QUESTIONS, checks }
  writeFileSync(join(OUT, 'fixture-manifest.json'), JSON.stringify(manifest, null, 2))
  console.log('Manifest: ' + join(OUT, 'fixture-manifest.json'))
  return { backend, manifest }
}

// Minimal Chrome DevTools Protocol driver for the rehearsal (no browser automation dependency).
async function browser(width, height) {
  const profile = mkdtempSync(join(tmpdir(), 'f56-demo-edge-'))
  const edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`, '--no-first-run', '--disable-gpu', 'about:blank'], { stdio: 'ignore' })
  let port
  for (let i = 0; i < 100 && !port; i++) { const f = join(profile, 'DevToolsActivePort'); if (existsSync(f)) port = readFileSync(f, 'utf8').split('\n')[0].trim(); else await sleep(100) }
  let target
  for (let i = 0; i < 50 && !target; i++) { target = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(t => t.type === 'page'); if (!target) await sleep(100) }
  const socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((done, fail) => { socket.onopen = done; socket.onerror = fail })
  let id = 0
  const pending = new Map()
  socket.onmessage = event => { const m = JSON.parse(event.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
  const send = (method, params = {}) => new Promise((done, fail) => { const n = ++id; const timer = setTimeout(() => fail(new Error('CDP timeout ' + method)), 30000)
    pending.set(n, m => { clearTimeout(timer); if (m.error) fail(new Error(m.error.message)); else done(m.result) }); socket.send(JSON.stringify({ id: n, method, params })) })
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
  const helpers = `const norm=t=>(t||'').replace(/\\s+/g,' ').replace(/\\s*\\*$/,'').trim();
    const control=l=>{for(const x of document.querySelectorAll('label')){if(norm(x.innerText)===l){const c=x.control||x.querySelector('input,select,textarea');if(c)return c}}throw new Error('No control '+l)};
    const fill=(l,v)=>{const c=control(l);if(c.type==='checkbox'){if(c.checked!==v)c.click();return true}
      const p=c.tagName==='SELECT'?HTMLSelectElement.prototype:c.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
      if(c.tagName==='SELECT'){const o=[...c.options].find(o=>o.text.includes(v));if(!o)throw new Error('No option '+v);v=o.value}
      Object.getOwnPropertyDescriptor(p,'value').set.call(c,v);c.dispatchEvent(new Event('input',{bubbles:true}));c.dispatchEvent(new Event('change',{bubbles:true}));return true};
    const click=n=>{const b=[...document.querySelectorAll('button,a')].find(e=>norm(e.innerText)===n);if(!b||b.disabled)throw new Error('Cannot click '+n);b.click();return true};`
  // Page code is always a constant from this file; every data value is passed as a CDP call argument.
  const evaluate = async (expression, ...args) => {
    const { result: global } = await send('Runtime.evaluate', { expression: 'globalThis' })
    const r = await send('Runtime.callFunctionOn', { objectId: global.objectId, functionDeclaration: `async function(...args){${helpers};return (${expression})}`,
      arguments: args.map(value => ({ value })), awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text)
    return r.result.value
  }
  const until = async (expression, ...args) => { const end = Date.now() + 20000; while (Date.now() < end) { try { if (await evaluate(expression, ...args)) return } catch { /* not yet */ } await sleep(150) } throw new Error('Timed out: ' + expression) }
  return { evaluate, until, async go(url) { await send('Page.navigate', { url }); await until('document.readyState==="complete"') },
    async shot(name) { const { data } = await send('Page.captureScreenshot', { format: 'png' }); writeFileSync(join(OUT, name), Buffer.from(data, 'base64')) },
    async login(email) { await this.go(WEB + '/login'); await until(`!!document.querySelector('input[type=password]')`); await evaluate(`fill('Email address',args[0])&&fill('Password',args[1])&&(document.querySelector('form button[type=submit]').click(),true)`, email, PASSWORD); await until(`!location.pathname.startsWith('/login')`) },
    close() { try { socket.close() } catch { /* closed */ } spawnSync('taskkill', ['/PID', String(edge.pid), '/T', '/F']) } }
}

async function rehearse(manifest, backend, width, height, pace) {
  // pace = seconds of presenter narration budgeted after each scripted step (matches DEMO_SCRIPT timings).
  const started = Date.now(), marks = []
  const mark = label => marks.push({ label, seconds: Math.round((Date.now() - started) / 1000) })
  const say = seconds => sleep(seconds * pace * 1000)
  const coach = await browser(width, height)
  await coach.login(COACH); await coach.go(WEB + '/coach/training/plans'); mark('Coach plans page'); await say(15)
  await coach.until(`!!document.querySelector('select')`)
  await coach.evaluate(`fill('Assigned class',args[0])`, manifest.className)
  await coach.until(`[...control('Member booking').options].length>1`)
  await coach.evaluate(`fill('Member booking',args[0])`, `Booking #${manifest.bookingId}`); await say(10)
  await coach.evaluate(`click('Suggest practice drills')`); await coach.until(`document.body.innerText.includes('Drafts are not saved.')`); mark('Drafts shown'); await say(25)
  await coach.shot(`rehearsal-${width}-1-drafts.png`)
  const title = `Committee badminton plan ${stamp}-${width}`
  await coach.evaluate(`fill('Select draft: '+args[0],true)&&fill('Select draft: '+args[1],true)&&click('Review selected drafts in plan form')`, manifest.draftTitles[0], manifest.draftTitles[1])
  await coach.evaluate(`fill('Plan title',args[0])&&fill('Exercise duration 1 (minutes)','6')`, title); mark('Drafts reviewed and edited'); await say(15)
  const plansBefore = sql(`SELECT COUNT(*) FROM session_plan WHERE booking_id=${manifest.bookingId}`)
  await coach.evaluate(`click('Assign plan')`); await coach.until(`document.body.innerText.includes('Plan assigned.')`); mark('Plan assigned'); await say(5)
  await coach.shot(`rehearsal-${width}-2-assigned.png`); coach.close()
  const plan = one(`SELECT id,(SELECT COUNT(*) FROM session_plan_step WHERE plan_id=p.id) steps FROM session_plan p WHERE booking_id=${manifest.bookingId} AND title='${title}'`)
  check('T06-04', `Coach assignment persisted one plan with two steps (${width}x${height})`, Number(plansBefore) + 1 === Number(sql(`SELECT COUNT(*) FROM session_plan WHERE booking_id=${manifest.bookingId}`)) && plan.steps === 2, plan)
  const member = await browser(width, height)
  await member.login(MEMBER); await member.go(WEB + `/member/training/plans/${plan.id}`); await member.until(`document.body.innerText.includes(args[0])`, title)
  mark('Member reads plan'); await say(15); await member.shot(`rehearsal-${width}-3-member-plan.png`)
  await member.go(WEB + '/member/assistant'); await member.until(`document.body.innerText.includes('Enable automated center consultation')`); mark('Assistant opened'); await say(15)
  await member.evaluate(`fill('Enable automated center consultation',true)`)
  for (const [topic, wait] of [['sessions', 15], ['packages', 10], ['faq', 15], ['refusal', 10]]) {
    const question = QUESTIONS[topic]
    await member.until(`!control('Center question').disabled`)
    await member.evaluate(`fill('Center question',args[0])`, question)
    await member.evaluate(`click('Ask center assistant')`)
    await member.until(`![...document.querySelectorAll('button')].some(b=>b.getAttribute('aria-busy')==='true') && document.body.innerText.includes(args[0])`, topic === 'refusal' ? 'does not answer personal, medical or private questions' : topic === 'faq' ? 'General exercise FAQ' : topic === 'packages' ? 'Current active package base prices' : 'Published sessions with available places')
    mark('Answer: ' + topic); await say(wait); await member.shot(`rehearsal-${width}-4-${topic}.png`)
  }
  member.close()
  const seconds = Math.round((Date.now() - started) / 1000)
  // Restart readback: the plan survives a backend restart, chat state does not exist server side.
  stop(backend); await sleep(2000)
  const restarted = await startBackend()
  const reread = await api(`/member/training/plans/${plan.id}`, { token: await login(MEMBER) })
  check('T06-04', `Member re-reads the same plan after backend restart (${width}x${height})`, reread.status === 200 && reread.body.plan.title === title)
  return { seconds, marks, backend: restarted }
}

let backend, frontend
try {
  const prepared = await prepare()
  backend = prepared.backend
  if (mode === 'serve') {
    frontend = await startFrontend()
    console.log(`Demo ready: ${WEB} (backend ${API}, database ${DB}). Press Ctrl+C to stop both processes.`)
    await new Promise(() => {})
  }
  if (mode === 'rehearse') {
    frontend = await startFrontend()
    const timings = {}
    for (const [width, height, pace] of [[1280, 720, 1], [1440, 900, 0]]) {
      const run = await rehearse(prepared.manifest, backend, width, height, pace)
      backend = run.backend
      timings[`${width}x${height}`] = { paced: pace === 1, seconds: run.seconds, marks: run.marks }
    }
    check('T06-07', 'Paced 1280x720 rehearsal including narration budget fits four minutes', timings['1280x720'].seconds <= 240, timings['1280x720'])
    // Fallback: with no published session in the window, the assistant says so truthfully and packages still answer.
    sql(`UPDATE class_session SET status='CANCELLED' WHERE status='PUBLISHED'`)
    const member = await login(MEMBER)
    const none = (await api('/member/training/consultation', { body: { question: QUESTIONS.sessions, optIn: true }, token: member })).body
    const prices = (await api('/member/training/consultation', { body: { question: QUESTIONS.packages, optIn: true }, token: member })).body
    check('T06-08', 'Without published sessions the assistant returns a truthful NO_ANSWER and packages still answer', none.status === 'NO_ANSWER' && /No matching published sessions/.test(none.answer) && prices.status === 'ANSWERED', { none: none.answer })
    writeFileSync(join(OUT, 'rehearsal.json'), JSON.stringify({ database: DB, timings, checks }, null, 2))
  }
  console.log('RESULT PASS ' + DB)
} catch (error) {
  console.error('RESULT FAIL ' + DB + ': ' + (error.stack || error))
  process.exitCode = 1
} finally {
  writeFileSync(join(OUT, 'checks.json'), JSON.stringify({ database: DB, mode, checks }, null, 2))
  stop(frontend); stop(backend)
}
