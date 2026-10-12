import { createContext } from 'react'
import type { LoginRequest, Role, UserProfile } from '../api/types'

/**
 * loading: a stored session is being restored.
 * authenticated: the profile is loaded.
 * anonymous: no session, or the session ended (rejected refresh token, logout).
 * error: a session exists but the profile could not be loaded because of a network error, a
 * server error (5xx) or rate limiting (429). The tokens are kept and the user can retry.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error'

export interface AuthContextValue {
  status: AuthStatus
  user: UserProfile | null
  /** Null when the user is anonymous or the backend returned an unknown role code. */
  role: Role | null
  /** True right after the refresh token was rejected; cleared on the next successful login. */
  sessionExpired: boolean
  /** The error behind status 'error', otherwise null. */
  loadError: unknown
  login(credentials: LoginRequest): Promise<UserProfile>
  logout(): void
  /** Reloads GET /users/me, for example after the profile was edited or to retry after an error. */
  reloadUser(): Promise<UserProfile | null>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
