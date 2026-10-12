import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { homePathFor, ROLE_LABELS } from '../../auth/roles'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { StatusPage } from './StatusPage'

export function ForbiddenPage() {
  const { user, role, logout } = useAuth()
  const home = homePathFor(user)

  return (
    <StatusPage
      code="403"
      title="Access denied"
      description={
        role
          ? `This page is not available for the ${ROLE_LABELS[role]} role.`
          : 'Your account does not have access to this page.'
      }
      actions={
        <>
          {home !== '/403' && (
            <Link to={home} className={buttonClassName('primary')}>
              Go to my home page
            </Link>
          )}
          <button type="button" className={buttonClassName('secondary')} onClick={logout}>
            Log in with another account
          </button>
        </>
      }
    />
  )
}
