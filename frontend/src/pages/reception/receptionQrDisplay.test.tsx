import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { demoQrApi, type ReceptionDeskProjection } from '../../api/demoQrApi'
import { ReceptionQrDisplayPage } from './ReceptionQrDisplayPage'

vi.mock('../../api/demoQrApi', () => ({
  demoQrApi: {
    receptionDeskProjection: vi.fn(),
  },
}))

function clipboard(writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined)) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  return writeText
}

const pendingProjection: ReceptionDeskProjection = {
  paymentId: 101,
  paymentCode: 'PAY-101',
  amount: 810000,
  currency: 'VND',
  paymentStatus: 'PENDING',
  itemDescription: 'Gold Membership (1 Month)',
  memberFullName: 'Nguyen Van A',
  memberCode: 'MEM-030',
  memberId: 30,
  settlementType: null,
  settlementProvider: null,
  simulatedTransactionId: null,
  activeSession: {
    sessionId: '00000000-0000-4000-8000-000000000001',
    bankCode: 'BIDV_DEMO',
    bankLabel: 'BIDV (demo)',
    state: 'PENDING',
    createdAt: '2026-10-30T14:00:00Z',
    expiresAt: '2026-10-30T14:10:00Z',
    serverTime: '2026-10-30T14:02:00Z',
  },
}

const settledProjection: ReceptionDeskProjection = {
  paymentId: 101,
  paymentCode: 'PAY-101',
  amount: 810000,
  currency: 'VND',
  paymentStatus: 'SUCCESS',
  itemDescription: 'Gold Membership (1 Month)',
  memberFullName: 'Nguyen Van A',
  memberCode: 'MEM-030',
  memberId: 30,
  settlementType: 'SIMULATED',
  settlementProvider: 'SPORTIFY_SIMULATOR',
  simulatedTransactionId: 9001,
  activeSession: {
    sessionId: '00000000-0000-4000-8000-000000000001',
    bankCode: 'BIDV_DEMO',
    bankLabel: 'BIDV (demo)',
    state: 'SETTLED',
    createdAt: '2026-10-30T14:00:00Z',
    expiresAt: '2026-10-30T14:10:00Z',
    serverTime: '2026-10-30T14:03:00Z',
  },
}

