import type { MemberCard } from '../../api/memberApi'
import type { SportPackage } from '../../api/packagesApi'
import { trainingFormatLabel } from './dashboardModel'

/**
 * Pure rules of Sport Packages (F1-09) and Package Detail (F1-10). The discount rule mirrors
 * the backend: MembershipCardServiceImpl.getApplicableDiscountPercentage returns 0 for packages
 * of 1 day or less (single visit) and otherwise the highest discount of the ACTIVE card in force
 * today; SportPackageServiceImpl.registerPackage then charges price * (100 - discount) / 100,
 * rounded half up to 2 decimals.
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

/** Discount percent the backend will apply to this package for a member holding this card. */
export function discountPercentFor(pkg: Pick<SportPackage, 'durationDays'>, card: MemberCard | null): number {
  if (isSingleVisit(pkg) || !card) {
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

/** Original price, discount and final price, computed in cents like the backend (half up). */
export function priceBreakdown(pkg: Pick<SportPackage, 'durationDays' | 'priceAmount'>, card: MemberCard | null): PriceBreakdown {
  const original = Number(pkg.priceAmount) || 0
  const discountPercent = discountPercentFor(pkg, card)
  // original * (100 - discount) is the final price in cents; Math.round rounds half up for
  // positive values, like RoundingMode.HALF_UP on the backend.
  const final = Math.round(original * (100 - discountPercent)) / 100
  return { original, discountPercent, discountAmount: original - final, final }
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

/** "30 days - 8 sessions", "Single visit - 1 session" or "30 days - No session limit". */
export function packageSummary(pkg: Pick<SportPackage, 'durationDays' | 'sessionCount'>): string {
  const sessions =
    pkg.sessionCount === null || pkg.sessionCount === undefined
      ? 'No session limit'
      : `${pkg.sessionCount} ${pkg.sessionCount === 1 ? 'session' : 'sessions'}`
  return `${durationLabel(pkg.durationDays)} - ${sessions}`
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
