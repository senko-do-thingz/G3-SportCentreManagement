import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'
import { normalizePage } from './page'
import type { Payment } from './paymentsApi'
import type { PaymentMethod } from './receptionApi'
import type { PageResponse, SpringPage } from './types'

/**
 * Front desk payment, invoice and refund endpoints of FE-12 (Figma F3-04 to F3-07).
 *
 * Contract notes:
 * - On main: GET /payments/{id}, PUT /refunds/{id}/complete (no body), GET /refunds/member/{memberId},
 *   GET /refunds/pending (MANAGER only on main), POST /refunds with {packageRegistrationId, reason}.
 * - Planned in BE-18: GET /payments?status=&query=&page=&size=, PUT /payments/{id}/confirm,
 *   PUT /payments/{id}/fail and GET /invoices/by-payment/{paymentId}. A confirm whose amount does
 *   not match answers 409 with code PAYMENT_AMOUNT_MISMATCH and leaves the payment PENDING.
 * - Planned in BE-21: GET /refunds/quote?registrationId=, amountRequested on POST /refunds,
 *   refundReference on PUT /refunds/{id}/complete, and GET /refunds/pending opened to RECEPTIONIST.
 * The shapes below are the ones written in docs/tests/FE-12_test_cases.md section 1.1. Unknown
 * fields are ignored; a missing required field is reported as an unexpected response.
 */

/** Payment statuses of com.sportify.payment.entity.PaymentStatus. */
export const PAYMENT_STATUS_CODES = ['PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'] as const

/** One row of GET /payments (BE-18): PaymentResponse plus the fields the desk list needs. */
export interface StaffPayment extends Payment {
  notes?: string | null
  updatedAt?: string | null
  /** Member full name, shown in the list and the verification form. */
  memberFullName?: string | null
  /** SPORT_PACKAGE or MEMBERSHIP_CARD. */
  itemType?: string | null
  /** Registration code or card code of the item the payment pays for. */
  itemCode?: string | null
  /** Recorded at confirm time: transfer or slip reference. */
  paymentReference?: string | null
  amountReceived?: number | string | null
  invoiceId?: number | null
  invoiceNumber?: string | null
}

/** Body of PUT /payments/{id}/confirm (BE-18). */
export interface ConfirmPaymentRequest {
  paymentMethod: PaymentMethod
  /** Required for every method except CASH; null for cash. */
  paymentReference: string | null
  amountReceived: number
  note: string | null
}

/** Body of PUT /payments/{id}/fail (BE-18). */
export interface FailPaymentRequest {
  reason: string
}

/** One invoice_line row of the invoice (BE-18). */
export interface InvoiceLine {
  id: number
  itemType: string | null
  description: string
  quantity: number | null
  unitPrice: number | string | null
  lineTotal: number | string | null
}

/** GET /invoices/by-payment/{paymentId} (BE-18), built from the V14 invoice columns. */
export interface Invoice {
  id: number
  invoiceNumber: string
  paymentId: number | null
  paymentCode?: string | null
  paymentMethod?: string | null
  paymentReference?: string | null
  paymentTime?: string | null
  memberId: number | null
  memberCode?: string | null
  memberFullName?: string | null
  subtotalAmount: number | string | null
  discountAmount: number | string | null
  totalAmount: number | string
  /** ISSUED, PAID, CANCELLED or REFUNDED. */
  status: string
  issueDate: string | null
  lines?: InvoiceLine[] | null
}

/** GET /refunds/quote?registrationId= (BE-21): what may be refunded for one registration. */
export interface RefundQuote {
  registrationId: number
  registrationCode: string | null
  memberId: number | null
  memberCode: string | null
  memberFullName: string | null
  serviceName: string | null
  paymentCode: string | null
  invoiceNumber: string | null
  /** Status of the original payment, normally SUCCESS. */
  paymentStatus: string | null
  paidAmount: number | null
  /** Amount refunded before for this registration, 0 when none. */
  priorRefundAmount: number | null
  /** Amount the backend suggests, prefilled in the form. */
  suggestedAmount: number
  /** Upper limit of a refund; the form refuses more. Defaults to the suggested amount. */
  maxRefundable: number
  eligible: boolean
  /** Why the registration cannot be refunded, when eligible is false. */
  ineligibleReason: string | null
}

/** Body of POST /refunds; amountRequested is added by BE-21. */
export interface CreateRefundRequest {
  packageRegistrationId: number
  amountRequested: number
  reason: string
}

/** Mirrors com.sportify.catalog.dto.RefundResponse, plus the optional BE-21 fields. */
export interface Refund {
  id: number
  refundCode: string
  packageRegistrationId: number | null
  registrationCode: string | null
  memberId: number | null
  memberCode: string | null
  memberFullName: string | null
  amountRequested: number | string | null
  amountApproved: number | string | null
  reason: string | null
  /** PENDING, APPROVED, REJECTED or COMPLETED. */
  status: string
  requestedByName?: string | null
  reviewedByName?: string | null
  reviewedAt?: string | null
  managerNote?: string | null
  createdAt: string | null
  /** BE-21: original payment and invoice, and the evidence recorded on completion. */
  paymentCode?: string | null
  invoiceNumber?: string | null
  refundReference?: string | null
  completedAt?: string | null
}

type Json = Record<string, unknown>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

