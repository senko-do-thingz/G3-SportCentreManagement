import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch as installBaseFetch, json, MEMBER, type Handler } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { confirmationToken } from '../../components/qr/confirmationLink'
import type { DemoSession } from '../../api/demoQrApi'
import { MemoryRouter, Link } from 'react-router-dom'
import { render } from '@testing-library/react'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'

const token = 'a'.repeat(43)
const base = '/payments/simulated-qr/sessions'
const capabilityPath = '/payments/simulated-qr/capability'
function installFakeFetch(handler: Handler) {
  return installBaseFetch((call) => call.path === capabilityPath ? json(200, { enabled: true, settlementEnabled: true, contractVersion: 1 }) : handler(call))
}
export const snapshot: DemoSession = {
  id: '00000000-0000-4000-8000-000000000052', paymentId: 52, reference: 'DEMO-52', amount: '810000.00', currency: 'VND',
  bankCode: 'BIDV_DEMO', bankLabel: 'BIDV (demo)', createdAt: '2026-10-29T08:00:00Z', expiresAt: '2026-10-29T08:05:00Z',
  serverTime: '2026-10-29T08:00:00Z', state: 'PENDING', mode: 'NON_PAYABLE',
}
function authenticated() { tokenStorage.startSession({ accessToken: 'test-access', refreshToken: 'test-refresh' }) }

