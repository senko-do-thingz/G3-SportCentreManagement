import { api } from './client'
import { ApiError } from './errors'
import { normalizePage } from './page'
import type { PageResponse, SpringPage } from './types'

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
  return normalizePage(await api.get<PageResponse<BrowseSession> | SpringPage<BrowseSession>>('/sessions', { query: { sportId, from, to, format, level, coachId, page, size }, signal }))
}

/** Complete a bounded calendar/lookup query. Failure never returns a misleading partial list. */
async function all(query: Omit<SessionQuery, 'page' | 'size'>, signal?: AbortSignal): Promise<BrowseSession[]> {
  const first = await list({ ...query, page: 0, size: 100 }, signal)
  const rows = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
    rows.push(...(await list({ ...query, page, size: 100 }, signal)).content)
  }
  if (rows.length < first.totalElements) throw new ApiError(0, 'The schedule changed while loading. Refresh to load the complete schedule.')
  return [...new Map(rows.map((row) => [row.id, row])).values()]
}

export const memberSessionsApi = { list, all, detail: (id: number, signal?: AbortSignal) => api.get<SessionDetail>(`/sessions/${id}`, { signal }) }
