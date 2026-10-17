import type { MemberCard, PackageRegistration } from '../api/memberApi'
import type { MemberSummary } from '../api/receptionApi'

// Wire shapes of the BE-05 records, deliberately different from the member detail DTOs.
export function deskCard(item: MemberCard) {
  return {
    id: item.id, cardCode: item.cardCode, memberId: item.memberId,
    memberCode: item.memberCode, memberName: item.memberFullName,
    tierCode: item.tierCode, tierName: item.tierName, status: item.status,
    startDate: item.startDate, endDate: item.endDate, amount: item.pricePaid, requestedAt: item.createdAt,
  }
}

export function deskPackage(item: PackageRegistration) {
  return {
    id: item.id, registrationCode: item.registrationCode, memberId: item.memberId,
    memberCode: 'MEM-0002', memberName: 'Test Member', packageId: item.packageId,
    packageName: item.packageName, status: item.status, startDate: item.startDate, endDate: item.endDate,
    totalSessions: item.totalSessions, remainingSessions: item.remainingSessions,
    amount: item.paidAmount, requestedAt: item.createdAt,
  }
}

export function deskOverview(member: Partial<MemberSummary>, cards: MemberCard[], registrations: PackageRegistration[]) {
  return {
    profile: member,
    currentCard: cards.filter((item) => item.status === 'ACTIVE').map(deskCard)[0] ?? null,
    pendingCardRequest: cards.filter((item) => item.status === 'PENDING_PAYMENT').map(deskCard)[0] ?? null,
    activePackageRegistrations: registrations.filter((item) => item.status === 'ACTIVE').map(deskPackage),
    pendingPackageRegistrations: registrations.filter((item) => item.status === 'PENDING_PAYMENT').map(deskPackage),
    recentCheckIns: [],
  }
}
