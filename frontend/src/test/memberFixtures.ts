import type { MemberBooking, MemberCard, PackageRegistration } from '../api/memberApi'

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
