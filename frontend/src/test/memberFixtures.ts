import type { CardTier } from '../api/cardsApi'
import type { MemberBooking, MemberCard, PackageRegistration } from '../api/memberApi'
import type { Sport, SportPackage } from '../api/packagesApi'
import type { Invoice, Payment } from '../api/paymentsApi'

/** Sample backend answers for the member screens (FE-02). Override any field per test. */

export function card(overrides: Partial<MemberCard> = {}): MemberCard {
  return {
    id: 1,
    cardCode: 'CARD-0001',
    memberId: 2,
    memberCode: 'MEM-0002',
    memberFullName: 'Taylor Nguyen',
    tierId: 3,
    tierCode: 'VIP',
    tierName: 'VIP',
    tierRank: 4,
    upgradeRequest: false,
    replacesCardId: null,
    discountPercentage: 10,
    startDate: '2026-07-05',
    endDate: '2027-07-04',
    status: 'ACTIVE',
    pricePaid: 600000,
    paymentId: 31,
    paymentCode: 'PAY-0031',
    createdAt: '2026-07-05T10:00:00',
    ...overrides,
  }
}

export function registration(overrides: Partial<PackageRegistration> = {}): PackageRegistration {
  return {
    id: 11,
    registrationCode: 'REG-0011',
    memberId: 2,
    packageId: 4,
    packageCode: 'FB-30',
    packageName: 'Football 30 days',
    sportName: 'Football',
    trainingFormat: 'COACH_LED',
    channel: 'ONLINE',
    originalPrice: 900000,
    discountPercentage: 10,
    paidAmount: 810000,
    totalSessions: 8,
    remainingSessions: 6,
    startDate: '2026-10-01',
    endDate: '2026-10-30',
    status: 'ACTIVE',
    paymentId: 41,
    paymentCode: 'PAY-0041',
    activatedAt: '2026-10-01T09:00:00',
    createdAt: '2026-09-30T20:00:00',
    ...overrides,
  }
}

export function booking(overrides: Partial<MemberBooking> = {}): MemberBooking {
  return {
    id: 21,
    bookingCode: 'BK-0021',
    sessionId: 5,
    sessionDate: '2026-10-14',
    startTime: '17:00:00',
    endTime: '18:30:00',
    sportName: 'Football',
    trainingType: 'COACH_LED',
    packageRegistrationId: 11,
    status: 'CONFIRMED',
    bookedAt: '2026-10-12T08:00:00',
    ...overrides,
  }
}

export function sport(overrides: Partial<Sport> = {}): Sport {
  return {
    id: 1,
    code: 'FOOTBALL',
    name: 'Football',
    venueType: 'FIELD',
    description: null,
    imageUrl: null,
    displayOrder: 1,
    ...overrides,
  }
}

/** Football coach-led 30 days, 8 sessions of 90 minutes, 900.000 VND (seed PK-0xx). */
export function sportPackage(overrides: Partial<SportPackage> = {}): SportPackage {
  return {
    id: 4,
    code: 'PK-024',
    name: 'Football - 30-day package',
    sportId: 1,
    sportName: 'Football',
    sportCode: 'FOOTBALL',
    trainingFormat: 'COACH_LED',
    durationDays: 30,
    sessionCount: 8,
    sessionMinutes: 90,
    priceAmount: 900000,
    description: 'Train with a coach in an eligible class.',
    isActive: true,
    ...overrides,
  }
}

export function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    id: 52,
    code: 'PAY-0052',
    status: 'PENDING',
    amount: 810000,
    method: 'BANK_TRANSFER',
    itemDescription: 'Football - 30-day package',
    originalPrice: 900000,
    discount: 90000,
    paymentTime: null,
    bankTransferInstructions: {
      accountName: 'SPORTIFY CENTER',
      accountNumber: '0123456789',
      bank: 'Demo Bank',
      transferContent: 'PAY-0052',
    },
    memberId: 2,
    memberCode: 'MEM-0002',
    createdAt: '2026-10-13T09:30:00',
    ...overrides,
  }
}

/**
 * Card tiers with the exact values of the V10 seed
 * (backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql): code, name,
 * price, durationMonths, discountPercentage and description. Ids 1, 2 and 3 follow the insert
 * order of the seed. cardTier() returns VIP Member; override any field per test.
 */
export function cardTier(overrides: Partial<CardTier> = {}): CardTier {
  return {
    id: 3,
    code: 'VIP',
    tierRank: 4,
    name: 'VIP Member',
    price: 600000,
    durationMonths: 12,
    discountPercentage: 10,
    description: 'VIP tier card valid for 12 months. 10% discount on 30-day and 90-day packages.',
    isActive: true,
    ...overrides,
  }
}

export const STANDARD_TIER = cardTier({
  id: 1,
  code: 'STANDARD',
  tierRank: 1,
  name: 'Standard',
  price: 0,
  durationMonths: 0,
  discountPercentage: 0,
  description: 'Standard membership card. Free permanent membership with 0% discount.',
})
export const GOLD_TIER = cardTier({
  id: 2,
  code: 'GOLD',
  tierRank: 3,
  name: 'Gold Member',
  price: 300000,
  durationMonths: 12,
  discountPercentage: 5,
  description: 'Gold tier card valid for 12 months. 5% discount on 30-day and 90-day packages.',
})
export const VIP_TIER = cardTier()
export const CARD_TIERS = [STANDARD_TIER, GOLD_TIER, VIP_TIER]

/** Assumed BE-20 answer of GET /invoices/{id} for a confirmed package payment (FE-11). */
export function invoice(overrides: Partial<Invoice> = {}): Invoice {
  return {
    id: 7,
    invoiceNumber: 'INV-0007',
    paymentId: 41,
    paymentCode: 'PAY-0041',
    paymentMethod: 'BANK_TRANSFER',
    paymentTime: '2026-10-02T10:15:00',
    memberId: 2,
    memberCode: 'MEM-0002',
    memberFullName: 'Taylor Nguyen',
    subtotalAmount: 900000,
    discountAmount: 90000,
    taxAmount: 0,
    totalAmount: 810000,
    status: 'PAID',
    issueDate: '2026-10-02T10:15:00',
    lines: [
      {
        id: 1,
        itemType: 'SPORT_PACKAGE',
        description: 'Football - Coach-led - 30 days',
        quantity: 1,
        unitPrice: 810000,
        lineTotal: 810000,
      },
    ],
    ...overrides,
  }
}
