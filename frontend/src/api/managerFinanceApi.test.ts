import { afterEach, describe, expect, it, vi } from 'vitest'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { tokenStorage } from './tokenStorage'
import { managerFinanceApi } from './managerFinanceApi'

afterEach(() => {
  vi.unstubAllGlobals()
  tokenStorage.clear()
})

const overviewDto = {
  month: '2026-10',
  from: '2026-10-01',
  to: '2026-10-31',
  paidRevenue: '16110000',
  paidTransactions: 12,
  averagePayment: '1342500',
  pendingAmount: '900000',
  pendingTransactions: 2,
  refundedAmount: '150000',
  refundedTransactions: 1,
  previousMonth: '2026-09',
  previousPaidRevenue: '17000000',
  revenueChange: '-890000',
  revenueChangePercent: '-5.24',
}

const transactionDto = {
  id: 41,
  code: 'PAY-1041',
  time: '2026-10-17T09:00:00',
  paymentTime: '2026-10-17T09:00:00',
  createdAt: '2026-10-17T08:58:00',
  status: 'SUCCESS',
  method: 'BANK_TRANSFER',
  amount: '810000',
  referenceCode: 'BANK-REF-41',
  memberId: 2,
  memberCode: 'MB-0248',
  memberName: 'Taylor Nguyen',
  itemType: 'SPORT_PACKAGE',
  itemCode: 'PK-001',
  itemName: 'Football 30-day package',
  sportName: 'Football',
  invoiceId: 82,
  invoiceNumber: 'INV-1041',
}

const refundDto = {
  id: 7,
  refundCode: 'RF-1040',
  packageRegistrationId: 10,
  paymentId: 41,
  paymentCode: 'PAY-1040',
  registrationCode: 'REG-0010',
  memberId: 2,
  memberCode: 'MB-0248',
  memberFullName: 'Taylor Nguyen',
  amountRequested: '150000',
  amountApproved: null,
  reason: 'Single visit cancelled before deadline',
  status: 'PENDING',
  requestedById: 4,
  requestedByName: 'Taylor Nguyen',
  reviewedById: null,
  reviewedByName: null,
  reviewedAt: null,
  managerNote: null,
  createdAt: '2026-10-17T10:00:00',
  cancelledBookings: 1,
}

const revenueDto = {
  from: '2026-10-01',
  to: '2026-10-31',
  groupBy: 'METHOD',
  totalRevenue: '16110000',
  totalTransactions: 12,
  rows: [{ key: 'BANK_TRANSFER', label: 'Bank transfer', revenue: '16110000', transactions: 12, sharePercent: '100.00' }],
}

