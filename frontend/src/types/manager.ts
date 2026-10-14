import type { CardTier } from '../api/cardsApi'
import type { SportPackage } from '../api/packagesApi'
import type { Role } from '../api/types'

export type StaffRole = Exclude<Role, 'MEMBER'>
export type AccountStatus = 'ACTIVE' | 'INACTIVE'
export type TrainingFormat = 'SELF_TRAINING' | 'COACH_LED'

/** Verified against the V1 ManagerUserResponse DTO. */
export interface ManagerUser {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: string
  status: string
  createdAt: string | null
}

/** Verified against the V1 StaffAccountCreateRequest DTO. */
export interface StaffAccountRequest {
  fullName: string
  email: string
  phone: string
  temporaryPassword: string
  role: StaffRole
  sportIds: number[]
}

export interface UsersQuery {
  page: number
  size: number
  role?: string
  status?: string
  search?: string
}

/** Verified create payload; updates are preview-only until BE-03 is published. */
export interface PackageCreateRequest {
  code: string
  name: string
  sportId: number
  trainingFormat: TrainingFormat
  durationDays: number
  sessionCount: number
  sessionMinutes: number
  priceAmount: number
  description?: string
}

export type ManagerPackage = SportPackage
export type ManagerCardTier = CardTier

/** Proposed BE-03 payload used exclusively by explicit development previews. */
export interface PackageUpdateDraft {
  name: string
  durationDays: number
  sessionCount: number
  sessionMinutes: number
  priceAmount: number
  isActive: boolean
}

/** Proposed BE-03 payload used exclusively by explicit development previews. */
export interface CardTierUpdateDraft {
  price: number
  durationMonths: number | null
  discountPercentage: number
  description: string
  isActive: boolean
}

/** Preview schema only; BE-04 field names and date semantics remain unverified. */
export interface ActivityLog {
  id: number
  createdAt: string
  actorId: number | null
  actorName: string | null
  actorRole: string | null
  action: string
  entityType: string | null
  entityId: number | null
  entityCode: string | null
  summary: string | null
  details: string | null
}

export interface ActivityQuery {
  page: number
  size: number
  actorId?: string
  action?: string
  from?: string
  to?: string
  search?: string
}
