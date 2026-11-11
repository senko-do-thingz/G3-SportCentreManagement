import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
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
  it('allows only one same-tick create request', async () => {
    let resolveCreate!: (response: Response) => void
    const create = new Promise<Response>((resolve) => { resolveCreate = resolve })
    let submitted = false
    const { calls } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet/topups' && call.method === 'POST') { submitted = true; return create }
      if (call.path === '/member/wallet/topups') return json(200, { simulatedTopUpsEnabled: false, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: submitted ? [pending] : [] })
      return backendError(404, 'Not found')
    })
    renderApp('/member/wallet/topup')
    fireEvent.change(await screen.findByLabelText('Amount (VND)'), { target: { value: '200000' } })
    const form = screen.getByRole('form', { name: 'New top-up' })
    fireEvent.submit(form)
    fireEvent.submit(form)
    await waitFor(() => expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1))
    await act(async () => { resolveCreate(json(201, pending)) })
    expect(await screen.findByAltText('VietQR code for top-up TU-000007')).toBeInTheDocument()
  })

  it('retains the create idempotency key after an uncertain response', async () => {
    let attempts = 0
    const { fetchMock } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet/topups' && call.method === 'POST') {
        attempts += 1
        return attempts === 1 ? backendError(500, 'Response unavailable') : json(200, pending)
      }
      if (call.path === '/member/wallet/topups') return json(200, { simulatedTopUpsEnabled: false, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: attempts > 1 ? [pending] : [] })
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/wallet/topup')
    await user.type(await screen.findByLabelText('Amount (VND)'), '200000')
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByText(/outcome was not confirmed/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByAltText('VietQR code for top-up TU-000007')).toBeInTheDocument()
    const keys = fetchMock.mock.calls
      .filter(([, options]) => options?.method === 'POST')
      .map(([, options]) => new Headers(options?.headers).get('Idempotency-Key'))
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
  })

  it('creates a PENDING request with one idempotency key and shows the QR and transfer note', async () => {
    let created = false
    const { calls, fetchMock } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet/topups' && call.method === 'POST') { created = true; return json(201, pending) }
      if (call.path === '/member/wallet/topups') return json(200, { simulatedTopUpsEnabled: false, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: created ? [pending] : [] })
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/wallet/topup')
    await user.type(await screen.findByLabelText('Amount (VND)'), '200000')
    expect(screen.queryByLabelText('Top-up type')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByAltText('VietQR code for top-up TU-000007')).toBeInTheDocument()
    expect(screen.getAllByText('SPW7').length).toBeGreaterThan(0)
    expect(screen.getByText(/Your wallet is credited after reception checks the money arrived/)).toBeInTheDocument()
    const post = calls.find(call => call.method === 'POST')
    expect(post?.body).toEqual({ amount: 200000, mode: 'ACTUAL' })
    const init = fetchMock.mock.calls.find(([, options]) => options?.method === 'POST')?.[1]
    expect(new Headers(init?.headers).get('Idempotency-Key')).toMatch(/.{8,}/)
  })

  it('refuses an amount below the minimum before calling the server', async () => {
    const { calls } = signedIn('MEMBER', () => json(200, { simulatedTopUpsEnabled: true, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: [] }))
    const user = userEvent.setup()
    renderApp('/member/wallet/topup')
    expect(await screen.findByLabelText('Top-up type')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Amount (VND)'), '5000')
    await user.click(screen.getByRole('button', { name: 'Create top-up request' }))
    expect(await screen.findByText(/Enter a whole amount from/)).toBeInTheDocument()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })

  it('does not offer a bank top-up when no verified receiving account is configured', async () => {
    const { calls } = signedIn('MEMBER', () => json(200, { simulatedTopUpsEnabled: false, realTopUpsAvailable: false, minAmount: 10000, maxAmount: 50000000, topUps: [] }))
    renderApp('/member/wallet/topup')
    expect(await screen.findByText(/Bank top-ups are not available right now/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create top-up request' })).toBeDisabled()
    expect(calls.some(call => call.method === 'POST')).toBe(false)
  })

  it('allows only one same-tick cancellation request', async () => {
    let resolveCancel!: (response: Response) => void
    const cancellation = new Promise<Response>((resolve) => { resolveCancel = resolve })
    const { calls } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet/topups/7/cancel') return cancellation
      if (call.path === '/member/wallet/topups') return json(200, { simulatedTopUpsEnabled: false, realTopUpsAvailable: true, minAmount: 10000, maxAmount: 50000000, topUps: [pending] })
      return backendError(404, 'Not found')
    })
    renderApp('/member/wallet/topup')
    const cancel = await screen.findByRole('button', { name: 'Cancel' })
    fireEvent.click(cancel)
    fireEvent.click(cancel)
    await waitFor(() => expect(calls.filter((call) => call.path.endsWith('/cancel'))).toHaveLength(1))
    await act(async () => { resolveCancel(json(200, { ...pending, status: 'CANCELLED' })) })
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

  it('shows a duplicate confirmation by someone else as a conflict without claiming success', async () => {
    const other = { ...pending, status: 'CONFIRMED', receivedAmount: 200000, bankReference: 'FT-OTHER', reviewedByName: 'Lan', instructions: null }
    signedIn('RECEPTIONIST', call => call.path.endsWith('/confirm') ? backendError(409, 'Top-up TU-000007 is already CONFIRMED')
      : call.path === '/reception/wallet/topups/7' ? json(200, other) : json(200, [pending]))
    const user = userEvent.setup()
    renderApp('/reception/wallet/topups')
    const row = (await screen.findByText('TU-000007')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Review' }))
    await user.type(screen.getByLabelText(/Amount received/), '200000')
    await user.type(screen.getByLabelText(/Bank transaction reference/), 'FT26110899')
    await user.click(screen.getByRole('button', { name: 'Confirm and credit wallet' }))
    expect(await screen.findByText(/Top-up TU-000007 is already CONFIRMED Current status: CONFIRMED by Lan/)).toBeInTheDocument()
    expect(screen.queryByText(/TU-000007 confirmed/)).not.toBeInTheDocument()
  })

  it('re-reads the request after a lost confirmation response and shows the committed outcome once', async () => {
    const committed = { ...pending, status: 'CONFIRMED', receivedAmount: 200000, bankReference: 'FT26110899', reviewedByName: 'Lan', instructions: null }
    const { calls } = signedIn('RECEPTIONIST', call => call.path.endsWith('/confirm') ? backendError(502, 'Bad gateway')
      : call.path === '/reception/wallet/topups/7' ? json(200, committed) : json(200, [pending]))
    const user = userEvent.setup()
    renderApp('/reception/wallet/topups')
    const row = (await screen.findByText('TU-000007')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Review' }))
    await user.type(screen.getByLabelText(/Amount received/), '200000')
    await user.type(screen.getByLabelText(/Bank transaction reference/), 'FT26110899')
    await user.click(screen.getByRole('button', { name: 'Confirm and credit wallet' }))
    expect(await screen.findByText(/TU-000007 is confirmed by Lan. The wallet was credited once/)).toBeInTheDocument()
    expect(calls.filter(call => call.path.endsWith('/confirm'))).toHaveLength(1)
  })

  it('offers a late-payment review for a member-cancelled bank top-up and requires a note', async () => {
    const cancelled = { ...pending, status: 'CANCELLED', instructions: null }
    signedIn('RECEPTIONIST', call => json(200, call.path.startsWith('/reception/wallet/topups?') ? [cancelled] : cancelled))
    const user = userEvent.setup()
    renderApp('/reception/wallet/topups')
    const row = (await screen.findByText('TU-000007')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Review late payment' }))
    expect(screen.getByLabelText('Note (required for a late payment)', { exact: false })).toBeRequired()
    expect(screen.queryByRole('button', { name: 'Reject request' })).not.toBeInTheDocument()
  })
})
