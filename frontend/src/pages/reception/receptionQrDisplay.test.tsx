import { act, render, screen, waitFor } from '@testing-library/react'
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
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders loading spinner then displays pending projection data and non-transfer notice', async () => {
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
      screen.getByText(/Customer-facing simulated QR code presentation for front desk payments/),
    ).toBeInTheDocument()

    // Payment & settlement info
    expect(screen.getByText('PAY-101')).toBeInTheDocument()
    expect(screen.getByText(/Nguyen Van A.*MEM-030/)).toBeInTheDocument()
    expect(screen.getByText('Gold Membership (1 Month)')).toBeInTheDocument()
    expect(screen.getByText('Pending payment')).toBeInTheDocument()
    expect(screen.getByText('Auto-refreshing every 4s')).toBeInTheDocument()

    // Active session details
    expect(screen.getByText('Active Simulation Session')).toBeInTheDocument()
    expect(screen.getByText('00000000-0000-4000-8000-000000000001')).toBeInTheDocument()

    // Strict boundary: zero confirmation button or token input
    expect(screen.queryByRole('button', { name: /confirm/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /settle/i })).not.toBeInTheDocument()
  })

  it('allows receptionist to switch demo bank and copies reference to clipboard', async () => {
    const user = userEvent.setup()
    const write = clipboard()
    vi.mocked(demoQrApi.receptionDeskProjection).mockResolvedValue(pendingProjection)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Demo bank' })).toBeInTheDocument()
    })

    const select = screen.getByRole('combobox', { name: 'Demo bank' })
    await user.selectOptions(select, 'bidv')

    const copyBtn = screen.getByRole('button', { name: 'Copy demo reference' })
    await user.click(copyBtn)

    expect(write).toHaveBeenCalledWith('DEMO-101')
    expect(
      screen.getByText(/Demo reference copied\. Simulated payment - no real money is transferred\./),
    ).toBeInTheDocument()
  })

  it('allows receptionist to copy full demo details', async () => {
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

  it('automatically transitions to SUCCESS and shows receipt CTA when payment settles', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(demoQrApi.receptionDeskProjection)
        .mockResolvedValueOnce(pendingProjection)
        .mockResolvedValueOnce(settledProjection)

      renderComponent()

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

      expect(
        screen.getByText(/Simulated payment received\. Provenance: SIMULATED \(SPORTIFY_SIMULATOR\)/),
      ).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'View receipt' })).toHaveAttribute(
        'href',
        '/reception/payments/101/receipt',
      )
    } finally {
      vi.useRealTimers()
    }
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
