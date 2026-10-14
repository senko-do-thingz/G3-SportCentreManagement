/**
 * Display formatters shared by every screen. Formats follow the Figma screens:
 * money "1.350.000 VND", date "04 Jul 2027", date and time "04 Jul 2027, 17:00".
 */

const MONEY_FORMAT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false })

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/** Placeholder shown when a value is missing. */
export const EMPTY_VALUE = '-'

/**
 * Parses an ISO date or date time from the backend. A date only value such as "2027-07-04"
 * (Java LocalDate) is read as a local date, so it is never shifted by the time zone.
 */
export function parseDate(value: string | number | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (typeof value === 'string') {
    const match = DATE_ONLY.exec(value)
    if (match) {
      return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
    }
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 1350000 becomes "1.350.000 VND". Accepts numbers or numeric strings (BigDecimal). */
export function formatMoney(amount: number | string | null | undefined, currency = 'VND'): string {
  if (amount === null || amount === undefined || amount === '') {
    return EMPTY_VALUE
  }
  const value = typeof amount === 'number' ? amount : Number(amount)
  if (!Number.isFinite(value)) {
    return EMPTY_VALUE
  }
  return `${MONEY_FORMAT.format(Math.round(value))} ${currency}`
}

/** "2027-07-04" becomes "04 Jul 2027". */
export function formatDate(value: string | number | Date | null | undefined): string {
  const date = parseDate(value)
  return date ? DATE_FORMAT.format(date) : EMPTY_VALUE
}

/** "2026-10-05T17:00:00" becomes "17:00". */
export function formatTime(value: string | number | Date | null | undefined): string {
  const date = parseDate(value)
  return date ? TIME_FORMAT.format(date) : EMPTY_VALUE
}

/** "2026-10-05T17:00:00" becomes "05 Oct 2026, 17:00". */
export function formatDateTime(value: string | number | Date | null | undefined): string {
  const date = parseDate(value)
  return date ? `${DATE_FORMAT.format(date)}, ${TIME_FORMAT.format(date)}` : EMPTY_VALUE
}

/** "PENDING_PAYMENT" becomes "Pending payment". */
export function humanizeCode(code: string | null | undefined): string {
  if (!code) {
    return EMPTY_VALUE
  }
  const words = code.toLowerCase().split(/[_\s]+/).filter(Boolean)
  if (words.length === 0) {
    return EMPTY_VALUE
  }
  const sentence = words.join(' ')
  return sentence.charAt(0).toUpperCase() + sentence.slice(1)
}

/** "Taylor Nguyen" becomes "TN". */
export function initials(name: string | null | undefined): string {
  if (!name) {
    return '?'
  }
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) {
    return '?'
  }
  const first = parts[0].charAt(0)
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : ''
  return (first + last).toUpperCase()
}
