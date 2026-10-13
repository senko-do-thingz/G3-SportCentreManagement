import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { booking, card, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

/**
 * FE-02: Member Dashboard (F1-07) against a stand-in for UserController, MembershipCardController,
 * SportPackageController and BookingController. Today is fixed at 2026-10-13 09:30.
 */

interface DashboardData {
  cards?: Response | unknown[]
  registrations?: Response | unknown[]
  bookings?: Response | unknown[]
}

function answer(value: Response | unknown[] | undefined): Response {
  if (value instanceof Response) {
    return value
  }
  return json(200, value ?? [])
}

function dashboardBackend(data: DashboardData = {}) {
  return (call: RecordedCall) => {
    if (call.path === '/users/me') {
      return json(200, MEMBER)
    }
    if (call.path === '/membership-cards/my') {
      return answer(data.cards)
    }
    if (call.path === '/packages/registrations/my') {
      return answer(data.registrations)
    }
    if (call.path.startsWith('/bookings/my')) {
      return answer(data.bookings)
    }
    return backendError(404, 'Resource not found')
  }
}

async function openDashboard(data: DashboardData = {}) {
  const fake = installFakeFetch(dashboardBackend(data))
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
  renderApp('/member')
  await screen.findByRole('region', { name: 'My sport packages' })
  return fake
}

function statValue(label: string): string {
  const labelElement = screen.getByText(label, { selector: '.stat-card__label' })
  return labelElement.parentElement?.querySelector('.stat-card__value')?.textContent ?? ''
}

function statHint(label: string): string {
  const labelElement = screen.getByText(label, { selector: '.stat-card__label' })
  return labelElement.parentElement?.querySelector('.stat-card__hint')?.textContent ?? ''
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-02 Member Dashboard (F1-07)', () => {
  it('TC-FE02-020 a new member sees the New Standard Member state', async () => {
    await openDashboard()

    expect(document.querySelector('.dashboard')).toHaveAttribute('data-state', 'new-standard')
    expect(screen.getByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Welcome, Taylor Nguyen.' })).toBeInTheDocument()
    expect(statValue('Membership tier')).toBe('Standard')
    expect(statHint('Membership tier')).toBe('Explore cards for package discounts')
    expect(statValue('Active packages')).toBe('0')
    expect(statValue('Upcoming sessions')).toBe('0')
    const packages = screen.getByRole('region', { name: 'My sport packages' })
    expect(within(packages).getByText('No sport packages yet')).toBeInTheDocument()
    expect(within(packages).getByRole('link', { name: 'Explore packages' })).toHaveAttribute('href', '/member/packages')
    expect(within(screen.getByRole('region', { name: 'Upcoming sessions' })).getByText('No upcoming sessions')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Pending payments' })).not.toBeInTheDocument()
  })

  it('TC-FE02-021 after a purchase the member sees the After Purchase state with tier, package and session', async () => {
    await openDashboard({ cards: [card()], registrations: [registration()], bookings: [booking()] })

    expect(document.querySelector('.dashboard')).toHaveAttribute('data-state', 'after-purchase')
    expect(screen.getByRole('heading', { name: 'Ready for your next session?' })).toBeInTheDocument()
    expect(screen.getByText('Welcome back, Taylor Nguyen')).toBeInTheDocument()
    expect(statValue('Membership tier')).toBe('VIP')
    expect(statHint('Membership tier')).toBe('Active - Until 04 Jul 2027 - 10% off 30 day and 90 day packages')
    expect(statValue('Active packages')).toBe('1')
    expect(statValue('Upcoming sessions')).toBe('1')

    const item = screen.getByRole('listitem', { name: 'Football - Coach-led' })
    expect(within(item).getByText('REG-0011')).toBeInTheDocument()
    expect(within(item).getByText('Football 30 days - Ends 30 Oct 2026')).toBeInTheDocument()
    expect(within(item).getByText('6 of 8 sessions remaining')).toBeInTheDocument()
    expect(within(item).getByText('Active')).toBeInTheDocument()
    const bar = within(item).getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '6')
    expect(bar).toHaveAttribute('aria-valuemax', '8')
    expect(within(item).getByRole('link', { name: 'Book a session' })).toHaveAttribute('href', '/member/schedule')
    expect(within(item).getByRole('link', { name: 'Renew package' })).toHaveAttribute('href', '/member/packages')

    const sessions = screen.getByRole('region', { name: 'Upcoming sessions' })
    expect(within(sessions).getByText('Football - Coach-led')).toBeInTheDocument()
    expect(within(sessions).getByText('14 Oct 2026, 17:00 to 18:30 - BK-0021')).toBeInTheDocument()
  })

  it('TC-FE02-022 the hero offers Book a session when a package is active', async () => {
    await openDashboard({ registrations: [registration()] })
    const hero = screen.getByRole('region', { name: 'Ready for your next session?' })
    expect(within(hero).getByRole('link', { name: 'Book a session' })).toHaveAttribute('href', '/member/schedule')
  })

  it('TC-FE02-023 a pending package links to its payment summary', async () => {
    const user = userEvent.setup()
    await openDashboard({
      registrations: [registration({ id: 12, status: 'PENDING_PAYMENT', paymentId: 52, paymentCode: 'PAY-0052' })],
    })

    expect(document.querySelector('.dashboard')).toHaveAttribute('data-state', 'after-purchase')
    const pending = screen.getByRole('region', { name: 'Pending payments' })
    expect(within(pending).getByText('Sport package - Football - Coach-led')).toBeInTheDocument()
    expect(within(pending).getByText(/PAY-0052 - 810\.000 VND/)).toBeInTheDocument()
    expect(within(pending).getByText('Pending payment')).toBeInTheDocument()
    const payNow = within(pending).getByRole('link', { name: 'Pay now for Sport package - Football - Coach-led' })
    expect(payNow).toHaveAttribute('href', '/member/payments/52')
    expect(statValue('Active packages')).toBe('0')

    await user.click(payNow)
    expect(await screen.findByRole('heading', { name: 'Payment Summary' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Page not found' })).not.toBeInTheDocument()
  })

  it('TC-FE02-024 a pending card request links to its payment summary', async () => {
    await openDashboard({ cards: [card({ id: 3, status: 'PENDING_PAYMENT', paymentId: 51 })] })
    const pending = screen.getByRole('region', { name: 'Pending payments' })
    expect(within(pending).getByRole('link', { name: 'Pay now for Membership card - VIP' })).toHaveAttribute(
      'href',
      '/member/payments/51',
    )
    // A pending card gives no discount yet, so the tier is still Standard.
    expect(statValue('Membership tier')).toBe('Standard')
  })

  it('TC-FE02-025 a pending item without a linked payment says to pay at the front desk', async () => {
    await openDashboard({ registrations: [registration({ status: 'PENDING_PAYMENT', paymentId: null, paymentCode: null })] })
    const pending = screen.getByRole('region', { name: 'Pending payments' })
    expect(within(pending).getByText('Pay at the front desk')).toBeInTheDocument()
    expect(within(pending).queryByRole('link', { name: /Pay now/ })).not.toBeInTheDocument()
  })

  it('TC-FE02-026 a package bought in advance shows its start date', async () => {
    await openDashboard({ registrations: [registration({ startDate: '2026-10-20', endDate: '2026-11-18' })] })
    const item = screen.getByRole('listitem', { name: 'Football - Coach-led' })
    expect(within(item).getByText('Starts 20 Oct 2026')).toBeInTheDocument()
    expect(statValue('Active packages')).toBe('1')
  })

  it('TC-FE02-027 a package without a session limit shows no progress bar', async () => {
    await openDashboard({ registrations: [registration({ totalSessions: null, remainingSessions: null, trainingFormat: 'SELF_TRAINING' })] })
    const item = screen.getByRole('listitem', { name: 'Football - Self-training' })
    expect(within(item).getByText('No session limit')).toBeInTheDocument()
    expect(within(item).queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('TC-FE02-028 the dashboard calls the three member endpoints with the access token', async () => {
    const { calls } = await openDashboard()
    await waitFor(() => expect(calls.some((call) => call.path.startsWith('/bookings/my'))).toBe(true))
    const paths = calls.map((call) => `${call.method} ${call.path}`)
    expect(paths).toEqual(
      expect.arrayContaining([
        'GET /users/me',
        'GET /membership-cards/my',
        'GET /packages/registrations/my',
        'GET /bookings/my?when=UPCOMING',
      ]),
    )
    for (const call of calls) {
      expect(call.authorization).toBe('Bearer member-access')
    }
  })

  it('TC-FE02-029 past, cancelled and later bookings are filtered and only three are listed', async () => {
    await openDashboard({
      registrations: [registration()],
      bookings: [
        booking({ id: 1, sessionDate: '2026-10-12', bookingCode: 'BK-PAST' }),
        booking({ id: 2, status: 'CANCELLED', bookingCode: 'BK-CANCELLED' }),
        booking({ id: 3, sessionDate: '2026-10-15', bookingCode: 'BK-3' }),
        booking({ id: 4, sessionDate: '2026-10-16', bookingCode: 'BK-4' }),
        booking({ id: 5, sessionDate: '2026-10-17', bookingCode: 'BK-5' }),
        booking({ id: 6, sessionDate: '2026-10-18', bookingCode: 'BK-6' }),
      ],
    })
    const sessions = screen.getByRole('region', { name: 'Upcoming sessions' })
    expect(within(sessions).getAllByRole('listitem')).toHaveLength(3)
    expect(within(sessions).queryByText(/BK-PAST|BK-CANCELLED|BK-6/)).not.toBeInTheDocument()
    expect(statValue('Upcoming sessions')).toBe('4')
  })

  it('TC-FE02-030 a failure of cards or packages shows an error with Retry that reloads', async () => {
    const user = userEvent.setup()
    let failing = true
    installFakeFetch((call) => {
      if (call.path === '/membership-cards/my' && failing) {
        return backendError(500, 'Internal server error')
      }
      return dashboardBackend({ cards: [card()] })(call)
    })
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    renderApp('/member')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not load your dashboard')
    expect(alert).toHaveTextContent('Internal server error')

    failing = false
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('region', { name: 'My sport packages' })).toBeInTheDocument()
    expect(statValue('Membership tier')).toBe('VIP')
  })

  it('TC-FE02-031 a failure of bookings only affects the Upcoming sessions block', async () => {
    await openDashboard({ registrations: [registration()], bookings: backendError(503, 'Service unavailable') })
    const sessions = screen.getByRole('region', { name: 'Upcoming sessions' })
    expect(await within(sessions).findByRole('alert')).toHaveTextContent('We could not load your sessions')
    expect(statValue('Upcoming sessions')).toBe('-')
    expect(statHint('Upcoming sessions')).toBe('Could not load your bookings')
    expect(statValue('Active packages')).toBe('1')
  })

  it('TC-FE02-032 a response that is not a list is reported as an error, not as an empty dashboard', async () => {
    installFakeFetch(dashboardBackend({ registrations: json(200, { content: [] }) }))
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    renderApp('/member')
    expect(await screen.findByRole('alert')).toHaveTextContent('The server returned an unexpected response.')
    expect(screen.queryByText('No sport packages yet')).not.toBeInTheDocument()
  })

  it('TC-FE02-033 quick links and Edit profile open the other member screens', async () => {
    const user = userEvent.setup()
    await openDashboard()
    const quickLinks = screen.getByRole('navigation', { name: 'Quick links' })
    expect(within(quickLinks).getByRole('link', { name: /My membership card/ })).toHaveAttribute('href', '/member/membership')
    expect(within(quickLinks).getByRole('link', { name: /Sport packages/ })).toHaveAttribute('href', '/member/packages')
    expect(within(quickLinks).getByRole('link', { name: /My schedule/ })).toHaveAttribute('href', '/member/schedule')
    expect(within(quickLinks).getByRole('link', { name: /My payments/ })).toHaveAttribute('href', '/member/payments')

    await user.click(screen.getByRole('link', { name: 'Edit profile' }))
    expect(await screen.findByRole('heading', { name: 'My Profile' })).toBeInTheDocument()
  })

  it('TC-FE02-034 an expired card is not the current tier', async () => {
    await openDashboard({ cards: [card({ status: 'EXPIRED', endDate: '2026-10-01' })] })
    expect(statValue('Membership tier')).toBe('Standard')
    // The member did buy a card before, so the page is in the After Purchase state.
    expect(document.querySelector('.dashboard')).toHaveAttribute('data-state', 'after-purchase')
  })

  // Review round 1

  it('TC-FE02-035 a package with no sessions left shows only Renew and the hero does not offer booking', async () => {
    await openDashboard({ registrations: [registration({ remainingSessions: 0 })] })
    const item = screen.getByRole('listitem', { name: 'Football - Coach-led' })
    expect(within(item).getByText('No sessions left')).toBeInTheDocument()
    expect(within(item).getByText('0 of 8 sessions remaining')).toBeInTheDocument()
    expect(within(item).queryByRole('link', { name: 'Book a session' })).not.toBeInTheDocument()
    expect(within(item).getByRole('link', { name: 'Renew package' })).toHaveAttribute('href', '/member/packages')

    const hero = screen.getByRole('region', { name: 'Find your next sport package.' })
    expect(hero).toHaveAttribute('data-variant', 'renew')
    expect(within(hero).queryByRole('link', { name: 'Book a session' })).not.toBeInTheDocument()
    expect(within(hero).getByRole('link', { name: 'Explore packages' })).toHaveAttribute('href', '/member/packages')
  })

  it('TC-FE02-036 a package with a total of 0 sessions is exhausted, never "No session limit"', async () => {
    await openDashboard({ registrations: [registration({ totalSessions: 0, remainingSessions: 0 })] })
    const item = screen.getByRole('listitem', { name: 'Football - Coach-led' })
    expect(within(item).getByText('0 of 0 sessions remaining')).toBeInTheDocument()
    expect(within(item).getByText('No sessions left')).toBeInTheDocument()
    expect(within(item).queryByText('No session limit')).not.toBeInTheDocument()
    expect(within(item).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(within(item).queryByRole('link', { name: 'Book a session' })).not.toBeInTheDocument()
  })

  it('TC-FE02-037 the hero offers Book a session when at least one package can be booked', async () => {
    await openDashboard({
      registrations: [registration({ id: 1, remainingSessions: 0 }), registration({ id: 2, sportName: 'Swimming' })],
    })
    const hero = screen.getByRole('region', { name: 'Ready for your next session?' })
    expect(within(hero).getByRole('link', { name: 'Book a session' })).toHaveAttribute('href', '/member/schedule')
  })

  it('TC-FE02-038 with only a pending payment the hero asks to complete it and links to its summary', async () => {
    await openDashboard({ registrations: [registration({ status: 'PENDING_PAYMENT', paymentId: 52 })] })
    const hero = screen.getByRole('region', { name: 'Complete your payment to start training.' })
    expect(hero).toHaveAttribute('data-variant', 'pay')
    expect(within(hero).queryByText(/Welcome back/)).not.toBeInTheDocument()
    expect(within(hero).getByRole('link', { name: 'Pay now' })).toHaveAttribute('href', '/member/payments/52')
  })

  it('TC-FE02-039 when the first pending item has no payment the hero points to the Pending payments block', async () => {
    await openDashboard({ cards: [card({ status: 'PENDING_PAYMENT', paymentId: null })] })
    const hero = screen.getByRole('region', { name: 'Complete your payment to start training.' })
    expect(within(hero).getByRole('link', { name: 'View pending payments' })).toHaveAttribute('href', '#pending-payments')
    expect(document.getElementById('pending-payments')).toBe(screen.getByRole('region', { name: 'Pending payments' }))
  })

  it('TC-FE02-090 View all packages opens My Membership and Sport Packages (F1-12)', async () => {
    await openDashboard({ registrations: [registration()] })
    const packages = screen.getByRole('region', { name: 'My sport packages' })
    expect(within(packages).getByRole('link', { name: 'View all packages' })).toHaveAttribute('href', '/member/membership')
  })

  it('TC-FE02-091 the progress bar value is clamped to the total, like the bar width', async () => {
    await openDashboard({ registrations: [registration({ totalSessions: 8, remainingSessions: 12 })] })
    const bar = screen.getByRole('progressbar')
    expect(bar).toHaveAttribute('aria-valuenow', '8')
    expect(bar).toHaveAttribute('aria-valuemax', '8')
    expect(screen.getByText('8 of 8 sessions remaining')).toBeInTheDocument()
  })

  it('TC-FE02-092 Retry after a purchases error does not reload bookings that loaded', async () => {
    const user = userEvent.setup()
    let failCards = true
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/membership-cards/my' && failCards) {
        return backendError(500, 'Internal server error')
      }
      return dashboardBackend({ bookings: [booking()] })(call)
    })
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    renderApp('/member')
    const alert = await screen.findByRole('alert')
    await waitFor(() => expect(calls.filter((call) => call.path.startsWith('/bookings/my'))).toHaveLength(1))

    failCards = false
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    await screen.findByRole('region', { name: 'My sport packages' })
    expect(calls.filter((call) => call.path === '/membership-cards/my')).toHaveLength(2)
    expect(calls.filter((call) => call.path.startsWith('/bookings/my'))).toHaveLength(1)
    expect(statValue('Upcoming sessions')).toBe('1')
  })

  it('TC-FE02-093 Retry after a purchases error reloads bookings that failed too', async () => {
    const user = userEvent.setup()
    let failing = true
    const { calls } = installFakeFetch((call) => {
      if (failing && (call.path === '/membership-cards/my' || call.path.startsWith('/bookings/my'))) {
        return backendError(503, 'Service unavailable')
      }
      return dashboardBackend({ bookings: [booking()] })(call)
    })
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    renderApp('/member')
    const alert = await screen.findByRole('alert')
    await waitFor(() => expect(calls.filter((call) => call.path.startsWith('/bookings/my'))).toHaveLength(1))

    failing = false
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    await screen.findByRole('region', { name: 'My sport packages' })
    await waitFor(() => expect(statValue('Upcoming sessions')).toBe('1'))
    expect(calls.filter((call) => call.path.startsWith('/bookings/my'))).toHaveLength(2)
  })

  // Review round 2

  it('TC-FE02-122 a member with an ACTIVE VIP card and no package is invited to choose a first package', async () => {
    await openDashboard({ cards: [card()] })
    const hero = screen.getByRole('region', { name: 'Choose your first sport package.' })
    expect(hero).toHaveAttribute('data-variant', 'first-package')
    expect(
      within(hero).getByText('Your VIP card gives you 10% off 30 day and 90 day packages. Pick a sport to start training.'),
    ).toBeInTheDocument()
    expect(within(hero).getByRole('link', { name: 'Explore packages' })).toHaveAttribute('href', '/member/packages')
    expect(within(hero).queryByText(/no sessions left|have ended/)).not.toBeInTheDocument()
  })

  it('TC-FE02-126 the tier hint and the first-package hero state the same discount rule', async () => {
    await openDashboard({ cards: [card()] })
    const rule = '10% off 30 day and 90 day packages'
    expect(statHint('Membership tier')).toContain(rule)
    expect(screen.getByRole('region', { name: 'Choose your first sport package.' })).toHaveTextContent(rule)
  })

  it('TC-FE02-123 a member with only an expired card and no package gets the generic first package text', async () => {
    await openDashboard({ cards: [card({ status: 'EXPIRED', endDate: '2026-10-01' })] })
    const hero = screen.getByRole('region', { name: 'Choose your first sport package.' })
    expect(within(hero).getByText('Pick a sport and a training style to start training.')).toBeInTheDocument()
  })

  it('TC-FE02-124 the Active packages stat says how many of its packages can still be booked', async () => {
    await openDashboard({ registrations: [registration({ remainingSessions: 0 })] })
    expect(statValue('Active packages')).toBe('1')
    expect(statHint('Active packages')).toBe('0 can be booked - 1 with no sessions left')
    expect(screen.getAllByRole('listitem', { name: 'Football - Coach-led' })).toHaveLength(1)
  })

  it('TC-FE02-125 the Active packages hint stays "Your paid sport access" when every package can be booked', async () => {
    await openDashboard({ registrations: [registration()] })
    expect(statHint('Active packages')).toBe('Your paid sport access')
  })
})
