import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { onSessionExpired } from '../api/client'
import { ApiError } from '../api/errors'
import { tokenStorage } from '../api/tokenStorage'
import type { LoginRequest, UserProfile } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'
import { roleOf } from './roles'

interface AuthState {
  status: AuthStatus
  user: UserProfile | null
  sessionExpired: boolean
  loadError: unknown
}

const ANONYMOUS: AuthState = { status: 'anonymous', user: null, sessionExpired: false, loadError: null }

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

  const loadProfile = useCallback(async (): Promise<UserProfile | null> => {
    loadCounter.current += 1
    const requestId = loadCounter.current
    const epoch = tokenStorage.getEpoch()
    const isCurrent = () => loadCounter.current === requestId && tokenStorage.getEpoch() === epoch
    try {
      const user = await authApi.me()
      if (isCurrent()) {
        setState({ status: 'authenticated', user, sessionExpired: false, loadError: null })
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
      tokenStorage.markExternalSessionChange()
      if (!tokenStorage.hasSession()) {
        loadCounter.current += 1
        setState(ANONYMOUS)
        return
      }
      void loadProfile()
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [loadProfile])

  const login = useCallback(async (credentials: LoginRequest) => {
    // Any profile request still running for an older session must not overwrite this login.
    loadCounter.current += 1
    const tokens = await authApi.login({
      email: credentials.email.trim(),
      password: credentials.password,
    })
    tokenStorage.startSession(tokens)
    try {
      const user = await authApi.me()
      setState({ status: 'authenticated', user, sessionExpired: false, loadError: null })
      return user
    } catch (error) {
      tokenStorage.clear()
      throw error
    }
  }, [])

  const logout = useCallback(() => {
    // The backend has no logout endpoint; dropping the tokens ends the session on this device.
    loadCounter.current += 1
    tokenStorage.clear()
    setState(ANONYMOUS)
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
      loadError: state.loadError,
      login,
      logout,
      reloadUser,
    }),
    [state, login, logout, reloadUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
