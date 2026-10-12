import { Navigate } from 'react-router-dom'
import { homePathFor } from '../../auth/roles'
import { useAuth } from '../../auth/useAuth'
import { PageLoader } from '../../components/ui/Spinner'
import { SessionErrorPage } from './SessionErrorPage'

/** "/" sends a signed in user to the home of their role and everyone else to the login page. */
export function HomeRedirect() {
  const { status, user } = useAuth()
  if (status === 'loading') {
    return <PageLoader label="Restoring your session" />
  }
  if (status === 'error') {
    return <SessionErrorPage />
  }
  if (status === 'authenticated') {
    return <Navigate to={homePathFor(user)} replace />
  }
  return <Navigate to="/login" replace />
}
