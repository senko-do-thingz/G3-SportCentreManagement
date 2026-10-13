import { describe, expect, it } from 'vitest'
import { card, sportPackage } from '../../test/memberFixtures'
import {
  discountPercentFor,
  durationOptions,
  filterPackages,
  filtersFromParams,
  filtersToParams,
  packageEndDate,
  packageSummary,
  priceBreakdown,
  START_DATE_IN_PAST,
  START_DATE_REQUIRED,
  validateStartDate,
} from './packagesModel'

/** FE-03: rules of Sport Packages (F1-09) and Package Detail (F1-10). Today is 2026-10-13. */
const TODAY = '2026-10-13'
const GOLD = card({ tierCode: 'GOLD', tierName: 'Gold', discountPercentage: 5 })
const VIP = card()

describe('FE-03 packages model', () => {
  it('TC-FE03-001 discount: STANDARD (no card) 0, GOLD 5, VIP 10; single visit always 0', () => {
    const month = sportPackage()
    const single = sportPackage({ durationDays: 1, sessionCount: 1, priceAmount: 150000 })
    expect(discountPercentFor(month, null)).toBe(0)
    expect(discountPercentFor(month, GOLD)).toBe(5)
    expect(discountPercentFor(month, VIP)).toBe(10)
    expect(discountPercentFor(single, VIP)).toBe(0)
    expect(discountPercentFor(single, GOLD)).toBe(0)
  })

  it('TC-FE03-002 price breakdown matches the backend for the seeded prices', () => {
    expect(priceBreakdown(sportPackage(), null)).toEqual({ original: 900000, discountPercent: 0, discountAmount: 0, final: 900000 })
    expect(priceBreakdown(sportPackage(), GOLD)).toEqual({ original: 900000, discountPercent: 5, discountAmount: 45000, final: 855000 })
    expect(priceBreakdown(sportPackage(), VIP)).toEqual({ original: 900000, discountPercent: 10, discountAmount: 90000, final: 810000 })
    expect(priceBreakdown(sportPackage({ durationDays: 90, priceAmount: 1350000 }), VIP).final).toBe(1215000)
    expect(priceBreakdown(sportPackage({ durationDays: 1, priceAmount: 80000 }), VIP).final).toBe(80000)
  })

  it('TC-FE03-003 price rounding is half up to 2 decimals like RoundingMode.HALF_UP', () => {
    // 99999.99 * 0.90 = 89999.991 -> 89999.99; 10.05 * 0.95 = 9.5475 -> 9.55
    expect(priceBreakdown(sportPackage({ priceAmount: 99999.99 }), VIP).final).toBe(89999.99)
    expect(priceBreakdown(sportPackage({ priceAmount: '10.05' }), GOLD).final).toBe(9.55)
  })

  it('TC-FE03-004 end date: N days include the start date, across months', () => {
    expect(packageEndDate('2026-10-02', 30)).toBe('2026-10-31')
    expect(packageEndDate('2026-10-13', 1)).toBe('2026-10-13')
    expect(packageEndDate('2026-12-15', 90)).toBe('2027-03-14')
  })

  it('TC-FE03-005 start date must be today or later', () => {
    expect(validateStartDate('', TODAY)).toBe(START_DATE_REQUIRED)
    expect(validateStartDate('2026-10-12', TODAY)).toBe(START_DATE_IN_PAST)
    expect(validateStartDate(TODAY, TODAY)).toBeNull()
    expect(validateStartDate('2026-11-01', TODAY)).toBeNull()
    expect(validateStartDate('13/10/2026', TODAY)).toBe('Start date is invalid')
  })

  it('TC-FE03-006 filters by sport, training type and duration, and hides inactive packages', () => {
    const list = [
      sportPackage({ id: 1, durationDays: 30 }),
      sportPackage({ id: 2, trainingFormat: 'SELF_TRAINING', durationDays: 30 }),
      sportPackage({ id: 3, sportId: 2, sportName: 'Swimming', durationDays: 90 }),
      sportPackage({ id: 4, durationDays: 1 }),
      sportPackage({ id: 5, isActive: false }),
    ]
    const ids = (filters: Parameters<typeof filterPackages>[1]) => filterPackages(list, filters).map((pkg) => pkg.id)
    expect(ids({ sport: '', format: '', duration: '' })).toEqual([4, 1, 2, 3])
    expect(ids({ sport: '1', format: '', duration: '' })).toEqual([4, 1, 2])
    expect(ids({ sport: '1', format: 'COACH_LED', duration: '' })).toEqual([4, 1])
    expect(ids({ sport: '1', format: 'COACH_LED', duration: '30' })).toEqual([1])
    expect(ids({ sport: '2', format: 'SELF_TRAINING', duration: '' })).toEqual([])
  })

  it('TC-FE03-007 labels, duration options and URL filters round trip', () => {
    expect(packageSummary(sportPackage())).toBe('30 days - 8 sessions')
    expect(packageSummary(sportPackage({ durationDays: 1, sessionCount: 1 }))).toBe('Single visit - 1 session')
    expect(packageSummary(sportPackage({ sessionCount: null }))).toBe('30 days - No session limit')
    expect(durationOptions([sportPackage({ durationDays: 90 }), sportPackage({ durationDays: 1 }), sportPackage()])).toEqual([
      { value: '1', label: 'Single visit' },
      { value: '30', label: '30 days' },
      { value: '90', label: '90 days' },
    ])
    const params = filtersToParams({ sport: '1', format: '', duration: '30' })
    expect(params.toString()).toBe('sport=1&duration=30')
    expect(filtersFromParams(params)).toEqual({ sport: '1', format: '', duration: '30' })
  })
})
