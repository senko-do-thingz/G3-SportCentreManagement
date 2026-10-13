import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { card, payment, registration, sport, sportPackage } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

/**
 * FE-03: Sport Packages (F1-09), Package Detail and Registration (F1-10), Payment Handoff overlay
 * and the read only Payment Summary (F3-01). Today is fixed at 2026-10-13.
 */

const SPORTS = [sport(), sport({ id: 2, code: 'SWIMMING', name: 'Swimming', displayOrder: 2 })]
const PACKAGES = [
  sportPackage({ id: 1, trainingFormat: 'SELF_TRAINING', durationDays: 1, sessionCount: 1, sessionMinutes: 60, priceAmount: 80000 }),
  sportPackage({ id: 2, trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 500000 }),
  sportPackage({ id: 4, trainingFormat: 'COACH_LED', durationDays: 30, sessionCount: 8, sessionMinutes: 90, priceAmount: 900000 }),
  sportPackage({ id: 6, trainingFormat: 'COACH_LED', durationDays: 90, sessionCount: 24, sessionMinutes: 90, priceAmount: 2400000 }),
  sportPackage({ id: 7, sportId: 2, sportName: 'Swimming', trainingFormat: 'SELF_TRAINING', durationDays: 30, priceAmount: 450000 }),
]
const GOLD = card({ tierCode: 'GOLD', tierName: 'Gold', discountPercentage: 5 })
const VIP = card()

interface Options {
  cards?: unknown[] | Response
  registrations?: unknown[] | Response
  packages?: unknown[]
  onRegister?: (call: RecordedCall) => Response | Promise<Response>
}

function packagesBackend(options: Options = {}) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/sports') return json(200, SPORTS)
    if (call.path === '/packages') return json(200, options.packages ?? PACKAGES)
    if (call.path === '/membership-cards/my') {
      return options.cards instanceof Response ? options.cards : json(200, options.cards ?? [])
    }
    if (call.path === '/packages/registrations' && call.method === 'POST') {
      return options.onRegister
        ? options.onRegister(call)
        : json(201, registration({ id: 40, registrationCode: 'REG-0040', status: 'PENDING_PAYMENT', paymentId: 52, paymentCode: 'PAY-0052', startDate: '2026-10-13', paidAmount: 810000 }))
    }
    if (call.path === '/payments/52') return json(200, payment())
    if (call.path === '/packages/registrations/my') {
      return options.registrations instanceof Response ? options.registrations : json(200, options.registrations ?? [])
    }
    if (call.path.startsWith('/bookings/my')) return json(200, [])
    return backendError(404, 'Resource not found')
  })
}

function signIn() {
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
}

async function openList(options: Options = {}, path = '/member/packages') {
  const fake = packagesBackend(options)
  signIn()
  renderApp(path)
  await screen.findByRole('list', { name: 'Sport packages' })
  return fake
}

function cardFor(name: RegExp) {
  return screen.getByRole('listitem', { name })
}

