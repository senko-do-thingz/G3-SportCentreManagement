import { isRole, type Role, type UserProfile } from '../api/types'

export const ROLE_LABELS: Record<Role, string> = {
  MEMBER: 'Member',
  RECEPTIONIST: 'Receptionist',
  MANAGER: 'Manager',
  COACH: 'Coach',
}

/** Landing page for each role right after login. */
export const ROLE_HOME: Record<Role, string> = {
  MEMBER: '/member',
  RECEPTIONIST: '/reception',
  MANAGER: '/manager',
  COACH: '/coach',
}

export const STAFF_ROLES: readonly Role[] = ['RECEPTIONIST', 'MANAGER', 'COACH']

export function roleOf(user: UserProfile | null): Role | null {
  return user && isRole(user.role) ? user.role : null
}

export function homePathFor(user: UserProfile | null): string {
  const role = roleOf(user)
  return role ? ROLE_HOME[role] : '/403'
}

/**
 * Decides where to go after login: back to the page that required login when it belongs to the
 * user's own area, otherwise the role home. This avoids sending a member to a manager URL that
 * would only show the 403 page.
 */
export function postLoginPath(user: UserProfile, requestedPath: string | null | undefined): string {
  const home = homePathFor(user)
  if (requestedPath && home !== '/403' && (requestedPath === home || requestedPath.startsWith(`${home}/`))) {
    return requestedPath
  }
  return home
}
