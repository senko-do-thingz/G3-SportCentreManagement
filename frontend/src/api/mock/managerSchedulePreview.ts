import type { RequestOptions } from '../client'
import type { ClassRequest, ManagerClass } from '../managerClassesApi'
import type { ManagerSession, SessionCreate, SessionUpdate } from '../managerSessionsApi'
import type { Sport } from '../packagesApi'
import { addDays, centerNow, monday, repeatDates } from '../../pages/manager/scheduleModel'

const coaches = [
  { id: 3, fullName: 'Daniel Smith', headline: 'Certified sports coach', specialties: 'Foundation skills and safe technique', yearsExperience: 8, sportIds: [1, 2, 4] },
  { id: 4, fullName: 'Chris Nguyen', headline: 'Performance coach', specialties: 'Intermediate training', yearsExperience: 5, sportIds: [2, 3, 4, 5, 6] },
]
const facilities = [{ id: 1, name: 'Training Court A', active: true }, { id: 2, name: 'Training Court B', active: true }, { id: 3, name: 'Indoor Pool', active: true }]
const ages = [{ id: 1, code: 'ADULT', label: 'Adults (18+)', minAge: 18, maxAge: null }, { id: 2, code: 'TEEN', label: 'Teens (12-17)', minAge: 12, maxAge: 17 }]
let classes: ManagerClass[] = [
  { id: 1, code: 'CL-001', name: 'Basketball Foundations', sportId: 2, sportName: 'Basketball', ageGroupId: 1, ageGroupLabel: ages[0].label, level: 'BEGINNER', coachId: 3, coachName: coaches[0].fullName, maxMembers: 20, upcomingSessions: 2, active: true, createdAt: '2026-10-01T09:00:00', updatedAt: '2026-10-01T09:00:00' },
  { id: 2, code: 'CL-002', name: 'Swimming Technique', sportId: 3, sportName: 'Swimming', ageGroupId: 2, ageGroupLabel: ages[1].label, level: 'INTERMEDIATE', coachId: 4, coachName: coaches[1].fullName, maxMembers: 12, upcomingSessions: 1, active: true, createdAt: '2026-10-01T09:00:00', updatedAt: '2026-10-01T09:00:00' },
]
const week = monday(centerNow().date)
let sessions: ManagerSession[] = [0, 2, 4, 6].map((offset, index) => ({
  id: index + 1, date: addDays(week, offset), startTime: '09:00:00', endTime: '10:00:00', class: { id: 1, code: 'CL-001', name: classes[0].name }, coach: { id: 3, name: coaches[0].fullName }, facility: { id: 1, name: facilities[0].name }, capacity: 20, bookedCount: index === 3 ? 0 : 4,
  trainingType: 'COACH_LED', status: index === 2 ? 'CANCELLED' : 'PUBLISHED', cancelReason: index === 2 ? 'Facility maintenance' : null, cancelledAt: index === 2 ? `${week}T08:00:00` : null, cancelledByUserId: index === 2 ? 1 : null,
}))
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (status: number, message: string) => json(status, { status, message })