function finalPrice(name: RegExp): string {
  return within(cardFor(name)).getByTestId('final-price').textContent ?? ''
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-03 Sport Packages (F1-09)', () => {
  it('TC-FE03-010 lists the packages from GET /packages with duration, sessions, minutes and price', async () => {
    const { calls } = await openList()
    expect(screen.getByRole('heading', { name: 'Sport Packages' })).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(5)
    const coach30 = cardFor(/^Coach-led Football, 30 days - 8 sessions$/)
    expect(within(coach30).getByText('900.000 VND')).toBeInTheDocument()
    expect(within(coach30).getByText(/Original price - 90 min \/ session/)).toBeInTheDocument()
    expect(within(coach30).getByRole('link', { name: /View package/ })).toHaveAttribute('href', '/member/packages/4')
    expect(calls.map((call) => call.path)).toEqual(expect.arrayContaining(['/sports', '/packages', '/membership-cards/my']))
  })

  it('TC-FE03-011 STANDARD member (no card): every price is the original price', async () => {
    await openList({ cards: [] })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('900.000 VND')
    expect(finalPrice(/^Coach-led Football, 90 days/)).toBe('2.400.000 VND')
    expect(finalPrice(/^Self-training Football, Single visit/)).toBe('80.000 VND')
  })

  it('TC-FE03-012 GOLD member: 5% off 30 and 90 day packages, single visit unchanged', async () => {
    await openList({ cards: [GOLD] })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('855.000 VND')
    expect(finalPrice(/^Coach-led Football, 90 days/)).toBe('2.280.000 VND')
    expect(finalPrice(/^Self-training Football, 30 days/)).toBe('475.000 VND')
    expect(finalPrice(/^Self-training Football, Single visit/)).toBe('80.000 VND')
    expect(within(cardFor(/^Coach-led Football, 30 days/)).getByText(/Gold 5% off/)).toBeInTheDocument()
    expect(within(cardFor(/^Self-training Football, Single visit/)).getByText(/Single visit - no card discount/)).toBeInTheDocument()
  })

  it('TC-FE03-013 VIP member: 10% off 30 and 90 day packages, single visit unchanged', async () => {
    await openList({ cards: [VIP] })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('810.000 VND')
    expect(finalPrice(/^Coach-led Football, 90 days/)).toBe('2.160.000 VND')
    expect(finalPrice(/^Self-training Football, Single visit/)).toBe('80.000 VND')
    expect(screen.getByText(/Your VIP card gives you 10% off packages longer than a single visit/)).toBeInTheDocument()
  })

  it('TC-FE03-014 a pending or expired card gives no discount', async () => {
    await openList({ cards: [card({ status: 'PENDING_PAYMENT' }), card({ id: 2, status: 'EXPIRED', endDate: '2026-10-01' })] })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('900.000 VND')
  })

  it('TC-FE03-015 filters by sport, training type and duration, and Reset filters clears them', async () => {
    const user = userEvent.setup()
    await openList()
    await user.selectOptions(screen.getByLabelText('Sport'), '1')
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
    await user.selectOptions(screen.getByLabelText('Training type'), 'COACH_LED')
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    await user.selectOptions(screen.getByLabelText('Duration'), '90')
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
    expect(cardFor(/^Coach-led Football, 90 days/)).toBeInTheDocument()
    // The detail link keeps the filters for "Back to packages".
    expect(within(cardFor(/^Coach-led Football, 90 days/)).getByRole('link', { name: /View package/ })).toHaveAttribute(
      'href',
      '/member/packages/6?sport=1&format=COACH_LED&duration=90',
    )

    await user.click(screen.getAllByRole('button', { name: 'Reset filters' })[0])
    expect(screen.getAllByRole('listitem')).toHaveLength(5)
    expect(screen.getByLabelText('Sport')).toHaveValue('')
  })

  it('TC-FE03-016 no match shows an empty state with Reset filters', async () => {
    const user = userEvent.setup()
    await openList()
    await user.selectOptions(screen.getByLabelText('Sport'), '2')
    await user.selectOptions(screen.getByLabelText('Training type'), 'COACH_LED')
    expect(screen.getByText('No packages match these filters')).toBeInTheDocument()
  })

  it('TC-FE03-017 filters are read from the URL', async () => {
    packagesBackend()
    signIn()
    renderApp('/member/packages?sport=2')
    expect(await screen.findAllByRole('listitem')).toHaveLength(1)
    expect(screen.getByLabelText('Sport')).toHaveValue('2')
  })

  it('TC-FE03-018 when the cards cannot be loaded, prices are original and a warning explains it', async () => {
    await openList({ cards: backendError(500, 'Internal server error') })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('900.000 VND')
    expect(screen.getByRole('alert')).toHaveTextContent('We could not load your membership card')
  })

  it('TC-FE03-019 a failure of the catalog shows an error with Retry', async () => {
    const user = userEvent.setup()
    let failing = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/packages' && failing) return backendError(503, 'Service unavailable')
      if (call.path === '/packages') return json(200, PACKAGES)
      if (call.path === '/sports') return json(200, SPORTS)
      return json(200, [])
    })
    signIn()
    renderApp('/member/packages')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not load the sport packages')
    failing = false
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('list', { name: 'Sport packages' })).toBeInTheDocument()
  })
})

