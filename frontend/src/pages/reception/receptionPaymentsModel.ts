import { ApiError } from '../../api/errors'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import type { PaymentMethod } from '../../api/receptionApi'
import type { ConfirmPaymentRequest, Refund, StaffPayment } from '../../api/receptionPaymentsApi'
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

/**
 * Start of the note BE-18 writes on a payment when the desk enters an amount that does not match
 * (PaymentVerificationServiceImpl.recordMismatch). The payment stays PENDING.
 */
export const AMOUNT_MISMATCH_NOTE = 'Amount mismatch:'

/**
 * Strict pattern matching the machine-generated mismatch segment written by BE-18:
 * "Amount mismatch: received <amount>, expected <amount> by <method>..."
 * Must be at the start of notes or preceded by a segment separator (" | ").
 * This contract prevents arbitrary free text containing "Amount mismatch:" from misclassifying records.
 */
export const AMOUNT_MISMATCH_PATTERN = /(?:^|\s*\|\s*)Amount mismatch:\s+received\s+\d+/i

export function hasAmountMismatchNote(notes?: string | null): boolean {
  if (!notes) return false
  return AMOUNT_MISMATCH_PATTERN.test(notes)
}

/** A pending payment with a recorded amount mismatch needs review before anyone confirms it (UI-30). */
export function needsReview(row: { status: string; notes?: string | null }): boolean {
  return row.status === 'PENDING' && hasAmountMismatchNote(row.notes)
}

/** Badge of a queue row: Needs review for a pending payment with a mismatch, the payment status otherwise. */
export function queueStatus(row: { status: string; notes?: string | null }): { code: string | null; label: string } {
  return needsReview(row)
    ? { code: 'NEEDS_REVIEW', label: 'Needs review' }
    : { code: paymentStatusTone(row.status), label: paymentStatusLabel(row.status) }
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
export const NOTE_MAX = 255
export const FAIL_REASON_MAX = 255
export const REASON_MAX = 500

export const VERIFY_STATEMENT = 'I have verified the full amount was received'

/** BE-18/BE-21 accept two decimals; keep FE-05 whole-VND parsing unchanged. */
export function parsePaymentAmount(value: string): number | null {
  const trimmed = value.trim()
  // Do not reinterpret a sub-cent value such as 0.001 as a grouped whole-VND amount.
  const amount = /^\d+\.\d{1,2}$/.test(trimmed) ? Number(trimmed) : /^0[.,\s]/.test(trimmed) ? null : parseAmount(trimmed)
  return amount !== null && Number.isFinite(amount) && Number.isSafeInteger(Math.round(amount * 100)) ? amount : null
}

const PAYMENT_MONEY_FORMAT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

/** Retain cents from backend amounts without changing FE-05/FE-11 formatting. */
export function formatPaymentMoney(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || String(amount).trim() === '') return '-'
  const value = Number(amount)
  return Number.isFinite(value) ? `${PAYMENT_MONEY_FORMAT.format(value)} VND` : '-'
}

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

/** A usable amount due: a finite number of at least 0. Anything else is null, never NaN. */
export function validAmountDue(amount: number | string | null | undefined): number | null {
  if (amount === null || amount === undefined || String(amount).trim() === '') return null
  const value = typeof amount === 'number' ? amount : Number(amount)
  return Number.isFinite(value) && value >= 0 ? value : null
}

export const INVALID_AMOUNT_DUE_MESSAGE = 'The amount due of this payment is not valid. Reload the payment or ask a manager.'

/**
 * BE-18 confirms an existing payment: every method must match exactly. FE-05 desk sales use
 * their separate cash-change policy. Empty required fields also block the Confirm button.
 */
export function validateConfirmForm(form: ConfirmForm, amountDue: number | null): ConfirmErrors {
  const errors: ConfirmErrors = {}
  if (amountDue === null) {
    errors.amountReceived = INVALID_AMOUNT_DUE_MESSAGE
  } else {
    const received = parsePaymentAmount(form.amountReceived)
    if (!form.amountReceived.trim()) {
      errors.amountReceived = 'Amount received is required'
    } else if (received === null || received <= 0 || received >= 10000000000) {
      errors.amountReceived = 'Amount received must be above 0 with at most 10 integer digits and 2 decimals'
    } else if (received !== amountDue) {
      errors.amountReceived = 'Amount received must equal the amount due for every payment method'
    }
  }
  if (referenceRequired(form.method) && !form.reference.trim()) errors.reference = 'Reference code is required for this method'
  if (!errors.method && !isPaymentMethod(form.method)) {
    errors.method = 'Choose a payment method'
  }
  if (!errors.reference && form.reference.trim().length > REFERENCE_MAX) {
    errors.reference = `Reference code must be at most ${REFERENCE_MAX} characters`
  }
  if (form.note.trim().length > NOTE_MAX) {
    errors.note = `Note must be at most ${NOTE_MAX} characters`
  }
  return errors
}

