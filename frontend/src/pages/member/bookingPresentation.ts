import { ApiError } from '../../api/errors'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import type { SessionDetail, SuitableRegistration } from '../../api/memberSessionsApi'

export function eligibleRegistrations(detail: SessionDetail): SuitableRegistration[] {
  if (!detail || detail.memberContext !== true || detail.eligible !== true || detail.alreadyBooked !== false
    || detail.reason !== null || detail.session.full || detail.session.freeSeats <= 0
    || !Array.isArray(detail.suitableRegistrations)) return []

  const { session } = detail
  return detail.suitableRegistrations.filter((registration) => registration
    && Number.isSafeInteger(registration.registrationId) && registration.registrationId > 0
    && registration.sportId === session.sportId
    && registration.trainingFormat === session.trainingFormat
    && registration.remainingSessions > 0
    && registration.startDate <= session.sessionDate
    && registration.endDate >= session.sessionDate)
}

export function bookingDate(value: string): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('/') : 'Date unavailable'
}

export function bookingTime(value: string | null): string {
  return value && /^(?:[01]\d|2[0-3]):[0-5]\d/.test(value) ? value.slice(0, 5) : 'Time unavailable'
}

/** The backend allows member cancellation until two hours before the local session start. */
export function cancellationDeadline(booking: Pick<MemberBookingRecord, 'sessionDate' | 'startTime'>): string | null {
  if (!booking.startTime || !/^\d{4}-\d{2}-\d{2}$/.test(booking.sessionDate) || !/^([01]\d|2[0-3]):[0-5]\d/.test(booking.startTime)) return null
  const [year, month, day] = booking.sessionDate.split('-').map(Number)
  const [hour, minute] = booking.startTime.split(':').map(Number)
  const deadline = new Date(Date.UTC(year, month - 1, day, hour - 2, minute) - 7 * 60 * 60 * 1000)
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(deadline)
}

export function bookingErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : 'The booking could not be completed. Please try again.'
  switch (error.code) {
    case 'ALREADY_BOOKED': return 'You already have a booking for this session. Check My Bookings before trying again.'
    case 'FULL': return 'This session has just filled up. Choose another available session.'
    case 'BOOKING_CHANGED':
    case 'CAPACITY_UNAVAILABLE': return 'Availability changed before the booking was saved. Refresh the session and try again.'
    case 'NO_PACKAGE':
    case 'NO_SESSIONS_LEFT':
    case 'PACKAGE_EXPIRED_BY_THEN':
    case 'PACKAGE_NOT_STARTED_BY_THEN': return 'This package no longer covers the session. Return to package selection and choose an eligible package.'
    case 'SESSION_UNAVAILABLE': return 'This session is no longer available. Browse the schedule for another session.'
    case 'SESSION_STARTED': return 'This session has already started and can no longer be booked.'
    case 'CANCELLATION_CUTOFF': return 'The cancellation deadline has passed. This booking was not cancelled.'
    case 'CANNOT_CANCEL': return 'This booking can no longer be cancelled.'
    default: return error.message || 'The request could not be completed. Please try again.'
  }
}

function calendarStamp(date: string, time: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^([01]\d|2[0-3]):[0-5]\d/.test(time)) return null
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  const value = new Date(Date.UTC(year, month - 1, day, hour - 7, minute))
  return value.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
}

function escapeCalendarText(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/\r?\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;')
}

export function bookingCalendarHref(booking: MemberBookingRecord): string | null {
  const start = booking.startTime && calendarStamp(booking.sessionDate, booking.startTime)
  const end = booking.endTime && calendarStamp(booking.sessionDate, booking.endTime)
  if (!start || !end) return null
  const title = booking.className || `${booking.sportName || 'Sport'} session`
  const location = [booking.facilityName, booking.coachName].filter(Boolean).join(' - ')
  const lines = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Sportify//Member Booking//EN', 'BEGIN:VEVENT',
    `UID:booking-${booking.id}@sportify`, `DTSTAMP:${new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')}`,
    `DTSTART:${start}`, `DTEND:${end}`, `SUMMARY:${escapeCalendarText(title)}`,
    ...(location ? [`LOCATION:${escapeCalendarText(location)}`] : []),
    `DESCRIPTION:${escapeCalendarText(`Booking ${booking.bookingCode}`)}`, 'END:VEVENT', 'END:VCALENDAR', '',
  ]
  return `data:text/calendar;charset=utf-8,${encodeURIComponent(lines.join('\r\n'))}`
}
