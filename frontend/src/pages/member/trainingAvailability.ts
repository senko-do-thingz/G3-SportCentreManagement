/** F5-01 training format and availability. Kept per Member in this browser; recommendations receive them as filters. */
export interface TrainingAvailability { format: '' | 'COACH_LED' | 'SELF_TRAINING'; days: string[]; earliest: string; latest: string }

export const WEEKDAYS = [
  { value: 'MON', label: 'Monday' }, { value: 'TUE', label: 'Tuesday' }, { value: 'WED', label: 'Wednesday' },
  { value: 'THU', label: 'Thursday' }, { value: 'FRI', label: 'Friday' }, { value: 'SAT', label: 'Saturday' }, { value: 'SUN', label: 'Sunday' },
] as const
export const FORMAT_OPTIONS = [
  { value: 'COACH_LED', label: 'Coach-led' },
  { value: 'SELF_TRAINING', label: 'Self-training' },
]

export const TIME_OPTIONS = Array.from({ length: 37 }, (_, i) => {
  const totalMinutes = 5 * 60 + i * 30 // from 05:00 to 23:00
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  const hh = String(hours).padStart(2, '0')
  const mm = String(minutes).padStart(2, '0')
  const value = `${hh}:${mm}`
  const hour12 = hours % 12 === 0 ? 12 : hours % 12
  const period = hours < 12 ? 'AM' : 'PM'
  const label = `${value} (${String(hour12).padStart(2, '0')}:${mm} ${period})`
  return { value, label }
})

export const EMPTY_AVAILABILITY: TrainingAvailability = { format: 'COACH_LED', days: [], earliest: '', latest: '' }
const key = (memberId: number | string) => `sportify.training-availability.${memberId}`
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/

/** Storage can be blocked or cleared; any problem returns the empty value instead of failing the page. */
export function loadAvailability(memberId: number | string | undefined): TrainingAvailability {
  if (memberId === undefined) return EMPTY_AVAILABILITY
  try {
    const raw = JSON.parse(localStorage.getItem(key(memberId)) ?? 'null') as Partial<TrainingAvailability> | null
    if (!raw || typeof raw !== 'object') return EMPTY_AVAILABILITY
    const days = Array.isArray(raw.days) ? raw.days.filter(day => WEEKDAYS.some(item => item.value === day)) : []
    return {
      format: raw.format === 'SELF_TRAINING' ? 'SELF_TRAINING' : 'COACH_LED',
      days,
      earliest: typeof raw.earliest === 'string' && TIME.test(raw.earliest) ? raw.earliest : '',
      latest: typeof raw.latest === 'string' && TIME.test(raw.latest) ? raw.latest : '',
    }
  } catch {
    return EMPTY_AVAILABILITY
  }
}

export function saveAvailability(memberId: number | string | undefined, value: TrainingAvailability): boolean {
  if (memberId === undefined) return false
  try { localStorage.setItem(key(memberId), JSON.stringify(value)); return true } catch { return false }
}

/** Query string for the recommendation page; empty values are left out. */
export function availabilityQuery(value: TrainingAvailability, extra?: { sportId?: number; level?: string; goal?: string }): string {
  const params = new URLSearchParams()
  if (extra?.sportId) params.set('sportId', String(extra.sportId))
  if (extra?.level) params.set('level', extra.level)
  if (extra?.goal) params.set('goal', extra.goal)
  if (value.format) params.set('format', value.format)
  if (value.days.length) params.set('days', value.days.join(','))
  if (value.earliest) params.set('earliest', value.earliest)
  if (value.latest) params.set('latest', value.latest)
  return params.toString()
}
