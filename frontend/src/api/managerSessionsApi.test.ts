import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ApiError } from './errors'
import { managerSessionsApi, scheduleConflicts } from './managerSessionsApi'
import { api } from './client'

vi.mock('./client', () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
const conflict = { sessionId: 7, date: '2026-10-20', requestedDate: '2026-10-20', startTime: '09:00:00', endTime: '10:00:00', conflictType: 'BOTH', class: null, coach: null, facility: null }
describe('manager session contracts', () => {
  it('preserves all exact conflicts and tolerates missing resource descriptions', () => {
    expect(scheduleConflicts(new ApiError(409, 'Conflict', {}, null, { conflicts: [conflict, { ...conflict, sessionId: 8 }] }))).toHaveLength(2)
  })
  it('does not treat generic errors or malformed details as scheduling conflicts', () => {
    for (const error of [new Error('oops'), new ApiError(409, 'Other conflict'), new ApiError(400, 'Bad request', {}, null, { conflicts: [conflict] }), new ApiError(409, 'Conflict', {}, null, { conflicts: [null, {}, { ...conflict, sessionId: -1 }, { ...conflict, conflictType: 'UNKNOWN' }] })]) {
      expect(scheduleConflicts(error)).toEqual([])
    }
  })
  it('uses manager routes and preserves weekly fields without inventing a status', async () => {
    const body = { fromDate: '2026-10-20', toDate: '2026-10-27', daysOfWeek: ['TUESDAY' as const], startTime: '09:00:00', endTime: '10:00:00', facilityId: 1, capacity: 10, trainingType: 'SELF_TRAINING' as const }
    await managerSessionsApi.create(4, body)
    expect(api.post).toHaveBeenCalledWith('/manager/classes/4/sessions', body)
    await managerSessionsApi.cancel(7, '  Maintenance  ')
    expect(api.put).toHaveBeenCalledWith('/manager/sessions/7/cancel', { reason: 'Maintenance' })
  })
})
