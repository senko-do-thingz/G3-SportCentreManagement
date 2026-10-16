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
    vi.mocked(api.get).mockResolvedValueOnce({ session: { id: 5, sportName: 'Basketball', sessionDate: '2026-10-16', startTime: '09:00', endTime: '10:00', trainingFormat: 'COACH_LED', capacity: 20, bookedCount: 0, freeSeats: 20 } })
    await memberSessionsApi.detail(5, signal)
    expect(api.get).toHaveBeenCalledWith('/sessions/5', { signal })
  })
  it('rejects incomplete detail payloads instead of crashing the screen', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ eligible: true })
    await expect(memberSessionsApi.detail(5)).rejects.toThrow('Session details could not be read')
  })
  it('rejects duplicated page content rather than pretending the calendar is complete', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ content: [{ id: 1 }], page: 0, size: 100, totalElements: 2, totalPages: 2 }).mockResolvedValueOnce({ content: [{ id: 1 }], page: 1, size: 100, totalElements: 2, totalPages: 2 })
    await expect(memberSessionsApi.all({})).rejects.toThrow('schedule changed')
  })
})
