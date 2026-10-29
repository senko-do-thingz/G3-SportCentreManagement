import type { DayOfWeek, ManagerSession } from '../../api/managerSessionsApi'

export const WEEKDAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
export function validDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}
export function addDays(date: string, count: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + count)
  return value.toISOString().slice(0, 10)
}
export function centerNow(now = new Date()): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(now)
  const get = (type: string) => parts.find((part) => part.type === type)!.value
  return { date: `${get('year')}-${get('month')}-${get('day')}`, time: `${get('hour')}:${get('minute')}:${get('second')}` }
}
export function monday(value: string): string {
  const date = validDate(value) ? value : centerNow().date
  return addDays(date, -((new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7))
}
export function displayDate(value: string): string { return value.split('-').reverse().join('/') }
function compareName(left: string | null | undefined, right: string | null | undefined): number {
  const a = (left ?? '').normalize('NFKC').trim().toLowerCase()
  const b = (right ?? '').normalize('NFKC').trim().toLowerCase()
  return a < b ? -1 : a > b ? 1 : 0
}
/** Stable display order for sessions occupying the same timetable cell. */
export function compareScheduleSessions(a: ManagerSession, b: ManagerSession): number {
  return compareName(a.class?.name, b.class?.name)
    || compareName(a.coach?.name, b.coach?.name)
    || compareName(a.facility?.name, b.facility?.name)
    || a.id - b.id
}
export function repeatDates(from: string, to: string, days: DayOfWeek[]): string[] {
  if (!validDate(from) || !validDate(to) || to < from || !days.length) return []
  const result: string[] = []
  // Stop after the first invalid batch size; do not iterate an arbitrarily large range.
  for (let date = from, visited = 0; date <= to && visited < 36600 && result.length <= 100; date = addDays(date, 1), visited++) {
    const weekday = WEEKDAYS[(new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7]
    if (days.includes(weekday)) result.push(date)
  }
  return result
}
export function canManageSession(session: ManagerSession, now = new Date()): boolean {
  const center = centerNow(now)
  return ['DRAFT', 'PUBLISHED'].includes(session.status) && `${session.date}T${session.startTime.length === 5 ? `${session.startTime}:00` : session.startTime}` > `${center.date}T${center.time}`
}
