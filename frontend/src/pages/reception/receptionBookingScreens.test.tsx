import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import type { MemberBookingRecord } from '../../api/memberBookingsApi'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const RECEPTIONIST = { id: 9, email: 'reception@sportify.demo', fullName: 'Jamie Tran', phone: '0901234599', role: 'RECEPTIONIST', status: 'ACTIVE' }
const MEMBER = { userId: 2, memberCode: 'MB-0248', fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', currentLevel: 'BEGINNER', status: 'ACTIVE' }
const OTHER_MEMBER = { userId: 3, memberCode: 'MB-0310', fullName: 'Alex Tran', email: 'alex@sportify.demo', phone: '0901234510', currentLevel: 'INTERMEDIATE', status: 'ACTIVE' }

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

const MEMBER_OVERVIEW = {
  profile: MEMBER,
  currentCard: null,
  pendingCardRequest: null,
  activePackageRegistrations: [],
  pendingPackageRegistrations: [],
  recentCheckIns: [],
}
const OTHER_MEMBER_OVERVIEW = { ...MEMBER_OVERVIEW, profile: OTHER_MEMBER }

type BookingSetter = (booking: MemberBookingRecord | null) => void
type SetupHandler = (call: RecordedCall, setCurrentBooking: BookingSetter) => Response | undefined | Promise<Response | undefined>

function setup(handler?: SetupHandler, initialBooking: MemberBookingRecord | null = booking) {
  tokenStorage.startSession({ accessToken: 'reception-access', refreshToken: 'reception-refresh' })
  let currentBooking = initialBooking
  const calls = installFakeFetch(async (call) => {
    const override = await handler?.(call, (nextBooking) => { currentBooking = nextBooking })
    if (override) return override
    const url = new URL(`http://api.test${call.path}`)
    if (call.path === '/users/me') return json(200, RECEPTIONIST)
    if (url.pathname === '/members' && call.method === 'GET') return json(200, { content: [MEMBER], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    if (url.pathname === '/members/2/overview') return json(200, MEMBER_OVERVIEW)
    if (url.pathname === '/members/3/overview') return json(200, OTHER_MEMBER_OVERVIEW)
    if (url.pathname === '/bookings/member/2' || url.pathname === '/bookings/member/3') {
      const requestedMemberId = Number(url.pathname.split('/')[3])
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 20)
      const memberBooking = currentBooking?.memberId === requestedMemberId ? currentBooking : null
      return json(200, { content: memberBooking ? [memberBooking] : [], page, size, totalElements: memberBooking ? 1 : 0, totalPages: memberBooking ? 1 : 0 })
    }
    if (url.pathname === '/bookings/21' && call.method === 'DELETE') {
      currentBooking = { ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00', cancelledByName: 'Jamie Tran', cancelledByRole: 'RECEPTIONIST' }
      return json(200, currentBooking)
    }
    if (url.pathname === '/sessions' && call.method === 'GET') return json(200, { content: [session], page: 0, size: 100, totalElements: 1, totalPages: 1 })
    if (url.pathname === '/sessions/5/eligibility') return json(200, eligibility)
    if (url.pathname === '/bookings' && call.method === 'POST') {
      currentBooking = booking
      return json(201, booking)
    }
    return backendError(404, `Unexpected test request: ${call.method} ${call.path}`)
  })
  return calls
}

function renderConfirmation(state: unknown, bookingId = booking.id) {
  return render(
    <MemoryRouter initialEntries={[{
      pathname: `/reception/bookings/confirmed/${bookingId}`,
      search: '?memberId=2&q=Taylor',
      state,
    }] }>
      <ToastProvider>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  )
}

function MemberRouteSwitcher({ to }: { to: string }) {
  const navigate = useNavigate()
  return <button type="button" onClick={() => navigate(to)}>Switch member</button>
}

function renderWithMemberSwitch(initialPath: string, nextPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <AuthProvider>
          <MemberRouteSwitcher to={nextPath} />
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('FE-10 receptionist booking screens', () => {
  it('loads a direct member link without a search query or page-one search dependency', async () => {
    const { calls } = setup()
    renderApp('/reception/bookings?memberId=2')

    expect(await screen.findByText('BK-1021')).toBeInTheDocument()
    expect(screen.getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/members/2/overview')).toBe(true)
    expect(calls.some((call) => call.path.startsWith('/members?'))).toBe(false)
  })

  it('keeps member lookup authorization failures visible and does not fabricate a profile', async () => {
    const { calls } = setup((call) => call.path === '/members/2/overview' ? backendError(403, 'Forbidden') : undefined)
    renderApp('/reception/bookings?memberId=2')

    expect(await screen.findByText('Forbidden')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Selected member' })).not.toBeInTheDocument()
    expect(calls.some((call) => call.path === '/members/2/overview')).toBe(true)
  })

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
    expect(within(dialog).getByText(/Package credit restoration depends on the registration's current state/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Confirm cancellation' }))
    await waitFor(() => expect(calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(1))
    expect(await screen.findByText(/is now cancelled/)).toBeInTheDocument()
  })

  it('closes a member A cancellation dialog before member B can act on the same route', async () => {
    const { calls } = setup()
    const user = userEvent.setup()
    renderWithMemberSwitch('/reception/bookings?memberId=2&q=Taylor', '/reception/bookings?memberId=3&q=Alex')

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }))
    expect(screen.getByRole('dialog', { name: 'Cancel member booking' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Switch member' }))

    expect(await screen.findByText('Alex Tran')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Cancel member booking' })).not.toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(0)
  })

  it('does not show member A cancellation results after switching members during a pending DELETE', async () => {
    let completeDelete!: (response: Response) => void
    const pendingDelete = new Promise<Response>((resolve) => { completeDelete = resolve })
    const { calls } = setup((call) => call.method === 'DELETE' && call.path === '/bookings/21' ? pendingDelete : undefined)
    const user = userEvent.setup()
    renderWithMemberSwitch('/reception/bookings?memberId=2&q=Taylor', '/reception/bookings?memberId=3&q=Alex')

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Cancel member booking' })).getByRole('button', { name: 'Confirm cancellation' }))
    await waitFor(() => expect(calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(1))
    await user.click(screen.getByRole('button', { name: 'Switch member' }))
    expect(await screen.findByText('Alex Tran')).toBeInTheDocument()

    await act(async () => {
      completeDelete(json(200, { ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00' }))
      await new Promise((resolve) => setTimeout(resolve, 10))
    })
    expect(screen.queryByRole('dialog', { name: 'Cancel member booking' })).not.toBeInTheDocument()
    expect(screen.queryByText('BK-1021 is now cancelled')).not.toBeInTheDocument()
    expect(screen.queryByText('Booking cancelled')).not.toBeInTheDocument()
  })

  it('reconciles a cancellation whose response was lost after the server committed it', async () => {
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'DELETE' && call.path === '/bookings/21') {
        setCurrentBooking({ ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00' })
        throw new Error('Connection closed after commit')
      }
      return undefined
    })
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2')

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Cancel member booking' })).getByRole('button', { name: 'Confirm cancellation' }))

    expect(await screen.findByText(/BK-1021 is now cancelled/)).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'DELETE' && call.path === '/bookings/21')).toHaveLength(1)
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=100')).toBe(true)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
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

  it('reconciles a booking whose response was lost after the server committed it', async () => {
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'POST' && call.path === '/bookings') {
        setCurrentBooking(booking)
        throw new Error('Connection closed after commit')
      }
      return undefined
    }, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&week=2026-10-19')

    await user.click(await screen.findByRole('radio', { name: /Basketball Foundations/ }))
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))

    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(1)
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=100')).toBe(true)
  })

  it('resets assisted-booking state on member change and ignores member A reconciliation replies', async () => {
    let completeMemberAHistory!: (response: Response) => void
    const pendingMemberAHistory = new Promise<Response>((resolve) => { completeMemberAHistory = resolve })
    let posts = 0
    const memberBBooking = { ...booking, memberId: 3, memberCode: OTHER_MEMBER.memberCode, memberFullName: OTHER_MEMBER.fullName }
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'POST' && call.path === '/bookings') {
        posts += 1
        if (posts === 1) throw new Error('Connection lost after request')
        setCurrentBooking(memberBBooking)
        return json(201, memberBBooking)
      }
      if (call.path.startsWith('/bookings/member/2') && call.path.includes('size=100')) return pendingMemberAHistory
      return undefined
    }, null)
    const user = userEvent.setup()
    renderWithMemberSwitch(
      '/reception/bookings/new?memberId=2&week=2026-10-19',
      '/reception/bookings/new?memberId=3&q=Alex&week=2026-10-19&sessionId=5',
    )

    await user.click(await screen.findByRole('radio', { name: /Basketball Foundations/ }))
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
    await waitFor(() => expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=100')).toBe(true))
    await user.click(screen.getByRole('button', { name: 'Switch member' }))
    expect(await screen.findByText('Alex Tran')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Confirm booking' })).toBeEnabled()
    expect(calls.some((call) => call.path === '/sessions/5/eligibility?memberId=3')).toBe(true)

    await act(async () => {
      completeMemberAHistory(json(200, { content: [booking], page: 0, size: 100, totalElements: 1, totalPages: 1 }))
      await new Promise((resolve) => setTimeout(resolve, 10))
    })
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm booking' }))

    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(screen.getByText('Alex Tran')).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'POST' && call.path === '/bookings').map((call) => call.body)).toEqual([
      { memberId: 2, sessionId: 5, packageRegistrationId: 11 },
      { memberId: 3, sessionId: 5, packageRegistrationId: 11 },
    ])
  })

  it('does not invite a retry when the booking outcome and reconciliation read are both unknown', async () => {
    const { calls } = setup((call) => {
      if (call.method === 'POST' && call.path === '/bookings') throw new Error('Connection lost')
      if (call.method === 'GET' && call.path.startsWith('/bookings/member/2')) return backendError(503, 'History unavailable')
      return undefined
    }, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&week=2026-10-19')

    await user.click(await screen.findByRole('radio', { name: /Basketball Foundations/ }))
    await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Booking outcome not verified')
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Check member bookings' })).toHaveAttribute('href', '/reception/bookings?memberId=2&q=')
    expect(calls.filter((call) => call.method === 'POST' && call.path === '/bookings')).toHaveLength(1)
  })

  it('does not trust a cancelled or cross-member booking from route state', async () => {
    const cancelled = { ...booking, status: 'CANCELLED' as const }
    setup(undefined, cancelled)
    renderConfirmation({ booking: cancelled, returnTo: '/reception/bookings?memberId=2' })
    expect(await screen.findByText('Booking not verified')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()

    cleanup()
    const otherMember = { ...booking, memberId: 3, memberFullName: 'Other Member' }
    setup(undefined, otherMember)
    renderConfirmation({ booking: otherMember, returnTo: '/reception/bookings?memberId=2' })
    expect(await screen.findByText('Booking not verified')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(screen.queryByText('Other Member')).not.toBeInTheDocument()
  })

  it('does not show a fabricated confirmation when member history has no matching booking', async () => {
    const { calls } = setup(undefined, null)
    renderConfirmation({ booking: { ...booking, status: 'CONFIRMED' }, returnTo: '/reception/bookings?memberId=2' })

    expect(await screen.findByText('Booking not verified')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=100')).toBe(true)

    cleanup()
    setup(undefined, null)
    renderConfirmation({ booking: { id: 21, status: 'CONFIRMED' }, returnTo: '/reception/bookings?memberId=2' })
    expect(await screen.findByText('Booking not verified')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
  })

  it('retries the authoritative confirmation history read in place', async () => {
    let reads = 0
    const { calls } = setup((call) => {
      if (call.path.startsWith('/bookings/member/2') && call.path.includes('size=100')) {
        reads += 1
        if (reads === 1) return backendError(503, 'History temporarily unavailable')
      }
      return undefined
    })
    const user = userEvent.setup()
    renderApp('/reception/bookings/confirmed/21?memberId=2&q=Taylor')

    expect(await screen.findByText('History temporarily unavailable')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(reads).toBe(2)
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=0&size=100')).toBe(true)
  })

  it('finds a confirmed booking beyond the first 100 member-history records', async () => {
    const laterBooking = { ...booking, id: 102, bookingCode: 'BK-1102' }
    const { calls } = setup((call) => {
      const url = new URL(`http://api.test${call.path}`)
      if (url.pathname !== '/bookings/member/2' || url.searchParams.get('size') !== '100' || url.searchParams.get('when') !== 'UPCOMING') return undefined
      return url.searchParams.get('page') === '1'
        ? json(200, { content: [laterBooking], page: 1, size: 100, totalElements: 101, totalPages: 2 })
        : json(200, { content: [booking], page: 0, size: 100, totalElements: 101, totalPages: 2 })
    })
    renderConfirmation(null, laterBooking.id)

    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/bookings/member/2?when=UPCOMING&page=1&size=100')).toBe(true)
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

const RECONCILIATION_READ = '/bookings/member/2?when=UPCOMING&page=0&size=100'

function writes(calls: RecordedCall[], method: 'POST' | 'DELETE') {
  return calls.filter((call) => call.method === method && call.path.startsWith('/bookings'))
}

function reconciliationReads(calls: RecordedCall[]) {
  return calls.filter((call) => call.method === 'GET' && call.path.startsWith('/bookings/member/') && call.path.includes('size=100'))
}

async function submitAssistedBooking(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('radio', { name: /Basketball Foundations/ }))
  await user.click(await screen.findByRole('button', { name: 'Confirm booking' }))
}

async function confirmMemberCancellation(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Cancel booking' }))
  await user.click(within(screen.getByRole('dialog', { name: 'Cancel member booking' })).getByRole('button', { name: 'Confirm cancellation' }))
}

async function settle() {
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)) })
}

describe('FE-10 HTTP 408 mutation reconciliation', () => {
  it('confirms a booking committed before an HTTP 408 only after member history verifies it', async () => {
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'POST' && call.path === '/bookings') {
        setCurrentBooking(booking)
        return backendError(408, 'Request Timeout')
      }
      return undefined
    }, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&q=Taylor&week=2026-10-19')

    await submitAssistedBooking(user)

    expect(await screen.findByRole('heading', { name: 'Booking Confirmed' })).toBeInTheDocument()
    expect(screen.getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(screen.getByText(/BK-1021/)).toBeInTheDocument()
    expect(screen.queryByText('Booking not completed')).not.toBeInTheDocument()
    expect(screen.queryByText('Request Timeout')).not.toBeInTheDocument()
    expect(writes(calls, 'POST').map((call) => call.body)).toEqual([{ memberId: 2, sessionId: 5, packageRegistrationId: 11 }])
    expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true)
    expect(reconciliationReads(calls).every((call) => call.path.startsWith('/bookings/member/2?'))).toBe(true)
  })

  it.each([
    ['a 5xx response', () => backendError(503, 'History unavailable')],
    ['a network failure', () => { throw new Error('Connection lost during reconciliation') }],
  ])('keeps an HTTP 408 booking unverified and locked when the history read fails with %s', async (_label, historyFailure) => {
    const { calls } = setup((call) => {
      if (call.method === 'POST' && call.path === '/bookings') return backendError(408, 'Request Timeout')
      if (call.method === 'GET' && call.path.startsWith('/bookings/member/2')) return historyFailure()
      return undefined
    }, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&week=2026-10-19')

    await submitAssistedBooking(user)

    expect(await screen.findByText('Booking outcome not verified')).toBeInTheDocument()
    expect(screen.getByText(/The booking outcome could not be verified/)).toBeInTheDocument()
    expect(screen.queryByText('Booking not completed')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Check member bookings' })).toHaveAttribute('href', '/reception/bookings?memberId=2&q=')
    expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true)
    await settle()
    expect(writes(calls, 'POST')).toHaveLength(1)
  })

  it('does not confirm an HTTP 408 booking when member history has no authoritative match', async () => {
    const cancelledSameSession = { ...booking, id: 30, bookingCode: 'BK-1030', status: 'CANCELLED' as const }
    const confirmedOtherSession = { ...booking, id: 31, bookingCode: 'BK-1031', sessionId: 6 }
    const confirmedOtherPackage = { ...booking, id: 32, bookingCode: 'BK-1032', packageRegistrationId: 12 }
    const { calls } = setup((call) => {
      if (call.method === 'POST' && call.path === '/bookings') return backendError(408, 'Request Timeout')
      if (call.method === 'GET' && call.path.startsWith('/bookings/member/2')) {
        return json(200, { content: [cancelledSameSession, confirmedOtherSession, confirmedOtherPackage], page: 0, size: 100, totalElements: 3, totalPages: 1 })
      }
      return undefined
    }, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&week=2026-10-19')

    await submitAssistedBooking(user)

    expect(await screen.findByText('Booking outcome not verified')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(screen.queryByText('Booking not completed')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled()
    expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true)
    expect(calls.some((call) => call.path === '/bookings/member/2?when=PAST&page=0&size=100')).toBe(true)
    await settle()
    expect(writes(calls, 'POST')).toHaveLength(1)
  })

  it('reports a cancellation committed before an HTTP 408 only after member history shows CANCELLED', async () => {
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'DELETE' && call.path === '/bookings/21') {
        setCurrentBooking({ ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00' })
        return backendError(408, 'Request Timeout')
      }
      return undefined
    })
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2')

    await confirmMemberCancellation(user)

    expect(await screen.findByText(/BK-1021 is now cancelled/)).toBeInTheDocument()
    expect(screen.queryByText('Cancellation was rejected')).not.toBeInTheDocument()
    expect(screen.queryByText('Request Timeout')).not.toBeInTheDocument()
    expect(screen.queryByText('Cancellation outcome not verified')).not.toBeInTheDocument()
    expect(writes(calls, 'DELETE').map((call) => call.path)).toEqual(['/bookings/21'])
    expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true)
    expect(reconciliationReads(calls).every((call) => call.path.startsWith('/bookings/member/2?'))).toBe(true)
  })

  it('keeps an HTTP 408 cancellation unverified when the history read fails and never repeats DELETE', async () => {
    let failReconciliation = true
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'DELETE' && call.path === '/bookings/21') {
        setCurrentBooking({ ...booking, status: 'CANCELLED', cancelledAt: '2026-10-17T10:30:00' })
        return backendError(408, 'Request Timeout')
      }
      if (failReconciliation && call.path.startsWith('/bookings/member/2') && call.path.includes('size=100')) {
        return backendError(503, 'History unavailable')
      }
      return undefined
    })
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2')

    await confirmMemberCancellation(user)

    expect(await screen.findByText('Cancellation outcome not verified')).toBeInTheDocument()
    expect(screen.queryByText('Cancellation was rejected')).not.toBeInTheDocument()
    expect(screen.queryByText(/is now cancelled/)).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Cancel member booking' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel booking' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Check booking status' })).toBeEnabled()
    await settle()
    expect(writes(calls, 'DELETE')).toHaveLength(1)

    failReconciliation = false
    await user.click(screen.getByRole('button', { name: 'Check booking status' }))
    expect(await screen.findByText(/BK-1021 is now cancelled/)).toBeInTheDocument()
    expect(screen.queryByText('Cancellation outcome not verified')).not.toBeInTheDocument()
    expect(writes(calls, 'DELETE')).toHaveLength(1)
  })

  it('reports the observed CONFIRMED status after an HTTP 408 cancellation without claiming a rejection', async () => {
    const { calls } = setup((call) => call.method === 'DELETE' && call.path === '/bookings/21' ? backendError(408, 'Request Timeout') : undefined)
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2')

    await confirmMemberCancellation(user)

    expect(await screen.findByText('Booking remains confirmed')).toBeInTheDocument()
    expect(screen.getByText(/latest member history still shows this booking as confirmed/)).toBeInTheDocument()
    expect(screen.queryByText('Cancellation was rejected')).not.toBeInTheDocument()
    expect(screen.queryByText('Request Timeout')).not.toBeInTheDocument()
    expect(screen.queryByText(/is now cancelled/)).not.toBeInTheDocument()
    expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true)
    await settle()
    expect(writes(calls, 'DELETE')).toHaveLength(1)
  })

  it.each([
    [400, 'Session is no longer bookable', undefined],
    [403, 'You do not have permission to book for this member', undefined],
    [404, 'Package registration not found', undefined],
    [409, 'This session has just filled up.', 'FULL'],
    [409, 'The member already has a booking for this session.', 'ALREADY_BOOKED'],
  ])('keeps a definitive HTTP %i booking rejection without reconciliation or success (%s)', async (status, message, code) => {
    const { calls } = setup((call) => call.method === 'POST' && call.path === '/bookings'
      ? json(status, { timestamp: '2026-10-12T10:00:00', status, message, ...(code ? { code } : {}) })
      : undefined, null)
    const user = userEvent.setup()
    renderApp('/reception/bookings/new?memberId=2&week=2026-10-19')

    await submitAssistedBooking(user)

    expect(await screen.findByText('Booking not completed')).toBeInTheDocument()
    expect(screen.getByText(message)).toBeInTheDocument()
    expect(screen.queryByText('Booking outcome not verified')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeEnabled()
    expect(reconciliationReads(calls)).toHaveLength(0)
    expect(writes(calls, 'POST')).toHaveLength(1)
  })

  it.each([
    [400, 'Booking can no longer be cancelled after the cutoff'],
    [403, 'You do not have permission to cancel this booking'],
    [404, 'Booking not found'],
    [409, 'Booking is already cancelled'],
  ])('keeps a definitive HTTP %i cancellation rejection without reconciliation or success', async (status, message) => {
    const { calls } = setup((call) => call.method === 'DELETE' && call.path === '/bookings/21' ? backendError(status, message) : undefined)
    const user = userEvent.setup()
    renderApp('/reception/bookings?memberId=2')

    await confirmMemberCancellation(user)

    expect(await screen.findByText('Cancellation was rejected')).toBeInTheDocument()
    expect(screen.getByText(message)).toBeInTheDocument()
    expect(screen.queryByText(/is now cancelled/)).not.toBeInTheDocument()
    expect(screen.queryByText('Cancellation outcome not verified')).not.toBeInTheDocument()
    expect(reconciliationReads(calls)).toHaveLength(0)
    expect(writes(calls, 'DELETE')).toHaveLength(1)
  })

  it('ignores member A HTTP 408 booking reconciliation after switching to member B', async () => {
    let completeMemberAHistory!: (response: Response) => void
    const pendingMemberAHistory = new Promise<Response>((resolve) => { completeMemberAHistory = resolve })
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'POST' && call.path === '/bookings') {
        setCurrentBooking(booking)
        return backendError(408, 'Request Timeout')
      }
      if (call.path.startsWith('/bookings/member/2') && call.path.includes('size=100')) return pendingMemberAHistory
      return undefined
    }, null)
    const user = userEvent.setup()
    renderWithMemberSwitch(
      '/reception/bookings/new?memberId=2&week=2026-10-19',
      '/reception/bookings/new?memberId=3&q=Alex&week=2026-10-19&sessionId=5',
    )

    await submitAssistedBooking(user)
    await waitFor(() => expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true))
    await user.click(screen.getByRole('button', { name: 'Switch member' }))
    expect(await screen.findByText('Alex Tran')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Confirm booking' })).toBeEnabled()

    await act(async () => {
      completeMemberAHistory(json(200, { content: [booking], page: 0, size: 100, totalElements: 1, totalPages: 1 }))
      await new Promise((resolve) => setTimeout(resolve, 20))
    })
    expect(screen.queryByRole('heading', { name: 'Booking Confirmed' })).not.toBeInTheDocument()
    expect(screen.queryByText('Booking outcome not verified')).not.toBeInTheDocument()
    expect(screen.queryByText('Taylor Nguyen')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeEnabled()
    expect(writes(calls, 'POST').map((call) => call.body)).toEqual([{ memberId: 2, sessionId: 5, packageRegistrationId: 11 }])
  })

  it('ignores member A HTTP 408 cancellation reconciliation after switching to member B', async () => {
    let completeMemberAHistory!: (response: Response) => void
    const pendingMemberAHistory = new Promise<Response>((resolve) => { completeMemberAHistory = resolve })
    const cancelled = { ...booking, status: 'CANCELLED' as const, cancelledAt: '2026-10-17T10:30:00' }
    const { calls } = setup((call, setCurrentBooking) => {
      if (call.method === 'DELETE' && call.path === '/bookings/21') {
        setCurrentBooking(cancelled)
        return backendError(408, 'Request Timeout')
      }
      if (call.path.startsWith('/bookings/member/2') && call.path.includes('size=100')) return pendingMemberAHistory
      return undefined
    })
    const user = userEvent.setup()
    renderWithMemberSwitch('/reception/bookings?memberId=2&q=Taylor', '/reception/bookings?memberId=3&q=Alex')

    await confirmMemberCancellation(user)
    await waitFor(() => expect(calls.some((call) => call.path === RECONCILIATION_READ)).toBe(true))
    await user.click(screen.getByRole('button', { name: 'Switch member' }))
    expect(await screen.findByText('Alex Tran')).toBeInTheDocument()
    expect(await screen.findByText('No upcoming bookings')).toBeInTheDocument()

    await act(async () => {
      completeMemberAHistory(json(200, { content: [cancelled], page: 0, size: 100, totalElements: 1, totalPages: 1 }))
      await new Promise((resolve) => setTimeout(resolve, 20))
    })
    expect(screen.queryByRole('dialog', { name: 'Cancel member booking' })).not.toBeInTheDocument()
    expect(screen.queryByText(/is now cancelled/)).not.toBeInTheDocument()
    expect(screen.queryByText('Booking remains confirmed')).not.toBeInTheDocument()
    expect(screen.queryByText('Cancellation outcome not verified')).not.toBeInTheDocument()
    expect(screen.queryByText('Cancellation was rejected')).not.toBeInTheDocument()
    expect(writes(calls, 'DELETE').map((call) => call.path)).toEqual(['/bookings/21'])
  })
})
