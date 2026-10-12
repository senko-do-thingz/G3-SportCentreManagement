/** Role codes returned by the backend in UserProfileResponse.role. */
export type Role = 'MEMBER' | 'RECEPTIONIST' | 'MANAGER' | 'COACH'

export const ROLES: readonly Role[] = ['MEMBER', 'RECEPTIONIST', 'MANAGER', 'COACH']

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value)
}

/** Mirrors com.sportify.identity.dto.AuthResponse. */
export interface AuthTokens {
  accessToken: string
  refreshToken: string
}

/** Mirrors com.sportify.identity.dto.UserProfileResponse (GET /users/me). */
export interface UserProfile {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: string
  status: string
}

/** Mirrors com.sportify.identity.dto.AuthRequest. */
export interface LoginRequest {
  email: string
  password: string
}

/** Mirrors com.sportify.identity.dto.RegisterRequest. */
export interface RegisterRequest {
  fullName: string
  email: string
  password: string
  phone?: string
}

/**
 * Mirrors com.sportify.catalog.dto.PageResponse, the stable paged shape used by most list
 * endpoints (manager users, manager classes, manager plans). Page numbers are zero based.
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Spring Data Page serialized directly, still returned by GET /members. Use normalizePage. */
export interface SpringPage<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
}