describe('QR-04 owner-authorized mobile confirmation', () => {
  beforeEach(() => { vi.stubEnv('VITE_QR_PAYMENT_DEMO', 'true'); authenticated() })
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

  it('does not preview or confirm when backend capability is disabled', async () => {
    const { calls } = installBaseFetch((call) => call.path === '/users/me' ? json(200, MEMBER)
      : json(200, { enabled: false, settlementEnabled: false, contractVersion: 1 }))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByRole('alert')).toHaveTextContent('simulation may be disabled')
    expect(calls.some((call) => call.path.startsWith(base))).toBe(false)
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()
  })

  it.each(['', '#token=', '#token=52', `#token=${token}&owner=2`, '#token=<script>', `#other=${token}`])('rejects missing or malformed fragments: %s', (hash) => {
    expect(confirmationToken(hash)).toBeNull()
  })

  it('keeps the approved branded shell when the private token is missing', async () => {
    const { calls } = installFakeFetch(() => json(200, MEMBER))
    renderApp('/demo/qr/confirm')
    expect(await screen.findByRole('heading', { name: 'Demo link unavailable' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Sportify Sports center home' }).closest('header')).toHaveClass('workspace-header')
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()
    expect(calls.every((call) => call.path === '/users/me')).toBe(true)
  })

  it.each(['PENDING', 'AUTHORIZED_PENDING_SETTLEMENT', 'SETTLED', 'CANCELED', 'EXPIRED'] as const)('shows server state %s and never auto-confirms', async (state) => {
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : json(200, { ...snapshot, state }))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByText('BIDV (demo)')).toBeVisible()
    expect(screen.getByText(state)).toBeVisible()
    expect(screen.getByText('Simulated QR Payment - No real money is transferred.')).toBeVisible()
    const brand = screen.getByRole('link', { name: 'Sportify Sports center home' })
    expect(brand).toBeVisible()
    expect(brand.closest('header')).toHaveClass('home-header', 'workspace-header')
    expect(brand).toHaveTextContent('Sportify')
    expect(brand).not.toHaveTextContent('Sports center')
    expect(calls.some((call) => call.path === `${base}/confirm` || call.path === `${base}/confirm-intent`)).toBe(false)
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' }) !== null).toBe(state === 'PENDING')
    expect(screen.queryByText(/Final simulated-payment consent:/) !== null).toBe(state === 'PENDING')
    expect(document.querySelector('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer')
    expect(localStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('token')).toBeNull()
  })

  it('confirms only on explicit consent via /confirm, settles in simulation and guards a second tap while the request is pending', async () => {
    let complete!: (response: Response) => void
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === `${base}/confirm`) return new Promise<Response>((resolve) => { complete = resolve })
      return json(200, snapshot)
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    const button = await screen.findByRole('button', { name: 'Confirm simulated payment' })
    expect(screen.getByText(/Final simulated-payment consent: Confirming finalizes this payment as Paid - Simulated \(SUCCESS\)/)).toBeVisible()
    await user.dblClick(button)
    expect(calls.filter((call) => call.path === `${base}/confirm`)).toHaveLength(1)
    expect(calls.some((call) => call.path === `${base}/confirm-intent`)).toBe(false)
    complete(json(200, {
      ...snapshot,
      state: 'SETTLED',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      transactionCode: 'SIM-QR-52-00000052',
      invoiceId: 7,
      invoiceNumber: 'INV-0007',
    }))
    expect(await screen.findByText(/Paid - Simulated\. Your payment was settled in simulation only/)).toBeVisible()
    expect(screen.getByText('SIM-QR-52-00000052')).toBeVisible()
    expect(screen.getByText('SIMULATED (SPORTIFY_SIMULATOR)')).toBeVisible()
    expect(screen.getByRole('link', { name: 'View simulated receipt' })).toHaveAttribute('href', '/member/payments/receipts/7')
    await waitFor(() => expect(document.activeElement).toHaveAttribute('aria-label', 'Demo session result'))
    expect(calls.every((call) => call.path === '/users/me' || call.path === capabilityPath || call.path.startsWith(base))).toBe(true)
  })

  it('cancels explicitly and refreshes demo state without calling financial APIs', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      return json(200, { ...snapshot, state: call.path.endsWith('/cancel') ? 'CANCELED' : 'PENDING' })
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Cancel Simulation Session' }))
    expect(await screen.findByText('CANCELED')).toBeVisible()
    expect(calls.some((call) => call.path.endsWith(`/${snapshot.id}/cancel`))).toBe(true)
  })

  it.each([404, 409, 500, 0])('fails closed on preview error %s without private summary', async (status) => {
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (status === 0) throw new TypeError('Offline')
      return backendError(status, 'Unavailable')
    })
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByText(/Demo link unavailable. It may be/)).toBeVisible()
    expect(screen.queryByText('810.000 VND')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()
  })

  it('does not announce success after a confirm error and requires a fresh server read', async () => {
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER)
      : call.path.endsWith('/confirm') ? backendError(409, 'Expired') : json(200, snapshot))
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Confirm simulated payment' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/Could not complete the demo action\. The simulation outcome could not be confirmed; refresh Simulation Status to verify the authoritative server state before retrying\./)
    expect(alert).not.toHaveTextContent(/No simulated settlement was recorded/)
    expect(screen.queryByText(/Paid - Simulated\. Your payment was settled in simulation only/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()
  })

  it('reconciles authoritative SETTLED server state automatically when confirm HTTP response is lost after SQL commit', async () => {
    let committedOnServer = false
    const settledSnapshot: DemoSession = {
      ...snapshot,
      state: 'SETTLED',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      transactionCode: 'SIM-QR-52-LOST-RESP',
      invoiceId: 19,
      invoiceNumber: 'INV-0019',
    }
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === `${base}/confirm`) {
        committedOnServer = true
        throw new TypeError('Network connection lost after server commit')
      }
      if (call.path === `${base}/preview`) {
        return json(200, committedOnServer ? settledSnapshot : snapshot)
      }
      return json(200, snapshot)
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Confirm simulated payment' }))
    expect(await screen.findByText(/Paid - Simulated\. Your payment was settled in simulation only/)).toBeVisible()
    expect(screen.getByText('SIM-QR-52-LOST-RESP')).toBeVisible()
    expect(screen.getByRole('link', { name: 'View simulated receipt' })).toHaveAttribute('href', '/member/payments/receipts/19')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(calls.filter((call) => call.path === `${base}/preview`)).toHaveLength(2)
  })

  it('reports unknown outcome without claiming no settlement was recorded when both confirm and reconciliation fail, then reconciles on Refresh', async () => {
    let committedOnServer = false
    let networkOnline = true
    const settledSnapshot: DemoSession = {
      ...snapshot,
      state: 'SETTLED',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      transactionCode: 'SIM-QR-52-RECOVERED',
      invoiceId: 21,
      invoiceNumber: 'INV-0021',
    }
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === `${base}/confirm`) {
        committedOnServer = true
        networkOnline = false
        throw new TypeError('Network dropped after SQL commit')
      }
      if (call.path === `${base}/preview`) {
        if (!networkOnline) throw new TypeError('Still offline')
        return json(200, committedOnServer ? settledSnapshot : snapshot)
      }
      return json(200, snapshot)
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Confirm simulated payment' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The simulation outcome could not be confirmed; refresh Simulation Status to verify the authoritative server state before retrying.')
    expect(alert).not.toHaveTextContent('No simulated settlement was recorded')
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()

    networkOnline = true
    await user.click(screen.getByRole('button', { name: 'Refresh Simulation Status' }))
    expect(await screen.findByText(/Paid - Simulated\. Your payment was settled in simulation only/)).toBeVisible()
    expect(screen.getByText('SIM-QR-52-RECOVERED')).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('retains the fragment only in memory through inline owner sign-in', async () => {
    tokenStorage.clear()
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, { accessToken: 'signed-access', refreshToken: 'signed-refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      return json(200, snapshot)
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByRole('heading', { name: 'Sign in as the payment owner' })).toBeVisible()
    expect(calls).toHaveLength(0)
    await user.type(screen.getByLabelText('Email', { exact: false }), 'member@example.test')
    await user.type(screen.getByLabelText(/^Password/), 'ExamplePass123!')
    await user.click(screen.getByRole('button', { name: 'Sign in to review simulation' }))
    expect(await screen.findByText('DEMO-52')).toBeVisible()
    expect(calls.find((call) => call.path === `${base}/preview`)?.body).toEqual({ token })
    expect(calls.some((call) => call.path.endsWith('/confirm') || call.path.endsWith('/confirm-intent'))).toBe(false)
  })

  it.each(['MANAGER', 'RECEPTIONIST', 'COACH'])('never queries session details for role %s', async (role) => {
    const { calls } = installFakeFetch(() => json(200, { ...MEMBER, role }))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByText(/Only the member who owns this demo session/)).toBeVisible()
    expect(calls).toHaveLength(1)
  })

  it('consumes a new fragment in the same tab and removes the previous payment summary', async () => {
    const nextToken = 'b'.repeat(43)
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      const next = (call.body as { token?: string })?.token === nextToken
      return json(200, next ? { ...snapshot, paymentId: 53, reference: 'DEMO-53', amount: 510000 } : snapshot)
    })
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={[`/demo/qr/confirm#token=${token}`]}><ToastProvider><AuthProvider>
      <Link to={`/demo/qr/confirm#token=${nextToken}`}>Load another demo link</Link>
      <AppRoutes />
    </AuthProvider></ToastProvider></MemoryRouter>)
    expect(await screen.findByText('DEMO-52')).toBeVisible()
    await user.click(screen.getByRole('link', { name: 'Load another demo link' }))
    expect(await screen.findByText('DEMO-53')).toBeVisible()
    expect(screen.queryByText('DEMO-52')).not.toBeInTheDocument()
    expect(calls.filter((call) => call.path.endsWith('/preview'))).toHaveLength(2)
    expect(calls.some((call) => call.path.endsWith('/confirm') || call.path.endsWith('/confirm-intent'))).toBe(false)
  })

  it.each([['false', true, 'test'], ['false', false, 'production']] as const)('disabled flag rejects route: %s %s %s', async (flag, dev, mode) => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', flag); vi.stubEnv('DEV', dev); vi.stubEnv('MODE', mode)
    const { calls } = installFakeFetch(() => json(200, MEMBER))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByRole('heading', { name: /Page not found/ })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Sportify Sports center home' }).closest('header')).toHaveClass('home-header', 'workspace-header')
    expect(screen.queryByRole('button', { name: 'Confirm simulated payment' })).not.toBeInTheDocument()
    expect(calls.every((call) => call.path === '/users/me')).toBe(true)
  })

  it('supports official simulation rollout in production mode when backend capability is enabled', async () => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', ''); vi.stubEnv('DEV', false); vi.stubEnv('MODE', 'production')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : json(200, snapshot))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByRole('button', { name: 'Confirm simulated payment' })).toBeVisible()
  })
})
