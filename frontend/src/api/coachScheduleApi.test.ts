import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './errors'
import { api } from './client'
import { coachScheduleApi, type CoachSessionRecord } from './coachScheduleApi'

vi.mock('./client', () => ({ api: { get: vi.fn() } }))

const session: CoachSessionRecord = {
  id: 5, classId: 7, className: 'Basketball Foundations', sportId: 3, sportName: 'Basketball', coachId: 8,
  coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', sessionDate: '2026-10-19',
  startTime: '17:00:00', endTime: '18:00:00', capacity: 20, bookedCount: 12, status: 'PUBLISHED',
}

beforeEach(() => vi.clearAllMocks())

describe('coach schedule API', () => {
  it('loads the owner-scoped schedule with the inclusive date bounds and no coach override', async () => {
    vi.mocked(api.get).mockResolvedValueOnce([session])
    const signal = new AbortController().signal
    await expect(coachScheduleApi.list('2026-10-19', '2026-10-25', signal)).resolves.toEqual([session])
    expect(api.get).toHaveBeenCalledWith('/coach/sessions', { query: { from: '2026-10-19', to: '2026-10-25' }, signal })
  })

  it('validates the date range, actual dates and 62-day backend limit before requesting', async () => {
    for (const [from, to] of [
      ['2026-02-30', '2026-03-01'], ['2026-10-02', '2026-10-01'], ['2026-01-01', '2026-03-04'],
    ]) {
      await expect(coachScheduleApi.list(from, to)).rejects.toBeInstanceOf(ApiError)
    }
    expect(api.get).not.toHaveBeenCalled()
  })

  it('loads the confirmed roster from the selected own-session endpoint', async () => {
    const roster = [{ bookingId: 22, memberCode: 'MB-002', memberName: 'Taylor Nguyen', memberPhone: '555-0199', packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', bookedAt: '2026-10-17T10:00:00' }]
    vi.mocked(api.get).mockResolvedValueOnce(roster)
    await expect(coachScheduleApi.roster(5)).resolves.toEqual(roster)
    expect(api.get).toHaveBeenCalledWith('/coach/sessions/5/roster', { signal: undefined })
  })

  it('accepts nullable historical values and rejects malformed schedule or roster rows', async () => {
    vi.mocked(api.get).mockResolvedValueOnce([{ ...session, classId: null, className: null, facilityId: null, facilityName: null, capacity: null }])
    await expect(coachScheduleApi.list('2026-10-19', '2026-10-25')).resolves.toHaveLength(1)
    vi.mocked(api.get).mockResolvedValueOnce([{ ...session, sessionDate: '2026-02-30' }])
    await expect(coachScheduleApi.list('2026-10-19', '2026-10-25')).rejects.toThrow('unexpected coach schedule response')
    vi.mocked(api.get).mockResolvedValueOnce([{ bookingId: 0 }])
    await expect(coachScheduleApi.roster(5)).rejects.toThrow('unexpected coach schedule response')
  })

  it('rejects invalid session IDs without requesting a roster', async () => {
    await expect(coachScheduleApi.roster(0)).rejects.toBeInstanceOf(ApiError)
    expect(api.get).not.toHaveBeenCalled()
  })
})
