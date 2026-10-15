import { describe, expect, it } from 'vitest'
import { card, CARD_TIERS, cardTier, GOLD_TIER, registration, STANDARD_TIER, VIP_TIER } from '../../test/memberFixtures'
import {
  addMonths,
  canCancelCard,
  cardEndDate,
  cardInForce,
  displayStatus,
  isFreeTier,
  pendingCard,
  planCard,
  PLAN_ACTION_LABELS,
  renewableCardId,
  sortCards,
  sortRegistrations,
  sortTiers,
  tabFromParam,
  termDays,
  tierPerks,
  validityLabel,
} from './cardsModel'

/** FE-04 pure rules. Today is 2026-10-13. planCard mirrors MembershipCardServiceImpl.planCard. */

const TODAY = '2026-10-13'
const GOLD_CARD = card({ id: 5, cardCode: 'CARD-0005', tierId: 2, tierCode: 'GOLD', tierName: 'Gold', discountPercentage: 5, pricePaid: 300000 })
const VIP_CARD = card()

describe('FE-04 card dates', () => {
  it('TC-FE04-001 addMonths clamps to the last day of a shorter month like LocalDate.plusMonths', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonths('2024-01-31', 1)).toBe('2024-02-29')
    expect(addMonths('2026-10-13', 12)).toBe('2027-10-13')
  })

  it('TC-FE04-002 a card ends the day before the same date N months later', () => {
    expect(cardEndDate('2026-10-13', 12)).toBe('2027-10-12')
    expect(cardEndDate('2027-07-05', 12)).toBe('2028-07-04')
    expect(cardEndDate('2024-02-29', 12)).toBe('2025-02-27')
    expect(cardEndDate('2026-12-31', 1)).toBe('2027-01-30')
  })
})

