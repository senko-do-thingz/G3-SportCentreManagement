import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './errors'
import { api } from './client'
import { memberBookingsApi, type MemberBookingRecord } from './memberBookingsApi'

vi.mock('./client', () => ({ api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

const booking: MemberBookingRecord = {
  id: 21, bookingCode: 'BK-1021', sessionId: 5, sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00',
  sportName: 'Basketball', sportId: 3, classId: 7, className: 'Basketball Foundations', coachId: 8, coachName: 'Quan Coach',
  facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MB-002', memberFullName: 'Taylor Nguyen',
  packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', status: 'CONFIRMED', bookedAt: '2026-10-17T10:00:00',
  cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}

beforeEach(() => vi.clearAllMocks())

describe('member bookings API', () => {
  it.each(['UPCOMING', 'PAST'] as const)('requests the %s booking list as an array', async (when) => {
    vi.mocked(api.get).mockResolvedValueOnce([booking])
    await expect(memberBookingsApi.my(when)).resolves.toEqual([booking])
    expect(api.get).toHaveBeenCalledWith('/bookings/my', { query: { when }, signal: undefined })
  })

  it('forwards a list request abort signal and rejects non-array responses', async () => {
    const signal = new AbortController().signal
    vi.mocked(api.get).mockResolvedValueOnce([]).mockResolvedValueOnce({ content: [] })
    await expect(memberBookingsApi.my('UPCOMING', signal)).resolves.toEqual([])
    expect(api.get).toHaveBeenLastCalledWith('/bookings/my', { query: { when: 'UPCOMING' }, signal })
    await expect(memberBookingsApi.my('PAST')).rejects.toThrow('unexpected booking response')
  })

  it('posts only the selected session and eligible package registration', async () => {
    vi.mocked(api.post).mockResolvedValueOnce(booking)
    await expect(memberBookingsApi.create({ sessionId: 5, packageRegistrationId: 11 })).resolves.toEqual(booking)
    expect(api.post).toHaveBeenCalledWith('/bookings', { sessionId: 5, packageRegistrationId: 11 })
  })

  it('deletes the selected booking and validates the response', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00', cancelledByName: 'Taylor Nguyen', cancelledByRole: 'MEMBER' })
    await expect(memberBookingsApi.cancel(21)).resolves.toMatchObject({ status: 'CANCELLED', cancelledByRole: 'MEMBER' })
    expect(api.delete).toHaveBeenCalledWith('/bookings/21')
    await expect(memberBookingsApi.cancel(0)).rejects.toBeInstanceOf(ApiError)
  })

  it('rejects malformed booking records instead of rendering them as valid data', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ ...booking, id: 0 })
    await expect(memberBookingsApi.create({ sessionId: 5, packageRegistrationId: 11 })).rejects.toThrow('unexpected booking response')
  })
})
