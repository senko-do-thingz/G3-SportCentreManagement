import { createContext } from 'react'
import type { LoginRequest, Role, UserProfile } from '../api/types'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  user: UserProfile | null
  /** Null when the user is anonymous or the backend returned an unknown role code. */
  role: Role | null
  /** True right after the refresh token was rejected; cleared on the next successful login. */
  sessionExpired: boolean
  login(credentials: LoginRequest): Promise<UserProfile>
  logout(): void
  /** Reloads GET /users/me, for example after the profile was edited. */
  reloadUser(): Promise<UserProfile | null>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
