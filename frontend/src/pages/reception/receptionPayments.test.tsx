import { readFileSync } from 'node:fs'
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { card, payment, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

/**
 * FE-12: Payment Requests (F3-04), Verify and Record Payment (F3-05) with S3-Mismatch, Payment
 * Confirmed and Receipt (F3-06, S3-Receipt), Refund Requests (F3-07) with S3-RefundSubmitted,
 * S3-RefundApproved and S3-RefundDone. The fake backend follows docs/tests/FE-12_test_cases.md 1.1.
 */

const RECEPTIONIST = {
  id: 9,
  email: 'reception@sportify.demo',
  fullName: 'Jamie Tran',
  phone: '0901234599',
  role: 'RECEPTIONIST',
  status: 'ACTIVE',
}

const TAYLOR = { userId: 2, memberCode: 'MB-0246', fullName: 'Taylor Nguyen', email: 'member@sportify.demo', phone: '0901234567', currentLevel: 'BEGINNER' }

function pendingPayment(overrides: Record<string, unknown> = {}) {
  const base = payment({
    id: 41,
    code: 'PAY-0041',
    status: 'PENDING',
    method: 'BANK_TRANSFER',
    amount: 810000,
    originalPrice: 900000,
    discount: 90000,
    itemDescription: 'Football / Coach-led / 30 days',
    memberId: 2,
    memberCode: 'MB-0246',
    bankTransferInstructions: null,
  })
  return { ...base, memberFullName: 'Taylor Nguyen', itemCode: 'REG-1041', ...overrides }
}

const PENDING_ROWS = [
  pendingPayment(),
  pendingPayment({ id: 43, code: 'PAY-0043', amount: 500000, itemDescription: 'Football / Self-training / 30 days' }),
]

const FOOTBALL_REG = registration({ id: 11, registrationCode: 'REG-1041', status: 'PENDING_PAYMENT', paymentId: 41, startDate: '2026-10-02', endDate: '2026-10-31', remainingSessions: 8 })
const PAID_REG = registration({ id: 10, registrationCode: 'REG-1040', status: 'ACTIVE', paymentId: 40, paidAmount: 150000 })

const INVOICE = {
  id: 1041,
  invoiceNumber: 'INV-1041',
  paymentId: 41,
  paymentMethod: 'BANK_TRANSFER',
  paymentReference: 'BANK-1041-20261002',
  paymentTime: '2026-10-02T10:15:00',
  memberId: 2,
  memberCode: 'MB-0246',
  memberFullName: 'Taylor Nguyen',
  subtotalAmount: 900000,
  discountAmount: 90000,
  totalAmount: 810000,
  status: 'PAID',
  issueDate: '2026-10-02',
  taxAmount: 0,
  lines: [{ id: 1, itemType: 'SPORT_PACKAGE', description: 'Football / Coach-led / 30 days', quantity: 1, unitPrice: 900000, lineTotal: 900000 }],
}

function refund(overrides = {}) {
  return {
    id: 7,
    refundCode: 'RF-1040',
    packageRegistrationId: 10,
    registrationCode: 'REG-1040',
    memberId: 2,
    memberCode: 'MB-0246',
    memberFullName: 'Taylor Nguyen',
    amountRequested: 150000,
    amountApproved: null,
    reason: 'Cancelled before 24-hour deadline',
    status: 'PENDING',
    createdAt: '2026-10-03T10:00:00',
    paymentId: 40,
    paymentCode: 'PAY-0040',
    ...overrides,
  }
}

interface Options {
  me?: unknown
  list?: (url: URL) => Response
  onConfirm?: (call: RecordedCall) => Response | Promise<Response>
  onFail?: (call: RecordedCall) => Response
  onCreateRefund?: (call: RecordedCall) => Response
  onComplete?: (call: RecordedCall) => Response | Promise<Response>
  quote?: unknown
  memberRefunds?: unknown[] | Response
  onMemberRefunds?: (call: RecordedCall) => Response | Promise<Response>
  invoice?: Response
  onInvoice?: () => Response
  pendingRefunds?: unknown[] | Response
  payments?: Record<number, unknown>
}

function backend(options: Options = {}) {
  const payments: Record<number, unknown> = { 41: pendingPayment(), ...options.payments }
  const registrations = [FOOTBALL_REG, PAID_REG]
  let memberRefunds = options.memberRefunds ?? [refund()]
  return installFakeFetch((call) => {
    const url = new URL(`http://x${call.path}`)
    if (call.path === '/users/me') return json(200, options.me ?? RECEPTIONIST)
    if (url.pathname === '/payments' && call.method === 'GET') {
      if (options.list) return options.list(url)
      return json(200, { content: PENDING_ROWS, page: 0, size: 10, totalElements: 12, totalPages: 2 })
    }
    if (call.path === '/payments/41/confirm' && call.method === 'PUT') {
      if (options.onConfirm) return options.onConfirm(call)
      payments[41] = pendingPayment({ status: 'SUCCESS', paymentTime: '2026-10-02T10:15:00' })
      registrations[0] = { ...FOOTBALL_REG, status: 'ACTIVE' }
      return json(200, payments[41])
    }
    if (call.path === '/payments/41/fail' && call.method === 'PUT') {
      if (options.onFail) return options.onFail(call)
      payments[41] = pendingPayment({ status: 'FAILED', notes: (call.body as { reason: string }).reason })
      return json(200, payments[41])
    }
    const paymentMatch = /^\/payments\/(\d+)$/.exec(url.pathname)
    if (paymentMatch && payments[Number(paymentMatch[1])]) return json(200, payments[Number(paymentMatch[1])])
    if (call.path === '/invoices/by-payment/41') return options.onInvoice?.() ?? options.invoice?.clone() ?? json(200, INVOICE)
    if (call.path === '/membership-cards/member/2') return json(200, [card()])
    if (call.path === '/packages/registrations/member/2') return json(200, registrations)
    if (url.pathname === '/members' && call.method === 'GET') {
      return json(200, { content: [TAYLOR], number: 0, size: 10, totalElements: 1, totalPages: 1 })
    }
    if (url.pathname === '/refunds/quote') {
      if (options.quote instanceof Response) return options.quote.clone()
      return json(200, options.quote ?? {
        registrationId: Number(url.searchParams.get('registrationId')),
        registrationCode: 'REG-1040',
        packageName: 'Football / Coach-led / Single visit',
        paymentCode: 'PAY-0040',
        paymentAmount: 150000,
        paidAmount: 150000,
        suggestedAmount: 150000,
      })
    }
    if (call.path === '/refunds' && call.method === 'POST') {
      if (options.onCreateRefund) return options.onCreateRefund(call)
      const created = refund({ id: 8, refundCode: 'RF-1041', amountRequested: (call.body as { amountRequested: number }).amountRequested })
      memberRefunds = [created, ...(Array.isArray(memberRefunds) ? memberRefunds : [])]
      return json(201, created)
    }
    if (call.path === '/refunds/pending') {
      if ((options.me ?? RECEPTIONIST) === RECEPTIONIST) return backendError(403, 'Access Denied')
      const pending = options.pendingRefunds ?? [refund()]
      return pending instanceof Response ? pending.clone() : json(200, pending)
    }
    if (call.path === '/refunds/member/2') {
      if (options.onMemberRefunds) return options.onMemberRefunds(call)
      return memberRefunds instanceof Response ? memberRefunds.clone() : json(200, memberRefunds)
    }
    const complete = /^\/refunds\/(\d+)\/complete$/.exec(url.pathname)
    if (complete && call.method === 'PUT') {
      if (options.onComplete) return options.onComplete(call)
      const id = Number(complete[1])
      memberRefunds = (Array.isArray(memberRefunds) ? memberRefunds : []).map((item) =>
        (item as { id: number }).id === id
          ? { ...(item as object), status: 'COMPLETED' }
          : item,
      )
      return json(200, memberRefunds.find((item) => (item as { id: number }).id === id))
    }
    return backendError(404, 'Resource not found')
  })
}

async function open(path: string, options: Options = {}) {
  const fake = backend(options)
  tokenStorage.startSession({ accessToken: 'desk-access', refreshToken: 'desk-refresh' })
  renderApp(path)
  await screen.findByRole('heading', { level: 1 })
  return fake
}

async function verifyForm() {
  return screen.findByRole('form', { name: 'Verify and record payment' })
}

async function fillTransfer(amount = '810000', reference = 'BANK-1041-20261002') {
  const user = userEvent.setup()
  const form = await verifyForm()
  if (amount) await user.type(within(form).getByLabelText(/Amount received/), amount)
  if (reference) await user.type(within(form).getByLabelText(/Reference code/), reference)
  return { user, form }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 14, 9, 30))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('FE-12 Payment Requests (F3-04)', () => {
  it('TC-FE12-020 lists pending requests with paging, stat cards and Review payment', async () => {
    const { calls } = await open('/reception/payments')
    expect(screen.getByRole('heading', { name: 'Payment Requests' })).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: 'Payment requests' })
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(3))
    expect(calls.some((call) => call.path === '/payments?status=PENDING&page=0&size=10')).toBe(true)
    const rows = within(table).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('REG-1041')
    expect(rows[1]).toHaveTextContent('Taylor Nguyen')
    expect(rows[1]).toHaveTextContent('810.000 VND')
    expect(within(rows[1]).getAllByRole('link', { name: 'Review payment REG-1041' })[0]).toHaveAttribute('href', '/reception/payments/41')
    expect(screen.getByText('Pending requests').nextElementSibling).toHaveTextContent('12')
    expect(screen.getByText('Amount due on this page').nextElementSibling).toHaveTextContent('1.310.000 VND')
    expect(screen.getByText('Needs review on this page').nextElementSibling).toHaveTextContent('0')
    expect(calls.some((call) => call.path === '/refunds/pending')).toBe(false)
    expect(screen.getByText('Showing 1-10 of 12')).toBeInTheDocument()
  })

  it('UI-30 shows the queue under the Home theme hero with the refund link', async () => {
    await open('/reception/payments')
    const heading = screen.getByRole('heading', { level: 1, name: 'Payment Requests' })
    const hero = heading.closest('header') as HTMLElement
    expect(hero).toHaveClass('payment-hero')
    expect(within(hero).getByRole('link', { name: 'Refund requests' })).toHaveAttribute('href', '/reception/refunds')
  })

  it('UI-30 keeps the backend order and gives every row one status badge and exactly one action', async () => {
    const rows = [
      pendingPayment({ id: 44, code: 'PAY-0044', itemCode: 'REG-1044', notes: 'Amount mismatch: received 800000, expected 810000 by BANK_TRANSFER' }),
      pendingPayment(),
      pendingPayment({ id: 40, code: 'PAY-0040', itemCode: 'REG-1040', status: 'SUCCESS' }),
      pendingPayment({ id: 39, code: 'PAY-0039', itemCode: 'REG-1039', status: 'FAILED' }),
    ]
    await open('/reception/payments?status=ALL', { list: () => json(200, { content: rows, page: 0, size: 10, totalElements: 4, totalPages: 1 }) })
    const table = await screen.findByRole('table', { name: 'Payment requests' })
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(5))
    const body = within(table).getAllByRole('row').slice(1)
    expect(body.map((row) => within(row).getAllByText(/^REG-10\d\d$/)[0].textContent)).toEqual(['REG-1044', 'REG-1041', 'REG-1040', 'REG-1039'])
    expect(body[0]).toHaveTextContent('Needs review')
    expect(body[1]).toHaveTextContent('Pending payment')
    expect(body[2]).toHaveTextContent('Paid')
    expect(body[3]).toHaveTextContent('Failed')
    for (const row of body) expect(within(row).getAllByRole('link')).toHaveLength(1)
    expect(within(body[0]).getByRole('link', { name: 'Review payment REG-1044' })).toHaveAttribute('href', '/reception/payments/44')
    expect(within(body[2]).getByRole('link', { name: 'Open payment REG-1040' })).toHaveAttribute('href', '/reception/payments/40')
    expect(within(body[2]).queryByRole('link', { name: /Review payment/ })).not.toBeInTheDocument()
    expect(screen.getByText('Needs review on this page').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('Amount due on this page').nextElementSibling).toHaveTextContent('1.620.000 VND')
  })

  it('UI-30 a mismatch note on a payment that is no longer pending does not mark it for review', async () => {
    const rows = [pendingPayment({ status: 'SUCCESS', notes: 'Amount mismatch: received 800000, expected 810000' })]
    await open('/reception/payments?status=ALL', { list: () => json(200, { content: rows, page: 0, size: 10, totalElements: 1, totalPages: 1 }) })
    const table = await screen.findByRole('table', { name: 'Payment requests' })
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(2))
    expect(within(table).queryByText('Needs review')).not.toBeInTheDocument()
    expect(within(table).getByText('Paid')).toBeInTheDocument()
  })

  it('UI-30 free-text notes containing Amount mismatch without the machine contract do not mark for review', async () => {
    const rows = [
      pendingPayment({ id: 50, code: 'PAY-0050', itemCode: 'REG-1050', notes: 'Member inquiry regarding Amount mismatch: was clarified' }),
      pendingPayment({ id: 51, code: 'PAY-0051', itemCode: 'REG-1051', notes: 'Prior note | Amount mismatch: received 500000, expected 550000 by CASH' }),
    ]
    await open('/reception/payments?status=ALL', { list: () => json(200, { content: rows, page: 0, size: 10, totalElements: 2, totalPages: 1 }) })
    const table = await screen.findByRole('table', { name: 'Payment requests' })
    await waitFor(() => expect(within(table).getAllByRole('row')).toHaveLength(3))
    const body = within(table).getAllByRole('row').slice(1)
    expect(body[0]).toHaveTextContent('Pending payment')
    expect(within(body[0]).queryByText('Needs review')).not.toBeInTheDocument()
    expect(body[1]).toHaveTextContent('Needs review')
  })

  it('TC-FE12-021 status filter, search and paging call GET /payments with the matching parameters', async () => {
    const { calls } = await open('/reception/payments')
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Payment requests' })
    await user.selectOptions(screen.getByLabelText('Status'), 'ALL')
    await waitFor(() => expect(calls.some((call) => call.path === '/payments?page=0&size=10')).toBe(true))
    await user.type(screen.getByLabelText('Search member or reference'), 'REG-1041')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(calls.some((call) => call.path === '/payments?query=REG-1041&page=0&size=10')).toBe(true))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Page 2' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    await waitFor(() => expect(calls.some((call) => call.path === '/payments?query=REG-1041&page=1&size=10')).toBe(true))
  })

  it('TC-FE12-022 a list error shows the backend message and Try again', async () => {
    await open('/reception/payments', { list: () => backendError(500, 'Internal server error') })
    expect(await screen.findByText('Internal server error')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

describe('FE-12 Verify and Record Payment (F3-05)', () => {
  it('TC-FE12-030 Confirm stays disabled until the checkbox is ticked and the required fields are filled', async () => {
    const { calls } = await open('/reception/payments/41')
    const user = userEvent.setup()
    const form = await verifyForm()
    expect(within(form).getByDisplayValue('REG-1041 / Taylor Nguyen / MB-0246')).toBeInTheDocument()
    const confirm = within(form).getByRole('button', { name: 'Confirm payment' })
    const checkbox = within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' })
    expect(confirm).toBeDisabled()
    await user.click(checkbox)
    expect(confirm).toBeDisabled()
    await user.type(within(form).getByLabelText(/Amount received/), '810000')
    expect(confirm).toBeDisabled()
    await user.type(within(form).getByLabelText(/Reference code/), 'BANK-1041')
    expect(confirm).toBeEnabled()
    await user.clear(within(form).getByLabelText(/Reference code/))
    expect(confirm).toBeDisabled()
    await user.selectOptions(within(form).getByLabelText(/Payment method/), 'CASH')
    expect(confirm).toBeEnabled()
    await user.click(checkbox)
    expect(confirm).toBeDisabled()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })

  it('TC-FE12-031 confirm sends the payment, shows the receipt and the package ACTIVE', async () => {
    const { calls } = await open('/reception/payments/41')
    const { user, form } = await fillTransfer('810.000')
    const panel = screen.getByRole('region', { name: 'Amount verification' })
    expect(within(panel).getByTestId('difference')).toHaveTextContent('0 VND')
    expect(within(panel).getByText('Ready to confirm')).toBeInTheDocument()
    await user.type(within(form).getByLabelText('Note'), 'Matched bank statement')
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))

    expect(await screen.findByRole('heading', { name: 'Payment Confirmed and Receipt' })).toBeInTheDocument()
    expect(calls.find((call) => call.path === '/payments/41/confirm')?.body).toEqual({
      paymentMethod: 'BANK_TRANSFER',
      paymentReference: 'BANK-1041-20261002',
      amountReceived: 810000,
      note: 'Matched bank statement',
    })
    const receipt = screen.getByRole('region', { name: 'Payment receipt' })
    expect(await within(receipt).findByRole('heading', { name: 'Receipt INV-1041' })).toBeInTheDocument()
    expect(within(receipt).getByText('Bank transfer / BANK-1041-20261002')).toBeInTheDocument()
    expect(within(receipt).getByText('-90.000 VND')).toBeInTheDocument()
    expect(within(receipt).getByRole('link', { name: 'Open receipt' })).toHaveAttribute('href', '/reception/payments/41/receipt')
    const service = screen.getByRole('region', { name: 'Service updated' })
    expect(await within(service).findByText('Active')).toBeInTheDocument()
    expect(within(service).getByText('REG-1041')).toBeInTheDocument()
    expect(within(service).getByText('8 available')).toBeInTheDocument()
    expect(within(service).getByText(/Package access is active/)).toBeInTheDocument()
  })

  it('TC-FE12-032 409 PAYMENT_AMOUNT_MISMATCH shows Payment Needs Review and keeps the payment pending', async () => {
    const { calls } = await open('/reception/payments/41', {
      onConfirm: () =>
        json(409, {
          status: 409,
          message: 'Amount received does not match the amount due',
          code: 'PAYMENT_AMOUNT_MISMATCH',
          details: { amountDue: 810000, amountReceived: 800000 },
        }),
    })
    // The desk typed the exact amount, but the backend found a different amount on the payment.
    const { user, form } = await fillTransfer('810000')
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))

    expect(await screen.findByRole('heading', { name: 'Payment Needs Review' })).toBeInTheDocument()
    const review = screen.getByRole('region', { name: 'Amount mismatch' })
    expect(within(review).getByText('810.000 VND')).toBeInTheDocument()
    expect(within(review).getByText('800.000 VND')).toBeInTheDocument()
    expect(within(review).getByTestId('outstanding')).toHaveTextContent('10.000 VND')
    expect(within(review).getByText(/Payment remains Pending payment/)).toBeInTheDocument()
    expect(within(review).getByRole('alert')).toHaveTextContent('Amount received does not match the amount due')
    expect(calls.filter((call) => call.method === 'PUT').map((call) => call.path)).toEqual(['/payments/41/confirm'])

    await user.click(within(review).getByRole('button', { name: 'Return to verification' }))
    const again = await verifyForm()
    expect(within(again).getByLabelText(/Amount received/)).toHaveValue('810000')
    expect(within(again).getByRole('checkbox', { name: 'I have verified the full amount was received' })).not.toBeChecked()
    expect(within(again).getByRole('button', { name: 'Confirm payment' })).toBeDisabled()
  })

  it('TC-FE12-033 Amount does not match opens the review without calling the backend', async () => {
    const { calls } = await open('/reception/payments/41')
    const { user, form } = await fillTransfer('900000', '')
    expect(screen.getByRole('region', { name: 'Amount verification' })).toHaveTextContent('Amount is above the amount due')
    await user.click(within(form).getByRole('button', { name: 'Amount does not match' }))
    const review = await screen.findByRole('region', { name: 'Amount mismatch' })
    expect(within(review).getByText('Received above the amount due')).toBeInTheDocument()
    expect(within(review).getByTestId('outstanding')).toHaveTextContent('90.000 VND')
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })

  it('TC-FE12-034 Mark as failed needs a reason and sends PUT /payments/{id}/fail', async () => {
    const { calls } = await open('/reception/payments/41')
    const user = userEvent.setup()
    const form = await verifyForm()
    await user.click(within(form).getByRole('button', { name: 'Mark as failed' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mark payment as failed' })
    await user.click(within(dialog).getByRole('button', { name: 'Mark as failed' }))
    expect(within(dialog).getByText('Reason is required')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/payments/41/fail')).toBe(false)
    await user.type(within(dialog).getByLabelText(/Reason/), 'Transfer not found in the bank statement')
    await user.click(within(dialog).getByRole('button', { name: 'Mark as failed' }))
    expect(await screen.findByRole('heading', { name: 'Payment Failed' })).toBeInTheDocument()
    expect(calls.find((call) => call.path === '/payments/41/fail')?.body).toEqual({ reason: 'Transfer not found in the bank statement' })
    expect(screen.getByText('Transfer not found in the bank statement')).toBeInTheDocument()
  })

  it('TC-FE12-035 a backend field error is shown under the matching field and the form stays', async () => {
    await open('/reception/payments/41', {
      onConfirm: () => backendError(400, 'Validation Error', { paymentReference: 'This transaction was already recorded' }),
    })
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))
    expect(await within(form).findByText('This transaction was already recorded')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Verify and Record Payment' })).toBeInTheDocument()
  })

  it('TC-FE12-036 the receipt page shows the invoice of the payment (S3-Receipt)', async () => {
    const { calls } = await open('/reception/payments/41/receipt')
    expect(await screen.findByRole('heading', { name: 'Receipt INV-1041' })).toBeInTheDocument()
    const receipt = screen.getByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByText('INV-1041 / 02 Oct 2026')).toBeInTheDocument()
    expect(within(receipt).getByText('Taylor Nguyen / MB-0246')).toBeInTheDocument()
    expect(within(receipt).getByText('810.000 VND')).toBeInTheDocument()
    expect(within(receipt).getByText('Paid')).toBeInTheDocument()
    expect(within(receipt).getByRole('link', { name: 'Back' })).toHaveAttribute('href', '/reception/payments/41')
    expect(calls.some((call) => call.path === '/invoices/by-payment/41')).toBe(true)
  })
})

describe('UI-31 Verify payment and receipt', () => {
  async function confirmFilled() {
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox'))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))
    return form
  }

  it('UI-31 shows the verification under the Home theme hero with the way back', async () => {
    await open('/reception/payments/41')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Verify and Record Payment' })
    const hero = heading.closest('header') as HTMLElement
    expect(hero).toHaveClass('payment-hero')
    expect(within(hero).getByRole('link', { name: 'Payment requests' })).toHaveAttribute('href', '/reception/payments')
  })

  it('UI-31 a 200 answer that is not SUCCESS is never shown as a confirmed payment', async () => {
    const { calls } = await open('/reception/payments/41', { onConfirm: () => json(200, pendingPayment()) })
    const form = await confirmFilled()
    expect(await within(form).findByText(/The payment was not confirmed \(status: Pending payment\)/)).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/payments/41/confirm')).toHaveLength(1)
    expect(screen.queryByText('Payment confirmed')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Payment Confirmed and Receipt' })).not.toBeInTheDocument()
    expect(within(form).getByLabelText(/Amount received/)).toBeEnabled()
  })

  it('UI-31 a SUCCESS answer for another payment is not claimed as this one', async () => {
    await open('/reception/payments/41', { onConfirm: () => json(200, pendingPayment({ id: 99, status: 'SUCCESS' })) })
    const form = await confirmFilled()
    expect(await within(form).findByText(/The payment was not confirmed/)).toBeInTheDocument()
    expect(screen.queryByText('Payment confirmed')).not.toBeInTheDocument()
  })

  it('UI-31 a SUCCESS answer with another amount received gives no success message', async () => {
    const { calls } = await open('/reception/payments/41', {
      onConfirm: () => json(200, pendingPayment({ status: 'SUCCESS', amountReceived: 800000 })),
    })
    await confirmFilled()
    await waitFor(() => expect(calls.filter((call) => call.path === '/payments/41' && call.method === 'GET')).toHaveLength(2))
    expect(screen.queryByText('Payment confirmed')).not.toBeInTheDocument()
  })

  it('UI-31 a paid payment whose amount received differs is Needs review and cannot be printed', async () => {
    await open('/reception/payments/41', { payments: { 41: pendingPayment({ status: 'SUCCESS', amountReceived: 800000 }) } })
    expect(await screen.findByRole('heading', { level: 1, name: 'Payment Needs Review' })).toBeInTheDocument()
    const receipt = screen.getByRole('region', { name: 'Payment receipt' })
    await within(receipt).findByRole('heading', { name: 'Receipt INV-1041' })
    expect(within(receipt).getByRole('alert')).toHaveTextContent('The amount received (800.000 VND) differs from the amount due (810.000 VND)')
    expect(within(receipt).getByText('Needs review')).toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    expect(within(receipt).queryByRole('link', { name: 'Open receipt' })).not.toBeInTheDocument()
    expect(receipt).not.toHaveClass('payment-print-receipt')
    expect(screen.queryByRole('heading', { name: 'Payment Confirmed and Receipt' })).not.toBeInTheDocument()
  })

  it('UI-31 an invoice total that differs from the payment amount blocks the receipt', async () => {
    await open('/reception/payments/41', {
      payments: { 41: pendingPayment({ status: 'SUCCESS' }) },
      invoice: json(200, { ...INVOICE, totalAmount: 800000 }),
    })
    expect(await screen.findByRole('heading', { level: 1, name: 'Payment Needs Review' })).toBeInTheDocument()
    const receipt = screen.getByRole('region', { name: 'Payment receipt' })
    expect(within(receipt).getByRole('alert')).toHaveTextContent('The invoice total (800.000 VND) differs from the amount due (810.000 VND)')
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
  })

  it('UI-31 a confirmed payment without a base price does not invent one from the total', async () => {
    await open('/reception/payments/41', {
      payments: { 41: pendingPayment({ status: 'SUCCESS', originalPrice: null }) },
      invoice: json(200, { ...INVOICE, subtotalAmount: null }),
    })
    const receipt = await screen.findByRole('region', { name: 'Payment receipt' })
    await within(receipt).findByRole('heading', { name: 'Receipt INV-1041' })
    expect(within(receipt).queryByText('Base price')).not.toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeEnabled()
  })

  it('UI-31 two submits in the same tick send one confirmation', async () => {
    let finish!: (response: Response) => void
    const answer = new Promise<Response>((resolve) => { finish = resolve })
    const { calls } = await open('/reception/payments/41', { onConfirm: () => answer })
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox'))
    act(() => {
      fireEvent.submit(form)
      fireEvent.submit(form)
    })
    await waitFor(() => expect(calls.filter((call) => call.path === '/payments/41/confirm')).toHaveLength(1))
    await act(async () => finish(json(200, pendingPayment({ status: 'SUCCESS' }))))
    expect(calls.filter((call) => call.path === '/payments/41/confirm')).toHaveLength(1)
  })

  it('UI-31 the hero is not printed with a desk receipt', () => {
    const css = readFileSync('src/styles/reception.css', 'utf8')
    expect(css).toMatch(/@media print \{[\s\S]*body:has\(\.payment-print-receipt\) \.payment-hero \{\s*display: none;/)
  })

  it('UI-31 the receipt page never shows the invoice of another payment', async () => {
    await open('/reception/payments/41/receipt', { invoice: json(200, { ...INVOICE, paymentId: 99 }) })
    expect(await screen.findByText(/belongs to another payment/)).toBeInTheDocument()
    expect(screen.queryByText('INV-1041')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Print receipt' })).not.toBeInTheDocument()
  })

  it('UI-31 the receipt page rejects an invoice with null paymentId', async () => {
    await open('/reception/payments/41/receipt', { invoice: json(200, { ...INVOICE, paymentId: null }) })
    expect(await screen.findByText('Receipt not found')).toBeInTheDocument()
    expect(screen.queryByText('INV-1041')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Print receipt' })).not.toBeInTheDocument()
  })

  it('UI-31 the receipt page rejects an invoice with missing paymentId', async () => {
    const noPaymentId = { ...INVOICE }
    delete (noPaymentId as { paymentId?: unknown }).paymentId
    await open('/reception/payments/41/receipt', { invoice: json(200, noPaymentId) })
    expect(await screen.findByText('Receipt not found')).toBeInTheDocument()
    expect(screen.queryByText('INV-1041')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Print receipt' })).not.toBeInTheDocument()
  })

  it('UI-31 a cancelled invoice cannot be printed and missing amounts are not invented', async () => {
    await open('/reception/payments/41/receipt', { invoice: json(200, { ...INVOICE, status: 'CANCELLED', subtotalAmount: null, discountAmount: null }) })
    const receipt = await screen.findByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByRole('alert')).toHaveTextContent('cannot be printed as a payment receipt')
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    expect(within(receipt).queryByText('Base price')).not.toBeInTheDocument()
    expect(within(receipt).queryByText('Discount')).not.toBeInTheDocument()
    expect(receipt).not.toHaveClass('payment-print-receipt')
  })
})

describe('FE-12 Verify and Record Payment - review round 1 amount rules', () => {
  it('TC-FE12-037 a non-cash amount short of the amount due keeps Confirm disabled and points to the review', async () => {
    const { calls } = await open('/reception/payments/41')
    const { user, form } = await fillTransfer('800000')
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    expect(within(form).getByRole('button', { name: 'Confirm payment' })).toBeDisabled()
    expect(within(form).getByText(/must equal the amount due\. Use Amount does not match/)).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Amount verification' })).toHaveTextContent('Amount is short - review needed')
    expect(within(form).getByRole('button', { name: 'Amount does not match' })).toBeEnabled()
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })

  it('TC-FE12-038 a non-cash amount above the amount due keeps Confirm disabled', async () => {
    await open('/reception/payments/41')
    const { user, form } = await fillTransfer('900000')
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    expect(within(form).getByRole('button', { name: 'Confirm payment' })).toBeDisabled()
    expect(screen.getByRole('region', { name: 'Amount verification' })).toHaveTextContent('Amount is above the amount due - review needed')
  })

  it('TC-FE12-039 cash above the amount due blocks confirmation and opens review without a PUT', async () => {
    const { calls } = await open('/reception/payments/41')
    const user = userEvent.setup()
    const form = await verifyForm()
    await user.selectOptions(within(form).getByLabelText(/Payment method/), 'CASH')
    await user.type(within(form).getByLabelText(/Amount received/), '1.000.000')
    const panel = screen.getByRole('region', { name: 'Amount verification' })
    expect(within(panel).getByTestId('received')).toHaveTextContent('1.000.000 VND')
    expect(panel).toHaveTextContent('Amount is above the amount due - review needed')
    expect(within(form).getByRole('button', { name: 'Amount does not match' })).toBeEnabled()
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    expect(within(form).getByRole('button', { name: 'Confirm payment' })).toBeDisabled()
    await user.click(within(form).getByRole('button', { name: 'Amount does not match' }))
    expect(screen.getByTestId('outstanding')).toHaveTextContent('190.000 VND')
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Return to verification' }))
    expect(screen.getByLabelText(/Amount received/)).toHaveValue('1.000.000')
    expect(screen.getByRole('checkbox', { name: 'I have verified the full amount was received' })).not.toBeChecked()
  })

  it('TC-FE12-048 an amount due that is not a number shows an error and blocks both actions', async () => {
    const { calls } = await open('/reception/payments/41', { payments: { 41: pendingPayment({ amount: 'abc' }) } })
    const form = await verifyForm()
    expect(within(form).getByRole('alert')).toHaveTextContent('The amount due of this payment is not valid')
    const user = userEvent.setup()
    await user.selectOptions(within(form).getByLabelText(/Payment method/), 'CASH')
    await user.type(within(form).getByLabelText(/Amount received/), '810000')
    await user.click(within(form).getByRole('checkbox', { name: 'I have verified the full amount was received' }))
    expect(within(form).getByRole('button', { name: 'Confirm payment' })).toBeDisabled()
    expect(within(form).getByRole('button', { name: 'Amount does not match' })).toBeDisabled()
    expect(screen.getByTestId('amount-due')).toHaveTextContent('-')
    expect(document.body.textContent).not.toMatch(/NaN/)
    expect(calls.some((call) => call.method === 'PUT')).toBe(false)
  })
})

describe('FE-12 Refund Requests (F3-07)', () => {
  it('TC-FE12-040 finds the registration, prefills the suggested amount and submits for approval', async () => {
    const { calls } = await open('/reception/refunds')
    const user = userEvent.setup()
    expect(screen.getByRole('heading', { name: 'Refund Requests' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Member'), 'Taylor')
    await user.click(screen.getByRole('button', { name: 'Find member' }))
    await user.click(await screen.findByRole('button', { name: 'Select Taylor Nguyen MB-0246' }))
    const paid = await screen.findByRole('list', { name: 'Paid registrations' })
    expect(within(paid).getAllByRole('listitem')).toHaveLength(1)
    await user.click(within(paid).getByRole('link', { name: 'Start refund for REG-1040' }))

    const form = await screen.findByRole('form', { name: 'Submit refund' })
    expect(calls.some((call) => call.path === '/refunds/quote?registrationId=10')).toBe(true)
    expect(within(form).getByLabelText(/Refund amount/)).toHaveValue('150000')
    expect(screen.getByRole('region', { name: 'Eligibility check' })).toHaveTextContent('Eligible for review')
    await user.click(within(form).getByRole('button', { name: 'Submit for approval' }))
    expect(within(form).getByText('Reason is required')).toBeInTheDocument()
    await user.type(within(form).getByLabelText(/Reason/), 'Cancelled before 24-hour deadline')
    await user.click(within(form).getByRole('button', { name: 'Submit for approval' }))

    expect(await screen.findByRole('heading', { name: 'Refund Request Submitted' })).toBeInTheDocument()
    expect(calls.find((call) => call.path === '/refunds' && call.method === 'POST')?.body).toEqual({
      packageRegistrationId: 10,
      amountRequested: 150000,
      reason: 'Cancelled before 24-hour deadline',
    })
    const detail = screen.getByRole('region', { name: 'Refund' })
    expect(within(detail).getByRole('heading', { name: 'RF-1041 / Awaiting approval' })).toBeInTheDocument()
    expect(within(detail).getByText('PAY-0040')).toBeInTheDocument()
    expect(within(detail).getByText('Pending approval')).toBeInTheDocument()
  })

  it('TC-FE12-041 more than the refundable balance or an ineligible quote never reaches POST /refunds', async () => {
    const { calls } = await open('/reception/refunds?memberId=2&registrationId=10')
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Submit refund' })
    const amount = within(form).getByLabelText(/Refund amount/)
    await user.clear(amount)
    await user.type(amount, '200000')
    await user.type(within(form).getByLabelText(/Reason/), 'Too much')
    await user.click(within(form).getByRole('button', { name: 'Submit for approval' }))
    expect(within(form).getByText('Refund amount is more than the refundable balance')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/refunds' && call.method === 'POST')).toBe(false)

    vi.unstubAllGlobals()
    document.body.innerHTML = ''
    await open('/reception/refunds?memberId=2&registrationId=10', {
      quote: backendError(400, 'Only active package registrations can be refunded'),
    })
    expect(await screen.findByText('Only active package registrations can be refunded')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Submit refund' })).not.toBeInTheDocument()
  })

  it('TC-FE12-042 approved payout needs confirmation, sends no body and reloads the completed state', async () => {
    const { calls } = await open('/reception/refunds/7?memberId=2', {
      memberRefunds: [refund({ status: 'APPROVED', amountApproved: 150000 })],
    })
    expect(await screen.findByRole('heading', { name: 'Refund Approved' })).toBeInTheDocument()
    const user = userEvent.setup()
    const form = screen.getByRole('form', { name: 'Record completed refund' })
    const record = within(form).getByRole('button', { name: 'Record completed refund' })
    expect(record).toBeDisabled()
    await user.click(within(form).getByRole('checkbox', { name: 'I have returned the approved amount' }))
    await user.click(record)
    // UI-32: the payout cannot be undone, so it is confirmed once more with the amount.
    expect(calls.filter((call) => call.path === '/refunds/7/complete')).toHaveLength(0)
    const dialog = await screen.findByRole('dialog', { name: 'Record the refund payout?' })
    await user.click(within(dialog).getByRole('button', { name: 'Yes, record 150.000 VND' }))
    expect(await screen.findByRole('heading', { name: 'Refund Completed' })).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/refunds/7/complete')).toHaveLength(1)
    expect(calls.find((call) => call.path === '/refunds/7/complete')?.body).toBeUndefined()
    const detail = screen.getByRole('region', { name: 'Refund' })
    expect(within(detail).getByRole('heading', { name: 'Refund receipt / RF-1040' })).toBeInTheDocument()
    expect(within(detail).queryByText('Refund reference')).not.toBeInTheDocument()
    expect(within(detail).getByText('Status').nextSibling).toHaveTextContent('Refunded')
    expect(screen.queryByText(/notified/i)).not.toBeInTheDocument()
  })

  it('TC-FE12-043 a complete error stays on the Approved state with the backend message', async () => {
    await open('/reception/refunds/7?memberId=2', {
      memberRefunds: [refund({ status: 'APPROVED', amountApproved: 150000 })],
      onComplete: () => backendError(400, 'Only approved refunds can be completed'),
    })
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Record completed refund' })
    await user.click(within(form).getByRole('checkbox', { name: 'I have returned the approved amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record completed refund' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /Yes, record/ }))
    expect(await within(form).findByText('Only approved refunds can be completed')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Refund Approved' })).toBeInTheDocument()
  })

  it('UI-32 Cancel in the payout confirmation sends nothing and keeps the refund approved', async () => {
    const { calls } = await open('/reception/refunds/7?memberId=2', {
      memberRefunds: [refund({ status: 'APPROVED', amountApproved: 120000 })],
    })
    const user = userEvent.setup()
    const form = await screen.findByRole('form', { name: 'Record completed refund' })
    await user.click(within(form).getByRole('checkbox', { name: 'I have returned the approved amount' }))
    await user.click(within(form).getByRole('button', { name: 'Record completed refund' }))
    const dialog = await screen.findByRole('dialog', { name: 'Record the refund payout?' })
    // The confirmation names the approved amount from the API, the member and the refund.
    expect(dialog).toHaveTextContent('120.000 VND returned to Taylor Nguyen / MB-0246 for RF-1040. This cannot be undone.')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls.some((call) => call.path === '/refunds/7/complete')).toBe(false)
    expect(screen.getByRole('heading', { name: 'Refund Approved' })).toBeInTheDocument()
  })

  it.each([
    ['PENDING', ['Requested', 'Approved', 'Refunded'], 'Requested'],
    ['APPROVED', ['Requested', 'Approved', 'Refunded'], 'Approved'],
    ['COMPLETED', ['Requested', 'Approved', 'Refunded'], 'Refunded'],
    ['REJECTED', ['Requested', 'Rejected'], 'Rejected'],
  ])('UI-32 a %s refund shows its progress with the current step marked', async (status, steps, current) => {
    await open('/reception/refunds/7?memberId=2', { memberRefunds: [refund({ status, amountApproved: status === 'PENDING' ? null : 150000 })] })
    const progress = await screen.findByRole('list', { name: 'Refund progress' })
    expect(within(progress).getAllByRole('listitem').map((item) => item.textContent)).toEqual(steps)
    expect(within(progress).getByText(current)).toHaveAttribute('aria-current', 'step')
    expect(progress.querySelectorAll('[aria-current]')).toHaveLength(1)
  })

  it('UI-32 refund pages use the Home theme hero', async () => {
    await open('/reception/refunds')
    const heading = screen.getByRole('heading', { level: 1, name: 'Refund Requests' })
    expect(heading.closest('.payment-workspace')).toHaveClass('home-page')
    expect(heading.closest('header')).toHaveClass('payment-hero')
  })

  it('TC-FE12-044 a rejected refund shows the manager note; an unknown id is not found', async () => {
    await open('/reception/refunds/7?memberId=2', {
      memberRefunds: [refund({ status: 'REJECTED', managerNote: 'Session already used' })],
    })
    expect(await screen.findByRole('heading', { name: 'Refund Rejected' })).toBeInTheDocument()
    expect(screen.getByText('Session already used')).toBeInTheDocument()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
    await open('/reception/refunds/99?memberId=2')
    expect(await screen.findByRole('heading', { name: 'Refund not found' })).toBeInTheDocument()
  })

  it('TC-FE12-045 queues: approved first, member history without PENDING, pending once', async () => {
    const { calls } = await open('/reception/refunds?memberId=2', {
      memberRefunds: [
        refund({ status: 'APPROVED', amountApproved: 150000 }),
        refund({ id: 6, refundCode: 'RF-1039', status: 'COMPLETED', createdAt: '2026-09-01T10:00:00' }),
        refund({ id: 9, refundCode: 'RF-1042', status: 'PENDING' }),
      ],
      pendingRefunds: [refund({ id: 9, refundCode: 'RF-1042', status: 'PENDING' })],
    })
    expect(calls.some((call) => call.path === '/refunds?status=APPROVED' || call.path === '/refunds/pending')).toBe(false)
    const tables = await screen.findAllByRole('table')
    expect(tables[0]).toHaveAccessibleName('Approved refunds waiting to be refunded')
    const approved = tables[0]
    await waitFor(() => expect(within(approved).getAllByRole('row')).toHaveLength(2))
    expect(within(approved).getByRole('link', { name: 'Open refund RF-1040' })).toHaveTextContent('Record refund')
    expect(within(approved).getByRole('link', { name: 'Open refund RF-1040' })).toHaveAttribute('href', '/reception/refunds/7?memberId=2')

    const member = screen.getByRole('table', { name: 'Refunds of this member' })
    await waitFor(() => expect(within(member).getAllByRole('row')).toHaveLength(2))
    expect(within(member).getByText('Refunded')).toBeInTheDocument()
    expect(within(member).queryByText('RF-1042')).not.toBeInTheDocument()
    const pending = screen.getByRole('table', { name: 'Refunds waiting for approval' })
    await waitFor(() => expect(within(pending).getAllByRole('row')).toHaveLength(2))
    expect(screen.getAllByText('RF-1042')).toHaveLength(1)
  })

  it('TC-FE12-046 a link without memberId explains how to recover context without unsupported calls', async () => {
    const { calls } = await open('/reception/refunds/7')
    expect(await screen.findByRole('heading', { name: 'Member context required' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to refund requests' })).toHaveAttribute('href', '/reception/refunds')
    expect(calls.some((call) => call.path.startsWith('/refunds'))).toBe(false)
  })

  it('TC-FE12-047 a member refund failure shows the error and Retry recovers', async () => {
    let attempt = 0
    const { calls } = await open('/reception/refunds?memberId=2', {
      onMemberRefunds: () => ++attempt === 1 ? backendError(500, 'Refund service unavailable') : json(200, [refund({ status: 'APPROVED' })]),
    })
    expect(await screen.findByText('Refund service unavailable')).toBeInTheDocument()
    expect(screen.queryByText('No approved refund is waiting')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table', { name: 'Approved refunds waiting to be refunded' })).toHaveTextContent('RF-1040')
    expect(calls.filter((call) => call.path === '/refunds/member/2')).toHaveLength(2)
  })

  it('TC-FE12-049 a detail API failure is an error, not Refund not found', async () => {
    await open('/reception/refunds/7?memberId=2', {
      memberRefunds: backendError(403, 'Access Denied'),
    })
    expect(await screen.findByRole('heading', { name: 'We could not load the refund' })).toBeInTheDocument()
    expect(screen.getByText('Access Denied')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Refund not found' })).not.toBeInTheDocument()
  })

  it('TC-FE12-R3-01 empty member queues show empty states and no global queue is fabricated', async () => {
    const { calls } = await open('/reception/refunds?memberId=2', { memberRefunds: [] })
    expect(await screen.findByText('No approved refund is waiting')).toBeInTheDocument()
    expect(screen.getByText('No refund history to show')).toBeInTheDocument()
    expect(screen.getByText('No refund is waiting')).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/refunds/member/2')).toHaveLength(1)
    expect(calls.some((call) => call.path === '/refunds?status=APPROVED' || call.path === '/refunds/pending')).toBe(false)
  })

  it.each(['PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'])(
    'TC-FE12-R3-02 reload resolves %s from memberId in the URL', async (status) => {
      const { calls } = await open('/reception/refunds/7?memberId=2', { memberRefunds: [refund({ status })] })
      const headings: Record<string, string> = { PENDING: 'Refund Request Submitted', APPROVED: 'Refund Approved', REJECTED: 'Refund Rejected', COMPLETED: 'Refund Completed' }
      expect(await screen.findByRole('heading', { name: headings[status] })).toBeInTheDocument()
      expect(calls.filter((call) => call.path === '/refunds/member/2')).toHaveLength(1)
      expect(calls.some((call) => call.path === '/refunds?status=APPROVED' || call.path === '/refunds/pending')).toBe(false)
    },
  )
})

describe('FE-12 access', () => {
  it.each(['/reception/payments', '/reception/payments/41', '/reception/payments/41/receipt', '/reception/refunds', '/reception/refunds/7'])(
    'TC-FE12-050 a MEMBER cannot open %s',
    async (path) => {
      const { calls } = await open(path, { me: MEMBER })
      expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
      expect(calls.some((call) => call.path.startsWith('/payments') || call.path.startsWith('/refunds') || call.path.startsWith('/invoices'))).toBe(false)
    },
  )

  it('UI-30 a COACH cannot open the payment queue and no payment is requested', async () => {
    const { calls } = await open('/reception/payments', { me: { ...RECEPTIONIST, id: 12, fullName: 'Quan Coach', role: 'COACH' } })
    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/payments'))).toBe(false)
  })
})

describe('FE-12 round 3 integration regressions', () => {
  it.each([['CASH', '800000'], ['CREDIT_CARD', '800000'], ['CREDIT_CARD', '820000']])(
    '%s amount %s cannot submit a mismatch', async (method, amount) => {
      const { calls } = await open('/reception/payments/41')
      const { user, form } = await fillTransfer(amount, method === 'CASH' ? '' : 'TEST-REFERENCE')
      await user.selectOptions(within(form).getByLabelText(/Payment method/), method)
      await user.click(within(form).getByRole('checkbox'))
      await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))
      expect(calls.some((call) => call.method === 'PUT')).toBe(false)
      await user.click(within(form).getByRole('button', { name: 'Amount does not match' }))
      expect(screen.getByTestId('outstanding')).toHaveTextContent('10.000 VND')
    },
  )

  it('an unrelated 409 remains a form error, not an amount mismatch', async () => {
    await open('/reception/payments/41', { onConfirm: () => json(409, { message: 'Payment changed; reload', code: 'PAYMENT_NOT_PENDING' }) })
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox'))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))
    expect(await within(form).findByText('Payment changed; reload')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Payment Needs Review' })).not.toBeInTheDocument()
  })

  it('backend canonical referenceCode validation maps to the reference input', async () => {
    await open('/reception/payments/41', { onConfirm: () => backendError(400, 'Validation Error', { referenceCode: 'Reference is not valid' }) })
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox'))
    await user.click(within(form).getByRole('button', { name: 'Confirm payment' }))
    expect(await within(form).findByText('Reference is not valid')).toBeInTheDocument()
    expect(within(form).getByLabelText(/Reference code/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('duplicate confirm clicks send only one in-flight PUT', async () => {
    let finish!: (response: Response) => void
    const answer = new Promise<Response>((resolve) => { finish = resolve })
    const { calls } = await open('/reception/payments/41', { onConfirm: () => answer })
    const { user, form } = await fillTransfer()
    await user.click(within(form).getByRole('checkbox'))
    await user.dblClick(within(form).getByRole('button', { name: 'Confirm payment' }))
    expect(calls.filter((call) => call.path === '/payments/41/confirm')).toHaveLength(1)
    expect(within(form).getByLabelText(/Amount received/)).toBeDisabled()
    await act(async () => finish(backendError(409, 'Payment changed; reload')))
    expect(await within(form).findByText('Payment changed; reload')).toBeInTheDocument()
  })

  it('leaving member context ignores a late member-refund response', async () => {
    let finish!: (response: Response) => void
    const answer = new Promise<Response>((resolve) => { finish = resolve })
    await open('/reception/refunds?memberId=2', { onMemberRefunds: () => answer })
    const changeMember = await screen.findByRole('button', { name: 'Change member' })
    await userEvent.setup().click(changeMember)
    await act(async () => finish(json(200, [refund({ status: 'APPROVED' })])))
    expect(screen.getByLabelText('Member')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Approved refunds waiting to be refunded' })).not.toBeInTheDocument()
    expect(screen.queryByText('RF-1040')).not.toBeInTheDocument()
  })

  it('receipt errors are shown and retry loads the invoice', async () => {
    let attempt = 0
    const fake = backend({ onInvoice: () => ++attempt === 1 ? backendError(500, 'Invoice unavailable') : json(200, INVOICE) })
    tokenStorage.startSession({ accessToken: 'desk-access', refreshToken: 'desk-refresh' })
    const view = renderApp('/reception/payments/41/receipt')
    expect(await screen.findByText('Invoice unavailable')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Receipt INV-1041' })).toBeInTheDocument()
    view.unmount()
    // A fresh page load has no receipt router state and must fetch the invoice again.
    renderApp('/reception/payments/41/receipt')
    expect(await screen.findByRole('heading', { name: 'Receipt INV-1041' })).toBeInTheDocument()
    expect(fake.calls.filter((call) => call.path === '/invoices/by-payment/41')).toHaveLength(3)
  })

  it('duplicate completion clicks send one PUT and a rejection never claims completion', async () => {
    let finish!: (response: Response) => void
    const answer = new Promise<Response>((resolve) => { finish = resolve })
    const { calls } = await open('/reception/refunds/7?memberId=2', {
      memberRefunds: [refund({ status: 'APPROVED', amountApproved: 150000 })], onComplete: () => answer,
    })
    const form = await screen.findByRole('form', { name: 'Record completed refund' })
    const user = userEvent.setup()
    await user.click(within(form).getByRole('checkbox'))
    await user.dblClick(within(form).getByRole('button', { name: 'Record completed refund' }))
    await user.dblClick(within(await screen.findByRole('dialog')).getByRole('button', { name: /Yes, record/ }))
    expect(calls.filter((call) => call.path === '/refunds/7/complete')).toHaveLength(1)
    await act(async () => finish(backendError(400, 'Only approved refunds can be completed')))
    expect(await within(form).findByText('Only approved refunds can be completed')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Refund Completed' })).not.toBeInTheDocument()
  })

  it('the actual BE-17 payment detail shape works without staff-list enrichment', async () => {
    await open('/reception/payments/41', { payments: { 41: pendingPayment({ memberFullName: undefined, itemCode: undefined }) } })
    const form = await verifyForm()
    expect(within(form).getByDisplayValue('PAY-0041 / MB-0246')).toBeInTheDocument()
  })

  it('a quote with zero unused sessions disables submission using the actual BE-21 field', async () => {
    const { calls } = await open('/reception/refunds?memberId=2&registrationId=10', {
      quote: { registrationId: 10, paymentAmount: 150000, suggestedAmount: 0, unusedSessions: 0 },
    })
    const form = await screen.findByRole('form', { name: 'Submit refund' })
    expect(within(form).getByRole('button', { name: 'Submit for approval' })).toBeDisabled()
    expect(screen.getByText('Cannot refund package with zero remaining sessions')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/refunds' && call.method === 'POST')).toBe(false)
  })

  it('a failed invoice cannot be printed; Retry must load the real invoice first', async () => {
    let attempt = 0
    await open('/reception/payments/41', {
      payments: { 41: pendingPayment({ status: 'SUCCESS' }) },
      onInvoice: () => ++attempt === 1 ? backendError(500, 'Invoice unavailable') : json(200, INVOICE),
    })
    expect(await screen.findByText(/The invoice could not be loaded/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Print receipt' })).toBeEnabled())
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    await user.click(screen.getByRole('button', { name: 'Print receipt' }))
    expect(print).toHaveBeenCalledTimes(1)
    print.mockRestore()
  })

  it('QR-05 displays Paid - Simulated and simulation provenance in receptionist payment detail and receipt', async () => {
    await open('/reception/payments/41', {
      payments: {
        41: pendingPayment({
          status: 'SUCCESS',
          settlementType: 'SIMULATED',
          settlementProvider: 'SPORTIFY_SIMULATOR',
          paymentReference: 'SIM-QR-41-00000041',
        }),
      },
      invoice: json(200, {
        ...INVOICE,
        settlementType: 'SIMULATED',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        paymentReference: 'SIM-QR-41-00000041',
      }),
    })
    expect(await screen.findByText('Paid - Simulated')).toBeInTheDocument()
    expect(screen.getByText(/SIMULATED SETTLEMENT - Completed via Sportify QR Simulator/)).toBeInTheDocument()
    expect(screen.getByText('SIMULATED (SPORTIFY_SIMULATOR)')).toBeInTheDocument()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''

    await open('/reception/payments/41/receipt', {
      invoice: json(200, {
        ...INVOICE,
        settlementType: 'SIMULATED',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        paymentReference: 'SIM-QR-41-00000041',
      }),
    })
    expect(await screen.findByText('Paid - Simulated')).toBeInTheDocument()
    expect(screen.getByText(/SIMULATED SETTLEMENT - Completed via Sportify QR Simulator/)).toBeInTheDocument()
    expect(screen.getByText('SIMULATED (SPORTIFY_SIMULATOR)')).toBeInTheDocument()
  })
})
