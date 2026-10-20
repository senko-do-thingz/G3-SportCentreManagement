import { businessDate } from '../../utils/date'

export function validBrowseDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && value.slice(0, 4) !== '0000' && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}
export function browseToday(now = new Date()): string {
  return businessDate(now)
}
export function browseAddDays(date: string, count: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + count)
  if (!Number.isFinite(value.getTime()) || value.getUTCFullYear() < 1 || value.getUTCFullYear() > 9999) return date
  return value.toISOString().slice(0, 10)
}
export function browseMonday(date: string): string { return browseAddDays(date, -((new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7)) }
export function browseDate(date: string): string { return validBrowseDate(date) ? date.split('-').reverse().join('/') : 'Date unavailable' }
export function browseDay(date: string): string { return new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`)) }
export function sessionMinutes(start: string, end: string): string {
  const seconds = (time: string) => {
    if (!/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(time)) return NaN
    const [hours, minutes, seconds = 0] = time.split(':').map(Number)
    return hours * 3600 + minutes * 60 + seconds
  }
  const duration = (seconds(end) - seconds(start)) / 60
  return Number.isFinite(duration) && duration > 0 ? `${Number(duration.toFixed(1))} minutes` : 'Not provided'
}
