import { api } from './client'
import { ApiError } from './errors'
import {
  UNEXPECTED_RESPONSE_MESSAGE,
  type MemberCard,
  type PackageRegistration,
  type RequestSignal,
} from './memberApi'
import { normalizePage } from './page'
import type { PageResponse, SpringPage } from './types'

/**
 * Front desk endpoints used by the receptionist screens (FE-05, Figma F1-13, F1-14, F1-15).
 *
 * Contract notes:
 * - On main: GET /members, GET /membership-cards/tiers, POST /membership-cards/purchase,
 *   POST /packages/registrations, PUT /membership-cards/{id}/activate,
 *   PUT /packages/registrations/{id}/activate, POST /check-ins.
 * - Planned in BE-05: GET /members/{id}/overview, POST /members, GET /membership-cards/pending,
 *   GET /packages/registrations/pending and the date filter of GET /check-ins.
 * - Planned in BE-19: the desk sale fields paymentReference and amountReceived on the card purchase
 *   and on the package registration, and a SUCCESS payment created at once.
 * The readers below accept exactly the shapes written in docs/tests/FE-05_test_cases.md section 1.1
 * and report anything else as an unexpected response.
 */

/** Payment methods of com.sportify.payment.entity.PaymentMethod. */
export const PAYMENT_METHODS = ['CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY'] as const
export type PaymentMethod = (typeof PAYMENT_METHODS)[number]

/** One row of GET /members (com.sportify.identity.dto.MemberSearchResponse). */
export interface MemberSummary {
  /** user_account.id, which is also member_profile.id (shared primary key). */
  userId: number
  memberCode: string | null
  fullName: string
  email: string | null
  phone: string | null
  currentLevel: string | null
  /** Account status, for example ACTIVE or LOCKED. Only in the overview (BE-05), null in search rows. */
  status: string | null
}

/** Answer of GET /members/{id}/overview (BE-05), after normalization. */
export interface MemberOverview {
  member: MemberSummary
  cards: MemberCard[]
  registrations: PackageRegistration[]
}

/** Body of POST /members (BE-05): the front desk creates a member profile. */
export interface CreateMemberRequest {
  fullName: string
  phone: string
  email: string
  dateOfBirth: string | null
  mainGoal: string | null
  currentLevel: string | null
}

/** Payment fields of a desk sale (BE-19). */
export interface DeskPayment {
  paymentMethod: PaymentMethod
  /** Transfer or card slip reference; null for cash. */
  paymentReference: string | null
  amountReceived: number
}

export interface DeskPackageSaleRequest extends DeskPayment {
  memberId: number
  packageId: number
  /** ISO date, today or later. */
  startDate: string
  channel: 'RECEPTION'
}

export interface DeskCardSaleRequest extends DeskPayment {
  memberId: number
  tierId: number
}

/** Mirrors com.sportify.catalog.dto.CheckInResponse. result is ALLOWED or DENIED. */
export interface CheckInRecord {
  id: number
  memberId: number | null
  memberName: string | null
  memberCode?: string | null
  bookingId: number | null
  bookingCode: string | null
  packageRegistrationId: number | null
  checkedInAt: string | null
  result: string
  denialReason: string | null
  note: string | null
}

/** Body of POST /check-ins: identifier is a member code or a member id. */
export interface CheckInRequest {
  identifier: string
  note?: string | null
}

