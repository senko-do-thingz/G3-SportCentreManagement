import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { payment } from '../../test/memberFixtures'
import { renderApp } from '../../test/renderApp'

function backend(options: { role?: string; status?: number; data?: ReturnType<typeof payment> } = {}) {
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, { ...MEMBER, role: options.role ?? 'MEMBER' })
    if (call.path === '/payments/52') return options.status ? backendError(options.status, 'Access to this payment is unavailable') : json(200, options.data ?? payment())
    return json(200, [])
  })
}

describe('QR-01 guarded demo route and payment integration', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', 'true')
    tokenStorage.startSession({ accessToken: 'qr-test-access', refreshToken: 'qr-test-refresh' })
  })
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

  it('keeps all four bank choices and copy actions display-only on the authorized route', async () => {
    const { calls } = backend({ data: payment({ method: 'MOMO' }) })
    const user = userEvent.setup()
    const write = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: write } })
    vi.stubGlobal('isSecureContext', true)
    renderApp('/member/payments/52/qr-demo')
    const select = await screen.findByRole('combobox', { name: 'Demo bank' })
    for (const bank of ['vietcombank', 'bidv', 'techcombank', 'mb']) {
      await user.selectOptions(select, bank)
      await user.click(screen.getByRole('button', { name: 'Copy demo details' }))
      expect(screen.getByRole('status')).toHaveTextContent('Demo details copied.')
    }
    expect(write).toHaveBeenCalledTimes(4)
    expect(calls.every((call) => call.method === 'GET')).toBe(true)
    expect(screen.getByText('810.000 VND')).toBeVisible()
  })

  it('opens the isolated demo without submitting a method or changing the real Continue action', async () => {
    const { calls } = backend({ data: payment({ method: 'MOMO' }) })
    const user = userEvent.setup()
    renderApp('/member/payments/52')
    const demo = await screen.findByRole('link', { name: 'QR Payment (Demo)' })
    expect(screen.getByRole('button', { name: 'Continue to payment' })).toBeEnabled()
    expect(screen.getByRole('radio', { name: 'MoMo' })).toBeDisabled()
    await user.click(demo)
    expect(await screen.findByRole('img', { name: /Non-payable demo QR for payment 52/ })).toBeVisible()
    expect(screen.getByText('810.000 VND')).toBeVisible()
    expect(screen.getByText('DEMO - NOT AVAILABLE')).toBeVisible()
    expect(screen.queryByText('0123456789')).not.toBeInTheDocument()
    expect(screen.queryByText('SPORTIFY CENTER')).not.toBeInTheDocument()
    expect(screen.getByText('DEMO ONLY - NO MONEY WILL BE TRANSFERRED')).toBeVisible()
    expect(document.querySelector('.payment-workspace-header .home-theme')).toBeInTheDocument()
    expect(document.querySelector('.payment-workspace-header select')).not.toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Back to Payment Summary' }))
    expect(await screen.findByRole('button', { name: 'Continue to payment' })).toBeEnabled()
    expect(calls.every((call) => call.method === 'GET')).toBe(true)
  })

  it('uses the authorized DTO amount rather than its price breakdown', async () => {
    backend({ data: payment({ amount: '731234', originalPrice: 900000, discount: 90000 }) })
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByText('731.234 VND')).toBeVisible()
    expect(screen.queryByText('810.000 VND')).not.toBeInTheDocument()
  })

  it.each([403, 404, 503])('preserves backend %s failure without rendering a QR or private fields', async (status) => {
    backend({ status })
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('heading', { name: 'We could not load the payment demo' })).toBeVisible()
    expect(screen.queryByRole('img', { name: /Non-payable/ })).not.toBeInTheDocument()
    expect(screen.queryByText('810.000 VND')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Payment Summary' })).toBeVisible()
  })

  it.each(['invalid', '0', '9007199254740992', '52%7CBANK%3Dreal'])('rejects malformed id %s before a payment read', async (id) => {
    const { calls } = backend()
    renderApp(`/member/payments/${id}/qr-demo`)
    expect(await screen.findByText('The demo payment link is not valid.')).toBeVisible()
    expect(calls.some((call) => call.path.startsWith('/payments/'))).toBe(false)
  })

  it.each([
    payment({ id: 53 }),
    payment({ amount: 'not-an-amount' }),
    payment({ amount: 0 }),
    payment({ amount: -1 }),
    payment({ amount: '0' }),
    payment({ amount: '-0.01' }),
  ])('does not expose mismatched or malformed DTOs', async (data) => {
    backend({ data })
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('heading', { name: 'Payment details unavailable' })).toBeVisible()
    expect(screen.queryByRole('img', { name: /Non-payable/ })).not.toBeInTheDocument()
  })

  it.each(['SUCCESS', 'FAILED', 'REFUNDED', 'UNKNOWN'])('does not offer or render the demo for status %s', async (status) => {
    backend({ data: payment({ status }) })
    const view = renderApp('/member/payments/52')
    await screen.findByRole('heading', { name: 'Order summary' })
    expect(screen.queryByRole('link', { name: 'QR Payment (Demo)' })).not.toBeInTheDocument()
    view.unmount()
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('heading', { name: 'Demo unavailable for this payment' })).toBeVisible()
    expect(screen.queryByRole('img', { name: /Non-payable/ })).not.toBeInTheDocument()
  })

  it('keeps anonymous users behind login and does not read payments', async () => {
    tokenStorage.clear()
    const { calls } = backend()
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(calls.some((call) => call.path.startsWith('/payments/'))).toBe(false)
  })

  it('keeps a receptionist outside the Member route', async () => {
    const { calls } = backend({ role: 'RECEPTIONIST' })
    renderApp('/member/payments/52/qr-demo')
    await waitFor(() => expect(screen.queryByRole('img', { name: /Non-payable/ })).not.toBeInTheDocument())
    await screen.findByRole('heading', { level: 1 })
    expect(calls.some((call) => call.path.startsWith('/payments/'))).toBe(false)
  })

  it.each([
    ['false', true, 'development'],
    ['true', false, 'production'],
  ])('disables entry and direct route for flag %s / DEV %s / mode %s', async (flag, dev, mode) => {
    vi.stubEnv('VITE_QR_PAYMENT_DEMO', flag); vi.stubEnv('DEV', dev); vi.stubEnv('MODE', mode)
    const { calls } = backend()
    const summary = renderApp('/member/payments/52')
    await screen.findByRole('heading', { name: 'Order summary' })
    expect(screen.queryByRole('link', { name: 'QR Payment (Demo)' })).not.toBeInTheDocument()
    summary.unmount()
    calls.length = 0
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
    expect(calls.some((call) => call.path.startsWith('/payments/'))).toBe(false)
  })

  it('supports only the explicit opted-in demo build when DEV is false', async () => {
    vi.stubEnv('DEV', false); vi.stubEnv('MODE', 'qr-demo')
    backend()
    renderApp('/member/payments/52/qr-demo')
    expect(await screen.findByRole('img', { name: /Non-payable/ })).toBeVisible()
  })
})
