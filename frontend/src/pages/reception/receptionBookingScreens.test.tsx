import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const RECEPTIONIST = { id: 9, email: 'reception@sportify.demo', fullName: 'Jamie Tran', phone: '0901234599', role: 'RECEPTIONIST', status: 'ACTIVE' }
const MEMBER = { userId: 2, memberCode: 'MB-0248', fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', currentLevel: 'BEGINNER', status: 'ACTIVE' }

const booking: MemberBookingRecord = {
  id: 21, bookingCode: 'BK-1021', sessionId: 5, sessionDate: '2026-10-20', startTime: '17:00:00', endTime: '18:00:00',
  sportName: 'Basketball', sportId: 3, classId: 7, className: 'Basketball Foundations', coachId: 8, coachName: 'Quan Coach',
  facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MB-0248', memberFullName: 'Taylor Nguyen',
  packageRegistrationId: 11, packageId: 4, packageName: 'Basketball package', status: 'CONFIRMED', bookedAt: '2026-10-17T10:00:00',
  cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}

const session = {
  id: 5, sportId: 3, sportName: 'Basketball', classId: 7, className: 'Basketball Foundations', trainingFormat: 'COACH_LED',
  level: 'BEGINNER', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', sessionDate: '2026-10-20',
  startTime: '17:00:00', endTime: '18:00:00', capacity: 20, bookedCount: 5, freeSeats: 15, full: false, status: 'PUBLISHED',
}

const eligibility = {
  session, memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: true, reason: null,
  suitableRegistrations: [{ registrationId: 11, registrationCode: 'REG-0011', packageId: 4, packageName: 'Basketball package', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-01', remainingSessions: 6 }],
}

function setup(handler?: (call: RecordedCall) => Response | undefined) {
  tokenStorage.startSession({ accessToken: 'reception-access', refreshToken: 'reception-refresh' })
  let currentBooking = booking
  const calls = installFakeFetch((call) => {
    const override = handler?.(call)
    if (override) return override
    const url = new URL(`http://api.test${call.path}`)
    if (call.path === '/users/me') return json(200, RECEPTIONIST)
    if (url.pathname === '/members' && call.method === 'GET') return json(200, { content: [MEMBER], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    if (url.pathname === '/members/2/overview') return json(200, { member: MEMBER, cards: [], registrations: [] })
    if (url.pathname === '/bookings/member/2') return json(200, { content: [currentBooking], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    if (url.pathname === '/bookings/21' && call.method === 'DELETE') {
      currentBooking = { ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00', cancelledByName: 'Jamie Tran', cancelledByRole: 'RECEPTIONIST' }
      return json(200, currentBooking)
    }
    if (url.pathname === '/sessions' && call.method === 'GET') return json(200, { content: [session], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    if (url.pathname === '/sessions/5/eligibility') return json(200, eligibility)
    if (url.pathname === '/bookings' && call.method === 'POST') return json(201, booking)
    return backendError(404, `Unexpected test request: ${call.method} ${call.path}`)
  })
  return calls
}

afterEach(() => vi.unstubAllGlobals())

describe('FE-10 receptionist booking screens', () => {
  it('searches for a member, shows their booking history and confirms cancellation once', async () => {
    const user = userEvent.setup()
    const { calls } = setup()
    renderApp('/reception/bookings?q=Taylor')

    await user.click(await screen.findByRole('button', { name: /select Taylor Nguyen/i }))
    expect(await screen.findByRole('heading', { name: 'Member bookings' })).toBeInTheDocument()
    expect(screen.getByText('BK-1021')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=20')).toBe(true)

    await user.click(screen.getByRole('button', { name: 'Cancel booking' }))
    const dialog = screen.getByRole('dialog', { name: 'Cancel member booking' })
    await user.click(within(dialog).getByRole('button', { name: 'Confirm cancellation' }))
    await waitFor(() => expect(calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(1))
    expect(await screen.findByText(/is now cancelled/)).toBeInTheDocument()
  })

  it('shows an empty booking history and keeps backend read failures retryable', async () => {
    const empty = setup((call) => call.path.startsWith('/bookings/member/2')
      ? json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) : undefined)
    renderApp('/reception/bookings?memberId=2&q=Taylor')
    expect(await screen.findByText('No upcoming bookings')).toBeInTheDocument()
    expect(empty.calls.some((call) => call.path.includes('/bookings/member/2'))).toBe(true)
  })

  it('shows backend booking history errors and retries the read', async () => {
    let reads = 0
    const { calls } = setup((call) => {
      if (!call.path.startsWith('/bookings/member/2')) return undefined
      reads += 1
      return reads === 1 ? backendError(503, 'Booking history is temporarily unavailable')
        : json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    })
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2&q=Taylor')
    expect(await screen.findByText('Booking history is temporarily unavailable')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No upcoming bookings')).toBeInTheDocument()
    expect(calls.filter((call) => call.path.startsWith('/bookings/member/2'))).toHaveLength(2)
  })

  it('creates a booking with explicit member and eligible package, then shows the confirmation', async () => {
    const user = userEvent.setup()
    const { calls } = setup()
    renderApp('/reception/bookings/new?memberId=2&q=Taylor&week=2026-10-19')

    const sessionChoice = await screen.findByRole('radio', { name: /Basketball Foundations/ })
    await user.click(sessionChoice)
    expect(await screen.findByText(/A suitable paid package is available/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm booking' }))

    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(screen.getByText(/BK-1021/)).toBeInTheDocument()
    const create = calls.find((call) => call.method === 'POST' && call.path === '/bookings')
    expect(create?.body).toEqual({ memberId: 2, sessionId: 5, packageRegistrationId: 11 })
    expect(calls.some((call) => call.path === '/sessions/5/eligibility?memberId=2')).toBe(true)
    expect(screen.getByText(/No notification delivery is reported/)).toBeInTheDocument()
  })

  it('does not submit a booking when eligibility is denied', async () => {
    const { calls } = setup((call) => call.path === '/sessions/5/eligibility?memberId=2'
      ? json(200, { ...eligibility, eligible: false, reason: 'NO_PACKAGE', suitableRegistrations: [] }) : undefined)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&q=Taylor&week=2026-10-19')
    await user.click(await screen.findByRole('radio', { name: /Basketball Foundations/ }))
    expect(await screen.findByText(/No paid sport package covers/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled()
    expect(calls.every((call) => call.method !== 'POST' || call.path !== '/bookings')).toBe(true)
  })
})
