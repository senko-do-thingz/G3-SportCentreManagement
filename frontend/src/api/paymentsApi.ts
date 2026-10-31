import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'
import type { PageResponse } from './types'

/**
 * Payment and invoice endpoints for the member payment screens (F3-01, F3-02, F3-03 and the
 * receipts, FE-11).
 * - GET /payments/{id} and PUT /payments/{id}/method exist on main (PaymentController, BE-17).
 * - GET /payments/my and GET /invoices/{id} match BE-20 on main. History is a PageResponse,
 *   including server ordering and totals; a bare array cannot represent the full history.
 */

/** Payment statuses of com.sportify.payment.entity.PaymentStatus. */
export const PAYMENT_STATUSES = ['PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'] as const
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number]

/**
 * Methods a member may choose online: PaymentService.ONLINE_ALLOWED_PAYMENT_METHODS. CASH and
 * CREDIT_CARD are staff only and are rejected with a 400 for a member.
 */
export const ONLINE_PAYMENT_METHODS = ['BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY'] as const
export type OnlinePaymentMethod = (typeof ONLINE_PAYMENT_METHODS)[number]

/** Mirrors com.sportify.payment.dto.BankTransferInstructions. */
export interface BankTransferInstructions {
  accountName: string | null
  accountNumber: string | null
  bank: string | null
  transferContent: string | null
}

/** Mirrors com.sportify.payment.dto.PaymentResponse (GET /payments/{id}). */
export interface Payment {
  id: number
  code: string
  /** PENDING, SUCCESS, FAILED or REFUNDED. */
  status: string
  amount: number | string
  method: string | null
  itemDescription: string | null
  originalPrice: number | string | null
  /** Discount as an amount of money, not a percent. */
  discount: number | string | null
  paymentTime: string | null
  /** Only sent while the payment is PENDING and the method is BANK_TRANSFER. */
  bankTransferInstructions?: BankTransferInstructions | null
  memberId: number | null
  memberCode: string | null
  notes?: string | null
  createdAt: string | null
  updatedAt?: string | null
  /** BE-20 history fields; GET /payments/{id} does not include an invoice association. */
  invoiceId?: number | null
  invoiceNumber?: string | null
  referenceCode?: string | null
  settlementType?: string | null
  settlementProvider?: string | null
  simulatedTransactionId?: number | null
}

/** BE-20 InvoiceLineResponse. */
export interface InvoiceLine {
  id: number
  /** SPORT_PACKAGE, MEMBERSHIP_CARD, CLASS_DROP_IN or PENALTY_FEE. */
  itemType: string | null
  description: string
  quantity: number | null
  unitPrice: number | string | null
  lineTotal: number | string | null
}

/** BE-20 InvoiceResponse from GET /invoices/{id}. */
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
  taxAmount: number | string | null
  totalAmount: number | string
  /** ISSUED, PAID, CANCELLED or REFUNDED. */
  status: string
  issueDate: string | null
  settlementType?: string | null
  settlementProvider?: string | null
  simulatedTransactionId?: number | null
  lines?: InvoiceLine[] | null
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asPayment(value: unknown): Payment {
  if (!isObject(value) || typeof value.id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as Payment
}

/** Require pagination metadata rather than silently treating a partial result as all payments. */
function asPaymentPage(value: unknown): PageResponse<Payment> {
  if (!isObject(value) || !Array.isArray(value.content)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const { page, size, totalElements, totalPages } = value
  if (
    typeof page !== 'number' || !Number.isSafeInteger(page) || page < 0 ||
    typeof size !== 'number' || !Number.isSafeInteger(size) || size < 1 || size > 100 ||
    typeof totalElements !== 'number' || !Number.isSafeInteger(totalElements) || totalElements < 0 ||
    typeof totalPages !== 'number' || !Number.isSafeInteger(totalPages) || totalPages < 0 ||
    totalPages !== Math.ceil(totalElements / size) || value.content.length > size
  ) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return { content: value.content.map(asPayment), page, size, totalElements, totalPages }
}

function asInvoice(value: unknown): Invoice {
  if (!isObject(value) || typeof value.id !== 'number' || typeof value.invoiceNumber !== 'string') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as Invoice
}

export interface MyPaymentsQuery extends RequestSignal {
  /** One of PAYMENT_STATUSES; empty or missing means every status. */
  status?: string | null
  page?: number
  size?: number
}

export const paymentsApi = {
  payment(paymentId: number, options: RequestSignal = {}): Promise<Payment> {
    return api.get<unknown>(`/payments/${paymentId}`, options).then(asPayment)
  },

  /** PUT /payments/{id}/method with {paymentMethod}; only a PENDING payment can change. */
  updateMethod(paymentId: number, paymentMethod: string): Promise<Payment> {
    return api.put<unknown>(`/payments/${paymentId}/method`, { paymentMethod }).then(asPayment)
  },

  /** BE-20: zero-based page, size 1..100; omit status for All. */
  myPayments({ status, page = 0, size = 20, signal }: MyPaymentsQuery = {}): Promise<PageResponse<Payment>> {
    return api
      .get<unknown>('/payments/my', { signal, query: { status: status || undefined, page, size } })
      .then(asPaymentPage)
  },

  /** GET /invoices/{id} (BE-20). */
  invoice(invoiceId: number, options: RequestSignal = {}): Promise<Invoice> {
    return api.get<unknown>(`/invoices/${invoiceId}`, options).then(asInvoice)
  },
}