/** A number, or a numeric string as Jackson writes some BigDecimal values; anything else is null. */
function amountOf(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function asWithId<T>(value: unknown): T {
  if (!isObject(value) || typeof value.id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T
}

function asPayment(value: unknown): StaffPayment {
  const payment = asWithId<StaffPayment>(value)
  if (typeof payment.code !== 'string' || typeof payment.status !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return payment
}

/** Accepts a PageResponse, a Spring Page or a plain array (one page with every row). */
function asPaymentPage(value: unknown): PageResponse<StaffPayment> {
  if (Array.isArray(value)) {
    const content = value.map(asPayment)
    return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 }
  }
  if (!isObject(value) || !Array.isArray(value.content)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const page = normalizePage(value as unknown as PageResponse<unknown> | SpringPage<unknown>)
  return { ...page, content: page.content.map(asPayment) }
}

function asInvoice(value: unknown): Invoice {
  const invoice = asWithId<Invoice>(value)
  if (typeof invoice.invoiceNumber !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return invoice
}

function asRefund(value: unknown): Refund {
  const refund = asWithId<Refund>(value)
  if (typeof refund.refundCode !== 'string' || typeof refund.status !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return refund
}

function asRefundList(value: unknown): Refund[] {
  if (!Array.isArray(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value.map(asRefund)
}

/**
 * Reads the refund quote. registrationId and a numeric suggestedAmount are required; eligible
 * defaults to true when the backend leaves it out, maxRefundable to the suggested amount.
 */
export function toRefundQuote(value: unknown): RefundQuote {
  if (!isObject(value) || typeof value.registrationId !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const suggestedAmount = amountOf(value.suggestedAmount)
  if (suggestedAmount === null || suggestedAmount < 0) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const maxRefundable = amountOf(value.maxRefundable)
  return {
    registrationId: value.registrationId,
    registrationCode: text(value.registrationCode),
    memberId: typeof value.memberId === 'number' ? value.memberId : null,
    memberCode: text(value.memberCode),
    memberFullName: text(value.memberFullName),
    serviceName: text(value.serviceName) ?? text(value.packageName),
    paymentCode: text(value.paymentCode),
    invoiceNumber: text(value.invoiceNumber),
    paymentStatus: text(value.paymentStatus),
    paidAmount: amountOf(value.paidAmount),
    priorRefundAmount: amountOf(value.priorRefundAmount),
    suggestedAmount,
    maxRefundable: maxRefundable !== null && maxRefundable >= 0 ? maxRefundable : suggestedAmount,
    eligible: value.eligible !== false,
    ineligibleReason: text(value.ineligibleReason),
  }
}

export interface PaymentListQuery extends RequestSignal {
  /** One of PAYMENT_STATUS_CODES; empty means every status. */
  status?: string
  /** Member name, member code or payment reference. */
  query?: string
  page?: number
  size?: number
}

export const receptionPaymentsApi = {
  /** GET /payments (BE-18). Empty filters are left out of the URL. */
  payments({ status, query, page = 0, size = 10, signal }: PaymentListQuery = {}): Promise<PageResponse<StaffPayment>> {
    return api
      .get<unknown>('/payments', { signal, query: { status: status || undefined, query: query || undefined, page, size } })
      .then(asPaymentPage)
  },

  payment(paymentId: number, options: RequestSignal = {}): Promise<StaffPayment> {
    return api.get<unknown>(`/payments/${paymentId}`, options).then(asPayment)
  },

  /** PUT /payments/{id}/confirm (BE-18): SUCCESS, activates the card or package, issues the invoice. */
  confirm(paymentId: number, request: ConfirmPaymentRequest): Promise<StaffPayment> {
    return api.put<unknown>(`/payments/${paymentId}/confirm`, request).then(asPayment)
  },

  /** PUT /payments/{id}/fail (BE-18): FAILED, the item stays unpaid. */
  fail(paymentId: number, request: FailPaymentRequest): Promise<StaffPayment> {
    return api.put<unknown>(`/payments/${paymentId}/fail`, request).then(asPayment)
  },

  /** GET /invoices/by-payment/{paymentId} (BE-18). */
  invoiceByPayment(paymentId: number, options: RequestSignal = {}): Promise<Invoice> {
    return api.get<unknown>(`/invoices/by-payment/${paymentId}`, options).then(asInvoice)
  },

  /** GET /refunds/quote?registrationId= (BE-21). */
  refundQuote(registrationId: number, options: RequestSignal = {}): Promise<RefundQuote> {
    return api.get<unknown>('/refunds/quote', { ...options, query: { registrationId } }).then(toRefundQuote)
  },

  /** POST /refunds: creates a PENDING refund for manager approval. */
  createRefund(request: CreateRefundRequest): Promise<Refund> {
    return api.post<unknown>('/refunds', request).then(asRefund)
  },

  /** GET /refunds/pending: every refund waiting for a manager decision. */
  pendingRefunds(options: RequestSignal = {}): Promise<Refund[]> {
    return api.get<unknown>('/refunds/pending', options).then(asRefundList)
  },

  /** GET /refunds/member/{memberId}: every refund of one member, all statuses. */
  memberRefunds(memberId: number, options: RequestSignal = {}): Promise<Refund[]> {
    return api.get<unknown>(`/refunds/member/${memberId}`, options).then(asRefundList)
  },

  /** PUT /refunds/{id}/complete: APPROVED -> COMPLETED, with the transfer evidence (BE-21). */
  completeRefund(refundId: number, refundReference: string): Promise<Refund> {
    return api.put<unknown>(`/refunds/${refundId}/complete`, { refundReference }).then(asRefund)
  },
}
