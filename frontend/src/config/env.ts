const DEFAULT_API_BASE_URL = 'http://localhost:8080/api/v1'

function normalizeBaseUrl(value: string | undefined): string {
  const raw = value && value.trim().length > 0 ? value.trim() : DEFAULT_API_BASE_URL
  // Strip trailing slashes so paths like '/auth/login' can be appended safely.
  return raw.replace(/\/+$/, '')
}

export const env = {
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL),
} as const

export interface SampleAccount {
  email: string
  password: string
}

/**
 * Sample account shown in the box on the Log in screen (Figma F1-05). Off by default: the box is
 * shown only when VITE_SHOW_SAMPLE_ACCOUNT is exactly "true" and both VITE_SAMPLE_ACCOUNT_EMAIL
 * and VITE_SAMPLE_ACCOUNT_PASSWORD are set. Put only a throwaway demo member here, never staff
 * credentials, and never commit real values (frontend/.env is git-ignored).
 *
 * Read on every call instead of once at start up, so tests can change the variables.
 */
export function sampleAccount(): SampleAccount | null {
  if (import.meta.env.VITE_SHOW_SAMPLE_ACCOUNT !== 'true') {
    return null
  }
  const email = import.meta.env.VITE_SAMPLE_ACCOUNT_EMAIL?.trim() ?? ''
  const password = import.meta.env.VITE_SAMPLE_ACCOUNT_PASSWORD?.trim() ?? ''
  return email && password ? { email, password } : null
}
