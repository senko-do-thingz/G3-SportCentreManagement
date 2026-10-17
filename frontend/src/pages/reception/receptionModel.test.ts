import { describe, expect, it } from 'vitest'
import type { CardTier } from '../../api/cardsApi'
import { ApiError } from '../../api/errors'
import { isMemberNotFound, toMemberOverview, toMemberSummary } from '../../api/receptionApi'
import { card, registration } from '../../test/memberFixtures'
import { deskOverview } from '../../test/receptionFixtures'
import {
  afterPaymentReference,
  changeDue,
  deskCardPlan,
  UNKNOWN_PLAN_MESSAGE,
  checkInVerdict,
  EMPTY_MEMBER_FORM,
  parseAmount,
  pendingRequests,
  positiveId,
  receptionPaymentPath,
  receptionDate,
  registerPath,
  saleOutcome,
  sellableTiers,
  sortedRegistrations,
  validateDeskPayment,
  validateIdentifier,
  validateMemberForm,
} from './receptionModel'

const TODAY = '2026-10-13'

function tier(overrides: Partial<CardTier> = {}): CardTier {
  return {
    id: 3,
    code: 'VIP',
    tierRank: 4,
    name: 'VIP',
    price: 600000,
    durationMonths: 12,
    discountPercentage: 10,
    description: null,
    isActive: true,
    ...overrides,
  }
}

const STANDARD = tier({ id: 1, code: 'STANDARD', tierRank: 1, name: 'Standard', price: 0, durationMonths: null, discountPercentage: 0 })
const GOLD = tier({ id: 2, code: 'GOLD', tierRank: 3, name: 'Gold', price: 300000, discountPercentage: 5 })
const VIP = tier()
const TIERS = [STANDARD, GOLD, VIP]

describe('FE-05 receptionModel - create member validation', () => {
  it('TC-FE05-001 requires full name, phone and email', () => {
    expect(validateMemberForm(EMPTY_MEMBER_FORM)).toEqual({
      fullName: 'Full name is required',
      phone: 'Phone number is required',
      email: 'Email is required',
    })
  })

  it('TC-FE05-002 matches BE-05 phone and email constraints', () => {
    const errors = validateMemberForm({ fullName: 'Test', phone: '12345', email: 'bad' })
    expect(errors.phone).toBe('Phone must be 8-15 digits with optional leading +')
    expect(errors.email).toBe('Email is invalid')
    expect(validateMemberForm({ fullName: 'Test Member', phone: '+84901234567', email: 'test@example.test' })).toEqual({})
    expect(validateMemberForm({ fullName: 'Test Member', phone: '12345678', email: 'x'.repeat(250) + '@a.test' }).email).toContain('255')
  })
})

describe('FE-05 receptionModel - desk payment', () => {
  it('TC-FE05-003 cash may exceed the amount payable and gives change', () => {
    const form = { method: 'CASH', reference: '', amountReceived: '1.000.000' }
    expect(validateDeskPayment(form, 810000)).toEqual({})
    expect(changeDue(form, 810000)).toBe(190000)
  })

  it('TC-FE05-004 rejects an amount below the amount payable and a non numeric amount', () => {
    expect(validateDeskPayment({ method: 'CASH', reference: '', amountReceived: '800000' }, 810000).amountReceived).toBe(
      'Amount received is less than the amount payable',
    )
    expect(validateDeskPayment({ method: 'CASH', reference: '', amountReceived: '81k' }, 810000).amountReceived).toBe(
      'Amount received must be a whole number of VND',
    )
    expect(validateDeskPayment({ method: 'CASH', reference: '', amountReceived: ' ' }, 810000).amountReceived).toBe(
      'Amount received is required',
    )
  })

  it('TC-FE05-005 other methods need a reference and the exact amount, and give no change', () => {
    const errors = validateDeskPayment({ method: 'BANK_TRANSFER', reference: '', amountReceived: '900000' }, 810000)
    expect(errors.reference).toBe('Payment reference is required for this method')
    expect(errors.amountReceived).toBe('Amount received must equal the amount payable for this method')
    expect(changeDue({ method: 'BANK_TRANSFER', reference: 'FT1', amountReceived: '900000' }, 810000)).toBe(0)
    expect(validateDeskPayment({ method: 'MOMO', reference: 'MM-1', amountReceived: '810000' }, 810000)).toEqual({})
    expect(validateDeskPayment({ method: 'CHEQUE', reference: '', amountReceived: '1' }, 1).method).toBe(
      'Choose a payment method',
    )
  })

  it('TC-FE05-006 parseAmount accepts plain digits or groups of three with one separator only', () => {
    expect(parseAmount('810000')).toBe(810000)
    expect(parseAmount('810.000')).toBe(810000)
    expect(parseAmount('810,000')).toBe(810000)
    expect(parseAmount('810 000')).toBe(810000)
    expect(parseAmount('1.000.000')).toBe(1000000)
    expect(parseAmount('200000.00')).toBeNull()
    expect(parseAmount('1.5')).toBeNull()
    expect(parseAmount('1,000.000')).toBeNull()
    expect(parseAmount('-5')).toBeNull()
    expect(parseAmount('')).toBeNull()
    expect(validateDeskPayment({ method: 'CASH', reference: '', amountReceived: '200000.00' }, 1).amountReceived).toBe(
      'Amount received must be a whole number of VND',
    )
  })
})

