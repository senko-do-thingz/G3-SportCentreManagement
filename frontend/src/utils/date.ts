/** Date helpers shared by every screen. */
import { isCalendarDate } from '../api/responseValidation'

/** A stored calendar date never changes with the browser timezone. */
export function formatCalendarDate(value: string | null | undefined): string {
  return isCalendarDate(value) ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : '-'
}

/** Stored session LocalTime fields already belong to the center calendar. */
export function formatClockTime(value: string | null | undefined): string {
  return value && /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d(\.\d{1,9})?)?$/.test(value) ? value.slice(0, 5) : '-'
}

/** Centre business day, matching the backend ClockConfig regardless of browser timezone. */
export function businessDate(moment = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(moment)
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)!.value
  return `${get('year')}-${get('month')}-${get('day')}`
}

/** Local calendar date of a moment as an ISO date, for example "2026-10-13". */
export function toIsoDate(moment: Date): string {
  const year = moment.getFullYear()
  const month = String(moment.getMonth() + 1).padStart(2, '0')
  const day = String(moment.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
