import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn(async () => 'data:image/png;base64,QR') } }))

afterEach(() => vi.unstubAllGlobals())

const pending = {
  id: 7, code: 'TU-000007', memberId: 2, memberCode: 'MEM-0002', memberName: 'Taylor Nguyen', mode: 'ACTUAL', amount: 200000,
  status: 'PENDING', transferReference: 'SPW7', receivedAmount: null, bankReference: null, reviewNote: null, reviewedByName: null,
  reviewedAt: null, ledgerEntryId: null, createdAt: '2026-11-08T10:00:00',
  instructions: { payable: true, notice: 'Transfer instructions only. Your wallet is credited after reception checks the money arrived.',
    bankBin: '970436', bankName: 'Vietcombank', accountNumber: '0011001234567', accountName: 'SPORTIFY CENTER', amount: 200000,
    transferReference: 'SPW7', qrPayload: '000201...' },
}

function signedIn(role: string, handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}

describe('Member wallet top-up (MW-04)', () => {
  it('creates a PENDING request with one idempotency key and shows the QR and transfer note', async () => {
    let created = false
    const { calls, fetchMock } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet/topups' && call.method === 'POST') { created = true; return json(201, pending) }
      if (call.path === '/member/wallet/topups') return json(200, { simulatedTopUpsEnabled: false, minAmount: 10000, maxAmount: 50000000, topUps: created ? [pending] : [] })
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/wallet/topup')
    await user.type(await screen.findByLabelText('Amount (VND)'), '200000')
    expect(screen.queryByLabelText('Top-up type')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByAltText('VietQR code for top-up TU-000007')).toBeInTheDocument()
    expect(screen.getAllByText('SPW7').length).toBeGreaterThan(0)
    expect(screen.getByText('Showing this code does not credit your wallet. Reception confirms the transfer first.')).toBeInTheDocument()
    const post = calls.find(call => call.method === 'POST')
    expect(post?.body).toEqual({ amount: 200000, mode: 'ACTUAL' })
    const init = fetchMock.mock.calls.find(([, options]) => options?.method === 'POST')?.[1]
    expect(new Headers(init?.headers).get('Idempotency-Key')).toMatch(/.{8,}/)
  })

  it('refuses an amount below the minimum before calling the server', async () => {
    const { calls } = signedIn('MEMBER', () => json(200, { simulatedTopUpsEnabled: true, minAmount: 10000, maxAmount: 50000000, topUps: [] }))
    const user = userEvent.setup()
    renderApp('/member/wallet/topup')
    expect(await screen.findByLabelText('Top-up type')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Amount (VND)'), '5000')
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByText(/Enter a whole amount from/)).toBeInTheDocument()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })
})

describe('Reception wallet top-up queue (MW-04)', () => {
  it('confirms with the typed amount and bank reference and shows the outcome', async () => {
    let confirmed = false
    const { calls } = signedIn('RECEPTIONIST', call => {
      if (call.path === '/reception/wallet/topups/7/confirm') { confirmed = true; return json(200, { ...pending, status: 'CONFIRMED', instructions: null }) }
      if (call.path.startsWith('/reception/wallet/topups?')) return json(200, confirmed ? [] : [pending])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/reception/wallet/topups')
    const row = (await screen.findByText('TU-000007')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Review' }))
    expect(screen.getByLabelText(/Amount received/)).toHaveValue('')
    await user.type(screen.getByLabelText(/Amount received/), '200000')
    await user.type(screen.getByLabelText(/Bank transaction reference/), 'FT26110899')
    await user.click(screen.getByRole('button', { name: 'Confirm and credit wallet' }))
    expect(await screen.findByText(/TU-000007 confirmed/)).toBeInTheDocument()
    expect(calls.find(call => call.path.endsWith('/confirm'))?.body).toEqual({ receivedAmount: 200000, bankReference: 'FT26110899', note: '' })
  })

  it('shows a duplicate confirmation conflict without claiming success', async () => {
    signedIn('RECEPTIONIST', call => call.path.endsWith('/confirm')
      ? backendError(409, 'Top-up TU-000007 is already CONFIRMED') : json(200, [pending]))
    const user = userEvent.setup()
    renderApp('/reception/wallet/topups')
    const row = (await screen.findByText('TU-000007')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Review' }))
    await user.type(screen.getByLabelText(/Amount received/), '200000')
    await user.type(screen.getByLabelText(/Bank transaction reference/), 'FT26110899')
    await user.click(screen.getByRole('button', { name: 'Confirm and credit wallet' }))
    expect(await screen.findByText('Top-up TU-000007 is already CONFIRMED')).toBeInTheDocument()
    expect(screen.queryByText(/TU-000007 confirmed/)).not.toBeInTheDocument()
  })
})