describe('FE-04 planCard', () => {
  it('TC-FE04-003 no paid card: new card from today, full price', () => {
    const plan = planCard(GOLD_TIER, [], CARD_TIERS, TODAY)
    expect(plan).toMatchObject({ kind: 'new', startDate: '2026-10-13', endDate: '2027-10-12', price: 300000 })
  })

  it('TC-FE04-004 same tier: renew from the day after the current end, full price', () => {
    const plan = planCard(VIP_TIER, [VIP_CARD], CARD_TIERS, TODAY)
    expect(plan).toMatchObject({ kind: 'renew', startDate: '2027-07-05', endDate: '2028-07-04', price: 600000 })
  })

  it('TC-FE04-005 dearer tier: upgrade from today, keeps the end date, pays the difference', () => {
    const plan = planCard(VIP_TIER, [GOLD_CARD], CARD_TIERS, TODAY)
    expect(plan).toMatchObject({ kind: 'upgrade', startDate: '2026-10-13', endDate: '2027-07-04', price: 300000, tierPrice: 600000 })
    expect(plan.baseCard?.cardCode).toBe('CARD-0005')
  })

  it('TC-FE04-006 cheaper tier: starts the day after the current card ends, full price', () => {
    const plan = planCard(GOLD_TIER, [VIP_CARD], CARD_TIERS, TODAY)
    expect(plan).toMatchObject({ kind: 'later', startDate: '2027-07-05', endDate: '2028-07-04', price: 300000 })
  })

  it('TC-FE04-007 upgrade is blocked while a renewal of the current card is queued', () => {
    const queued = card({ id: 6, cardCode: 'CARD-0006', tierId: 2, tierName: 'Gold', startDate: '2027-07-05', endDate: '2028-07-04' })
    const plan = planCard(VIP_TIER, [GOLD_CARD, queued], CARD_TIERS, TODAY)
    expect(plan.kind).toBe('blocked')
    expect(plan.reason).toMatch(/already renewed from 2027-07-05/)
  })

  it('TC-FE04-008 renewal compares with the last card: VIP in force and Gold queued, VIP starts after Gold', () => {
    const queuedGold = card({ id: 7, tierId: 2, tierName: 'Gold', startDate: '2027-07-05', endDate: '2028-07-04' })
    expect(planCard(VIP_TIER, [VIP_CARD, queuedGold], CARD_TIERS, TODAY)).toMatchObject({
      kind: 'later',
      startDate: '2028-07-05',
      endDate: '2029-07-04',
    })
    expect(planCard(GOLD_TIER, [VIP_CARD, queuedGold], CARD_TIERS, TODAY)).toMatchObject({
      kind: 'renew',
      startDate: '2028-07-05',
    })
  })

  it('TC-FE04-009 pending, cancelled, replaced, standard and past-end cards are ignored', () => {
    const cards = [
      card({ id: 8, status: 'PENDING_PAYMENT' }),
      card({ id: 9, status: 'CANCELLED' }),
      card({ id: 10, status: 'REPLACED' }),
      card({ id: 11, tierId: 1, tierName: 'Standard', startDate: '2026-01-01', endDate: null }),
      card({ id: 12, startDate: '2025-10-01', endDate: '2026-09-30' }),
    ]
    expect(planCard(VIP_TIER, cards, CARD_TIERS, TODAY)).toMatchObject({ kind: 'new', startDate: TODAY, endDate: '2027-10-12' })
  })

  it('TC-FE04-010 a card ending today still counts (end date >= today)', () => {
    const endsToday = card({ startDate: '2025-10-14', endDate: TODAY })
    expect(planCard(VIP_TIER, [endsToday], CARD_TIERS, TODAY)).toMatchObject({ kind: 'renew', startDate: '2026-10-14' })
  })

  it('TC-FE04-011 current tier not on sale any more: price unknown, backend decides', () => {
    const oldTierCard = card({ tierId: 99, tierName: 'Legacy', tierRank: null })
    expect(planCard(GOLD_TIER, [oldTierCard], CARD_TIERS, TODAY)).toMatchObject({ kind: 'unknown', price: null })
  })

  it('TC-FE04-012 price difference is exact for decimal prices', () => {
    const tiers = [cardTier({ id: 2, code: 'GOLD', tierRank: 3, price: '300000.10' }), cardTier({ id: 3, code: 'VIP', tierRank: 4, price: '600000.30' })]
    const plan = planCard(tiers[1], [card({ tierId: 2, tierCode: 'GOLD', tierRank: 3, pricePaid: '300000.10' })], tiers, TODAY)
    expect(plan.price).toBe(300000.2)
  })
})

