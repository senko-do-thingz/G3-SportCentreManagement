import type { EligibilityReason, SessionDetail } from '../../api/memberSessionsApi'

export type DetailState = EligibilityReason | 'ELIGIBLE' | 'UNKNOWN'
const reasons: EligibilityReason[] = ['NO_PACKAGE', 'NO_SESSIONS_LEFT', 'PACKAGE_EXPIRED_BY_THEN', 'PACKAGE_NOT_STARTED_BY_THEN', 'FULL', 'ALREADY_BOOKED', 'CAPACITY_UNAVAILABLE']
export function resolveSessionDetailState(detail: SessionDetail): DetailState {
  if (detail.memberContext !== true) return 'UNKNOWN'
  if (detail.alreadyBooked === true || detail.reason === 'ALREADY_BOOKED') return 'ALREADY_BOOKED'
  if (detail.eligible === false && reasons.includes(detail.reason as EligibilityReason)) return detail.reason as EligibilityReason
  if (detail.eligible === true && detail.reason === null && detail.alreadyBooked === false && detail.session.full === false && detail.session.freeSeats > 0
    && Array.isArray(detail.suitableRegistrations) && detail.suitableRegistrations.some((entry) => Number.isSafeInteger(entry.registrationId) && entry.registrationId > 0 && entry.sportId === detail.session.sportId && entry.trainingFormat === detail.session.trainingFormat && entry.remainingSessions > 0)) return 'ELIGIBLE'
  return 'UNKNOWN'
}
export const ELIGIBILITY_COPY: Record<DetailState, { title: string; message: string }> = {
  ELIGIBLE: { title: 'Session Detail', message: 'Your sport package covers this session. No seat or credit has been reserved.' },
  FULL: { title: 'Session Fully Booked', message: 'This session has no available places. Please select another session.' },
  NO_PACKAGE: { title: 'No Eligible Sport Package', message: 'No paid sport package covers the selected session. A membership card alone does not cover a class booking.' },
  NO_SESSIONS_LEFT: { title: 'No Eligible Sport Package', message: 'Your matching package has no sessions left. Choose a suitable sport package to continue.' },
  PACKAGE_EXPIRED_BY_THEN: { title: 'Package Does Not Cover This Date', message: 'Your matching package expires before this session. Choose another session or a suitable package.' },
  PACKAGE_NOT_STARTED_BY_THEN: { title: 'Package Has Not Started', message: 'Your matching package starts after this session. Choose a later session or a suitable package.' },
  ALREADY_BOOKED: { title: 'Already Booked', message: 'You already have a confirmed booking for this session. No duplicate booking is needed.' },
  CAPACITY_UNAVAILABLE: { title: 'Availability Cannot Be Confirmed', message: 'The booking capacity is temporarily unavailable. Refresh availability or choose another session.' },
  UNKNOWN: { title: 'Eligibility Cannot Be Confirmed', message: 'We cannot safely confirm eligibility for this session. Refresh availability and try again.' },
}
