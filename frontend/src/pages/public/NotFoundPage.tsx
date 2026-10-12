import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { homePathFor } from '../../auth/roles'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { StatusPage } from './StatusPage'

export function NotFoundPage() {
  const { status, user } = useAuth()
  const target = status === 'authenticated' ? homePathFor(user) : '/login'

  return (
    <StatusPage
      code="404"
      title="Page not found"
      description="The page you are looking for does not exist or has been moved."
      actions={
        <Link to={target === '/403' ? '/login' : target} className={buttonClassName('primary')}>
          {status === 'authenticated' ? 'Back to my home page' : 'Go to log in'}
        </Link>
      }
    />
  )
}
