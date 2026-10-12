import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { onSessionExpired } from '../api/client'
import { tokenStorage } from '../api/tokenStorage'
import type { LoginRequest, UserProfile } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'
import { roleOf } from './roles'

interface AuthState {
  status: AuthStatus
  user: UserProfile | null
  sessionExpired: boolean
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    status: tokenStorage.hasSession() ? 'loading' : 'anonymous',
    user: null,
    sessionExpired: false,
  }))

  // Restore the session after a page reload. The API client refreshes the access token if needed.
  useEffect(() => {
    if (!tokenStorage.hasSession()) {
      return
    }
    let cancelled = false
    // A login finished while this call was running must win over this older result.
    const epoch = tokenStorage.getEpoch()
    const isCurrent = () => !cancelled && tokenStorage.getEpoch() === epoch
    authApi
      .me()
      .then((user) => {
        if (isCurrent()) {
          setState({ status: 'authenticated', user, sessionExpired: false })
        }
      })
      .catch(() => {
        if (isCurrent()) {
          setState({ status: 'anonymous', user: null, sessionExpired: false })
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  // The API client calls this when the refresh token is rejected.
  useEffect(
    () =>
      onSessionExpired(() => {
        setState((previous) => ({
          status: 'anonymous',
          user: null,
          sessionExpired: previous.status === 'authenticated' || previous.sessionExpired,
        }))
      }),
    [],
  )

  // Follow logout and login done in another browser tab, which share the same localStorage.
  useEffect(() => {
    const handleStorage = (event: StorageEvent) => {
      if (event.key !== tokenStorage.refreshKey && event.key !== null) {
        return
      }
      if (!tokenStorage.hasSession()) {
        setState({ status: 'anonymous', user: null, sessionExpired: false })
        return
      }
      // A login (possibly another user) or a token refresh in another tab: reload the profile.
      authApi
        .me()
        .then((user) => setState({ status: 'authenticated', user, sessionExpired: false }))
        // A rejected session already logs out through onSessionExpired; other errors keep the state.
        .catch(() => undefined)
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    const tokens = await authApi.login({
      email: credentials.email.trim(),
      password: credentials.password,
    })
    tokenStorage.startSession(tokens)
    try {
      const user = await authApi.me()
      setState({ status: 'authenticated', user, sessionExpired: false })
      return user
    } catch (error) {
      tokenStorage.clear()
      throw error
    }
  }, [])

  const logout = useCallback(() => {
    // The backend has no logout endpoint; dropping the tokens ends the session on this device.
    tokenStorage.clear()
    setState({ status: 'anonymous', user: null, sessionExpired: false })
  }, [])

  const reloadUser = useCallback(async () => {
    if (!tokenStorage.hasSession()) {
      return null
    }
    const user = await authApi.me()
    setState({ status: 'authenticated', user, sessionExpired: false })
    return user
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      user: state.user,
      role: roleOf(state.user),
      sessionExpired: state.sessionExpired,
      login,
      logout,
      reloadUser,
    }),
    [state, login, logout, reloadUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
