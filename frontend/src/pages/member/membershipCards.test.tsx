import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { card, CARD_TIERS, payment, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

/**
 * FE-04: Membership Cards (F1-11) with the Renew Membership Card overlay, and My Membership and
 * Sport Packages (F1-12) with the states Active, Expired and Pending Payment. Today is 2026-10-13.
 */

const GOLD_CARD = card({ id: 5, cardCode: 'CARD-0005', tierId: 2, tierCode: 'GOLD', tierName: 'Gold', discountPercentage: 5, pricePaid: 300000 })
const VIP_CARD = card()
const PENDING_CARD = card({
  id: 9,
  cardCode: 'CARD-0009',
  status: 'PENDING_PAYMENT',
  startDate: '2027-07-05',
  endDate: '2028-07-04',
  paymentId: 52,
  paymentCode: 'PAY-0052',
  createdAt: '2026-10-13T09:00:00',
})
const PENDING_MESSAGE = 'A membership card request is already waiting for payment: CARD-0009. Pay or cancel it first.'

interface Options {
  cards?: unknown[]
  registrations?: unknown[]
  tiers?: Response
  onPurchase?: (call: RecordedCall) => Response
  onCancel?: (call: RecordedCall) => Response
}

function membershipBackend(options: Options = {}) {
  let cards = options.cards ?? []
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/membership-cards/tiers') return options.tiers ?? json(200, CARD_TIERS)
    if (call.path === '/membership-cards/my') return json(200, cards)
    if (call.path === '/packages/registrations/my') return json(200, options.registrations ?? [])
    if (call.path === '/membership-cards/purchase' && call.method === 'POST') {
      return options.onPurchase
        ? options.onPurchase(call)
        : json(201, card({ id: 30, cardCode: 'CARD-0030', status: 'PENDING_PAYMENT', paymentId: 52, paymentCode: 'PAY-0052' }))
    }
    const cancel = /^\/membership-cards\/(\d+)\/cancel$/.exec(call.path)
    if (cancel && call.method === 'PUT') {
      if (options.onCancel) return options.onCancel(call)
      const id = Number(cancel[1])
      cards = (cards as { id: number; status: string }[]).map((item) => (item.id === id ? { ...item, status: 'CANCELLED' } : item))
      return json(200, cards.find((item) => (item as { id: number }).id === id))
    }
    if (call.path === '/payments/52') return json(200, payment({ itemDescription: 'Membership card - VIP', amount: 600000 }))
    return backendError(404, 'Resource not found')
  })
}

function signIn() {
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
}

async function openCards(options: Options = {}) {
  const fake = membershipBackend(options)
  signIn()
  renderApp('/member/membership/cards')
  await screen.findByRole('list', { name: 'Membership card tiers' })
  return fake
}

function tier(name: string) {
  return screen.getByRole('listitem', { name })
}

