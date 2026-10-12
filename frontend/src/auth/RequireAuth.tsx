import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { PageLoader } from '../components/ui/Spinner'
import { SessionErrorPage } from '../pages/public/SessionErrorPage'
import { useAuth } from './useAuth'

export interface LoginRedirectState {
  /** Path the user tried to open before being sent to the login page. */
  from?: string
}

/**
 * Renders the child routes only for a signed in user. Anonymous users go to /login. When the
 * session exists but the profile could not be loaded (server down), the Retry screen is shown
 * instead, without redirecting and without dropping the tokens.
 */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <PageLoader label="Restoring your session" />
  }
  if (status === 'error') {
    return <SessionErrorPage />
  }
  if (status === 'anonymous') {
    const state: LoginRedirectState = { from: `${location.pathname}${location.search}` }
    return <Navigate to="/login" replace state={state} />
  }
  return children ?? <Outlet />
}
