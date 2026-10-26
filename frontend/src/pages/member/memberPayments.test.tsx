import { readFileSync } from 'node:fs'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Payment } from '../../api/paymentsApi'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { card, invoice, payment, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'
import { POLL_INTERVAL_MS } from './paymentsModel'

/**
 * FE-11: Payment Summary (F3-01, S3-Single), Payment Instructions and Status (F3-02), My Payments
 * and Receipts (F3-03, S3-PaidHistory) and the receipts (S3-CardReceipt, S3-MemberReceipt,
 * S3-SingleReceipt). History and receipts follow the verified BE-20 contract on main.
 */

const PENDING_REGISTRATION = registration({
  id: 40,
  registrationCode: 'REG-0040',
  status: 'PENDING_PAYMENT',
  startDate: '2026-10-02',
  endDate: '2026-10-31',
  paymentId: 52,
  paymentCode: 'PAY-0052',
})

interface Options {
  /** Answers of GET /payments/52 in order; the last one repeats. */
  payments?: (Payment | Response)[]
  registrations?: unknown[]
  cards?: unknown[]
  myPayments?: (call: RecordedCall) => Response
  invoice?: Response
  onMethod?: (call: RecordedCall) => Response
}

function paymentsBackend(options: Options = {}) {
  const answers = options.payments ?? [payment()]
  let index = 0
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/packages/registrations/my') return json(200, options.registrations ?? [PENDING_REGISTRATION])
    if (call.path === '/membership-cards/my') return json(200, options.cards ?? [])
    if (call.path === '/payments/52' && call.method === 'GET') {
      const answer = answers[Math.min(index, answers.length - 1)]
      index += 1
      return answer instanceof Response ? answer.clone() : json(200, answer)
    }
    if (call.path === '/payments/52/method' && call.method === 'PUT') {
      return options.onMethod
        ? options.onMethod(call)
        : json(200, payment({ method: (call.body as { paymentMethod: string }).paymentMethod }))
    }
    if (call.path.startsWith('/payments/my')) return options.myPayments ? options.myPayments(call) : json(200, historyPage())
    if (call.path === '/invoices/7') return options.invoice ?? json(200, invoice())
    return backendError(404, 'Resource not found')
  })
}

function signIn() {
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
}