describe('FE-03 Package Detail and Registration (F1-10)', () => {
  async function openDetail(options: Options = {}, path = '/member/packages/4') {
    const fake = packagesBackend(options)
    signIn()
    renderApp(path)
    await screen.findByRole('form', { name: 'Package registration' })
    // Confirm waits for the registrations list (B3b); every detail test starts after it arrived.
    await waitFor(() => expect(screen.queryByText('Checking your registrations')).not.toBeInTheDocument())
    return fake
  }

  function summaryValue(label: string): string {
    const summary = screen.getByRole('region', { name: 'Registration summary' })
    const term = within(summary).getByText(label, { selector: 'dt' })
    return term.nextElementSibling?.textContent ?? ''
  }

  it('TC-FE03-030 shows package info and the price breakdown for a VIP member', async () => {
    await openDetail({ cards: [VIP] })
    expect(screen.getByRole('heading', { name: 'Football - 30 days - 8 sessions' })).toBeInTheDocument()
    expect(summaryValue('Original price')).toBe('900.000 VND')
    expect(summaryValue('Membership discount')).toBe('VIP - 10% (-90.000 VND)')
    expect(summaryValue('Total payable')).toBe('810.000 VND')
    expect(summaryValue('Start date')).toBe('13 Oct 2026')
    expect(summaryValue('Expiry date')).toBe('11 Nov 2026')
  })

  it('TC-FE03-031 GOLD member gets 5% off', async () => {
    await openDetail({ cards: [GOLD] })
    expect(summaryValue('Membership discount')).toBe('Gold - 5% (-45.000 VND)')
    expect(summaryValue('Total payable')).toBe('855.000 VND')
  })

  it('TC-FE03-032 STANDARD member pays the original price', async () => {
    await openDetail({ cards: [] })
    expect(summaryValue('Membership discount')).toBe('None (Standard)')
    expect(summaryValue('Total payable')).toBe('900.000 VND')
  })

  it('TC-FE03-033 single visit: no discount even with a VIP card, expiry is the start date', async () => {
    await openDetail({ cards: [VIP] }, '/member/packages/1')
    expect(summaryValue('Membership discount')).toBe('Not applied to single visits')
    expect(summaryValue('Total payable')).toBe('80.000 VND')
    expect(summaryValue('Expiry date')).toBe('13 Oct 2026')
  })

  it('TC-FE03-034 the date picker starts at today and a past date cannot be submitted', async () => {
    const user = userEvent.setup()
    const { calls } = await openDetail()
    const input = screen.getByLabelText(/Start date/)
    expect(input).toHaveAttribute('min', '2026-10-13')
    expect(input).toHaveValue('2026-10-13')
    await user.clear(input)
    await user.type(input, '2026-10-12')
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    expect(await screen.findByText('Start date cannot be in the past')).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
    await waitFor(() => expect(input).toHaveFocus())
  })

  it('TC-FE03-035 a later start date updates the expiry date and is sent to the backend', async () => {
    const user = userEvent.setup()
    const { calls } = await openDetail()
    const input = screen.getByLabelText(/Start date/)
    await user.clear(input)
    await user.type(input, '2026-11-01')
    expect(summaryValue('Expiry date')).toBe('30 Nov 2026')
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    await screen.findByRole('dialog', { name: 'Payment summary' })
    const post = calls.find((call) => call.method === 'POST' && call.path === '/packages/registrations')
    expect(post?.body).toEqual({ packageId: 4, startDate: '2026-11-01', channel: 'ONLINE' })
    expect(post?.authorization).toBe('Bearer member-access')
  })

  it('TC-FE03-036 register -> Payment Handoff overlay -> Continue to payment lands on the new payment summary', async () => {
    const user = userEvent.setup()
    await openDetail({ cards: [VIP] })
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))

    const dialog = await screen.findByRole('dialog', { name: 'Payment summary' })
    expect(within(dialog).getByText('REG-0040')).toBeInTheDocument()
    expect(within(dialog).getByText('PAY-0052')).toBeInTheDocument()
    expect(within(dialog).getByTestId('amount-payable')).toHaveTextContent('810.000 VND')
    expect(within(dialog).getByRole('link', { name: 'View my registration' })).toHaveAttribute('href', '/member/membership')

    await user.click(within(dialog).getByRole('link', { name: 'Continue to payment' }))
    expect(await screen.findByRole('heading', { name: 'Payment Summary' })).toBeInTheDocument()
    const summary = await screen.findByRole('region', { name: 'Payment summary' })
    expect(within(summary).getByText('PAY-0052')).toBeInTheDocument()
    expect(within(summary).getByTestId('payment-amount')).toHaveTextContent('810.000 VND')
    expect(within(summary).getByText('Pending')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Bank transfer' })).toHaveTextContent('0123456789')
  })

  it('TC-FE03-037 a second submit while sending sends one request; the form locks after success', async () => {
    const user = userEvent.setup()
    let release: (response: Response) => void = () => undefined
    const { calls } = await openDetail({
      onRegister: () => new Promise<Response>((resolve) => {
        release = resolve
      }),
    })
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    const form = screen.getByRole('form', { name: 'Package registration' })
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1)
    await act(async () => {
      release(json(201, registration({ registrationCode: 'REG-0041', status: 'PENDING_PAYMENT', paymentId: 52 })))
    })
    expect(await screen.findByRole('dialog', { name: 'Payment summary' })).toBeInTheDocument()
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1)
  })

  it('TC-FE03-038 a backend error is shown above the summary and the form stays usable', async () => {
    const user = userEvent.setup()
    await openDetail({ onRegister: () => backendError(400, 'Cannot register for an inactive sport package') })
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    const summary = screen.getByRole('region', { name: 'Registration summary' })
    expect(await within(summary).findByRole('alert')).toHaveTextContent('Cannot register for an inactive sport package')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm registration' })).toBeEnabled()
  })

  it('TC-FE03-039 the real backend answer for a past start date goes under the date field', async () => {
    const user = userEvent.setup()
    // GlobalExceptionHandler.handleBusinessRuleException: 400 {timestamp, status, message}, no details.
    await openDetail({ onRegister: () => backendError(400, 'Start date cannot be in the past') })
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    const input = screen.getByLabelText(/Start date/)
    await waitFor(() => expect(input).toHaveAttribute('aria-invalid', 'true'))
    expect(screen.getByText('Start date cannot be in the past', { selector: '.field__error' })).toBeInTheDocument()
    await waitFor(() => expect(input).toHaveFocus())
    expect(within(screen.getByRole('region', { name: 'Registration summary' })).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('TC-FE03-040 an unknown package id shows "This package is not available"; Back keeps the filters', async () => {
    packagesBackend()
    signIn()
    renderApp('/member/packages/999?sport=1')
    expect(await screen.findByText('This package is not available')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Back to packages' })[0]).toHaveAttribute('href', '/member/packages?sport=1')
  })

  it('TC-FE03-041 when the cards cannot be loaded the discount is "Calculated when you confirm"', async () => {
    await openDetail({ cards: backendError(500, 'Internal server error') })
    expect(summaryValue('Membership discount')).toBe('Calculated when you confirm')
    expect(summaryValue('Total payable')).toBe('900.000 VND')
  })
})

describe('FE-03 review round 1', () => {
  async function openDetail(options: Options = {}, path = '/member/packages/4') {
    const fake = packagesBackend(options)
    signIn()
    renderApp(path)
    await screen.findByRole('form', { name: 'Package registration' })
    // Confirm waits for the registrations list (B3b); every detail test starts after it arrived.
    await waitFor(() => expect(screen.queryByText('Checking your registrations')).not.toBeInTheDocument())
    return fake
  }

  it('TC-FE03-042 single visit detail shows "1 session" (B1)', async () => {
    await openDetail({}, '/member/packages/1')
    expect(screen.getByRole('heading', { name: 'Football - Single visit - 1 session' })).toBeInTheDocument()
    const term = screen.getByText('Included sessions', { selector: 'dt' })
    expect(term.nextElementSibling).toHaveTextContent(/^1 session$/)
    expect(screen.queryByText(/1 sessions/)).not.toBeInTheDocument()
  })

  it('TC-FE03-043 a 60 day package is discounted for GOLD and the note states the same rule (B2)', async () => {
    const sixty = sportPackage({ id: 9, durationDays: 60, sessionCount: 16, priceAmount: 1000000 })
    await openList({ cards: [GOLD], packages: [...PACKAGES, sixty] })
    expect(finalPrice(/^Coach-led Football, 60 days/)).toBe('950.000 VND')
    const note = screen.getByText(/Your Gold card gives you/)
    expect(note).toHaveTextContent('Your Gold card gives you 5% off packages longer than a single visit.')
    expect(note).not.toHaveTextContent('30 day and 90 day')
  })

  it('TC-FE03-044 a pending registration of this package blocks Confirm and links to its payment (B3)', async () => {
    const { calls } = await openDetail({
      registrations: [registration({ packageId: 4, registrationCode: 'REG-0030', status: 'PENDING_PAYMENT', paymentId: 60 })],
    })
    const notice = await screen.findByText(/You already have a registration waiting for payment for this package/)
    expect(notice).toHaveTextContent('REG-0030')
    expect(screen.getByRole('link', { name: 'Continue to payment' })).toHaveAttribute('href', '/member/payments/60')
    expect(screen.getByRole('button', { name: 'Confirm registration' })).toBeDisabled()
    screen.getByRole('form', { name: 'Package registration' }).dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })

  it('TC-FE03-045 a pending registration of another package does not block (B3)', async () => {
    await openDetail({ registrations: [registration({ packageId: 6, status: 'PENDING_PAYMENT', paymentId: 61 })] })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Confirm registration' })).toBeEnabled())
    expect(screen.queryByText(/already have a registration waiting/)).not.toBeInTheDocument()
  })

  it('TC-FE03-046 if the registrations cannot be loaded nothing is blocked and nothing crashes (B3)', async () => {
    const user = userEvent.setup()
    await openDetail({ registrations: backendError(500, 'Internal server error') })
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    expect(await screen.findByRole('dialog', { name: 'Payment summary' })).toBeInTheDocument()
  })

  it('TC-FE03-047 a page left open past midnight cannot submit yesterday; the picker minimum follows (B4)', async () => {
    const user = userEvent.setup()
    vi.setSystemTime(new Date(2026, 9, 13, 23, 59))
    const { calls } = await openDetail()
    const input = screen.getByLabelText(/Start date/)
    expect(input).toHaveValue('2026-10-13')

    vi.setSystemTime(new Date(2026, 9, 14, 0, 1))
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    expect(await screen.findByText('Start date cannot be in the past', { selector: '.field__error' })).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
    expect(input).toHaveAttribute('min', '2026-10-14')
  })

  it('TC-FE03-048 while the cards load, eligible prices show "-" and "Checking your card discount" (M2, N1)', async () => {
    let release: (response: Response) => void = () => undefined
    // The cards answer stays open until the test releases it.
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/sports') return json(200, SPORTS)
      if (call.path === '/packages') return json(200, PACKAGES)
      if (call.path === '/membership-cards/my') {
        return new Promise<Response>((resolve) => {
          release = resolve
        })
      }
      return json(200, [])
    })
    signIn()
    renderApp('/member/packages')
    await screen.findByRole('list', { name: 'Sport packages' })
    expect(within(cardFor(/^Coach-led Football, 30 days/)).getByText(/Checking your card discount/)).toBeInTheDocument()
    expect(within(cardFor(/^Coach-led Football, 30 days/)).queryByText(/Original price/)).not.toBeInTheDocument()
    // Round 2 (N1): the big price waits for the card; a single visit keeps its price.
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('-')
    expect(finalPrice(/^Self-training Football, Single visit/)).toBe('80.000 VND')
    await act(async () => {
      release(json(200, [VIP]))
    })
    expect(finalPrice(/^Coach-led Football, 30 days/)).toBe('810.000 VND')
    expect(within(cardFor(/^Coach-led Football, 30 days/)).queryByText(/Checking your card discount/)).not.toBeInTheDocument()
  })

  it('TC-FE03-049 unknown URL filters are ignored: selects and list agree (M3)', async () => {
    packagesBackend()
    signIn()
    renderApp('/member/packages?sport=999&format=abc&duration=7')
    expect(await screen.findAllByRole('listitem')).toHaveLength(5)
    expect(screen.getByLabelText('Sport')).toHaveValue('')
    expect(screen.getByLabelText('Training type')).toHaveValue('')
    expect(screen.getByLabelText('Duration')).toHaveValue('')
    expect(within(cardFor(/^Coach-led Football, 30 days/)).getByRole('link', { name: /View package/ })).toHaveAttribute(
      'href',
      '/member/packages/4',
    )
  })
})

