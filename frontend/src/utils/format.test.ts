import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatMoney, formatTime, humanizeCode, initials, parseDate } from './format'

describe('format', () => {
  it('formats money like the Figma screens', () => {
    expect(formatMoney(80000)).toBe('80.000 VND')
    expect(formatMoney(1350000)).toBe('1.350.000 VND')
    expect(formatMoney('2400000.00')).toBe('2.400.000 VND')
    expect(formatMoney(0)).toBe('0 VND')
    expect(formatMoney(null)).toBe('-')
    expect(formatMoney('abc')).toBe('-')
  })

  it('formats LocalDate values without a time zone shift', () => {
    expect(formatDate('2027-07-04')).toBe('04 Jul 2027')
    expect(parseDate('2026-01-01')?.getDate()).toBe(1)
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate('not a date')).toBe('-')
  })

  it('formats LocalDateTime values', () => {
    expect(formatDateTime('2026-10-05T17:00:00')).toBe('05 Oct 2026, 17:00')
    expect(formatTime('2026-10-05T18:30:00')).toBe('18:30')
  })

  it('turns backend codes into labels', () => {
    expect(humanizeCode('PENDING_PAYMENT')).toBe('Pending payment')
    expect(humanizeCode('ACTIVE')).toBe('Active')
    expect(humanizeCode('')).toBe('-')
  })

  it('builds initials for the avatar', () => {
    expect(initials('Taylor Nguyen')).toBe('TN')
    expect(initials('Nguyen Tan Phat')).toBe('NP')
    expect(initials('Morgan')).toBe('M')
    expect(initials('')).toBe('?')
  })
})
