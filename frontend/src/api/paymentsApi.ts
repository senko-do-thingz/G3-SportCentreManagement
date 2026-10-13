import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type RequestSignal } from './memberApi'

/**
 * Payment endpoints of PaymentController (BE-17). FE-03 uses the read only summary; FE-11 adds the
 * payment method change and the status screens here.
 */

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
  bankTransferInstructions?: BankTransferInstructions | null
  memberId: number | null
  memberCode: string | null
  createdAt: string | null
}

function asPayment(value: unknown): Payment {
  if (!value || typeof value !== 'object' || typeof (value as Payment).id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as Payment
}

export const paymentsApi = {
  payment(paymentId: number, options: RequestSignal = {}): Promise<Payment> {
    return api.get<unknown>(`/payments/${paymentId}`, options).then(asPayment)
  },
}