describe('FE-05 receptionModel - membership card at the desk (FE-04 planCard)', () => {
  it('TC-FE05-007 a sellable plan keeps the planCard dates, kind and price', () => {
    const vip = card({ tierId: 3, startDate: '2026-07-05', endDate: '2027-07-04' })
    expect(deskCardPlan(VIP, [vip], TIERS, TODAY)).toMatchObject({
      sellable: true,
      message: null,
      plan: { kind: 'renew', startDate: '2027-07-05', endDate: '2028-07-04', price: 600000 },
    })
  })

  it('TC-FE05-008 a member without a paid card buys a new card from today', () => {
    expect(deskCardPlan(VIP, [], TIERS, TODAY).plan).toMatchObject({
      kind: 'new',
      startDate: TODAY,
      endDate: '2027-10-12',
      price: 600000,
    })
  })

  it('TC-FE05-009 a cheaper tier starts after the current card (later)', () => {
    const vip = card({ tierId: 3, startDate: '2026-07-05', endDate: '2027-07-04' })
    expect(deskCardPlan(GOLD, [vip], TIERS, TODAY)).toMatchObject({
      sellable: true,
      plan: { kind: 'later', startDate: '2027-07-05', price: 300000 },
    })
  })

  it('TC-FE05-010 a dearer tier upgrades today for the difference and keeps the end date', () => {
    const gold = card({ tierId: 2, tierCode: 'GOLD', tierRank: 3, tierName: 'Gold', pricePaid: 300000, startDate: '2026-07-05', endDate: '2027-07-04' })
    expect(deskCardPlan(VIP, [gold], TIERS, TODAY)).toMatchObject({
      sellable: true,
      plan: { kind: 'upgrade', startDate: TODAY, endDate: '2027-07-04', price: 300000 },
    })
  })

  it('TC-FE05-011 a blocked upgrade is not sellable and uses the desk wording', () => {
    const gold = card({ id: 1, tierId: 2, startDate: '2026-07-05', endDate: '2027-07-04' })
    const queued = card({ id: 2, tierId: 2, startDate: '2027-07-05', endDate: '2028-07-04' })
    const desk = deskCardPlan(VIP, [gold, queued], TIERS, TODAY)
    expect(desk.sellable).toBe(false)
    expect(desk.plan.kind).toBe('blocked')
    expect(desk.message).toBe("The member's card is already renewed from 2027-07-05. It can be upgraded once that term has started.")
  })

  it('TC-FE05-012 only active paid tiers are sold', () => {
    expect(sellableTiers([...TIERS, tier({ id: 9, isActive: false })]).map((item) => item.id)).toEqual([2, 3])
  })

  it('TC-FE05-017 an ACTIVE card of a tier that is no longer listed is never treated as no card', () => {
    const retired = card({ tierId: 8, tierCode: 'PLATINUM', tierRank: null, tierName: 'Platinum', startDate: '2026-07-05', endDate: '2027-07-04' })
    const desk = deskCardPlan(VIP, [retired], TIERS, TODAY)
    expect(desk.plan.kind).toBe('unknown')
    expect(desk.plan.startDate).not.toBe(TODAY)
    expect(desk).toMatchObject({ sellable: false, message: UNKNOWN_PLAN_MESSAGE })
  })
})

