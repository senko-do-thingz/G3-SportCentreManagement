import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { confirmationToken } from '../../components/qr/confirmationLink'
import type { DemoSession } from '../../api/demoQrApi'
import { MemoryRouter, Link } from 'react-router-dom'
import { render } from '@testing-library/react'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'

const token = 'a'.repeat(43)
const base = '/demo/qr/sessions'
export const snapshot: DemoSession = {
  id: '00000000-0000-4000-8000-000000000052', paymentId: 52, reference: 'DEMO-52', amount: '810000.00', currency: 'VND',
  bankCode: 'BIDV_DEMO', bankLabel: 'BIDV (demo)', createdAt: '2026-10-29T08:00:00Z', expiresAt: '2026-10-29T08:05:00Z',
  serverTime: '2026-10-29T08:00:00Z', state: 'PENDING', mode: 'NON_PAYABLE',
}
function authenticated() { tokenStorage.startSession({ accessToken: 'test-access', refreshToken: 'test-refresh' }) }

describe('QR-04 owner-authorized mobile confirmation', () => {
  beforeEach(() => { vi.stubEnv('VITE_QR_PAYMENT_DEMO', 'true'); authenticated() })
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

  it.each(['', '#token=', '#token=52', `#token=${token}&owner=2`, '#token=<script>', `#other=${token}`])('rejects missing or malformed fragments: %s', (hash) => {
    expect(confirmationToken(hash)).toBeNull()
  })

  it('keeps the approved branded shell when the private token is missing', async () => {
    const { calls } = installFakeFetch(() => json(200, MEMBER))
    renderApp('/demo/qr/confirm')
    expect(await screen.findByRole('heading', { name: 'Demo link unavailable' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Sportify Center home' }).closest('header')).toHaveClass('workspace-header')
    expect(screen.queryByRole('button', { name: 'Confirm Demo Payment' })).not.toBeInTheDocument()
    expect(calls.every((call) => call.path === '/users/me')).toBe(true)
  })

  it.each(['PENDING', 'CONFIRMED', 'CANCELED', 'EXPIRED'] as const)('shows server state %s and never auto-confirms', async (state) => {
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : json(200, { ...snapshot, state }))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByText('BIDV (demo)')).toBeVisible()
    expect(screen.getByText(state)).toBeVisible()
    expect(screen.getByText('DEMO ONLY - NO MONEY TRANSFER')).toBeVisible()
    const brand = screen.getByRole('link', { name: 'Sportify Center home' })
    expect(brand).toBeVisible()
    expect(brand.closest('header')).toHaveClass('home-header', 'workspace-header')
    expect(brand).toHaveTextContent('Sportify')
    expect(brand).not.toHaveTextContent('Sports center')
    expect(calls.some((call) => call.path === `${base}/confirm`)).toBe(false)
    expect(screen.queryByRole('button', { name: 'Confirm Demo Payment' }) !== null).toBe(state === 'PENDING')
    expect(document.querySelector('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer')
    expect(localStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('token')).toBeNull()
  })

  it('confirms only on explicit intent and guards a second tap while the request is pending', async () => {
    let complete!: (response: Response) => void
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path === `${base}/confirm`) return new Promise<Response>((resolve) => { complete = resolve })
      return json(200, snapshot)
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    const button = await screen.findByRole('button', { name: 'Confirm Demo Payment' })
    await user.dblClick(button)
    expect(calls.filter((call) => call.path === `${base}/confirm`)).toHaveLength(1)
    complete(json(200, { ...snapshot, state: 'CONFIRMED' }))
    expect(await screen.findByText('Demo confirmation completed. The real payment remains unchanged.')).toBeVisible()
    await waitFor(() => expect(document.activeElement).toHaveAttribute('aria-label', 'Demo session result'))
    expect(calls.every((call) => call.path === '/users/me' || call.path.startsWith(base))).toBe(true)
  })

  it('cancels explicitly and refreshes demo state without calling financial APIs', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      return json(200, { ...snapshot, state: call.path.endsWith('/cancel') ? 'CANCELED' : 'PENDING' })
    })
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Cancel Demo Session' }))
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
    expect(screen.queryByRole('button', { name: 'Confirm Demo Payment' })).not.toBeInTheDocument()
  })

  it('does not announce success after a confirm error and requires a fresh server read', async () => {
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER)
      : call.path.endsWith('/confirm') ? backendError(409, 'Expired') : json(200, snapshot))
    const user = userEvent.setup()
    renderApp(`/demo/qr/confirm#token=${token}`)
    await user.click(await screen.findByRole('button', { name: 'Confirm Demo Payment' }))
    expect(await screen.findByText(/Could not complete the demo action/)).toBeVisible()
    expect(screen.queryByText(/Demo confirmation completed/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm Demo Payment' })).not.toBeInTheDocument()
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
    await user.click(screen.getByRole('button', { name: 'Sign in to review demo' }))
    expect(await screen.findByText('DEMO-52')).toBeVisible()
    expect(calls.find((call) => call.path === `${base}/preview`)?.body).toEqual({ token })
    expect(calls.some((call) => call.path.endsWith('/confirm'))).toBe(false)
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
    expect(calls.some((call) => call.path.endsWith('/confirm'))).toBe(false)
  })

  it.each([['false', true, 'test'], ['true', false, 'production']] as const)('disabled flag/mode rejects route: %s %s %s', async (flag, dev, mode) => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', flag); vi.stubEnv('DEV', dev); vi.stubEnv('MODE', mode)
    const { calls } = installFakeFetch(() => json(200, MEMBER))
    renderApp(`/demo/qr/confirm#token=${token}`)
    expect(await screen.findByRole('heading', { name: /Page not found/ })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Sportify Center home' }).closest('header')).toHaveClass('home-header', 'workspace-header')
    expect(screen.queryByRole('button', { name: 'Confirm Demo Payment' })).not.toBeInTheDocument()
    expect(calls.every((call) => call.path === '/users/me')).toBe(true)
  })
})
