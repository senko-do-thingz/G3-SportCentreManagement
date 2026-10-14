import type { CardTier } from '../../api/cardsApi'
import type { MemberCard, PackageRegistration } from '../../api/memberApi'

/**
 * Pure rules of Membership Cards (F1-11) and My Membership and Sport Packages (F1-12).
 *
 * planCard mirrors MembershipCardServiceImpl.planCard on main, so the dates and the price shown
 * before Confirm are the ones the backend will save:
 * - no paid card in force: a new card from today, full price;
 * - the same tier as the last paid card: a renewal from the day after that card ends, full price;
 * - a dearer tier than the card in force today: an upgrade from today that keeps the current end
 *   date; the member pays only the price difference (blocked while a renewal is already queued);
 * - otherwise (a cheaper tier, or a different tier while a renewal is queued): it starts the day
 *   after the last card ends, full price.
 * A card lasts durationMonths months: it ends the day before the same date durationMonths later
 * (Java LocalDate.plusMonths clamps to the last day of a shorter month).
 * Dates are ISO strings ("2026-10-13"), compared as strings, read in the local time zone of the
 * browser. The backend clock runs in Asia/Ho_Chi_Minh (UTC+7), so the preview matches what the
 * backend saves only when the browser also runs in UTC+7; elsewhere "today" can differ by one day
 * around midnight and the backend answer stays the authority.
 */

export type CardPlanKind = 'new' | 'renew' | 'upgrade' | 'later' | 'blocked' | 'unknown'

export interface CardPlan {
  kind: CardPlanKind
  startDate: string | null
  endDate: string | null
  /** Amount the member pays for this request. */
  price: number | null
  /** Full price of the tier. */
  tierPrice: number
  /** Card in force today that an upgrade replaces, or that a renewal or later card follows. */
  baseCard: MemberCard | null
  /** Why the request cannot be made (kind 'blocked'). */
  reason: string | null
}

/** True for the free, permanent tier (Standard): no duration, nothing to buy. */
export function isFreeTier(tier: Pick<CardTier, 'durationMonths'>): boolean {
  return tier.durationMonths === null || tier.durationMonths === undefined || tier.durationMonths <= 0
}

