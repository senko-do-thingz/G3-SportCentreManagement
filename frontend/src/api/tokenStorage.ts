import type { AuthTokens } from './types'

const ACCESS_KEY = 'sportify.accessToken'
const REFRESH_KEY = 'sportify.refreshToken'
/** Random id written on login and removed on logout. A refresh keeps it, so other tabs can tell them apart. */
const SESSION_KEY = 'sportify.sessionId'

type Area = 'local' | 'session'

/**
 * Persists the JWT pair so a page reload keeps the user signed in.
 *
 * Rule: one account per browser. Two storage areas are used:
 * - localStorage for a remembered session ("Remember me" ticked, every sign up, and any call to
 *   startSession without options). All tabs share these keys and the session survives a browser
 *   restart.
 * - sessionStorage for a session that is not remembered. It is written only in the tab that
 *   logged in and ends when that tab is closed (some browsers restore it when they reopen tabs
 *   from the last session).
 *
 * What other tabs see:
 * - A new login in any tab, remembered or not, and a logout in any tab clear both areas of that
 *   tab, which includes the shared localStorage. A remembered session that another tab was using
 *   therefore ends: that tab receives a storage event, becomes anonymous and its login page says
 *   that another account signed in or out in this browser. This keeps an older remembered user
 *   from coming back on a shared front desk computer.
 * - A tab whose own session is not remembered drops it too when it sees another tab log in or out
 *   (endTabSession, called from the AuthProvider storage listener).
 * - Limitation: a login without "Remember me" writes nothing to localStorage when localStorage is
 *   already empty, so a tab that holds its own not remembered session gets no event and keeps it.
 *
 * Inside one tab, its own sessionStorage is read before localStorage.
 *
 * Trade-off: both areas are readable by any script on the page, so the app must never render
 * untrusted HTML. Moving to httpOnly cookies would need backend changes and is out of scope.
 * Every access is wrapped in try/catch because storage can be blocked (private mode, policy).
 */
function storageFor(area: Area): Storage | null {
  try {
    return area === 'local' ? window.localStorage : window.sessionStorage
  } catch {
    return null
  }
}

function readFrom(area: Area, key: string): string | null {
  try {
    return storageFor(area)?.getItem(key) ?? null
  } catch {
    return null
  }
}

function writeTo(area: Area, key: string, value: string | null): void {
  try {
    const storage = storageFor(area)
    if (!storage) {
      return
    }
    if (value === null) {
      storage.removeItem(key)
    } else {
      storage.setItem(key, value)
    }
  } catch {
    // Storage is unavailable: the session then lives only until the page is reloaded.
  }
}

/** The area that holds the current session: the tab's own sessionStorage first, then localStorage. */
function activeArea(): Area {
  return readFrom('session', ACCESS_KEY) !== null || readFrom('session', REFRESH_KEY) !== null
    ? 'session'
    : 'local'
}

function read(key: string): string | null {
  return readFrom(activeArea(), key)
}

function clearArea(area: Area): void {
  writeTo(area, ACCESS_KEY, null)
  writeTo(area, REFRESH_KEY, null)
  writeTo(area, SESSION_KEY, null)
}

function newSessionId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

export interface StartSessionOptions {
  /**
   * True (default) keeps the session in localStorage: shared by all tabs and kept after the
   * browser is closed. False keeps it in sessionStorage for this tab only ("Remember me" off).
   */
  persistent?: boolean
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

  /**
   * Stores the tokens of a new login or sign up. Any older session in either area is removed
   * first, so a remembered session of a previous user can never come back after a restart.
   */
  startSession(tokens: AuthTokens, options: StartSessionOptions = {}): void {
    const area: Area = options.persistent === false ? 'session' : 'local'
    sessionEpoch += 1
    clearArea('session')
    clearArea('local')
    writeTo(area, ACCESS_KEY, tokens.accessToken)
    writeTo(area, REFRESH_KEY, tokens.refreshToken)
    writeTo(area, SESSION_KEY, newSessionId())
  },

  /** Stores the rotated pair returned by POST /auth/refresh in the area of the current session. */
  rotate(tokens: AuthTokens): void {
    const area = activeArea()
    writeTo(area, ACCESS_KEY, tokens.accessToken)
    writeTo(area, REFRESH_KEY, tokens.refreshToken)
  },

  /**
   * Removes the session this tab keeps for itself (Remember me off). Called when another tab
   * logged in or out, so the browser never holds two accounts at once. The shared localStorage
   * is not touched.
   */
  endTabSession(): void {
    clearArea('session')
  },

  clear(): void {
    sessionEpoch += 1
    clearArea('session')
    clearArea('local')
  },
}
