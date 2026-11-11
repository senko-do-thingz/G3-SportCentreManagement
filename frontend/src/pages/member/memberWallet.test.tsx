import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const notice = 'Sportify Wallet is an internal balance at this center. Demo funds are not real money and cannot be withdrawn to a bank.'
const empty = { availableBalance: 0, reservedBalance: 0, demoAvailableBalance: 0, demoReservedBalance: 0, currency: 'VND',
  updatedAt: '2026-11-08T10:00:00', recentActivity: [], activityCount: 0, notice }

afterEach(() => vi.unstubAllGlobals())

function signedIn(role: string, handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}

describe('My Wallet overview (MW-03)', () => {
  it('shows a zero wallet with an empty activity state and the demo-funds notice', async () => {
    signedIn('MEMBER', call => call.path === '/member/wallet' ? json(200, empty) : backendError(404, 'Not found'))
    renderApp('/member/wallet')
    expect(await screen.findByText('No wallet activity yet')).toBeInTheDocument()
    expect(screen.getByText('Available balance')).toBeInTheDocument()
    expect(screen.getByText('Demo balance (not real money)')).toBeInTheDocument()
    expect(screen.getAllByText('0 VND')).toHaveLength(3)
    expect(screen.getByText(notice)).toBeInTheDocument()
  })

  it('shows backend balances and labels demo activity without summing rows in the browser', async () => {
    signedIn('MEMBER', () => json(200, { ...empty, availableBalance: 300000, demoAvailableBalance: 50000, activityCount: 7,
      recentActivity: [
        { id: 2, entryType: 'TOPUP_CREDIT', pool: 'SIMULATED', amount: 50000, direction: 'CREDIT', availableAfter: 50000, reservedAfter: 0,
          sourceType: 'WALLET_TOPUP', sourceId: 4, description: 'Demo top-up TU-0004', createdAt: '2026-11-08T09:00:00' },
        { id: 1, entryType: 'PURCHASE_DEBIT', pool: 'ACTUAL', amount: 200000, direction: 'DEBIT', availableAfter: 300000, reservedAfter: 0,
          sourceType: 'PAYMENT', sourceId: 9, description: 'Package payment', createdAt: '2026-11-08T08:00:00' },
      ] }))
    renderApp('/member/wallet')
    expect(await screen.findByText('Recent activity (7)')).toBeInTheDocument()
    expect(screen.getByText('300.000 VND', { selector: '.stat-card__value' })).toBeInTheDocument()
    expect(screen.getByText('Demo')).toBeInTheDocument()
    expect(screen.getByText('You have demo funds. They cannot pay for real services or be withdrawn to a bank.')).toBeInTheDocument()
    expect(screen.getByText('-200.000 VND')).toBeInTheDocument()
    expect(screen.getByText('+50.000 VND')).toBeInTheDocument()
  })

  it('shows a load error with retry instead of a fake zero balance', async () => {
    signedIn('MEMBER', () => backendError(503, 'Wallet service unavailable'))
    renderApp('/member/wallet')
    expect(await screen.findByText('Wallet service unavailable')).toBeInTheDocument()
    expect(screen.queryByText('Available balance')).not.toBeInTheDocument()
  })

  it('is listed in the member menu and not available to staff', async () => {
    signedIn('MEMBER', call => call.path === '/member/wallet' ? json(200, empty) : json(200, []))
    renderApp('/member')
    expect((await screen.findAllByRole('link', { name: /My wallet/ }))[0]).toHaveAttribute('href', '/member/wallet')
  })
})
