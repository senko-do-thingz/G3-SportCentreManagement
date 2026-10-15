import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import { PAYMENT_STATUSES, type Payment } from '../../api/paymentsApi'
import type { SelectOption } from '../../components/ui'
import { formatDate } from '../../utils/format'
import { termDays } from './cardsModel'

/**
 * Rules and labels shared by the member payment screens (FE-11): Payment Summary (F3-01 and
 * S3-Single), Payment Instructions and Status (F3-02), My Payments and Receipts (F3-03 and
 * S3-PaidHistory) and the receipts (S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt).
 */

/** The status page asks the backend again every 15 seconds while the payment is PENDING. */
export const POLL_INTERVAL_MS = 15000

export const PAYMENT_PATHS = {
  list: '/member/payments',
  summary: (paymentId: number) => `/member/payments/${paymentId}`,
  status: (paymentId: number) => `/member/payments/${paymentId}/status`,
  receipt: (invoiceId: number) => `/member/payments/receipts/${invoiceId}`,
} as const

/**
 * Methods a member can pay with online today. The backend also accepts MOMO, VNPAY and ZALOPAY,
 * but none of them has a checkout yet, so the summary shows them disabled ("Not available yet").
 */
export const AVAILABLE_ONLINE_METHODS: readonly string[] = ['BANK_TRANSFER']

/** Method preselected on the summary; Continue switches any other method to it. */
export const DEFAULT_PAYMENT_METHOD = 'BANK_TRANSFER'

export function isMethodAvailable(method: string): boolean {
  return AVAILABLE_ONLINE_METHODS.includes(method)
}

const METHOD_LABELS: Record<string, string> = {
  BANK_TRANSFER: 'Bank transfer',
  MOMO: 'MoMo',
  VNPAY: 'VNPay',
  ZALOPAY: 'ZaloPay',
  CASH: 'Cash',
  CREDIT_CARD: 'Credit card',
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  SUCCESS: 'Paid',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
}

/** Badge tones of the payment statuses; SUCCESS is not in the shared status map. */
export const PAYMENT_STATUS_TONE_CODES: Record<string, string> = {
  PENDING: 'PENDING',
  SUCCESS: 'PAID',
  FAILED: 'FAILED',
  REFUNDED: 'REFUNDED',
}

export function methodLabel(method: string | null | undefined): string {
  if (!method) {
    return 'Not chosen'
  }
  return METHOD_LABELS[method] ?? method
}

/** "Paid" for SUCCESS, as in Figma F3-03; other codes keep their own word. */
export function paymentStatusLabel(status: string | null | undefined): string {
  if (!status) {
    return 'Unknown'
  }
  return STATUS_LABELS[status] ?? status
}

/** Code given to StatusBadge so that SUCCESS gets the green tone of PAID. */
export function paymentStatusToneCode(status: string | null | undefined): string | null {
  if (!status) {
    return null
  }
  return PAYMENT_STATUS_TONE_CODES[status] ?? status
}

/** A route id such as "52"; anything that is not a positive whole number is null. */
export function parseIdParam(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null
  }
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

/** PENDING is the only status that can still change by itself; polling stops on every other. */
export function isPollingStatus(status: string | null | undefined): boolean {
  return status === 'PENDING'
}

export function isPending(payment: Pick<Payment, 'status'>): boolean {
  return payment.status === 'PENDING'
}

/** Filter options of My Payments: All first, then the backend statuses. */
export const STATUS_FILTER_OPTIONS: SelectOption[] = PAYMENT_STATUSES.map((status) => ({
  value: status,
  label: paymentStatusLabel(status),
}))

/** Reads ?status= from the URL; anything that is not a known status means all statuses. */
export function statusFilterFromParam(value: string | null): string {
  if (!value) {
    return ''
  }
  const upper = value.toUpperCase()
  return (PAYMENT_STATUSES as readonly string[]).includes(upper) ? upper : ''
}

/** URL values must fit the backend's int page and its size limit (ReceiptService). */
export function historyPageFromParam(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 0
  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2147483647 ? page : 0
}

export function historySizeFromParam(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 20
  const size = Number(value)
  return Number.isSafeInteger(size) && size >= 1 && size <= 100 ? size : 20
}

/** Text put on the clipboard for the account number: digits and letters only, no spaces. */
export function accountNumberToCopy(accountNumber: string | null | undefined): string {
  return (accountNumber ?? '').replace(/\s+/g, '')
}

/** The transfer content is the payment code (PaymentServiceImpl.mapToPaymentResponse). */
export function transferContentToCopy(payment: Pick<Payment, 'code' | 'bankTransferInstructions'>): string {
  return (payment.bankTransferInstructions?.transferContent ?? payment.code).trim()
}

/** Plain banking amount, retaining the backend's supported fractional VND. */
export function amountToCopy(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') return ''
  const value = typeof amount === 'number' ? amount : Number(amount)
  return Number.isFinite(value) ? String(value) : ''
}

const PAYMENT_MONEY_FORMAT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

/** BE-17 accepts up to two decimal places; keep them on member payment screens. */
export function formatPaymentMoney(amount: number | string | null | undefined): string {
  const plain = amountToCopy(amount)
  return plain === '' ? '-' : `${PAYMENT_MONEY_FORMAT.format(Number(plain))} VND`
}

/** The registration paid by this payment, if the member has one. */
export function registrationForPayment(
  registrations: PackageRegistration[],
  paymentId: number,
): PackageRegistration | null {
  return registrations.find((item) => item.paymentId === paymentId) ?? null
}

/** The membership card bought with this payment, if any. */
export function cardForPayment(cards: MemberCard[], paymentId: number): MemberCard | null {
  return cards.find((item) => item.paymentId === paymentId) ?? null
}

/**
 * A registration of one day is a single visit (packagesModel.SINGLE_VISIT_MAX_DAYS): its price
 * never gets a card discount, and Figma S3-Single shows it with its own title.
 */
export function isSingleVisitRegistration(
  registration: Pick<PackageRegistration, 'startDate' | 'endDate'> | null,
): boolean {
  if (!registration) {
    return false
  }
  const days = termDays(registration.startDate, registration.endDate)
  return days !== null && days <= 1
}

/** "02 Oct 2026 - 31 Oct 2026", or "-" pieces when a date is missing. */
export function validityRange(startDate: string | null, endDate: string | null): string {
  return `${formatDate(startDate)} - ${formatDate(endDate)}`
}

/** Discount percent shown next to the discount line, from the amounts, or null when none. */
export function discountPercent(
  originalPrice: number | string | null | undefined,
  discount: number | string | null | undefined,
): number | null {
  const original = Number(originalPrice ?? 0)
  const off = Number(discount ?? 0)
  if (!Number.isFinite(original) || !Number.isFinite(off) || original <= 0 || off <= 0) {
    return null
  }
  return Math.round((off / original) * 100)
}
