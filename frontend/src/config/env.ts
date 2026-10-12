const DEFAULT_API_BASE_URL = 'http://localhost:8080/api/v1'

function normalizeBaseUrl(value: string | undefined): string {
  const raw = value && value.trim().length > 0 ? value.trim() : DEFAULT_API_BASE_URL
  // Strip trailing slashes so paths like '/auth/login' can be appended safely.
  return raw.replace(/\/+$/, '')
}

export const env = {
  apiBaseUrl: normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL),
} as const
