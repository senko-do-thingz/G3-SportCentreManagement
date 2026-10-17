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

/** Validate untrusted authentication JSON before storing or attaching token values. */
export function isAuthTokens(value: unknown): value is AuthTokens {
  if (!value || typeof value !== 'object') return false
  const tokens = value as Record<string, unknown>
  return [tokens.accessToken, tokens.refreshToken].every(
    (token) => typeof token === 'string' && token.length > 0 && !/\s/.test(token),
  )
}

/**
 * Mirrors com.sportify.identity.dto.UserProfileResponse (GET /users/me).
 * The optional fields are the member profile fields planned in BE-02 (date_of_birth and address
 * of user_account, main_goal and current_level of member_profile). The backend on main does not
 * return them yet, so every screen must treat a missing value as empty.
 */
export interface UserProfile {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: string
  status: string
  /** ISO date "2004-06-15" (Java LocalDate). */
  dateOfBirth?: string | null
  address?: string | null
  /** IMPROVE_FITNESS, BUILD_SKILLS or COMPETITION. */
  mainGoal?: string | null
  /** BEGINNER, INTERMEDIATE or ADVANCED. */
  currentLevel?: string | null
  memberCode?: string | null
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
