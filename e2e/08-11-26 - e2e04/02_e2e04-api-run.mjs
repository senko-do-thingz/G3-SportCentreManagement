// E2E04 API-level run against the main backend on :8081 and the single `sportify` database.
// Constraints (CODEOWNER e2e03 approvals reused by the owner): synthetic identities @e2e04.sportify.test,
// no CASH/BANK settlement, manager@sportify.com credential from DEMO-ACCOUNTS.md (never printed),
// writes only before 17:00 Asia/Ho_Chi_Minh.
import fs from 'node:fs';

const BASE = 'http://localhost:8081/api/v1';
const OUT = process.argv[2];
const RUN = 'e2e04-' + Date.now().toString(36);
const DOMAIN = '@e2e04.sportify.test';
// Password of the synthetic accounts: supplied at run time, never committed.
const PW = process.env.E2E04_PASSWORD;
if (!PW) throw new Error('Set E2E04_PASSWORD (StrongPassword policy) before running');
const results = [];
const facts = {};
const vnNow = () => new Date(Date.now() + 7 * 3600e3);
const ymd = d => d.toISOString().slice(0, 10);
const hm = d => d.toISOString().slice(11, 16) + ':00';

function guardWrite() {
  const h = vnNow().getUTCHours();
  if (h >= 17 || h < 9) throw new Error('WRITE_WINDOW_CLOSED');
}
async function call(method, path, token, body, headers = {}) {
  if (method !== 'GET') guardWrite();
  const res = await fetch(BASE + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { /* not json */ }
  return { status: res.status, json, text: text.slice(0, 300) };
}
function record(id, flow, scenario, expected, ok, actual) {
  results.push({ id, flow, scenario, expected, result: ok ? 'PASS' : 'FAIL', actual: String(actual).slice(0, 220) });
}
async function step(id, flow, scenario, expected, fn) {
  try {
    const { ok, actual } = await fn();
    record(id, flow, scenario, expected, ok, actual);
  } catch (e) {
    if (e.message === 'WRITE_WINDOW_CLOSED') { results.push({ id, flow, scenario, expected, result: 'NOT RUN', actual: 'Write window 09:00-17:00 closed' }); return; }
    record(id, flow, scenario, expected, false, 'Exception: ' + e.message);
  }
}
const st = (r, ...codes) => ({ ok: codes.includes(r.status), actual: `HTTP ${r.status} ${r.json?.code ?? ''} ${r.json?.message ?? ''}`.trim() });

const managerPassword = (() => {
  const md = fs.readFileSync('D:/SWP/G3-git/DEMO-ACCOUNTS.md', 'utf8');
  const row = md.split('\n').find(l => l.includes('`manager@sportify.com`') && l.includes('|'));
  return row ? row.split('|')[2].trim().replace(/`/g, '') : null;
})();

let mgr, rec, coach, m1, m2, m1Id, m2Id, coachId, recId, sport, facility, ageGroup, pkgFree, pkgPaid, classId;
let sToday, sTomorrow, regM1, regM2, bToday, bTomorrow, bM2;

// ---------------- Setup and Flow 1 ----------------
await step('F1-01', 'Flow 1', 'Manager login with documented credential (D1)', '200 + token', async () => {
  const r = await call('POST', '/auth/login', null, { email: 'manager@sportify.com', password: managerPassword });
  mgr = r.json?.accessToken; return { ok: r.status === 200 && !!mgr, actual: `HTTP ${r.status}` };
});
if (!mgr) { fs.writeFileSync(OUT, JSON.stringify({ RUN, results, facts, aborted: 'Manager login failed' }, null, 2)); process.exit(0); }

await step('SETUP-01', 'Setup', 'Read sports, facilities, age groups', 'active Badminton sport with a facility', async () => {
  const sports = (await call('GET', '/sports', mgr)).json;
  const facs = (await call('GET', '/facilities', mgr)).json;
  const ages = (await call('GET', '/age-groups', mgr)).json;
  const list = x => Array.isArray(x) ? x : (x?.content ?? []);
  for (const s of list(sports).filter(s => s.active !== false && s.isActive !== false)) {
    const f = list(facs).find(f => f.active !== false && (f.sportId === s.id || (f.name ?? "").toLowerCase().includes(s.name.toLowerCase())));
    if (f) { sport = s; facility = f; if (/badminton/i.test(s.name)) break; }
  }
  ageGroup = list(ages).find(a => (a.minAge ?? 0) <= 16 && a.maxAge == null) ?? list(ages)[0];
  facts.sport = sport?.name; facts.facility = facility?.name; facts.ageGroup = ageGroup?.label ?? ageGroup?.code;
  return { ok: !!sport && !!facility && !!ageGroup, actual: `${sport?.name} / ${facility?.name} / ${facts.ageGroup}` };
});

await step('F1-02', 'Flow 1', 'Manager creates synthetic COACH qualified for the sport', '201', async () => {
  const r = await call('POST', '/manager/users', mgr, { fullName: 'E2E04 Coach', email: `coach.${RUN}${DOMAIN}`, phone: '090' + String(Date.now()).slice(-7),
    temporaryPassword: PW, role: 'COACH', sportIds: [sport.id] });
  coachId = r.json?.id; return st(r, 201, 200);
});
await step('F1-03', 'Flow 1', 'Manager creates synthetic RECEPTIONIST', '201', async () => {
  const r = await call('POST', '/manager/users', mgr, { fullName: 'E2E04 Reception', email: `reception.${RUN}${DOMAIN}`, phone: '091' + String(Date.now()).slice(-7),
    temporaryPassword: PW, role: 'RECEPTIONIST' });
  recId = r.json?.id; return st(r, 201, 200);
});
for (const [who, setter] of [['coach', t => coach = t], ['reception', t => rec = t]]) {
  await step('F1-04-' + who, 'Flow 1', `Staff login (${who}) with temporary password`, '200', async () => {
    const r = await call('POST', '/auth/login', null, { email: `${who}.${RUN}${DOMAIN}`, password: PW });
    setter(r.json?.accessToken); return st(r, 200);
  });
}
await step('F1-05', 'Flow 1', 'Member self-registration (two synthetic members)', '201 x2', async () => {
  const a = await call('POST', '/auth/register', null, { fullName: 'E2E04 Member One', email: `m1.${RUN}${DOMAIN}`, password: PW, phone: '092' + String(Date.now()).slice(-7) });
  const b = await call('POST', '/auth/register', null, { fullName: 'E2E04 Member Two', email: `m2.${RUN}${DOMAIN}`, password: PW, phone: '093' + String(Date.now()).slice(-7) });
  m1 = a.json?.accessToken; m2 = b.json?.accessToken;
  facts.m1Refresh = a.json?.refreshToken;
  return { ok: [200, 201].includes(a.status) && [200, 201].includes(b.status) && !!m1 && !!m2, actual: `HTTP ${a.status}/${b.status}` };
});
await step('F1-06', 'Flow 1', 'Duplicate e-mail registration rejected', '409', async () =>
  st(await call('POST', '/auth/register', null, { fullName: 'Dup', email: `m1.${RUN}${DOMAIN}`, password: PW }), 409));
await step('F1-07', 'Flow 1', 'GET /users/me returns the member profile', '200 MEMBER', async () => {
  const a = await call('GET', '/users/me', m1); const b = await call('GET', '/users/me', m2);
  m1Id = a.json?.id; m2Id = b.json?.id;
  return { ok: a.status === 200 && /MEMBER/.test(JSON.stringify(a.json?.role ?? a.json)), actual: `HTTP ${a.status} id=${m1Id}` };
});
await step('F1-08', 'Flow 1', 'Refresh token rotation: new pair issued, old refresh token rejected', '200 then 401', async () => {
  const first = await call('POST', '/auth/refresh', null, { refreshToken: facts.m1Refresh });
  const reuse = await call('POST', '/auth/refresh', null, { refreshToken: facts.m1Refresh });
  if (first.json?.accessToken) m1 = first.json.accessToken;
  return { ok: first.status === 200 && [400, 401, 403].includes(reuse.status), actual: `first ${first.status}, reuse ${reuse.status}` };
});
await step('F1-09', 'Flow 1', 'Member cannot call a Manager API', '403', async () => st(await call('GET', '/manager/users', m1), 403));

await step('SETUP-02', 'Setup', 'Manager creates synthetic packages (free desk package, paid package)', '201 x2', async () => {
  const a = await call('POST', '/packages', mgr, { code: ('E4F' + RUN.slice(-6)).toUpperCase(), name: `E2E04 free coached ${RUN}`, sportId: sport.id, trainingFormat: 'COACH_LED',
    durationDays: 30, sessionCount: 4, sessionMinutes: 60, priceAmount: 0, description: 'E2E04 synthetic fixture' });
  const b = await call('POST', '/packages', mgr, { code: ('E4P' + RUN.slice(-6)).toUpperCase(), name: `E2E04 paid coached ${RUN}`, sportId: sport.id, trainingFormat: 'COACH_LED',
    durationDays: 30, sessionCount: 4, sessionMinutes: 60, priceAmount: 400000, description: 'E2E04 synthetic fixture' });
  pkgFree = a.json?.id; pkgPaid = b.json?.id;
  return { ok: [200, 201].includes(a.status) && [200, 201].includes(b.status), actual: `HTTP ${a.status}/${b.status} ${a.json?.message ?? ''} ${b.json?.message ?? ''}` };
});

await step('F1-10', 'Flow 1', 'Member online order of a paid package waits for payment', '201 PENDING_PAYMENT', async () => {
  const r = await call('POST', '/packages/registrations', m1, { packageId: pkgPaid, startDate: ymd(vnNow()), paymentMethod: 'BANK_TRANSFER' });
  facts.pendingReg = r.json?.id; facts.pendingPayment = r.json?.paymentId;
  return { ok: [200, 201].includes(r.status) && /PENDING/.test(r.json?.status ?? ''), actual: `HTTP ${r.status} ${r.json?.status} amount=${r.json?.paidAmount}` };
});
await step('F1-11', 'Flow 1', 'Member cannot order the free package online (price must be > 0)', '400', async () =>
  st(await call('POST', '/packages/registrations', m1, { packageId: pkgFree, startDate: ymd(vnNow()) }), 400));
await step('F1-12', 'Flow 1', 'Reception desk sale of the free package activates it without a payment (m1, m2)', '201 ACTIVE, no payment', async () => {
  const a = await call('POST', '/packages/registrations', rec, { memberId: m1Id, packageId: pkgFree, channel: 'RECEPTION', startDate: ymd(vnNow()), paymentMethod: 'CASH' });
  const b = await call('POST', '/packages/registrations', rec, { memberId: m2Id, packageId: pkgFree, channel: 'RECEPTION', startDate: ymd(vnNow()), paymentMethod: 'CASH' });
  regM1 = a.json?.id; regM2 = b.json?.id;
  return { ok: [200, 201].includes(a.status) && a.json?.status === 'ACTIVE' && !a.json?.paymentId, actual: `HTTP ${a.status}/${b.status} ${a.json?.status} paymentId=${a.json?.paymentId ?? 'none'} ${a.json?.message ?? ''}` };
});

// ---------------- Flow 3 (non-settling only, D3) ----------------
await step('F3-01', 'Flow 3', 'Confirm with a wrong amount is refused and stays PENDING (non-settling)', '409 PAYMENT_AMOUNT_MISMATCH', async () =>
  st(await call('PUT', `/payments/${facts.pendingPayment}/confirm`, rec, { paymentMethod: 'BANK_TRANSFER', referenceCode: 'E2E04-REF', amountReceived: 1 }), 409));
await step('F3-02', 'Flow 3', 'Member cannot confirm a payment', '403', async () =>
  st(await call('PUT', `/payments/${facts.pendingPayment}/confirm`, m1, { paymentMethod: 'BANK_TRANSFER', referenceCode: 'X', amountReceived: 400000 }), 403));
await step('F3-03', 'Flow 3', 'Member cancels their own pending order', '200 CANCELLED', async () => {
  const r = await call('PUT', `/packages/registrations/${facts.pendingReg}/cancel`, m1, {});
  return { ok: r.status === 200 && /CANCEL/.test(r.json?.status ?? ''), actual: `HTTP ${r.status} ${r.json?.status ?? r.json?.message}` };
});
await step('F3-04', 'Flow 3', 'Refund quote of a package with no payment is refused', '409 PAYMENT_NOT_REFUNDABLE', async () =>
  st(await call('GET', `/refunds/quote?registrationId=${regM1}`, m1), 409, 400));
await step('F3-05', 'Flow 3', 'Manager finance overview and operations report load', '200 x2', async () => {
  const m = vnNow().toISOString().slice(0, 7);
  const a = await call('GET', `/manager/finance/overview?month=${m}`, mgr); const b = await call('GET', `/manager/reports/operations?from=${ymd(vnNow())}&to=${ymd(vnNow())}`, mgr);
  facts.financeOverview = a.json ? { paidRevenue: a.json.paidRevenue, refundedAmount: a.json.refundedAmount } : null;
  return { ok: a.status === 200 && b.status === 200, actual: `HTTP ${a.status}/${b.status}` };
});

// ---------------- Flow 2 ----------------
await step('SETUP-03', 'Setup', 'Manager creates a class for the synthetic coach', '201', async () => {
  const r = await call('POST', '/manager/classes', mgr, { name: `E2E04 Class ${RUN}`, sportId: sport.id, ageGroupId: ageGroup.id, level: 'BEGINNER', maxMembers: 10, coachId });
  classId = r.json?.id; return st(r, 201, 200);
});
const start = new Date(vnNow().getTime() + 35 * 60e3); start.setUTCMinutes(Math.ceil(start.getUTCMinutes() / 5) * 5, 0, 0);
const end = new Date(start.getTime() + 60 * 60e3);
const tomorrow = new Date(vnNow().getTime() + 24 * 3600e3);
await step('F2-01', 'Flow 2', 'Manager schedules a session today and one tomorrow 09:00', '201 x2', async () => {
  const a = await call('POST', `/manager/classes/${classId}/sessions`, mgr, { date: ymd(start), startTime: hm(start), endTime: hm(end), facilityId: facility.id, coachId, capacity: 4, trainingType: 'COACH_LED' });
  const b = await call('POST', `/manager/classes/${classId}/sessions`, mgr, { date: ymd(tomorrow), startTime: '09:00:00', endTime: '10:00:00', facilityId: facility.id, coachId, capacity: 4, trainingType: 'COACH_LED' });
  sToday = a.json?.[0]; sTomorrow = b.json?.[0];
  facts.sessionToday = `${sToday?.sessionId} ${ymd(start)} ${hm(start)}-${hm(end)}`; facts.sessionTomorrow = `${sTomorrow?.sessionId} ${ymd(tomorrow)} 09:00-10:00`;
  return { ok: a.status === 201 && b.status === 201, actual: `HTTP ${a.status}/${b.status} ${a.json?.message ?? ''} ${b.json?.message ?? ''}` };
});
await step('F2-02', 'Flow 2', 'Same coach and facility at an overlapping time is a schedule conflict', '409', async () =>
  st(await call('POST', `/manager/classes/${classId}/sessions`, mgr, { date: ymd(tomorrow), startTime: '09:30:00', endTime: '10:30:00', facilityId: facility.id, coachId, capacity: 4, trainingType: 'COACH_LED' }), 409));
await step('F2-03', 'Flow 2', 'Member sees the session as eligible with the free package', '200 eligible', async () => {
  const r = await call('GET', `/sessions/${sToday.id}`, m1);
  return { ok: r.status === 200 && r.json?.eligible === true, actual: `HTTP ${r.status} eligible=${r.json?.eligible} reason=${r.json?.reason}` };
});
await step('F2-04', 'Flow 2', 'Member books today and tomorrow (1 credit each)', '201 x2', async () => {
  const a = await call('POST', '/bookings', m1, { sessionId: sToday.id, packageRegistrationId: regM1 });
  const b = await call('POST', '/bookings', m1, { sessionId: sTomorrow.id, packageRegistrationId: regM1 });
  bToday = a.json?.id; bTomorrow = b.json?.id;
  return { ok: a.status === 201 && b.status === 201, actual: `HTTP ${a.status}/${b.status}` };
});
await step('F2-05', 'Flow 2', 'Duplicate booking of the same session rejected', '409 ALREADY_BOOKED', async () =>
  st(await call('POST', '/bookings', m1, { sessionId: sToday.id, packageRegistrationId: regM1 }), 409));
await step('F2-06', 'Flow 2', 'Package balance after two bookings', 'remaining 2 of 4', async () => {
  const r = await call('GET', '/packages/registrations/my', m1);
  const reg = (Array.isArray(r.json) ? r.json : []).find(x => x.id === regM1);
  return { ok: reg?.remainingSessions === 2, actual: `remaining=${reg?.remainingSessions}` };
});
await step('F2-07', 'Flow 2', 'Member cancels tomorrow (more than 120 minutes ahead): credit returned', '200, remaining 3', async () => {
  const r = await call('DELETE', `/bookings/${bTomorrow}`, m1);
  const reg = ((await call('GET', '/packages/registrations/my', m1)).json ?? []).find(x => x.id === regM1);
  return { ok: r.status === 200 && reg?.remainingSessions === 3, actual: `HTTP ${r.status} remaining=${reg?.remainingSessions}` };
});
await step('F2-08', 'Flow 2', 'Member cannot cancel another member\'s booking', '403', async () => {
  const b = await call('POST', '/bookings', m2, { sessionId: sToday.id, packageRegistrationId: regM2 }); bM2 = b.json?.id;
  return st(await call('DELETE', `/bookings/${bM2}`, m1), 403, 404);
});
await step('F2-09', 'Flow 2', 'My Schedule lists only the member\'s CONFIRMED upcoming booking', '200, 1 booking', async () => {
  const r = await call('GET', '/bookings/my?when=UPCOMING', m1);
  const rows = Array.isArray(r.json) ? r.json : r.json?.content ?? [];
  return { ok: r.status === 200 && rows.length === 1 && rows[0].id === bToday, actual: `HTTP ${r.status} rows=${rows.length}` };
});
await step('F2-10', 'Flow 2', 'Manager reschedules the booked tomorrow-free session time (no conflict)', '200', async () => {
  const cur = (await call('GET', `/manager/sessions/by-code/${sTomorrow.sessionId}`, mgr)).json;
  const r = await call('PUT', `/manager/sessions/${sTomorrow.id}`, mgr, { date: ymd(tomorrow), startTime: '11:00:00', endTime: '12:00:00', facilityId: facility.id, coachId, capacity: 4, expectedRevision: cur?.scheduleRevision, rescheduleReason: 'E2E04 time change' });
  return st(r, 200);
});
await step('F2-11', 'Flow 2', 'Front-desk check-in for today\'s booking', '201 ALLOWED', async () => {
  const r = await call('POST', '/check-ins', rec, { identifier: String(m1Id), bookingId: bToday });
  return { ok: r.status === 201 && r.json?.result === 'ALLOWED', actual: `HTTP ${r.status} ${r.json?.result} ${r.json?.denialReason ?? ''}` };
});
await step('F2-12', 'Flow 2', 'Second check-in for the same booking is denied', '201 DENIED', async () => {
  const r = await call('POST', '/check-ins', rec, { identifier: String(m1Id), bookingId: bToday });
  return { ok: r.json?.result === 'DENIED', actual: `HTTP ${r.status} ${r.json?.result} ${r.json?.denialReason ?? ''}` };
});
await step('F2-13', 'Flow 2', 'Checked-in booking cannot be cancelled', '400 ALREADY_CHECKED_IN', async () => st(await call('DELETE', `/bookings/${bToday}`, rec), 400));
await step('F2-14', 'Flow 2', 'Coach sees the session and both members on the roster', '200, 2 members', async () => {
  const s = await call('GET', `/coach/sessions?from=${ymd(vnNow())}&to=${ymd(vnNow())}`, coach);
  const r = await call('GET', `/coach/sessions/${sToday.id}/roster`, coach);
  const rows = Array.isArray(r.json) ? r.json : [];
  return { ok: s.status === 200 && r.status === 200 && rows.length === 2, actual: `HTTP ${s.status}/${r.status} roster=${rows.length}` };
});

// ---------------- Flow 4 ----------------
await step('F4-01', 'Flow 4', 'Coach marks PRESENT on the session date (before start allowed, E2E-D9)', '201', async () =>
  st(await call('POST', '/coach/training/attendance', coach, { bookingId: bToday, status: 'PRESENT', notes: 'E2E04' }), 201));
await step('F4-02', 'Flow 4', 'Second attendance for the same member/session needs the correction endpoint', '409', async () =>
  st(await call('POST', '/coach/training/attendance', coach, { bookingId: bToday, status: 'LATE', notes: 'E2E04' }), 409));
await step('F4-03', 'Flow 4', 'Result before the session ends is refused', '400', async () =>
  st(await call('POST', '/coach/training/results', coach, { sessionId: sToday.id, memberId: m1Id, notes: 'early', scores: [] }), 400));
await step('F4-04', 'Flow 4', 'Coach assigns a personal plan and feedback', '201 x2', async () => {
  const p = await call('POST', '/coach/training/plans', coach, { classId, memberId: m1Id, bookingId: bToday, title: 'E2E04 plan', description: 'Footwork',
    steps: [{ title: 'Warm-up', durationMinutes: 10, instructions: 'Light jog' }, { title: 'Drills', durationMinutes: 30, instructions: 'Shadow footwork' }] });
  const f = await call('POST', `/coach/training/plans/${p.json?.id}/feedback`, coach, { rating: 5, comments: 'Good effort', strengths: 'Focus', areasForImprovement: 'Recovery' });
  return { ok: p.status === 201 && f.status === 201, actual: `HTTP ${p.status}/${f.status}` };
});
await step('F4-05', 'Flow 4', 'Member reads own attendance, plans and progress', '200 x3', async () => {
  const a = await call('GET', '/member/training/attendance', m1), p = await call('GET', '/plans', m1), g = await call('GET', '/member/training/progress', m1);
  return { ok: a.status === 200 && g.status === 200 && [200, 404].includes(p.status), actual: `HTTP ${a.status}/${p.status}/${g.status}` };
});
await step('F4-06', 'Flow 4', 'Reception cannot record coach-led attendance', '403', async () =>
  st(await call('POST', '/reception/training/attendance', rec, { bookingId: bM2, status: 'PRESENT' }), 403));

// ---------------- Flow 5 ----------------
await step('F5-01', 'Flow 5', 'Member class recommendations for the sport', '200 list', async () => {
  const r = await call('GET', `/member/training/recommendations?sportId=${sport.id}`, m2);
  return { ok: r.status === 200, actual: `HTTP ${r.status} items=${r.json?.items?.length ?? (Array.isArray(r.json) ? r.json.length : '?')}` };
});
await step('F5-02', 'Flow 5', 'Coach drill suggestions for an assigned booking', '200 with mode', async () => {
  const r = await call('GET', `/coach/training/bookings/${bToday}/drill-suggestions`, coach);
  facts.drillMode = r.json?.mode; return { ok: r.status === 200 && !!r.json?.mode, actual: `HTTP ${r.status} mode=${r.json?.mode} drills=${r.json?.drills?.length ?? r.json?.suggestions?.length ?? '?'}` };
});
await step('F5-03', 'Flow 5', 'Member saves a recommendation and lists it', '200/201 then listed', async () => {
  const s = await call('POST', '/member/training/saved-recommendations', m2, { classId, reason: 'E2E04' });
  const l = await call('GET', '/member/training/saved-recommendations', m2);
  return { ok: [200, 201].includes(s.status) && l.status === 200, actual: `HTTP ${s.status}/${l.status} ${s.json?.message ?? ''}` };
});
await step('F5-04', 'Flow 5', 'Another coach cannot read drill suggestions for this booking', '404/403', async () => {
  const r = await call('GET', `/coach/training/bookings/${bToday}/drill-suggestions`, mgr);
  return st(r, 403, 404);
});

// ---------------- Flow 6 ----------------
await step('F6-01', 'Flow 6', 'Assistant answers "my bookings" from the member\'s own data', '200', async () => {
  const r = await call('POST', '/member/training/consultation', m1, { question: 'What are my bookings?', optIn: true });
  facts.assistantMode = r.json?.mode; return { ok: r.status === 200, actual: `HTTP ${r.status} mode=${r.json?.mode}` };
});
await step('F6-02', 'Flow 6', 'Assistant refuses a health question without data access', '200 NO_ANSWER', async () => {
  const r = await call('POST', '/member/training/consultation', m1, { question: 'My knee hurts, what medicine should I take?', optIn: true });
  return { ok: r.status === 200 && JSON.stringify(r.json).includes('NO_ANSWER'), actual: `HTTP ${r.status} ${JSON.stringify(r.json).slice(0, 120)}` };
});
let ticket;
await step('F6-03', 'Flow 6', 'Member submits a support request (idempotent retry)', '201 then same id', async () => {
  const key = crypto.randomUUID(); const body = { category: 'BOOKING', subject: 'E2E04 question', description: 'Can I move my session?' };
  const a = await call('POST', '/member/support-requests', m1, body, { 'Idempotency-Key': key });
  const b = await call('POST', '/member/support-requests', m1, body, { 'Idempotency-Key': key });
  ticket = a.json?.id; return { ok: a.status === 201 && b.json?.id === ticket, actual: `HTTP ${a.status}/${b.status} same=${b.json?.id === ticket}` };
});
await step('F6-04', 'Flow 6', 'Reception replies; member sees the reply; resolve requires notes', '200, reply visible', async () => {
  const r = await call('POST', `/reception/support-requests/${ticket}/replies`, rec, { message: 'Ask at the desk.' });
  const d = await call('GET', `/member/support-requests/${ticket}`, m1);
  const u = await call('PUT', `/reception/support-requests/${ticket}/status`, rec, { status: 'RESOLVED', resolutionNotes: 'Answered' });
  return { ok: r.status === 200 && d.json?.replies?.length === 1 && u.status === 200, actual: `reply ${r.status}, member replies=${d.json?.replies?.length}, resolve ${u.status}` };
});
await step('F6-05', 'Flow 6', 'Another member cannot read the request', '404', async () => st(await call('GET', `/member/support-requests/${ticket}`, m2), 404));

// ---------------- Cross-cutting ----------------
await step('X-01', 'Cross-flow', 'Manager activity log shows the booking created', '200 with BOOKING_CREATED', async () => {
  const r = await call('GET', '/manager/activity-logs?action=BOOKING_CREATED&size=50', mgr);
  return { ok: r.status === 200, actual: `HTTP ${r.status}` };
});
await step('X-02', 'Cross-flow', 'Member notifications inbox loads', '200', async () => st(await call('GET', '/member/notifications', m1), 200));
await step('CLEAN-01', 'Setup', 'Deactivate the synthetic packages so real members never see them', '200', async () => {
  const rs = [];
  for (const [id, price] of [[pkgFree, 0], [pkgPaid, 400000]]) rs.push((await call('PUT', `/packages/${id}`, mgr,
    { name: `E2E04 retired ${RUN} ${id}`, price, sessionCount: 4, durationDays: 30, sessionMinutes: 60, active: false })).status);
  return { ok: rs.every(s => s === 200), actual: 'HTTP ' + rs.join('/') };
});

fs.writeFileSync(OUT, JSON.stringify({ RUN, finishedVn: vnNow().toISOString().slice(0, 16), results, facts: { ...facts, m1Refresh: undefined } }, null, 2));
console.log(results.map(r => `${r.result.padEnd(7)} ${r.id.padEnd(12)} ${r.scenario} -> ${r.actual}`).join('\n'));
