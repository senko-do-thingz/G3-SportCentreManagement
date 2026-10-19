import { afterEach, describe, expect, it, vi } from 'vitest'
import { Buffer } from 'node:buffer'
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
  previousPaidTransactions: 13,
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
  cancelledBookings: null,
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
      : json(200, { ...refundDto, status: (call.body as { approved: boolean }).approved ? 'APPROVED' : 'REJECTED', amountApproved: 125000 }))

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

describe('FE-13 Round 2 contract regressions', () => {
  it('forwards the supported transaction method filter', async () => {
    const { calls } = installFakeFetch(() => json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
    await managerFinanceApi.transactions({ month: '2026-10', method: 'CASH' })
    expect(calls[0].path).toBe('/manager/finance/transactions?from=2026-10-01&to=2026-10-31&method=CASH&page=0&size=20')
  })

  it.each([
    { ...refundDto, id: 0 },
    { ...refundDto, refundCode: '' },
    { ...refundDto, amountRequested: 'invalid' },
    { ...refundDto, memberFullName: { name: 'Invalid type' } },
    { ...refundDto, reason: [] },
    { ...refundDto, status: 'UNKNOWN' },
  ])('rejects malformed pending refund fields: %j', async (dto) => {
    installFakeFetch(() => json(200, [dto]))
    await expect(managerFinanceApi.pendingRefunds()).rejects.toThrow()
  })

  it.each([
    { ...refundDto, status: 'PENDING' },
    { ...refundDto, status: 'REJECTED' },
    { ...refundDto, id: 99, status: 'APPROVED', amountApproved: '125000' },
    { ...refundDto, status: 'APPROVED', amountApproved: null },
  ])('does not confirm a mismatched or incomplete review result: %j', async (dto) => {
    installFakeFetch(() => json(200, dto))
    await expect(managerFinanceApi.reviewRefund(7, {
      approved: true, amountApproved: 125000, managerNote: 'Verified payment.',
    })).rejects.toThrow()
  })

  it.each([
    ['application/json', '{"message":"Export failed"}'],
    ['text/html', '<html>Service unavailable</html>'],
    ['text/csv', '{"message":"Export failed"}'],
    ['text/csv', '<html>Service unavailable</html>'],
    ['text/csv', ''],
  ])('rejects nominal 200 export errors and empty files (%s, %s)', async (contentType, body) => {
    installFakeFetch(() => new Response(body, { headers: { 'Content-Type': contentType } }))
    await expect(managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31')).rejects.toThrow()
  })

  it('preserves BOM and large CSV bytes without rebuilding a grouped report', async () => {
    const bytes = new TextEncoder().encode('\ufeffPayment code,Member name,Amount\r\n' + 'PAY-1,Nguy\u1ec5n,1000\r\n'.repeat(50000))
    installFakeFetch(() => new Response(bytes, { headers: { 'Content-Type': 'text/csv;charset=UTF-8' } }))
    const result = await managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31')
    expect(Buffer.from(await result.blob.arrayBuffer()).equals(Buffer.from(bytes))).toBe(true)
  })

  it('accepts a header-only CSV for a genuinely empty export', async () => {
    const body = '\ufeffPayment code,Amount\r\n'
    installFakeFetch(() => new Response(body, { headers: { 'Content-Type': 'text/csv;charset=UTF-8' } }))
    const result = await managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31')
    expect(result.blob.size).toBe(new TextEncoder().encode(body).length)
  })

  it('drops CSV bytes when the session changes while reading the body', async () => {
    tokenStorage.startSession({ accessToken: 'original-session', refreshToken: 'original-refresh' })
    const response = new Response('Payment code,Amount\r\n', { headers: { 'Content-Type': 'text/csv' } })
    let release!: (blob: Blob) => void
    const read = vi.spyOn(response, 'blob').mockImplementation(() => new Promise((resolve) => { release = resolve }))
    installFakeFetch(() => response)
    const result = managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31')
    const assertion = expect(result).rejects.toMatchObject({ name: 'StaleSessionError' })
    await vi.waitFor(() => expect(read).toHaveBeenCalledOnce())
    tokenStorage.startSession({ accessToken: 'new-session', refreshToken: 'new-refresh' })
    release(new Blob(['old-account-csv']))
    await assertion
  })

  it.each([
    { ...revenueDto, totalRevenue: null },
    { ...revenueDto, totalTransactions: '12' },
    { ...revenueDto, rows: null },
    { ...revenueDto, rows: [{ ...revenueDto.rows[0], label: {} }] },
    { ...revenueDto, rows: [{ ...revenueDto.rows[0], sharePercent: 'invalid' }] },
  ])('rejects malformed revenue DTOs instead of rendering fabricated zeroes: %j', async (dto) => {
    installFakeFetch(() => json(200, dto))
    await expect(managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'METHOD' })).rejects.toThrow()
  })

  it('accepts genuinely empty revenue and trend lists', async () => {
    installFakeFetch((call) => json(200, call.path.includes('/trend') ? [] : { ...revenueDto, totalRevenue: '0.00', totalTransactions: 0, rows: [] }))
    await expect(managerFinanceApi.trend()).resolves.toEqual([])
    await expect(managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'METHOD' })).resolves.toMatchObject({ totalRevenue: 0, totalTransactions: 0, rows: [] })
  })

  it.each(['2026-01', '2026-12', '2025-02', '2024-02'])('uses calendar dates without timezone shifts for %s', async (month) => {
    const { calls } = installFakeFetch(() => json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
    await managerFinanceApi.transactions({ month })
    const lastDay = month.endsWith('02') ? (month.startsWith('2024') ? '29' : '28') : '31'
    expect(calls[0].path).toBe(`/manager/finance/transactions?from=${month}-01&to=${month}-${lastDay}&page=0&size=20`)
  })

  it('accepts zero overview metrics and nullable percentage without deriving net revenue', async () => {
    installFakeFetch(() => json(200, {
      ...overviewDto, paidRevenue: '0.00', paidTransactions: 0, averagePayment: 0,
      pendingAmount: 0, pendingTransactions: 0, refundedAmount: 0, refundedTransactions: 0,
      previousPaidRevenue: 0, revenueChange: 0, revenueChangePercent: null,
    }))
    await expect(managerFinanceApi.overview('2026-10')).resolves.toMatchObject({ paidRevenue: 0, revenueChangePercent: null })
  })

  it.each([undefined, null, 'bad', -1, true])('rejects an invalid required amount %s', async (paidRevenue) => {
    installFakeFetch(() => json(200, { ...overviewDto, paidRevenue }))
    await expect(managerFinanceApi.overview('2026-10')).rejects.toThrow()
  })

  it.each([400, 403, 404, 500])('preserves HTTP %i for all finance resources', async (status) => {
    installFakeFetch(() => backendError(status, 'Finance request failed'))
    for (const load of [
      () => managerFinanceApi.overview('2026-10'),
      () => managerFinanceApi.trend(),
      () => managerFinanceApi.transactions(),
      () => managerFinanceApi.revenue({ from: '2026-10-01', to: '2026-10-31', groupBy: 'METHOD' }),
      () => managerFinanceApi.exportRevenue('2026-10-01', '2026-10-31'),
    ]) await expect(load()).rejects.toMatchObject({ status })
  })
})
