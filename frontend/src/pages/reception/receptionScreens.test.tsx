import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { card, payment, registration, sportPackage } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

/**
 * FE-05: Member Search and Profile (F1-13), Register and Renew (F1-14), Check-in (F1-15) and the
 * front desk payment confirmation. Today is fixed at 2026-10-13.
 */

const RECEPTIONIST = {
  id: 9,
  email: 'reception@sportify.demo',
  fullName: 'Jamie Tran',
  phone: '0901234599',
  role: 'RECEPTIONIST',
  status: 'ACTIVE',
}

const TAYLOR = { userId: 2, memberCode: 'MB-0248', fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', currentLevel: 'BEGINNER' }
const TAYLOR_B = { ...TAYLOR, userId: 3, memberCode: 'MB-0301', email: 'taylor.b@sportify.demo', phone: '0901234511' }

const ACTIVE_BADMINTON = registration({
  id: 10,
  registrationCode: 'REG-ACTIVE-001',
  packageId: 2,
  packageName: 'Badminton - 30-day package',
  sportName: 'Badminton',
  trainingFormat: 'SELF_TRAINING',
  paidAmount: 405000,
  startDate: '2026-09-25',
  endDate: '2026-10-24',
  status: 'ACTIVE',
  paymentId: 30,
  paymentCode: 'PAY-0030',
})
const PENDING_FOOTBALL = registration({
  id: 11,
  registrationCode: 'REG-ONLINE-001',
  status: 'PENDING_PAYMENT',
  paymentId: 41,
  paymentCode: 'PAY-0041',
  startDate: '2026-10-02',
  endDate: '2026-10-31',
  activatedAt: null,
})
const VIP_CARD = card({ tierId: 3 })

const OVERVIEW = { member: { ...TAYLOR, status: 'ACTIVE' }, cards: [VIP_CARD], registrations: [ACTIVE_BADMINTON, PENDING_FOOTBALL] }

const TIERS = [
  { id: 1, code: 'STANDARD', tierRank: 1, name: 'Standard', price: 0, durationMonths: null, discountPercentage: 0, description: null, isActive: true },
  { id: 2, code: 'GOLD', tierRank: 3, name: 'Gold', price: 300000, durationMonths: 12, discountPercentage: 5, description: null, isActive: true },
  { id: 3, code: 'VIP', tierRank: 4, name: 'VIP', price: 600000, durationMonths: 12, discountPercentage: 10, description: null, isActive: true },
]

const PACKAGES = [
  sportPackage({ id: 2, sportName: 'Badminton', sportCode: 'BADMINTON', trainingFormat: 'SELF_TRAINING', priceAmount: 450000 }),
  sportPackage({ id: 4 }),
]

interface Options {
  me?: unknown
  search?: (query: string) => unknown[]
  overview?: unknown
  onCreateMember?: (call: RecordedCall) => Response
  onSellPackage?: (call: RecordedCall) => Response
  onSellCard?: (call: RecordedCall) => Response
  onCheckIn?: (call: RecordedCall) => Response
  checkIns?: unknown[] | Response
  payments?: Record<number, unknown>
  overviewError?: Response
}

function receptionBackend(options: Options = {}) {
  const payments: Record<number, unknown> = {
    41: payment({ id: 41, code: 'PAY-0041', amount: 810000, memberCode: 'MB-0248' }),
    ...options.payments,
  }
  // Cards of member 2 as the backend holds them; a desk card sale adds the sold card.
  const cards: unknown[] = [VIP_CARD]
  return installFakeFetch((call) => {
    const url = new URL(`http://x${call.path}`)
    if (call.path === '/users/me') return json(200, options.me ?? RECEPTIONIST)
    if (url.pathname === '/members' && call.method === 'GET') {
      const query = url.searchParams.get('query') ?? ''
      const content = options.search ? options.search(query) : [TAYLOR]
      return json(200, { content, number: 0, size: 10, totalElements: content.length, totalPages: 1 })
    }
    if (url.pathname === '/members' && call.method === 'POST') {
      return options.onCreateMember ? options.onCreateMember(call) : json(201, { ...TAYLOR_B, userId: 7, memberCode: 'MB-0007', fullName: 'An Le' })
    }
    const overview = /^\/members\/(\d+)\/overview$/.exec(url.pathname)
    if (overview) {
      if (options.overviewError) return options.overviewError
      return json(200, options.overview ?? { ...OVERVIEW, cards, member: { ...OVERVIEW.member, userId: Number(overview[1]) } })
    }
    if (call.path === '/membership-cards/member/2') return json(200, cards)
    if (call.path === '/packages/registrations/member/2') return json(200, OVERVIEW.registrations)
    if (call.path === '/packages') return json(200, PACKAGES)
    if (call.path === '/membership-cards/tiers') return json(200, TIERS)
    if (call.path === '/packages/registrations' && call.method === 'POST') {
      return options.onSellPackage
        ? options.onSellPackage(call)
        : json(201, registration({ id: 60, registrationCode: 'REG-0060', status: 'ACTIVE', channel: 'RECEPTION', startDate: '2026-10-13', endDate: '2026-11-11', paymentId: 60, paymentCode: 'PAY-0060', paidAmount: 810000 }))
    }
    if (call.path === '/membership-cards/purchase' && call.method === 'POST') {
      if (options.onSellCard) return options.onSellCard(call)
      const sold = card({ id: 70, cardCode: 'CARD-0070', tierId: 3, status: 'ACTIVE', startDate: '2027-07-05', endDate: '2028-07-04', paymentId: 70, paymentCode: 'PAY-0070', pricePaid: 600000 })
      cards.push(sold)
      return json(201, sold)
    }
    if (call.path === '/membership-cards/pending') {
      return json(200, [card({ id: 5, cardCode: 'CARD-0005', tierName: 'Gold', status: 'PENDING_PAYMENT', paymentId: 45, paymentCode: 'PAY-0045', pricePaid: 300000, createdAt: '2026-10-12T09:00:00' })])
    }
    if (call.path === '/packages/registrations/pending') return json(200, [PENDING_FOOTBALL])
    if (call.path === '/payments/41/confirm' && call.method === 'PUT') {
      payments[41] = payment({ id: 41, code: 'PAY-0041', status: 'SUCCESS', method: 'CASH', amount: 810000, paymentTime: '2026-10-13T09:40:00' })
      return json(200, payments[41])
    }
    if (call.path === '/invoices/by-payment/41') {
      return json(200, { id: 1041, invoiceNumber: 'INV-1041', paymentId: 41, memberId: 2, subtotalAmount: 900000, discountAmount: 90000, totalAmount: 810000, status: 'PAID', issueDate: '2026-10-13' })
    }
    if (call.path === '/check-ins' && call.method === 'POST') {
      return options.onCheckIn
        ? options.onCheckIn(call)
        : json(201, { id: 80, memberId: 2, memberName: 'Taylor Nguyen', bookingId: 21, bookingCode: 'BK-0021', packageRegistrationId: 10, checkedInAt: '2026-10-13T17:02:00', result: 'ALLOWED', denialReason: null, note: null })
    }
    if (url.pathname === '/check-ins' && call.method === 'GET') {
      return options.checkIns instanceof Response ? options.checkIns.clone() : json(200, options.checkIns ?? [])
    }
    const paymentMatch = /^\/payments\/(\d+)$/.exec(url.pathname)
    if (paymentMatch && payments[Number(paymentMatch[1])]) return json(200, payments[Number(paymentMatch[1])])
    return backendError(404, 'Resource not found')
  })
}

function signIn() {
  tokenStorage.startSession({ accessToken: 'desk-access', refreshToken: 'desk-refresh' })
}

async function open(path: string, options: Options = {}) {
  const fake = receptionBackend(options)
  signIn()
  renderApp(path)
  await screen.findByRole('heading', { level: 1 })
  return fake
}

async function search(text: string, button = 'Search') {
  const user = userEvent.setup()
  const box = await screen.findByRole('textbox', { name: 'Name, phone or email' })
  await user.clear(box)
  await user.type(box, text)
  await user.click(screen.getByRole('button', { name: button }))
  return user
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 13, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-05 Member Search and Profile (F1-13)', () => {
  it('TC-FE05-030 a single result opens the member overview with card, discount and registrations', async () => {
    const { calls } = await open('/reception')
    expect(screen.getByRole('heading', { name: 'Member Search and Profile' })).toBeInTheDocument()
    await search('Taylor')
    const profile = await screen.findByRole('region', { name: 'Member profile' })
    expect(calls.filter((call) => call.path.startsWith('/members?')).map((call) => call.path)).toEqual([
      '/members?query=Taylor&page=0&size=10',
    ])
    expect(calls.some((call) => call.path === '/members/2/overview')).toBe(true)
    expect(within(profile).getByText('MB-0248')).toBeInTheDocument()
    expect(within(profile).getByText('VIP - Active')).toBeInTheDocument()
    expect(within(profile).getByText('04 Jul 2027')).toBeInTheDocument()
    expect(within(profile).getByText('10% on eligible packages')).toBeInTheDocument()
    const list = within(profile).getByRole('list', { name: 'Sport registrations' })
    const items = within(list).getAllByRole('listitem')
    expect(items[0]).toHaveAccessibleName(/REG-ONLINE-001/)
    expect(within(items[0]).getByRole('button', { name: 'Continue to payment' })).toBeInTheDocument()
    expect(within(items[1]).getByRole('link', { name: 'Renew package' })).toHaveAttribute(
      'href',
      '/reception/register?memberId=2&packageId=2',
    )
    expect(within(profile).getByRole('link', { name: 'Check-in' })).toHaveAttribute('href', '/reception/check-in?code=MB-0248')
  })

  it('TC-FE05-031 several results are listed and the chosen member is opened', async () => {
    const { calls } = await open('/reception', { search: () => [TAYLOR, TAYLOR_B] })
    const user = await search('Taylor')
    const results = await screen.findByRole('list', { name: 'Search results' })
    expect(within(results).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('2 members found')).toBeInTheDocument()
    await user.click(within(results).getByRole('button', { name: 'Select Taylor Nguyen MB-0301' }))
    await screen.findByRole('region', { name: 'Member profile' })
    expect(calls.some((call) => call.path === '/members/3/overview')).toBe(true)
  })

  it('TC-FE05-032 an empty search is refused without calling the backend', async () => {
    const { calls } = await open('/reception')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(screen.getByText('Enter a name, phone number or email')).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/members'))).toBe(false)
  })

  it('TC-FE05-033 Member Not Found offers Create member; the new member opens after POST /members', async () => {
    const { calls } = await open('/reception', { search: () => [] })
    const user = await search('An Le')
    expect(await screen.findByRole('heading', { name: 'Member not found' })).toBeInTheDocument()
    const notFound = screen.getByText(/No member matches "An Le"/).closest('.state') as HTMLElement
    await user.click(within(notFound).getByRole('button', { name: 'Create member' }))
    const dialog = await screen.findByRole('dialog', { name: 'Create member' })
    await user.click(within(dialog).getByRole('button', { name: 'Save member' }))
    expect(within(dialog).getByText('Full name is required')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/members' && call.method === 'POST')).toBe(false)

    await user.type(within(dialog).getByLabelText(/Full name/), 'An Le')
    await user.type(within(dialog).getByLabelText(/Phone number/), '0901234522')
    await user.type(within(dialog).getByLabelText(/Email address/), 'an.le@sportify.demo')
    await user.click(within(dialog).getByRole('button', { name: 'Save member' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = calls.find((call) => call.path === '/members' && call.method === 'POST')
    expect(post?.body).toEqual({
      fullName: 'An Le',
      phone: '0901234522',
      email: 'an.le@sportify.demo',
      dateOfBirth: null,
      mainGoal: 'IMPROVE_FITNESS',
      currentLevel: 'BEGINNER',
    })
    await screen.findByRole('region', { name: 'Member profile' })
    expect(calls.some((call) => call.path === '/members/7/overview')).toBe(true)
  })

  it('TC-FE05-034 a duplicate phone or email (409) keeps the form open with the backend message', async () => {
    await open('/reception', { onCreateMember: () => backendError(409, 'Email is already registered.') })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Create member' }))
    const dialog = await screen.findByRole('dialog', { name: 'Create member' })
    await user.type(within(dialog).getByLabelText(/Full name/), 'Taylor Nguyen')
    await user.type(within(dialog).getByLabelText(/Phone number/), '0901234567')
    await user.type(within(dialog).getByLabelText(/Email address/), 'member@sportify.demo')
    await user.click(within(dialog).getByRole('button', { name: 'Save member' }))
    expect(await within(dialog).findByText(/Email is already registered\. Search for the existing profile/)).toBeInTheDocument()
  })

  it('TC-FE05-035 Payment Handoff opens the confirmation; confirming returns to the profile in the After Payment state', async () => {
    const { calls } = await open('/reception?q=Taylor&memberId=2')
    const user = userEvent.setup()
    const profile = await screen.findByRole('region', { name: 'Member profile' })
    await user.click(within(profile).getByRole('button', { name: 'Continue to payment' }))
    const dialog = await screen.findByRole('dialog', { name: 'Review payment request' })
    expect(within(dialog).getByText('Taylor Nguyen - REG-ONLINE-001')).toBeInTheDocument()
    expect(within(dialog).getByText('810.000 VND')).toBeInTheDocument()
    expect(within(dialog).getByText('90.000 VND')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('link', { name: 'Open payment confirmation' }))

    expect(await screen.findByRole('heading', { name: 'Verify and Record Payment' })).toBeInTheDocument()
    const confirm = screen.getByRole('form', { name: 'Verify and record payment' })
    await user.selectOptions(within(confirm).getByLabelText(/Payment method/), 'CASH')
    await user.type(within(confirm).getByLabelText(/Amount received/), '810000')
    await user.click(within(confirm).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    await user.click(within(confirm).getByRole('button', { name: 'Confirm payment' }))
    expect(await screen.findByRole('heading', { name: 'Payment Confirmed and Receipt' })).toBeInTheDocument()
    const confirmCall = calls.find((call) => call.path === '/payments/41/confirm')
    expect(confirmCall?.method).toBe('PUT')
    expect(confirmCall?.body).toEqual({ paymentMethod: 'CASH', paymentReference: null, amountReceived: 810000, note: null })

    await user.click(screen.getByRole('link', { name: 'Back to member profile' }))
    expect(await screen.findByText(/Payment PAY-0041 is confirmed and the request is active/)).toBeInTheDocument()
  })

  it('TC-FE05-036 a paid reference typed in the URL never shows the After Payment notice', async () => {
    await open('/reception?memberId=2&paid=ANY')
    await screen.findByRole('region', { name: 'Member profile' })
    expect(screen.queryByText(/is confirmed and the request is active/)).not.toBeInTheDocument()
  })

  it('TC-FE05-037 only the proposed 404 "Member not found" reads as a missing member', async () => {
    await open('/reception?memberId=2', { overviewError: backendError(404, 'Resource not found') })
    expect(await screen.findByRole('heading', { name: 'We could not load the member profile' })).toBeInTheDocument()
    expect(screen.getByText('Resource not found')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Member not found' })).not.toBeInTheDocument()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
    await open('/reception?memberId=2', { overviewError: backendError(404, 'Member not found') })
    expect(await screen.findByRole('heading', { name: 'Member not found' })).toBeInTheDocument()
  })
})

describe('FE-05 Register and Renew (F1-14)', () => {
  it('TC-FE05-040 without a member the page asks to choose one first', async () => {
    await open('/reception/register')
    expect(screen.getByRole('heading', { name: 'Register and Renew' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Choose a member' })).toBeInTheDocument()
    await search('Taylor', 'Find member')
    expect(await screen.findByRole('form', { name: 'Sell a sport package' })).toBeInTheDocument()
  })

  it('TC-FE05-041 sells a package at the desk with cash and shows the receipt link', async () => {
    const { calls } = await open('/reception/register?memberId=2')
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    await user.selectOptions(within(form).getByLabelText(/^Package/), '4')
    expect(within(form).getByLabelText(/Start date/)).toHaveValue('2026-10-13')
    const summary = screen.getByRole('region', { name: 'Fee summary' })
    expect(within(summary).getByText('New registration')).toBeInTheDocument()
    expect(within(summary).getByText('10% - 90.000 VND')).toBeInTheDocument()
    expect(within(summary).getByTestId('total-payable')).toHaveTextContent('810.000 VND')
    expect(within(summary).getByText('13 Oct 2026 to 11 Nov 2026')).toBeInTheDocument()

    await user.type(within(form).getByLabelText(/Amount received/), '1.000.000')
    expect(screen.getByTestId('change-due')).toHaveTextContent('190.000 VND')
    await user.click(within(form).getByRole('button', { name: 'Record payment and register' }))

    const receipt = await screen.findByRole('region', { name: 'Sale recorded' })
    const post = calls.find((call) => call.path === '/packages/registrations' && call.method === 'POST')
    expect(post?.body).toEqual({
      memberId: 2,
      packageId: 4,
      startDate: '2026-10-13',
      channel: 'RECEPTION',
      paymentMethod: 'CASH',
      paymentReference: null,
      amountReceived: 1000000,
    })
    expect(within(receipt).getByText(/is active\. Payment PAY-0060 is recorded/)).toBeInTheDocument()
    expect(within(receipt).getByText('190.000 VND')).toBeInTheDocument()
    expect(within(receipt).getByRole('link', { name: 'View receipt' })).toHaveAttribute('href', '/reception/payments/60')
  })

  it('TC-FE05-042 a transfer needs its reference and the exact amount before anything is sent', async () => {
    const { calls } = await open('/reception/register?memberId=2')
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    await user.selectOptions(within(form).getByLabelText(/^Package/), '4')
    await user.selectOptions(within(form).getByLabelText(/Payment method/), 'BANK_TRANSFER')
    await user.type(within(form).getByLabelText(/Amount received/), '800000')
    await user.click(within(form).getByRole('button', { name: 'Record payment and register' }))
    expect(within(form).getByText('Payment reference is required for this method')).toBeInTheDocument()
    expect(within(form).getByText('Amount received is less than the amount payable')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/packages/registrations' && call.method === 'POST')).toBe(false)
  })

  it('TC-FE05-043 Renew package preselects the package and starts after the current registration ends', async () => {
    await open('/reception/register?memberId=2&packageId=2')
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    expect(within(form).getByLabelText(/^Package/)).toHaveValue('2')
    expect(within(form).getByLabelText(/Start date/)).toHaveValue('2026-10-25')
    expect(within(screen.getByRole('region', { name: 'Fee summary' })).getByText('Renewal')).toBeInTheDocument()
  })

  it('TC-FE05-044 a backend error stays on the form with its message', async () => {
    await open('/reception/register?memberId=2', {
      onSellPackage: () => backendError(400, 'Validation Error', { amountReceived: 'Amount received is below the price' }),
    })
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    await user.selectOptions(within(form).getByLabelText(/^Package/), '4')
    await user.click(within(form).getByRole('button', { name: 'Use exact amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record payment and register' }))
    expect(await within(form).findByText('Amount received is below the price')).toBeInTheDocument()
  })

  it('TC-FE05-045 Membership Card state: renewing VIP shows the new term and sends the card sale', async () => {
    const { calls } = await open('/reception/register?memberId=2&tab=card')
    const user = userEvent.setup()
    expect(screen.getByRole('tab', { name: 'Membership card' })).toHaveAttribute('aria-selected', 'true')
    const form = await screen.findByRole('form', { name: 'Sell a membership card' })
    expect(within(form).getByLabelText(/Membership card/).querySelectorAll('option')).toHaveLength(3)
    await user.selectOptions(within(form).getByLabelText(/Membership card/), '3')
    const summary = screen.getByRole('region', { name: 'Fee summary' })
    expect(within(summary).getByText('Renew')).toBeInTheDocument()
    expect(within(summary).getByText('05 Jul 2027 to 04 Jul 2028')).toBeInTheDocument()
    expect(within(summary).getByTestId('total-payable')).toHaveTextContent('600.000 VND')
    await user.selectOptions(within(form).getByLabelText(/Payment method/), 'CREDIT_CARD')
    await user.type(within(form).getByLabelText(/Payment reference/), 'POS-7781')
    await user.click(within(form).getByRole('button', { name: 'Use exact amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record payment and issue card' }))
    const receipt = await screen.findByRole('region', { name: 'Sale recorded' })
    const post = calls.find((call) => call.path === '/membership-cards/purchase')
    expect(post?.body).toEqual({ memberId: 2, tierId: 3, paymentMethod: 'CREDIT_CARD', paymentReference: 'POS-7781', amountReceived: 600000 })
    expect(within(receipt).getByRole('link', { name: 'View receipt' })).toHaveAttribute('href', '/reception/payments/70')
  })

  it('TC-FE05-046 a sale answered as pending payment (hypothetical backend, not main) links to the confirmation', async () => {
    await open('/reception/register?memberId=2', {
      onSellPackage: () =>
        json(201, registration({ id: 61, registrationCode: 'REG-0061', status: 'PENDING_PAYMENT', paymentId: 61, paymentCode: 'PAY-0061' })),
    })
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    await user.selectOptions(within(form).getByLabelText(/^Package/), '4')
    await user.click(within(form).getByRole('button', { name: 'Use exact amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record payment and register' }))
    const receipt = await screen.findByRole('region', { name: 'Sale recorded' })
    expect(within(receipt).getByText(/saved as pending payment/)).toBeInTheDocument()
    expect(within(receipt).getByRole('link', { name: 'Confirm payment' })).toHaveAttribute(
      'href',
      '/reception/payments/61?memberId=2',
    )
  })

  it('TC-FE05-048 main without BE-19: ACTIVE with no payment record never claims a recorded payment', async () => {
    await open('/reception/register?memberId=2', {
      onSellPackage: () =>
        json(201, registration({ id: 62, registrationCode: 'REG-0062', status: 'ACTIVE', channel: 'RECEPTION', paymentId: null, paymentCode: null })),
    })
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Sell a sport package' })
    await user.selectOptions(within(form).getByLabelText(/^Package/), '4')
    await user.click(within(form).getByRole('button', { name: 'Use exact amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record payment and register' }))
    const receipt = await screen.findByRole('region', { name: 'Sale recorded' })
    expect(within(receipt).getByText(/is active, but no payment record was created \(needs BE-19\)/)).toBeInTheDocument()
    expect(within(receipt).getByText('No payment record')).toBeInTheDocument()
    expect(within(receipt).queryByText(/payment is recorded|is recorded\./i)).not.toBeInTheDocument()
    expect(within(receipt).queryByRole('link', { name: 'View receipt' })).not.toBeInTheDocument()
  })

  it('TC-FE05-049 New sale uses the reloaded overview: VIP again is a renewal after the new end date', async () => {
    const { calls } = await open('/reception/register?memberId=2&tab=card')
    const user = userEvent.setup()
    let form = await screen.findByRole('form', { name: 'Sell a membership card' })
    await user.selectOptions(within(form).getByLabelText(/Membership card/), '3')
    await user.click(within(form).getByRole('button', { name: 'Use exact amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record payment and issue card' }))
    const receipt = await screen.findByRole('region', { name: 'Sale recorded' })
    await user.click(within(receipt).getByRole('button', { name: 'New sale' }))
    form = await screen.findByRole('form', { name: 'Sell a membership card' })
    await user.selectOptions(within(form).getByLabelText(/Membership card/), '3')
    const summary = screen.getByRole('region', { name: 'Fee summary' })
    expect(within(summary).getByText('Renew')).toBeInTheDocument()
    expect(within(summary).getByText('05 Jul 2028 to 04 Jul 2029')).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/members/2/overview')).toHaveLength(2)
  })

  it('TC-FE05-047 the pending tab lists online requests oldest first and opens the confirm screen', async () => {
    const { calls } = await open('/reception/register?tab=pending')
    const user = userEvent.setup()
    const table = await screen.findByRole('table', { name: 'Pending online requests' })
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(3))
    const rows = within(table).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('REG-ONLINE-001')
    expect(rows[2]).toHaveTextContent('CARD-0005')
    expect(calls.some((call) => call.path === '/membership-cards/pending')).toBe(true)
    expect(calls.some((call) => call.path === '/packages/registrations/pending')).toBe(true)
    await user.click(within(rows[1]).getByRole('link', { name: 'Confirm payment for REG-ONLINE-001' }))
    expect(await screen.findByRole('heading', { name: 'Verify and Record Payment' })).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Verify and record payment' })).toBeInTheDocument()
  })
})

describe('FE-05 Check-in (F1-15)', () => {
  it('TC-FE05-050 Recorded: POST /check-ins with the code, result shown and today list reloaded', async () => {
    const { calls } = await open('/reception/check-in?code=MB-0248')
    const user = userEvent.setup()
    expect(screen.getByLabelText(/Member code or card scan/)).toHaveValue('MB-0248')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    const result = await screen.findByRole('status', { name: 'Check-in recorded' })
    expect(within(result).getByText('Taylor Nguyen')).toBeInTheDocument()
    expect(within(result).getByText('17:02')).toBeInTheDocument()
    expect(within(result).getByText('BK-0021')).toBeInTheDocument()
    expect(calls.find((call) => call.path === '/check-ins' && call.method === 'POST')?.body).toEqual({
      identifier: 'MB-0248',
      note: null,
    })
    expect(calls.filter((call) => call.path === '/check-ins?date=2026-10-13')).toHaveLength(2)
    expect(screen.getByLabelText(/Member code or card scan/)).toHaveValue('')
  })

  it('TC-FE05-051 Rejected: the reason from the backend and the way to Register or renew', async () => {
    await open('/reception/check-in', {
      onCheckIn: () =>
        json(201, { id: 81, memberId: 2, memberName: 'Taylor Nguyen', bookingId: null, bookingCode: null, packageRegistrationId: null, checkedInAt: '2026-10-13T17:05:00', result: 'DENIED', denialReason: 'No confirmed booking for today', note: null }),
      checkIns: [
        { id: 81, memberId: 2, memberName: 'Taylor Nguyen', bookingId: null, bookingCode: null, packageRegistrationId: null, checkedInAt: '2026-10-13T17:05:00', result: 'DENIED', denialReason: 'No confirmed booking for today', note: null },
      ],
    })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/Member code or card scan/), 'MB-0248')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    const result = await screen.findByRole('status', { name: 'Check-in rejected' })
    expect(within(result).getByTestId('denial-reason')).toHaveTextContent('No confirmed booking for today')
    expect(within(result).getByRole('link', { name: 'Register or renew' })).toHaveAttribute('href', '/reception/register?memberId=2')
    const table = screen.getByRole('table', { name: "Today's check-ins" })
    expect(within(table).getByText('Rejected')).toBeInTheDocument()
  })

  it('TC-FE05-052 an empty code is refused; an unknown code is Rejected with "Member not found" and stays for correction', async () => {
    const { calls } = await open('/reception/check-in', {
      onCheckIn: (call) =>
        (call.body as { identifier: string }).identifier === 'MB-0000'
          ? backendError(404, 'Resource not found')
          : backendError(404, 'Member not found'),
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    expect(screen.getByText('Enter a member code or scan the member card')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/check-ins' && call.method === 'POST')).toBe(false)
    const field = screen.getByLabelText(/Member code or card scan/)
    await user.type(field, 'MB-9999')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    const result = await screen.findByRole('status', { name: 'Check-in rejected' })
    expect(within(result).getByTestId('denial-reason')).toHaveTextContent('Member not found')
    expect(within(result).getByText('MB-9999')).toBeInTheDocument()
    expect(field).toHaveValue('MB-9999')
    expect(field).toHaveFocus()
    // Any other 404 is a technical error: danger notice with the backend message, no Rejected panel.
    await user.clear(field)
    await user.type(field, 'MB-0000')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Resource not found')
    expect(screen.queryByRole('status', { name: 'Check-in rejected' })).not.toBeInTheDocument()
  })

  it('TC-FE05-053 without the BE-05 date filter the list shows a neutral notice and this screen\'s check-ins', async () => {
    await open('/reception/check-in', {
      checkIns: backendError(400, "Required request parameter 'memberId' is not present"),
      onCheckIn: (call) =>
        (call.body as { identifier: string }).identifier === 'MB-9999'
          ? backendError(404, 'Member not found')
          : json(201, { id: 80, memberId: 2, memberName: 'Taylor Nguyen', bookingId: 21, bookingCode: 'BK-0021', packageRegistrationId: 10, checkedInAt: '2026-10-13T17:02:00', result: 'ALLOWED', denialReason: null, note: null }),
    })
    const user = userEvent.setup()
    expect(await screen.findByText(/Today's list needs the date filter from BE-05\./)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    const field = screen.getByLabelText(/Member code or card scan/)
    await user.type(field, 'MB-0248')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    await screen.findByRole('status', { name: 'Check-in recorded' })
    await user.type(field, 'MB-9999')
    await user.click(screen.getByRole('button', { name: 'Confirm check-in' }))
    await screen.findByRole('status', { name: 'Check-in rejected' })
    const rows = within(screen.getByRole('table', { name: "Today's check-ins" })).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('MB-9999')
    expect(rows[1]).toHaveTextContent('Member not found')
    expect(rows[2]).toHaveTextContent('Taylor Nguyen')
    expect(rows[2]).toHaveTextContent('Recorded')
  })
})

describe('FE-05 access', () => {
  it.each(['/reception', '/reception/register', '/reception/check-in', '/reception/payments/41'])(
    'TC-FE05-060 a MEMBER cannot open %s',
    async (path) => {
      const { calls } = await open(path, { me: MEMBER })
      expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
      expect(calls.some((call) => call.path.startsWith('/members') || call.path.startsWith('/check-ins') || call.path.startsWith('/payments'))).toBe(false)
    },
  )
})