describe('FE-05 receptionModel - sale outcome', () => {
  it('TC-FE05-018 the outcome comes from the payment, not only from the item status', () => {
    expect(saleOutcome({ status: 'ACTIVE', paymentId: 60 })).toBe('paid')
    expect(saleOutcome({ status: 'ACTIVE', paymentId: null })).toBe('unrecorded')
    expect(saleOutcome({ status: 'PENDING_PAYMENT', paymentId: 61 })).toBe('pending')
    expect(saleOutcome({ status: 'CANCELLED', paymentId: null })).toBe('other')
  })
})

describe('FE-05 receptionModel - lists, paths and readers', () => {
  it('uses the centre calendar date across UTC midnight boundaries', () => {
    expect(receptionDate(new Date('2026-10-16T16:59:59Z'))).toBe('2026-10-16')
    expect(receptionDate(new Date('2026-10-16T17:00:00Z'))).toBe('2026-10-17')
  })
  it('TC-FE05-013 pending requests merge cards and packages, oldest first', () => {
    const rows = pendingRequests(
      [card({ id: 5, cardCode: 'CARD-0005', status: 'PENDING_PAYMENT', createdAt: '2026-10-12T09:00:00', memberFullName: 'Taylor Nguyen' })],
      [registration({ id: 40, registrationCode: 'REG-0040', status: 'PENDING_PAYMENT', createdAt: '2026-10-11T08:00:00' })],
    )
    expect(rows.map((row) => row.key)).toEqual(['package-40', 'card-5'])
    expect(rows[1].memberLabel).toBe('Taylor Nguyen (MEM-0002)')
    expect(rows[0].memberLabel).toBe('Member #2')
  })

  it('TC-FE05-014 profile registrations list pending first, then active, then the rest', () => {
    const sorted = sortedRegistrations([
      registration({ id: 1, status: 'EXPIRED' }),
      registration({ id: 2, status: 'ACTIVE' }),
      registration({ id: 3, status: 'PENDING_PAYMENT' }),
    ])
    expect(sorted.map((item) => item.id)).toEqual([3, 2, 1])
  })

  it('TC-FE05-015 paths, ids, verdicts and the identifier check', () => {
    expect(registerPath({ memberId: 2, packageId: 4 })).toBe('/reception/register?memberId=2&packageId=4')
    expect(receptionPaymentPath(52, 2)).toBe('/reception/payments/52?memberId=2')
    expect(receptionPaymentPath(52)).toBe('/reception/payments/52')
    expect(positiveId('12')).toBe(12)
    expect(positiveId('0')).toBeNull()
    expect(positiveId('1e3')).toBeNull()
    expect(checkInVerdict({ result: 'ALLOWED' })).toBe('recorded')
    expect(checkInVerdict({ result: 'DENIED' })).toBe('rejected')
    expect(validateIdentifier('  ')).toBe('Enter a member code or scan the member card')
    expect(validateIdentifier('MB-0248')).toBeNull()
  })

  it('TC-FE05-016 the overview reader accepts only MemberOverviewResponse', () => {
    const wire = deskOverview({ userId: 2, fullName: 'Test Member', status: 'ACTIVE' }, [card()], [registration()])
    expect(toMemberOverview(wire)).toMatchObject({ profile: { userId: 2, status: 'ACTIVE' }, currentCard: { id: 1 } })
    expect(() => toMemberOverview({ member: wire.profile, cards: [], registrations: [] })).toThrow('unexpected response')
    expect(() => toMemberOverview({ ...wire, profile: null })).toThrow('unexpected response')
    expect(() => toMemberOverview({ ...wire, currentCard: {} })).toThrow('unexpected response')
    expect(() => toMemberOverview({ ...wire, activePackageRegistrations: [null] })).toThrow('unexpected response')
    expect(() => toMemberSummary({ userId: 3 })).toThrow('unexpected response')
  })

  it('TC-FE05-020 only the proposed 404 message means a missing member; router state carries the paid notice', () => {
    expect(isMemberNotFound(new ApiError(404, 'Member not found'))).toBe(true)
    expect(isMemberNotFound(new ApiError(404, 'Resource not found'))).toBe(false)
    expect(isMemberNotFound(new ApiError(500, 'Member not found'))).toBe(false)
    expect(afterPaymentReference({ paidReference: 'PAY-0041' })).toBe('PAY-0041')
    expect(afterPaymentReference(null)).toBeNull()
    expect(afterPaymentReference({ paidReference: 41 })).toBeNull()
  })
})
