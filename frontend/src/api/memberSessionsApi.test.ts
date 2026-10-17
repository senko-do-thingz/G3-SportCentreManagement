import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { memberSessionsApi } from './memberSessionsApi'
vi.mock('./client', () => ({ api: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
describe('member session read API', () => {
  it('loads every calendar page and does not use manager endpoints', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 2, totalPages: 2 }).mockResolvedValueOnce({ content: [{ id: 2 }], page: 1, size: 100, totalElements: 2, totalPages: 2 })
    expect(await memberSessionsApi.all({ from: '2026-10-16', to: '2026-10-22' })).toHaveLength(2)
    expect(api.get).toHaveBeenLastCalledWith('/sessions', expect.objectContaining({ query: expect.objectContaining({ page: 1, size: 100 }) }))
  })
  it('does not return partial results when a later page fails', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 2, totalPages: 2 }).mockRejectedValueOnce(new Error('Unavailable'))
    await expect(memberSessionsApi.all({})).rejects.toThrow('Unavailable')
  })
  it('gets eligibility from detail and propagates abort signals', async () => {
    const signal = new AbortController().signal
    vi.mocked(api.get).mockResolvedValueOnce({ session: { id: 5, sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: false, bookingStatus: null, alreadyBooked: null, eligible: null, reason: null, suitableRegistrations: [] })
    await memberSessionsApi.detail(5, signal)
    expect(api.get).toHaveBeenCalledWith('/sessions/5', { signal })
  })
  it('checks eligibility for the selected member and rejects a non-member response', async () => {
    const signal = new AbortController().signal
    vi.mocked(api.get).mockResolvedValueOnce({ session: { id: 5, sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: false, reason: 'NO_PACKAGE', suitableRegistrations: [] })
    await expect(memberSessionsApi.eligibilityForMember(5, 8, signal)).resolves.toMatchObject({ memberContext: true, eligible: false })
    expect(api.get).toHaveBeenCalledWith('/sessions/5/eligibility', { query: { memberId: 8 }, signal })
    vi.mocked(api.get).mockResolvedValueOnce({ session: { id: 5, sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: false, bookingStatus: null, alreadyBooked: null, eligible: null, reason: null, suitableRegistrations: [] })
    await expect(memberSessionsApi.eligibilityForMember(5, 8)).rejects.toThrow('Member eligibility could not be confirmed')
  })
  it('does not issue eligibility requests for invalid member or session IDs', async () => {
    await expect(memberSessionsApi.eligibilityForMember(0, 8)).rejects.toThrow('valid member and session')
    await expect(memberSessionsApi.eligibilityForMember(5, 0)).rejects.toThrow('valid member and session')
    expect(api.get).not.toHaveBeenCalled()
  })
  it('rejects incomplete detail payloads instead of crashing the screen', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ eligible: true })
    await expect(memberSessionsApi.detail(5)).rejects.toThrow('Session details could not be read')
  })
  it('rejects duplicated page content rather than pretending the calendar is complete', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 2, totalPages: 2 }).mockResolvedValueOnce({ content: [{ id: 1 }], page: 1, size: 100, totalElements: 2, totalPages: 2 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('schedule changed')
  })
  it('rejects oversized ranges instead of fetching an unbounded number of pages', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 10001, totalPages: 101 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('more than 10,000 sessions')
    expect(api.get).toHaveBeenCalledTimes(1)
  })
  it('rejects pagination metadata that changes while loading', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [{ id: 2 }], page: 1, size: 100, totalElements: 3, totalPages: 2 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('schedule changed')
  })
})
