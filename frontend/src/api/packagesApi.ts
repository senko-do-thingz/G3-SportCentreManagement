import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type PackageRegistration, type RequestSignal } from './memberApi'

/**
 * Sport packages and registration (FE-03, Figma F1-09, F1-10).
 * Types mirror the backend DTOs on main; BigDecimal values arrive as numbers.
 */

/** Mirrors com.sportify.catalog.dto.SportResponse (GET /sports, public). */
export interface Sport {
  id: number
  code: string
  name: string
  venueType: string | null
  description: string | null
  imageUrl: string | null
  displayOrder: number | null
}

/** Mirrors com.sportify.catalog.dto.SportPackageResponse (GET /packages, public, active packages only). */
export interface SportPackage {
  id: number
  code: string
  name: string
  sportId: number
  sportName: string
  sportCode: string | null
  /** SELF_TRAINING or COACH_LED. */
  trainingFormat: string
  durationDays: number
  sessionCount: number | null
  sessionMinutes: number | null
  priceAmount: number | string
  description: string | null
  isActive: boolean | null
}

/**
 * Body of POST /packages/registrations. For a MEMBER the backend forces channel ONLINE and status
 * PENDING_PAYMENT and creates a PENDING payment (BE-17); paymentMethod defaults to BANK_TRANSFER.
 */
export interface PackageRegistrationRequest {
  packageId: number
  /** ISO date, today or later. */
  startDate: string
  channel: 'ONLINE'
}

function asList<T>(value: unknown): T[] {
  if (!Array.isArray(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T[]
}

function asObjectWithId<T extends { id: number }>(value: unknown): T {
  if (!value || typeof value !== 'object' || typeof (value as T).id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T
}

export const packagesApi = {
  sports(options: RequestSignal = {}): Promise<Sport[]> {
    return api.get<unknown>('/sports', options).then(asList<Sport>)
  },

  packages(options: RequestSignal = {}): Promise<SportPackage[]> {
    return api.get<unknown>('/packages', options).then(asList<SportPackage>)
  },

  register(request: PackageRegistrationRequest): Promise<PackageRegistration> {
    return api.post<unknown>('/packages/registrations', request).then(asObjectWithId<PackageRegistration>)
  },
}
