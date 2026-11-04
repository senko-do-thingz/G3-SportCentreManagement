/**
 * Shared presentation: money "1.350.000 VND" and center-based dates "04/07/2027 17:00".
 */

const MONEY_FORMAT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
export { formatDate, formatTime, formatDateTime, parseDate } from './dateDisplay'

/** Placeholder shown when a value is missing or invalid. */
export const EMPTY_VALUE = '-'

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
