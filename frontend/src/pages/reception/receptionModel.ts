import type { CardTier } from '../../api/cardsApi'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'
import type { CheckInRecord, PaymentMethod } from '../../api/receptionApi'
import { PAYMENT_METHODS } from '../../api/receptionApi'
import { parseDate } from '../../utils/format'
import { isFreeTier, planCard, type CardPlan, type CardPlanKind } from '../member/cardsModel'

/** Rules and helpers of the receptionist screens (FE-05). No React here, so everything is unit tested. */

export const RECEPTION_PATHS = {
  search: '/reception',
  register: '/reception/register',
  checkIn: '/reception/check-in',
} as const

export type RegisterTab = 'package' | 'card' | 'pending'
export const REGISTER_TABS: readonly RegisterTab[] = ['package', 'card', 'pending']

export function isRegisterTab(value: string | null): value is RegisterTab {
  return value !== null && (REGISTER_TABS as readonly string[]).includes(value)
}

export function memberSearchPath(memberId: number): string {
  return `${RECEPTION_PATHS.search}?memberId=${memberId}`
}

/**
 * Router state handed to the profile after a confirmed payment (After Payment state of F1-13).
 * It travels in navigation state, not in the URL, so a typed link cannot show a fake notice.
 */
export interface AfterPaymentState {
  paidReference: string
}

export function afterPaymentReference(state: unknown): string | null {
  if (state && typeof state === 'object' && typeof (state as AfterPaymentState).paidReference === 'string') {
    return (state as AfterPaymentState).paidReference
  }
  return null
}

export function registerPath(options: { memberId?: number; tab?: RegisterTab; packageId?: number } = {}): string {
  const params = new URLSearchParams()
  if (options.tab) params.set('tab', options.tab)
  if (options.memberId !== undefined) params.set('memberId', String(options.memberId))
  if (options.packageId !== undefined) params.set('packageId', String(options.packageId))
  const search = params.toString()
  return search ? `${RECEPTION_PATHS.register}?${search}` : RECEPTION_PATHS.register
}

export function checkInPath(memberCode?: string | null): string {
  return memberCode ? `${RECEPTION_PATHS.checkIn}?code=${encodeURIComponent(memberCode)}` : RECEPTION_PATHS.checkIn
}

export type PaymentItemKind = 'card' | 'package'

/**
 * Verify and Record Payment and receipt screen (F3-05, F3-06). Since FE-12 the payment is confirmed
 * through PUT /payments/{id}/confirm, so the item kind and id only describe where the link came
 * from; memberId adds the way back to the profile.
 */
export function receptionPaymentPath(
  paymentId: number,
  item?: { kind: PaymentItemKind; id: number; memberId?: number | null },
): string {
  const base = `/reception/payments/${paymentId}`
  if (!item) {
    return base
  }
  const params = new URLSearchParams({ item: item.kind, itemId: String(item.id) })
  if (item.memberId) params.set('memberId', String(item.memberId))
  return `${base}?${params.toString()}`
}

/** Reads a positive integer from a URL parameter; anything else is null. */
export function positiveId(value: string | null | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null
  }
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  CREDIT_CARD: 'Card (POS)',
  BANK_TRANSFER: 'Bank transfer',
  MOMO: 'MoMo',
  VNPAY: 'VNPay',
  ZALOPAY: 'ZaloPay',
}

export const PAYMENT_METHOD_OPTIONS = PAYMENT_METHODS.map((value) => ({ value, label: PAYMENT_METHOD_LABELS[value] }))

export function isPaymentMethod(value: string): value is PaymentMethod {
  return (PAYMENT_METHODS as readonly string[]).includes(value)
}

/* Create member (F1-13 overlay) */

export interface CreateMemberForm {
  fullName: string
  phone: string
  email: string
  dateOfBirth: string
  mainGoal: string
  currentLevel: string
}

export const EMPTY_MEMBER_FORM: CreateMemberForm = {
  fullName: '',
  phone: '',
  email: '',
  dateOfBirth: '',
  mainGoal: 'IMPROVE_FITNESS',
  currentLevel: 'BEGINNER',
}

export const MEMBER_FULL_NAME_MAX = 100
const PHONE_PATTERN = /^0\d{9,10}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export const GOAL_OPTIONS = [
  { value: 'IMPROVE_FITNESS', label: 'Health and fitness' },
  { value: 'BUILD_SKILLS', label: 'Build skills' },
  { value: 'COMPETITION', label: 'Competition' },
]

export const LEVEL_OPTIONS = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
]

export type MemberFormErrors = Partial<Record<keyof CreateMemberForm, string>>