type Json = Record<string, unknown>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function numberOf(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

/** A list answer whose every element is an object with a numeric id; anything else is an error. */
function asIdList<T>(value: unknown): T[] {
  if (!Array.isArray(value) || !value.every((item) => isObject(item) && typeof item.id === 'number')) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T[]
}

function asObjectWithId<T>(value: unknown): T {
  if (!isObject(value) || typeof value.id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T
}

/**
 * Reads a member row: com.sportify.identity.dto.MemberSearchResponse (userId, memberCode, fullName,
 * email, phone, currentLevel), plus status in the overview. userId and fullName are required.
 */
export function toMemberSummary(value: unknown): MemberSummary {
  if (!isObject(value) || numberOf(value.userId) === null || text(value.fullName) === null) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    userId: value.userId as number,
    memberCode: text(value.memberCode),
    fullName: value.fullName as string,
    email: text(value.email),
    phone: text(value.phone),
    currentLevel: text(value.currentLevel),
    status: text(value.status),
  }
}

/**
 * Reads GET /members/{id}/overview (BE-05). The one agreed shape:
 * { member: member row with status, cards: MemberCardResponse[], registrations: PackageRegistrationResponse[] }.
 */
export function toMemberOverview(value: unknown): MemberOverview {
  if (!isObject(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    member: toMemberSummary(value.member),
    cards: asIdList<MemberCard>(value.cards),
    registrations: asIdList<PackageRegistration>(value.registrations),
  }
}

/**
 * Message of the 404 that means the member itself does not exist (agreed with BE-05, the same text
 * as ResourceNotFoundException("Member not found") in CheckInServiceImpl). Any other 404, for
 * example "Resource not found" for an endpoint that is not deployed, is a technical error.
 */
export const MEMBER_NOT_FOUND_MESSAGE = 'Member not found'

export function isMemberNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404 && error.message === MEMBER_NOT_FOUND_MESSAGE
}

export interface MemberSearchQuery extends RequestSignal {
  query: string
  page?: number
  size?: number
}

export const receptionApi = {
  searchMembers({ query, page = 0, size = 10, signal }: MemberSearchQuery): Promise<PageResponse<MemberSummary>> {
    return api
      .get<PageResponse<unknown> | SpringPage<unknown>>('/members', { signal, query: { query, page, size } })
      .then((answer) => {
        if (!isObject(answer) || !Array.isArray((answer as Json).content)) {
          throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
        }
        const normalized = normalizePage(answer)
        return { ...normalized, content: normalized.content.map(toMemberSummary) }
      })
  },

  memberOverview(memberId: number, options: RequestSignal = {}): Promise<MemberOverview> {
    return api.get<unknown>(`/members/${memberId}/overview`, options).then(toMemberOverview)
  },

  createMember(request: CreateMemberRequest): Promise<MemberSummary> {
    return api.post<unknown>('/members', request).then(toMemberSummary)
  },

  sellPackage(request: DeskPackageSaleRequest): Promise<PackageRegistration> {
    return api.post<unknown>('/packages/registrations', request).then(asObjectWithId<PackageRegistration>)
  },

  sellCard(request: DeskCardSaleRequest): Promise<MemberCard> {
    return api.post<unknown>('/membership-cards/purchase', request).then(asObjectWithId<MemberCard>)
  },

  pendingCards(options: RequestSignal = {}): Promise<MemberCard[]> {
    return api.get<unknown>('/membership-cards/pending', options).then(asIdList<MemberCard>)
  },

  pendingRegistrations(options: RequestSignal = {}): Promise<PackageRegistration[]> {
    return api.get<unknown>('/packages/registrations/pending', options).then(asIdList<PackageRegistration>)
  },

  /** Every card of a member, newest first (MembershipCardController, staff only). */
  memberCards(memberId: number, options: RequestSignal = {}): Promise<MemberCard[]> {
    return api.get<unknown>(`/membership-cards/member/${memberId}`, options).then(asIdList<MemberCard>)
  },

  /** Every package registration of a member (SportPackageController, staff only). */
  memberRegistrations(memberId: number, options: RequestSignal = {}): Promise<PackageRegistration[]> {
    return api.get<unknown>(`/packages/registrations/member/${memberId}`, options).then(asIdList<PackageRegistration>)
  },

  /** Confirms the payment of an online card request: PENDING_PAYMENT -> ACTIVE. */
  activateCard(cardId: number, paymentMethod: PaymentMethod): Promise<MemberCard> {
    return api.put<unknown>(`/membership-cards/${cardId}/activate`, { paymentMethod }).then(asObjectWithId<MemberCard>)
  },

  /** Confirms the payment of an online package registration: PENDING_PAYMENT -> ACTIVE. */
  activateRegistration(registrationId: number, paymentMethod: PaymentMethod): Promise<PackageRegistration> {
    return api
      .put<unknown>(`/packages/registrations/${registrationId}/activate`, { paymentMethod })
      .then(asObjectWithId<PackageRegistration>)
  },

  recordCheckIn(request: CheckInRequest): Promise<CheckInRecord> {
    return api.post<unknown>('/check-ins', request).then(asObjectWithId<CheckInRecord>)
  },

  /** Check-ins of one day, newest first (date filter planned in BE-05). */
  checkInsOn(date: string, options: RequestSignal = {}): Promise<CheckInRecord[]> {
    return api.get<unknown>('/check-ins', { ...options, query: { date } }).then(asIdList<CheckInRecord>)
  },
}
