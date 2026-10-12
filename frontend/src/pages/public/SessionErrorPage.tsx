import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { Brand } from '../../components/Brand'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'

/**
 * Shown when a stored session exists but the profile could not be loaded because the server is
 * unreachable or failing. The tokens are kept, so Retry restores the session without logging in.
 */
export function SessionErrorPage() {
  const { loadError, reloadUser, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="status-page">
      <div className="status-page__card status-page__card--wide">
        <Brand />
        <ErrorState
          title="We could not load your account"
          error={loadError}
          onRetry={() => {
            void reloadUser()
          }}
          retryLabel="Retry"
        />
        <Button variant="ghost" onClick={handleLogout}>
          Log out
        </Button>
      </div>
    </main>
  )
}
