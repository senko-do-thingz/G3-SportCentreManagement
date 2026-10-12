import type { ReactNode } from 'react'
import { Outlet } from 'react-router-dom'
import type { Role } from '../api/types'
import { ForbiddenPage } from '../pages/public/ForbiddenPage'
import { RequireAuth } from './RequireAuth'
import { useAuth } from './useAuth'

export interface RequireRoleProps {
  roles: readonly Role[]
  children?: ReactNode
}

/**
 * Renders the child routes only when the signed in user has one of the given roles.
 * A wrong role shows the 403 page in place, so the address bar keeps the requested URL.
 * Includes RequireAuth, so anonymous users are still redirected to /login.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  return (
    <RequireAuth>
      <RoleGate roles={roles}>{children}</RoleGate>
    </RequireAuth>
  )
}

function RoleGate({ roles, children }: RequireRoleProps) {
  const { role } = useAuth()
  if (!role || !roles.includes(role)) {
    return <ForbiddenPage />
  }
  return children ?? <Outlet />
}