function toNumber(value: number | string | null | undefined): number {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

/** Money in integer cents, so price differences never carry binary rounding errors. */
function cents(value: number | string | null | undefined): number {
  return Math.round(toNumber(value) * 100)
}

function parseIso(value: string): { year: number; month: number; day: number } {
  const [year, month, day] = value.split('-').map(Number)
  return { year, month, day }
}

function formatIso(year: number, monthIndex: number, day: number): string {
  const date = new Date(year, monthIndex, day)
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${mm}-${dd}`
}

/** "2026-10-13" plus days, which may be negative. */
export function addDays(isoDate: string, days: number): string {
  const { year, month, day } = parseIso(isoDate)
  return formatIso(year, month - 1, day + days)
}

/** Same as Java LocalDate.plusMonths: the day is clamped to the last day of the target month. */
export function addMonths(isoDate: string, months: number): string {
  const { year, month, day } = parseIso(isoDate)
  const targetMonthIndex = month - 1 + months
  const lastDay = new Date(year, targetMonthIndex + 1, 0).getDate()
  return formatIso(year, targetMonthIndex, Math.min(day, lastDay))
}

/** Last day of a card that starts on startDate and lasts months months. */
export function cardEndDate(startDate: string, months: number): string {
  return addDays(addMonths(startDate, months), -1)
}

/**
 * Paid cards the backend counts in planCard: ACTIVE, with an end date today or later
 * (findActiveCardsForMember) and a tier with a duration. A card whose tier is not on sale any
 * more is not in the tier list; it still counts when it has an end date, as on the backend.
 */
function paidTerms(cards: MemberCard[], tiers: CardTier[], today: string): MemberCard[] {
  return cards.filter((card) => {
    if (card.status !== 'ACTIVE' || !card.endDate || card.endDate < today) {
      return false
    }
    const tier = tiers.find((candidate) => candidate.id === card.tierId)
    return !tier || !isFreeTier(tier)
  })
}

/** The card with the latest end date; on a tie the first one wins, like Stream.max. */
function latestEnding(cards: MemberCard[]): MemberCard | null {
  return cards.reduce<MemberCard | null>(
    (best, card) => (best === null || (card.endDate ?? '') > (best.endDate ?? '') ? card : best),
    null,
  )
}

/** Paid card in force today (its term has started), or null on the Standard tier. */
export function cardInForce(cards: MemberCard[], tiers: CardTier[], today: string): MemberCard | null {
  return latestEnding(paidTerms(cards, tiers, today).filter((card) => !card.startDate || card.startDate <= today))
}

/** The paid card that ends last (a queued renewal when one exists), or null. */
export function lastPaidCard(cards: MemberCard[], tiers: CardTier[], today: string): MemberCard | null {
  return latestEnding(paidTerms(cards, tiers, today))
}

/** What buying tier means today for this member. See the file comment for the rules. */
export function planCard(tier: CardTier, cards: MemberCard[], tiers: CardTier[], today: string): CardPlan {
  const months = tier.durationMonths ?? 0
  const tierPrice = cents(tier.price)
  const base = { tierPrice: tierPrice / 100, reason: null }
  const current = cardInForce(cards, tiers, today)
  const last = lastPaidCard(cards, tiers, today)

  if (!last) {
    return { ...base, kind: 'new', startDate: today, endDate: cardEndDate(today, months), price: tierPrice / 100, baseCard: null }
  }
  if (last.tierId === tier.id) {
    const from = addDays(last.endDate as string, 1)
    return { ...base, kind: 'renew', startDate: from, endDate: cardEndDate(from, months), price: tierPrice / 100, baseCard: last }
  }
  if (current) {
    const currentTier = tiers.find((candidate) => candidate.id === current.tierId)
    if (!currentTier) {
      // The tier of the card in force is no longer on sale, so its price is unknown here.
      return { ...base, kind: 'unknown', startDate: null, endDate: null, price: null, baseCard: current }
    }
    const currentPrice = cents(currentTier.price)
    if (tierPrice > currentPrice) {
      if (last !== current) {
        return {
          ...base,
          kind: 'blocked',
          startDate: null,
          endDate: null,
          price: null,
          baseCard: current,
          reason: `Your card is already renewed from ${last.startDate ?? 'a later date'}. You can upgrade once that term has started.`,
        }
      }
      return {
        ...base,
        kind: 'upgrade',
        startDate: today,
        endDate: current.endDate,
        price: (tierPrice - currentPrice) / 100,
        baseCard: current,
      }
    }
  }
  const from = addDays(last.endDate as string, 1)
  return { ...base, kind: 'later', startDate: from, endDate: cardEndDate(from, months), price: tierPrice / 100, baseCard: last }
}

/** Label of the tier button and the eyebrow of the confirm overlay. */
export const PLAN_ACTION_LABELS: Record<CardPlanKind, string> = {
  new: 'Get card',
  renew: 'Renew card',
  upgrade: 'Upgrade card',
  later: 'Buy after current card',
  blocked: 'Upgrade card',
  unknown: 'Choose card',
}

/**
 * Benefits shown under each tier. The backend has no perks field yet (CardTierResponse carries
 * price, duration, discount and description only), so the list is built from the discount rule
 * the backend applies: every package longer than a single visit.
 */
export function tierPerks(tier: Pick<CardTier, 'durationMonths' | 'discountPercentage'>): string[] {
  if (isFreeTier(tier)) {
    return ['0% off sport packages', 'Standard sport package prices', 'Pay per sport or session']
  }
  return [
    `${tier.discountPercentage ?? 0}% off packages longer than a single visit`,
    'Self-training and coach-led packages',
    'Single visits excluded',
  ]
}

/** "12 months validity" or "No expiry" for the free tier. */
export function validityLabel(tier: Pick<CardTier, 'durationMonths'>): string {
  if (isFreeTier(tier)) {
    return 'No expiry'
  }
  const months = tier.durationMonths as number
  return `${months} ${months === 1 ? 'month' : 'months'} validity`
}

/** Active tiers, free tier first, then by price. */
export function sortTiers(tiers: CardTier[]): CardTier[] {
  return tiers
    .filter((tier) => tier.isActive !== false)
    .sort((a, b) => cents(a.price) - cents(b.price) || a.id - b.id)
}

/** The card request that waits for payment, newest first when old data holds more than one. */
export function pendingCard(cards: MemberCard[]): MemberCard | null {
  const pending = cards
    .filter((card) => card.status === 'PENDING_PAYMENT')
    .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  return pending[0] ?? null
}

/** Only a request that waits for payment can be cancelled (MembershipCardServiceImpl.cancelCard). */
export function canCancelCard(card: Pick<MemberCard, 'status'>): boolean {
  return card.status === 'PENDING_PAYMENT'
}

/** True for an ACTIVE card whose term starts after today (a renewal bought in advance). */
export function isQueued(card: Pick<MemberCard, 'status' | 'startDate'>, today: string): boolean {
  return card.status === 'ACTIVE' && Boolean(card.startDate && card.startDate > today)
}

/**
 * Status shown on a card or registration. An ACTIVE row whose end date has passed is shown as
 * EXPIRED: the nightly jobs that set EXPIRED (F1-B3, F1-B4) are not merged yet.
 */
export function displayStatus(item: { status: string; endDate: string | null }, today: string): string {
  if (item.status === 'ACTIVE' && item.endDate && item.endDate < today) {
    return 'EXPIRED'
  }
  return item.status
}

const STATUS_ORDER: Record<string, number> = {
  PENDING_PAYMENT: 0,
  ACTIVE: 1,
  EXPIRED: 2,
  REPLACED: 3,
  CANCELLED: 4,
}

function statusRank(status: string): number {
  return STATUS_ORDER[status] ?? 5
}

/** Registrations for F1-12: waiting for payment first, then active, expired, cancelled; newest first inside. */
export function sortRegistrations(registrations: PackageRegistration[], today: string): PackageRegistration[] {
  return [...registrations].sort(
    (a, b) =>
      statusRank(displayStatus(a, today)) - statusRank(displayStatus(b, today)) ||
      (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
  )
}

/** Cards for F1-12 in the same order as the registrations. */
export function sortCards(cards: MemberCard[], today: string): MemberCard[] {
  return [...cards].sort(
    (a, b) =>
      statusRank(displayStatus(a, today)) - statusRank(displayStatus(b, today)) ||
      (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
  )
}

/** Days from start to end, both included, or null when a date is missing. */
export function termDays(startDate: string | null, endDate: string | null): number | null {
  if (!startDate || !endDate) {
    return null
  }
  const start = parseIso(startDate)
  const end = parseIso(endDate)
  const startUtc = Date.UTC(start.year, start.month - 1, start.day)
  const endUtc = Date.UTC(end.year, end.month - 1, end.day)
  return Math.round((endUtc - startUtc) / 86400000) + 1
}

/** Tabs of F1-12, kept in the query string (?tab=card) so links can open the card tab. */
export type MembershipTab = 'packages' | 'card'

export function tabFromParam(value: string | null): MembershipTab {
  return value === 'card' ? 'card' : 'packages'
}

/**
 * Id of the card that gets "Renew card" on the Membership card tab of F1-12, or null.
 * Only when nothing paid is in force or queued (cardInForce and lastPaidCard are both null), and
 * only on the latest paid card that has ended: ACTIVE or EXPIRED with an end date, the latest end
 * date wins. REPLACED, CANCELLED, PENDING_PAYMENT and the free Standard card never get it.
 * Tiers are not loaded on F1-12; with an empty tier list every card with an end date counts as
 * paid, which is the same test the backend applies (the free tier has no end date).
 */
export function renewableCardId(cards: MemberCard[], today: string): number | null {
  if (cardInForce(cards, [], today) || lastPaidCard(cards, [], today)) {
    return null
  }
  const ended = cards.filter(
    (card) => (card.status === 'ACTIVE' || card.status === 'EXPIRED') && card.endDate !== null && card.endDate < today,
  )
  return latestEnding(ended)?.id ?? null
}
