import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'
import type { Refund } from './receptionPaymentsApi'
import type { PageResponse } from './types'

export type RevenueGroup = 'ITEM_TYPE' | 'PAYMENT_METHOD' | 'SPORT'

export interface FinanceServiceCollection {
  service: string
  collected: number
}

export interface ManagerFinanceOverview {
  paymentsCollected: number
  completedRefunds: number
  netCollected: number
  collectedByService: FinanceServiceCollection[]
  pendingPaymentsCount: number
  pendingRefundApprovalCount: number
  approvedNotReturnedCount: number
}

export interface FinanceTrendPoint {
  month: string
  revenue: number
}

export interface FinanceTransaction {
  id: number
  reference: string
  memberName: string | null
  memberCode: string | null
  service: string | null
  itemType: string | null
  amount: number
  method: string | null
  status: string
  occurredAt: string | null
  invoiceNumber: string | null
}

export interface FinanceTransactionQuery extends RequestSignal {
  month?: string
  query?: string
  status?: string
  page?: number
  size?: number
}

export interface FinanceRevenueRow {
  category: string
  collected: number
  refunded: number
  netCollected: number
}

export interface ManagerRevenueReport {
  from: string
  to: string
  groupBy: RevenueGroup
  totals: {
    collected: number
    refunded: number
    netCollected: number
  }
  rows: FinanceRevenueRow[]
}

export interface RevenueQuery extends RequestSignal {
  from: string
  to: string
  groupBy: RevenueGroup
  sportId?: number
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

function count(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value
}

function firstValue(source: Json, keys: string[]): unknown {
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null) return source[key]
  }
  return undefined
}

function optionalText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function requiredText(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value
}

function asOverview(value: unknown): ManagerFinanceOverview {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  const attention = isObject(value.needsAttention) ? value.needsAttention : value
  const serviceRows = firstValue(value, ['collectedByService', 'serviceBreakdown', 'byService'])
  if (!Array.isArray(serviceRows)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    paymentsCollected: amount(firstValue(value, ['paymentsCollected', 'totalCollected'])),
    completedRefunds: amount(firstValue(value, ['completedRefunds', 'totalRefunded'])),
    netCollected: amount(firstValue(value, ['netCollected', 'netRevenue'])),
    collectedByService: serviceRows.map((row) => {
      if (!isObject(row)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
      return {
        service: requiredText(firstValue(row, ['service', 'serviceName', 'category', 'itemType'])),
        collected: amount(firstValue(row, ['collected', 'amount', 'total'])),
      }
    }),
    pendingPaymentsCount: count(firstValue(attention, ['pendingPaymentsCount', 'pendingPayments'])),
    pendingRefundApprovalCount: count(firstValue(attention, ['pendingRefundApprovalCount', 'awaitingRefundApproval', 'pendingRefunds'])),
    approvedNotReturnedCount: count(firstValue(attention, ['approvedNotReturnedCount', 'approvedNotReturned', 'approvedRefunds'])),
  }
}

function asTrendPoint(value: unknown): FinanceTrendPoint {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    month: requiredText(firstValue(value, ['month', 'period', 'label'])),
    revenue: amount(firstValue(value, ['revenue', 'collected', 'amount', 'total'])),
  }
}

