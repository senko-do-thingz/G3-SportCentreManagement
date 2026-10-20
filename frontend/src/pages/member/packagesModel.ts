import type { MemberCard } from '../../api/memberApi'
import type { Sport, SportPackage } from '../../api/packagesApi'
import { trainingFormatLabel } from './dashboardModel'

/**
 * Pure rules of Sport Packages (F1-09) and Package Detail (F1-10). The discount rule mirrors
 * the backend: MembershipCardServiceImpl.getApplicableDiscountPercentage returns 0 for packages
 * of 1 day or less (single visit) and otherwise the highest discount of the ACTIVE card in force
 * today; SportPackageServiceImpl.registerPackage then charges price * (100 - discount) / 100
 * with BigDecimal, rounded half up to 2 decimals. priceBreakdown repeats that in integer cents.
 * The docs (docs/database/09) say 30-day and 90-day packages only; the frontend follows the code,
 * so the price shown is the price charged (open question in the FE-03 audit log).
 */

/** A package of this many days or less is a single visit and never gets a card discount. */
export const SINGLE_VISIT_MAX_DAYS = 1

export interface PackageFilters {
  /** Sport id as a string, '' for all sports. */
  sport: string
  /** SELF_TRAINING, COACH_LED or '' for all types. */
  format: string
  /** Duration in days as a string, '' for all durations. */
  duration: string
}

export const EMPTY_FILTERS: PackageFilters = { sport: '', format: '', duration: '' }

export const FORMAT_FILTER_OPTIONS = [
  { value: 'SELF_TRAINING', label: 'Self-training' },
  { value: 'COACH_LED', label: 'Coach-led' },
]

export function isSingleVisit(pkg: Pick<SportPackage, 'durationDays'>): boolean {
  return pkg.durationDays <= SINGLE_VISIT_MAX_DAYS
}

/** Backend rule: a card discount applies to every package longer than a single visit. */
export function isDiscountEligible(durationDays: number): boolean {
  return durationDays > SINGLE_VISIT_MAX_DAYS
}

/** Discount percent the backend will apply to this package for a member holding this card. */
export function discountPercentFor(pkg: Pick<SportPackage, 'durationDays'>, card: MemberCard | null): number {
  if (!isDiscountEligible(pkg.durationDays) || !card) {
    return 0
  }
  return Math.max(0, Math.min(100, card.discountPercentage ?? 0))
}

export interface PriceBreakdown {
  original: number
  discountPercent: number
  discountAmount: number
  final: number
}

/**
 * Original price, discount and final price, computed in integer cents so the result matches the
 * backend BigDecimal HALF_UP to 2 decimals (for example 0.35 with 10% -> 0.32, 2.30 with 5% -> 2.19).
 * cents * (100 - discount) is an exact integer; dividing by 100 leaves at most two decimals, so
 * Math.round (half up for positive values) never sees a binary rounding error at .5.
 */
export function priceBreakdown(pkg: Pick<SportPackage, 'durationDays' | 'priceAmount'>, card: MemberCard | null): PriceBreakdown {
  const cents = Math.round((Number(pkg.priceAmount) || 0) * 100)
  const discountPercent = discountPercentFor(pkg, card)
  const finalCents = Math.round((cents * (100 - discountPercent)) / 100)
  return {
    original: cents / 100,
    discountPercent,
    discountAmount: (cents - finalCents) / 100,
    final: finalCents / 100,
  }
}

/** Last day of a package that starts on startDate: N days include the start date. */
export function packageEndDate(startDate: string, durationDays: number): string {
  const [year, month, day] = startDate.split('-').map(Number)
  const end = new Date(year, month - 1, day + Math.max(1, durationDays) - 1)
  const mm = String(end.getMonth() + 1).padStart(2, '0')
  const dd = String(end.getDate()).padStart(2, '0')
  return `${end.getFullYear()}-${mm}-${dd}`
}

/** "Single visit" or "30 days". */
export function durationLabel(days: number): string {
  return days <= SINGLE_VISIT_MAX_DAYS ? 'Single visit' : `${days} days`
}

