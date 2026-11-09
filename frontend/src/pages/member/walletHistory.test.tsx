import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const rows = [
  { type: 'TOPUP', id: 2, reference: 'TU-000002', createdAt: '2026-11-08T11:00:00', amount: 50000, direction: 'NONE', status: 'PENDING',
    pool: 'ACTUAL', availableAfter: null, detail: 'Bank transfer top-up, note SPW2', paymentId: null, bankReference: null, settledAt: null },
  { type: 'WITHDRAWAL', id: 1, reference: 'WD-000001', createdAt: '2026-11-08T10:30:00', amount: 100000, direction: 'DEBIT', status: 'APPROVED',
    pool: 'ACTUAL', availableAfter: 300000, detail: 'To Vietcombank ****4567', paymentId: null, bankReference: null, settledAt: null },
  { type: 'WALLET_PAYMENT', id: 9, reference: 'PAY-1000', createdAt: '2026-11-08T10:00:00', amount: 450000, direction: 'DEBIT', status: 'PAID',
    pool: 'ACTUAL', availableAfter: 400000, detail: 'Payment PAY-1000 - REG-PKG-1000', paymentId: 52, bankReference: null, settledAt: '2026-11-08T10:00:00' },
  { type: 'TOPUP', id: 1, reference: 'TU-000001', createdAt: '2026-11-08T09:00:00', amount: 30000, direction: 'CREDIT', status: 'CONFIRMED',
    pool: 'SIMULATED', availableAfter: 30000, detail: 'Demo top-up (not real money)', paymentId: null, bankReference: null, settledAt: '2026-11-08T09:05:00' },
]

function backend(reconciled = true) {
  tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
  return installFakeFetch(call => {
    if (call.path === '/users/me') return json(200, MEMBER)
    return json(200, { page: { content: call.path.includes('type=TOPUP') ? rows.filter(r => r.type === 'TOPUP') : rows, page: 0, size: 10,
      totalElements: 4, totalPages: 1 }, availableBalance: 300000, reservedBalance: 100000, ledgerAvailable: 300000, ledgerReserved: 100000, reconciled })
  })
}

describe('Wallet activity history (MW-08)', () => {
  it('shows pending, held, paid and demo rows honestly and opens an own detail', async () => {
    backend()
    const user = userEvent.setup()
    renderApp('/member/wallet/history')
    expect(await screen.findByText('Pending - not credited')).toBeInTheDocument()
    expect(screen.getByText('Approved - not paid yet')).toBeInTheDocument()
    expect(screen.getByText('Demo')).toBeInTheDocument()
    expect(screen.getByText(/Balance matches the wallet ledger/)).toBeInTheDocument()
    const paid = screen.getByText('PAY-1000').closest('tr') as HTMLElement
    expect(within(paid).getByText('-450.000 VND')).toBeInTheDocument()
    await user.click(within(paid).getByRole('button', { name: 'View' }))
    expect(screen.getByRole('link', { name: 'Open payment and receipt' })).toHaveAttribute('href', '/member/payments/52')
    const pending = screen.getByText('TU-000002').closest('tr') as HTMLElement
    expect(within(pending).getByText('50.000 VND')).toBeInTheDocument()
    expect(within(pending).getAllByText('-').length).toBeGreaterThan(0)
  })

  it('sends filters to the server and warns when the ledger does not reconcile', async () => {
    const { calls } = backend(false)
    const user = userEvent.setup()
    renderApp('/member/wallet/history')
    expect(await screen.findByText(/does not match the ledger/)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Type'), 'TOPUP')
    await screen.findByText('TU-000001')
    expect(calls.some(c => c.path.startsWith('/member/wallet/history') && c.path.includes('type=TOPUP') && c.path.includes('page=0'))).toBe(true)
  })

  it('links wallet activity from My Payments', async () => {
    tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
    installFakeFetch(call => call.path === '/users/me' ? json(200, MEMBER)
      : json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
    renderApp('/member/payments')
    expect(await screen.findByRole('link', { name: 'Wallet activity' })).toHaveAttribute('href', '/member/wallet/history')
  })
})
