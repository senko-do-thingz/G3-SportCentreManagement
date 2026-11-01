import { readFileSync } from 'node:fs'
import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json } from '../../test/fakeBackend'
import { card, payment, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

const RECEPTIONIST = {
  id: 9,
  email: 'reception@sportify.demo',
  fullName: 'Jamie Tran',
  phone: '0901234599',
  role: 'RECEPTIONIST',
  status: 'ACTIVE',
}

const LEGACY_INVOICE = {
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
  settlementType: null,
  settlementProvider: null,
  simulatedTransactionId: null,
  issueDate: '2026-10-02',
  taxAmount: 0,
  lines: [
    {
      id: 1,
      itemType: 'SPORT_PACKAGE',
      description: 'Football / Coach-led / 30 days',
      quantity: 1,
      unitPrice: 900000,
      lineTotal: 900000,
    },
  ],
}

interface TestOptions {
  invoice?: unknown
  paymentRecord?: Record<string, unknown>
}

function setupFakeReceptionBackend(options: TestOptions = {}) {
  const base = payment({
    id: 41,
    code: 'PAY-0041',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 810000,
    originalPrice: 900000,
    discount: 90000,
    itemDescription: 'Football / Coach-led / 30 days',
    memberId: 2,
    memberCode: 'MB-0246',
  })
  const currentPayment = {
    ...base,
    memberFullName: 'Taylor Nguyen',
    itemCode: 'REG-1041',
    settlementType: null,
    settlementProvider: null,
    ...options.paymentRecord,
  }

  return installFakeFetch((call) => {
    const url = new URL(`http://x${call.path}`)
    if (call.path === '/users/me') return json(200, RECEPTIONIST)
    if (call.path === '/payments/41') return json(200, currentPayment)
    if (call.path === '/invoices/by-payment/41') {
      const invoiceData = options.invoice ?? LEGACY_INVOICE
      return json(200, invoiceData)
    }
    if (call.path === '/membership-cards/member/2') return json(200, [card()])
    if (call.path === '/packages/registrations/member/2') {
      return json(200, [
        registration({
          id: 11,
          registrationCode: 'REG-1041',
          status: 'ACTIVE',
          paymentId: 41,
          startDate: '2026-10-02',
          endDate: '2026-10-31',
          remainingSessions: 8,
        }),
      ])
    }
    if (url.pathname === '/refunds/member/2') return json(200, [])
    return backendError(404, 'Resource not found')
  })
}

async function openReception(path: string, options: TestOptions = {}) {
  setupFakeReceptionBackend(options)
  tokenStorage.startSession({ accessToken: 'desk-access', refreshToken: 'desk-refresh' })
  renderApp(path)
  if (path.endsWith('/receipt')) {
    await screen.findByRole('heading', { name: /^Receipt INV-/ })
  } else {
    await screen.findByRole('heading', { name: 'Payment Confirmed and Receipt' })
    await screen.findByRole('heading', { name: /^Receipt INV-/ })
  }
}

describe('QR-07 D03 - Reception Legacy Provenance & Receipt Maintenance (QR07-056, QR07-069)', () => {
  beforeEach(() => {
    vi.stubGlobal('isSecureContext', true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
    tokenStorage.clear()
  })

  it('QR07-056/QR07-069: ReceptionReceiptPage displays Legacy unverified and disables print for NULL settlementType', async () => {
    await openReception('/reception/payments/41/receipt', {
      invoice: LEGACY_INVOICE,
    })

    // Hero description must not claim confirmed payment record
    expect(
      screen.getByText('Historical payment record without verified settlement provenance.'),
    ).toBeInTheDocument()

    const receipt = screen.getByRole('region', { name: 'Receipt' })

    // Must visibly display Legacy unverified status and provenance
    expect(within(receipt).getByText('Paid - Legacy unverified')).toBeInTheDocument()
    expect(within(receipt).getByText('Legacy unverified')).toBeInTheDocument()

    // Must render explicit prominent warning banner
    expect(
      within(receipt).getByText(/LEGACY UNVERIFIED - Historical payment record without verified settlement provenance/),
    ).toBeInTheDocument()

    // Print receipt button must be disabled to prevent generating unverified receipts
    const printButton = within(receipt).getByRole('button', { name: 'Print receipt' })
    expect(printButton).toBeDisabled()

    // Alert explaining print restriction must be displayed
    expect(
      within(receipt).getByRole('alert'),
    ).toHaveTextContent(/This invoice lacks verified settlement provenance \(Legacy unverified\)/)

    // Receipt card must not have print receipt class
    expect(receipt).not.toHaveClass('payment-print-receipt')
  })

  it('QR07-056/QR07-069: ReceptionReceiptPage displays Legacy unverified for UNKNOWN settlementType', async () => {
    await openReception('/reception/payments/41/receipt', {
      invoice: {
        ...LEGACY_INVOICE,
        settlementType: 'UNKNOWN',
      },
    })

    const receipt = screen.getByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByText('Paid - Legacy unverified')).toBeInTheDocument()
    expect(
      within(receipt).getByText(/LEGACY UNVERIFIED - Historical payment record without verified settlement provenance/),
    ).toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    expect(receipt).not.toHaveClass('payment-print-receipt')
  })

  it('QR07-069: ReceptionReceiptPage flags contradictory/inconsistent provenance and blocks receipt printing', async () => {
    await openReception('/reception/payments/41/receipt', {
      invoice: {
        ...LEGACY_INVOICE,
        settlementType: 'ACTUAL',
        settlementProvider: 'SPORTIFY_SIMULATOR', // Contradictory provider
      },
    })

    const receipt = screen.getByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByText('Paid - Inconsistent provenance')).toBeInTheDocument()
    expect(within(receipt).getByText('Inconsistent provenance')).toBeInTheDocument()
    expect(
      within(receipt).getByText(/INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified/),
    ).toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    expect(receipt).not.toHaveClass('payment-print-receipt')
  })

  it('QR07-069: ReceptionReceiptPage allows verified receipt printing for genuine ACTUAL settlement', async () => {
    await openReception('/reception/payments/41/receipt', {
      invoice: {
        ...LEGACY_INVOICE,
        settlementType: 'ACTUAL',
        settlementProvider: 'DESK_CASH',
      },
    })

    expect(screen.getByText('Confirmed payment record.')).toBeInTheDocument()

    const receipt = screen.getByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByText('Paid')).toBeInTheDocument()
    expect(within(receipt).getByText('ACTUAL (DESK_CASH)')).toBeInTheDocument()
    expect(
      within(receipt).queryByText(/LEGACY UNVERIFIED/),
    ).not.toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeEnabled()
    expect(receipt).toHaveClass('payment-print-receipt')
  })

  it('QR07-069: ReceptionReceiptPage preserves SIMULATED warning while allowing simulated printout', async () => {
    await openReception('/reception/payments/41/receipt', {
      invoice: {
        ...LEGACY_INVOICE,
        settlementType: 'SIMULATED',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        simulatedTransactionId: 9001,
      },
    })

    const receipt = screen.getByRole('region', { name: 'Receipt' })
    expect(within(receipt).getByText('Paid - Simulated')).toBeInTheDocument()
    expect(within(receipt).getByText('SIMULATED (SPORTIFY_SIMULATOR)')).toBeInTheDocument()
    expect(
      within(receipt).getByText(/SIMULATED SETTLEMENT - Completed via Sportify QR Simulator/),
    ).toBeInTheDocument()
    expect(within(receipt).getByRole('button', { name: 'Print receipt' })).toBeEnabled()
    expect(receipt).toHaveClass('payment-print-receipt')
  })

  it('QR07-056: ReceptionPaymentPage displays Paid - Legacy unverified and unverified notice when invoice has null provenance', async () => {
    await openReception('/reception/payments/41', {
      invoice: LEGACY_INVOICE,
    })

    const receiptCard = screen.getByRole('region', { name: 'Payment receipt' })
    expect(within(receiptCard).getByText('Paid - Legacy unverified')).toBeInTheDocument()
    expect(within(receiptCard).getByText('Legacy unverified')).toBeInTheDocument()
    expect(
      within(receiptCard).getByText(/LEGACY UNVERIFIED - Historical payment record without verified settlement provenance/),
    ).toBeInTheDocument()
    expect(within(receiptCard).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
    expect(
      within(receiptCard).getByText(/This payment record lacks verified settlement provenance \(Legacy unverified\)/),
    ).toBeInTheDocument()
  })

  it('ReceptionPaymentPage displays Paid - Inconsistent provenance when provenance metadata contradicts', async () => {
    await openReception('/reception/payments/41', {
      invoice: {
        ...LEGACY_INVOICE,
        settlementType: 'SIMULATED',
        settlementProvider: 'DESK_CASH', // Inconsistent: SIMULATED with non-simulator provider
      },
    })

    const receiptCard = screen.getByRole('region', { name: 'Payment receipt' })
    expect(within(receiptCard).getByText('Paid - Inconsistent provenance')).toBeInTheDocument()
    expect(within(receiptCard).getByRole('button', { name: 'Print receipt' })).toBeDisabled()
  })

  it('Print CSS regression: ensures warning and provenance notices survive native print media', () => {
    const css = readFileSync('src/styles/reception.css', 'utf-8')
    expect(css).toMatch(/@media print \{[\s\S]*\.payment-print-receipt \.notice \{\s*display: block !important;/)
    expect(css).toMatch(/border: 1px solid currentColor;/)
    expect(css).toMatch(/font-weight: 600;/)
  })
})
