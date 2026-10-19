import { api, type ApiDownload } from './client'
import { StaleSessionError } from './errors'
import { tokenStorage } from './tokenStorage'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'
import type { Refund } from './receptionPaymentsApi'
import type { PageResponse } from './types'

export type RevenueGroup = 'ITEM_TYPE' | 'METHOD' | 'SPORT'

/** Mirrors BE-22 FinanceOverviewResponse. Refunds are reported separately from paid revenue. */
export interface ManagerFinanceOverview {
  month: string
  from: string
  to: string
  paidRevenue: number
  paidTransactions: number
  averagePayment: number
  pendingAmount: number
  pendingTransactions: number
  refundedAmount: number
  refundedTransactions: number
  previousMonth: string
  previousPaidRevenue: number
  revenueChange: number
  revenueChangePercent: number | null
}

/** Mirrors BE-22 RevenueTrendPoint. The endpoint is anchored to the current month. */
export interface FinanceTrendPoint {
  month: string
  revenue: number
  transactions: number
}

/** Mirrors BE-22 FinanceTransactionResponse fields used by the finance screens. */
export interface FinanceTransaction {
  id: number
  code: string
  time: string | null
  paymentTime: string | null
  createdAt: string | null
  status: string
  method: string | null
  amount: number
  referenceCode: string | null
  memberId: number | null
  memberCode: string | null
  memberName: string | null
  itemType: string | null
  itemCode: string | null
  itemName: string | null
  sportName: string | null
  invoiceId: number | null
  invoiceNumber: string | null
}

/** The UI keeps a month and query in the URL; these are translated to BE-22 from/to/search. */
export interface FinanceTransactionQuery extends RequestSignal {
  month?: string
  query?: string
  status?: string
  method?: string
  page?: number
  size?: number
}

/** Mirrors BE-22 RevenueBreakdownRow. */
export interface FinanceRevenueRow {
  key: string | null
  label: string
  revenue: number
  transactions: number
  sharePercent: number
}

/** Mirrors BE-22 RevenueBreakdownResponse. */
export interface ManagerRevenueReport {
  from: string
  to: string
  groupBy: RevenueGroup
  totalRevenue: number
  totalTransactions: number
  rows: FinanceRevenueRow[]
}

export interface RevenueQuery extends RequestSignal {
  from: string
  to: string
  groupBy: RevenueGroup
}

export interface ReviewRefundRequest {
  approved: boolean
  amountApproved: number | null
  managerNote: string
}

type Json = Record<string, unknown>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function amount(value: unknown): number {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' && value.trim() ? Number(value) : Number.NaN
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return parsed
}

function signedAmount(value: unknown): number {
  const parsed = typeof value === 'number' ? value : typeof value === 'string' && value.trim() ? Number(value) : Number.NaN
  if (!Number.isFinite(parsed)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return parsed
}

function nullableSignedAmount(value: unknown): number | null {
  return value === null || value === undefined ? null : signedAmount(value)
}

function count(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value
}

function requiredText(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value
}

function optionalText(value: unknown): string | null {
  if (value === null || value === undefined) return null
  if (typeof value !== 'string') throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value.trim() ? value : null
}

function asOverview(value: unknown): ManagerFinanceOverview {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    month: requiredText(value.month),
    from: requiredText(value.from),
    to: requiredText(value.to),
    paidRevenue: amount(value.paidRevenue),
    paidTransactions: count(value.paidTransactions),
    averagePayment: amount(value.averagePayment),
    pendingAmount: amount(value.pendingAmount),
    pendingTransactions: count(value.pendingTransactions),
    refundedAmount: amount(value.refundedAmount),
    refundedTransactions: count(value.refundedTransactions),
    previousMonth: requiredText(value.previousMonth),
    previousPaidRevenue: amount(value.previousPaidRevenue),
    revenueChange: signedAmount(value.revenueChange),
    revenueChangePercent: nullableSignedAmount(value.revenueChangePercent),
  }
}

function asTrendPoint(value: unknown): FinanceTrendPoint {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    month: requiredText(value.month),
    revenue: amount(value.revenue),
    transactions: count(value.transactions),
  }
}

