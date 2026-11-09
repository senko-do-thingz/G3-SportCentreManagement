import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { invoice, payment, registration } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const wallet = (available: number) => ({ availableBalance: available, reservedBalance: 0, demoAvailableBalance: 500000,
  demoReservedBalance: 0, currency: 'VND', updatedAt: '2026-11-08T10:00:00', recentActivity: [], activityCount: 0, notice: 'n' })

function backend(available: number, onPay: () => Response) {
  tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
  const pending = payment()
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, MEMBER)
    if (call.path === '/packages/registrations/my') return json(200, [registration({ status: 'PENDING_PAYMENT', paymentId: pending.id })])
    if (call.path === '/membership-cards/my') return json(200, [])
    if (call.path === `/payments/${pending.id}`) return json(200, pending)
    if (call.path === '/member/wallet') return json(200, wallet(available))
    if (call.path === `/member/wallet/payments/${pending.id}`) return onPay()
    if (call.path === '/invoices/7') return json(200, invoice())
    return backendError(404, 'Resource not found')
  })
}

describe('Pay a pending purchase with Sportify Wallet (MW-05)', () => {
  it('pays from the wallet once and opens the receipt', async () => {
    const pending = payment()
    const { calls } = backend(Number(pending.amount) + 1000, () => json(200, { paymentId: pending.id, paymentCode: pending.code, status: 'SUCCESS',
      method: 'WALLET', amount: pending.amount, ledgerEntryId: 3, availableAfter: 1000, invoiceId: 7, invoiceNumber: 'INV-7', replay: false }))
    const user = userEvent.setup()
    renderApp(`/member/payments/${pending.id}`)
    const button = await screen.findByRole('button', { name: /from wallet/ })
    expect(screen.getByText(/Demo funds cannot be used/)).toBeInTheDocument()
    await user.click(button)
    expect((await screen.findAllByText(/Receipt/i)).length).toBeGreaterThan(0)
    expect(calls.filter((call) => call.path === `/member/wallet/payments/${pending.id}` && call.method === 'POST')).toHaveLength(1)
  })

  it('disables wallet payment when the real balance is too low, even with demo funds', async () => {
    const pending = payment()
    const { calls } = backend(Number(pending.amount) - 1, () => json(200, {}))
    renderApp(`/member/payments/${pending.id}`)
    const button = await screen.findByRole('button', { name: /from wallet/ })
    expect(button).toBeDisabled()
    expect(screen.getByText(/not enough for this payment. Nothing will be charged/)).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/member/wallet/payments'))).toBe(false)
  })

  it('shows the server refusal and does not leave the summary', async () => {
    const pending = payment()
    backend(Number(pending.amount), () => backendError(400, 'Not enough wallet balance for this operation'))
    const user = userEvent.setup()
    renderApp(`/member/payments/${pending.id}`)
    await user.click(await screen.findByRole('button', { name: /from wallet/ }))
    expect(await screen.findByText('Not enough wallet balance for this operation')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /from wallet/ })).toBeInTheDocument()
  })
})
