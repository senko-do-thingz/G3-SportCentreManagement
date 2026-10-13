import { ApiError } from '../../api/errors'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import type { PaymentMethod } from '../../api/receptionApi'
import type { ConfirmPaymentRequest, Refund } from '../../api/receptionPaymentsApi'
import { humanizeCode } from '../../utils/format'
import { isPaymentMethod, parseAmount } from './receptionModel'

/**
 * Rules of the receptionist payment screens (FE-12): Payment Requests (F3-04), Verify and Record
 * Payment (F3-05) with S3-Mismatch, Payment Confirmed and Receipt (F3-06, S3-Receipt) and Refund
 * Requests (F3-07) with S3-RefundSubmitted, S3-RefundApproved and S3-RefundDone. No React here.
 */

export const PAYMENT_LIST_PATH = '/reception/payments'
export const REFUND_LIST_PATH = '/reception/refunds'
export const PAYMENT_PAGE_SIZE = 10

export function paymentReceiptPath(paymentId: number): string {
  return `${PAYMENT_LIST_PATH}/${paymentId}/receipt`
}

export function refundDetailPath(refundId: number, memberId?: number | null): string {
  const base = `${REFUND_LIST_PATH}/${refundId}`
  return memberId ? `${base}?memberId=${memberId}` : base
}

export function refundCreatePath(memberId: number, registrationId?: number): string {
  const params = new URLSearchParams({ memberId: String(memberId) })
  if (registrationId !== undefined) params.set('registrationId', String(registrationId))
  return `${REFUND_LIST_PATH}?${params.toString()}`
}

/* Payment Requests (F3-04) */

/** Value of the status filter that lists every status. */
export const ALL_STATUSES = 'ALL'

export const PAYMENT_STATUS_FILTER_OPTIONS = [
  { value: 'PENDING', label: 'Pending payment' },
  { value: 'SUCCESS', label: 'Paid' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
  { value: ALL_STATUSES, label: 'All statuses' },
]

export interface PaymentListParams {
  /** A status code, or ALL_STATUSES. PENDING when the URL has no valid status. */
  status: string
  query: string
  page: number
}

export const QUERY_MAX = 100

/** Reads ?status=&q=&page= from the URL; unknown values fall back to the defaults. */
export function readPaymentListParams(params: URLSearchParams): PaymentListParams {
  const rawStatus = (params.get('status') ?? '').toUpperCase()
  const status = PAYMENT_STATUS_FILTER_OPTIONS.some((option) => option.value === rawStatus) ? rawStatus : 'PENDING'
  const query = (params.get('q') ?? '').trim().slice(0, QUERY_MAX)
  const rawPage = params.get('page')
  const page = rawPage && /^\d+$/.test(rawPage) ? Math.min(Number(rawPage), 10000) : 0
  return { status, query, page }
}

/** Writes the list parameters back; defaults are left out so the plain path is the default list. */
export function writePaymentListParams(value: PaymentListParams): URLSearchParams {
  const params = new URLSearchParams()
  if (value.status !== 'PENDING') params.set('status', value.status)
  if (value.query) params.set('q', value.query)
  if (value.page > 0) params.set('page', String(value.page))
  return params
}

/** The status sent to GET /payments: empty for every status. */
export function apiStatus(status: string): string {
  return status === ALL_STATUSES ? '' : status
}

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending payment',
  SUCCESS: 'Paid',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
}

export function paymentStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown'
  return PAYMENT_STATUS_LABELS[status] ?? humanizeCode(status)
}

/** Badge code for a payment status: SUCCESS takes the green tone of PAID. */
export function paymentStatusTone(status: string | null | undefined): string | null {
  return status === 'SUCCESS' ? 'PAID' : (status ?? null)
}

/** "Taylor Nguyen / MB-0246", with whatever part is known. */
export function memberLine(fullName: string | null | undefined, memberCode: string | null | undefined, memberId?: number | null): string {
  const parts = [fullName, memberCode].filter((part): part is string => typeof part === 'string' && part.trim() !== '')
  if (parts.length > 0) return parts.join(' / ')
  return memberId ? `Member #${memberId}` : '-'
}

