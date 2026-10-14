import type { RequestOptions } from '../client'
import type { Sport } from '../packagesApi'
import type { PageResponse, UserProfile } from '../types'
import type { ActivityLog, CardTierUpdateDraft, ManagerCardTier, ManagerPackage, ManagerUser, PackageCreateRequest, PackageUpdateDraft, StaffAccountRequest } from '../../types/manager'

/** All FE-06 fixture data and simulated business writes live in this opt-in module. */
const sports: Sport[] = ['Football', 'Basketball', 'Swimming', 'Badminton', 'Volleyball', 'Tennis'].map((name, index) => ({
  id: index + 1, code: name.toUpperCase(), name, venueType: name === 'Swimming' ? 'POOL' : 'COURT',
  description: null, imageUrl: null, displayOrder: index,
}))

const manager: UserProfile = { id: 1, fullName: 'Morgan Lee', email: 'manager@preview.sportify.local', phone: '0901234500', role: 'MANAGER', status: 'ACTIVE' }
const member: UserProfile = { id: 2, fullName: 'Taylor Nguyen', email: 'member@preview.sportify.local', phone: '0901234567', role: 'MEMBER', status: 'ACTIVE' }
let users: ManagerUser[] = [
  { ...manager, createdAt: '2026-09-01T09:00:00' },
  { ...member, createdAt: '2026-09-27T10:00:00' },
  { id: 3, fullName: 'Daniel Smith', email: 'daniel@sportify.demo', phone: '0901234568', role: 'COACH', status: 'ACTIVE', createdAt: '2026-09-01T10:00:00' },
  { id: 4, fullName: 'Chris Nguyen', email: 'chris@sportify.demo', phone: '0901234569', role: 'COACH', status: 'ACTIVE', createdAt: '2026-09-03T10:00:00' },
  { id: 5, fullName: 'Jamie Tran', email: 'jamie@sportify.demo', phone: '0901234570', role: 'RECEPTIONIST', status: 'ACTIVE', createdAt: '2026-09-04T10:00:00' },
  { id: 6, fullName: 'Linh Pham', email: 'linh@sportify.demo', phone: '0901234571', role: 'MEMBER', status: 'INACTIVE', createdAt: '2026-09-05T10:00:00' },
  ...Array.from({ length: 20 }, (_, index) => ({ id: index + 7, fullName: `Sample Member ${index + 1}`, email: `sample${index + 1}@sportify.demo`, phone: `09020000${String(index).padStart(2, '0')}`, role: 'MEMBER', status: 'ACTIVE', createdAt: '2026-10-01T10:00:00' })),
]

