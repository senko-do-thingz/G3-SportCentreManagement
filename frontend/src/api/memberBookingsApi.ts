import { api } from './client'
import { ApiError } from './errors'

export type BookingWhen = 'UPCOMING' | 'PAST'
export type MemberBookingStatus = 'CONFIRMED' | 'CANCELLED'

/** Mirrors BookingResponse returned by POST/DELETE /bookings and GET /bookings/my. */
export interface MemberBookingRecord {
  id: number
  bookingCode: string
  sessionId: number
  sessionDate: string
  startTime: string | null
  endTime: string | null
  sportName: string | null
  sportId: number | null
  classId: number | null
  className: string | null
  coachId: number | null
  coachName: string | null
  facilityId: number | null
  facilityName: string | null
  trainingType: string | null
  memberId: number | null
  memberCode: string | null
  memberFullName: string | null
  packageRegistrationId: number | null
  packageId: number | null
  packageName: string | null
  status: MemberBookingStatus
  bookedAt: string | null
  cancelledAt: string | null
  cancelReason: string | null
  cancelledById: number | null
  cancelledByName: string | null
  cancelledByRole: string | null
}

export interface CreateMemberBookingRequest {
  sessionId: number
  packageRegistrationId: number
}

const UNEXPECTED_BOOKING_RESPONSE = 'The server returned an unexpected booking response. Please try again later.'

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

export function parseMemberBooking(value: unknown): MemberBookingRecord {
  if (!value || typeof value !== 'object') throw new ApiError(0, UNEXPECTED_BOOKING_RESPONSE)
  const booking = value as Partial<MemberBookingRecord>
  if (!Number.isSafeInteger(booking.id) || (booking.id ?? 0) <= 0
    || !Number.isSafeInteger(booking.sessionId) || (booking.sessionId ?? 0) <= 0
    || typeof booking.bookingCode !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(booking.sessionDate ?? '')
    || !['CONFIRMED', 'CANCELLED'].includes(booking.status ?? '')
    || !isNullableString(booking.startTime) || !isNullableString(booking.endTime)) {
    throw new ApiError(0, UNEXPECTED_BOOKING_RESPONSE)
  }
  return booking as MemberBookingRecord
}

export function parseMemberBookings(value: unknown): MemberBookingRecord[] {
  if (!Array.isArray(value)) throw new ApiError(0, UNEXPECTED_BOOKING_RESPONSE)
  return value.map(parseMemberBooking)
}

async function my(when: BookingWhen, signal?: AbortSignal): Promise<MemberBookingRecord[]> {
  return parseMemberBookings(await api.get<unknown>('/bookings/my', { query: { when }, signal }))
}

async function create(request: CreateMemberBookingRequest): Promise<MemberBookingRecord> {
  const booking = parseMemberBooking(await api.post<unknown>('/bookings', request))
  if (booking.status !== 'CONFIRMED') throw new ApiError(0, 'The server did not confirm this booking.')
  return booking
}

async function cancel(id: number): Promise<MemberBookingRecord> {
  if (!Number.isSafeInteger(id) || id <= 0) throw new ApiError(0, 'Choose a valid booking.')
  return parseMemberBooking(await api.delete<unknown>(`/bookings/${id}`))
}

async function findMine(id: number, memberId: number, signal?: AbortSignal): Promise<MemberBookingRecord | null> {
  if (!Number.isSafeInteger(id) || id <= 0 || !Number.isSafeInteger(memberId) || memberId <= 0) {
    throw new ApiError(0, 'Choose a valid member and booking.')
  }
  const [upcoming, past] = await Promise.all([my('UPCOMING', signal), my('PAST', signal)])
  return [...upcoming, ...past].find((booking) => booking.id === id && booking.memberId === memberId) ?? null
}

async function findUpcomingForSession(sessionId: number, memberId: number, signal?: AbortSignal): Promise<MemberBookingRecord | null> {
  if (!Number.isSafeInteger(sessionId) || sessionId <= 0 || !Number.isSafeInteger(memberId) || memberId <= 0) {
    throw new ApiError(0, 'Choose a valid member and session.')
  }
  const upcoming = await my('UPCOMING', signal)
  return upcoming.find((booking) => booking.memberId === memberId && booking.sessionId === sessionId && booking.status === 'CONFIRMED') ?? null
}

export const memberBookingsApi = { my, create, cancel, findMine, findUpcomingForSession }
