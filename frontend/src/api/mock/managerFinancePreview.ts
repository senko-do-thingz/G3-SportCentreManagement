import type { RequestOptions } from '../client'
import type { FinanceRevenueRow, FinanceTransaction, FinanceTrendPoint, ManagerFinanceOverview, ManagerRevenueReport } from '../managerFinanceApi'
import type { Refund } from '../receptionPaymentsApi'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

export const overviewData: ManagerFinanceOverview = {
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
  simulatedRevenue: 0,
  simulatedTransactions: 0,
  netRevenue: 15960000,
}

export const trendData: FinanceTrendPoint[] = [
  { month: '2026-05', revenue: 12500000, transactions: 10 },
  { month: '2026-06', revenue: 14200000, transactions: 11 },
  { month: '2026-07', revenue: 15800000, transactions: 12 },
  { month: '2026-08', revenue: 16500000, transactions: 13 },
  { month: '2026-09', revenue: 17000000, transactions: 13 },
  { month: '2026-10', revenue: 16110000, transactions: 12 },
]

export const transactionsData: FinanceTransaction[] = [
  {
    id: 41,
    code: 'PAY-1041',
    time: '2026-10-17T09:00:00',
    paymentTime: '2026-10-17T09:00:00',
    createdAt: '2026-10-17T08:58:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 810000,
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
    settlementType: 'REAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 42,
    code: 'PAY-1042',
    time: '2026-10-16T14:30:00',
    paymentTime: '2026-10-16T14:30:00',
    createdAt: '2026-10-16T14:28:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 500000,
    referenceCode: 'CASH-REC-42',
    memberId: 3,
    memberCode: 'MB-0249',
    memberName: 'Daniel Smith',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-002',
    itemName: 'Basketball single session',
    sportName: 'Basketball',
    invoiceId: 83,
    invoiceNumber: 'INV-1042',
    settlementType: 'REAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  {
    id: 43,
    code: 'PAY-1043',
    time: '2026-10-15T11:15:00',
    paymentTime: null,
    createdAt: '2026-10-15T11:10:00',
    status: 'PENDING',
    method: 'BANK_TRANSFER',
    amount: 900000,
    referenceCode: 'BANK-REF-43',
    memberId: 4,
    memberCode: 'MB-0250',
    memberName: 'Chris Nguyen',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-003',
    itemName: 'Swimming 30-day package',
    sportName: 'Swimming',
    invoiceId: 84,
    invoiceNumber: 'INV-1043',
    settlementType: 'REAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 44,
    code: 'PAY-1044',
    time: '2026-10-14T16:00:00',
    paymentTime: '2026-10-14T16:00:00',
    createdAt: '2026-10-14T15:55:00',
    status: 'REFUNDED',
    method: 'BANK_TRANSFER',
    amount: 150000,
    referenceCode: 'BANK-REF-44',
    memberId: 5,
    memberCode: 'MB-0251',
    memberName: 'Jamie Tran',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-004',
    itemName: 'Badminton single session',
    sportName: 'Badminton',
    invoiceId: 85,
    invoiceNumber: 'INV-1044',
    settlementType: 'REAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 45,
    code: 'PAY-1045',
    time: '2026-10-13T10:20:00',
    paymentTime: '2026-10-13T10:20:00',
    createdAt: '2026-10-13T10:15:00',
    status: 'SUCCESS',
    method: 'WALLET',
    amount: 600000,
    referenceCode: 'WALLET-TX-45',
    memberId: 6,
    memberCode: 'MB-0252',
    memberName: 'Linh Pham',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-VIP',
    itemName: 'VIP Tier 12-month',
    sportName: null,
    invoiceId: 86,
    invoiceNumber: 'INV-1045',
    settlementType: 'REAL',
    settlementProvider: 'WALLET',
    simulatedTransactionId: null,
  },
]

