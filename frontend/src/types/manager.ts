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

/** SportPackageCreateRequest on main uses priceAmount, unlike the update DTO. */
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

/** Mirrors the published BE-03 SportPackageUpdateRequest. */
export interface PackageUpdateDraft {
  name: string
  durationDays: number
  sessionCount: number
  sessionMinutes: number
  price: number
  active: boolean
}

/** Mirrors the published BE-03 MembershipCardTierUpdateRequest. */
export interface CardTierUpdateDraft {
  price: number
  durationMonths: number
  discountPercentage: number
  description: string
  active: boolean
}

/** Mirrors the scalar projection returned by BE-04 ActivityLogResponse. */
export interface ActivityLog {
  id: number
  createdAt: string
  actorId: number | null
  actorName: string
  actorRole: string | null
  action: string
  entityType: string | null
  entityId: number | null
  shortDetail: string
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