/** Confirm stays disabled until the checkbox is ticked and every rule passes. */
export function canConfirm(form: ConfirmForm, amountDue: number | null): boolean {
  return form.verified && Object.keys(validateConfirmForm(form, amountDue)).length === 0
}

/**
 * Request body built from a valid BE-18 confirmation. Jackson explicitly supports these alias
 * names. Cash without a reference sends null; amounts are never rounded before serialization.
 */
export function toConfirmRequest(form: ConfirmForm): ConfirmPaymentRequest {
  const reference = form.reference.trim()
  const note = form.note.trim()
  return {
    paymentMethod: form.method as PaymentMethod,
    paymentReference: form.method === 'CASH' ? reference || null : reference,
    amountReceived: parsePaymentAmount(form.amountReceived) ?? 0,
    note: note || null,
  }
}

export type AmountCheck = 'empty' | 'match' | 'short' | 'over'

/** Compares the amount typed with the amount due, for the Amount verification panel. */
export function amountCheck(amountDue: number | null, amountReceived: string): AmountCheck {
  const received = parsePaymentAmount(amountReceived)
  if (received === null || amountDue === null) return 'empty'
  if (received === amountDue) return 'match'
  return received < amountDue ? 'short' : 'over'
}

/** Received minus due; null while either amount is not a valid number. */
export function amountDifference(amountDue: number | null, amountReceived: string): number | null {
  const received = parsePaymentAmount(amountReceived)
  return received === null || amountDue === null ? null : Math.round((received - amountDue) * 100) / 100
}

/**
 * Any underpayment or overpayment requires review, including cash.
 */
export function isAmountToReview(form: Pick<ConfirmForm, 'method' | 'amountReceived'>, amountDue: number | null): boolean {
  const check = amountCheck(amountDue, form.amountReceived)
  return check === 'short' || check === 'over'
}

export const AMOUNT_MISMATCH_CODE = 'PAYMENT_AMOUNT_MISMATCH'

/** The 409 of PUT /payments/{id}/confirm that keeps the payment PENDING (BE-18). */
export function isAmountMismatch(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 409) return false
  return error.code === AMOUNT_MISMATCH_CODE
}

/** Amounts shown on S3-Mismatch; the backend values win over the typed one when it sends them. */
export interface MismatchAmounts {
  message?: string
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
  const received = detailAmount(details, 'amountReceived') ?? parsePaymentAmount(typed) ?? 0
  return {
    amountDue: due, amountReceived: received, outstanding: Math.round((due - received) * 100) / 100,
    ...(error instanceof ApiError ? { message: error.message } : {}),
  }
}

function moneyOf(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || String(value).trim() === '') return null
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

function sameMoney(a: number, b: number): boolean {
  return Math.abs(a - b) < 0.005
}

/**
 * Why a SUCCESS payment must not be shown as a clean success (UI-31): the amount received or the
 * invoice total differs from the amount due. Null when every known amount agrees. BE-18 rejects a
 * mismatch, so this only catches inconsistent data, which must never look like a confirmed payment.
 */
export function confirmedAmountProblem(
  payment: Pick<StaffPayment, 'amount' | 'amountReceived'>,
  invoiceTotal?: number | string | null,
): string | null {
  const due = moneyOf(payment.amount)
  if (due === null) return INVALID_AMOUNT_DUE_MESSAGE
  const received = moneyOf(payment.amountReceived)
  if (received !== null && !sameMoney(received, due)) {
    return `The amount received (${formatPaymentMoney(received)}) differs from the amount due (${formatPaymentMoney(due)}). Do not hand over a receipt; ask a manager.`
  }
  const total = moneyOf(invoiceTotal)
  if (total !== null && !sameMoney(total, due)) {
    return `The invoice total (${formatPaymentMoney(total)}) differs from the amount due (${formatPaymentMoney(due)}). Do not hand over a receipt; ask a manager.`
  }
  return null
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
  const amount = parsePaymentAmount(form.amount)
  if (form.amount.trim() === '') {
    errors.amount = 'Refund amount is required'
  } else if (amount === null || amount <= 0 || amount >= 1000000000000) {
    errors.amount = 'Refund amount must be above 0 with at most 12 integer digits and 2 decimals'
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

/** BE-21 only accepts ACTIVE registrations with a payment. The quote checks the remaining rules. */
export function refundableRegistrations(registrations: PackageRegistration[]): PackageRegistration[] {
  return registrations.filter((item) => item.paymentId != null && item.status === 'ACTIVE')
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

/** History excludes pending/approved refunds, which each have a dedicated queue. */
export function memberRefundHistory(refunds: Refund[]): Refund[] {
  return sortRefunds(refunds.filter((refund) => refund.status !== 'PENDING' && refund.status !== 'APPROVED'))
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

/** Newest first, so the queue shows the latest requests at the top. */
export function sortRefunds(refunds: Refund[]): Refund[] {
  return [...refunds].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
}