export function validateMemberForm(form: CreateMemberForm, today: string): MemberFormErrors {
  const errors: MemberFormErrors = {}
  const fullName = form.fullName.trim()
  if (!fullName) {
    errors.fullName = 'Full name is required'
  } else if (fullName.length > MEMBER_FULL_NAME_MAX) {
    errors.fullName = `Full name must be at most ${MEMBER_FULL_NAME_MAX} characters`
  }
  const phone = form.phone.trim()
  if (!phone) {
    errors.phone = 'Phone number is required'
  } else if (!PHONE_PATTERN.test(phone)) {
    errors.phone = 'Phone number must start with 0 and have 10 or 11 digits'
  }
  const email = form.email.trim()
  if (!email) {
    errors.email = 'Email is required'
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = 'Email is invalid'
  }
  if (form.dateOfBirth) {
    if (!parseDate(form.dateOfBirth)) {
      errors.dateOfBirth = 'Date of birth is invalid'
    } else if (form.dateOfBirth >= today) {
      errors.dateOfBirth = 'Date of birth must be before today'
    }
  }
  return errors
}

/* Desk payment (BE-19) */

export interface DeskPaymentForm {
  method: string
  reference: string
  amountReceived: string
}

export const EMPTY_DESK_PAYMENT: DeskPaymentForm = { method: 'CASH', reference: '', amountReceived: '' }

export type DeskPaymentErrors = Partial<Record<keyof DeskPaymentForm, string>>

/**
 * Parses an amount typed at the desk, in whole VND: plain digits ("810000"), or digits grouped in
 * threes with one kind of separator ("810.000", "1,000,000", "1 000 000"). Decimals such as
 * "200000.00" or "1.5" and mixed separators such as "1,000.000" are refused (null).
 */
const PLAIN_AMOUNT = /^\d+$/
const GROUPED_AMOUNT = /^\d{1,3}(?:([.,\s])\d{3})(?:\1\d{3})*$/

export function parseAmount(value: string): number | null {
  const trimmed = value.trim()
  if (!PLAIN_AMOUNT.test(trimmed) && !GROUPED_AMOUNT.test(trimmed)) {
    return null
  }
  const amount = Number(trimmed.replace(/[.,\s]/g, ''))
  return Number.isSafeInteger(amount) ? amount : null
}

/**
 * Cash may be more than the amount payable (the desk gives change). Any other method records
 * exactly the amount payable and needs the transaction reference.
 */
export function validateDeskPayment(form: DeskPaymentForm, amountDue: number): DeskPaymentErrors {
  const errors: DeskPaymentErrors = {}
  if (!isPaymentMethod(form.method)) {
    errors.method = 'Choose a payment method'
    return errors
  }
  if (form.method !== 'CASH' && !form.reference.trim()) {
    errors.reference = 'Payment reference is required for this method'
  }
  const amount = parseAmount(form.amountReceived)
  if (form.amountReceived.trim() === '') {
    errors.amountReceived = 'Amount received is required'
  } else if (amount === null) {
    errors.amountReceived = 'Amount received must be a whole number of VND'
  } else if (amount < amountDue) {
    errors.amountReceived = 'Amount received is less than the amount payable'
  } else if (form.method !== 'CASH' && amount !== amountDue) {
    errors.amountReceived = 'Amount received must equal the amount payable for this method'
  }
  return errors
}

/** Change to give back for a cash payment, 0 otherwise. */
export function changeDue(form: DeskPaymentForm, amountDue: number): number {
  const amount = parseAmount(form.amountReceived)
  if (form.method !== 'CASH' || amount === null) {
    return 0
  }
  return Math.max(0, amount - amountDue)
}

/* Membership card at the desk: the FE-04 planCard rules with desk labels */

export const CARD_PLAN_LABELS: Record<CardPlanKind, string> = {
  new: 'New card',
  renew: 'Renew',
  later: 'Starts after the current card',
  upgrade: 'Upgrade (pay the difference)',
  blocked: 'Upgrade not possible yet',
  unknown: 'Cannot be priced here',
}

export const UNKNOWN_PLAN_MESSAGE =
  "The tier of the member's current card is no longer on sale, so this sale cannot be priced here. Ask a manager."

export interface DeskCardPlan {
  plan: CardPlan
  sellable: boolean
  /** Shown instead of the payment fields when the sale is not possible. */
  message: string | null
}

export function deskCardPlan(tier: CardTier, cards: MemberCard[], tiers: CardTier[], today: string): DeskCardPlan {
  const plan = planCard(tier, cards, tiers, today, 'desk')
  if (plan.kind === 'blocked') {
    return { plan, sellable: false, message: plan.reason ?? UNKNOWN_PLAN_MESSAGE }
  }
  if (plan.kind === 'unknown' || plan.price === null) {
    return { plan, sellable: false, message: UNKNOWN_PLAN_MESSAGE }
  }
  return { plan, sellable: true, message: null }
}