describe('FE-03 review round 2', () => {
  it('TC-FE03-052 Confirm waits for the registrations list: no POST while it loads (B3b)', async () => {
    const user = userEvent.setup()
    let release: (response: Response) => void = () => undefined
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/packages') return json(200, PACKAGES)
      if (call.path === '/membership-cards/my') return json(200, [])
      if (call.path === '/packages/registrations/my') {
        return new Promise<Response>((resolve) => {
          release = resolve
        })
      }
      if (call.path === '/packages/registrations' && call.method === 'POST') {
        return json(201, registration({ status: 'PENDING_PAYMENT', paymentId: 52 }))
      }
      return json(200, [])
    })
    signIn()
    renderApp('/member/packages/4')
    const form = await screen.findByRole('form', { name: 'Package registration' })

    const confirm = screen.getByRole('button', { name: 'Confirm registration' })
    expect(confirm).toBeDisabled()
    expect(screen.getByText('Checking your registrations')).toBeInTheDocument()
    await user.click(confirm)
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    expect(calls.some((call) => call.method === 'POST')).toBe(false)

    await act(async () => {
      release(json(200, []))
    })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Confirm registration' })).toBeEnabled())
    expect(screen.queryByText('Checking your registrations')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))
    expect(await screen.findByRole('dialog', { name: 'Payment summary' })).toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1)
  })
})

describe('FE-03 Payment Summary (F3-01, read only)', () => {
  it('TC-FE03-050 a payment that cannot be loaded shows an error with Retry', async () => {
    installFakeFetch((call) =>
      call.path === '/users/me' ? json(200, MEMBER) : backendError(404, 'Payment not found'),
    )
    signIn()
    renderApp('/member/payments/77')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not load the payment')
    expect(alert).toHaveTextContent('Payment not found')
  })

  it('TC-FE03-051 an invalid payment id makes no request', async () => {
    const { calls } = installFakeFetch((call) => (call.path === '/users/me' ? json(200, MEMBER) : json(200, payment())))
    signIn()
    renderApp('/member/payments/abc')
    expect(await screen.findByText('The payment link is not valid.')).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/payments'))).toBe(false)
  })
})