describe('PR40 authoritative replacement history', () => {
  const silver = cardTier({ id: 4, code: 'SILVER', tierRank: 2, price: 550000 })
  const gold = cardTier({ id: 2, code: 'GOLD', tierRank: 3, price: 500000 })
  const vip = cardTier({ price: 1000000 })
  const tiers = [silver, gold, vip]
  const predecessor = card({ id: 1, tierId: 4, tierCode: 'SILVER', tierRank: 2, status: 'REPLACED', pricePaid: 200000 })
  const active = card({ id: 3, tierId: 2, tierCode: 'GOLD', tierRank: 3, pricePaid: 300000, upgradeRequest: true, replacesCardId: 1 })

  it('RV40-FE-01 uses the actual 200000 plus 300000 chain', () => {
    expect(planCard(vip, [active, predecessor], tiers, TODAY)).toMatchObject({ kind: 'upgrade', price: 500000 })
  })

  it('RV40-FE-02 excludes unrelated REPLACED history with the same end date', () => {
    const unrelated = card({ id: 2, status: 'REPLACED', pricePaid: 100000 })
    expect(planCard(vip, [unrelated, active, predecessor], tiers, TODAY)).toMatchObject({ kind: 'upgrade', price: 500000 })
  })

  it.each([
    ['orphan upgrade', [card({ ...active, replacesCardId: null })]],
    ['missing predecessor', [active]],
    ['cycle', [active, card({ ...predecessor, upgradeRequest: true, replacesCardId: 3 })]],
    ['missing metadata', [card({ ...active, upgradeRequest: undefined }), predecessor]],
    ['missing paid snapshot', [active, card({ ...predecessor, pricePaid: null })]],
    ['another member', [active, card({ ...predecessor, memberId: 999 })]],
    ['another term', [active, card({ ...predecessor, endDate: '2027-08-01' })]],
    ['duplicate predecessor id', [active, predecessor, predecessor]],
  ])('RV40-FE-03 fails closed for %s', (_name, history) => {
    for (const audience of ['member', 'desk'] as const) {
      expect(planCard(vip, history, tiers, TODAY, audience)).toMatchObject({ kind: 'unknown', price: null })
    }
  })

  it('RV40-FE-04 accepts 20 predecessor edges and rejects 21', () => {
    const history = Array.from({ length: 22 }, (_, index) => card({
      id: 100 + index, tierId: 2, status: index === 0 ? 'ACTIVE' : 'REPLACED', pricePaid: 100,
      upgradeRequest: index < 21, replacesCardId: index < 21 ? 101 + index : null,
    }))
    expect(planCard(vip, history, tiers, TODAY)).toMatchObject({ kind: 'unknown', price: null })
    history[20] = card({ ...history[20], upgradeRequest: false, replacesCardId: null })
    expect(planCard(vip, history, tiers, TODAY)).toMatchObject({ kind: 'upgrade', price: 997900 })
  })

  it('TC-BE03-72 RV40-FE-05 uses rank and blocks zero fee for members while allowing staff', () => {
    const paidSilver = card({ tierId: 4, tierCode: 'SILVER', tierRank: 2, pricePaid: 550000 })
    expect(planCard(gold, [paidSilver], tiers, TODAY)).toMatchObject({ kind: 'blocked', price: 0 })
    expect(planCard(gold, [paidSilver], tiers, TODAY, 'desk')).toMatchObject({ kind: 'upgrade', price: 0 })
  })

  it('RV40-FE-06 preserves historical payments after catalog price changes', () => {
    const changedGold = cardTier({ ...gold, price: 850000 })
    expect(planCard(vip, [active, predecessor], [silver, changedGold, vip], TODAY).price).toBe(500000)
  })

  it.each([null, 0, -1, 2.5, NaN])('RV40-FE-07 rejects invalid target rank %s', (tierRank) => {
    expect(planCard(cardTier({ ...vip, tierRank }), [active, predecessor], tiers, TODAY)).toMatchObject({ kind: 'unknown', price: null })
  })

  it('RV40-FE-08 uses the mapped rank of a deactivated tier absent from the catalog', () => {
    expect(planCard(vip, [active, predecessor], [vip], TODAY)).toMatchObject({ kind: 'upgrade', price: 500000 })
  })
})

