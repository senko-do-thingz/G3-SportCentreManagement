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
 * BE-05 summaries omit payment links, tier rank and discounts. Read those from the existing
 * staff member detail endpoints; never infer them from a summary or a tier name.
 * BE-19 records paid sales atomically. FE-12 owns confirmation and receipts.
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
  recentCheckIns: RecentCheckIn[]
}

export interface RecentCheckIn {
  id: number
  checkedInAt: string
  result: 'ALLOWED' | 'DENIED'
  denialReason: string | null
  note: string | null
}

interface FrontDeskItem {
  id: number
  memberId: number
  status: string
}

interface BackendOverview {
  profile: MemberSummary
  currentCard: FrontDeskItem | null
  pendingCardRequest: FrontDeskItem | null
  activePackageRegistrations: FrontDeskItem[]
  pendingPackageRegistrations: FrontDeskItem[]
  recentCheckIns: RecentCheckIn[]
}

/** Body of POST /members (BE-05): the front desk creates a member profile. */
export interface CreateMemberRequest {
  fullName: string
  phone: string
  email: string
}

export interface CreatedMember {
  member: MemberSummary
  temporaryPassword: string
}

/** Payment fields of a desk sale (BE-19). */
export interface DeskPayment {
  paymentMethod: PaymentMethod
  /** Transfer or card slip reference; null for cash. */
  referenceCode: string | null
  /** Omitted for a zero-fee sale: the backend creates no payment in that case. */
  amountReceived?: number
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
 * Reads the exact BE-05 MemberOverviewResponse. Summaries stay summaries until hydrated.
 */
export function toMemberOverview(value: unknown): BackendOverview {
  if (!isObject(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return {
    profile: toMemberSummary(value.profile),
    currentCard: value.currentCard === null ? null : asDeskItem(value.currentCard),
    pendingCardRequest: value.pendingCardRequest === null ? null : asDeskItem(value.pendingCardRequest),
    activePackageRegistrations: asDeskList(value.activePackageRegistrations),
    pendingPackageRegistrations: asDeskList(value.pendingPackageRegistrations),
    recentCheckIns: asIdList<RecentCheckIn>(value.recentCheckIns).map((item) => {
      if (!item.checkedInAt || !['ALLOWED', 'DENIED'].includes(item.result)) {
        throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
      }
      return item
    }),
  }
}

function asDeskItem(value: unknown): FrontDeskItem {
  if (!isObject(value) || numberOf(value.id) === null || numberOf(value.memberId) === null || !text(value.status)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as FrontDeskItem
}

function asDeskList(value: unknown): FrontDeskItem[] {
  return asIdList<unknown>(value).map(asDeskItem)
}

/** Follow Spring pages in backend order. Detail calls are shared for each member in this load. */
async function pendingItems<T extends FrontDeskItem>(
  path: string, details: (id: number, options: RequestSignal) => Promise<T[]>, options: RequestSignal,
): Promise<T[]> {
  const rows: FrontDeskItem[] = []
  let page = 0
  let totalPages: number
  do {
    const answer = await api.get<SpringPage<unknown>>(path, { ...options, query: { page, size: 100 } })
    if (!isObject(answer) || !Array.isArray(answer.content)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    const normalized = normalizePage(answer)
    if (!Number.isSafeInteger(normalized.totalPages) || normalized.totalPages < 0) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    rows.push(...asDeskList(normalized.content))
    totalPages = normalized.totalPages
    page += 1
  } while (page < totalPages)
  const byMember = new Map<number, T[]>()
  await Promise.all([...new Set(rows.map((row) => row.memberId))].map(async (id) => {
    byMember.set(id, await details(id, options))
  }))
  return rows.flatMap((row) => {
    const item = byMember.get(row.memberId)?.find((item) => item.id === row.id && item.memberId === row.memberId)
    // A request may have been paid or cancelled between the two reads.
    return item?.status === 'PENDING_PAYMENT' ? [item] : []
  })
}

/**
 * Message of the 404 that means the member itself does not exist (BE-05 and CheckInServiceImpl). Any other 404, for
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

  async memberOverview(memberId: number, options: RequestSignal = {}): Promise<MemberOverview> {
    const overview = toMemberOverview(await api.get<unknown>(`/members/${memberId}/overview`, options))
    if (overview.profile.userId !== memberId) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    const [cards, registrations] = await Promise.all([
      receptionApi.memberCards(memberId, options), receptionApi.memberRegistrations(memberId, options),
    ])
    if ([...cards, ...registrations].some((item) => item.memberId !== memberId)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    return { member: overview.profile, cards, registrations, recentCheckIns: overview.recentCheckIns }
  },

  async createMember(request: CreateMemberRequest): Promise<CreatedMember> {
    const value = await api.post<unknown>('/members', request)
    if (!isObject(value) || !text(value.temporaryPassword)) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
    return { member: toMemberSummary(value), temporaryPassword: value.temporaryPassword as string }
  },

  sellPackage(request: DeskPackageSaleRequest): Promise<PackageRegistration> {
    return api.post<unknown>('/packages/registrations', request).then(asObjectWithId<PackageRegistration>)
  },

  sellCard(request: DeskCardSaleRequest): Promise<MemberCard> {
    return api.post<unknown>('/membership-cards/purchase', request).then(asObjectWithId<MemberCard>)
  },

  pendingCards(options: RequestSignal = {}): Promise<MemberCard[]> {
    return pendingItems('/membership-cards/pending', receptionApi.memberCards, options)
  },

  pendingRegistrations(options: RequestSignal = {}): Promise<PackageRegistration[]> {
    return pendingItems('/packages/registrations/pending', receptionApi.memberRegistrations, options)
  },

  /** Every card of a member, newest first (MembershipCardController, staff only). */
  memberCards(memberId: number, options: RequestSignal = {}): Promise<MemberCard[]> {
    return api.get<unknown>(`/membership-cards/member/${memberId}`, options).then(asIdList<MemberCard>)
  },

  /** Every package registration of a member (SportPackageController, staff only). */
  memberRegistrations(memberId: number, options: RequestSignal = {}): Promise<PackageRegistration[]> {
    return api.get<unknown>(`/packages/registrations/member/${memberId}`, options).then(asIdList<PackageRegistration>)
  },

  recordCheckIn(request: CheckInRequest): Promise<CheckInRecord> {
    return api.post<unknown>('/check-ins', request).then(asObjectWithId<CheckInRecord>)
  },

  /** Check-ins of one centre calendar day, newest first. */
  checkInsOn(date: string, options: RequestSignal = {}): Promise<CheckInRecord[]> {
    return api.get<unknown>('/check-ins', { ...options, query: { date } }).then(asIdList<CheckInRecord>)
  },
}
