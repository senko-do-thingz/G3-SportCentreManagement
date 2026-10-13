import type { MemberBooking, MemberCard, PackageRegistration } from '../../api/memberApi'
import { formatMoney, humanizeCode } from '../../utils/format'

/**
 * Pure rules behind the Member Dashboard (F1-07). They take "today" and "now" as arguments so
 * the tests can fix the date. Dates are compared as ISO strings ("2026-10-13"), which sort in
 * calendar order, and are always read in the local time zone of the browser.
 */

/** Paths of the other member screens linked from the dashboard. */
export const MEMBER_PATHS = {
  profile: '/member/profile',
  packages: '/member/packages',
  membership: '/member/membership',
  schedule: '/member/schedule',
  payments: '/member/payments',
} as const

/** The two Figma states of F1-07. */
export type DashboardState = 'new-standard' | 'after-purchase'

/** Tier shown when the member has no paid card in force (free, permanent, 0 percent discount). */
export const STANDARD_TIER_NAME = 'Standard'
export const STANDARD_TIER_CODE = 'STANDARD'

export const TRAINING_FORMAT_LABELS: Record<string, string> = {
  SELF_TRAINING: 'Self-training',
  COACH_LED: 'Coach-led',
}

/** "2026-10-13" for the given moment, in the local time zone. */
export function toIsoDate(moment: Date): string {
  const year = moment.getFullYear()
  const month = String(moment.getMonth() + 1).padStart(2, '0')
  const day = String(moment.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function trainingFormatLabel(format: string | null | undefined): string {
  if (!format) {
    return ''
  }
  return TRAINING_FORMAT_LABELS[format] ?? humanizeCode(format)
}

/** "Football - Coach-led", or only the sport when the format is unknown. */
export function registrationTitle(registration: Pick<PackageRegistration, 'sportName' | 'trainingFormat'>): string {
  const format = trainingFormatLabel(registration.trainingFormat)
  return format ? `${registration.sportName} - ${format}` : registration.sportName
}

/** True when the date range contains today. A missing start or end date is an open bound. */
function coversToday(startDate: string | null, endDate: string | null, today: string): boolean {
  return (!startDate || startDate <= today) && (!endDate || endDate >= today)
}

/**
 * The card that is in force today: ACTIVE and inside its validity period. When several are in
 * force (for example during an upgrade), the one with the highest discount wins, the same rule
 * the backend uses for package discounts. Null means the member is on the free Standard tier.
 */
export function currentCard(cards: MemberCard[], today: string): MemberCard | null {
  const inForce = cards.filter(
    (card) => card.status === 'ACTIVE' && coversToday(card.startDate, card.endDate, today),
  )
  if (inForce.length === 0) {
    return null
  }
  return inForce.reduce((best, card) =>
    (card.discountPercentage ?? 0) > (best.discountPercentage ?? 0) ? card : best,
  )
}

/** A paid tier the member has bought (pending, active, renewed or upgraded), not the free one. */
function isPaidCard(card: MemberCard): boolean {
  return card.tierCode?.toUpperCase() !== STANDARD_TIER_CODE && card.status !== 'CANCELLED'
}

/**
 * New Standard Member: no paid card and no sport package yet, cancelled requests ignored.
 * After Purchase: at least one paid card or one package registration, including a request that
 * still waits for its payment.
 */
export function dashboardState(cards: MemberCard[], registrations: PackageRegistration[]): DashboardState {
  const hasPackage = registrations.some((registration) => registration.status !== 'CANCELLED')
  const hasPaidCard = cards.some(isPaidCard)
  return hasPackage || hasPaidCard ? 'after-purchase' : 'new-standard'
}

/**
 * Packages the member can train with now or soon: ACTIVE and not past their end date, sorted by
 * the nearest end date first.
 */
export function activeRegistrations(registrations: PackageRegistration[], today: string): PackageRegistration[] {
  return registrations
    .filter((registration) => registration.status === 'ACTIVE' && (!registration.endDate || registration.endDate >= today))
    .sort((a, b) => (a.endDate ?? '9999-12-31').localeCompare(b.endDate ?? '9999-12-31'))
}

/** True when an active package has not started yet (bought in advance). */
export function startsLater(registration: Pick<PackageRegistration, 'startDate'>, today: string): boolean {
  return Boolean(registration.startDate && registration.startDate > today)
}

/** Remaining sessions as a share of the total, between 0 and 1; null when there is no limit. */
export function sessionShare(registration: Pick<PackageRegistration, 'totalSessions' | 'remainingSessions'>): number | null {
  const total = registration.totalSessions
  if (!total || total <= 0) {
    return null
  }
  const remaining = Math.max(0, Math.min(total, registration.remainingSessions ?? 0))
  return remaining / total
}

/** One card request or package registration that waits for its payment. */
export interface PendingItem {
  key: string
  kind: 'card' | 'package'
  title: string
  reference: string
  amount: string
  requestedAt: string | null
  /** Null when the backend has not linked a payment yet: the item cannot be paid online. */
  paymentId: number | null
}

/** Card requests and package registrations in PENDING_PAYMENT, newest first. */
export function pendingItems(cards: MemberCard[], registrations: PackageRegistration[]): PendingItem[] {
  const fromCards: PendingItem[] = cards
    .filter((card) => card.status === 'PENDING_PAYMENT')
    .map((card) => ({
      key: `card-${card.id}`,
      kind: 'card',
      title: `Membership card - ${card.tierName}`,
      reference: card.paymentCode ?? card.cardCode,
      amount: formatMoney(card.pricePaid),
      requestedAt: card.createdAt,
      paymentId: card.paymentId,
    }))
  const fromPackages: PendingItem[] = registrations
    .filter((registration) => registration.status === 'PENDING_PAYMENT')
    .map((registration) => ({
      key: `package-${registration.id}`,
      kind: 'package',
      title: `Sport package - ${registrationTitle(registration)}`,
      reference: registration.paymentCode ?? registration.registrationCode,
      amount: formatMoney(registration.paidAmount),
      requestedAt: registration.createdAt,
      paymentId: registration.paymentId,
    }))
  return [...fromCards, ...fromPackages].sort((a, b) => (b.requestedAt ?? '').localeCompare(a.requestedAt ?? ''))
}

/** Path of the payment summary screen (F3-01, task FE-11) for a pending payment. */
export function paymentSummaryPath(paymentId: number): string {
  return `/member/payments/${paymentId}`
}

/** "17:00:00" becomes "17:00". */
export function shortTime(value: string | null | undefined): string {
  return value ? value.slice(0, 5) : ''
}

/** Local start moment of a booking, or null when the date is missing or invalid. */
function bookingStart(booking: MemberBooking): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(booking.sessionDate ?? '')
  if (!match) {
    return null
  }
  const [hours, minutes] = (booking.startTime ?? '00:00').split(':').map(Number)
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), hours || 0, minutes || 0)
}

/**
 * Confirmed bookings that have not started yet, soonest first. The backend filter
 * (when=UPCOMING, BE-12) is applied again here, so the screen is right before and after BE-12.
 */
export function upcomingBookings(bookings: MemberBooking[], now: Date): MemberBooking[] {
  return bookings
    .map((booking) => ({ booking, start: bookingStart(booking) }))
    .filter(({ booking, start }) => booking.status === 'CONFIRMED' && start !== null && start.getTime() >= now.getTime())
    .sort((a, b) => (a.start as Date).getTime() - (b.start as Date).getTime())
    .map(({ booking }) => booking)
}