const priceSets = [
  [80000, 500000, 1350000, 150000, 900000, 2400000],
  [70000, 450000, 1200000, 130000, 800000, 2100000],
  [90000, 600000, 1620000, 180000, 1100000, 2970000],
  [70000, 450000, 1200000, 140000, 850000, 2250000],
  [60000, 400000, 1080000, 120000, 750000, 1950000],
  [120000, 800000, 2160000, 220000, 1400000, 3780000],
]
let packages: ManagerPackage[] = sports.flatMap((sport, sportIndex) => priceSets[sportIndex].map((priceAmount, index) => ({
  id: sportIndex * 6 + index + 1, code: `PK-${String(sportIndex * 6 + index + 1).padStart(3, '0')}`,
  name: `${sport.name} ${['single session', '30-day package', '90-day package'][index % 3]}`,
  sportId: sport.id, sportName: sport.name, sportCode: sport.code,
  trainingFormat: index < 3 ? 'SELF_TRAINING' : 'COACH_LED', durationDays: [1, 30, 90][index % 3],
  sessionCount: [1, 8, 24][index % 3], sessionMinutes: index < 3 ? 60 : 90,
  priceAmount, description: null, isActive: true,
})))
const tiers: ManagerCardTier[] = [
  { id: 1, code: 'STANDARD', name: 'Standard', price: 0, durationMonths: null, discountPercentage: 0, description: 'Pay standard sport prices. No expiry for account access.', isActive: true },
  { id: 2, code: 'GOLD', name: 'Gold', price: 300000, durationMonths: 12, discountPercentage: 5, description: 'Self-training and coach-led packages. Single sessions excluded.', isActive: true },
  { id: 3, code: 'VIP', name: 'VIP', price: 600000, durationMonths: 12, discountPercentage: 10, description: 'Self-training and coach-led packages. Single sessions excluded.', isActive: true },
]
let logs: ActivityLog[] = [
  { id: 3, createdAt: '2026-10-14T09:30:00', actorId: 1, actorName: 'Morgan Lee', actorRole: 'MANAGER', action: 'PACKAGE_CREATED', entityType: 'SPORT_PACKAGE', entityId: 5, entityCode: 'PK-005', summary: 'Created Football coach-led 30-day package.', details: '8 sessions, 90 minutes each. Price: 900.000 VND.' },
  { id: 2, createdAt: '2026-10-14T09:15:00', actorId: 1, actorName: 'Morgan Lee', actorRole: 'MANAGER', action: 'CARD_TIER_UPDATED', entityType: 'CARD_TIER', entityId: 3, entityCode: 'VIP', summary: 'Updated VIP benefits.', details: '12-month validity and 10% off eligible duration packages.' },
  { id: 1, createdAt: '2026-10-13T16:40:00', actorId: 5, actorName: 'Jamie Tran', actorRole: 'RECEPTIONIST', action: 'PAYMENT_RECORDED', entityType: 'PAYMENT', entityId: 1038, entityCode: 'PAY-1038', summary: 'Recorded a membership payment.', details: 'Payment received at the front desk.' },
]

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
function failure(status: number, message: string, details?: Record<string, string>) {
  return json(status, { status, message, details })
}
function page<T>(rows: T[], options: RequestOptions): PageResponse<T> {
  const current = Math.max(0, Number(options.query?.page ?? 0))
  const size = Math.max(1, Number(options.query?.size ?? 20))
  return { content: rows.slice(current * size, (current + 1) * size), page: current, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}
function record(action: string, entityType: string, entityId: number, entityCode: string, summary: string) {
  logs = [{ id: Math.max(0, ...logs.map((log) => log.id)) + 1, createdAt: new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).replace(' ', 'T'), actorId: manager.id, actorName: manager.fullName, actorRole: manager.role, action, entityType, entityId, entityCode, summary, details: 'Created in the frontend preview. No backend data changed.' }, ...logs]
}
function delay(signal: AbortSignal | undefined, ms: number): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('Aborted', 'AbortError')); return }
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve() }, ms)
    signal?.addEventListener('abort', abort, { once: true })
  })
}

