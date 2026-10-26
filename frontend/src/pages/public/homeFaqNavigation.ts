/** Reveal just the addressed Home FAQ. Summary clicks retain their native toggle behavior. */
export function revealHomeFaq(hash: string, scroll = false): HTMLDetailsElement | null {
  if (!/^#home-faq-\d+$/.test(hash)) return null
  const item = document.getElementById(hash.slice(1))
  if (!(item instanceof HTMLDetailsElement)) return null
  item.open = true
  item.querySelector('summary')?.focus({ preventScroll: true })
  if (scroll) item.scrollIntoView?.({ block: 'start', behavior: 'auto' })
  return item
}
