import type { AuthTokens } from './types'

const ACCESS_KEY = 'sportify.accessToken'
const REFRESH_KEY = 'sportify.refreshToken'

/**
 * Persists the JWT pair in localStorage so a page reload keeps the user signed in.
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

/**
 * Increases every time a session starts (login) or ends (logout, expiry). A refresh keeps the
 * same session. Async work captures the value first and checks it again before changing the
 * session, so a slow response from an old session can never overwrite or clear a newer one.
 */
let sessionEpoch = 0

export const tokenStorage = {
  accessKey: ACCESS_KEY,
  refreshKey: REFRESH_KEY,

  getAccessToken(): string | null {
    return read(ACCESS_KEY)
  },

  getRefreshToken(): string | null {
    return read(REFRESH_KEY)
  },

  hasSession(): boolean {
    return read(ACCESS_KEY) !== null || read(REFRESH_KEY) !== null
  },

  getEpoch(): number {
    return sessionEpoch
  },

  /** Stores the tokens of a new login. */
  startSession(tokens: AuthTokens): void {
    sessionEpoch += 1
    write(ACCESS_KEY, tokens.accessToken)
    write(REFRESH_KEY, tokens.refreshToken)
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
  },
}