function historyPage(rows: Payment[] = [], page = 0, size = 20) {
  return { content: rows, page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}

function paymentGets(calls: RecordedCall[]) {
  return calls.filter((call) => call.path === '/payments/52' && call.method === 'GET')
}

function stubClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
  Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true })
  return writeText
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('FE-11 Payment Summary (F3-01, S3-Single)', () => {
  beforeEach(signIn)

  it('TC-FE11-001 shows the item, original price, discount with percent and total', async () => {
    paymentsBackend()
    renderApp('/member/payments/52')
    const details = await screen.findByRole('region', { name: 'Payment details' })
    expect(within(details).getByRole('heading', { name: 'Football - 30-day package' })).toBeInTheDocument()
    expect(within(details).getByText('PAY-0052')).toBeInTheDocument()
    expect(await within(details).findByText('REG-0040')).toBeInTheDocument()
    expect(within(details).getByText('Coach-led')).toBeInTheDocument()
    expect(within(details).getByText('02 Oct 2026 - 31 Oct 2026')).toBeInTheDocument()
    expect(within(details).getByText('8 sessions')).toBeInTheDocument()

    const order = screen.getByRole('region', { name: 'Order summary' })
    expect(within(order).getByText('Original price').nextSibling).toHaveTextContent('900.000 VND')
    expect(within(order).getByText('Discount (10%)').nextSibling).toHaveTextContent('-90.000 VND')
    expect(within(order).getByText('Total due').nextSibling).toHaveTextContent('810.000 VND')
    expect(within(order).getByTestId('payment-amount')).toHaveTextContent('810.000 VND')
    expect(screen.getByRole('heading', { level: 1, name: 'Payment Summary' })).toBeInTheDocument()
  })

  it('TC-FE11-002 a one day registration shows the Single Visit Payment Summary with no card discount', async () => {
    paymentsBackend({
      payments: [payment({ itemDescription: 'Football single visit', originalPrice: 150000, discount: 0, amount: 150000 })],
      registrations: [{ ...PENDING_REGISTRATION, startDate: '2026-10-05', endDate: '2026-10-05', totalSessions: 1 }],
    })
    renderApp('/member/payments/52')
    expect(await screen.findByRole('heading', { level: 1, name: 'Single Visit Payment Summary' })).toBeInTheDocument()
    const order = screen.getByRole('region', { name: 'Order summary' })
    expect(within(order).getByText('Card discount').nextSibling).toHaveTextContent('0 VND - Single visits excluded')
    expect(within(order).getByText('Price').nextSibling).toHaveTextContent('150.000 VND')
  })

  it('TC-FE11-003 a card payment shows the card request rows', async () => {
    paymentsBackend({
      payments: [payment({ itemDescription: 'VIP Member Card', originalPrice: 600000, discount: 0, amount: 600000 })],
      registrations: [],
      cards: [card({ id: 9, cardCode: 'CARD-0009', tierName: 'VIP Member', status: 'PENDING_PAYMENT', paymentId: 52 })],
    })
    renderApp('/member/payments/52')
    const details = await screen.findByRole('region', { name: 'Payment details' })
    expect(await within(details).findByText('CARD-0009')).toBeInTheDocument()
    expect(within(details).getByText('VIP Member')).toBeInTheDocument()
  })

  it('TC-FE11-004 Continue with the same method sends no PUT and opens the status screen', async () => {
    const user = userEvent.setup()
    const { calls } = paymentsBackend()
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).getByRole('radio', { name: 'Bank transfer' })).toBeChecked()
    await user.click(within(order).getByRole('button', { name: 'Continue to payment' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Payment Instructions and Status' })).toBeInTheDocument()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })

  it('TC-FE11-005 wallets are shown disabled; a wallet payment is switched to bank transfer by Continue', async () => {
    const user = userEvent.setup()
    const { calls } = paymentsBackend({
      payments: [payment({ method: 'MOMO', bankTransferInstructions: null }), payment()],
    })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).getByRole('radio', { name: 'Bank transfer' })).toBeChecked()
    for (const wallet of ['MoMo', 'VNPay', 'ZaloPay']) {
      const radio = within(order).getByRole('radio', { name: wallet })
      expect(radio).toBeDisabled()
      expect(radio).not.toBeChecked()
      expect(radio).toHaveAccessibleDescription('Not available yet')
    }
    expect(within(order).getAllByText('Not available yet')).toHaveLength(3)
    await user.click(within(order).getByRole('radio', { name: 'MoMo' }))
    expect(within(order).getByRole('radio', { name: 'Bank transfer' })).toBeChecked()

    await user.click(within(order).getByRole('button', { name: 'Continue to payment' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Payment Instructions and Status' })).toBeInTheDocument()
    const put = calls.filter((call) => call.method === 'PUT')
    expect(put).toHaveLength(1)
    expect(put[0].path).toBe('/payments/52/method')
    expect(put[0].body).toEqual({ paymentMethod: 'BANK_TRANSFER' })
    expect(await screen.findByRole('region', { name: 'Bank transfer' })).toHaveTextContent('0123456789')
  })

  it('TC-FE11-006 a rejected method change shows the backend message and stays on the summary', async () => {
    const user = userEvent.setup()
    paymentsBackend({
      payments: [payment({ method: 'VNPAY', bankTransferInstructions: null })],
      onMethod: () => backendError(400, 'Only pending payments can have their payment method updated'),
    })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    await user.click(within(order).getByRole('button', { name: 'Continue to payment' }))
    const alert = await within(order).findByRole('alert')
    expect(alert).toHaveTextContent('Only pending payments can have their payment method updated')
    expect(screen.getByRole('heading', { level: 1, name: 'Payment Summary' })).toBeInTheDocument()
  })

  it('TC-FE11-007 a paid payment has no method choice and links to its receipt', async () => {
    paymentsBackend({
      payments: [payment({ status: 'SUCCESS', bankTransferInstructions: null, invoiceId: 7, invoiceNumber: 'INV-0007' })],
    })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).queryByRole('radio')).not.toBeInTheDocument()
    expect(within(order).getByText('Paid with: Bank transfer')).toBeInTheDocument()
    expect(within(order).getByRole('link', { name: 'View receipt' })).toHaveAttribute('href', '/member/payments/receipts/7')
    expect(within(screen.getByRole('region', { name: 'Payment details' })).getByText('Paid')).toBeInTheDocument()
  })

  it('TC-FE11-008 a failed or refunded payment says Method, not Paid with', async () => {
    paymentsBackend({ payments: [payment({ status: 'FAILED', bankTransferInstructions: null })] })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).getByText('Method: Bank transfer')).toBeInTheDocument()
    expect(within(order).queryByText(/Paid with/)).not.toBeInTheDocument()
  })

  it('TC-FE11-009 the payment sub navigation links to summary, status and my payments', async () => {
    paymentsBackend()
    renderApp('/member/payments/52')
    const nav = await screen.findByRole('navigation', { name: 'Payment pages' })
    expect(within(nav).getByRole('link', { name: 'Payment summary' })).toHaveAttribute('href', '/member/payments/52')
    expect(within(nav).getByRole('link', { name: 'Payment status' })).toHaveAttribute('href', '/member/payments/52/status')
    expect(within(nav).getByRole('link', { name: 'My payments' })).toHaveAttribute('href', '/member/payments')
  })

  it('TC-FE11-010 the summary still works when the registration list fails', async () => {
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/payments/52') return json(200, payment())
      return backendError(500, 'Server error')
    })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).getByTestId('payment-amount')).toHaveTextContent('810.000 VND')
    await waitFor(() => expect(screen.queryByText('Loading the item details')).not.toBeInTheDocument())
    expect(screen.queryByText('Registration')).not.toBeInTheDocument()
  })

  it.each([403, 404])('does not display payment details when the backend rejects access with %s', async status => {
    paymentsBackend({ payments: [backendError(status, 'Payment unavailable')] })
    renderApp('/member/payments/52')
    expect(await screen.findByRole('alert')).toHaveTextContent('Payment unavailable')
    expect(screen.queryByRole('region', { name: 'Payment details' })).not.toBeInTheDocument()
    expect(screen.queryByText('MEM-0002')).not.toBeInTheDocument()
  })

  it('UI-27 shows the summary under the Home theme payment hero', async () => {
    paymentsBackend()
    renderApp('/member/payments/52')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Payment Summary' })
    const hero = heading.closest('header')
    expect(hero).toHaveClass('payment-hero')
    expect(within(hero as HTMLElement).getByText('Member / Payments')).toHaveClass('payment-hero__eyebrow')
  })

  it('UI-27 hides the original price and discount when they do not add up to the amount charged', async () => {
    paymentsBackend({ payments: [payment({ originalPrice: 900000, discount: 90000, amount: 850000 })] })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).queryByText('Original price')).not.toBeInTheDocument()
    expect(within(order).queryByText(/Discount/)).not.toBeInTheDocument()
    expect(within(order).getByText('Total due').nextSibling).toHaveTextContent('850.000 VND')
    expect(within(order).getByTestId('payment-amount')).toHaveTextContent('850.000 VND')
    expect(within(order).getByText(/Price details are not available/)).toBeInTheDocument()
  })

  it('UI-27 does not invent an original price when the backend sends none', async () => {
    paymentsBackend({ payments: [payment({ originalPrice: null, discount: null, amount: 810000 })] })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).queryByText('Original price')).not.toBeInTheDocument()
    expect(within(order).getByTestId('payment-amount')).toHaveTextContent('810.000 VND')
  })

  it('UI-27 mentions a card discount only when this payment has one', async () => {
    paymentsBackend()
    renderApp('/member/payments/52')
    const details = await screen.findByRole('region', { name: 'Payment details' })
    expect(await within(details).findByText(/Your membership card discount is included in the total/)).toBeInTheDocument()
  })

  it('UI-27 says no card discount applies to a package paid at full price', async () => {
    paymentsBackend({ payments: [payment({ originalPrice: 900000, discount: 0, amount: 900000 })] })
    renderApp('/member/payments/52')
    const details = await screen.findByRole('region', { name: 'Payment details' })
    expect(await within(details).findByText(/No card discount applies to this payment/)).toBeInTheDocument()
    expect(within(details).queryByText(/discount is included/)).not.toBeInTheDocument()
  })

  it('UI-27 shows a single visit with its visit date and no package validity or card discount claim', async () => {
    paymentsBackend({
      payments: [payment({ itemDescription: 'Football single visit', originalPrice: 150000, discount: 0, amount: 150000 })],
      registrations: [{ ...PENDING_REGISTRATION, startDate: '2026-10-05', endDate: '2026-10-05', totalSessions: 1 }],
    })
    renderApp('/member/payments/52')
    await screen.findByRole('heading', { level: 1, name: 'Single Visit Payment Summary' })
    const details = screen.getByRole('region', { name: 'Payment details' })
    expect(within(details).getByText('Visit date').nextSibling).toHaveTextContent('05 Oct 2026')
    expect(within(details).queryByText('Package validity')).not.toBeInTheDocument()
    expect(within(details).queryByText(/card discount/i)).not.toBeInTheDocument()
    expect(within(details).getByText(/Reception must confirm the payment/)).toBeInTheDocument()
  })

  it('UI-27 makes no discount claim while the paid item is unknown', async () => {
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/payments/52') return json(200, payment())
      return backendError(500, 'Server error')
    })
    renderApp('/member/payments/52')
    const details = await screen.findByRole('region', { name: 'Payment details' })
    await waitFor(() => expect(screen.queryByText('Loading the item details')).not.toBeInTheDocument())
    expect(within(details).queryByText(/card discount/i)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Payment Summary' })).toBeInTheDocument()
  })

  it('UI-27 shows no amount when the backend answers with another payment than the link', async () => {
    paymentsBackend({ payments: [payment({ id: 53, code: 'PAY-0053' })] })
    renderApp('/member/payments/52')
    expect(await screen.findByRole('heading', { name: 'Payment not found' })).toBeInTheDocument()
    expect(screen.getByText(/does not match this link/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Order summary' })).not.toBeInTheDocument()
    expect(screen.queryByText('PAY-0053')).not.toBeInTheDocument()
    expect(screen.queryByText(/810\.000 VND/)).not.toBeInTheDocument()
  })

  it('retains backend fractional original price, discount and total', async () => {
    paymentsBackend({ payments: [payment({ originalPrice: '0.25', discount: '0.12', amount: '0.13' })] })
    renderApp('/member/payments/52')
    const order = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(order).getByText('Original price').nextSibling).toHaveTextContent('0,25 VND')
    expect(within(order).getByText('Discount (48%)').nextSibling).toHaveTextContent('-0,12 VND')
    expect(within(order).getByText('Total due').nextSibling).toHaveTextContent('0,13 VND')
  })
})

