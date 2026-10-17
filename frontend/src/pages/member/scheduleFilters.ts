import type { BrowseFormat, BrowseLevel, SessionQuery } from '../../api/memberSessionsApi'
import { browseAddDays, browseToday, validBrowseDate } from './memberScheduleDates'

export const BROWSE_FORMATS = [{ value: 'COACH_LED', label: 'Coach-led' }, { value: 'SELF_TRAINING', label: 'Self-training' }]
export const BROWSE_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'].map((value) => ({ value, label: value[0] + value.slice(1).toLowerCase() }))
export function browseId(value: string | null): number | undefined {
  return value && /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : undefined
}
export function browseFilters(params: URLSearchParams, today = browseToday()): SessionQuery {
  const from = params.get('from') ?? today
  const to = params.get('to') ?? browseAddDays(today, 30)
  const pageValue = params.get('page') ?? '0'
  const page = /^\d+$/.test(pageValue) && Number(pageValue) <= Math.floor(2147483647 / 20) ? Number(pageValue) : 0
  const format = BROWSE_FORMATS.some((entry) => entry.value === params.get('format')) ? params.get('format') as BrowseFormat : undefined
  return {
    sportId: browseId(params.get('sportId')), from, to, format,
    level: BROWSE_LEVELS.some((entry) => entry.value === params.get('level')) ? params.get('level') as BrowseLevel : undefined,
    coachId: format === 'SELF_TRAINING' ? undefined : browseId(params.get('coachId')), page, size: 20,
  }
}
export function browseRangeError(from: string, to: string): string | null {
  if (!validBrowseDate(from) || !validBrowseDate(to)) return 'Choose valid from and to dates.'
  return from > to ? 'From date must not be after to date.' : null
}
export function updateBrowseParams(params: URLSearchParams, key: string, value: string): URLSearchParams {
  const next = new URLSearchParams(params)
  if (value) next.set(key, value); else next.delete(key)
  if (key !== 'page') next.delete('page')
  if (key === 'format' && value === 'SELF_TRAINING') next.delete('coachId')
  return next
}
export function safeBrowseReturn(value: string | null): string {
  if (!value || value.includes('\\') || [...value].some((char) => char.charCodeAt(0) < 32)) return '/member/classes'
  try {
    const url = new URL(value, 'https://sportify.invalid')
    if (!value.startsWith('/') || url.origin !== 'https://sportify.invalid' || !['/member/classes', '/member/schedule'].includes(url.pathname)) return '/member/classes'
    return url.pathname + url.search
  } catch { return '/member/classes' }
}