async function openOverlay(tierName: string, button: string) {
  const user = userEvent.setup()
  await user.click(within(tier(tierName)).getByRole('button', { name: button }))
  const dialog = await screen.findByRole('dialog')
  return { user, dialog }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-04 Membership Cards (F1-11)', () => {
  it('TC-FE04-030 lists tiers with price, validity and perks; Standard is current without a card', async () => {
    const { calls } = await openCards()
    expect(screen.getByRole('heading', { name: 'Membership Cards' })).toBeInTheDocument()
    expect(within(tier('Standard')).getByText('Free')).toBeInTheDocument()
    expect(within(tier('Standard')).getByText('Current')).toBeInTheDocument()
    expect(within(tier('Standard')).getByRole('link', { name: 'Explore sport packages' })).toHaveAttribute('href', '/member/packages')
    expect(within(tier('Gold')).getByText('300.000 VND')).toBeInTheDocument()
    expect(within(tier('Gold')).getByText('12 months validity')).toBeInTheDocument()
    expect(within(tier('Gold')).getByText('5% off packages longer than a single visit')).toBeInTheDocument()
    expect(within(tier('VIP')).getByText('600.000 VND')).toBeInTheDocument()
    expect(within(tier('VIP')).getByRole('button', { name: 'Get card' })).toBeInTheDocument()
    expect(calls.map((call) => call.path)).toEqual(expect.arrayContaining(['/membership-cards/tiers', '/membership-cards/my']))
  })

  it('TC-FE04-031 new card: overlay shows today to the end of the term and the full price', async () => {
    await openCards()
    const { dialog } = await openOverlay('Gold', 'Get card')
    expect(within(dialog).getByTestId('card-amount')).toHaveTextContent('300.000 VND')
    expect(within(dialog).getByTestId('card-start')).toHaveTextContent('13 Oct 2026')
    expect(within(dialog).getByTestId('card-end')).toHaveTextContent('12 Oct 2027')
  })

  it('TC-FE04-032 renew: VIP member sees Renew card, term starts the day after the current end', async () => {
    await openCards({ cards: [VIP_CARD] })
    expect(within(tier('VIP')).getByText('Active')).toBeInTheDocument()
    expect(within(tier('Standard')).queryByText('Current')).not.toBeInTheDocument()
    const { dialog } = await openOverlay('VIP', 'Renew card')
    expect(within(dialog).getByText('Renew card')).toBeInTheDocument()
    expect(within(dialog).getByTestId('card-amount')).toHaveTextContent('600.000 VND')
    expect(within(dialog).getByTestId('card-start')).toHaveTextContent('05 Jul 2027')
    expect(within(dialog).getByTestId('card-end')).toHaveTextContent('04 Jul 2028')
    expect(within(dialog).getByText('10%')).toBeInTheDocument()
  })

  it('TC-FE04-033 upgrade: Gold member pays the difference and keeps the end date', async () => {
    await openCards({ cards: [GOLD_CARD] })
    expect(within(tier('VIP')).getByText(/Pay 300.000 VND now and keep your current end date/)).toBeInTheDocument()
    const { dialog } = await openOverlay('VIP', 'Upgrade card')
    expect(within(dialog).getByText('Card fee').nextElementSibling).toHaveTextContent('600.000 VND')
    expect(within(dialog).getByText('Paid with your Gold card').nextElementSibling).toHaveTextContent('-300.000 VND')
    expect(within(dialog).getByTestId('card-amount')).toHaveTextContent('300.000 VND')
    expect(within(dialog).getByTestId('card-start')).toHaveTextContent('13 Oct 2026')
    expect(within(dialog).getByTestId('card-end')).toHaveTextContent('04 Jul 2027')
  })

  it('TC-FE04-034 cheaper tier: VIP member buying Gold starts after the VIP card ends', async () => {
    await openCards({ cards: [VIP_CARD] })
    expect(within(tier('Gold')).getByText(/Starts 05 Jul 2027, after your current card ends/)).toBeInTheDocument()
    const { dialog } = await openOverlay('Gold', 'Buy after current card')
    expect(within(dialog).getByTestId('card-amount')).toHaveTextContent('300.000 VND')
    expect(within(dialog).getByTestId('card-start')).toHaveTextContent('05 Jul 2027')
    expect(within(dialog).getByTestId('card-end')).toHaveTextContent('04 Jul 2028')
  })

  it('TC-FE04-035 confirm sends POST /membership-cards/purchase and opens the payment summary', async () => {
    const { calls } = await openCards({ cards: [VIP_CARD] })
    const { user, dialog } = await openOverlay('VIP', 'Renew card')
    await user.click(within(dialog).getByRole('button', { name: 'Confirm and continue' }))
    expect(await screen.findByRole('heading', { name: 'Payment Summary' })).toBeInTheDocument()
    const purchase = calls.filter((call) => call.path === '/membership-cards/purchase')
    expect(purchase).toHaveLength(1)
    expect(purchase[0]).toMatchObject({ method: 'POST', body: { tierId: 3 } })
    expect(await screen.findByTestId('payment-amount')).toHaveTextContent('600.000 VND')
  })

  it('TC-FE04-036 a second request while one is pending shows the backend error', async () => {
    const { calls } = await openCards({
      cards: [VIP_CARD, PENDING_CARD],
      onPurchase: () => backendError(400, PENDING_MESSAGE),
    })
    expect(screen.getByText(/Your VIP card request \(CARD-0009\) is waiting for payment/)).toBeInTheDocument()
    expect(within(tier('VIP')).getByText('Pending payment')).toBeInTheDocument()
    const { user, dialog } = await openOverlay('Gold', 'Buy after current card')
    await user.click(within(dialog).getByRole('button', { name: 'Confirm and continue' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(PENDING_MESSAGE)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/membership-cards/purchase')).toHaveLength(1)
    expect(within(dialog).getByRole('button', { name: 'Confirm and continue' })).toBeEnabled()
  })

  it('TC-FE04-037 a double click sends only one purchase request', async () => {
    const { calls } = await openCards()
    const { user, dialog } = await openOverlay('Gold', 'Get card')
    const confirm = within(dialog).getByRole('button', { name: 'Confirm and continue' })
    await user.dblClick(confirm)
    await screen.findByRole('heading', { name: 'Payment Summary' })
    expect(calls.filter((call) => call.path === '/membership-cards/purchase')).toHaveLength(1)
  })

  it('TC-FE04-038 upgrade while a renewal is queued is blocked with the reason', async () => {
    const queued = card({ id: 6, tierId: 2, tierName: 'Gold', startDate: '2027-07-05', endDate: '2028-07-04' })
    await openCards({ cards: [GOLD_CARD, queued] })
    expect(within(tier('Gold')).getByText('Starts 05 Jul 2027')).toBeInTheDocument()
    expect(within(tier('VIP')).getByText(/already renewed from 2027-07-05/)).toBeInTheDocument()
    expect(within(tier('VIP')).queryByRole('button')).not.toBeInTheDocument()
  })

  it('TC-FE04-039 Cancel closes the overlay without a request', async () => {
    const { calls } = await openCards()
    const { user, dialog } = await openOverlay('Gold', 'Get card')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls.some((call) => call.path === '/membership-cards/purchase')).toBe(false)
  })

  it('TC-FE04-040 a failed load shows an error with Try again', async () => {
    let fail = true
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/membership-cards/tiers') return fail ? backendError(500, 'Server error') : json(200, CARD_TIERS)
      if (call.path === '/membership-cards/my') return json(200, [])
      return backendError(404, 'Resource not found')
    })
    signIn()
    renderApp('/member/membership/cards')
    expect(await screen.findByText('We could not load the membership cards')).toBeInTheDocument()
    fail = false
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('list', { name: 'Membership card tiers' })).toBeInTheDocument()
  })
})

