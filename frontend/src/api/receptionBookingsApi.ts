import { api } from './client'
import { ApiError } from './errors'
import { memberBookingsApi, parseMemberBooking, type BookingWhen, type MemberBookingRecord } from './memberBookingsApi'
import { normalizePage } from './page'
import type { PageResponse } from './types'

export type StaffBookingStatus = 'CONFIRMED' | 'CANCELLED'

export interface MemberBookingHistoryQuery {
  memberId: number
  when: BookingWhen
  page?: number
  size?: number
  status?: StaffBookingStatus
  signal?: AbortSignal
}

export interface FindMemberBookingQuery {
  memberId: number
  matches: (booking: MemberBookingRecord) => boolean
  signal?: AbortSignal
}

export interface AssistedBookingRequest {
  memberId: number
  sessionId: number
  packageRegistrationId: number
}

const UNEXPECTED_RESPONSE = 'The server returned an unexpected booking response. Please try again later.'
const LOOKUP_PAGE_SIZE = 100
const LOOKUP_MAX_PAGES = 100
const LOOKUP_LIMIT_MESSAGE = 'This booking history is too large to verify automatically. Refresh the member booking history before taking another action.'

function asBookingPage(value: unknown): PageResponse<MemberBookingRecord> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(0, UNEXPECTED_RESPONSE)
  const page = value as Record<string, unknown>
  const pageIndex = page.page ?? page.number
  if (!Array.isArray(page.content) || !Number.isSafeInteger(pageIndex) || (pageIndex as number) < 0
    || !Number.isSafeInteger(page.size) || (page.size as number) < 1
    || !Number.isSafeInteger(page.totalElements) || (page.totalElements as number) < 0
    || !Number.isSafeInteger(page.totalPages) || (page.totalPages as number) < 0) {
    throw new ApiError(0, UNEXPECTED_RESPONSE)
  }
  const normalized = normalizePage(value as PageResponse<unknown>)
  return { ...normalized, content: normalized.content.map(parseMemberBooking) }
}

async function forMember({ memberId, when, page = 0, size = 20, status, signal }: MemberBookingHistoryQuery): Promise<PageResponse<MemberBookingRecord>> {
  if (!Number.isSafeInteger(memberId) || memberId <= 0) throw new ApiError(0, 'Choose a valid member.')
  if (!['UPCOMING', 'PAST'].includes(when)) throw new ApiError(0, 'Choose a valid booking period.')
  if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100) throw new ApiError(0, 'Choose a valid booking page.')
  if (status !== undefined && !['CONFIRMED', 'CANCELLED'].includes(status)) throw new ApiError(0, 'Choose a valid booking status.')
  return asBookingPage(await api.get<unknown>(`/bookings/member/${memberId}`, { query: { when, status, page, size }, signal }))
}

async function createForMember(request: AssistedBookingRequest): Promise<MemberBookingRecord> {
  if (![request.memberId, request.sessionId, request.packageRegistrationId].every((value) => Number.isSafeInteger(value) && value > 0)) {
    throw new ApiError(0, 'Choose a valid member, session and eligible package.')
  }
  return parseMemberBooking(await api.post<unknown>('/bookings', request))
}

async function findForMember({ memberId, matches, signal }: FindMemberBookingQuery): Promise<MemberBookingRecord | null> {
  if (!Number.isSafeInteger(memberId) || memberId <= 0) throw new ApiError(0, 'Choose a valid member.')

  for (const when of ['UPCOMING', 'PAST'] as const) {
    const firstPage = await forMember({ memberId, when, page: 0, size: LOOKUP_PAGE_SIZE, signal })
    const firstMatch = firstPage.content.find(matches)
    if (firstMatch) return firstMatch
    if (firstPage.totalPages > LOOKUP_MAX_PAGES) throw new ApiError(0, LOOKUP_LIMIT_MESSAGE)

    for (let page = 1; page < firstPage.totalPages; page += 1) {
      const nextPage = await forMember({ memberId, when, page, size: LOOKUP_PAGE_SIZE, signal })
      const match = nextPage.content.find(matches)
      if (match) return match
    }
  }

  return null
}

export const receptionBookingsApi = {
  forMember,
  findForMember,
  createForMember,
  cancel: memberBookingsApi.cancel,
}
