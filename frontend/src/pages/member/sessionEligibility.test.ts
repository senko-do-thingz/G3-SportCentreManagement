import { describe, expect, it } from 'vitest'
import { resolveSessionDetailState } from './sessionEligibility'
import type { EligibilityReason, SessionDetail } from '../../api/memberSessionsApi'
const detail = { memberContext: true, alreadyBooked: false, eligible: true, reason: null, session: { sportId: 3, trainingFormat: 'COACH_LED', full: false, freeSeats: 5 }, suitableRegistrations: [{ registrationId: 1, sportId: 3, trainingFormat: 'COACH_LED', remainingSessions: 2 }] } as SessionDetail
describe('member eligibility resolver', () => {
  it.each<EligibilityReason>(['FULL', 'NO_PACKAGE', 'NO_SESSIONS_LEFT', 'PACKAGE_EXPIRED_BY_THEN', 'PACKAGE_NOT_STARTED_BY_THEN', 'CAPACITY_UNAVAILABLE', 'ALREADY_BOOKED'])('preserves backend reason %s', (reason) => expect(resolveSessionDetailState({ ...detail, eligible: false, reason })).toBe(reason))
  it('only enables eligibility with a usable member snapshot', () => {
    expect(resolveSessionDetailState(detail)).toBe('ELIGIBLE')
    for (const item of [{ ...detail, memberContext: false }, { ...detail, eligible: null }, { ...detail, suitableRegistrations: [] }, { ...detail, reason: 'NEW_REASON' as EligibilityReason }, { ...detail, session: { ...detail.session, full: true } }]) expect(resolveSessionDetailState(item)).toBe('UNKNOWN')
  })
  it('already booked takes precedence over full', () => expect(resolveSessionDetailState({ ...detail, alreadyBooked: true, reason: 'FULL', eligible: false })).toBe('ALREADY_BOOKED'))
})
