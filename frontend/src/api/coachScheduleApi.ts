import { api } from './client'
import { ApiError } from './errors'

export interface CoachSessionRecord {
  id: number
  classId: number | null
  className: string | null
  sportId: number
  sportName: string
  coachId: number
  coachName: string | null
  facilityId: number | null
  facilityName: string | null
  sessionDate: string
  startTime: string
  endTime: string
  capacity: number | null
  bookedCount: number
  status: string
}

/** The server returns memberPhone, but the coach screens intentionally never render it. */
export interface CoachRosterRecord {
  bookingId: number
  memberCode: string | null
  memberName: string | null
  memberPhone: string | null
  packageRegistrationId: number | null
  packageId: number | null
  packageName: string | null
  bookedAt: string | null
}

const UNEXPECTED_RESPONSE = 'The server returned an unexpected coach schedule response. Please try again later.'

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isNullablePositiveId(value: unknown): value is number | null {
  return value === null || (Number.isSafeInteger(value) && Number(value) > 0)
}

function isValidDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(`${value}T00:00:00Z`))
    && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}

function parseCoachSession(value: unknown): CoachSessionRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(0, UNEXPECTED_RESPONSE)
  const session = value as Partial<CoachSessionRecord>
  if (!Number.isSafeInteger(session.id) || Number(session.id) <= 0
    || !Number.isSafeInteger(session.sportId) || Number(session.sportId) <= 0
    || !Number.isSafeInteger(session.coachId) || Number(session.coachId) <= 0
    || !isValidDate(session.sessionDate ?? '')
    || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(session.startTime ?? '')
    || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(session.endTime ?? '')
    || typeof session.sportName !== 'string' || !['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'].includes(session.status ?? '')
    || !isNullablePositiveId(session.classId) || !isNullablePositiveId(session.facilityId)
    || !isNullableString(session.className) || !isNullableString(session.coachName) || !isNullableString(session.facilityName)
    || !(session.capacity === null || (Number.isSafeInteger(session.capacity) && Number(session.capacity) > 0))
    || !Number.isSafeInteger(session.bookedCount) || Number(session.bookedCount) < 0) {
    throw new ApiError(0, UNEXPECTED_RESPONSE)
  }
  return session as CoachSessionRecord
}

function parseRosterRecord(value: unknown): CoachRosterRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(0, UNEXPECTED_RESPONSE)
  const item = value as Partial<CoachRosterRecord>
  if (!Number.isSafeInteger(item.bookingId) || Number(item.bookingId) <= 0
    || !isNullableString(item.memberCode) || !isNullableString(item.memberName) || !isNullableString(item.memberPhone)
    || !isNullablePositiveId(item.packageRegistrationId) || !isNullablePositiveId(item.packageId)
    || !isNullableString(item.packageName) || !isNullableString(item.bookedAt)) {
    throw new ApiError(0, UNEXPECTED_RESPONSE)
  }
  return item as CoachRosterRecord
}

function asRecords<T>(value: unknown, parse: (item: unknown) => T): T[] {
  if (!Array.isArray(value)) throw new ApiError(0, UNEXPECTED_RESPONSE)
  return value.map(parse)
}

async function list(from: string, to: string, signal?: AbortSignal): Promise<CoachSessionRecord[]> {
  const dayCount = (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000 + 1
  if (!isValidDate(from) || !isValidDate(to) || from > to || dayCount > 62) {
    throw new ApiError(0, 'Choose a valid teaching week.')
  }
  return asRecords(await api.get<unknown>('/coach/sessions', { query: { from, to }, signal }), parseCoachSession)
}

async function roster(sessionId: number, signal?: AbortSignal): Promise<CoachRosterRecord[]> {
  if (!Number.isSafeInteger(sessionId) || sessionId <= 0) throw new ApiError(0, 'Choose a valid session.')
  return asRecords(await api.get<unknown>(`/coach/sessions/${sessionId}/roster`, { signal }), parseRosterRecord)
}

export const coachScheduleApi = { list, roster }
