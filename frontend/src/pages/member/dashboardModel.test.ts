import { describe, expect, it } from 'vitest'
import { booking, card, registration } from '../../test/memberFixtures'
import {
  activeRegistrations,
  canBook,
  currentCard,
  dashboardState,
  heroVariant,
  isExhausted,
  isPaidCard,
  remainingSessionCount,
  paymentSummaryPath,
  pendingItems,
  registrationTitle,
  sessionShare,
  shortTime,
  startsLater,
  toIsoDate,
  trainingFormatLabel,
  upcomingBookings,
} from './dashboardModel'

/**
 * FE-02: rules behind the Member Dashboard (F1-07). Today is fixed at 2026-10-13 and now at
 * 2026-10-13 09:30 local time.
 */
const TODAY = '2026-10-13'
const NOW = new Date(2026, 9, 13, 9, 30)

describe('FE-02 dashboard model', () => {
  it('TC-FE02-001 toIsoDate uses the local calendar date', () => {
    expect(toIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })

  it('TC-FE02-002 a member with nothing bought is in the New Standard Member state', () => {
    expect(dashboardState([], [])).toBe('new-standard')
  })

  it('TC-FE02-003 a free STANDARD card and cancelled requests keep the New Standard Member state', () => {
    const cards = [card({ tierCode: 'STANDARD', tierName: 'Standard', discountPercentage: 0 }), card({ id: 2, status: 'CANCELLED' })]
    const registrations = [registration({ status: 'CANCELLED' })]
    expect(dashboardState(cards, registrations)).toBe('new-standard')
  })

  it('TC-FE02-004 a package registration, even unpaid, gives the After Purchase state', () => {
    expect(dashboardState([], [registration({ status: 'PENDING_PAYMENT' })])).toBe('after-purchase')
  })

  it('TC-FE02-005 a paid card request alone gives the After Purchase state', () => {
    expect(dashboardState([card({ status: 'PENDING_PAYMENT' })], [])).toBe('after-purchase')
  })

  it('TC-FE02-006 the current card is ACTIVE and inside its dates; otherwise the tier is Standard', () => {
    expect(currentCard([card()], TODAY)?.tierName).toBe('VIP')
    expect(currentCard([card({ status: 'PENDING_PAYMENT' })], TODAY)).toBeNull()
    expect(currentCard([card({ endDate: '2026-10-12' })], TODAY)).toBeNull()
    expect(currentCard([card({ startDate: '2026-10-14' })], TODAY)).toBeNull()
    expect(currentCard([card({ endDate: TODAY })], TODAY)?.tierName).toBe('VIP')
  })

  it('TC-FE02-007 with two cards in force the highest discount wins', () => {
    const gold = card({ id: 7, tierCode: 'GOLD', tierName: 'Gold', discountPercentage: 5 })
    expect(currentCard([gold, card()], TODAY)?.tierName).toBe('VIP')
    expect(currentCard([card(), gold], TODAY)?.tierName).toBe('VIP')
  })

  it('TC-FE02-008 active packages exclude pending, cancelled and past packages and sort by end date', () => {
    const list = [
      registration({ id: 1, endDate: '2026-12-01' }),
      registration({ id: 2, status: 'PENDING_PAYMENT' }),
      registration({ id: 3, status: 'ACTIVE', endDate: '2026-10-12' }),
      registration({ id: 4, endDate: '2026-10-20' }),
      registration({ id: 5, status: 'EXPIRED' }),
    ]
    expect(activeRegistrations(list, TODAY).map((item) => item.id)).toEqual([4, 1])
  })

  it('TC-FE02-009 startsLater is true only for a start date after today', () => {
    expect(startsLater({ startDate: '2026-10-20' }, TODAY)).toBe(true)
    expect(startsLater({ startDate: TODAY }, TODAY)).toBe(false)
    expect(startsLater({ startDate: null }, TODAY)).toBe(false)
  })

  it('TC-FE02-010 sessionShare is remaining over total, clamped, 0 for a total of 0 and null only without a limit', () => {
    expect(sessionShare({ totalSessions: 8, remainingSessions: 6 })).toBe(0.75)
    expect(sessionShare({ totalSessions: 8, remainingSessions: 12 })).toBe(1)
    expect(sessionShare({ totalSessions: 8, remainingSessions: null })).toBe(0)
    expect(sessionShare({ totalSessions: null, remainingSessions: null })).toBeNull()
    expect(sessionShare({ totalSessions: 0, remainingSessions: 0 })).toBe(0)
    expect(sessionShare({ totalSessions: 8, remainingSessions: 0 })).toBe(0)
  })

  it('TC-FE02-011 pending items list unpaid cards and packages, newest first, with the payment id', () => {
    const items = pendingItems(
      [card({ id: 3, status: 'PENDING_PAYMENT', paymentId: 51, paymentCode: 'PAY-0051', createdAt: '2026-10-10T08:00:00' })],
      [
        registration({ id: 12, status: 'PENDING_PAYMENT', paymentId: 52, paymentCode: 'PAY-0052', createdAt: '2026-10-12T08:00:00' }),
        registration({ id: 13 }),
      ],
    )
    expect(items.map((item) => item.key)).toEqual(['package-12', 'card-3'])
    expect(items[0]).toMatchObject({ title: 'Sport package - Football - Coach-led', reference: 'PAY-0052', paymentId: 52, amount: '810.000 VND' })
    expect(items[1]).toMatchObject({ title: 'Membership card - VIP', reference: 'PAY-0051', paymentId: 51, amount: '600.000 VND' })
  })

  it('TC-FE02-012 a pending item without a linked payment keeps a null payment id and its own code', () => {
    const [item] = pendingItems([], [registration({ status: 'PENDING_PAYMENT', paymentId: null, paymentCode: null })])
    expect(item.paymentId).toBeNull()
    expect(item.reference).toBe('REG-0011')
  })

  it('TC-FE02-013 the payment summary path is under the member payments screen', () => {
    expect(paymentSummaryPath(52)).toBe('/member/payments/52')
  })

  it('TC-FE02-014 upcoming bookings keep confirmed sessions from now on, soonest first', () => {
    const list = [
      booking({ id: 1, sessionDate: '2026-10-20' }),
      booking({ id: 2, sessionDate: '2026-10-13', startTime: '09:00:00' }),
      booking({ id: 3, sessionDate: '2026-10-13', startTime: '10:00:00' }),
      booking({ id: 4, status: 'CANCELLED' }),
      booking({ id: 5, sessionDate: '2026-10-12' }),
      booking({ id: 6, sessionDate: 'not-a-date' }),
    ]
    expect(upcomingBookings(list, NOW).map((item) => item.id)).toEqual([3, 1])
  })

  it('TC-FE02-015 labels: training format, package title and short time', () => {
    expect(trainingFormatLabel('SELF_TRAINING')).toBe('Self-training')
    expect(trainingFormatLabel('NEW_FORMAT')).toBe('New format')
    expect(trainingFormatLabel(null)).toBe('')
    expect(registrationTitle({ sportName: 'Swimming', trainingFormat: null })).toBe('Swimming')
    expect(shortTime('17:00:00')).toBe('17:00')
    expect(shortTime(null)).toBe('')
  })

  // Review round 1

  it('TC-FE02-016 remainingSessionCount clamps to 0..total and is null only without a limit', () => {
    expect(remainingSessionCount({ totalSessions: 8, remainingSessions: 12 })).toBe(8)
    expect(remainingSessionCount({ totalSessions: 8, remainingSessions: -2 })).toBe(0)
    expect(remainingSessionCount({ totalSessions: 0, remainingSessions: 0 })).toBe(0)
    expect(remainingSessionCount({ totalSessions: null, remainingSessions: null })).toBeNull()
    expect(isExhausted({ totalSessions: 8, remainingSessions: 0 })).toBe(true)
    expect(isExhausted({ totalSessions: 0, remainingSessions: 0 })).toBe(true)
    expect(isExhausted({ totalSessions: null, remainingSessions: null })).toBe(false)
  })

  it('TC-FE02-017 canBook needs ACTIVE, not ended and at least one session left (or no limit)', () => {
    expect(canBook(registration(), TODAY)).toBe(true)
    expect(canBook(registration({ totalSessions: null, remainingSessions: null }), TODAY)).toBe(true)
    expect(canBook(registration({ startDate: '2026-10-20', endDate: '2026-11-18' }), TODAY)).toBe(true)
    expect(canBook(registration({ remainingSessions: 0 }), TODAY)).toBe(false)
    expect(canBook(registration({ totalSessions: 0, remainingSessions: 0 }), TODAY)).toBe(false)
    expect(canBook(registration({ endDate: '2026-10-12' }), TODAY)).toBe(false)
    expect(canBook(registration({ status: 'PENDING_PAYMENT' }), TODAY)).toBe(false)
  })

  it('TC-FE02-018 a missing tier code counts as a paid card; STANDARD in any case does not', () => {
    expect(isPaidCard({ tierCode: null, status: 'ACTIVE' })).toBe(true)
    expect(isPaidCard({ tierCode: 'standard', status: 'ACTIVE' })).toBe(false)
    expect(isPaidCard({ tierCode: 'VIP', status: 'CANCELLED' })).toBe(false)
    expect(dashboardState([card({ tierCode: null })], [])).toBe('after-purchase')
  })

  it('TC-FE02-019 heroVariant picks welcome, book, pay or renew', () => {
    expect(heroVariant('new-standard', false, 0)).toBe('welcome')
    expect(heroVariant('after-purchase', true, 2)).toBe('book')
    expect(heroVariant('after-purchase', false, 1)).toBe('pay')
    expect(heroVariant('after-purchase', false, 0)).toBe('renew')
  })
})