describe('FE-13 manager finance API - BE contract integration', () => {
  it('maps FinanceOverviewResponse fields without inventing overview totals', async () => {
    const { calls } = installFakeFetch(() => json(200, overviewDto))

    await expect(managerFinanceApi.overview('2026-10')).resolves.toEqual({
      month: '2026-10',
      from: '2026-10-01',
      to: '2026-10-31',
      paidRevenue: 16110000,
      paidTransactions: 12,
      averagePayment: 1342500,
      pendingAmount: 900000,
      pendingTransactions: 2,
      refundedAmount: 150000,
      refundedTransactions: 1,
      previousMonth: '2026-09',
      previousPaidRevenue: 17000000,
      revenueChange: -890000,
      revenueChangePercent: -5.24,
    })
    expect(calls[0].path).toBe('/manager/finance/overview?month=2026-10')
  })

  it('requests the current six-month trend using RevenueTrendPoint DTO fields', async () => {
    const { calls } = installFakeFetch(() => json(200, [
      { month: '2026-05', revenue: '9000000', transactions: 8 },
      { month: '2026-06', revenue: 10500000, transactions: 9 },
    ]))

    await expect(managerFinanceApi.trend()).resolves.toEqual([
      { month: '2026-05', revenue: 9000000, transactions: 8 },
      { month: '2026-06', revenue: 10500000, transactions: 9 },
    ])
    expect(calls[0].path).toBe('/manager/finance/trend?months=6')
  })

  it('translates the month and UI search into BE-22 from, to and search filters', async () => {
    const { calls } = installFakeFetch(() => json(200, {
      content: [transactionDto],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))

    const result = await managerFinanceApi.transactions({ month: '2024-02', query: ' Taylor ', status: 'SUCCESS', page: 1, size: 20 })
    expect(result).toMatchObject({
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
      content: [{
        id: 41,
        code: 'PAY-1041',
        referenceCode: 'BANK-REF-41',
        memberName: 'Taylor Nguyen',
        itemName: 'Football 30-day package',
        sportName: 'Football',
        amount: 810000,
        invoiceNumber: 'INV-1041',
      }],
    })
    expect(calls[0].path).toBe('/manager/finance/transactions?from=2024-02-01&to=2024-02-29&search=Taylor&status=SUCCESS&page=1&size=20')
  })

  it('uses the BE-21 pending refund and review contracts', async () => {
    const { calls } = installFakeFetch((call) => call.method === 'GET'
      ? json(200, [refundDto])
      : json(200, { ...refundDto, status: (call.body as { approved: boolean }).approved ? 'APPROVED' : 'REJECTED' }))

    await expect(managerFinanceApi.pendingRefunds()).resolves.toMatchObject([{ id: 7, refundCode: 'RF-1040', status: 'PENDING' }])
    await managerFinanceApi.reviewRefund(7, {
      approved: true,
      amountApproved: 125000,
      managerNote: 'Approved after checking the cancellation timestamp.',
    })
    expect(calls.map(({ method, path, body }) => ({ method, path, body }))).toEqual([
      { method: 'GET', path: '/refunds/pending', body: undefined },
      { method: 'PUT', path: '/refunds/7/review', body: { approved: true, amountApproved: 125000, managerNote: 'Approved after checking the cancellation timestamp.' } },
    ])
  })

  it('loads RevenueBreakdownResponse with the supported METHOD grouping and no unsupported sportId', async () => {
    const { calls } = installFakeFetch(() => json(200, revenueDto))

    await expect(managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'METHOD' })).resolves.toEqual({
      from: '2026-10-01',
      to: '2026-10-31',
      groupBy: 'METHOD',
      totalRevenue: 16110000,
      totalTransactions: 12,
      rows: [{ key: 'BANK_TRANSFER', label: 'Bank transfer', revenue: 16110000, transactions: 12, sharePercent: 100 }],
    })
    expect(calls[0].path).toBe('/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=METHOD')
  })

  it('downloads the backend CSV with the manager bearer token and server filename metadata', async () => {
    tokenStorage.startSession({ accessToken: 'finance-manager-token', refreshToken: 'finance-refresh-token' })
    const { calls, fetchMock } = installFakeFetch(() => new Response('Code,Amount\r\nPAY-1041,810000\r\n', {
      status: 200,
      headers: {
        'Content-Type': 'text/csv;charset=UTF-8',
        'Content-Disposition': 'attachment; filename="revenue_2026-10-01_2026-10-31.csv"',
      },
    }))

    const download = await managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31')

    expect(await download.blob.text()).toBe('Code,Amount\r\nPAY-1041,810000\r\n')
    expect(download.contentType).toBe('text/csv;charset=UTF-8')
    expect(download.contentDisposition).toBe('attachment; filename="revenue_2026-10-01_2026-10-31.csv"')
    expect(calls[0]).toMatchObject({
      method: 'GET',
      path: '/manager/finance/revenue/export?from=2026-10-01&to=2026-10-31',
      authorization: 'Bearer finance-manager-token',
    })
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Accept')).toBe('text/csv')
  })

  it('preserves backend errors and rejects former invented payload shapes', async () => {
    installFakeFetch(() => backendError(403, 'Manager role required'))
    await expect(managerFinanceApi.overview('2026-10')).rejects.toThrow('Manager role required')

    installFakeFetch(() => json(200, { paymentsCollected: 0, completedRefunds: 0, netCollected: 0 }))
    await expect(managerFinanceApi.overview('2026-10')).rejects.toThrow()

    installFakeFetch(() => json(200, { ...revenueDto, groupBy: 'PAYMENT_METHOD' }))
    await expect(managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'METHOD' })).rejects.toThrow()
  })
})
