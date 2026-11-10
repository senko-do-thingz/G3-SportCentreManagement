import type { RequestOptions } from '../client'
import type { FinanceRevenueRow, FinanceTransaction, FinanceTrendPoint, ManagerFinanceOverview, ManagerRevenueReport, RevenueGroup } from '../managerFinanceApi'
import type { Refund } from '../receptionPaymentsApi'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

export const transactionsData: FinanceTransaction[] = [
  // 2026-11 transactions (dated 2026-11-02 through 2026-11-08)
  {
    id: 101,
    code: 'PAY-1101',
    time: '2026-11-08T09:00:00',
    paymentTime: '2026-11-08T09:00:00',
    createdAt: '2026-11-08T08:58:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 810000,
    referenceCode: 'BANK-REF-1101',
    memberId: 2,
    memberCode: 'MB-0248',
    memberName: 'Taylor Nguyen',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-001',
    itemName: 'Football 30-day package',
    sportName: 'Football',
    invoiceId: 201,
    invoiceNumber: 'INV-1101',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 102,
    code: 'PAY-1102',
    time: '2026-11-08T15:10:00',
    paymentTime: null,
    createdAt: '2026-11-08T15:10:00',
    status: 'PENDING',
    method: 'BANK_TRANSFER',
    amount: 450000,
    referenceCode: 'BANK-REF-1102',
    memberId: 7,
    memberCode: 'MB-0253',
    memberName: 'Sample Member 1',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-020',
    itemName: 'Badminton 30-day package',
    sportName: 'Badminton',
    invoiceId: 202,
    invoiceNumber: 'INV-1102',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 103,
    code: 'PAY-1103',
    time: '2026-11-07T14:30:00',
    paymentTime: '2026-11-07T14:30:00',
    createdAt: '2026-11-07T14:28:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 900000,
    referenceCode: 'CASH-REC-1103',
    memberId: 8,
    memberCode: 'MB-0254',
    memberName: 'Sample Member 2',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-008',
    itemName: 'Basketball 30-day package',
    sportName: 'Basketball',
    invoiceId: 203,
    invoiceNumber: 'INV-1103',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  {
    id: 104,
    code: 'PAY-1104',
    time: '2026-11-05T10:20:00',
    paymentTime: '2026-11-05T10:20:00',
    createdAt: '2026-11-05T10:15:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 600000,
    referenceCode: 'BANK-REF-1104',
    memberId: 6,
    memberCode: 'MB-0252',
    memberName: 'Linh Pham',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-VIP',
    itemName: 'VIP Tier 12-month',
    sportName: null,
    invoiceId: 204,
    invoiceNumber: 'INV-1104',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 105,
    code: 'PAY-1105',
    time: '2026-11-04T13:00:00',
    paymentTime: '2026-11-04T13:00:00',
    createdAt: '2026-11-04T12:55:00',
    status: 'REFUNDED',
    method: 'BANK_TRANSFER',
    amount: 120000,
    referenceCode: 'BANK-REF-1105',
    memberId: 9,
    memberCode: 'MB-0255',
    memberName: 'Sample Member 3',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-031',
    itemName: 'Tennis single session',
    sportName: 'Tennis',
    invoiceId: 205,
    invoiceNumber: 'INV-1105',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 106,
    code: 'PAY-1106',
    time: '2026-11-03T16:15:00',
    paymentTime: '2026-11-03T16:15:00',
    createdAt: '2026-11-03T16:10:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 1650000,
    referenceCode: 'BANK-REF-1106',
    memberId: 10,
    memberCode: 'MB-0256',
    memberName: 'Sample Member 4',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-015',
    itemName: 'Swimming 90-day package',
    sportName: 'Swimming',
    invoiceId: 206,
    invoiceNumber: 'INV-1106',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 107,
    code: 'PAY-1107',
    time: '2026-11-02T11:00:00',
    paymentTime: '2026-11-02T11:00:00',
    createdAt: '2026-11-02T10:55:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 300000,
    referenceCode: 'CASH-REC-1107',
    memberId: 11,
    memberCode: 'MB-0257',
    memberName: 'Sample Member 5',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-GOLD',
    itemName: 'Gold Tier 12-month',
    sportName: null,
    invoiceId: 207,
    invoiceNumber: 'INV-1107',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  // 2026-10 transactions (12 paid totaling 16,110,000 VND, 2 pending totaling 900,000 VND, 1 refunded of 150,000 VND)
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
    settlementType: 'ACTUAL',
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
    settlementType: 'ACTUAL',
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
    amount: 600000,
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
    settlementType: 'ACTUAL',
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
    settlementType: 'ACTUAL',
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
    method: 'BANK_TRANSFER',
    amount: 1310000,
    referenceCode: 'BANK-REF-45',
    memberId: 6,
    memberCode: 'MB-0252',
    memberName: 'Linh Pham',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-VIP',
    itemName: 'VIP Tier 12-month',
    sportName: null,
    invoiceId: 86,
    invoiceNumber: 'INV-1045',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 46,
    code: 'PAY-1046',
    time: '2026-10-12T09:30:00',
    paymentTime: '2026-10-12T09:30:00',
    createdAt: '2026-10-12T09:25:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 1300000,
    referenceCode: 'CASH-REC-46',
    memberId: 7,
    memberCode: 'MB-0253',
    memberName: 'Sample Member 1',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-GOLD',
    itemName: 'Gold Tier 12-month',
    sportName: null,
    invoiceId: 87,
    invoiceNumber: 'INV-1046',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  {
    id: 47,
    code: 'PAY-1047',
    time: '2026-10-11T15:00:00',
    paymentTime: '2026-10-11T15:00:00',
    createdAt: '2026-10-11T14:55:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 1620000,
    referenceCode: 'BANK-REF-47',
    memberId: 8,
    memberCode: 'MB-0254',
    memberName: 'Sample Member 2',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-015',
    itemName: 'Swimming 90-day package',
    sportName: 'Swimming',
    invoiceId: 88,
    invoiceNumber: 'INV-1047',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 48,
    code: 'PAY-1048',
    time: '2026-10-10T11:20:00',
    paymentTime: '2026-10-10T11:20:00',
    createdAt: '2026-10-10T11:15:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 1200000,
    referenceCode: 'BANK-REF-48',
    memberId: 9,
    memberCode: 'MB-0255',
    memberName: 'Sample Member 3',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-009',
    itemName: 'Basketball 90-day package',
    sportName: 'Basketball',
    invoiceId: 89,
    invoiceNumber: 'INV-1048',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 49,
    code: 'PAY-1049',
    time: '2026-10-09T14:00:00',
    paymentTime: '2026-10-09T14:00:00',
    createdAt: '2026-10-09T13:55:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 1350000,
    referenceCode: 'CASH-REC-49',
    memberId: 10,
    memberCode: 'MB-0256',
    memberName: 'Sample Member 4',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-003',
    itemName: 'Football 90-day package',
    sportName: 'Football',
    invoiceId: 90,
    invoiceNumber: 'INV-1049',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  {
    id: 50,
    code: 'PAY-1050',
    time: '2026-10-08T10:00:00',
    paymentTime: '2026-10-08T10:00:00',
    createdAt: '2026-10-08T09:55:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 2160000,
    referenceCode: 'BANK-REF-50',
    memberId: 11,
    memberCode: 'MB-0257',
    memberName: 'Sample Member 5',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-033',
    itemName: 'Tennis 90-day package',
    sportName: 'Tennis',
    invoiceId: 91,
    invoiceNumber: 'INV-1050',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 51,
    code: 'PAY-1051',
    time: '2026-10-07T16:30:00',
    paymentTime: '2026-10-07T16:30:00',
    createdAt: '2026-10-07T16:25:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 1200000,
    referenceCode: 'BANK-REF-51',
    memberId: 12,
    memberCode: 'MB-0258',
    memberName: 'Sample Member 6',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-021',
    itemName: 'Badminton 90-day package',
    sportName: 'Badminton',
    invoiceId: 92,
    invoiceNumber: 'INV-1051',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 52,
    code: 'PAY-1052',
    time: '2026-10-06T09:15:00',
    paymentTime: '2026-10-06T09:15:00',
    createdAt: '2026-10-06T09:10:00',
    status: 'SUCCESS',
    method: 'CASH',
    amount: 1080000,
    referenceCode: 'CASH-REC-52',
    memberId: 13,
    memberCode: 'MB-0259',
    memberName: 'Sample Member 7',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-027',
    itemName: 'Volleyball 90-day package',
    sportName: 'Volleyball',
    invoiceId: 93,
    invoiceNumber: 'INV-1052',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
  {
    id: 53,
    code: 'PAY-1053',
    time: '2026-10-05T13:45:00',
    paymentTime: '2026-10-05T13:45:00',
    createdAt: '2026-10-05T13:40:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 1400000,
    referenceCode: 'BANK-REF-53',
    memberId: 14,
    memberCode: 'MB-0260',
    memberName: 'Sample Member 8',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-035',
    itemName: 'Tennis coach-led 30-day package',
    sportName: 'Tennis',
    invoiceId: 94,
    invoiceNumber: 'INV-1053',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 54,
    code: 'PAY-1054',
    time: '2026-10-04T10:30:00',
    paymentTime: '2026-10-04T10:30:00',
    createdAt: '2026-10-04T10:25:00',
    status: 'SUCCESS',
    method: 'BANK_TRANSFER',
    amount: 2180000,
    referenceCode: 'BANK-REF-54',
    memberId: 15,
    memberCode: 'MB-0261',
    memberName: 'Sample Member 9',
    itemType: 'SPORT_PACKAGE',
    itemCode: 'PK-017',
    itemName: 'Swimming coach-led 30-day package',
    sportName: 'Swimming',
    invoiceId: 95,
    invoiceNumber: 'INV-1054',
    settlementType: 'ACTUAL',
    settlementProvider: 'VIETQR',
    simulatedTransactionId: null,
  },
  {
    id: 56,
    code: 'PAY-1056',
    time: '2026-10-02T11:00:00',
    paymentTime: null,
    createdAt: '2026-10-02T10:55:00',
    status: 'PENDING',
    method: 'CASH',
    amount: 300000,
    referenceCode: 'CASH-REC-56',
    memberId: 17,
    memberCode: 'MB-0263',
    memberName: 'Sample Member 11',
    itemType: 'MEMBERSHIP_CARD',
    itemCode: 'TIER-GOLD',
    itemName: 'Gold Tier 12-month',
    sportName: null,
    invoiceId: 97,
    invoiceNumber: 'INV-1056',
    settlementType: 'ACTUAL',
    settlementProvider: 'CASH',
    simulatedTransactionId: null,
  },
]

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
  { month: '2026-06', revenue: 14200000, transactions: 11 },
  { month: '2026-07', revenue: 15800000, transactions: 12 },
  { month: '2026-08', revenue: 16500000, transactions: 13 },
  { month: '2026-09', revenue: 17000000, transactions: 13 },
  { month: '2026-10', revenue: 16110000, transactions: 12 },
  { month: '2026-11', revenue: 4260000, transactions: 5 },
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
    createdAt: '2026-11-08T10:00:00',
    cancelledBookings: null,
  },
]

function txDate(tx: FinanceTransaction): string {
  return (tx.time ?? tx.createdAt ?? '').slice(0, 10)
}

function monthRange(month: string): { from: string; to: string; previousMonth: string } {
  const match = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(month)
  const year = match ? Number(match[1]) : 2026
  const m = match ? Number(match[2]) : 11
  const lastDay = new Date(Date.UTC(year, m, 0)).getUTCDate()
  const prevYear = m === 1 ? year - 1 : year
  const prevM = m === 1 ? 12 : m - 1
  return {
    from: `${month}-01`,
    to: `${month}-${String(lastDay).padStart(2, '0')}`,
    previousMonth: `${prevYear}-${String(prevM).padStart(2, '0')}`,
  }
}

function computeOverview(month: string, empty: boolean): ManagerFinanceOverview {
  const { from, to, previousMonth } = monthRange(month)
  const inMonth = empty ? [] : transactionsData.filter((t) => {
    const d = txDate(t)
    return d >= from && d <= to
  })
  const paid = inMonth.filter((t) => t.status === 'SUCCESS' && t.settlementType !== 'SIMULATED')
  const simulated = inMonth.filter((t) => t.status === 'SUCCESS' && t.settlementType === 'SIMULATED')
  const pending = inMonth.filter((t) => t.status === 'PENDING')
  const refunded = inMonth.filter((t) => t.status === 'REFUNDED')

  const paidRevenue = paid.reduce((sum, t) => sum + t.amount, 0)
  const paidTransactions = paid.length
  const averagePayment = paidTransactions > 0 ? Math.round(paidRevenue / paidTransactions) : 0
  const pendingAmount = pending.reduce((sum, t) => sum + t.amount, 0)
  const refundedAmount = refunded.reduce((sum, t) => sum + t.amount, 0)
  const simulatedRevenue = simulated.reduce((sum, t) => sum + t.amount, 0)

  const prevRange = monthRange(previousMonth)
  const prevPaid = empty
    ? []
    : transactionsData.filter((t) => {
        const d = txDate(t)
        return d >= prevRange.from && d <= prevRange.to && t.status === 'SUCCESS' && t.settlementType !== 'SIMULATED'
      })
  const previousPaidRevenue = previousMonth === '2026-09' && !empty
    ? 17000000
    : prevPaid.reduce((sum, t) => sum + t.amount, 0)
  const revenueChange = paidRevenue - previousPaidRevenue
  const revenueChangePercent = previousPaidRevenue > 0
    ? Number(((revenueChange / previousPaidRevenue) * 100).toFixed(2))
    : null

  return {
    month,
    from,
    to,
    paidRevenue,
    paidTransactions,
    averagePayment,
    pendingAmount,
    pendingTransactions: pending.length,
    refundedAmount,
    refundedTransactions: refunded.length,
    previousMonth,
    previousPaidRevenue,
    revenueChange,
    revenueChangePercent,
    simulatedRevenue,
    simulatedTransactions: simulated.length,
    netRevenue: paidRevenue - refundedAmount,
  }
}

function computeRevenueReport(from: string, to: string, groupBy: RevenueGroup, empty: boolean): ManagerRevenueReport {
  const paid = empty
    ? []
    : transactionsData.filter((t) => {
        const d = txDate(t)
        return d >= from && d <= to && t.status === 'SUCCESS' && t.settlementType !== 'SIMULATED'
      })
  const totalRevenue = paid.reduce((sum, t) => sum + t.amount, 0)
  const totalTransactions = paid.length

  const buckets = new Map<string, { key: string | null; label: string; revenue: number; transactions: number }>()
  for (const tx of paid) {
    let key: string
    let label: string
    if (groupBy === 'METHOD') {
      key = tx.method ?? 'UNKNOWN'
      label = key === 'BANK_TRANSFER' ? 'Bank transfer' : key === 'CASH' ? 'Cash' : key
    } else if (groupBy === 'SPORT') {
      key = tx.sportName ? tx.sportName.toUpperCase() : 'MEMBERSHIP_CARD'
      label = tx.sportName ?? 'Membership cards'
    } else {
      key = tx.itemType ?? 'OTHER'
      label = key === 'SPORT_PACKAGE' ? 'Sport packages' : key === 'MEMBERSHIP_CARD' ? 'Membership cards' : key === 'SINGLE_VISIT' ? 'Single visits' : key
    }
    const current = buckets.get(key)
    if (current) {
      current.revenue += tx.amount
      current.transactions += 1
    } else {
      buckets.set(key, { key, label, revenue: tx.amount, transactions: 1 })
    }
  }

  const rows: FinanceRevenueRow[] = [...buckets.values()]
    .sort((a, b) => b.revenue - a.revenue)
    .map((row) => ({
      ...row,
      sharePercent: totalRevenue > 0 ? Number(((row.revenue / totalRevenue) * 100).toFixed(2)) : 0,
    }))

  return {
    from,
    to,
    groupBy,
    totalRevenue,
    totalTransactions,
    rows,
  }
}

export function financePreviewResponse(path: string, options: RequestOptions, empty: boolean): Response | undefined {
  const method = options.method ?? 'GET'
  const query = options.query ?? {}

  if (path === '/manager/finance/overview' && method === 'GET') {
    const month = query.month ? String(query.month) : '2026-11'
    return json(200, computeOverview(month, empty))
  }

  if (path === '/manager/finance/trend' && method === 'GET') {
    return json(200, empty ? [] : trendData)
  }

  if (path === '/manager/finance/transactions' && method === 'GET') {
    const page = Number(query.page ?? 0)
    const size = Number(query.size ?? 20)
    const from = query.from ? String(query.from) : null
    const to = query.to ? String(query.to) : null
    const status = query.status ? String(query.status) : null
    const paymentMethod = query.method ? String(query.method) : null
    const search = query.search ? String(query.search).toLowerCase() : null
    let filtered = empty ? [] : transactionsData
    if (from) {
      filtered = filtered.filter((t) => txDate(t) >= from)
    }
    if (to) {
      filtered = filtered.filter((t) => txDate(t) <= to)
    }
    if (status) {
      filtered = filtered.filter((t) => t.status === status)
    }
    if (paymentMethod) {
      filtered = filtered.filter((t) => t.method === paymentMethod)
    }
    if (search) {
      filtered = filtered.filter((t) =>
        `${t.code} ${t.referenceCode ?? ''} ${t.memberName ?? ''} ${t.memberCode ?? ''} ${t.itemName ?? ''} ${t.invoiceNumber ?? ''}`.toLowerCase().includes(search)
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
    const groupBy = (query.groupBy as RevenueGroup) ?? 'ITEM_TYPE'
    const from = String(query.from ?? '2026-11-01')
    const to = String(query.to ?? '2026-11-30')
    return json(200, computeRevenueReport(from, to, groupBy, empty))
  }

  if (path === '/manager/finance/revenue/export' && method === 'GET') {
    const from = String(query.from ?? '2026-11-01')
    const to = String(query.to ?? '2026-11-30')
    const report = computeRevenueReport(from, to, 'ITEM_TYPE', empty)
    const lines = [
      'Category,Revenue (VND),Transactions,Share (%)',
      ...report.rows.map((r) => `"${r.label}",${r.revenue},${r.transactions},${r.sharePercent}`),
      `"Total",${report.totalRevenue},${report.totalTransactions},100`,
    ]
    return new Response(lines.join('\n'), {
      status: 200,
      headers: {
        'Content-Type': 'text/csv;charset=UTF-8',
        'Content-Disposition': `attachment; filename="revenue_${from}_${to}.csv"`,
      },
    })
  }

  if (path === '/manager/reports/operations' && method === 'GET') {
    return json(200, {
      from: String(query.from ?? '2026-11-01'),
      to: String(query.to ?? '2026-11-30'),
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
    return json(200, empty ? [] : pendingRefundsData.filter((r) => r.status === 'PENDING'))
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
