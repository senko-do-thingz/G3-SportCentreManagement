const ALLOWED_AREAS = ['/member', '/manager', '/coach', '/reception']

export function safeAssistantReturnTo(value: string | null, defaultFallback = '/member'): string {
  if (!value || /[\\\r\n]/.test(value)) return defaultFallback
  const matchedArea = ALLOWED_AREAS.find((area) => value === area || value.startsWith(`${area}/`))
  if (!matchedArea) return defaultFallback
  try {
    const url = new URL(value, 'https://sportify.local')
    if (
      url.origin !== 'https://sportify.local' ||
      (url.pathname !== matchedArea && !url.pathname.startsWith(`${matchedArea}/`)) ||
      url.pathname.endsWith('/assistant') ||
      url.pathname.includes('/assistant/')
    ) {
      return matchedArea
    }
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return matchedArea
  }
}

export function safeMemberReturnTo(value: string | null): string {
  if (!value || !value.startsWith('/member') || /[\\\r\n]/.test(value)) return '/member'
  const url = new URL(value, 'https://sportify.local')
  if (
    url.origin !== 'https://sportify.local' ||
    (url.pathname !== '/member' && !url.pathname.startsWith('/member/')) ||
    url.pathname.startsWith('/member/assistant')
  ) {
    return '/member'
  }
  return `${url.pathname}${url.search}${url.hash}`
}
