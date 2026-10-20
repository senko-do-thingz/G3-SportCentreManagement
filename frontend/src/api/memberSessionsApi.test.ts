import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { memberSessionsApi } from './memberSessionsApi'
import { ApiError } from './errors'
vi.mock('./client', () => ({ api: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
const session = (id: number) => ({ id, sportId: 3, sportName: 'Basketball', classId: null, className: null, trainingFormat: 'COACH_LED', level: null, coachId: null, coachName: null, facilityId: null, facilityName: null, sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', capacity: 20, bookedCount: 0, freeSeats: 20, full: false, status: 'PUBLISHED' })
describe('member session read API', () => {
  it('loads every calendar page and does not use manager endpoints', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [session(1)], page: 0, size: 1, totalElements: 2, totalPages: 2 }).mockResolvedValueOnce({ content: [session(2)], page: 1, size: 1, totalElements: 2, totalPages: 2 })
    expect(await memberSessionsApi.all({ from: '2026-10-16', to: '2026-10-22' })).toHaveLength(2)
    expect(api.get).toHaveBeenLastCalledWith('/sessions', expect.objectContaining({ query: expect.objectContaining({ page: 1, size: 100 }) }))
  })
  it('does not return partial results when a later page fails', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [session(1)], page: 0, size: 1, totalElements: 2, totalPages: 2 }).mockRejectedValueOnce(new Error('Unavailable'))
    await expect(memberSessionsApi.all({})).rejects.toThrow('Unavailable')
  })
  it('gets eligibility from detail and propagates abort signals', async () => {
    const signal = new AbortController().signal
    vi.mocked(api.get).mockResolvedValueOnce({ session: { ...session(5), sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: false, bookingStatus: null, alreadyBooked: null, eligible: null, reason: null, suitableRegistrations: [] })
    await memberSessionsApi.detail(5, signal)
    expect(api.get).toHaveBeenCalledWith('/sessions/5', { signal })
  })
  it('checks eligibility for the selected member and rejects a non-member response', async () => {
    const signal = new AbortController().signal
    vi.mocked(api.get).mockResolvedValueOnce({ session: { ...session(5), sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: false, reason: 'NO_PACKAGE', suitableRegistrations: [] })
    await expect(memberSessionsApi.eligibilityForMember(5, 8, signal)).resolves.toMatchObject({ memberContext: true, eligible: false })
    expect(api.get).toHaveBeenCalledWith('/sessions/5/eligibility', { query: { memberId: 8 }, signal })
    vi.mocked(api.get).mockResolvedValueOnce({ session: { ...session(5), sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 }, memberContext: false, bookingStatus: null, alreadyBooked: null, eligible: null, reason: null, suitableRegistrations: [] })
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
    vi.mocked(api.get).mockResolvedValueOnce({ content: [session(1)], page: 0, size: 1, totalElements: 2, totalPages: 2 }).mockResolvedValueOnce({ content: [session(1)], page: 1, size: 1, totalElements: 2, totalPages: 2 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('schedule changed')
  })
  it('rejects oversized ranges instead of fetching an unbounded number of pages', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: Array.from({ length: 100 }, (_, i) => session(i + 1)), page: 0, size: 100, totalElements: 10001, totalPages: 101 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('more than 10,000 sessions')
    expect(api.get).toHaveBeenCalledTimes(1)
  })
  it('rejects pagination metadata that changes while loading', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [session(1)], page: 0, size: 1, totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [session(2)], page: 1, size: 1, totalElements: 3, totalPages: 3 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('schedule changed')
  })
})

describe('DEF-002 session list and detail validation', () => {
  const page = (content: unknown[], extra = {}) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra })
  it.each([null, 'invalid', [], {}, { content: null }, page([null]), page([42]), page([{}]), page([{ ...session(1), id: -1 }]), page([{ ...session(1), sportId: '3' }]), page([{ ...session(1), sessionDate: '2026-02-30' }]), page([{ ...session(1), startTime: '25:00' }]), page([{ ...session(1), endTime: '08:00' }]), page([{ ...session(1), status: 'UNKNOWN' }]), page([{ ...session(1), trainingFormat: 'UNKNOWN' }]), page([{ ...session(1), freeSeats: 99 }]), page([session(1)], { totalPages: 2 }), page([session(1)], { totalElements: 21 }), page([session(1)], { page: -1 }), page([session(1)], { number: 2 }), page([session(1)], { size: 0 }), page([session(1)], { totalElements: '1' }), page([session(1), session(1)])])('rejects invalid envelope/row %j without fake empty results', async (payload) => {
    vi.mocked(api.get).mockResolvedValueOnce(payload)
    await expect(memberSessionsApi.list({})).rejects.toThrow()
  })
  it.each(['sportId', 'sportName', 'trainingFormat', 'sessionDate', 'startTime', 'endTime', 'capacity', 'bookedCount', 'freeSeats', 'full', 'status'])('rejects missing mandatory session %s in list and detail', async (key) => {
    const row: Record<string, unknown> = { ...session(1) }
    delete row[key]
    vi.mocked(api.get).mockResolvedValueOnce(page([row])).mockResolvedValueOnce({ session: row })
    await expect(memberSessionsApi.list({})).rejects.toThrow('Session details could not be read')
    await expect(memberSessionsApi.detail(1)).rejects.toThrow('Session details could not be read')
  })
  it('accepts valid empty Spring pages and preserves optional coach/facility/class data', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 }).mockResolvedValueOnce(page([session(1)]))
    await expect(memberSessionsApi.list({})).resolves.toEqual({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    await expect(memberSessionsApi.list({})).resolves.toMatchObject({ content: [{ coachId: null, facilityId: null, className: null }] })
  })
  it('accepts a genuinely full first page, final page and out-of-range empty page', async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page([session(1)], { size: 1, totalElements: 2, totalPages: 2 })).mockResolvedValueOnce(page([session(2)], { page: 1, size: 1, totalElements: 2, totalPages: 2 })).mockResolvedValueOnce(page([], { page: 2, totalElements: 1, totalPages: 1 }))
    await expect(memberSessionsApi.list({ page: 0, size: 1 })).resolves.toMatchObject({ page: 0, totalPages: 2 })
    await expect(memberSessionsApi.list({ page: 1, size: 1 })).resolves.toMatchObject({ page: 1, content: [{ id: 2 }] })
    await expect(memberSessionsApi.list({ page: 2 })).resolves.toMatchObject({ content: [], totalElements: 1 })
  })
  it('accepts backend fractional LocalTime values and legacy free-text level', async () => {
    vi.mocked(api.get).mockResolvedValueOnce(page([{ ...session(1), startTime: '09:00:00.123456789', endTime: '10:00:00', level: 'Legacy level' }]))
    await expect(memberSessionsApi.list({})).resolves.toMatchObject({ content: [{ id: 1 }] })
  })
  it.each(['trainingFormat', 'status'])('rejects array values masquerading as enum %s', async (key) => {
    const row = session(1)
    vi.mocked(api.get).mockResolvedValueOnce(page([{ ...row, [key]: [row[key as keyof typeof row]] }]))
    await expect(memberSessionsApi.list({})).rejects.toThrow('Session details could not be read')
  })
  it.each([0, 400, 401, 403, 404, 409, 500])('preserves HTTP/network error %s and succeeds after retry', async (status) => {
    const failure = new ApiError(status, 'Synthetic response failure')
    vi.mocked(api.get).mockRejectedValueOnce(failure).mockResolvedValueOnce(page([session(1)]))
    await expect(memberSessionsApi.list({})).rejects.toBe(failure)
    await expect(memberSessionsApi.list({})).resolves.toMatchObject({ totalElements: 1 })
  })
})
