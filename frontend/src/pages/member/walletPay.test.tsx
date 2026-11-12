import { screen } from '@testing-library/react'
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

describe('Pay a pending purchase with Sportify Wallet (retired)', () => {
  it('does not offer wallet payment option on payment summary', async () => {
    const pending = payment()
    backend(Number(pending.amount) + 1000, () => json(200, {}))
    renderApp(`/member/payments/${pending.id}`)
    expect(await screen.findByRole('region', { name: 'Order summary' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /bank transfer/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /from wallet/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/Demo funds cannot be used/i)).not.toBeInTheDocument()
  })
})
