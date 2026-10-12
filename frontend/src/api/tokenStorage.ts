import type { AuthTokens } from './types'

const ACCESS_KEY = 'sportify.accessToken'
const REFRESH_KEY = 'sportify.refreshToken'
/** Random id written on login and removed on logout. A refresh keeps it, so other tabs can tell them apart. */
const SESSION_KEY = 'sportify.sessionId'

/**
 * Persists the JWT pair in localStorage so a page reload keeps the user signed in. All browser
 * tabs share these keys.
 *
 * Trade-off: localStorage is readable by any script on the page, so the app must never render
 * untrusted HTML. Moving to httpOnly cookies would need backend changes and is out of scope.
 * Every access is wrapped in try/catch because storage can be blocked (private mode, policy).
 */
function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key)
    } else {
      window.localStorage.setItem(key, value)
    }
  } catch {
    // Storage is unavailable: the session then lives only until the page is reloaded.
  }
}

function newSessionId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

/**
 * Increases every time a session starts or ends in this tab, or another tab reports that it
 * started or ended one. A refresh keeps the same value. Async work captures it first and checks
 * it again before changing the session, so a slow response from an old session can never
 * overwrite or clear a newer one.
 */
let sessionEpoch = 0

export const tokenStorage = {
  accessKey: ACCESS_KEY,
  refreshKey: REFRESH_KEY,
  sessionKey: SESSION_KEY,

  getAccessToken(): string | null {
    return read(ACCESS_KEY)
  },

  getRefreshToken(): string | null {
    return read(REFRESH_KEY)
  },

  getSessionId(): string | null {
    return read(SESSION_KEY)
  },

  hasSession(): boolean {
    return read(ACCESS_KEY) !== null || read(REFRESH_KEY) !== null
  },

  getEpoch(): number {
    return sessionEpoch
  },

  /** Called when the storage event shows that another tab started or ended a session. */
  markExternalSessionChange(): void {
    sessionEpoch += 1
  },

  /** Stores the tokens of a new login. */
  startSession(tokens: AuthTokens): void {
    sessionEpoch += 1
    write(ACCESS_KEY, tokens.accessToken)
    write(REFRESH_KEY, tokens.refreshToken)
    write(SESSION_KEY, newSessionId())
  },

  /** Stores the rotated pair returned by POST /auth/refresh for the current session. */
  rotate(tokens: AuthTokens): void {
    write(ACCESS_KEY, tokens.accessToken)
    write(REFRESH_KEY, tokens.refreshToken)
  },

  clear(): void {
    sessionEpoch += 1
    write(ACCESS_KEY, null)
    write(REFRESH_KEY, null)
    write(SESSION_KEY, null)
  },
}
