export type ThemePreference = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'sportify.themePreference'
const SYSTEM_QUERY = '(prefers-color-scheme: dark)'

export function normalizePreference(value: unknown): ThemePreference {
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function themeStorage(): Storage | null {
  try { return window.localStorage } catch { return null }
}

export function readPreference(): ThemePreference {
  try { return normalizePreference(themeStorage()?.getItem(THEME_STORAGE_KEY)) } catch { return 'system' }
}

export function savePreference(preference: ThemePreference): void {
  try { themeStorage()?.setItem(THEME_STORAGE_KEY, preference) } catch { /* Keep the in-memory choice when storage is blocked. */ }
}

function systemMedia(): MediaQueryList | null {
  try { return typeof window.matchMedia === 'function' ? window.matchMedia(SYSTEM_QUERY) : null } catch { return null }
}

export function getSystemTheme(): ResolvedTheme {
  return systemMedia()?.matches ? 'dark' : 'light'
}

export function resolveTheme(preference: ThemePreference, system: ResolvedTheme): ResolvedTheme {
  return preference === 'system' ? system : preference
}

export function subscribeSystemTheme(notify: () => void): () => void {
  const media = systemMedia()
  if (!media) return () => undefined
  if (typeof media.addEventListener === 'function') {
    media.addEventListener('change', notify)
    return () => media.removeEventListener?.('change', notify)
  }
  // Older WebKit exposes only addListener/removeListener on MediaQueryList.
  if (typeof media.addListener === 'function') {
    media.addListener(notify)
    return () => media.removeListener?.(notify)
  }
  return () => undefined
}

export function applyTheme(preference: ThemePreference, resolved: ResolvedTheme): void {
  const root = document.documentElement
  root.dataset.theme = resolved
  root.dataset.themePreference = preference
  root.style.colorScheme = resolved
  root.style.backgroundColor = resolved === 'dark' ? '#0d1724' : '#f5f8fc'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#0d1724' : '#f5f8fc')
}
