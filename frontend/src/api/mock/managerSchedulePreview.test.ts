import { describe, expect, it } from 'vitest'
import { schedulePreviewResponse } from './managerSchedulePreview'
import { addDays, centerNow } from '../../pages/manager/scheduleModel'
import type { ManagerSession } from '../managerSessionsApi'

describe('isolated manager schedule preview', () => {
  it('creates sessions and rejects an overlapping batch without partial writes', async () => {
    const date = addDays(centerNow().date, 14)
    const body = { date, startTime: '13:00:00', endTime: '14:00:00', facilityId: 1, coachId: 3, capacity: 10, trainingType: 'COACH_LED' }
    const first = schedulePreviewResponse('/manager/classes/1/sessions', { method: 'POST', body }, [], false)!
    expect(first.status).toBe(201)
    const created = await first.json() as ManagerSession[]
    const overlap = schedulePreviewResponse('/manager/classes/1/sessions', { method: 'POST', body }, [], false)!
    expect(overlap.status).toBe(409)
    const error = await overlap.json()
    expect(error.details.conflicts[0]).toMatchObject({ sessionId: created[0].id, date, conflictType: 'BOTH' })
    const listed = await schedulePreviewResponse('/manager/sessions', { query: { from: date, to: date } }, [], false)!.json()
    expect(listed).toHaveLength(1)
  })
})
