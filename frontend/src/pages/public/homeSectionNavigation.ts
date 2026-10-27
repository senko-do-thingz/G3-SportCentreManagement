import { revealHomeFaq } from './homeFaqNavigation'

export function homeScrollBehavior(): ScrollBehavior {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
}

/** Real fragment targets keep the measured sticky-header scroll margin and native FAQ semantics. */
export function scrollHomeSection(hash: string): boolean {
  let id: string
  try { id = decodeURIComponent(hash.slice(1)) } catch { return false }
  if (!hash.startsWith('#') || !id) return false
  const target = document.getElementById(id)
  if (!target?.closest('.home-page')) return false
  if (!revealHomeFaq(hash)) {
    target.tabIndex = -1
    target.focus({ preventScroll: true })
  }
  target.scrollIntoView?.({ block: 'start', behavior: homeScrollBehavior() })
  return true
}