/** Called only inside the existing DEV + explicit preview flag + preview-token boundary. */
export function schedulePreviewResponse(path: string, options: RequestOptions, sports: Sport[], empty: boolean): Response | undefined {
  const method = options.method ?? 'GET'
  const query = options.query ?? {}
  if (path === '/age-groups') return json(200, ages)
  if (path === '/facilities') return json(200, facilities)
  if (path === '/coaches') return json(200, coaches.filter((coach) => !query.sportId || coach.sportIds.includes(Number(query.sportId))))
  const profile = /^\/coaches\/(\d+)$/.exec(path)
  if (profile) {
    const coach = coaches.find((entry) => entry.id === Number(profile[1]))
    return coach ? json(200, { ...coach, name: coach.fullName, bio: 'Frontend preview coach.', sports: sports.filter((sport) => coach.sportIds.includes(sport.id)), certifications: [{ id: coach.id, sportId: coach.sportIds[0], name: 'Sports Coaching Certificate', issuer: 'Preview Sports Federation', verified: true, issuedOn: '2024-01-01', expiresOn: null }] }) : failure(404, 'Coach not found.')
  }
  if (path === '/manager/classes' && method === 'GET') {
    const search = String(query.search ?? '').toLowerCase()
    const filtered = empty ? [] : classes.filter((entry) => (!query.sportId || entry.sportId === Number(query.sportId)) && (!query.coachId || entry.coachId === Number(query.coachId)) && (!query.level || entry.level === query.level) && (query.active === undefined || entry.active === (String(query.active) === 'true')) && (!search || `${entry.name} ${entry.code}`.toLowerCase().includes(search)))
    const page = Number(query.page ?? 0), size = Number(query.size ?? 20)
    return json(200, { content: filtered.slice(page * size, (page + 1) * size), page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) })
  }
  const classPath = /^\/manager\/classes\/(\d+)(\/status)?$/.exec(path)
  if ((path === '/manager/classes' && method === 'POST') || (classPath && method === 'PUT')) {
    const body = options.body as ClassRequest
    const old = classPath ? classes.find((entry) => entry.id === Number(classPath[1])) : undefined
    if (classPath && !old) return failure(404, 'Class not found.')
    const sport = sports.find((entry) => entry.id === body.sportId), coach = coaches.find((entry) => entry.id === body.coachId), age = ages.find((entry) => entry.id === body.ageGroupId)
    if (!sport || !coach?.sportIds.includes(body.sportId) || !age) return failure(400, 'Choose valid class details and a qualified coach.')
    const id = old?.id ?? Math.max(0, ...classes.map((entry) => entry.id)) + 1
    const entry: ManagerClass = { ...body, id, code: old?.code ?? `CL-${String(id).padStart(3, '0')}`, sportName: sport.name, coachName: coach.fullName, ageGroupLabel: age.label, upcomingSessions: old?.upcomingSessions ?? 0, active: old?.active ?? true, createdAt: old?.createdAt ?? `${centerNow().date}T09:00:00`, updatedAt: `${centerNow().date}T09:00:00` }
    classes = [entry, ...classes.filter((current) => current.id !== id)]
    return json(old ? 200 : 201, entry)
  }
  if (classPath) {
    const entry = classes.find((current) => current.id === Number(classPath[1]))
    if (!entry) return failure(404, 'Class not found.')
    if (method === 'PATCH' && classPath[2]) entry.active = (options.body as { active: boolean }).active
    return json(200, entry)
  }
  if (path === '/manager/sessions' && method === 'GET') return json(200, empty ? [] : sessions.filter((entry) => entry.date >= String(query.from) && entry.date <= String(query.to) && (!query.classId || entry.class?.id === Number(query.classId)) && (!query.coachId || entry.coach?.id === Number(query.coachId)) && (!query.facilityId || entry.facility?.id === Number(query.facilityId))))
  const create = /^\/manager\/classes\/(\d+)\/sessions$/.exec(path)
  const update = /^\/manager\/sessions\/(\d+)(\/cancel)?$/.exec(path)
  if (create || update) {
    const old = update ? sessions.find((entry) => entry.id === Number(update[1])) : undefined
    if (update && !old) return failure(404, 'Session not found.')
    if (update?.[2] && old) { old.status = 'CANCELLED'; old.cancelReason = (options.body as { reason: string }).reason; old.cancelledByUserId = 1; old.cancelledAt = `${centerNow().date}T${centerNow().time}`; return json(200, old) }
    const body = options.body as SessionCreate | SessionUpdate
    const sessionClass = classes.find((entry) => entry.id === (old?.class?.id ?? Number(create?.[1])))
    const facility = facilities.find((entry) => entry.id === body.facilityId), coach = coaches.find((entry) => entry.id === body.coachId)
    if (!sessionClass || !facility) return failure(400, 'Class and facility are required.')
    const dates = body.date ? [body.date] : 'fromDate' in body && body.fromDate ? repeatDates(body.fromDate, body.toDate, body.daysOfWeek) : []
    if (!dates.length || dates.length > 100) return failure(400, 'Choose between 1 and 100 sessions.')
    const conflicts = dates.flatMap((date) => sessions.filter((entry) => entry.id !== old?.id && entry.status !== 'CANCELLED' && entry.date === date && entry.startTime < body.endTime && entry.endTime > body.startTime && (entry.facility?.id === body.facilityId || (body.coachId && entry.coach?.id === body.coachId))).map((entry) => ({ sessionId: entry.id, requestedDate: date, date: entry.date, startTime: entry.startTime, endTime: entry.endTime, class: entry.class, coach: entry.coach, facility: entry.facility, conflictType: entry.facility?.id === body.facilityId ? body.coachId && entry.coach?.id === body.coachId ? 'BOTH' : 'FACILITY' : 'COACH' })))
    if (conflicts.length) return json(409, { status: 409, message: 'The schedule overlaps existing sessions.', details: { conflicts } })
    let nextId = Math.max(0, ...sessions.map((entry) => entry.id)) + 1
    const result: ManagerSession[] = dates.map((date) => ({ id: old?.id ?? nextId++, date, startTime: body.startTime, endTime: body.endTime, class: { id: sessionClass.id, name: sessionClass.name, code: sessionClass.code }, coach: coach ? { id: coach.id, name: coach.fullName } : null, facility, capacity: body.capacity, bookedCount: old?.bookedCount ?? 0, trainingType: old?.trainingType ?? ('trainingType' in body ? body.trainingType : 'COACH_LED'), status: old?.status ?? 'PUBLISHED', cancelReason: null, cancelledAt: null, cancelledByUserId: null }))
    sessions = [...sessions.filter((entry) => entry.id !== old?.id), ...result]
    return json(old ? 200 : 201, old ? result[0] : result)
  }
  return undefined
}
