import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatCalendarDate, formatDate, formatDateTime, formatDisplayCalendarDate, formatTime, formatMonth, formatMonthRange, formatWeekRange, parseDate, parseDisplayCalendarDate } from './dateDisplay'

afterEach(() => vi.unstubAllEnvs())

describe('ID-05 center calendar display', () => {
  it.each([
    ['2026-11-11', '11/11/2026'],
    ['2026-01-02', '02/01/2026'],
    ['2028-02-29', '29/02/2028'],
  ])('round trips calendar date %s', (iso, display) => {
    expect(formatDisplayCalendarDate(iso)).toBe(display)
    expect(parseDisplayCalendarDate(display)).toBe(iso)
  })
  it.each(['29/02/2027', '31/04/2026', '01/01/0000', '1/01/2026', '01/1/2026', '11/11/26', '11/11/', '', ' 11/11/2026 '])('rejects invalid or partial display date %j', value => {
    expect(parseDisplayCalendarDate(value)).toBeNull()
  })
  it('keeps invalid and blank editable values empty instead of inventing an epoch date', () => {
    expect(formatDisplayCalendarDate('')).toBe('')
    expect(formatDisplayCalendarDate('not-a-date')).toBe('')
    expect(formatDisplayCalendarDate('1970-01-01')).toBe('01/01/1970')
  })
  it.each(['UTC', 'Asia/Tokyo', 'America/Los_Angeles'])('keeps calendar and timestamp semantics in client zone %s', zone => {
    vi.stubEnv('TZ', zone)
    expect(formatDate('2026-01-05')).toBe('05/01/2026')
    expect(formatDateTime('2026-12-31T17:00:00Z')).toBe('01/01/2027 00:00')
    expect(formatDateTime('2026-11-03T00:05:00')).toBe('03/11/2026 00:05')
    expect(formatDateTime('2026-11-03T00:05:00+09:00')).toBe('02/11/2026 22:05')
  })
  it.each(['2026-02-29', '2028-02-30', '1900-02-29', '0000-01-01', '2026-13-01', '2026-00-01', '01/02/2026', '2026-2-01', '2026-01-01 trailing', '', null, undefined])('rejects an invalid or missing calendar %s', value => {
    expect(formatCalendarDate(value)).toBe('-')
    expect(formatDate(value)).toBe('-')
    expect(parseDate(value)).toBeNull()
  })
  it.each(['2026-02-30T10:00:00Z', '2026-01-01T24:00:00Z', '2026-01-01T10:60:00Z', '2026-01-01T10:00:60Z', '2026-01-01T10:00:00+14:01', '2026-01-01T10:00:00+25:00', '2026-01-01 10:00:00', '0'])('rejects invalid or ambiguous timestamp %s', value => {
    expect(formatDateTime(value)).toBe('-')
    expect(formatTime(value)).toBe('-')
  })
  it('handles leap days, nanosecond precision and epoch milliseconds including zero', () => {
    expect(formatDate('2000-02-29')).toBe('29/02/2000')
    expect(formatDateTime('2028-02-28T17:00:00.123456789Z')).toBe('29/02/2028 00:00')
    // The IANA center zone used UTC+08:00 at the Unix epoch. Zero is a valid instant.
    expect(formatDateTime(0)).toBe('01/01/1970 08:00')
    expect(formatTime(new Date(0))).toBe('08:00')
    expect(formatDateTime('1970-01-01T00:00:00')).toBe('01/01/1970 00:00')
    expect(formatDateTime(NaN)).toBe('-')
    expect(formatDateTime(new Date('bad'))).toBe('-')
  })
  it('does not invent a time for date-only storage', () => {
    expect(formatTime('2026-11-03')).toBe('-')
    expect(formatDateTime('2026-11-03')).toBe('-')
    expect(formatTime('09:05:59.1234567')).toBe('09:05')
    expect(formatTime('24:00')).toBe('-')
  })
  it('describes full monthly and ISO weekly periods without changing native values', () => {
    expect(formatMonth('2028-02')).toBe('02/2028')
    expect(formatMonth('2026-13')).toBe('-')
    expect(formatMonthRange('2028-02')).toBe('01/02/2028 - 29/02/2028')
    expect(formatMonthRange('2026-13')).toBe('-')
    expect(formatWeekRange('2020-W53')).toBe('28/12/2020 - 03/01/2021')
    expect(formatWeekRange('2021-W53')).toBe('-')
    expect(formatWeekRange('2026-W45')).toBe('02/11/2026 - 08/11/2026')
  })
})
