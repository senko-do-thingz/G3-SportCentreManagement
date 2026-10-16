import { EMPTY_VALUE, parseDate } from '../../utils/format'

const calendar = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' })
const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Ho_Chi_Minh' })

/** Offset-free server timestamps are center-local; offset-bearing timestamps are converted to the center timezone. */
export function formatManagerDateTime(value: string | null | undefined): string {
  if (!value) return EMPTY_VALUE
  const centerValue = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value) ? `${value}+07:00` : value
  const date = parseDate(centerValue)
  return date ? `${calendar.format(date)}, ${clock.format(date)}` : EMPTY_VALUE
}
