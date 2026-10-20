import { describe, expect, it } from 'vitest'
import { businessDate, toIsoDate } from './date'
import { validateStartDate } from '../pages/member/packagesModel'

describe('DEF-003 Vietnam business calendar', () => {
  it.each([
    ['2026-10-19T18:00:00Z', '2026-10-20'],
    ['2026-10-19T16:59:59Z', '2026-10-19'],
    ['2026-10-19T17:00:00Z', '2026-10-20'],
    ['2026-01-31T16:59:59Z', '2026-01-31'],
    ['2026-01-31T17:00:00Z', '2026-02-01'],
    ['2026-12-31T16:59:59Z', '2026-12-31'],
    ['2026-12-31T17:00:00Z', '2027-01-01'],
    ['2028-02-28T17:00:00Z', '2028-02-29'],
  ])('%s maps to business day %s', (instant, expected) => {
    expect(businessDate(new Date(instant))).toBe(expected)
  })
  it('keeps the documented local calendar helper unchanged', () => {
    expect(toIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })
  it('accepts business today/tomorrow and rejects the previous day', () => {
    const today = businessDate(new Date('2026-10-19T18:00:00Z'))
    expect(validateStartDate('2026-10-20', today)).toBeNull()
    expect(validateStartDate('2026-10-21', today)).toBeNull()
    expect(validateStartDate('2026-10-19', today)).toMatch(/past/)
  })
})