/** Sum of the amounts of the PENDING rows on the page, for the "Amount due" stat card. */
export function pendingTotal(rows: { status: string; amount: number | string | null }[]): number {
  return rows.reduce((total, row) => {
    const value = Number(row.amount)
    return row.status === 'PENDING' && Number.isFinite(value) ? total + value : total
  }, 0)
}

/* Verify and Record Payment (F3-05) */

export interface ConfirmForm {
  method: string
  reference: string
  amountReceived: string
  note: string
  verified: boolean
}

export const REFERENCE_MAX = 100
export const NOTE_MAX = 500
export const REASON_MAX = 500

export const VERIFY_STATEMENT = 'I have verified the full amount was received'

/** The method of the payment when it is a known one, bank transfer otherwise (online requests). */
export function initialConfirmForm(method: string | null | undefined): ConfirmForm {
  return {
    method: method && isPaymentMethod(method) ? method : 'BANK_TRANSFER',
    reference: '',
    amountReceived: '',
    note: '',
    verified: false,
  }
}

export function referenceRequired(method: string): boolean {
  return method !== 'CASH'
}

export type ConfirmErrors = Partial<Record<'method' | 'reference' | 'amountReceived' | 'note', string>>

/**
 * Every field rule of the confirm form. Required fields that are still empty are errors too, so
 * the Confirm button can use the same result; the screen shows only the errors of touched fields.
 */
export function validateConfirmForm(form: ConfirmForm): ConfirmErrors {
  const errors: ConfirmErrors = {}
  if (!isPaymentMethod(form.method)) {
    errors.method = 'Choose a payment method'
  }
  const reference = form.reference.trim()
  if (referenceRequired(form.method) && !reference) {
    errors.reference = 'Reference code is required for this method'
  } else if (reference.length > REFERENCE_MAX) {
    errors.reference = `Reference code must be at most ${REFERENCE_MAX} characters`
  }
  const amount = parseAmount(form.amountReceived)
  if (form.amountReceived.trim() === '') {
    errors.amountReceived = 'Amount received is required'
  } else if (amount === null || amount <= 0) {
    errors.amountReceived = 'Amount received must be a whole number of VND above 0'
  }
  if (form.note.trim().length > NOTE_MAX) {
    errors.note = `Note must be at most ${NOTE_MAX} characters`
  }
  return errors
}

/** Confirm stays disabled until the checkbox is ticked and every required field is valid. */
export function canConfirm(form: ConfirmForm): boolean {
  return form.verified && Object.keys(validateConfirmForm(form)).length === 0
}

/** Request body built from a valid form. Cash sends no reference. */
export function toConfirmRequest(form: ConfirmForm): ConfirmPaymentRequest {
  const reference = form.reference.trim()
  const note = form.note.trim()
  return {
    paymentMethod: form.method as PaymentMethod,
    paymentReference: form.method === 'CASH' ? reference || null : reference,
    amountReceived: parseAmount(form.amountReceived) ?? 0,
    note: note || null,
  }
}

export type AmountCheck = 'empty' | 'match' | 'short' | 'over'

/** Compares the amount typed with the amount due, for the Amount verification panel. */
export function amountCheck(amountDue: number, amountReceived: string): AmountCheck {
  const received = parseAmount(amountReceived)
  if (received === null) return 'empty'
  if (received === amountDue) return 'match'
  return received < amountDue ? 'short' : 'over'
}

/** Received minus due; null while the amount is not a valid number. */
export function amountDifference(amountDue: number, amountReceived: string): number | null {
  const received = parseAmount(amountReceived)
  return received === null ? null : received - amountDue
}

export const AMOUNT_MISMATCH_CODE = 'PAYMENT_AMOUNT_MISMATCH'

/** The 409 of PUT /payments/{id}/confirm that keeps the payment PENDING (BE-18). */
export function isAmountMismatch(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 409) return false
  return error.code === AMOUNT_MISMATCH_CODE || error.message.includes(AMOUNT_MISMATCH_CODE)
}

/** Amounts shown on S3-Mismatch; the backend values win over the typed one when it sends them. */
export interface MismatchAmounts {
  amountDue: number
  amountReceived: number
  /** Due minus received: positive is still owed, negative is paid too much. */
  outstanding: number
}

