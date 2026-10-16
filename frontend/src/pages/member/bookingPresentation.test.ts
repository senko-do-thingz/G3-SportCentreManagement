import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/errors'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import type { SessionDetail } from '../../api/memberSessionsApi'
import { bookingCalendarHref, bookingDate, bookingErrorMessage, bookingTime, cancellationDeadline, eligibleRegistrations } from './bookingPresentation'

const session: SessionDetail = {
  session: { id: 5, sportId: 3, sportName: 'Basketball', classId: 7, className: 'Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00', capacity: 20, bookedCount: 3, freeSeats: 17, full: false, status: 'PUBLISHED' },
  memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: true, reason: null,
  suitableRegistrations: [{ registrationId: 11, registrationCode: 'REG-11', packageId: 4, packageName: 'Basketball package', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-01', remainingSessions: 5 }],
}
const booking: MemberBookingRecord = {
  id: 21, bookingCode: 'BK-1021', sessionId: 5, sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00', sportName: 'Basketball', sportId: 3, classId: 7, className: 'Foundations, Level 1', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MB-002', memberFullName: 'Taylor Nguyen', packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', status: 'CONFIRMED', bookedAt: null, cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}

describe('FE-09 booking presentation helpers', () => {
  it('lists only registrations that match the sport, format, available credits and session dates', () => {
    const mismatched = { ...session.suitableRegistrations[0], registrationId: 12, sportId: 9 }
    const noCredits = { ...session.suitableRegistrations[0], registrationId: 13, remainingSessions: 0 }
    expect(eligibleRegistrations({ ...session, suitableRegistrations: [...session.suitableRegistrations, mismatched, noCredits] }).map((row) => row.registrationId)).toEqual([11])
    expect(eligibleRegistrations({ ...session, alreadyBooked: true })).toEqual([])
    expect(eligibleRegistrations({ ...session, suitableRegistrations: [{ ...session.suitableRegistrations[0], endDate: '2026-11-04' }] })).toEqual([])
  })

  it('formats session dates, times and the two-hour Vietnam-time cancellation cutoff', () => {
    expect(bookingDate('2026-11-05')).toBe('05/11/2026')
    expect(bookingDate('bad')).toBe('Date unavailable')
    expect(bookingTime('17:05:00')).toBe('17:05')
    expect(bookingTime(null)).toBe('Time unavailable')
    expect(cancellationDeadline(booking)).toBe('05/11/2026, 15:00')
  })

  it('creates a Vietnam-time calendar file with escaped event text', () => {
    const href = bookingCalendarHref(booking)
    expect(href).toContain('data:text/calendar')
    const contents = decodeURIComponent(href!.split(',')[1])
    expect(contents).toContain('DTSTART:20261105T100000Z')
    expect(contents).toContain('DTEND:20261105T110000Z')
    expect(contents).toContain('SUMMARY:Foundations\\, Level 1')
    expect(bookingCalendarHref({ ...booking, startTime: null })).toBeNull()
  })

  it.each([
    ['ALREADY_BOOKED', 'You already have a booking'],
    ['FULL', 'session has just filled up'],
    ['CANCELLATION_CUTOFF', 'deadline has passed'],
  ])('maps backend %s conflicts to actionable member copy', (code, message) => {
    expect(bookingErrorMessage(new ApiError(409, 'Backend detail', {}, code))).toContain(message)
  })
})
