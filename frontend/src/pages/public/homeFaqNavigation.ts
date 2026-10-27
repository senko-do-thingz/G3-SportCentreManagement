/** Reveal just the addressed Home FAQ. Summary clicks retain their native toggle behavior. */
export function revealHomeFaq(hash: string): HTMLDetailsElement | null {
  if (!/^#home-faq-\d+$/.test(hash)) return null
  const item = document.getElementById(hash.slice(1))
  if (!(item instanceof HTMLDetailsElement)) return null
  item.open = true
  item.querySelector('summary')?.focus({ preventScroll: true })
  return item
}
