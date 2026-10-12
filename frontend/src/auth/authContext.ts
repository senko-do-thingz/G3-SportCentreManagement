import { createContext } from 'react'
import type { LoginRequest, RegisterRequest, Role, UserProfile } from '../api/types'

/**
 * loading: a stored session is being restored.
 * authenticated: the profile is loaded.
 * anonymous: no session, or the session ended (rejected refresh token, logout).
 * error: a session exists but the profile could not be loaded because of a network error, a
 * server error (5xx) or rate limiting (429). The tokens are kept and the user can retry.
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error'

export interface LoginOptions {
  /**
   * "Remember me". True (default) keeps the session after the browser is closed and shares it
   * with every tab; false keeps it for this tab only.
   */
  remember?: boolean
}

/** Set when this tab followed another tab of the browser to a different account (one account per browser). */
export interface AccountSwitch {
  userId: number
  fullName: string
  /** Increases on every switch, so the redirect runs once per switch. */
  seq: number
}

export interface AuthContextValue {
  status: AuthStatus
  user: UserProfile | null
  /** Null when the user is anonymous or the backend returned an unknown role code. */
  role: Role | null
  /** True right after the refresh token was rejected; cleared on the next successful login. */
  sessionExpired: boolean
  /**
   * True when this tab was signed in and lost its session because another tab of the same
   * browser logged in or out (one account per browser). Never set by a logout in this tab.
   * Cleared on the next successful login or sign up.
   */
  signedOutElsewhere: boolean
  /**
   * Not null right after another tab signed a different account in and this tab followed it.
   * The app then opens the new user's home page and shows a notice. Cleared by
   * dismissAccountSwitch, the next login, sign up, logout, or the next switch.
   */
  accountSwitched: AccountSwitch | null
  dismissAccountSwitch(): void
  /** The error behind status 'error', otherwise null. */
  loadError: unknown
  login(credentials: LoginRequest, options?: LoginOptions): Promise<UserProfile>
  /**
   * Creates a member account with POST /auth/register, then signs the new member in with the
   * returned tokens (remembered on this device) and loads the profile.
   */
  register(request: RegisterRequest): Promise<UserProfile>
  logout(): void
  /** Reloads GET /users/me, for example after the profile was edited or to retry after an error. */
  reloadUser(): Promise<UserProfile | null>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Thrown by register() when POST /auth/register created the account but the follow-up profile
 * request failed (for example the server went down in between). The account exists, so the sign
 * up form must not be sent again: the page asks the user to log in instead.
 */
export class AccountCreatedError extends Error {
  constructor(cause: unknown) {
    super('Your account was created, but we could not sign you in. Please log in.', { cause })
    this.name = 'AccountCreatedError'
  }
}
