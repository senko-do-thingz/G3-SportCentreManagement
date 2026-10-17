import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import type { SessionDetail } from '../../api/memberSessionsApi'
import { tokenStorage } from '../../api/tokenStorage'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'

const detail: SessionDetail = {
  session: { id: 5, sportId: 3, sportName: 'Basketball', classId: 7, className: 'Basketball Foundations', trainingFormat: 'COACH_LED', level: 'BEGINNER', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00', capacity: 20, bookedCount: 3, freeSeats: 17, full: false, status: 'PUBLISHED' },
  memberContext: true, bookingStatus: null, alreadyBooked: false, eligible: true, reason: null,
  suitableRegistrations: [
    { registrationId: 11, registrationCode: 'REG-11', packageId: 4, packageName: 'Basketball 10 sessions', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-01', remainingSessions: 5 },
    { registrationId: 12, registrationCode: 'REG-12', packageId: 5, packageName: 'Basketball 20 sessions', sportId: 3, trainingFormat: 'COACH_LED', startDate: '2026-10-01', endDate: '2026-12-01', remainingSessions: 12 },
  ],
}
const booking: MemberBookingRecord = {
  id: 21, bookingCode: 'BK-1021', sessionId: 5, sessionDate: '2026-11-05', startTime: '17:00:00', endTime: '18:00:00', sportName: 'Basketball', sportId: 3, classId: 7, className: 'Basketball Foundations', coachId: 8, coachName: 'Quan Coach', facilityId: 2, facilityName: 'Court A', trainingType: 'COACH_LED', memberId: 2, memberCode: 'MB-002', memberFullName: 'Taylor Nguyen', packageRegistrationId: 11, packageId: 4, packageName: 'Basketball 10 sessions', status: 'CONFIRMED', bookedAt: '2026-10-17T10:00:00', cancelledAt: null, cancelReason: null, cancelledById: null, cancelledByName: null, cancelledByRole: null,
}
const cancelled = { ...booking, status: 'CANCELLED' as const, cancelledAt: '2026-10-17T10:30:00', cancelledByName: 'Taylor Nguyen', cancelledByRole: 'MEMBER' }
const registration = { id: 11, registrationCode: 'REG-11', memberId: 2, packageId: 4, packageCode: 'PKG-4', packageName: 'Basketball 10 sessions', sportName: 'Basketball', trainingFormat: 'COACH_LED', channel: 'ONLINE', originalPrice: 1000000, discountPercentage: 0, paidAmount: 1000000, totalSessions: 10, remainingSessions: 3, startDate: '2026-10-01', endDate: '2026-12-01', status: 'ACTIVE', paymentId: null, paymentCode: null, activatedAt: null, createdAt: null }
const OTHER_MEMBER = { ...MEMBER, id: 3, email: 'other-member@sportify.demo', fullName: 'Alex Other' }

function api(handler: Parameters<typeof installFakeFetch>[0], profile = MEMBER) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, profile)
    return handler(call)
  })
}

beforeEach(() => {
  tokenStorage.startSession({ accessToken: 'member-test', refreshToken: 'refresh-test' })
})
afterEach(() => {
  tokenStorage.clear()
})

