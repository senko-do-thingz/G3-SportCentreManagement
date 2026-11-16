import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ROLE_LABELS } from '../auth/roles'
import { useAuth } from '../auth/useAuth'
import { initials } from '../utils/format'
import { useAvatarSrc } from '../components/avatar/useAvatarSrc'
import { AvatarUploadModal } from '../components/avatar/AvatarUploadModal'

/** Avatar, name, role and the Log out action shown at the top right of every signed in layout. */
export function UserMenu() {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()
  const [modalOpen, setModalOpen] = useState(false)
  const avatarImageSrc = useAvatarSrc(user?.avatarUrl)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="user-menu">
      <button
        type="button"
        className="user-menu__avatar-btn"
        onClick={() => setModalOpen(true)}
        title="Upload or change avatar"
        aria-label="Upload or change avatar"
        style={{
          background: 'none',
          border: 'none',
          padding: 0,
          margin: 0,
          cursor: 'pointer',
          display: 'inline-flex',
        }}
      >
        <span className="avatar" aria-hidden="true">
          {avatarImageSrc ? (
            <img src={avatarImageSrc} alt="" />
          ) : (
            initials(user?.fullName)
          )}
        </span>
      </button>
      <span className="user-menu__text">
        <span className="user-menu__name">{user?.fullName ?? 'Signed in'}</span>
        <span className="user-menu__role">{role ? ROLE_LABELS[role] : user?.role}</span>
      </span>
      <button type="button" className="user-menu__logout" onClick={handleLogout}>
        Log out
      </button>

      <AvatarUploadModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        currentAvatarUrl={user?.avatarUrl}
        fullName={user?.fullName}
      />
    </div>
  )
}