/** "No session limit" for null or undefined, "1 session", otherwise "N sessions". */
export function sessionsLabel(count: number | null | undefined): string {
  if (count === null || count === undefined) {
    return 'No session limit'
  }
  return `${count} ${count === 1 ? 'session' : 'sessions'}`
}

/** "30 days - 8 sessions", "Single visit - 1 session" or "30 days - No session limit". */
// Display-only tolerance for incomplete/legacy input does not define the package API contract.
export function packageSummary(pkg: { durationDays: number; sessionCount?: number | null }): string {
  return `${durationLabel(pkg.durationDays)} - ${sessionsLabel(pkg.sessionCount)}`
}

/** "Football - Coach-led". */
export function packageTitle(pkg: Pick<SportPackage, 'sportName' | 'trainingFormat'>): string {
  const format = trainingFormatLabel(pkg.trainingFormat)
  return format ? `${pkg.sportName} - ${format}` : pkg.sportName
}

/** Distinct durations of the packages, shortest first, for the Duration filter. */
export function durationOptions(packages: Pick<SportPackage, 'durationDays'>[]): { value: string; label: string }[] {
  return [...new Set(packages.map((pkg) => pkg.durationDays))]
    .sort((a, b) => a - b)
    .map((days) => ({ value: String(days), label: durationLabel(days) }))
}

/** Packages matching every filter, ordered by sport, training format, then duration. */
export function filterPackages(packages: SportPackage[], filters: PackageFilters): SportPackage[] {
  return packages
    .filter((pkg) => pkg.isActive !== false)
    .filter((pkg) => !filters.sport || String(pkg.sportId) === filters.sport)
    .filter((pkg) => !filters.format || pkg.trainingFormat === filters.format)
    .filter((pkg) => !filters.duration || String(pkg.durationDays) === filters.duration)
    .sort(
      (a, b) =>
        a.sportName.localeCompare(b.sportName) ||
        a.trainingFormat.localeCompare(b.trainingFormat) ||
        a.durationDays - b.durationDays,
    )
}

/**
 * Drops filter values that match nothing selectable (unknown sport id, unknown format, a duration
 * no package has), so the selects and the list always agree.
 */
export function sanitizeFilters(
  filters: PackageFilters,
  sports: Pick<Sport, 'id'>[],
  packages: Pick<SportPackage, 'durationDays'>[],
): PackageFilters {
  return {
    sport: sports.some((sport) => String(sport.id) === filters.sport) ? filters.sport : '',
    format: FORMAT_FILTER_OPTIONS.some((option) => option.value === filters.format) ? filters.format : '',
    duration: durationOptions(packages).some((option) => option.value === filters.duration) ? filters.duration : '',
  }
}

/** Reads the filters from the URL query, so Back to packages keeps them. */
export function filtersFromParams(params: URLSearchParams): PackageFilters {
  return {
    sport: params.get('sport') ?? '',
    format: params.get('format') ?? '',
    duration: params.get('duration') ?? '',
  }
}

/** Query string for the filters, without empty values. */
export function filtersToParams(filters: PackageFilters): URLSearchParams {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      params.set(key, value)
    }
  }
  return params
}

/** Path of the detail page of a package, keeping the current filters for the way back. */
export function packageDetailPath(packageId: number, search = ''): string {
  return `/member/packages/${packageId}${search}`
}

export const START_DATE_REQUIRED = 'Start date is required'
export const START_DATE_INVALID = 'Start date is invalid'
/** Same text as the backend BusinessRuleException in SportPackageServiceImpl.registerPackage. */
export const START_DATE_IN_PAST = 'Start date cannot be in the past'

/** Client check of the start date: today or later. */
export function validateStartDate(startDate: string, today: string): string | null {
  if (!startDate) {
    return START_DATE_REQUIRED
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate)) {
    return START_DATE_INVALID
  }
  return startDate < today ? START_DATE_IN_PAST : null
}
