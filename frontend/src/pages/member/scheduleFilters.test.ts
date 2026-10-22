import { describe, expect, it } from 'vitest'
import { browseFilters, browseRangeError, safeBrowseReturn, updateBrowseParams } from './scheduleFilters'
import { browseAddDays, browseIsoWeek, browseMonday, browseMondayFromIsoWeek, browseToday, sessionMinutes, validBrowseDate } from './memberScheduleDates'
describe('member browse query and dates', () => {
  it('parses supported filters and safe defaults', () => {
    expect(browseFilters(new URLSearchParams('sportId=3&format=COACH_LED&level=BEGINNER&coachId=4&page=2'), '2026-10-16')).toMatchObject({ sportId: 3, coachId: 4, format: 'COACH_LED', level: 'BEGINNER', page: 2, from: '2026-10-16', to: '2026-11-15' })
    expect(browseFilters(new URLSearchParams('sportId=-1&page=999999999999&format=bad&level=bad&coachId=NaN'))).toMatchObject({ sportId: undefined, page: 0, format: undefined, level: undefined, coachId: undefined })
  })
  it('resets pagination and removes coach filter for self-training while preserving unrelated query keys', () => {
    const params = updateBrowseParams(new URLSearchParams('page=3&coachId=4&level=BEGINNER&previewState=slow'), 'format', 'SELF_TRAINING')
    expect(params.has('page')).toBe(false)
    expect(params.has('coachId')).toBe(false)
    expect(params.get('level')).toBe('BEGINNER')
    expect(params.get('previewState')).toBe('slow')
  })
  it('validates dates and keeps date-only arithmetic independent of browser timezone', () => {
    expect(validBrowseDate('2026-02-30')).toBe(false)
    expect(validBrowseDate('0000-01-01')).toBe(false)
    expect(browseRangeError('2026-10-20', '2026-10-19')).toMatch(/From date/)
    expect(browseMonday('2026-10-18')).toBe('2026-10-12')
    expect(browseIsoWeek('2026-10-16')).toBe('2026-W42')
    expect(browseMondayFromIsoWeek('2026-W42')).toBe('2026-10-12')
    expect(browseIsoWeek('2021-01-01')).toBe('2020-W53')
    expect(browseMondayFromIsoWeek('2021-W53')).toBeNull()
    expect(browseMondayFromIsoWeek('2026-W00')).toBeNull()
    expect(browseMondayFromIsoWeek('not-a-week')).toBeNull()
    expect(browseAddDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(browseToday(new Date('2026-10-16T18:00:00Z'))).toBe('2026-10-17')
    expect(sessionMinutes('09:00:00', '10:30:00')).toBe('90 minutes')
    expect(sessionMinutes('bad', '10:00')).toBe('Not provided')
  })
  it('rejects unsafe return targets', () => {
    for (const target of ['https://evil.test', '//evil.test/member/classes', '/\\evil.test', '/member/payments', '/member/classes\n']) expect(safeBrowseReturn(target)).toBe('/member/classes')
    expect(safeBrowseReturn('/member/schedule?date=2026-10-16')).toBe('/member/schedule?date=2026-10-16')
  })
})
