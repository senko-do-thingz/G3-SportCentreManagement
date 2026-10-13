import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { onSessionExpired } from '../api/client'
import { ApiError } from '../api/errors'
import { tokenStorage } from '../api/tokenStorage'
import type { AuthTokens, LoginRequest, RegisterRequest, UserProfile } from '../api/types'
import {
  AccountCreatedError,
  AuthContext,
  type AuthContextValue,
  type AccountSwitch,
  type AuthStatus,
  type LoginOptions,
} from './authContext'
import { roleOf } from './roles'

interface AuthState {
  status: AuthStatus
  user: UserProfile | null
  sessionExpired: boolean
  signedOutElsewhere: boolean
  accountSwitched: AccountSwitch | null
  loadError: unknown
}

const ANONYMOUS: AuthState = {
  status: 'anonymous',
  user: null,
  sessionExpired: false,
  signedOutElsewhere: false,
  accountSwitched: null,
  loadError: null,
}

/**
 * True when a failed GET /users/me means the session is over. The API client already tried a
 * refresh before this point; a rejected refresh clears the stored tokens. Network errors (status
 * 0), server errors (5xx) and rate limiting (429) do not end the session.
 */
function endsSession(error: unknown): boolean {
  if (!tokenStorage.hasSession()) {
    return true
  }
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    ...ANONYMOUS,
    status: tokenStorage.hasSession() ? 'loading' : 'anonymous',
  }))
  // Only the latest profile request may change the state; older answers are ignored.
  const loadCounter = useRef(0)
  // The user this tab shows right now, read by the storage listener before it reloads the profile.
  const shownUser = useRef<UserProfile | null>(null)
  const switchCounter = useRef(0)

  // A layout effect runs in the same commit as the render that shows the user. A passive effect
  // could run later, and a storage event from another tab in between read null here, so a login
  // as another account was not seen as a switch and the tab stayed on the 403 page.
  useLayoutEffect(() => {
    shownUser.current = state.status === 'authenticated' ? state.user : null
  }, [state])

  /**
   * Loads GET /users/me for the current tokens. previousUserId is the user this tab showed before
   * another tab changed the session: a different id marks an account switch, the same id changes
   * nothing visible.
   */
  const loadProfile = useCallback(async (previousUserId: number | null = null): Promise<UserProfile | null> => {
    loadCounter.current += 1
    const requestId = loadCounter.current
    const epoch = tokenStorage.getEpoch()
    const isCurrent = () => loadCounter.current === requestId && tokenStorage.getEpoch() === epoch
    try {
      const user = await authApi.me()
      if (isCurrent()) {
        const switched = previousUserId !== null && user.id !== previousUserId
        if (switched) {
          switchCounter.current += 1
        }
        const seq = switchCounter.current
        setState((previous) => ({
          status: 'authenticated',
          user,
          sessionExpired: false,
          signedOutElsewhere: false,
          accountSwitched: switched ? { userId: user.id, fullName: user.fullName, seq } : previous.accountSwitched,
          loadError: null,
        }))
      }
      return user
    } catch (error) {
      if (!isCurrent()) {
        return null
      }
      if (endsSession(error)) {
        tokenStorage.clear()
        setState(ANONYMOUS)
      } else {
        // Keep the tokens. A signed in screen stays as it is; a restore shows the Retry screen.
        setState((previous) =>
          previous.status === 'authenticated' ? previous : { ...ANONYMOUS, status: 'error', loadError: error },
        )
      }
      return null
    }
  }, [])

  // Restore the session after a page reload. The API client refreshes the access token if needed.
  useEffect(() => {
    if (tokenStorage.hasSession()) {
      void loadProfile()
    }
  }, [loadProfile])

  // The API client calls this when the refresh token is rejected.
  useEffect(
    () =>
      onSessionExpired(() => {
        loadCounter.current += 1
        setState((previous) => ({
          ...ANONYMOUS,
          sessionExpired: previous.status === 'authenticated' || previous.sessionExpired,
        }))
      }),
    [],
  )

  // Follow a login or logout done in another browser tab. Token rotation by a refresh in another
  // tab changes only the token keys and is ignored here, so it does not trigger GET /users/me.
  useEffect(() => {
    const handleStorage = (event: StorageEvent) => {
      if (event.key !== null && event.key !== tokenStorage.sessionKey) {
        return
      }
      if (event.key !== null && event.oldValue === event.newValue) {
        return
      }
      const previousUser = shownUser.current
      tokenStorage.markExternalSessionChange()
      // One account per browser: a login or logout in another tab also ends the session that
      // this tab keeps for itself without "Remember me".
      tokenStorage.endTabSession()
      if (!tokenStorage.hasSession()) {
        loadCounter.current += 1
        // Tell a signed in user why the session ended; a tab that was already anonymous stays quiet.
        setState((previous) => ({ ...ANONYMOUS, signedOutElsewhere: previous.status === 'authenticated' }))
        return
      }
      // The new session may belong to another account: loadProfile compares the ids and, when they
      // differ, records the switch so the app opens the new user's home page with a notice.
      void loadProfile(previousUser?.id ?? null)
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [loadProfile])

  /** Stores a new token pair and loads its profile. A failed profile load drops the tokens again. */
  const startSession = useCallback(async (tokens: AuthTokens, persistent: boolean) => {
    tokenStorage.startSession(tokens, { persistent })
    try {
      const user = await authApi.me()
      setState({
        status: 'authenticated',
        user,
        sessionExpired: false,
        signedOutElsewhere: false,
        accountSwitched: null,
        loadError: null,
      })
      return user
    } catch (error) {
      tokenStorage.clear()
      throw error
    }
  }, [])

  const login = useCallback(
    async (credentials: LoginRequest, options: LoginOptions = {}) => {
      // Any profile request still running for an older session must not overwrite this login.
      loadCounter.current += 1
      const tokens = await authApi.login({
        email: credentials.email.trim(),
        password: credentials.password,
      })
      return startSession(tokens, options.remember !== false)
    },
    [startSession],
  )

  const register = useCallback(
    async (request: RegisterRequest) => {
      loadCounter.current += 1
      const tokens = await authApi.register(request)
      try {
        return await startSession(tokens, true)
      } catch (error) {
        throw new AccountCreatedError(error)
      }
    },
    [startSession],
  )

  const logout = useCallback(() => {
    // The backend has no logout endpoint; dropping the tokens ends the session on this device.
    loadCounter.current += 1
    tokenStorage.clear()
    setState(ANONYMOUS)
  }, [])

  const dismissAccountSwitch = useCallback(() => {
    setState((previous) => (previous.accountSwitched ? { ...previous, accountSwitched: null } : previous))
  }, [])

  const reloadUser = useCallback(async () => {
    if (!tokenStorage.hasSession()) {
      return null
    }
    setState((previous) => (previous.status === 'error' ? { ...previous, status: 'loading' } : previous))
    return loadProfile()
  }, [loadProfile])

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      user: state.user,
      role: roleOf(state.user),
      sessionExpired: state.sessionExpired,
      signedOutElsewhere: state.signedOutElsewhere,
      accountSwitched: state.accountSwitched,
      dismissAccountSwitch,
      loadError: state.loadError,
      login,
      register,
      logout,
      reloadUser,
    }),
    [state, login, register, logout, reloadUser, dismissAccountSwitch],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