describe('FE-11 Payment Instructions and Status (F3-02)', () => {
  beforeEach(signIn)

  it('TC-FE11-011 shows the bank transfer details and the copy buttons copy the right text', async () => {
    const user = userEvent.setup()
    paymentsBackend()
    renderApp('/member/payments/52/status')
    const bank = await screen.findByRole('region', { name: 'Bank transfer' })
    // userEvent.setup() installs its own clipboard, so the stub goes in after it.
    const writeText = stubClipboard()
    expect(within(bank).getByText('Demo Bank')).toBeInTheDocument()
    expect(within(bank).getByText('SPORTIFY CENTER')).toBeInTheDocument()

    await user.click(within(bank).getByRole('button', { name: 'Copy account number' }))
    expect(writeText).toHaveBeenLastCalledWith('0123456789')
    await user.click(within(bank).getByRole('button', { name: 'Copy transfer content' }))
    expect(writeText).toHaveBeenLastCalledWith('PAY-0052')
    await user.click(within(bank).getByRole('button', { name: 'Copy amount' }))
    expect(writeText).toHaveBeenLastCalledWith('810000')
    expect(await screen.findByText('Copied amount')).toBeInTheDocument()
  })

  it('TC-FE11-012 an account number with spaces is copied without spaces', async () => {
    const user = userEvent.setup()
    paymentsBackend({
      payments: [
        payment({
          bankTransferInstructions: { accountName: 'SPORTIFY', accountNumber: '0000 1041 2460', bank: 'Demo Bank', transferContent: 'PAY-0052' },
        }),
      ],
    })
    renderApp('/member/payments/52/status')
    const bank = await screen.findByRole('region', { name: 'Bank transfer' })
    const writeText = stubClipboard()
    expect(within(bank).getByText('0000 1041 2460')).toBeInTheDocument()
    await user.click(within(bank).getByRole('button', { name: 'Copy account number' }))
    expect(writeText).toHaveBeenLastCalledWith('000010412460')
  })

  it('TC-FE11-013 when copying fails the member is told to copy by hand', async () => {
    const user = userEvent.setup()
    paymentsBackend()
    renderApp('/member/payments/52/status')
    const bank = await screen.findByRole('region', { name: 'Bank transfer' })
    const writeText = stubClipboard()
    writeText.mockRejectedValue(new Error('denied'))
    document.execCommand = vi.fn().mockReturnValue(false)
    await user.click(within(bank).getByRole('button', { name: 'Copy transfer content' }))
    expect(await screen.findByText('Could not copy the transfer content')).toBeInTheDocument()
  })

  it('TC-FE11-014 a pending payment updates by itself when reception confirms it, then polling stops', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend({
      payments: [
        payment(),
        payment({ status: 'SUCCESS', bankTransferInstructions: null, invoiceId: 7, invoiceNumber: 'INV-0007', paymentTime: '2026-10-13T10:00:00' }),
      ],
    })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    expect(within(panel).getByRole('heading', { name: 'Awaiting confirmation' })).toBeInTheDocument()
    expect(within(panel).getByText(/Checks again every 15 seconds/)).toBeInTheDocument()
    expect(paymentGets(calls)).toHaveLength(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    expect(await within(panel).findByRole('heading', { name: 'Payment confirmed' })).toBeInTheDocument()
    expect(within(panel).getByText('Paid')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'View receipt' })).toHaveAttribute('href', '/member/payments/receipts/7')
    expect(screen.queryByRole('region', { name: 'Bank transfer' })).not.toBeInTheDocument()
    expect(paymentGets(calls)).toHaveLength(2)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3)
    })
    expect(paymentGets(calls)).toHaveLength(2)
  })

  it('TC-FE11-015 no check is sent before 15 seconds and a still pending payment is checked again', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend()
    renderApp('/member/payments/52/status')
    await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS - 1000)
    })
    expect(paymentGets(calls)).toHaveLength(1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(2))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(3))
  })

  it('TC-FE11-016 Refresh status asks the backend at once', async () => {
    const user = userEvent.setup()
    const { calls } = paymentsBackend({ payments: [payment(), payment({ status: 'FAILED', bankTransferInstructions: null })] })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    await user.click(within(panel).getByRole('button', { name: 'Refresh status' }))
    expect(await within(panel).findByRole('heading', { name: 'Payment failed' })).toBeInTheDocument()
    expect(paymentGets(calls)).toHaveLength(2)
    expect(within(panel).queryByRole('button', { name: 'Refresh status' })).not.toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'My membership and packages' })).toHaveAttribute('href', '/member/membership')
  })

  it('TC-FE11-017 a failed check keeps the payment on screen and the next check recovers', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    paymentsBackend({
      payments: [payment(), backendError(500, 'Server error'), payment({ status: 'SUCCESS', bankTransferInstructions: null })],
    })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    expect(await within(panel).findByRole('alert')).toHaveTextContent('Could not check the status')
    expect(within(panel).getByRole('heading', { name: 'Awaiting confirmation' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Bank transfer' })).toBeInTheDocument()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    expect(await within(panel).findByRole('heading', { name: 'Payment confirmed' })).toBeInTheDocument()
    expect(within(panel).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('TC-FE11-018 a 4xx answer stops the automatic checks; Refresh status checks again', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const { calls } = paymentsBackend({
      payments: [payment(), backendError(403, 'Access denied'), payment()],
    })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    expect(await within(panel).findByRole('alert')).toHaveTextContent('Access denied')
    expect(within(panel).getByText(/Automatic checks stopped/)).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Bank transfer' })).toBeInTheDocument()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3)
    })
    expect(paymentGets(calls)).toHaveLength(2)

    await user.click(within(panel).getByRole('button', { name: 'Refresh status' }))
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(3))
    await waitFor(() => expect(within(panel).queryByRole('alert')).not.toBeInTheDocument())
    expect(within(panel).getByText(/Checks again every 15 seconds/)).toBeInTheDocument()
  })

  it('TC-FE11-019 leaving the page stops polling: no request after unmount', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend()
    const { unmount } = renderApp('/member/payments/52/status')
    await screen.findByRole('region', { name: 'Payment status' })
    expect(paymentGets(calls)).toHaveLength(1)
    unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 4)
    })
    expect(paymentGets(calls)).toHaveLength(1)
  })

  it('TC-FE11-020 Refresh status while a check is running sends no second request', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    let release: (response: Response) => void = () => undefined
    let gets = 0
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === '/payments/52') {
        gets += 1
        if (gets === 2) {
          return new Promise<Response>((resolve) => {
            release = resolve
          })
        }
        return json(200, payment())
      }
      return backendError(404, 'Resource not found')
    })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    })
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(2))

    // The automatic check is still running: both clicks are ignored.
    await user.click(within(panel).getByRole('button', { name: 'Refresh status' }))
    await user.click(within(panel).getByRole('button', { name: 'Refresh status' }))
    expect(paymentGets(calls)).toHaveLength(2)

    await act(async () => {
      release(json(200, payment()))
    })
    await user.click(within(panel).getByRole('button', { name: 'Refresh status' }))
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(3))
  })

  it('TC-FE11-021 a wallet method without bank details shows the reference to give at reception', async () => {
    paymentsBackend({ payments: [payment({ method: 'ZALOPAY', bankTransferInstructions: null })] })
    renderApp('/member/payments/52/status')
    const instructions = await screen.findByRole('region', { name: 'Payment instructions' })
    expect(within(instructions).getByRole('heading', { name: 'ZaloPay' })).toBeInTheDocument()
    expect(within(instructions).getByText('PAY-0052')).toBeInTheDocument()
    expect(instructions).toHaveTextContent('ZaloPay has no online checkout yet')
    expect(within(instructions).getByRole('link', { name: 'Switch to bank transfer' })).toHaveAttribute('href', '/member/payments/52')
  })

  it('TC-FE11-022 a first load error shows Retry, and Retry loads the payment', async () => {
    const user = userEvent.setup()
    paymentsBackend({ payments: [backendError(404, 'Payment not found for id: 52'), payment()] })
    renderApp('/member/payments/52/status')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Payment not found for id: 52')
    await user.click(within(alert).getByRole('button', { name: /Retry|Try again/ }))
    expect(await screen.findByRole('region', { name: 'Bank transfer' })).toBeInTheDocument()
  })

  it('TC-FE11-023 an invalid payment id makes no request', async () => {
    const { calls } = paymentsBackend()
    renderApp('/member/payments/abc/status')
    expect(await screen.findByText('The payment link is not valid.')).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/payments'))).toBe(false)
  })

  it('copies a supported fractional amount without rounding or grouping', async () => {
    const user = userEvent.setup()
    paymentsBackend({ payments: [payment({ amount: '810000.25' })] })
    renderApp('/member/payments/52/status')
    const bank = await screen.findByRole('region', { name: 'Bank transfer' })
    const writeText = stubClipboard()
    expect(bank).toHaveTextContent('810.000,25 VND')
    await user.click(within(bank).getByRole('button', { name: 'Copy amount' }))
    expect(writeText).toHaveBeenCalledWith('810000.25')
  })

  it.each(['FAILED', 'REFUNDED'])('stops automatic polling after PENDING becomes %s', async status => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend({ payments: [payment(), payment({ status, bankTransferInstructions: null })] })
    renderApp('/member/payments/52/status')
    await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => { await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS) })
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(2))
    expect(screen.queryByRole('button', { name: 'Refresh status' })).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3) })
    expect(paymentGets(calls)).toHaveLength(2)
  })

  it('UI-28 shows the instructions under the Home theme payment hero', async () => {
    paymentsBackend()
    renderApp('/member/payments/52/status')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Payment Instructions and Status' })
    expect(heading.closest('header')).toHaveClass('payment-hero')
  })

  it('UI-28 the copied transfer content is exactly the reference shown, spaces and case kept', async () => {
    const user = userEvent.setup()
    paymentsBackend({
      payments: [payment({ bankTransferInstructions: { accountName: 'SPORTIFY', accountNumber: '0123456789', bank: 'Demo Bank', transferContent: ' REG1041 MB0246 ' } })],
    })
    renderApp('/member/payments/52/status')
    const bank = await screen.findByRole('region', { name: 'Bank transfer' })
    const writeText = stubClipboard()
    expect(within(bank).getByText('REG1041 MB0246')).toBeInTheDocument()
    await user.click(within(bank).getByRole('button', { name: 'Copy transfer content' }))
    expect(writeText).toHaveBeenLastCalledWith('REG1041 MB0246')
  })

  it('UI-28 never shows another payment when the backend answers with a different id', async () => {
    paymentsBackend({ payments: [payment({ id: 99, code: 'PAY-0099' })] })
    renderApp('/member/payments/52/status')
    expect(await screen.findByRole('alert')).toHaveTextContent('does not match this link')
    expect(screen.queryByText('PAY-0099')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Bank transfer' })).not.toBeInTheDocument()
  })

  it('UI-28 keeps the right payment and stops checking when a later answer is for another payment', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend({ payments: [payment(), payment({ id: 99, code: 'PAY-0099', status: 'SUCCESS' })] })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    await act(async () => { await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS) })
    expect(await within(panel).findByRole('alert')).toHaveTextContent('does not match this link')
    expect(within(panel).getByRole('heading', { name: 'Awaiting confirmation' })).toBeInTheDocument()
    expect(screen.queryByText('PAY-0099')).not.toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3) })
    expect(paymentGets(calls)).toHaveLength(2)
  })

  it('UI-28 checks a pending payment at once when the tab becomes visible again', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { calls } = paymentsBackend({ payments: [payment(), payment({ status: 'SUCCESS', bankTransferInstructions: null, invoiceId: 7 })] })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    expect(paymentGets(calls)).toHaveLength(1)
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible')
    // Let the effects of the first render run, as they have long before a member switches tabs.
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')) })
    expect(await within(panel).findByRole('heading', { name: 'Payment confirmed' })).toBeInTheDocument()
    expect(paymentGets(calls)).toHaveLength(2)
    // Once paid, coming back to the tab sends nothing more.
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')) })
    expect(paymentGets(calls)).toHaveLength(2)
    visibility.mockRestore()
  })

  it('UI-28 a paid payment without an invoice yet says where the receipt will appear', async () => {
    paymentsBackend({ payments: [payment({ status: 'SUCCESS', bankTransferInstructions: null, invoiceId: null })] })
    renderApp('/member/payments/52/status')
    const panel = await screen.findByRole('region', { name: 'Payment status' })
    expect(within(panel).getByText('The receipt appears in My payments once it is issued.')).toBeInTheDocument()
    expect(within(panel).queryByRole('link', { name: 'View receipt' })).not.toBeInTheDocument()
  })

  it('aborts an in-flight request on unmount', async () => {
    let release: (response: Response) => void = () => undefined
    const { calls, fetchMock } = installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER) :
      new Promise<Response>(resolve => { release = resolve }))
    const view = renderApp('/member/payments/52/status')
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(1))
    const signal = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/payments/52'))?.[1]?.signal
    view.unmount()
    expect(signal?.aborted).toBe(true)
    await act(async () => release(json(200, payment())))
    expect(screen.queryByText('PAY-0052')).not.toBeInTheDocument()
  })

  it('drops an old account payment response after another tab switches members', async () => {
    let release: (response: Response) => void = () => undefined
    const { calls } = installFakeFetch(call => {
      if (call.path === '/users/me') return json(200, call.authorization === 'Bearer second-member' ?
        { ...MEMBER, id: 3, fullName: 'Second member' } : MEMBER)
      if (call.path === '/payments/52') return new Promise<Response>(resolve => { release = resolve })
      return json(200, [])
    })
    renderApp('/member/payments/52/status')
    await waitFor(() => expect(paymentGets(calls)).toHaveLength(1))
    const oldSession = tokenStorage.getSessionId()
    await act(async () => {
      localStorage.setItem(tokenStorage.accessKey, 'second-member')
      localStorage.setItem(tokenStorage.refreshKey, 'second-refresh')
      localStorage.setItem(tokenStorage.sessionKey, 'second-session')
      window.dispatchEvent(new StorageEvent('storage', { key: tokenStorage.sessionKey, oldValue: oldSession, newValue: 'second-session' }))
    })
    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Account switched' })).toHaveTextContent('Second member')
    await act(async () => release(json(200, payment({ code: 'PRIVATE-OLD-ACCOUNT' }))))
    expect(screen.queryByText('PRIVATE-OLD-ACCOUNT')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Bank transfer' })).not.toBeInTheDocument()
  })
})