function detailAmount(details: unknown, key: string): number | null {
  if (!details || typeof details !== 'object') return null
  const value = (details as Record<string, unknown>)[key]
  const amount = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN
  return Number.isFinite(amount) ? amount : null
}

export function mismatchAmounts(amountDue: number, typed: string, error?: unknown): MismatchAmounts {
  const details = error instanceof ApiError ? error.details : undefined
  const due = detailAmount(details, 'amountDue') ?? amountDue
  const received = detailAmount(details, 'amountReceived') ?? parseAmount(typed) ?? 0
  return { amountDue: due, amountReceived: received, outstanding: due - received }
}

/* Payment Confirmed (F3-06): the card or package the payment activated */

export type PaidItem =
  | { kind: 'package'; item: PackageRegistration }
  | { kind: 'card'; item: MemberCard }

/** Finds the registration or card linked to the payment among the member's items. */
export function findPaidItem(paymentId: number, cards: MemberCard[], registrations: PackageRegistration[]): PaidItem | null {
  const registration = registrations.find((item) => item.paymentId === paymentId)
  if (registration) return { kind: 'package', item: registration }
  const card = cards.find((item) => item.paymentId === paymentId)
  return card ? { kind: 'card', item: card } : null
}

/* Refund Requests (F3-07) */

export interface RefundForm {
  amount: string
  reason: string
}

export type RefundErrors = Partial<Record<keyof RefundForm, string>>

export function validateRefundForm(form: RefundForm, maxRefundable: number): RefundErrors {
  const errors: RefundErrors = {}
  const amount = parseAmount(form.amount)
  if (form.amount.trim() === '') {
    errors.amount = 'Refund amount is required'
  } else if (amount === null || amount <= 0) {
    errors.amount = 'Refund amount must be a whole number of VND above 0'
  } else if (amount > maxRefundable) {
    errors.amount = 'Refund amount is more than the refundable balance'
  }
  const reason = form.reason.trim()
  if (!reason) {
    errors.reason = 'Reason is required'
  } else if (reason.length > REASON_MAX) {
    errors.reason = `Reason must be at most ${REASON_MAX} characters`
  }
  return errors
}

/** Registrations that can be refunded: paid ones (ACTIVE or EXPIRED) with a payment. */
export function refundableRegistrations(registrations: PackageRegistration[]): PackageRegistration[] {
  return registrations.filter((item) => item.paymentId !== null && (item.status === 'ACTIVE' || item.status === 'EXPIRED'))
}

export type RefundStage = 'submitted' | 'approved' | 'done' | 'rejected' | 'other'

/** Which Figma state a refund is in: S3-RefundSubmitted, S3-RefundApproved or S3-RefundDone. */
export function refundStage(status: string): RefundStage {
  switch (status) {
    case 'PENDING':
      return 'submitted'
    case 'APPROVED':
      return 'approved'
    case 'COMPLETED':
      return 'done'
    case 'REJECTED':
      return 'rejected'
    default:
      return 'other'
  }
}

const REFUND_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending approval',
  APPROVED: 'Approved - not yet refunded',
  COMPLETED: 'Refunded',
  REJECTED: 'Rejected',
}

export function refundStatusLabel(status: string): string {
  return REFUND_STATUS_LABELS[status] ?? humanizeCode(status)
}

/** Original payment shown on the refund: invoice number first, then the payment code. */
export function refundOriginal(refund: Pick<Refund, 'invoiceNumber' | 'paymentCode' | 'registrationCode'>): string {
  return refund.invoiceNumber ?? refund.paymentCode ?? refund.registrationCode ?? '-'
}

/** Amount actually returned: the approved amount, or the requested one when none was set. */
export function refundAmount(refund: Pick<Refund, 'amountApproved' | 'amountRequested'>): number | string | null {
  return refund.amountApproved ?? refund.amountRequested
}

export const REFUND_REFERENCE_MAX = 100

export function validateRefundReference(value: string): string | null {
  const reference = value.trim()
  if (!reference) return 'Refund transaction reference is required'
  if (reference.length > REFUND_REFERENCE_MAX) {
    return `Refund transaction reference must be at most ${REFUND_REFERENCE_MAX} characters`
  }
  return null
}

/** Newest first, so the queue shows the latest requests at the top. */
export function sortRefunds(refunds: Refund[]): Refund[] {
  return [...refunds].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
}
