import { isCalendarDate } from '../api/responseValidation'

export const CENTER_TIME_ZONE = 'Asia/Ho_Chi_Minh'
type DateValue = string | number | Date | null | undefined
const EMPTY = '-'
const LOCAL_TIME = /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d(\.\d{1,9})?)?$/
const TIMESTAMP = /^(\d{4}-\d{2}-\d{2})T([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?(Z|[+-](?:0\d|1[0-4]):[0-5]\d)?$/
const calendar = new Intl.DateTimeFormat('en-GB', { timeZone: CENTER_TIME_ZONE, day: '2-digit', month: '2-digit', year: 'numeric', era: 'short' })
const clock = new Intl.DateTimeFormat('en-GB', { timeZone: CENTER_TIME_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
const offsetFormat = new Intl.DateTimeFormat('en-GB', { timeZone: CENTER_TIME_ZONE, timeZoneName: 'longOffset' })
const wallFormat = new Intl.DateTimeFormat('en-GB', { timeZone: CENTER_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })

function displayCalendar(date: Date): string {
  const parts = calendar.formatToParts(date)
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find(part => part.type === type)?.value
  const year = get('year')
  return get('era') === 'AD' && year && Number(year) >= 1 && Number(year) <= 9999
    ? `${get('day')}/${get('month')}/${year.padStart(4, '0')}` : EMPTY
}

function centerInstant(value: string): Date | null {
  const wall = new Date(`${value}Z`)
  let instant = wall
  // Resolve the zone offset at the resulting instant as well as at the initial wall value.
  for (let attempt = 0; attempt < 2; attempt++) {
    const offset = offsetFormat.formatToParts(instant).find(part => part.type === 'timeZoneName')?.value
    const match = /^GMT([+-])(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(offset ?? '')
    if (!match) return null
    const seconds = (Number(match[2]) * 3600 + Number(match[3]) * 60 + Number(match[4] ?? 0)) * (match[1] === '+' ? 1 : -1)
    instant = new Date(wall.getTime() - seconds * 1000)
  }
  const parts = wallFormat.formatToParts(instant)
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find(part => part.type === type)?.value
  const actual = `${get('year')?.padStart(4, '0')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}:${get('second')}`
  const expected = value.length === 16 ? `${value}:00` : value.slice(0, 19)
  return actual === expected ? instant : null
}

/** Date-only storage is a calendar value, never a browser-local instant. */
export function formatCalendarDate(value: string | null | undefined): string {
  return isCalendarDate(value) ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : EMPTY
}

/** A session LocalTime already belongs to the center calendar. */
export function formatClockTime(value: string | null | undefined): string {
  return value && LOCAL_TIME.test(value) ? value.slice(0, 5) : EMPTY
}

/** Strict ISO parsing. Offset-free server timestamps are center-local; numbers are epoch milliseconds. */
export function parseDate(value: DateValue): Date | null {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date || typeof value === 'number') {
    const date = new Date(value instanceof Date ? value.getTime() : value)
    return Number.isFinite(date.getTime()) && date.getUTCFullYear() >= 1 && date.getUTCFullYear() <= 9999 ? date : null
  }
  if (isCalendarDate(value)) return centerInstant(`${value}T00:00:00`)
  const match = TIMESTAMP.exec(value)
  if (!match || !isCalendarDate(match[1])) return null
  const offset = match[3]
  if (offset && /^[+-]14:(?!00)/.test(offset)) return null
  if (!offset) return centerInstant(value)
  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? date : null
}

export function formatDate(value: DateValue): string {
  if (typeof value === 'string' && isCalendarDate(value)) return formatCalendarDate(value)
  const date = parseDate(value)
  return date ? displayCalendar(date) : EMPTY
}

export function formatTime(value: DateValue): string {
  if (typeof value === 'string') {
    if (LOCAL_TIME.test(value)) return formatClockTime(value)
    if (isCalendarDate(value)) return EMPTY
  }
  const date = parseDate(value)
  return date ? clock.format(date) : EMPTY
}

/** A date-only value cannot establish a time and is not expanded into an invented midnight. */
export function formatDateTime(value: DateValue): string {
  if (typeof value === 'string' && isCalendarDate(value)) return EMPTY
  const date = parseDate(value)
  return date && displayCalendar(date) !== EMPTY ? `${displayCalendar(date)} ${clock.format(date)}` : EMPTY
}

/** Revenue buckets represent a whole month, not a fabricated day or instant. */
export function formatMonthRange(value: string): string {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(value) || value.startsWith('0000')) return EMPTY
  const first = `${value}-01`
  const next = new Date(`${first}T00:00:00Z`)
  next.setUTCMonth(next.getUTCMonth() + 1, 0)
  return `${formatCalendarDate(first)} - ${formatCalendarDate(next.toISOString().slice(0, 10))}`
}

export function formatWeekRange(value: string): string {
  const match = /^(\d{4})-W(0[1-9]|[1-4]\d|5[0-3])$/.exec(value)
  if (!match || match[1] === '0000') return EMPTY
  const start = new Date(`${match[1]}-01-04T00:00:00Z`)
  start.setUTCDate(start.getUTCDate() - (start.getUTCDay() + 6) % 7 + 7 * (Number(match[2]) - 1))
  const thursday = new Date(start)
  thursday.setUTCDate(start.getUTCDate() + 3)
  if (thursday.getUTCFullYear() !== Number(match[1])) return EMPTY
  const end = new Date(start)
  end.setUTCDate(start.getUTCDate() + 6)
  return `${formatCalendarDate(start.toISOString().slice(0, 10))} - ${formatCalendarDate(end.toISOString().slice(0, 10))}`
}
