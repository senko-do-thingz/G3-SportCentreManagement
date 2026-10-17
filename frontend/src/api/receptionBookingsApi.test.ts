import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './errors'
import { api } from './client'
import { receptionBookingsApi } from './receptionBookingsApi'

vi.mock('./client', () => ({ api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

const booking = {
  id: 21, bookingCode: 'BK-1021', sessionId: 5, sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00',
  sportName: 'Basketball', sportId: 3, classId: 7, className: 'Basketball Foundations', coachId: 8, coachName: 'Quan Coach',
  facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MB-002', memberFullName: 'Taylor Nguyen',
  packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', status: 'CONFIRMED', bookedAt: '2026-10-17T10:00:00',
  cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}

beforeEach(() => vi.clearAllMocks())

describe('reception booking API', () => {
  it('loads one paged member history with the requested filters', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [booking], page: 1, size: 20, totalElements: 21, totalPages: 2 })
    const signal = new AbortController().signal
    await expect(receptionBookingsApi.forMember({ memberId: 2, when: 'UPCOMING', status: 'CONFIRMED', page: 1, size: 20, signal }))
      .resolves.toMatchObject({ page: 1, totalElements: 21, content: [expect.objectContaining({ id: 21 })] })
    expect(api.get).toHaveBeenCalledWith('/bookings/member/2', {
      query: { when: 'UPCOMING', status: 'CONFIRMED', page: 1, size: 20 }, signal,
    })
  })

  it('accepts Spring page numbering as well as the stable PageResponse shape', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 })
    await expect(receptionBookingsApi.forMember({ memberId: 2, when: 'PAST' })).resolves.toMatchObject({ page: 0, size: 20, content: [] })
    expect(api.get).toHaveBeenCalledWith('/bookings/member/2', {
      query: { when: 'PAST', status: undefined, page: 0, size: 20 }, signal: undefined,
    })
  })

  it('sends the selected member and package explicitly when reception books', async () => {
    vi.mocked(api.post).mockResolvedValueOnce(booking)
    await expect(receptionBookingsApi.createForMember({ memberId: 2, sessionId: 5, packageRegistrationId: 11 }))
      .resolves.toMatchObject({ id: 21 })
    expect(api.post).toHaveBeenCalledWith('/bookings', { memberId: 2, sessionId: 5, packageRegistrationId: 11 })
  })

  it.each([
    { memberId: 0, when: 'UPCOMING' as const },
    { memberId: 2, when: 'OTHER' as 'UPCOMING' },
    { memberId: 2, when: 'PAST' as const, page: -1 },
    { memberId: 2, when: 'PAST' as const, size: 101 },
    { memberId: 2, when: 'PAST' as const, status: 'CHECKED_IN' as 'CONFIRMED' },
  ])('rejects invalid history query %# without issuing a request', async (query) => {
    await expect(receptionBookingsApi.forMember(query)).rejects.toBeInstanceOf(ApiError)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('rejects invalid assisted-booking IDs and malformed history pages', async () => {
    await expect(receptionBookingsApi.createForMember({ memberId: 2, sessionId: 0, packageRegistrationId: 11 })).rejects.toBeInstanceOf(ApiError)
    expect(api.post).not.toHaveBeenCalled()
    vi.mocked(api.get).mockResolvedValueOnce({ content: [booking], page: 0, size: 20, totalElements: 1 })
    await expect(receptionBookingsApi.forMember({ memberId: 2, when: 'UPCOMING' })).rejects.toThrow('unexpected booking response')
  })
})
