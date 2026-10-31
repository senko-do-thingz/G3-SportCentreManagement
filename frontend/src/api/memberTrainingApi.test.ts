import { describe, expect, it } from 'vitest'
import { ApiError } from './errors'
import {
  parseAttendanceRecord,
  parseCoachNote,
  parseTrainingPlan,
  parseTrainingResult,
} from './memberTrainingApi'

const session = { sessionId: 9, sessionDate: '2026-10-20', startTime: '09:00:00', endTime: '10:00:00', sportName: 'Basketball', className: 'Foundations', coachName: 'Daniel', facilityName: 'Court A' }

describe('member training parsers (NX-14, NX-15)', () => {
  it('keeps only allowlisted plan fields and drops peer or staff-only data', () => {
    const plan = parseTrainingPlan({
      id: 1, title: 'Plan', audience: 'CLASS', revision: 2, steps: [{ order: 2, title: 'B', details: null }, { order: 1, title: 'A', details: 'x' }],
      recipients: [{ memberId: 77, fullName: 'Other Member' }], privateNotes: 'staff only', memberId: 77,
    })
    expect(JSON.stringify(plan)).not.toMatch(/Other Member|staff only|recipients|memberId/)
    expect(plan.steps.map((step) => step.title)).toEqual(['A', 'B'])
  })

  it('drops peer fields from attendance, results and notes', () => {
    const attendance = parseAttendanceRecord({ id: 1, ...session, state: 'ATTENDED', arrivedAt: '2026-10-20T08:55:00', memberId: 5, memberFullName: 'Peer' })
    const result = parseTrainingResult({ id: 2, ...session, metrics: [], privateComment: 'secret', memberFullName: 'Peer' })
    const note = parseCoachNote({ id: 3, comment: 'Hi', staffOnlyReason: 'secret', memberId: 5 })
    expect(JSON.stringify([attendance, result, note])).not.toMatch(/Peer|secret|memberId/)
  })

  it('keeps UNRECORDED apart from ABSENT and ignores an arrival time unless attended', () => {
    expect(parseAttendanceRecord({ id: 1, ...session, state: 'UNRECORDED', arrivedAt: '2026-10-20T08:55:00' })).toMatchObject({ state: 'UNRECORDED', arrivedAt: null })
    expect(parseAttendanceRecord({ id: 2, ...session, state: 'ABSENT' }).state).toBe('ABSENT')
    expect(() => parseAttendanceRecord({ id: 3, ...session, state: 'PRESENT' })).toThrow(ApiError)
  })

  it('keeps a recorded zero apart from a missing metric', () => {
    const result = parseTrainingResult({ id: 1, ...session, metrics: [{ label: 'Turnovers', value: 0 }, { label: 'Sprint', value: null }, { label: 'Reps' }] })
    expect(result.metrics.map((metric) => metric.value)).toEqual([0, null, null])
  })

  it('rejects malformed rows with the generic training message', () => {
    expect(() => parseTrainingPlan({ id: 0, title: 'x', audience: 'CLASS', revision: 1, steps: [] })).toThrow(ApiError)
    expect(() => parseTrainingPlan({ id: 1, title: 'x', audience: 'PEERS', revision: 1, steps: [] })).toThrow(ApiError)
    expect(() => parseTrainingResult({ id: 1, ...session, sessionDate: '20/10/2026' })).toThrow(ApiError)
    expect(() => parseCoachNote({ id: 1, comment: '   ' })).toThrow(ApiError)
    expect(() => parseTrainingResult({ id: 1, ...session, metrics: [{ label: 'x', value: '5' }] })).toThrow(ApiError)
  })
})
