export function safeMemberReturnTo(value: string | null): string {
  if (!value || !value.startsWith('/member') || /[\\\r\n]/.test(value)) return '/member'
  const url = new URL(value, 'https://sportify.local')
  if (url.origin !== 'https://sportify.local' || (url.pathname !== '/member' && !url.pathname.startsWith('/member/'))
      || url.pathname.startsWith('/member/assistant')) return '/member'
  return `${url.pathname}${url.search}${url.hash}`
}
