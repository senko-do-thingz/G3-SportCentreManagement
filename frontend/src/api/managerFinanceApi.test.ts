import { afterEach, describe, expect, it, vi } from 'vitest'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { managerFinanceApi } from './managerFinanceApi'

afterEach(() => vi.unstubAllGlobals())

const refund = {
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
  createdAt: '2026-10-17T10:00:00',
}

describe('FE-13 manager finance API', () => {
  it('loads overview KPI values and collected service totals for the selected month', async () => {
    const { calls } = installFakeFetch(() => json(200, {
      paymentsCollected: '16110000',
      completedRefunds: 150000,
      netCollected: '15960000',
      collectedByService: [
        { service: 'Sport packages', collected: '12360000' },
        { serviceName: 'Membership cards', amount: 2700000 },
      ],
      needsAttention: { pendingPaymentsCount: 4, pendingRefundApprovalCount: 2, approvedNotReturnedCount: 0 },
    }))

    await expect(managerFinanceApi.overview('2026-10')).resolves.toEqual({
      paymentsCollected: 16110000,
      completedRefunds: 150000,
      netCollected: 15960000,
      collectedByService: [
        { service: 'Sport packages', collected: 12360000 },
        { service: 'Membership cards', collected: 2700000 },
      ],
      pendingPaymentsCount: 4,
      pendingRefundApprovalCount: 2,
      approvedNotReturnedCount: 0,
    })
    expect(calls).toMatchObject([{ method: 'GET', path: '/manager/finance/overview?month=2026-10' }])
  })

  it('loads the six-month trend in the context of the selected month', async () => {
    const points = [
      { month: '2026-05', revenue: '9000000' },
      { period: '2026-06', amount: 10500000 },
    ]
    const { calls } = installFakeFetch(() => json(200, points))

    await expect(managerFinanceApi.trend('2026-10')).resolves.toEqual([
      { month: '2026-05', revenue: 9000000 },
      { month: '2026-06', revenue: 10500000 },
    ])
    expect(calls[0].path).toBe('/manager/finance/trend?month=2026-10')
  })

  it('applies transaction search, status, month and pagination filters', async () => {
    const { calls } = installFakeFetch(() => json(200, {
      content: [{
        id: 41,
        paymentCode: 'PAY-1041',
        invoiceNumber: 'INV-1041',
        memberFullName: 'Taylor Nguyen',
        memberCode: 'MB-0248',
        itemDescription: 'Football / Coach-led / 30 days',
        itemType: 'SPORT_PACKAGE',
        amount: '810000',
        paymentMethod: 'BANK_TRANSFER',
        status: 'SUCCESS',
        paymentTime: '2026-10-17T09:00:00',
      }],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    }))

    const result = await managerFinanceApi.transactions({ month: '2026-10', query: 'Taylor', status: 'SUCCESS', page: 1, size: 20 })
    expect(result).toMatchObject({
      page: 1,
      size: 20,
      totalElements: 21,
      content: [{ id: 41, reference: 'PAY-1041', invoiceNumber: 'INV-1041', memberName: 'Taylor Nguyen', amount: 810000, status: 'SUCCESS' }],
    })
    expect(calls[0].path).toBe('/manager/finance/transactions?month=2026-10&query=Taylor&status=SUCCESS&page=1&size=20')
  })

  it('loads manager refund approvals and sends an explicit approve or reject decision', async () => {
    const { calls, fetchMock } = installFakeFetch((call) => call.method === 'GET'
      ? json(200, [refund])
      : json(200, { ...refund, status: call.body && (call.body as { approved: boolean }).approved ? 'APPROVED' : 'REJECTED' }))

    await expect(managerFinanceApi.pendingRefunds()).resolves.toMatchObject([{ id: 7, refundCode: 'RF-1040', status: 'PENDING' }])
    await expect(managerFinanceApi.reviewRefund(7, {
      approved: true,
      amountApproved: 125000,
      managerNote: 'Approved after checking the cancellation timestamp.',
    })).resolves.toMatchObject({ status: 'APPROVED' })
    await expect(managerFinanceApi.reviewRefund(7, {
      approved: false,
      amountApproved: null,
      managerNote: 'Evidence incomplete; verify payment reference.',
    })).resolves.toMatchObject({ status: 'REJECTED' })

    expect(calls.map(({ method, path, body }) => ({ method, path, body }))).toEqual([
      { method: 'GET', path: '/refunds/pending', body: undefined },
      { method: 'PUT', path: '/refunds/7/review', body: { approved: true, amountApproved: 125000, managerNote: 'Approved after checking the cancellation timestamp.' } },
      { method: 'PUT', path: '/refunds/7/review', body: { approved: false, amountApproved: null, managerNote: 'Evidence incomplete; verify payment reference.' } },
    ])
    expect(JSON.parse(fetchMock.mock.calls[1][1]?.body as string)).toEqual(calls[1].body)
  })

  it('loads date, grouping and sport filters for a revenue report', async () => {
    const { calls } = installFakeFetch(() => json(200, {
      from: '2026-10-01',
      to: '2026-10-31',
      totals: { collected: '16110000', refunded: 150000, netCollected: '15960000' },
      rows: [{ category: 'Football', collected: 7200000, refunded: 0, netCollected: 7200000 }],
    }))

    await expect(managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'SPORT', sportId: 3 })).resolves.toMatchObject({
      from: '2026-10-01',
      to: '2026-10-31',
      groupBy: 'SPORT',
      totals: { collected: 16110000, refunded: 150000, netCollected: 15960000 },
      rows: [{ category: 'Football', collected: 7200000, refunded: 0, netCollected: 7200000 }],
    })
    expect(calls[0].path).toBe('/manager/finance/revenue?from=2026-10-01&to=2026-10-31&groupBy=SPORT&sportId=3')
  })

  it('preserves backend errors and rejects incomplete finance payloads instead of fabricating values', async () => {
    installFakeFetch(() => backendError(403, 'Manager role required'))
    await expect(managerFinanceApi.overview('2026-10')).rejects.toThrow('Manager role required')

    installFakeFetch(() => json(200, { paymentsCollected: 0 }))
    await expect(managerFinanceApi.overview('2026-10')).rejects.toThrow()
  })
})
