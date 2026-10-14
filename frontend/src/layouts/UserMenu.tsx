import { useNavigate } from 'react-router-dom'
import { ROLE_LABELS } from '../auth/roles'
import { useAuth } from '../auth/useAuth'
import { initials } from '../utils/format'

/** Avatar, name, role and the Log out action shown at the top right of every signed in layout. */
export function UserMenu() {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="user-menu">
      <span className="avatar" aria-hidden="true">
        {initials(user?.fullName)}
      </span>
      <span className="user-menu__text">
        <span className="user-menu__name">{user?.fullName ?? 'Signed in'}</span>
        <span className="user-menu__role">{role ? ROLE_LABELS[role] : user?.role}</span>
      </span>
      <button type="button" className="user-menu__logout" onClick={handleLogout}>
        Log out
      </button>
    </div>
  )
}