describe('FE-09 member booking screens', () => {
  it('shows matching registrations and hides coach detail for self-training options', async () => {
    api((call) => call.path === '/sessions/5'
      ? json(200, { ...detail, session: { ...detail.session, trainingFormat: 'SELF_TRAINING', coachName: 'Hidden Coach' }, suitableRegistrations: detail.suitableRegistrations.map((row) => ({ ...row, trainingFormat: 'SELF_TRAINING' })) })
      : backendError(404, 'Unexpected request'))
    renderApp('/member/bookings/new?sessionId=5&returnTo=%2Fmember%2Fclasses')
    expect(await screen.findByRole('heading', { name: 'Booking options - Self-training' })).toBeInTheDocument()
    expect(screen.getByText('Basketball 10 sessions')).toBeInTheDocument()
    expect(screen.getByText('5 sessions before booking')).toBeInTheDocument()
    expect(screen.queryByText('Hidden Coach')).not.toBeInTheDocument()
    expect(screen.getByText('Self-training sessions do not have a coach assignment.')).toBeInTheDocument()
  })

  it('revalidates the package, posts one booking on rapid double click and shows confirmation and calendar link', async () => {
    const backend = api((call) => {
      if (call.path === '/sessions/5') return json(200, detail)
      if (call.method === 'POST' && call.path === '/bookings') return json(201, booking)
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [])
      if (call.path === '/packages/registrations/my') return json(200, [registration])
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/new?sessionId=5&returnTo=%2Fmember%2Fclasses')
    expect(await screen.findByRole('heading', { name: 'Booking options' })).toBeInTheDocument()
    expect(screen.getByText('5 sessions before booking')).toBeInTheDocument()
    expect(screen.getByText('4 sessions after booking')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Review booking' }))
    expect(await screen.findByRole('heading', { name: 'Review booking' })).toBeInTheDocument()
    expect(screen.getByText('5 before / 4 after')).toBeInTheDocument()
    const confirm = await screen.findByRole('button', { name: 'Confirm booking' })
    await user.dblClick(confirm)
    expect(await screen.findByRole('heading', { name: 'Booking confirmed' })).toBeInTheDocument()
    expect(screen.getByText('BK-1021')).toBeInTheDocument()
    expect(screen.getByText('3', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add to calendar' })).toHaveAttribute('download', 'sportify-BK-1021.ics')
    expect(backend.calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(1)
    expect(backend.calls.find((call) => call.method === 'POST')?.body).toEqual({ sessionId: 5, packageRegistrationId: 11 })
  })

  it('does not submit when the selected registration is no longer eligible', async () => {
    const backend = api((call) => call.path === '/sessions/5'
      ? json(200, { ...detail, eligible: false, reason: 'NO_SESSIONS_LEFT', suitableRegistrations: [] })
      : backendError(404, 'Unexpected request'))
    renderApp('/member/bookings/review?sessionId=5&packageRegistrationId=11')
    expect(await screen.findByText(/Package eligibility changed/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm booking' })).not.toBeInTheDocument()
    expect(backend.calls.some((call) => call.method === 'POST' && call.path === '/bookings')).toBe(false)
  })

  it('shows a useful definite FULL rejection without claiming a booking succeeded', async () => {
    api((call) => {
      if (call.path === '/sessions/5') return json(200, detail)
      if (call.path === '/bookings' && call.method === 'POST') return json(409, { message: 'Conflict', code: 'FULL' })
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/review?sessionId=5&packageRegistrationId=11')
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    expect(await screen.findByText(/This session has just filled up/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking confirmed' })).not.toBeInTheDocument()
  })

  it('reconciles a server error against Upcoming history before declaring the booking confirmed', async () => {
    const backend = api((call) => {
      if (call.path === '/sessions/5') return json(200, detail)
      if (call.path === '/bookings' && call.method === 'POST') return backendError(500, 'Response lost')
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [])
      if (call.path === '/packages/registrations/my') return json(200, [registration])
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/review?sessionId=5&packageRegistrationId=11')
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    expect(await screen.findByRole('heading', { name: 'Booking confirmed' })).toBeInTheDocument()
    expect(screen.getByText('BK-1021')).toBeInTheDocument()
    expect(backend.calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(1)
  })

  it('keeps retry disabled until a failed status lookup succeeds and finds no booking', async () => {
    let upcomingChecks = 0
    let posts = 0
    const backend = api((call) => {
      if (call.path === '/sessions/5') return json(200, detail)
      if (call.path === '/bookings' && call.method === 'POST') {
        posts += 1
        return posts === 1 ? backendError(500, 'Response lost') : json(409, { message: 'Conflict', code: 'FULL' })
      }
      if (call.path === '/bookings/my?when=UPCOMING') {
        upcomingChecks += 1
        return upcomingChecks === 1 ? backendError(503, 'History unavailable') : json(200, [])
      }
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/review?sessionId=5&packageRegistrationId=11')
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    const retry = await screen.findByRole('button', { name: 'Confirm booking' })
    expect(retry).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Check booking status' }))
    const retryBooking = await screen.findByRole('button', { name: 'Retry booking' })
    expect(retryBooking).toBeEnabled()
    await user.click(retryBooking)
    expect(await screen.findByText(/This session has just filled up/)).toBeInTheDocument()
    expect(posts).toBe(2)
    expect(backend.calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(2)
  })

  it('does not enable retry when ALREADY_BOOKED conflicts with a missing history record', async () => {
    const backend = api((call) => {
      if (call.path === '/sessions/5') return json(200, detail)
      if (call.path === '/bookings' && call.method === 'POST') return json(409, { message: 'Conflict', code: 'ALREADY_BOOKED' })
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [])
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/review?sessionId=5&packageRegistrationId=11')
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    expect(await screen.findByText(/reported an existing booking, but it is not visible in Upcoming/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled()
    expect(backend.calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(1)
  })

  it('loads Upcoming and Past from the matching backend filter and shows the cancelling actor', async () => {
    const backend = api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [cancelled])
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings')
    expect(await screen.findByRole('heading', { name: 'Basketball Foundations' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cancel booking' })).toHaveAttribute('href', '/member/bookings/21/cancel')
    await user.click(screen.getByRole('tab', { name: 'Past' }))
    expect(await screen.findByText('Cancelled by Taylor Nguyen')).toBeInTheDocument()
    expect(backend.calls.some((call) => call.path === '/bookings/my?when=UPCOMING')).toBe(true)
    expect(backend.calls.some((call) => call.path === '/bookings/my?when=PAST')).toBe(true)
  })

  it('does not display booking data from navigation state or another member', async () => {
    const outsiderBooking = { ...booking, memberId: MEMBER.id }
    const backend = api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [outsiderBooking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [])
      return backendError(404, 'Unexpected request')
    }, OTHER_MEMBER)
    renderApp('/member/bookings/confirmed/21', { booking, remainingAfter: 0 })
    expect(await screen.findByText('This booking is not in your upcoming list. It may have been cancelled or changed.')).toBeInTheDocument()
    expect(screen.queryByText('BK-1021')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Basketball Foundations' })).not.toBeInTheDocument()
    expect(backend.calls.some((call) => call.path === '/bookings/my?when=UPCOMING')).toBe(true)
  })

  it('does not allow a different member to cancel a booking passed in navigation state', async () => {
    const backend = api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [])
      return backendError(404, 'Unexpected request')
    }, OTHER_MEMBER)
    renderApp('/member/bookings/21/cancel', { booking })
    expect(await screen.findByText('This booking could not be found in your upcoming or past bookings.')).toBeInTheDocument()
    expect(screen.queryByText('BK-1021')).not.toBeInTheDocument()
    expect(backend.calls.some((call) => call.method === 'DELETE')).toBe(false)
  })

  it('does not show a cancellation result from another member navigation state', async () => {
    const backend = api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [])
      if (call.path === '/bookings/my?when=PAST') return json(200, [cancelled])
      return backendError(404, 'Unexpected request')
    }, OTHER_MEMBER)
    renderApp('/member/bookings/21/cancelled', { booking: cancelled })
    expect(await screen.findByText('This booking is not listed as cancelled. Refresh My Bookings to confirm its current status.')).toBeInTheDocument()
    expect(screen.queryByText('BK-1021')).not.toBeInTheDocument()
    expect(backend.calls.some((call) => call.path === '/packages/registrations/my')).toBe(false)
  })

  it('cancels once, displays the two-hour cutoff and shows the confirmed cancelled result', async () => {
    let cancelledOnServer = false
    const backend = api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, cancelledOnServer ? [] : [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, cancelledOnServer ? [cancelled] : [])
      if (call.path === '/bookings/21' && call.method === 'DELETE') {
        cancelledOnServer = true
        return json(200, cancelled)
      }
      if (call.path === '/packages/registrations/my') return json(200, [{ ...registration, remainingSessions: 6 }])
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/21/cancel')
    expect(await screen.findByText(/Cancel by 05\/11\/2026, 15:00 Vietnam time/)).toBeInTheDocument()
    await user.dblClick(screen.getByRole('button', { name: 'Confirm cancellation' }))
    expect(await screen.findByRole('heading', { name: 'Booking cancelled' })).toBeInTheDocument()
    expect(screen.getByText('Cancelled by', { selector: 'dt' })).toBeInTheDocument()
    expect(screen.getByText('Latest package balance: 6 sessions remaining.')).toBeInTheDocument()
    expect(backend.calls.some((call) => call.path === '/packages/registrations/my')).toBe(true)
    expect(backend.calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(1)
  })

  it('keeps booking confirmed when backend rejects cancellation after cutoff', async () => {
    api((call) => {
      if (call.path === '/bookings/my?when=UPCOMING') return json(200, [booking])
      if (call.path === '/bookings/my?when=PAST') return json(200, [])
      if (call.path === '/bookings/21' && call.method === 'DELETE') return json(400, { message: 'Too late', code: 'CANCELLATION_CUTOFF' })
      return backendError(404, 'Unexpected request')
    })
    const user = userEvent.setup()
    renderApp('/member/bookings/21/cancel')
    await user.click(await screen.findByRole('button', { name: 'Confirm cancellation' }))
    expect(await screen.findByText(/cancellation deadline has passed/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking cancelled' })).not.toBeInTheDocument()
  })

  it('guards the booking route from non-members before making booking requests', async () => {
    const backend = api(() => backendError(404, 'Unexpected request'), { ...MANAGER, role: 'MANAGER' })
    renderApp('/member/bookings')
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(backend.calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
