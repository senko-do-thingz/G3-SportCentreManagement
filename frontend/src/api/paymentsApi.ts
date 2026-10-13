import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'

/**
 * Payment and invoice endpoints for the member payment screens (F3-01, F3-02, F3-03 and the
 * receipts, FE-11).
 * - GET /payments/{id} and PUT /payments/{id}/method exist on main (PaymentController, BE-17).
 * - GET /payments/my and GET /invoices/{id} are task BE-20 and are not on main yet. The shapes
 *   below are the contract the frontend assumes; the parsers accept a plain array or a page for the
 *   list and ignore unknown fields, so small differences in BE-20 do not break the screens.
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
  /** Assumed BE-20 fields: the invoice issued when the payment was confirmed, null before. */
  invoiceId?: number | null
  invoiceNumber?: string | null
}

/** Assumed BE-20 shape of one invoice_line row. */
export interface InvoiceLine {
  id: number
  /** SPORT_PACKAGE, MEMBERSHIP_CARD, CLASS_DROP_IN or PENALTY_FEE. */
  itemType: string | null
  description: string
  quantity: number | null
  unitPrice: number | string | null
  lineTotal: number | string | null
}

/** Assumed BE-20 shape of GET /invoices/{id}, built from the V14 invoice columns. */
export interface Invoice {
  id: number
  invoiceNumber: string
  paymentId: number | null
  paymentCode?: string | null
  paymentMethod?: string | null
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

/** Accepts a JSON array or a page ({content: [...]}) and checks every row. */
function asPaymentList(value: unknown): Payment[] {
  const rows = Array.isArray(value) ? value : isObject(value) && Array.isArray(value.content) ? value.content : null
  if (!rows) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return rows.map(asPayment)
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
}

export const paymentsApi = {
  payment(paymentId: number, options: RequestSignal = {}): Promise<Payment> {
    return api.get<unknown>(`/payments/${paymentId}`, options).then(asPayment)
  },

  /** PUT /payments/{id}/method with {paymentMethod}; only a PENDING payment can change. */
  updateMethod(paymentId: number, paymentMethod: string): Promise<Payment> {
    return api.put<unknown>(`/payments/${paymentId}/method`, { paymentMethod }).then(asPayment)
  },

  /** GET /payments/my?status= (BE-20). The status is left out of the URL when it is empty. */
  myPayments({ status, signal }: MyPaymentsQuery = {}): Promise<Payment[]> {
    return api
      .get<unknown>('/payments/my', { signal, query: status ? { status } : undefined })
      .then(asPaymentList)
  },

  /** GET /invoices/{id} (BE-20). */
  invoice(invoiceId: number, options: RequestSignal = {}): Promise<Invoice> {
    return api.get<unknown>(`/invoices/${invoiceId}`, options).then(asInvoice)
  },
}