/** Tiers the desk can sell: active paid tiers (the free Standard tier is not sold). */
export function sellableTiers(tiers: CardTier[]): CardTier[] {
  return tiers.filter((tier) => tier.isActive !== false && !isFreeTier(tier) && Number(tier.price) > 0)
}

/* Result of a desk sale */

/**
 * Decided from the payment, not only from the item:
 * - paid: the item is ACTIVE and a payment record exists (BE-19) -> receipt;
 * - pending: the item waits for payment -> confirm it;
 * - unrecorded: the item is ACTIVE but no payment record exists (backend on main without BE-19);
 * - other: any other status.
 */
export type SaleOutcome = 'paid' | 'pending' | 'unrecorded' | 'other'

export function saleOutcome(item: { status: string; paymentId: number | null }): SaleOutcome {
  if (item.status === 'ACTIVE') {
    return item.paymentId !== null ? 'paid' : 'unrecorded'
  }
  return item.status === 'PENDING_PAYMENT' ? 'pending' : 'other'
}

/* Pending online requests (F1-14 tab) */

export interface PendingRequest {
  key: string
  kind: PaymentItemKind
  itemId: number
  code: string
  memberId: number
  memberLabel: string
  description: string
  amount: number | string | null
  createdAt: string | null
  paymentId: number | null
  paymentCode: string | null
}

function memberLabelOf(source: { memberId: number; memberCode?: string | null }, name: unknown): string {
  const fullName = typeof name === 'string' && name.trim() ? name : null
  const code = source.memberCode ?? null
  if (fullName && code) return `${fullName} (${code})`
  return fullName ?? code ?? `Member #${source.memberId}`
}

/** Merges pending card and package requests, oldest first, so the desk serves them in order. */
export function pendingRequests(cards: MemberCard[], registrations: PackageRegistration[]): PendingRequest[] {
  const cardRows: PendingRequest[] = cards.map((card) => ({
    key: `card-${card.id}`,
    kind: 'card',
    itemId: card.id,
    code: card.cardCode,
    memberId: card.memberId,
    memberLabel: memberLabelOf(card, card.memberFullName),
    description: `Membership card - ${card.tierName}`,
    amount: card.pricePaid,
    createdAt: card.createdAt,
    paymentId: card.paymentId,
    paymentCode: card.paymentCode,
  }))
  const packageRows: PendingRequest[] = registrations.map((registration) => {
    const extra = registration as PackageRegistration & { memberFullName?: string | null; memberCode?: string | null }
    return {
      key: `package-${registration.id}`,
      kind: 'package',
      itemId: registration.id,
      code: registration.registrationCode,
      memberId: registration.memberId,
      memberLabel: memberLabelOf({ memberId: registration.memberId, memberCode: extra.memberCode }, extra.memberFullName),
      description: registration.packageName,
      amount: registration.paidAmount,
      createdAt: registration.createdAt,
      paymentId: registration.paymentId,
      paymentCode: registration.paymentCode,
    }
  })
  return [...cardRows, ...packageRows].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''))
}

/* Check-in (F1-15) */

export type CheckInVerdict = 'recorded' | 'rejected'

export function checkInVerdict(record: Pick<CheckInRecord, 'result'>): CheckInVerdict {
  return record.result === 'ALLOWED' ? 'recorded' : 'rejected'
}

export const CHECK_IN_IDENTIFIER_REQUIRED = 'Enter a member code or scan the member card'
export const CHECK_IN_IDENTIFIER_MAX = 50

export function validateIdentifier(value: string): string | null {
  const identifier = value.trim()
  if (!identifier) {
    return CHECK_IN_IDENTIFIER_REQUIRED
  }
  if (identifier.length > CHECK_IN_IDENTIFIER_MAX) {
    return `The member code must be at most ${CHECK_IN_IDENTIFIER_MAX} characters`
  }
  return null
}

/** Member overview helpers (F1-13) */

/** Registrations to show on the profile: pending first, then active, then the rest, newest first inside each group. */
export function sortedRegistrations(registrations: PackageRegistration[]): PackageRegistration[] {
  const rank = (status: string) => (status === 'PENDING_PAYMENT' ? 0 : status === 'ACTIVE' ? 1 : 2)
  return [...registrations].sort(
    (a, b) => rank(a.status) - rank(b.status) || (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
  )
}

/** Card requests waiting for payment at the desk. */
export function pendingCards(cards: MemberCard[]): MemberCard[] {
  return cards.filter((card) => card.status === 'PENDING_PAYMENT')
}