export async function previewResponse(path: string, options: RequestOptions, token: string | null): Promise<Response | undefined> {
  const body = options.body as Record<string, unknown> | undefined
  const method = options.method ?? 'GET'
  if (path === '/auth/login' && String(body?.email).endsWith('@preview.sportify.local')) {
    if (!['manager@preview.sportify.local', 'member@preview.sportify.local'].includes(String(body?.email)) || body?.password !== 'preview') return failure(401, 'Unknown preview account.')
    const role = body?.email === manager.email ? 'MANAGER' : 'MEMBER'
    await delay(options.signal, 200)
    return json(200, { accessToken: `preview-${role}`, refreshToken: `preview-refresh-${role}` })
  }
  if (!token?.startsWith('preview-')) return undefined
  if (path === '/users/me') return json(200, token === 'preview-MANAGER' ? manager : member)
  const isManagerRoute = path.startsWith('/manager/') || (method !== 'GET' && (path.startsWith('/packages') || path.startsWith('/membership-cards/tiers/')))
  if (isManagerRoute && token !== 'preview-MANAGER') return failure(403, 'Manager access is required.')
  const scenario = new URLSearchParams(window.location.search).get('previewState')
  await delay(options.signal, scenario === 'slow' ? 2500 : 250)
  if (method === 'GET' && path.startsWith('/manager/') && scenario === 'error') return failure(503, 'The preview service is temporarily unavailable. Change the preview state or retry.')
  const empty = scenario === 'empty'
  if (path === '/sports') return json(200, sports)
  if (path === '/manager/users' && method === 'GET') {
    const query = options.query ?? {}
    const search = String(query.search ?? '').toLowerCase()
    const filtered = users.filter((user) => (!query.role || user.role === query.role) && (!query.status || user.status === query.status) && (!search || `${user.fullName} ${user.email} ${user.phone}`.toLowerCase().includes(search)))
    return json(200, page(empty ? [] : filtered, options))
  }
  if (path === '/manager/users' && method === 'POST') {
    const request = options.body as StaffAccountRequest
    if (users.some((user) => user.email.toLowerCase() === request.email.toLowerCase())) return failure(409, 'An account already uses this email address.', { email: 'Use a different email address.' })
    if (users.some((user) => user.phone === request.phone)) return failure(400, 'Phone is already in use.', { phone: 'Use a different phone number.' })
    const user: ManagerUser = { id: Math.max(...users.map((item) => item.id)) + 1, fullName: request.fullName, email: request.email, phone: request.phone, role: request.role, status: 'ACTIVE', createdAt: new Date().toISOString() }
    users = [user, ...users]
    record('USER_CREATED', 'USER_ACCOUNT', user.id, user.email, `Created ${user.fullName}'s staff account.`)
    return json(201, user)
  }
  const userStatus = /^\/manager\/users\/(\d+)\/status$/.exec(path)
  if (userStatus && method === 'PATCH') {
    const user = users.find((item) => item.id === Number(userStatus[1]))
    if (!user) return failure(404, 'Account not found.')
    if (user.id === manager.id && body?.status === 'INACTIVE') return failure(400, 'You cannot deactivate your own account.')
    user.status = String(body?.status)
    record('USER_STATUS_CHANGED', 'USER_ACCOUNT', user.id, user.email, `Changed ${user.fullName}'s account status to ${user.status.toLowerCase()}.`)
    return json(200, user)
  }
  if (path === '/manager/packages' && method === 'GET') {
    const query = options.query ?? {}
    return json(200, empty ? [] : packages.filter((item) => (!query.sportId || item.sportId === Number(query.sportId)) && (!query.format || item.trainingFormat === query.format) && (query.active === undefined || query.active === '' || item.isActive === (String(query.active) === 'true'))))
  }
  if (path === '/packages' && method === 'POST') {
    const request = options.body as PackageCreateRequest
    if (packages.some((item) => item.code === request.code)) return failure(409, 'Package code already exists.', { code: 'Use a unique package code.' })
    const sport = sports.find((item) => item.id === request.sportId)
    if (!sport) return failure(400, 'Choose a valid sport.', { sportId: 'Sport is not available.' })
    const item: ManagerPackage = { ...request, id: Math.max(...packages.map((item) => item.id)) + 1, sportName: sport.name, sportCode: sport.code, isActive: true, description: request.description ?? null }
    packages = [item, ...packages]
    record('PACKAGE_CREATED', 'SPORT_PACKAGE', item.id, item.code, `Created ${item.name}.`)
    return json(201, item)
  }
  const packageUpdate = /^\/packages\/(\d+)$/.exec(path)
  if (packageUpdate && method === 'PUT') {
    const item = packages.find((item) => item.id === Number(packageUpdate[1]))
    if (!item) return failure(404, 'Package not found.')
    Object.assign(item, options.body as PackageUpdateDraft)
    record('PACKAGE_UPDATED', 'SPORT_PACKAGE', item.id, item.code, `Updated ${item.name}.`)
    return json(200, item)
  }
  if (path === '/manager/card-tiers' && method === 'GET') return json(200, empty ? [] : tiers)
  const tierUpdate = /^\/membership-cards\/tiers\/(\d+)$/.exec(path)
  if (tierUpdate && method === 'PUT') {
    const tier = tiers.find((item) => item.id === Number(tierUpdate[1]))
    if (!tier) return failure(404, 'Membership tier not found.')
    if (tier.code === 'STANDARD') return failure(400, 'Standard is a protected free account tier.')
    Object.assign(tier, options.body as CardTierUpdateDraft)
    record('CARD_TIER_UPDATED', 'CARD_TIER', tier.id, tier.code, `Updated ${tier.name} benefits.`)
    return json(200, tier)
  }
  if (path === '/manager/activity-logs' && method === 'GET') {
    const query = options.query ?? {}
    const search = String(query.search ?? '').toLowerCase()
    const filtered = logs.filter((log) => (!query.actorId || log.actorId === Number(query.actorId)) && (!query.action || log.action === query.action) && (!query.from || log.createdAt.slice(0, 10) >= String(query.from)) && (!query.to || log.createdAt.slice(0, 10) <= String(query.to)) && (!search || `${log.actorName} ${log.action} ${log.entityCode} ${log.summary}`.toLowerCase().includes(search)))
    return json(200, page(empty ? [] : filtered, options))
  }
  return failure(404, 'This service is outside the FE-06 preview.')
}
