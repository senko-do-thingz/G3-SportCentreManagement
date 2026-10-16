import type { RequestOptions } from '../client'
import type { BrowseSession, EligibilityReason, SessionDetail } from '../memberSessionsApi'
import type { Sport } from '../packagesApi'
import { browseAddDays, browseToday } from '../../pages/member/memberScheduleDates'

const reasons: (EligibilityReason | null)[] = [null, null, 'FULL', 'NO_PACKAGE', 'NO_SESSIONS_LEFT', 'PACKAGE_EXPIRED_BY_THEN', 'PACKAGE_NOT_STARTED_BY_THEN', 'ALREADY_BOOKED', 'CAPACITY_UNAVAILABLE']
function sessions(sports: Sport[]): BrowseSession[] {
  const sport = sports.find((entry) => entry.code === 'BASKETBALL') ?? sports[0]
  return reasons.map((reason, index) => ({
    id: 801 + index, sportId: sport.id, sportName: sport.name, classId: index === 1 ? null : 801,
    className: index === 1 ? null : `Basketball ${reason ? reason.toLowerCase().replaceAll('_', ' ') : 'Foundations'}`,
    trainingFormat: index === 1 ? 'SELF_TRAINING' : 'COACH_LED', level: 'BEGINNER', coachId: index === 1 ? null : 3,
    coachName: index === 1 ? null : 'Daniel Smith', facilityId: 1, facilityName: 'Training Court A',
    sessionDate: browseAddDays(browseToday(), 1 + index % 3), startTime: `${String(9 + index).padStart(2, '0')}:00:00`, endTime: `${String(10 + index).padStart(2, '0')}:00:00`,
    capacity: 16, bookedCount: reason === 'FULL' ? 16 : 4, freeSeats: reason === 'FULL' ? 0 : 12, full: reason === 'FULL', status: 'PUBLISHED',
  }))
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** Read-only fixtures behind the existing DEV flag and synthetic MEMBER token boundary. */
export function memberBrowsePreview(path: string, options: RequestOptions, sports: Sport[], scenario: string | null): Response | undefined {
  if ((options.method ?? 'GET') !== 'GET') return undefined
  if (path !== '/sessions' && !/^\/sessions\/\d+$/.test(path) && !/^\/coaches\/\d+$/.test(path)) return undefined
  if (scenario === 'error') return json(503, { status: 503, message: 'The member preview service is temporarily unavailable.' })
  const rows = sessions(sports)
  if (path === '/sessions') {
    const query = options.query ?? {}
    const filtered = scenario === 'empty' ? [] : rows.filter((row) => (!query.sportId || row.sportId === Number(query.sportId)) && (!query.from || row.sessionDate >= String(query.from)) && (!query.to || row.sessionDate <= String(query.to)) && (!query.format || row.trainingFormat === query.format) && (!query.level || row.level === query.level) && (!query.coachId || row.coachId === Number(query.coachId)))
    const page = Number(query.page ?? 0), size = Number(query.size ?? 20)
    return json(200, { content: filtered.slice(page * size, (page + 1) * size), page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size) })
  }
  if (path.startsWith('/sessions/')) {
    const session = rows.find((row) => row.id === Number(path.split('/').pop()))
    if (!session) return json(404, { status: 404, message: 'Session not found.' })
    const reason = reasons[session.id - 801]
    const detail: SessionDetail = { session, memberContext: true, alreadyBooked: reason === 'ALREADY_BOOKED', bookingStatus: reason === 'ALREADY_BOOKED' ? 'CONFIRMED' : null, eligible: reason === null, reason,
      suitableRegistrations: reason ? [] : [{ registrationId: 801, registrationCode: 'PREVIEW-801', packageId: 1, packageName: `${session.sportName} preview package`, sportId: session.sportId, trainingFormat: session.trainingFormat, startDate: browseToday(), endDate: browseAddDays(browseToday(), 30), remainingSessions: 8 }] }
    return json(200, detail)
  }
  if (path === '/coaches/3') return json(200, { id: 3, name: 'Daniel Smith', headline: 'Foundation skills coach', bio: 'Frontend preview profile. No backend data is changed.', yearsExperience: 8, specialties: 'Technique, teamwork and safe training', sports: [{ id: rows[0].sportId, name: rows[0].sportName }], certifications: [{ id: 1, sportId: rows[0].sportId, name: 'Sports Coaching Certificate', issuer: 'Preview Sports Federation', issuedOn: '2024-01-01', expiresOn: null, verified: true }], upcomingSessions: { content: rows.filter((row) => row.coachId === 3).slice(0, 3), page: 0, size: 3, totalElements: 8, totalPages: 3 } })
  return json(404, { status: 404, message: 'Coach profile is unavailable.' })
}