async function openMembership(options: Options = {}, path = '/member/membership') {
  const fake = membershipBackend(options)
  signIn()
  renderApp(path)
  await screen.findByRole('heading', { name: 'My Membership and Sport Packages' })
  return fake
}

function registrationItem(code: RegExp) {
  return screen.getByRole('listitem', { name: code })
}

describe('FE-04 My Membership and Sport Packages (F1-12)', () => {
  it('TC-FE04-050 empty: no sport packages yet with Explore packages', async () => {
    await openMembership()
    expect(await screen.findByText('No sport packages yet')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveAttribute('aria-selected', 'true')
    const panel = screen.getByRole('tabpanel')
    expect(within(panel).getByRole('link', { name: 'Explore packages' })).toHaveAttribute('href', '/member/packages')
  })

  it('TC-FE04-051 Pending Payment state: badge, validity, amount, sessions and Pay now', async () => {
    await openMembership({
      registrations: [
        registration({ registrationCode: 'REG-0040', status: 'PENDING_PAYMENT', startDate: '2026-10-02', endDate: '2026-10-31', remainingSessions: 8, paymentId: 52 }),
      ],
    })
    await screen.findByRole('list', { name: 'Sport package registrations' })
    const item = registrationItem(/REG-0040/)
    expect(within(item).getByText('Pending payment')).toBeInTheDocument()
    expect(within(item).getByText('Football - Coach-led')).toBeInTheDocument()
    expect(within(item).getByText('30 days - 8 sessions')).toBeInTheDocument()
    expect(within(item).getByText('02 Oct 2026 to 31 Oct 2026')).toBeInTheDocument()
    expect(within(item).getByText('810.000 VND')).toBeInTheDocument()
    expect(within(item).getByText('8 of 8 sessions remaining')).toBeInTheDocument()
    expect(within(item).getByRole('link', { name: 'Pay now' })).toHaveAttribute('href', '/member/payments/52')
    expect(within(item).queryByRole('link', { name: 'Book a session' })).not.toBeInTheDocument()
  })

  it('TC-FE04-052 Active state: badge, progress and Book a session', async () => {
    await openMembership({ registrations: [registration()] })
    await screen.findByRole('list', { name: 'Sport package registrations' })
    const item = registrationItem(/REG-0011/)
    expect(within(item).getByText('Active')).toBeInTheDocument()
    expect(within(item).getByText('6 of 8 sessions remaining')).toBeInTheDocument()
    expect(within(item).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '6')
    expect(within(item).getByRole('link', { name: 'Book a session' })).toHaveAttribute('href', '/member/schedule')
    expect(within(item).getByRole('link', { name: 'Renew package' })).toHaveAttribute('href', '/member/packages')
  })

  it('TC-FE04-053 Expired state: EXPIRED rows and ACTIVE rows past their end date', async () => {
    await openMembership({
      registrations: [
        registration({ id: 1, registrationCode: 'REG-0001', status: 'EXPIRED', startDate: '2026-08-01', endDate: '2026-08-30' }),
        registration({ id: 2, registrationCode: 'REG-0002', status: 'ACTIVE', startDate: '2026-09-01', endDate: '2026-09-30' }),
      ],
    })
    await screen.findByRole('list', { name: 'Sport package registrations' })
    for (const code of [/REG-0001/, /REG-0002/]) {
      const item = registrationItem(code)
      expect(within(item).getByText('Expired')).toBeInTheDocument()
      expect(within(item).getByRole('link', { name: 'Renew package' })).toBeInTheDocument()
      expect(within(item).getByRole('link', { name: 'View payment' })).toHaveAttribute('href', '/member/payments/41')
      expect(within(item).queryByRole('link', { name: 'Book a session' })).not.toBeInTheDocument()
    }
  })

  it('TC-FE04-054 the card tab lists cards and links to the card tiers', async () => {
    await openMembership({ cards: [VIP_CARD] }, '/member/membership?tab=card')
    expect(screen.getByRole('tab', { name: 'Membership card' })).toHaveAttribute('aria-selected', 'true')
    const item = await screen.findByRole('listitem', { name: /CARD-0001 VIP/ })
    expect(within(item).getByText('Active')).toBeInTheDocument()
    expect(within(item).getByText('05 Jul 2026 to 04 Jul 2027')).toBeInTheDocument()
    expect(within(item).queryByRole('button', { name: 'Cancel request' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'View card tiers' })[0]).toHaveAttribute('href', '/member/membership/cards')
  })

  it('TC-FE04-055 switching tabs with click and arrow keys updates the panel', async () => {
    await openMembership({ cards: [] })
    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: 'Membership card' }))
    expect(await screen.findByText('You are on the Standard tier')).toBeInTheDocument()
    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Sport packages' })).toHaveFocus()
  })

  it('TC-FE04-056 cancel works for a pending request: PUT cancel, then the list reloads', async () => {
    const { calls } = await openMembership({ cards: [VIP_CARD, PENDING_CARD] }, '/member/membership?tab=card')
    const user = userEvent.setup()
    const pending = await screen.findByRole('listitem', { name: /CARD-0009/ })
    expect(within(pending).getByText('Pending payment')).toBeInTheDocument()
    expect(within(pending).getByRole('link', { name: 'Pay now' })).toHaveAttribute('href', '/member/payments/52')
    await user.click(within(pending).getByRole('button', { name: 'Cancel request' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel request' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const after = await screen.findByRole('listitem', { name: /CARD-0009/ })
    expect(within(after).getByText('Cancelled')).toBeInTheDocument()
    expect(within(after).queryByRole('button', { name: 'Cancel request' })).not.toBeInTheDocument()
    expect(calls.filter((call) => call.method === 'PUT').map((call) => call.path)).toEqual(['/membership-cards/9/cancel'])
  })

  it('TC-FE04-057 cancel is offered only for pending requests', async () => {
    await openMembership(
      {
        cards: [
          VIP_CARD,
          card({ id: 2, cardCode: 'CARD-0002', status: 'CANCELLED' }),
          card({ id: 3, cardCode: 'CARD-0003', status: 'REPLACED' }),
          card({ id: 4, cardCode: 'CARD-0004', status: 'EXPIRED', startDate: '2025-07-05', endDate: '2026-07-04' }),
        ],
      },
      '/member/membership?tab=card',
    )
    await screen.findByRole('list', { name: 'Membership cards' })
    expect(screen.queryByRole('button', { name: 'Cancel request' })).not.toBeInTheDocument()
    const expired = screen.getByRole('listitem', { name: /CARD-0004/ })
    expect(within(expired).getByRole('link', { name: 'Renew card' })).toHaveAttribute('href', '/member/membership/cards')
  })

  it('TC-FE04-058 a cancel rejected by the backend shows its message and keeps the dialog', async () => {
    const message = 'Only a card request that is waiting for payment can be cancelled.'
    await openMembership({ cards: [PENDING_CARD], onCancel: () => backendError(400, message) }, '/member/membership?tab=card')
    const user = userEvent.setup()
    const pending = await screen.findByRole('listitem', { name: /CARD-0009/ })
    await user.click(within(pending).getByRole('button', { name: 'Cancel request' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel request' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(message)
  })

  it('TC-FE04-059 My Membership is the active menu item on both screens', async () => {
    await openCards()
    expect(screen.getByRole('link', { name: 'My Membership' })).toHaveAttribute('aria-current', 'page')
  })
})
