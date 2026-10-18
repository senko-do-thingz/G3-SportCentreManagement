import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderApp } from '../../test/renderApp'
import { backendError, installFakeFetch, json, MANAGER, MEMBER } from '../../test/fakeBackend'
import { tokenStorage } from '../../api/tokenStorage'

const overview = {
  paymentsCollected: 16110000,
  completedRefunds: 150000,
  netCollected: 15960000,
  collectedByService: [
    { service: 'Sport packages', collected: 12360000 },
    { service: 'Membership cards', collected: 2700000 },
    { service: 'Single visits', collected: 1050000 },
  ],
  needsAttention: { pendingPaymentsCount: 4, pendingRefundApprovalCount: 2, approvedNotReturnedCount: 0 },
}
const trend = [
  { month: '2026-05', revenue: 9000000 },
  { month: '2026-06', revenue: 10500000 },
  { month: '2026-07', revenue: 11100000 },
  { month: '2026-08', revenue: 12500000 },
  { month: '2026-09', revenue: 14700000 },
  { month: '2026-10', revenue: 16110000 },
]
const recent = {
  content: [{ id: 41, paymentCode: 'PAY-1041', invoiceNumber: 'INV-1041', memberFullName: 'Taylor Nguyen', itemDescription: 'Football / Coach-led / 30 days', amount: 810000, paymentMethod: 'BANK_TRANSFER', status: 'SUCCESS', paymentTime: '2026-10-17T09:00:00' }],
  page: 0,
  size: 5,
  totalElements: 1,
  totalPages: 1,
}

function financeApi(user = MANAGER) {
  tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
  return installFakeFetch((call) => {
    if (call.path === '/users/me') return json(200, user)
    if (call.path.startsWith('/manager/finance/overview')) {
      return call.path.includes('2026-09') ? json(200, { ...overview, paymentsCollected: 9100000, netCollected: 9050000 }) : json(200, overview)
    }
    if (call.path.startsWith('/manager/finance/trend')) return json(200, trend)
    if (call.path.startsWith('/manager/finance/transactions')) return json(200, recent)
    return backendError(404, `Unexpected request: ${call.method} ${call.path}`)
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('FE-13 financial overview', () => {
  it('renders exact API KPIs, service totals, attention counts, trend and recent transactions', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')

    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('150.000 VND')).toBeInTheDocument()
    expect(within(kpis).getByText('15.960.000 VND')).toBeInTheDocument()
    const serviceCard = screen.getByRole('heading', { name: 'Collected by service' }).closest('section')
    expect(within(serviceCard!).getByText('Sport packages')).toBeInTheDocument()
    expect(within(serviceCard!).getByText('12.360.000 VND')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '2 requests' })).toHaveAttribute('href', '/manager/transactions#refund-queue')
    expect(screen.getByRole('img', { name: 'Six-month collected revenue' })).toBeInTheDocument()
    expect(await screen.findByText('INV-1041')).toBeInTheDocument()
    expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-10')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/trend?month=2026-10')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?month=2026-10&page=0&size=5')).toBe(true)
  })

  it('updates overview, trend and recent transaction queries when the month changes', async () => {
    const { calls } = financeApi()
    renderApp('/manager/finance?month=2026-10')
    const kpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(kpis).getByText('16.110.000 VND')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Reporting period'), { target: { value: '2026-09' } })

    await waitFor(() => expect(calls.some((call) => call.path === '/manager/finance/overview?month=2026-09')).toBe(true))
    expect(calls.some((call) => call.path === '/manager/finance/trend?month=2026-09')).toBe(true)
    expect(calls.some((call) => call.path === '/manager/finance/transactions?month=2026-09&page=0&size=5')).toBe(true)
    const updatedKpis = await screen.findByRole('region', { name: 'Financial key performance indicators' })
    expect(within(updatedKpis).getByText('9.100.000 VND')).toBeInTheDocument()
  })

  it('keeps manager-only routing and surfaces the backend denial without showing finance data', async () => {
    financeApi(MEMBER)
    renderApp('/manager/finance?month=2026-10')

    expect(await screen.findByText(/do not have access|access denied|forbidden/i)).toBeInTheDocument()
    expect(screen.queryByText('Payments collected')).not.toBeInTheDocument()
  })
})