function asTrend(value: unknown): FinanceTrendPoint[] {
  const rows = Array.isArray(value) ? value : isObject(value) ? firstValue(value, ['months', 'trend', 'content']) : null
  if (!Array.isArray(rows)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return rows.map(asTrendPoint)
}

function asTransaction(value: unknown): FinanceTransaction {
  if (!isObject(value) || typeof value.id !== 'number' || typeof value.status !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const reference = requiredText(firstValue(value, ['reference', 'paymentCode', 'code', 'invoiceNumber']))
  const rawAmount = firstValue(value, ['amount', 'amountCollected', 'totalAmount'])
  return {
    id: value.id,
    reference,
    memberName: optionalText(firstValue(value, ['memberName', 'memberFullName'])),
    memberCode: optionalText(value.memberCode),
    service: optionalText(firstValue(value, ['service', 'serviceName', 'itemDescription', 'description'])),
    itemType: optionalText(value.itemType),
    amount: amount(rawAmount),
    method: optionalText(firstValue(value, ['method', 'paymentMethod'])),
    status: value.status,
    occurredAt: optionalText(firstValue(value, ['occurredAt', 'paymentTime', 'createdAt'])),
    invoiceNumber: optionalText(value.invoiceNumber),
  }
}

function asTransactionPage(value: unknown): PageResponse<FinanceTransaction> {
  if (Array.isArray(value)) {
    const content = value.map(asTransaction)
    return { content, page: 0, size: Math.max(content.length, 1), totalElements: content.length, totalPages: content.length ? 1 : 0 }
  }
  if (!isObject(value) || !Array.isArray(value.content)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  const pageValue = firstValue(value, ['page', 'number']) ?? 0
  const sizeValue = value.size ?? Math.max(value.content.length, 1)
  const totalElements = value.totalElements ?? value.content.length
  const totalPages = value.totalPages ?? (Number(sizeValue) > 0 ? Math.ceil(Number(totalElements) / Number(sizeValue)) : 0)
  if (![pageValue, sizeValue, totalElements, totalPages].every((item) => typeof item === 'number' && Number.isSafeInteger(item) && item >= 0)
    || Number(sizeValue) < 1 || value.content.length > Number(sizeValue)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    content: value.content.map(asTransaction),
    page: Number(pageValue),
    size: Number(sizeValue),
    totalElements: Number(totalElements),
    totalPages: Number(totalPages),
  }
}

function asRefund(value: unknown): Refund {
  if (!isObject(value) || typeof value.id !== 'number' || typeof value.refundCode !== 'string' || typeof value.status !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as Refund
}

function asRefundList(value: unknown): Refund[] {
  if (!Array.isArray(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return value.map(asRefund)
}

function asRevenueRow(value: unknown): FinanceRevenueRow {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    category: requiredText(firstValue(value, ['category', 'label', 'name', 'service'])),
    collected: amount(firstValue(value, ['collected', 'paymentsCollected', 'gross'])),
    refunded: amount(firstValue(value, ['refunded', 'completedRefunds', 'refundAmount'])),
    netCollected: amount(firstValue(value, ['netCollected', 'netRevenue', 'net'])),
  }
}

function asRevenue(value: unknown, query: RevenueQuery): ManagerRevenueReport {
  if (!isObject(value)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  const totals = isObject(value.totals) ? value.totals : value
  const rows = firstValue(value, ['rows', 'categories', 'content'])
  if (!Array.isArray(rows)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return {
    from: typeof value.from === 'string' ? value.from : query.from,
    to: typeof value.to === 'string' ? value.to : query.to,
    groupBy: query.groupBy,
    totals: {
      collected: amount(firstValue(totals, ['collected', 'paymentsCollected', 'gross'])),
      refunded: amount(firstValue(totals, ['refunded', 'completedRefunds', 'refundAmount'])),
      netCollected: amount(firstValue(totals, ['netCollected', 'netRevenue', 'net'])),
    },
    rows: rows.map(asRevenueRow),
  }
}

export const managerFinanceApi = {
  overview(month: string, options: RequestSignal = {}): Promise<ManagerFinanceOverview> {
    return api.get<unknown>('/manager/finance/overview', { ...options, query: { month } }).then(asOverview)
  },

  trend(month: string, options: RequestSignal = {}): Promise<FinanceTrendPoint[]> {
    return api.get<unknown>('/manager/finance/trend', { ...options, query: { month } }).then(asTrend)
  },

  transactions({ month, query, status, page = 0, size = 20, signal }: FinanceTransactionQuery = {}): Promise<PageResponse<FinanceTransaction>> {
    return api.get<unknown>('/manager/finance/transactions', {
      signal,
      query: { month, query: query || undefined, status: status || undefined, page, size },
    }).then(asTransactionPage)
  },

  pendingRefunds(options: RequestSignal = {}): Promise<Refund[]> {
    return api.get<unknown>('/refunds/pending', options).then(asRefundList)
  },

  reviewRefund(refundId: number, request: ReviewRefundRequest): Promise<Refund> {
    return api.put<unknown>(`/refunds/${refundId}/review`, request).then(asRefund)
  },

  revenue(query: RevenueQuery): Promise<ManagerRevenueReport> {
    const { from, to, groupBy, sportId, signal } = query
    return api.get<unknown>('/manager/finance/revenue', {
      signal,
      query: { from, to, groupBy, sportId },
    }).then((value) => asRevenue(value, query))
  },
}
