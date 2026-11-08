import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

const wallet = { availableBalance: 500000, reservedBalance: 0, demoAvailableBalance: 0, demoReservedBalance: 0, currency: 'VND',
  updatedAt: '2026-11-08T10:00:00', recentActivity: [], activityCount: 1, notice: 'n' }
const account = { bankBin: '970436', bankName: 'Vietcombank', maskedAccountNumber: '****4567', accountHolder: 'NGUYEN TEST', updatedAt: '2026-11-08T10:00:00' }
const banks = { banks: [{ bin: '970436', code: 'VCB', name: 'Vietcombank', transferSupported: true }] }
const pending = { id: 3, code: 'WD-000003', memberId: 2, memberCode: 'MEM-0002', memberName: 'Taylor Nguyen', pool: 'ACTUAL', amount: 200000,
  status: 'PENDING', bankName: 'Vietcombank', accountNumber: '****4567', accountHolder: 'NGUYEN TEST', reviewNote: null, reviewedByName: null,
  reviewedAt: null, payoutReference: null, completedByName: null, completedAt: null, createdAt: '2026-11-08T10:05:00' }

function signedIn(role: string, handler: (call: RecordedCall) => Response | Promise<Response>) {
  tokenStorage.startSession({ accessToken: 'token', refreshToken: 'refresh' })
  return installFakeFetch(call => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : handler(call))
}

describe('Member withdrawal (MW-06)', () => {
  it('requests a withdrawal with the confirmed last 4 digits and shows only a masked account', async () => {
    let created = false
    const { calls } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet') return json(200, wallet)
      if (call.path === '/member/wallet/payout-account') return json(200, account)
      if (call.path === '/payments/vietqr-banks') return json(200, banks)
      if (call.path === '/member/wallet/withdrawals' && call.method === 'POST') { created = true; return json(201, pending) }
      if (call.path === '/member/wallet/withdrawals') return json(200, created ? [pending] : [])
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/member/wallet/withdraw')
    expect(await screen.findByText('****4567')).toBeInTheDocument()
    expect(screen.getByText(/never asks for your bank login, PIN or OTP/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Amount (VND)'), '200000')
    await user.type(screen.getByLabelText(/Confirm last 4 digits/), '4567')
    await user.click(screen.getByRole('button', { name: 'Request withdrawal' }))
    expect(await screen.findByText(/WD-000003 requested/)).toBeInTheDocument()
    expect(calls.find(c => c.method === 'POST')?.body).toEqual({ amount: 200000, pool: 'ACTUAL', confirmAccountLast4: '4567' })
    expect(await screen.findByText('Waiting for review')).toBeInTheDocument()
    expect(screen.queryByText('0011001234567')).not.toBeInTheDocument()
  })

  it('refuses more than the available balance before calling the server', async () => {
    const { calls } = signedIn('MEMBER', call => {
      if (call.path === '/member/wallet') return json(200, wallet)
      if (call.path === '/member/wallet/payout-account') return json(200, account)
      if (call.path === '/payments/vietqr-banks') return json(200, banks)
      return json(200, [])
    })
    const user = userEvent.setup()
    renderApp('/member/wallet/withdraw')
    await user.type(await screen.findByLabelText('Amount (VND)'), '600000')
    await user.type(screen.getByLabelText(/Confirm last 4 digits/), '4567')
    await user.click(screen.getByRole('button', { name: 'Request withdrawal' }))
    expect(await screen.findByText(/more than your available balance/)).toBeInTheDocument()
    expect(calls.some(c => c.method === 'POST')).toBe(false)
  })
})

describe('Reception withdrawal queue (MW-06)', () => {
  it('approval says it is not paid yet; recording the payout needs a reference', async () => {
    let state = 'PENDING'
    const { calls } = signedIn('RECEPTIONIST', call => {
      if (call.path.startsWith('/reception/wallet/withdrawals?')) return json(200, [{ ...pending, status: state }])
      if (call.path === '/reception/wallet/withdrawals/3/approve') { state = 'APPROVED'; return json(200, { ...pending, status: 'APPROVED' }) }
      if (call.path === '/reception/wallet/withdrawals/3/complete') { state = 'COMPLETED'; return json(200, { ...pending, status: 'COMPLETED', payoutReference: 'PO1' }) }
      if (call.path === '/reception/wallet/withdrawals/3') return json(200, { ...pending, status: state, accountNumber: '0011001234567' })
      return backendError(404, 'Not found')
    })
    const user = userEvent.setup()
    renderApp('/reception/wallet/withdrawals')
    const row = (await screen.findByText('WD-000003')).closest('tr') as HTMLElement
    await user.click(within(row).getByRole('button', { name: 'Process' }))
    expect(await screen.findByText('0011001234567')).toBeInTheDocument()
    expect(screen.getByText(/Approving does not transfer money/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Approve' }))
    expect(await screen.findByText('WD-000003 approved. It is not paid yet.')).toBeInTheDocument()
    await user.click(within((await screen.findByText('WD-000003')).closest('tr') as HTMLElement).getByRole('button', { name: 'Process' }))
    await user.type(await screen.findByLabelText(/Bank payout reference/), 'PO1')
    await user.click(screen.getByRole('button', { name: 'Record transfer as paid' }))
    expect(await screen.findByText('WD-000003 recorded as paid out.')).toBeInTheDocument()
    expect(calls.find(c => c.path.endsWith('/complete'))?.body).toEqual({ payoutReference: 'PO1', note: '' })
  })
})
