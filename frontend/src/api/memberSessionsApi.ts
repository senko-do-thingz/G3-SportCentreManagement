import { api } from './client'
import { ApiError } from './errors'
import { parsePage } from './page'
import type { PageResponse } from './types'
import { isCalendarDate, isNonnegativeInteger, isOptionalText, isPositiveId, isRecord, isText } from './responseValidation'

const SESSION_PAGE_SIZE = 100
const MAX_SESSION_PAGES = 100
const SCHEDULE_CHANGED_MESSAGE = 'The schedule changed while loading. Refresh to load the complete schedule.'

export type BrowseFormat = 'SELF_TRAINING' | 'COACH_LED'
export type BrowseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export interface BrowseSession {
  id: number
  sportId: number
  sportName: string
  classId: number | null
  className: string | null
  trainingFormat: BrowseFormat
  level: string | null
  coachId: number | null
  coachName: string | null
  facilityId: number | null
  facilityName: string | null
  sessionDate: string
  startTime: string
  endTime: string
  capacity: number
  bookedCount: number
  freeSeats: number
  full: boolean
  status: string
}
export type EligibilityReason = 'NO_PACKAGE' | 'NO_SESSIONS_LEFT' | 'PACKAGE_EXPIRED_BY_THEN' | 'PACKAGE_NOT_STARTED_BY_THEN' | 'FULL' | 'ALREADY_BOOKED' | 'CAPACITY_UNAVAILABLE'
export interface SuitableRegistration {
  registrationId: number
  registrationCode: string
  packageId: number
  packageName: string
  sportId: number
  trainingFormat: BrowseFormat
  startDate: string
  endDate: string
  remainingSessions: number
}
export interface SessionDetail {
  session: BrowseSession
  memberContext: boolean
  bookingStatus: string | null
  alreadyBooked: boolean | null
  eligible: boolean | null
  reason: EligibilityReason | null
  suitableRegistrations: SuitableRegistration[]
}
export interface SessionQuery {
  sportId?: number
  from?: string
  to?: string
  format?: BrowseFormat
  level?: BrowseLevel
  coachId?: number
  page?: number
  size?: number
}

async function list(query: SessionQuery, signal?: AbortSignal): Promise<PageResponse<BrowseSession>> {
  const { sportId, from, to, format, level, coachId, page, size } = query
  const result = parsePage(await api.get<unknown>('/sessions', { query: { sportId, from, to, format, level, coachId, page, size }, signal }), asBrowseSession)
  if (result.page !== (page ?? 0) || new Set(result.content.map((row) => row.id)).size !== result.content.length) {
    throw new ApiError(0, SCHEDULE_CHANGED_MESSAGE)
  }
  return result
}

/** Shared row guarantee for list, calendar, detail and selected-member eligibility. */
function asBrowseSession(value: unknown): BrowseSession {
  const validTime = (time: unknown): time is string => typeof time === 'string' && /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d(\.\d{1,9})?)?$/.test(time)
  const seconds = (time: string) => { const [hours, minutes, seconds = 0] = time.split(':').map(Number); return hours * 3600 + minutes * 60 + seconds }
  if (!isRecord(value) || !isPositiveId(value.id) || !isPositiveId(value.sportId) || !isText(value.sportName)
    || typeof value.trainingFormat !== 'string' || !['SELF_TRAINING', 'COACH_LED'].includes(value.trainingFormat)
    || typeof value.status !== 'string' || !['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'].includes(value.status)
    || !['classId', 'coachId', 'facilityId'].every((key) => value[key] == null || isPositiveId(value[key]))
    || !['className', 'coachName', 'facilityName', 'level'].every((key) => isOptionalText(value[key]))
    || !isCalendarDate(value.sessionDate) || !validTime(value.startTime) || !validTime(value.endTime)
    || seconds(value.startTime) >= seconds(value.endTime)
    || !isNonnegativeInteger(value.capacity) || !isNonnegativeInteger(value.bookedCount) || !isNonnegativeInteger(value.freeSeats)
    || value.freeSeats !== Math.max(0, value.capacity - value.bookedCount) || value.full !== (value.freeSeats === 0)) {
    throw new ApiError(0, 'Session details could not be read. Refresh availability and try again.')
  }
  return { ...value, classId: value.classId ?? null, className: value.className ?? null, coachId: value.coachId ?? null, coachName: value.coachName ?? null, facilityId: value.facilityId ?? null, facilityName: value.facilityName ?? null, level: value.level ?? null } as unknown as BrowseSession
}

/** Complete a bounded calendar/lookup query. Failure never returns a misleading partial list. */
async function all(query: Omit<SessionQuery, 'page' | 'size'>, signal?: AbortSignal): Promise<BrowseSession[]> {
  const first = await list({ ...query, page: 0, size: SESSION_PAGE_SIZE }, signal)
  if (first.totalPages > MAX_SESSION_PAGES) {
    throw new ApiError(0, 'This date range contains more than 10,000 sessions. Narrow the date range or filters and try again.')
  }
  const rows = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
    const next = await list({ ...query, page, size: SESSION_PAGE_SIZE }, signal)
    if (next.totalPages !== first.totalPages || next.totalElements !== first.totalElements) {
      throw new ApiError(0, SCHEDULE_CHANGED_MESSAGE)
    }
    rows.push(...next.content)
  }
  const unique = [...new Map(rows.map((row) => [row.id, row])).values()]
  if (unique.length !== first.totalElements) throw new ApiError(0, SCHEDULE_CHANGED_MESSAGE)
  return unique
}

async function detail(id: number, signal?: AbortSignal): Promise<SessionDetail> {
  const value = await api.get<unknown>(`/sessions/${id}`, { signal })
  return asSessionDetail(value)
}

async function eligibilityForMember(id: number, memberId: number, signal?: AbortSignal): Promise<SessionDetail> {
  if (!Number.isSafeInteger(id) || id <= 0 || !Number.isSafeInteger(memberId) || memberId <= 0) {
    throw new ApiError(0, 'Choose a valid member and session.')
  }
  const value = await api.get<unknown>(`/sessions/${id}/eligibility`, { query: { memberId }, signal })
  const detail = asSessionDetail(value)
  if (detail.memberContext !== true) throw new ApiError(0, 'Member eligibility could not be confirmed. Refresh and try again.')
  return detail
}

function asSessionDetail(value: unknown): SessionDetail {
  const detail = value as SessionDetail | null
  const session = asBrowseSession(detail?.session)
  if (!detail || typeof detail !== 'object' || !session || !Number.isSafeInteger(session.id) || session.id <= 0
    || typeof detail.memberContext !== 'boolean'
    || ![null, true, false].includes(detail.alreadyBooked)
    || ![null, true, false].includes(detail.eligible)
    || !(detail.bookingStatus === null || typeof detail.bookingStatus === 'string')
    || !(detail.reason === null || typeof detail.reason === 'string')
    || !Array.isArray(detail.suitableRegistrations)
    || !['sportName', 'sessionDate', 'startTime', 'endTime'].every((key) => typeof session[key as keyof BrowseSession] === 'string')
    || !['COACH_LED', 'SELF_TRAINING'].includes(session.trainingFormat)
    || ![session.capacity, session.bookedCount, session.freeSeats].every((number) => Number.isFinite(number))) throw new ApiError(0, 'Session details could not be read. Refresh availability and try again.')
  return { ...detail, session }
}
export const memberSessionsApi = { list, all, detail, eligibilityForMember }