describe('FE-04 helpers', () => {
  it('TC-FE04-013 cardInForce ignores a card that starts later', () => {
    const queued = card({ id: 6, startDate: '2027-07-05', endDate: '2028-07-04' })
    expect(cardInForce([queued], CARD_TIERS, TODAY)).toBeNull()
    expect(cardInForce([VIP_CARD, queued], CARD_TIERS, TODAY)?.id).toBe(VIP_CARD.id)
  })

  it('TC-FE04-014 free tier, perks, validity and tier order', () => {
    expect(isFreeTier(STANDARD_TIER)).toBe(true)
    expect(isFreeTier(GOLD_TIER)).toBe(false)
    expect(tierPerks(GOLD_TIER)[0]).toBe('5% off packages longer than a single visit')
    expect(tierPerks(STANDARD_TIER)[0]).toBe('0% off sport packages')
    expect(validityLabel(VIP_TIER)).toBe('12 months validity')
    expect(validityLabel(cardTier({ durationMonths: 1 }))).toBe('1 month validity')
    expect(sortTiers([VIP_TIER, STANDARD_TIER, cardTier({ id: 4, isActive: false }), GOLD_TIER]).map((tier) => tier.id)).toEqual([1, 2, 3])
  })

  it('TC-FE04-015 only a pending request can be cancelled', () => {
    expect(canCancelCard({ status: 'PENDING_PAYMENT' })).toBe(true)
    for (const status of ['ACTIVE', 'EXPIRED', 'CANCELLED', 'REPLACED']) {
      expect(canCancelCard({ status })).toBe(false)
    }
  })

  it('TC-FE04-016 pendingCard returns the newest pending request', () => {
    const older = card({ id: 1, status: 'PENDING_PAYMENT', createdAt: '2026-10-01T08:00:00' })
    const newer = card({ id: 2, status: 'PENDING_PAYMENT', createdAt: '2026-10-12T08:00:00' })
    expect(pendingCard([older, newer, VIP_CARD])?.id).toBe(2)
    expect(pendingCard([VIP_CARD])).toBeNull()
  })

  it('TC-FE04-017 an ACTIVE row past its end date is shown as EXPIRED', () => {
    expect(displayStatus({ status: 'ACTIVE', endDate: '2026-10-12' }, TODAY)).toBe('EXPIRED')
    expect(displayStatus({ status: 'ACTIVE', endDate: TODAY }, TODAY)).toBe('ACTIVE')
    expect(displayStatus({ status: 'PENDING_PAYMENT', endDate: '2026-01-01' }, TODAY)).toBe('PENDING_PAYMENT')
  })

  it('TC-FE04-018 registrations and cards sort pending, active, expired, cancelled, newest first', () => {
    const list = [
      registration({ id: 1, status: 'CANCELLED' }),
      registration({ id: 2, status: 'EXPIRED' }),
      registration({ id: 3, status: 'ACTIVE', createdAt: '2026-09-01T08:00:00' }),
      registration({ id: 4, status: 'PENDING_PAYMENT' }),
      registration({ id: 5, status: 'ACTIVE', createdAt: '2026-10-01T08:00:00' }),
    ]
    expect(sortRegistrations(list, TODAY).map((item) => item.id)).toEqual([4, 5, 3, 2, 1])
    const cards = [card({ id: 1, status: 'REPLACED' }), card({ id: 2, status: 'PENDING_PAYMENT' }), card({ id: 3 })]
    expect(sortCards(cards, TODAY).map((item) => item.id)).toEqual([2, 3, 1])
  })

  it('TC-FE04-019 termDays counts both ends; tab param defaults to packages', () => {
    expect(termDays('2026-10-02', '2026-10-31')).toBe(30)
    expect(termDays('2026-10-02', null)).toBeNull()
    expect(tabFromParam('card')).toBe('card')
    expect(tabFromParam(null)).toBe('packages')
    expect(tabFromParam('other')).toBe('packages')
  })

  it('TC-FE04-020 renewableCardId: latest ended paid card, only when nothing is in force or queued', () => {
    const older = card({ id: 1, status: 'EXPIRED', startDate: '2024-07-05', endDate: '2025-07-04' })
    const latest = card({ id: 2, status: 'EXPIRED', startDate: '2025-07-05', endDate: '2026-07-04' })
    const replaced = card({ id: 3, status: 'REPLACED', endDate: '2026-09-30' })
    const cancelled = card({ id: 4, status: 'CANCELLED', endDate: '2026-09-30' })
    expect(renewableCardId([older, latest, replaced, cancelled], TODAY)).toBe(2)
    expect(renewableCardId([older, latest, VIP_CARD], TODAY)).toBeNull()
    const queued = card({ id: 5, startDate: '2027-01-01', endDate: '2027-12-31' })
    expect(renewableCardId([latest, queued], TODAY)).toBeNull()
    expect(renewableCardId([replaced, cancelled, card({ id: 6, status: 'PENDING_PAYMENT' })], TODAY)).toBeNull()
  })

  it('TC-FE04-021 unknown plan uses the neutral label Choose card', () => {
    expect(PLAN_ACTION_LABELS.unknown).toBe('Choose card')
  })
})
