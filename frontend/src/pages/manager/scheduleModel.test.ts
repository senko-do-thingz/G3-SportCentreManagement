import { describe, expect, it } from 'vitest'
import { addDays, canManageSession, centerNow, monday, repeatDates, validDate } from './scheduleModel'
import type { ManagerSession } from '../../api/managerSessionsApi'
describe('Vietnam schedule date model', () => {
  it('normalizes weeks without browser timezone conversions', () => {
    expect(monday('2026-10-18')).toBe('2026-10-12')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(validDate('2026-02-30')).toBe(false)
    expect(centerNow(new Date('2026-10-16T18:00:00Z'))).toEqual({ date: '2026-10-17', time: '01:00:00' })
  })
  it('counts inclusive weekly repetitions and caps oversized previews', () => {
    expect(repeatDates('2026-10-19', '2026-10-26', ['MONDAY'])).toEqual(['2026-10-19', '2026-10-26'])
    expect(repeatDates('2026-10-19', '2026-10-20', [])).toEqual([])
    expect(repeatDates('2026-01-01', '2030-01-01', ['MONDAY'])).toHaveLength(101)
  })
  it('only enables future draft or published sessions', () => {
    const session = { date: '2026-10-16', startTime: '10:00:00', status: 'PUBLISHED' } as ManagerSession
    expect(canManageSession(session, new Date('2026-10-16T02:00:00Z'))).toBe(true)
    expect(canManageSession(session, new Date('2026-10-16T03:00:00Z'))).toBe(false)
    expect(canManageSession({ ...session, status: 'CANCELLED' }, new Date('2026-10-16T02:00:00Z'))).toBe(false)
  })
  it('anchors weekly session fixtures to Monday across all weekdays including Sunday', () => {
    const sampleDates = [
      { day: 'Monday', iso: '2026-10-12T12:00:00+07:00', expectedDate: '2026-10-12' },
      { day: 'Wednesday', iso: '2026-10-14T12:00:00+07:00', expectedDate: '2026-10-14' },
      { day: 'Saturday', iso: '2026-10-17T12:00:00+07:00', expectedDate: '2026-10-17' },
      { day: 'Sunday', iso: '2026-10-18T12:00:00+07:00', expectedDate: '2026-10-18' },
    ]
    for (const { iso, expectedDate } of sampleDates) {
      const center = centerNow(new Date(iso))
      expect(center.date).toBe(expectedDate)
      const fixtureMonday = addDays(monday(center.date), 7)
      const fixtureTuesday = addDays(fixtureMonday, 1)
      const displayedWeekStart = monday(fixtureMonday)
      const displayedWeekEnd = addDays(displayedWeekStart, 6)
      expect(displayedWeekStart).toBe(fixtureMonday)
      expect(fixtureMonday >= displayedWeekStart && fixtureMonday <= displayedWeekEnd).toBe(true)
      expect(fixtureTuesday >= displayedWeekStart && fixtureTuesday <= displayedWeekEnd).toBe(true)
    }
  })
})