export const pendingRefundsData: Refund[] = [
  {
    id: 7,
    refundCode: 'RF-1040',
    packageRegistrationId: 10,
    paymentId: 41,
    paymentCode: 'PAY-1040',
    registrationCode: 'REG-0010',
    memberId: 2,
    memberCode: 'MB-0248',
    memberFullName: 'Taylor Nguyen',
    amountRequested: 150000,
    amountApproved: null,
    reason: 'Single visit cancelled before deadline',
    status: 'PENDING',
    requestedByName: 'Taylor Nguyen',
    reviewedByName: null,
    reviewedAt: null,
    managerNote: null,
    createdAt: '2026-10-17T10:00:00',
    cancelledBookings: null,
  },
]

const serviceRows: FinanceRevenueRow[] = [
  { key: 'SPORT_PACKAGE', label: 'Sport packages', revenue: 13500000, transactions: 10, sharePercent: 83.8 },
  { key: 'MEMBERSHIP_CARD', label: 'Membership cards', revenue: 2610000, transactions: 2, sharePercent: 16.2 },
]

export function financePreviewResponse(path: string, options: RequestOptions, empty: boolean): Response | undefined {
  const method = options.method ?? 'GET'
  const query = options.query ?? {}

  if (path === '/manager/finance/overview' && method === 'GET') {
    return json(200, empty ? { ...overviewData, paidRevenue: 0, paidTransactions: 0, netRevenue: 0 } : overviewData)
  }

  if (path === '/manager/finance/trend' && method === 'GET') {
    return json(200, empty ? [] : trendData)
  }

  if (path === '/manager/finance/transactions' && method === 'GET') {
    const page = Number(query.page ?? 0)
    const size = Number(query.size ?? 20)
    const status = query.status ? String(query.status) : null
    const search = query.search ? String(query.search).toLowerCase() : null
    let filtered = empty ? [] : transactionsData
    if (status) {
      filtered = filtered.filter((t) => t.status === status)
    }
    if (search) {
      filtered = filtered.filter((t) =>
        `${t.code} ${t.memberName} ${t.itemName} ${t.invoiceNumber}`.toLowerCase().includes(search)
      )
    }
    return json(200, {
      content: filtered.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
    })
  }

  if (path === '/manager/finance/revenue' && method === 'GET') {
    const groupBy = (query.groupBy as 'ITEM_TYPE' | 'METHOD' | 'SPORT') ?? 'ITEM_TYPE'
    const report: ManagerRevenueReport = {
      from: String(query.from ?? '2026-10-01'),
      to: String(query.to ?? '2026-10-31'),
      groupBy,
      totalRevenue: empty ? 0 : 16110000,
      totalTransactions: empty ? 0 : 12,
      rows: empty ? [] : serviceRows,
    }
    return json(200, report)
  }

  if (path === '/manager/reports/operations' && method === 'GET') {
    return json(200, {
      from: String(query.from ?? '2026-10-01'),
      to: String(query.to ?? '2026-10-31'),
      timeZone: 'Asia/Ho_Chi_Minh',
      registeredMembers: empty ? 0 : 24,
      currentlyEnabledMembersRegistered: empty ? 0 : 22,
      classBookings: {
        total: empty ? 0 : 48,
        confirmed: empty ? 0 : 42,
        cancelled: empty ? 0 : 6,
        upcoming: empty ? 0 : 14,
        completed: empty ? 0 : 28,
      },
    })
  }

  if (path === '/refunds/pending' && method === 'GET') {
    return json(200, empty ? [] : pendingRefundsData)
  }

  const reviewMatch = /^\/refunds\/(\d+)\/review$/.exec(path)
  if (reviewMatch && method === 'PUT') {
    const refundId = Number(reviewMatch[1])
    const refund = pendingRefundsData.find((r) => r.id === refundId)
    if (!refund) {
      return json(404, { status: 404, message: 'Refund request not found.' })
    }
    const body = options.body as { approved: boolean; amountApproved: number | null; managerNote: string }
    refund.status = body.approved ? 'APPROVED' : 'REJECTED'
    refund.amountApproved = body.amountApproved
    refund.managerNote = body.managerNote
    refund.reviewedByName = 'Morgan Lee'
    refund.reviewedAt = new Date().toISOString()
    return json(200, refund)
  }

  return undefined
}
