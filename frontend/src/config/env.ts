const DEFAULT_API_BASE_URL = 'http://localhost:8080/api/v1'

export function normalizeBaseUrl(value: string | undefined): string {
  const raw = value && value.trim().length > 0 ? value.trim() : DEFAULT_API_BASE_URL
  const base = raw.replace(/\/+$/, '')
  if (base === '/api/v1') {
    return base
  }
  const message =
    'Invalid VITE_API_BASE_URL. Use an HTTP(S) origin, an origin ending in /api/v1, or /api/v1. Restart Vite after changing the setting.'
  let url: URL
  try {
    url = new URL(base)
  } catch {
    throw new Error(message)
  }
  if (
    !['http:', 'https:'].includes(url.protocol) || url.username || url.password ||
    url.search || url.hash || !['/', '/api/v1'].includes(url.pathname)
  ) {
    throw new Error(message)
  }
  // A bare origin is supported shorthand; endpoint paths never include the API prefix.
  return `${url.origin}/api/v1`
}

export const env = {
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL),
} as const

/** Preview fixtures are opt-in and excluded from production builds. */
export function managerPreviewEnabled(): boolean {
  return import.meta.env.DEV && import.meta.env.VITE_MANAGER_PREVIEW === 'true'
}

export interface SampleAccount {
  email: string
  password: string
}

/**
 * Sample account shown in the box on the Log in screen (Figma F1-05). Off by default: the box is
 * shown only in the development server (import.meta.env.DEV, npm run dev), and only when
 * VITE_SHOW_SAMPLE_ACCOUNT is exactly "true" and both VITE_SAMPLE_ACCOUNT_EMAIL and
 * VITE_SAMPLE_ACCOUNT_PASSWORD are set. Vite replaces import.meta.env.DEV with false in a production
 * build (npm run build), so the code after the first check is removed and the values never reach
 * the bundle. Put only a throwaway demo member here, never staff
 * credentials, and never commit real values (frontend/.env is git-ignored).
 *
 * Read on every call instead of once at start up, so tests can change the variables.
 */
export function sampleAccount(): SampleAccount | null {
  if (!import.meta.env.DEV) {
    return null
  }
  if (import.meta.env.VITE_SHOW_SAMPLE_ACCOUNT !== 'true') {
    return null
  }
  const email = import.meta.env.VITE_SAMPLE_ACCOUNT_EMAIL?.trim() ?? ''
  const password = import.meta.env.VITE_SAMPLE_ACCOUNT_PASSWORD?.trim() ?? ''
  return email && password ? { email, password } : null
}