function renderComponent(paymentId = '101') {
  return render(
    <MemoryRouter initialEntries={[`/reception/payments/${paymentId}/qr-display`]}>
      <Routes>
        <Route
          path="/reception/payments/:paymentId/qr-display"
          element={<ReceptionQrDisplayPage />}
        />
        <Route
          path="/reception/payments/:paymentId"
          element={<div>Payment Verification Page</div>}
        />
        <Route
          path="/reception/payments/:paymentId/receipt"
          element={<div>Receipt Page</div>}
        />
        <Route
          path="/reception/payments"
          element={<div>Payment Requests List</div>}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ReceptionQrDisplayPage', () => {
  beforeEach(() => {
    vi.stubGlobal('isSecureContext', true)
    vi.resetAllMocks()
  })

  afterEach(() => {
    cleanup()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('renders loading spinner then displays pending projection data with informational QR', async () => {
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValueOnce(pendingProjection)

    renderComponent()

    expect(screen.getByText('Loading the desk QR presentation')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Simulated QR Payment Display')).toBeInTheDocument()
    })

    // Non-transfer disclaimers and notices
    expect(
      screen.getByText('Simulated QR Payment - No real money is transferred.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Customer-facing informational QR presentation for front desk payments/),
    ).toBeInTheDocument()

    // Informational card title and steps (QR06-R1-02)
    expect(screen.getByText('Informational Desk QR (Not Payable)')).toBeInTheDocument()
    expect(screen.getByText(/Member Self-Confirmation Steps:/)).toBeInTheDocument()
    expect(screen.getByText(/Customer signs in at/)).toBeInTheDocument()
    expect(screen.getByText(/Customer opens simulated payment/)).toBeInTheDocument()

    // Payment & settlement info
    expect(screen.getByText('PAY-101')).toBeInTheDocument()
    expect(screen.getByText(/Nguyen Van A.*MEM-030/)).toBeInTheDocument()
    expect(screen.getByText('Gold Membership (1 Month)')).toBeInTheDocument()
    expect(screen.getByText('Pending payment')).toBeInTheDocument()
    expect(screen.getByText('Auto-refreshing every 4s')).toBeInTheDocument()

    // Authoritative session bank locked display (QR06-R1-03)
    expect(screen.getByText('Session bank (authoritative)')).toBeInTheDocument()
    expect(screen.getAllByText('BIDV (demo)').length).toBeGreaterThanOrEqual(1)
    expect(
      screen.getByText(/Bank selection is locked to the customer active session/),
    ).toBeInTheDocument()

    // Strict boundary: zero confirmation button or token input
    expect(screen.queryByRole('button', { name: /confirm/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /settle/i })).not.toBeInTheDocument()
  })

  it('allows receptionist to select preview bank when no active session exists (QR06-R1-03)', async () => {
    const user = userEvent.setup()
    const write = clipboard()
    const noSessionPending: ReceptionDeskProjection = {
      ...pendingProjection,
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(noSessionPending)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Demo bank' })).toBeInTheDocument()
    })

    // Shows informational preview notice
    expect(
      screen.getAllByText(/Informational preview only - Does not change the Member session or settlement bank\./).length,
    ).toBeGreaterThanOrEqual(1)

    const select = screen.getByRole('combobox', { name: 'Demo bank' })
    await user.selectOptions(select, 'techcombank')

    const copyBtn = screen.getByRole('button', { name: 'Copy demo reference' })
    await user.click(copyBtn)

    expect(write).toHaveBeenCalledWith('DEMO-101')
    expect(
      screen.getByText(/Demo reference copied\. Simulated payment - no real money is transferred\./),
    ).toBeInTheDocument()
  })

  it('allows receptionist to copy full demo details when pending', async () => {
    const user = userEvent.setup()
    const write = clipboard()
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(pendingProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Copy demo details' })).toBeInTheDocument()
    })

    const copyBtn = screen.getByRole('button', { name: 'Copy demo details' })
    await user.click(copyBtn)

    expect(write).toHaveBeenCalledWith(
      expect.stringContaining('Account: DEMO - NOT AVAILABLE'),
    )
    expect(
      screen.getByText(/Demo details copied\. Simulated payment - no real money is transferred\./),
    ).toBeInTheDocument()
  })

  it('automatically transitions to SUCCESS and renders inactive QR indication on settlement (QR06-R1-01, QR06-R1-04)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(demoQrApi.receptionDeskProjection)
        .mockResolvedValueOnce(pendingProjection)
        .mockResolvedValueOnce(settledProjection)

      const view = renderComponent()

      await waitFor(() => {
        expect(screen.getByText('Pending payment')).toBeInTheDocument()
      })

      // Advance by 4 seconds to trigger polling
      await act(async () => {
        await vi.advanceTimersByTimeAsync(4100)
      })

      await waitFor(() => {
        expect(screen.getByText('Paid - Simulated')).toBeInTheDocument()
      })

      // Banner verifies simulated provenance without claiming unverified entitlement activation
      expect(
        screen.getByText(/Simulated payment received\. Provenance: SIMULATED \(SPORTIFY_SIMULATOR\)/),
      ).toBeInTheDocument()
      expect(screen.queryByText(/Entitlement is active/)).not.toBeInTheDocument()

      // Terminal state renders Inactive QR indication and removes copy buttons (QR06-R1-04)
      expect(
        screen.getByText('QR Code Inactive - Payment is Paid - Simulated'),
      ).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Copy demo reference' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Copy demo details' })).not.toBeInTheDocument()

      // Shows receipt link
      expect(screen.getByRole('link', { name: 'View receipt' })).toHaveAttribute(
        'href',
        '/reception/payments/101/receipt',
      )

      view.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('renders Paid - Actual for actual settlement provenance (QR06-R1-01)', async () => {
    const actualProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
      simulatedTransactionId: null,
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(actualProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Paid - Actual')).toBeInTheDocument()
    })

    expect(
      screen.getByText(/Payment settled via actual payment provider \(DESK_CASH\)\./),
    ).toBeInTheDocument()
    expect(screen.queryByText('Paid - Simulated')).not.toBeInTheDocument()
    expect(
      screen.queryByText('Simulated QR Payment - No real money is transferred.'),
    ).not.toBeInTheDocument()
  })

  it('renders Paid - Legacy unverified for null provenance (QR06-R1-01)', async () => {
    const legacyProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'SUCCESS',
      settlementType: null,
      settlementProvider: null,
      simulatedTransactionId: null,
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(legacyProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Paid - Legacy unverified')).toBeInTheDocument()
    })

    expect(
      screen.getByText(/Payment recorded as SUCCESS without settlement provenance records \(Legacy unverified\)\./),
    ).toBeInTheDocument()
    expect(screen.queryByText('Paid - Simulated')).not.toBeInTheDocument()
  })

  it('renders Paid - Inconsistent provenance for mismatched simulated provider (QR06-R1-01)', async () => {
    const inconsistentProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'UNKNOWN_GATEWAY',
      simulatedTransactionId: 101,
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(inconsistentProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Paid - Inconsistent provenance')).toBeInTheDocument()
    })

    expect(
      screen.getByText(/Authoritative payment status is SUCCESS, but settlement provenance is inconsistent or unverified\./),
    ).toBeInTheDocument()
    expect(screen.queryByText('Paid - Simulated')).not.toBeInTheDocument()
  })

  it('renders inactive QR state for terminal FAILED payment (QR06-R1-04)', async () => {
    const failedProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'FAILED',
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(failedProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('QR Code Inactive - Payment is Failed')).toBeInTheDocument()
    })

    expect(screen.getAllByText('Failed').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByRole('button', { name: 'Copy demo reference' })).not.toBeInTheDocument()
  })

  it('renders inactive QR state for terminal CANCELED payment (QR06-R1-04)', async () => {
    const canceledProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'CANCELED',
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(canceledProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('QR Code Inactive - Payment is Canceled')).toBeInTheDocument()
    })

    expect(screen.getAllByText('Canceled').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByRole('button', { name: 'Copy demo reference' })).not.toBeInTheDocument()
  })

  it('renders inactive QR state for terminal REFUNDED payment (QR06-R1-04)', async () => {
    const refundedProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      paymentStatus: 'REFUNDED',
      activeSession: null,
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(refundedProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('QR Code Inactive - Payment is Refunded')).toBeInTheDocument()
    })

    expect(screen.getAllByText('Refunded').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByRole('button', { name: 'Copy demo reference' })).not.toBeInTheDocument()
  })

  it('prevents stale polling responses from reverting terminal status (QR06-R1-04)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(demoQrApi.receptionDeskProjection)
        .mockResolvedValueOnce(pendingProjection)
        .mockResolvedValueOnce(settledProjection)

      const view = renderComponent()

      await waitFor(() => {
        expect(screen.getByText('Pending payment')).toBeInTheDocument()
      })

      // Advance timer for first poll -> transitions to SUCCESS
      await act(async () => {
        await vi.advanceTimersByTimeAsync(4100)
      })

      await waitFor(() => {
        expect(screen.getByText('Paid - Simulated')).toBeInTheDocument()
      })

      // Even if polling were invoked with stale data, status remains terminal Paid - Simulated
      expect(screen.getByText('Paid - Simulated')).toBeInTheDocument()
      expect(screen.queryByText('Pending payment')).not.toBeInTheDocument()

      view.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('renders notice when member simulation session has expired (QR06-R1-03)', async () => {
    const expiredProjection: ReceptionDeskProjection = {
      ...pendingProjection,
      activeSession: {
        ...pendingProjection.activeSession!,
        state: 'EXPIRED',
      },
    }
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(expiredProjection)

    renderComponent()

    await waitFor(() => {
      expect(
        screen.getByText(/Active member simulation session has expired\. The customer must restart from their Member portal\./),
      ).toBeInTheDocument()
    })
  })

  it('displays error state when desk projection API fails and allows retry', async () => {
    const user = userEvent.setup()
    vi.mocked(demoQrApi.receptionDeskProjection)
      .mockRejectedValueOnce(new Error('Network connection dropped'))
      .mockResolvedValueOnce(pendingProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Could not load desk projection')).toBeInTheDocument()
    })

    const retryBtn = screen.getByRole('button', { name: 'Try again' })
    await user.click(retryBtn)

    await waitFor(() => {
      expect(screen.getByText('Simulated QR Payment Display')).toBeInTheDocument()
      expect(screen.getByText('PAY-101')).toBeInTheDocument()
    })
  })

  it('displays empty state for invalid or non-numeric paymentId', async () => {
    renderComponent('invalid-id')

    expect(screen.getByText('Payment not found')).toBeInTheDocument()
    expect(screen.getByText('The payment link is not valid.')).toBeInTheDocument()
  })

  it('provides navigation back to payment verification and payment requests', async () => {
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(pendingProjection)

    renderComponent()

    await waitFor(() => {
      expect(
        screen.getByRole('link', { name: 'Back to payment verification' }),
      ).toHaveAttribute('href', '/reception/payments/101')
      expect(screen.getByRole('link', { name: 'Payment requests' })).toHaveAttribute(
        'href',
        '/reception/payments',
      )
    })
  })
})
