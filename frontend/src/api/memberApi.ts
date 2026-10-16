import { api } from './client'
import type { UserProfile } from './types'

/**
 * Member endpoints used by the Member Dashboard (F1-07) and My Profile (F1-08).
 * The types mirror the backend DTOs on main; BigDecimal values arrive as numbers and Java
 * LocalDate, LocalTime and LocalDateTime values arrive as ISO strings.
 */

/** Mirrors com.sportify.catalog.dto.MemberCardResponse (GET /membership-cards/my). */
export interface MemberCard {
  id: number
  cardCode: string
  memberId: number
  memberCode: string | null
  memberFullName: string | null
  tierId: number
  /** member_card.tier_id is NOT NULL, so null only appears if the tier mapping is broken. */
  tierCode: string | null
  tierName: string
  tierRank: number | null
  upgradeRequest: boolean
  replacesCardId: number | null
  discountPercentage: number | null
  startDate: string | null
  endDate: string | null
  /** PENDING_PAYMENT, ACTIVE, EXPIRED, CANCELLED or REPLACED. */
  status: string
  /**
   * Price of this card request. For a PENDING_PAYMENT card it is also the amount of the linked
   * payment: MembershipCardServiceImpl.purchaseCard passes the same plan.price() to newCard
   * (pricePaid) and to PaymentService.createPendingPayment (payment.amount).
   */
  pricePaid: number | string | null
  paymentId: number | null
  paymentCode: string | null
  createdAt: string | null
}

/** Mirrors com.sportify.catalog.dto.PackageRegistrationResponse (GET /packages/registrations/my). */
export interface PackageRegistration {
  id: number
  registrationCode: string
  memberId: number
  packageId: number
  packageCode: string | null
  packageName: string
  sportName: string
  /** SELF_TRAINING or COACH_LED. */
  trainingFormat: string | null
  channel: string | null
  originalPrice: number | string | null
  discountPercentage: number | null
  paidAmount: number | string | null
  /** Null for packages without a session limit. */
  totalSessions: number | null
  remainingSessions: number | null
  startDate: string | null
  endDate: string | null
  /** PENDING_PAYMENT, ACTIVE, EXPIRED or CANCELLED. */
  status: string
  paymentId: number | null
  paymentCode: string | null
  activatedAt: string | null
  createdAt: string | null
}

/** Mirrors com.sportify.catalog.dto.BookingResponse (GET /bookings/my). */
export interface MemberBooking {
  id: number
  bookingCode: string
  sessionId: number
  /** "2026-10-20" */
  sessionDate: string
  /** "17:00:00" */
  startTime: string | null
  endTime: string | null
  sportName: string | null
  trainingType: string | null
  packageRegistrationId: number | null
  status: string
  bookedAt: string | null
}

/**
 * Body of PUT /users/me. Email is not part of it: the login email is changed
 * by the front desk only. Empty optional values are sent as null.
 */
export interface UpdateProfileRequest {
  fullName: string
  phone: string | null
  dateOfBirth: string | null
  address: string | null
  mainGoal: string | null
  currentLevel: string | null
}

/** Body of PUT /users/me/password. Confirmation is also checked by the server. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export const UNEXPECTED_RESPONSE_MESSAGE = 'The server returned an unexpected response. Please try again later.'

/** List endpoints must answer with a JSON array; anything else is reported as an error, never as an empty list. */
function asList<T>(value: unknown): T[] {
  if (!Array.isArray(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T[]
}

function asProfile(value: unknown): UserProfile {
  if (!value || typeof value !== 'object' || typeof (value as UserProfile).id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as UserProfile
}

export interface RequestSignal {
  signal?: AbortSignal
}

export const memberApi = {
  me(options: RequestSignal = {}): Promise<UserProfile> {
    return api.get<unknown>('/users/me', options).then(asProfile)
  },

  myCards(options: RequestSignal = {}): Promise<MemberCard[]> {
    return api.get<unknown>('/membership-cards/my', options).then(asList<MemberCard>)
  },

  myRegistrations(options: RequestSignal = {}): Promise<PackageRegistration[]> {
    return api.get<unknown>('/packages/registrations/my', options).then(asList<PackageRegistration>)
  },

  /**
   * Upcoming bookings. The when filter comes with BE-12; until it is merged the backend ignores
   * the parameter and returns every booking, so the dashboard filters the list again itself.
   */
  myUpcomingBookings(options: RequestSignal = {}): Promise<MemberBooking[]> {
    return api
      .get<unknown>('/bookings/my', { ...options, query: { when: 'UPCOMING' } })
      .then(asList<MemberBooking>)
  },

  updateProfile(request: UpdateProfileRequest): Promise<UserProfile | undefined> {
    return api.put<UserProfile | undefined>('/users/me', request)
  },

  /**
   * authFailure 'refresh-then-throw': a 401 first refreshes the token and retries once, so an
   * expired access token never looks like a wrong password. A 401 on the retry (fresh token) is
   * thrown as ApiError(401) without signing out and is shown under Current password. A rejected
   * refresh token still ends the session like any other request.
   */
  changePassword(request: ChangePasswordRequest): Promise<void> {
    return api.put<void>('/users/me/password', request, { authFailure: 'refresh-then-throw' })
  },
}