function asTrend(value: unknown): FinanceTrendPoint[] {
  if (!Array.isArray(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value.map(asTrendPoint)
}

function asTransaction(value: unknown): FinanceTransaction {
  if (!isObject(value) || typeof value.id !== 'number' || !Number.isSafeInteger(value.id) || value.id < 1) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    id: value.id,
    code: requiredText(value.code),
    time: optionalText(value.time),
    paymentTime: optionalText(value.paymentTime),
    createdAt: optionalText(value.createdAt),
    status: requiredText(value.status),
    method: optionalText(value.method),
    amount: amount(value.amount),
    referenceCode: optionalText(value.referenceCode),
    memberId: value.memberId === null || value.memberId === undefined ? null : count(value.memberId),
    memberCode: optionalText(value.memberCode),
    memberName: optionalText(value.memberName),
    itemType: optionalText(value.itemType),
    itemCode: optionalText(value.itemCode),
    itemName: optionalText(value.itemName),
    sportName: optionalText(value.sportName),
    invoiceId: value.invoiceId === null || value.invoiceId === undefined ? null : count(value.invoiceId),
    invoiceNumber: optionalText(value.invoiceNumber),
  }
}

function asTransactionPage(value: unknown): PageResponse<FinanceTransaction> {
  if (!isObject(value) || !Array.isArray(value.content)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  const page = count(value.page)
  const size = count(value.size)
  const totalElements = count(value.totalElements)
  const totalPages = count(value.totalPages)
  if (size < 1 || value.content.length > size) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return { content: value.content.map(asTransaction), page, size, totalElements, totalPages }
}

function asRefund(value: unknown): Refund {
  if (!isObject(value) || !['PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'].includes(String(value.status))) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const id = count(value.id)
  const amountRequested = amount(value.amountRequested)
  const amountApproved = value.amountApproved === null ? null : amount(value.amountApproved)
  if (id < 1 || amountRequested <= 0 || ((value.status === 'APPROVED' || value.status === 'COMPLETED') && (amountApproved === null || amountApproved <= 0))) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    id,
    refundCode: requiredText(value.refundCode),
    status: requiredText(value.status),
    packageRegistrationId: count(value.packageRegistrationId),
    registrationCode: optionalText(value.registrationCode),
    memberId: count(value.memberId),
    memberCode: optionalText(value.memberCode),
    memberFullName: optionalText(value.memberFullName),
    amountRequested,
    amountApproved,
    reason: requiredText(value.reason),
    requestedByName: optionalText(value.requestedByName),
    reviewedByName: optionalText(value.reviewedByName),
    reviewedAt: optionalText(value.reviewedAt),
    managerNote: optionalText(value.managerNote),
    createdAt: requiredText(value.createdAt),
    paymentId: value.paymentId === null || value.paymentId === undefined ? null : count(value.paymentId),
    paymentCode: optionalText(value.paymentCode),
  }
}

function asRefundList(value: unknown): Refund[] {
  if (!Array.isArray(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value.map((row) => {
    const refund = asRefund(row)
    if (refund.status !== 'PENDING') throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    return refund
  })
}

function asRevenueRow(value: unknown): FinanceRevenueRow {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    key: optionalText(value.key),
    label: requiredText(value.label),
    revenue: amount(value.revenue),
    transactions: count(value.transactions),
    sharePercent: amount(value.sharePercent),
  }
}

function asRevenue(value: unknown, query: RevenueQuery): ManagerRevenueReport {
  if (!isObject(value) || value.groupBy !== query.groupBy || !Array.isArray(value.rows)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    from: requiredText(value.from),
    to: requiredText(value.to),
    groupBy: query.groupBy,
    totalRevenue: amount(value.totalRevenue),
    totalTransactions: count(value.totalTransactions),
    rows: value.rows.map(asRevenueRow),
  }
}

function datesForMonth(month: string | undefined): { from?: string; to?: string } {
  if (month === undefined || month === '') return {}
  const match = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(month)
  if (!match) throw new Error('Choose a valid reporting month.')
  const lastDay = new Date(Date.UTC(Number(match[1]), Number(match[2]), 0)).getUTCDate()
  return { from: `${month}-01`, to: `${month}-${String(lastDay).padStart(2, '0')}` }
}

export const managerFinanceApi = {
  overview(month: string, options: RequestSignal = {}): Promise<ManagerFinanceOverview> {
    return api.get<unknown>('/manager/finance/overview', { ...options, query: { month } }).then(asOverview)
  },

  trend(options: RequestSignal = {}): Promise<FinanceTrendPoint[]> {
    return api.get<unknown>('/manager/finance/trend', { ...options, query: { months: 6 } }).then(asTrend)
  },

  transactions({ month, query, status, method, page = 0, size = 20, signal }: FinanceTransactionQuery = {}): Promise<PageResponse<FinanceTransaction>> {
    const dateRange = datesForMonth(month)
    return api.get<unknown>('/manager/finance/transactions', {
      signal,
      query: { ...dateRange, search: query?.trim() || undefined, status: status || undefined, method: method || undefined, page, size },
    }).then(asTransactionPage)
  },

  pendingRefunds(options: RequestSignal = {}): Promise<Refund[]> {
    return api.get<unknown>('/refunds/pending', options).then(asRefundList)
  },

  reviewRefund(refundId: number, request: ReviewRefundRequest): Promise<Refund> {
    return api.put<unknown>(`/refunds/${refundId}/review`, request).then((value) => {
      const refund = asRefund(value)
      if (refund.id !== refundId || refund.status !== (request.approved ? 'APPROVED' : 'REJECTED')) {
        throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
      }
      return refund
    })
  },

  revenue(query: RevenueQuery): Promise<ManagerRevenueReport> {
    const { from, to, groupBy, signal } = query
    return api.get<unknown>('/manager/finance/revenue', {
      signal,
      query: { from, to, groupBy },
    }).then((value) => asRevenue(value, query))
  },

  async exportRevenue(from: string, to: string, options: RequestSignal = {}): Promise<ApiDownload> {
    const epoch = tokenStorage.getEpoch()
    const result = await api.getDownload('/manager/finance/revenue/export', {
      ...options,
      query: { from, to },
      headers: { Accept: 'text/csv' },
    })
    if (!/^text\/csv(?:\s*;|$)/i.test(result.contentType ?? '') || result.blob.size === 0) {
      throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    }
    // Inspect a bounded prefix only; keep the original Blob, BOM and file bytes intact.
    const prefix = (await result.blob.slice(0, 512).text()).trimStart()
    if (/^[{[<]/.test(prefix)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    if (tokenStorage.getEpoch() !== epoch) throw new StaleSessionError()
    return result
  },
}
