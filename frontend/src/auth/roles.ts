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

/** Accept only literal same-app paths, before applying the authenticated role boundary. */
export function safeReturnPath(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')
    || [...value].some((char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) return null
  try {
    const url = new URL(value, 'https://sportify.invalid')
    const pathname = value.split(/[?#]/, 1)[0]
    // Encoded paths and normalization tricks are unnecessary for our role routes.
    if (url.origin !== 'https://sportify.invalid' || url.pathname !== pathname || pathname.includes('%')) return null
    return url.pathname + url.search + url.hash
  } catch { return null }
}

/** Copy only return intent when switching auth forms, without profile or credential fields. */
export function loginReturnState(state: unknown): { from: string } | null {
  const path = safeReturnPath(state && typeof state === 'object' ? (state as { from?: unknown }).from : null)
  return path ? { from: path } : null
}

/** Resume only within the signed-in user's area; other destinations fall back to role home. */
export function postLoginPath(user: UserProfile, requestedPath: unknown): string {
  const home = homePathFor(user)
  const path = safeReturnPath(requestedPath)
  const pathname = path?.split(/[?#]/, 1)[0]
  if (path && pathname && home !== '/403' && (pathname === home || pathname.startsWith(`${home}/`))) {
    return path
  }
  return home
}
