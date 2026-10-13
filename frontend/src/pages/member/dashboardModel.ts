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
  /**
   * My Membership and Sport Packages (F1-11, F1-12). Its default tab "Sport packages" lists the
   * member's registrations ("My registrations"), so "View all packages" points here.
   */
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

/**
 * "10% off packages longer than a single visit". Shared by the tier stat hint, the first-package
 * hero and the Sport Packages note, so every text states the rule the prices are computed with:
 * MembershipCardServiceImpl.getApplicableDiscountPercentage gives 0 only to packages of 1 day or
 * less (see isDiscountEligible in packagesModel.ts). docs/database/09 lines 149 to 153 say 30-day
 * and 90-day packages only; that difference is an open question for the backend team.
 */
export function cardDiscountText(discountPercentage: number): string {
  return `${discountPercentage}% off packages longer than a single visit`
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

/**
 * A paid tier the member has bought (pending, active, renewed or upgraded), not the free one.
 * A missing tier code counts as paid: member_card.tier_id is NOT NULL, so a card row always has a
 * real tier, and a missing code is a mapping problem. Counting it as a purchase never hides a card
 * the member paid for behind the New Standard Member state.
 */
export function isPaidCard(card: Pick<MemberCard, 'tierCode' | 'status'>): boolean {
  const code = (card.tierCode ?? '').toUpperCase()
  return code !== STANDARD_TIER_CODE && card.status !== 'CANCELLED'
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

/** True for a package without a session limit (totalSessions is null). */
export function hasNoSessionLimit(registration: Pick<PackageRegistration, 'totalSessions'>): boolean {
  return registration.totalSessions === null || registration.totalSessions === undefined
}

/**
 * Sessions still usable. Null means no limit. A limited package never goes below 0 or above its
 * total; a total of 0 always gives 0 (an exhausted package, never "no limit").
 */
export function remainingSessionCount(
  registration: Pick<PackageRegistration, 'totalSessions' | 'remainingSessions'>,
): number | null {
  if (hasNoSessionLimit(registration)) {
    return null
  }
  const total = Math.max(0, registration.totalSessions ?? 0)
  return Math.max(0, Math.min(total, registration.remainingSessions ?? 0))
}

/**
 * Remaining sessions as a share of the total, between 0 and 1; null only when there is no limit.
 * A total of 0 gives 0, the same as an exhausted package.
 */
export function sessionShare(registration: Pick<PackageRegistration, 'totalSessions' | 'remainingSessions'>): number | null {
  const remaining = remainingSessionCount(registration)
  if (remaining === null) {
    return null
  }
  const total = registration.totalSessions ?? 0
  return total > 0 ? remaining / total : 0
}

/** True when a limited package has no session left (including a total of 0). */
export function isExhausted(registration: Pick<PackageRegistration, 'totalSessions' | 'remainingSessions'>): boolean {
  return remainingSessionCount(registration) === 0
}

/**
 * True when the member can book a session with this package: ACTIVE, not past its end date and
 * with at least one session left (or no limit). A package bought in advance can be booked for
 * sessions after its start date, so it counts as bookable.
 */
export function canBook(registration: PackageRegistration, today: string): boolean {
  return (
    registration.status === 'ACTIVE' &&
    (!registration.endDate || registration.endDate >= today) &&
    !isExhausted(registration)
  )
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

/**
 * Which hero the dashboard shows:
 * - welcome: New Standard Member, nothing bought yet.
 * - book: at least one package can be booked.
 * - pay: nothing can be booked but a request waits for its payment.
 * - first-package: after a purchase (a paid card) but the member has never had a package.
 * - renew: the member had packages, and they are now used up or ended.
 */
export type HeroVariant = 'welcome' | 'book' | 'pay' | 'first-package' | 'renew'

export interface HeroFacts {
  state: DashboardState
  hasBookablePackage: boolean
  pendingCount: number
  /** True when at least one registration was ever paid (ACTIVE or EXPIRED). */
  hasHadPackage: boolean
}

export function heroVariant({ state, hasBookablePackage, pendingCount, hasHadPackage }: HeroFacts): HeroVariant {
  if (state === 'new-standard') {
    return 'welcome'
  }
  if (hasBookablePackage) {
    return 'book'
  }
  if (pendingCount > 0) {
    return 'pay'
  }
  return hasHadPackage ? 'renew' : 'first-package'
}

/**
 * True when the member has had at least one paid package: ACTIVE (even used up) or EXPIRED.
 * Pending and cancelled registrations were never paid, so they do not count.
 */
export function hasHadPackage(registrations: Pick<PackageRegistration, 'status'>[]): boolean {
  return registrations.some((registration) => registration.status === 'ACTIVE' || registration.status === 'EXPIRED')
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