describe('FE-11 My Payments and Receipts (F3-03, S3-PaidHistory)', () => {
  beforeEach(signIn)

  const HISTORY = [
    payment({ id: 52, code: 'PAY-0052', status: 'PENDING', createdAt: '2026-10-12T09:00:00' }),
    payment({
      id: 41,
      code: 'PAY-0041',
      status: 'SUCCESS',
      itemDescription: 'Football single visit',
      amount: 150000,
      createdAt: '2026-10-02T09:00:00',
      invoiceId: 7,
      invoiceNumber: 'INV-0007',
    }),
    payment({ id: 31, code: 'PAY-0031', status: 'FAILED', itemDescription: 'VIP Member Card', createdAt: '2026-09-01T09:00:00' }),
  ]

  function historyRows() {
    const table = screen.getByRole('table', { name: 'Payment history' })
    return within(table).getAllByRole('row').slice(1)
  }

  it('TC-FE11-024 lists payments newest first with Check status, View receipt and View details', async () => {
    paymentsBackend({ myPayments: () => json(200, historyPage(HISTORY)) })
    renderApp('/member/payments')
    await screen.findByText('PAY-0052')
    const rows = historyRows()
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('PAY-0052')
    expect(within(rows[0]).getByRole('link', { name: 'Check status of PAY-0052' })).toHaveAttribute('href', '/member/payments/52/status')
    expect(rows[1]).toHaveTextContent('INV-0007')
    expect(rows[1]).toHaveTextContent('Paid')
    expect(within(rows[1]).getByRole('link', { name: 'View receipt of PAY-0041' })).toHaveAttribute('href', '/member/payments/receipts/7')
    expect(within(rows[2]).getByRole('link', { name: 'View details of PAY-0031' })).toHaveAttribute('href', '/member/payments/31')
  })

  it('TC-FE11-025 the status filter asks the backend with ?status= and shows only that status', async () => {
    const user = userEvent.setup()
    const { calls } = paymentsBackend({ myPayments: (call) => {
      const status = new URL(call.path, 'http://api.test').searchParams.get('status')
      return json(200, historyPage(status ? HISTORY.filter(row => row.status === status) : HISTORY))
    } })
    renderApp('/member/payments')
    await screen.findByText('PAY-0052')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'PENDING')
    await waitFor(() => expect(calls.some((call) => call.path === '/payments/my?status=PENDING&page=0&size=20')).toBe(true))
    await waitFor(() => expect(historyRows()).toHaveLength(1))
    expect(historyRows()[0]).toHaveTextContent('PAY-0052')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Status' }), '')
    await waitFor(() => expect(historyRows()).toHaveLength(3))
    expect(calls.filter((call) => call.path === '/payments/my?page=0&size=20')).toHaveLength(2)
  })

  it('TC-FE11-026 the status filter is read from the URL', async () => {
    const { calls } = paymentsBackend({ myPayments: () => json(200, historyPage([HISTORY[1]])) })
    renderApp('/member/payments?status=success')
    await screen.findByText('INV-0007')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveValue('SUCCESS')
    expect(calls.some((call) => call.path === '/payments/my?status=SUCCESS&page=0&size=20')).toBe(true)
  })

  it('TC-FE11-027 shows an empty state when the member has no payments', async () => {
    paymentsBackend({ myPayments: () => json(200, historyPage()) })
    renderApp('/member/payments')
    expect(await screen.findByRole('heading', { name: 'No payments yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse sport packages' })).toHaveAttribute('href', '/member/packages')
  })

  it('TC-FE11-028 a filter with no match shows its own empty state', async () => {
    paymentsBackend({ myPayments: () => json(200, historyPage()) })
    renderApp('/member/payments?status=REFUNDED')
    expect(await screen.findByRole('heading', { name: 'No refunded payments' })).toBeInTheDocument()
  })

  it('TC-FE11-029 a page answer ({content: [...]}) is accepted', async () => {
    paymentsBackend({ myPayments: () => json(200, { content: [HISTORY[0]], page: 0, size: 20, totalElements: 1, totalPages: 1 }) })
    renderApp('/member/payments')
    expect(await screen.findByText('PAY-0052')).toBeInTheDocument()
  })

  it('TC-FE11-030 a load error shows the message with Retry', async () => {
    paymentsBackend({ myPayments: () => backendError(500, 'Server error') })
    renderApp('/member/payments')
    expect(await screen.findByRole('alert')).toHaveTextContent('Server error')
  })

  it('UI-29 the history heading is the Home theme payment hero', async () => {
    paymentsBackend()
    renderApp('/member/payments')
    const heading = await screen.findByRole('heading', { level: 1, name: 'My Payments and Receipts' })
    expect(heading.closest('header')).toHaveClass('payment-hero')
  })

  it('TC-FE11-031 the My Payments menu item opens the real screen', async () => {
    paymentsBackend({ myPayments: () => json(200, historyPage()) })
    renderApp('/member/payments')
    expect(await screen.findByRole('heading', { level: 1, name: 'My Payments and Receipts' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Member navigation' })
    expect(within(nav).getByRole('link', { name: 'My Payments' })).toHaveClass('workspace-header__nav-link--active')
  })
})

describe('FE-11 Receipts (S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt)', () => {
  beforeEach(signIn)

  it('TC-FE11-032 shows the receipt rows and Print receipt calls window.print', async () => {
    const user = userEvent.setup()
    const print = vi.fn()
    vi.stubGlobal('print', print)
    paymentsBackend()
    renderApp('/member/payments/receipts/7')
    const receipt = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(screen.getByRole('heading', { level: 1, name: 'Receipt INV-0007' })).toBeInTheDocument()
    expect(within(receipt).getByText('Receipt / date').nextSibling).toHaveTextContent('INV-0007 / 02 Oct 2026')
    expect(within(receipt).getByText('Issued to').nextSibling).toHaveTextContent('Taylor Nguyen / MEM-0002')
    expect(within(receipt).getByText('Service').nextSibling).toHaveTextContent('Football - Coach-led - 30 days')
    expect(within(receipt).getByText('Base price').nextSibling).toHaveTextContent('900.000 VND')
    expect(within(receipt).getByText('Discount').nextSibling).toHaveTextContent('-90.000 VND')
    expect(within(receipt).getByTestId('receipt-total')).toHaveTextContent('810.000 VND')
    expect(within(receipt).getByText('Payment').nextSibling).toHaveTextContent('PAY-0041 / Bank transfer')
    expect(within(receipt).getByText('Paid')).toBeInTheDocument()
    expect(within(receipt).queryByText('Tax')).not.toBeInTheDocument()

    await user.click(within(receipt).getByRole('button', { name: 'Print receipt' }))
    expect(print).toHaveBeenCalledTimes(1)
    expect(within(receipt).getByRole('button', { name: 'Print receipt' }).closest('.no-print')).not.toBeNull()
    expect(screen.getByRole('navigation', { name: 'Payment pages' })).toHaveClass('no-print')
  })

  it('TC-FE11-033 Issued to comes from the invoice only, never from the signed in account', async () => {
    paymentsBackend({ invoice: json(200, invoice({ memberFullName: null })) })
    const first = renderApp('/member/payments/receipts/7')
    const receipt = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(within(receipt).getByText('Issued to').nextSibling).toHaveTextContent('MEM-0002')
    expect(within(receipt).getByText('Issued to').nextSibling).not.toHaveTextContent('Taylor Nguyen')
    first.unmount()

    paymentsBackend({ invoice: json(200, invoice({ memberFullName: null, memberCode: null })) })
    renderApp('/member/payments/receipts/7')
    const second = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(within(second).getByText('Issued to').nextSibling).toHaveTextContent(/^-$/)
  })

  it('TC-FE11-034 a card receipt and a receipt with several lines list every item', async () => {
    paymentsBackend({
      invoice: json(
        200,
        invoice({
          subtotalAmount: 750000,
          discountAmount: 0,
          totalAmount: 750000,
          lines: [
            { id: 1, itemType: 'MEMBERSHIP_CARD', description: 'VIP Member Card - 12 months', quantity: 1, unitPrice: 600000, lineTotal: 600000 },
            { id: 2, itemType: 'CLASS_DROP_IN', description: 'Football single visit', quantity: 1, unitPrice: 150000, lineTotal: 150000 },
          ],
        }),
      ),
    })
    renderApp('/member/payments/receipts/7')
    const items = await screen.findByRole('list', { name: 'Receipt items' })
    expect(within(items).getAllByRole('listitem')).toHaveLength(2)
    expect(items).toHaveTextContent('VIP Member Card - 12 months (Membership card)')
    expect(items).toHaveTextContent('Football single visit (Single visit)')
    expect(screen.getByText('Discount').nextSibling).toHaveTextContent('0 VND')
  })

  it('TC-FE11-035 a receipt that cannot be loaded shows an error with Retry', async () => {
    paymentsBackend({ invoice: backendError(404, 'Invoice not found') })
    renderApp('/member/payments/receipts/7')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not load the receipt')
    expect(alert).toHaveTextContent('Invoice not found')
  })

  it('TC-FE11-036 an invalid receipt id makes no request', async () => {
    const { calls } = paymentsBackend()
    renderApp('/member/payments/receipts/x1')
    expect(await screen.findByText('The receipt link is not valid.')).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/invoices'))).toBe(false)
  })

  it('shows the complete payment reference and fractional invoice totals without repeating item descriptions', async () => {
    paymentsBackend({ invoice: json(200, invoice({ paymentReference: 'BANK-REFERENCE-123', subtotalAmount: '0.25',
      discountAmount: '0.12', taxAmount: '0.05', totalAmount: '0.18', lines: [
        { id: 1, itemType: 'SPORT_PACKAGE', description: 'First invoice line', quantity: 2, unitPrice: '0.10', lineTotal: '0.20' },
        { id: 2, itemType: 'CLASS_DROP_IN', description: 'Second invoice line', quantity: 1, unitPrice: '0.05', lineTotal: '0.05' },
      ] })) })
    renderApp('/member/payments/receipts/7')
    const receipt = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(within(receipt).getByText('Payment reference').nextSibling).toHaveTextContent('BANK-REFERENCE-123')
    expect(within(receipt).getByTestId('receipt-total')).toHaveTextContent('0,18 VND')
    expect(within(receipt).getByText('Tax').nextSibling).toHaveTextContent('0,05 VND')
    expect(within(receipt).getAllByText(/First invoice line/)).toHaveLength(1)
    expect(within(receipt).getByRole('list', { name: 'Receipt items' })).toHaveTextContent('x 2')
  })

  it('UI-29 shows no base price, discount or service the invoice does not have', async () => {
    paymentsBackend({ invoice: json(200, invoice({ subtotalAmount: null, discountAmount: null, lines: [] })) })
    renderApp('/member/payments/receipts/7')
    const receipt = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(within(receipt).queryByText('Base price')).not.toBeInTheDocument()
    expect(within(receipt).queryByText('Discount')).not.toBeInTheDocument()
    expect(within(receipt).queryByText('Service')).not.toBeInTheDocument()
    expect(within(receipt).getByTestId('receipt-total')).toHaveTextContent('810.000 VND')
  })

  it('UI-29 a zero discount sent by the invoice is still shown as 0 VND', async () => {
    paymentsBackend({ invoice: json(200, invoice({ subtotalAmount: 810000, discountAmount: 0 })) })
    renderApp('/member/payments/receipts/7')
    const receipt = await screen.findByRole('region', { name: 'Receipt INV-0007' })
    expect(within(receipt).getByText('Discount').nextSibling).toHaveTextContent('0 VND')
    expect(within(receipt).getByText('Base price').nextSibling).toHaveTextContent('810.000 VND')
  })

  it('UI-29 receipt and history headings use the Home theme payment hero, hidden when the receipt prints', async () => {
    paymentsBackend()
    renderApp('/member/payments/receipts/7')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Receipt INV-0007' })
    expect(heading.closest('header')).toHaveClass('payment-hero')
    const css = readFileSync('src/styles/payments.css', 'utf8')
    expect(css).toMatch(/@media print \{[\s\S]*\.receipt-page \.page-header,\s*\.receipt-page \.payment-hero \{\s*display: none;/)
  })

  it('does not render another member receipt after the backend returns 403', async () => {
    paymentsBackend({ invoice: backendError(403, 'This invoice belongs to another member') })
    renderApp('/member/payments/receipts/7')
    expect(await screen.findByRole('alert')).toHaveTextContent('This invoice belongs to another member')
    expect(screen.queryByTestId('receipt-total')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Print receipt' })).not.toBeInTheDocument()
  })
})
