const ALLOWED_AREAS = ['/member', '/manager', '/coach', '/reception']

export function safeAssistantReturnTo(value: string | null, defaultFallback = '/member'): string {
  if (!value || /[\\\r\n]/.test(value)) return defaultFallback
  const matchedArea = ALLOWED_AREAS.find((area) => value === area || value.startsWith(`${area}/`))
  if (!matchedArea) return defaultFallback
  try {
    const url = new URL(value, 'https://sportify.local')
    if (url.origin !== 'https://sportify.local' || url.pathname.endsWith('/assistant')) {
      return matchedArea
    }
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return matchedArea
  }
}

export function safeMemberReturnTo(value: string | null): string {
  return safeAssistantReturnTo(value, '/member')
}
