import { api } from './client'
import { UNEXPECTED_RESPONSE_MESSAGE, type PackageRegistration, type RequestSignal } from './memberApi'
import { isOptionalText, isPositiveId, isRecord, isText } from './responseValidation'

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
  /** Persisted read value: V10 requires a positive, non-null session_count. */
  sessionCount: number
  /** Persisted read value: V13 requires non-null minutes; omitted create input is defaulted. */
  sessionMinutes: number
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

function asList<T extends { id: number }>(value: unknown, parse: (row: unknown) => T): T[] {
  if (!Array.isArray(value)) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  const rows = value.map(parse)
  if (new Set(rows.map((row) => row.id)).size !== rows.length) throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  return rows
}

function asSport(value: unknown): Sport {
  if (!isRecord(value) || !isPositiveId(value.id) || !isText(value.code) || !isText(value.name)
    || !['venueType', 'description', 'imageUrl'].every((key) => isOptionalText(value[key]))
    || !(value.displayOrder == null || Number.isSafeInteger(value.displayOrder))) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as Sport
}

function asPackage(value: unknown): SportPackage {
  const amount = isRecord(value) ? value.priceAmount : undefined
  const validAmount = (typeof amount === 'number' || typeof amount === 'string' && /^\d+(\.\d{1,2})?$/.test(amount))
    && Number.isFinite(Number(amount)) && Number(amount) >= 0 && Number(amount) <= 999999999999.99
  if (!isRecord(value) || !isPositiveId(value.id) || !isPositiveId(value.sportId)
    || !['code', 'name', 'sportName'].every((key) => isText(value[key]))
    || typeof value.trainingFormat !== 'string' || !['SELF_TRAINING', 'COACH_LED'].includes(value.trainingFormat)
    || !isPositiveId(value.durationDays) || !isPositiveId(value.sessionCount) || !isPositiveId(value.sessionMinutes)
    || !validAmount || !['sportCode', 'description'].every((key) => isOptionalText(value[key]))
    || !(value.isActive == null || typeof value.isActive === 'boolean')) {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as unknown as SportPackage
}

function asObjectWithId<T extends { id: number }>(value: unknown): T {
  if (!value || typeof value !== 'object' || typeof (value as T).id !== 'number') {
    throw new Error(UNEXPECTED_RESPONSE_MESSAGE)
  }
  return value as T
}

export const packagesApi = {
  sports(options: RequestSignal = {}): Promise<Sport[]> {
    return api.get<unknown>('/sports', options).then((value) => asList(value, asSport))
  },

  packages(options: RequestSignal = {}): Promise<SportPackage[]> {
    return api.get<unknown>('/packages', options).then((value) => asList(value, asPackage))
  },

  register(request: PackageRegistrationRequest): Promise<PackageRegistration> {
    return api.post<unknown>('/packages/registrations', request).then(asObjectWithId<PackageRegistration>)
  },
}
