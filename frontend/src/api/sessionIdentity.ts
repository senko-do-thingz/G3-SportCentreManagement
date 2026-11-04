import { ApiError } from './errors'
import { isCalendarDate } from './responseValidation'

declare const publicSessionId: unique symbol
/** Persisted business identity. Numeric legacy session/class/booking keys cannot satisfy it. */
export type SessionId = string & { readonly [publicSessionId]: true }
export type SessionSurrogateId = number
export type BookingId = number
export type ClassId = number

export function parseSessionId(value: unknown): SessionId {
  if (typeof value !== 'string' || !/^[0-9]{6}(FB|SW|BM|VB|BK|TE)(0[1-9]|[1-9][0-9])$/.test(value)
    || !isCalendarDate(`20${value.slice(4, 6)}-${value.slice(2, 4)}-${value.slice(0, 2)}`)) {
    throw new ApiError(0, 'Enter a valid 10-character Session ID, for example 031126FB01.')
  }
  return value as SessionId
}

/** Older responses may omit the additive field; never fabricate a business ID from other fields. */
export function optionalSessionId(value: unknown): SessionId | undefined {
  return value == null ? undefined : parseSessionId(value)
}

export function sessionIdText(value: string | null | undefined): string {
  try { return parseSessionId(value) } catch { return 'Session ID unavailable' }
}

/** Accept old numeric bookmarks without treating their surrogate as a public Session ID. */
export function parseSessionReference(value: string | null | undefined): SessionId | number | null {
  if (value && /^[1-9][0-9]*$/.test(value)) {
    const id = Number(value)
    return Number.isSafeInteger(id) ? id : null
  }
  try { return parseSessionId(value) } catch { return null }
}

export function sessionRouteReference(session: { id: number; sessionId?: string | null }): string {
  try { return parseSessionId(session.sessionId) } catch { return String(session.id) }
}
