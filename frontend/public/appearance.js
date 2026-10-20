/* Runs before the Vite app. Keep resolution in sync with src/theme/theme.ts (parity tested). */
(() => {
  let preference = 'system'
  let systemDark = false
  try {
    const saved = localStorage.getItem('sportify.themePreference')
    if (saved === 'light' || saved === 'dark') preference = saved
  } catch { /* Storage is optional; system appearance still works. */ }
  try { systemDark = typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches } catch { /* Fall back to light if media queries are unavailable. */ }
  const resolved = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
  const root = document.documentElement
  root.dataset.theme = resolved
  root.dataset.themePreference = preference
  root.style.colorScheme = resolved
  root.style.backgroundColor = resolved === 'dark' ? '#0d1724' : '#f5f8fc'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#0d1724' : '#f5f8fc')
})()
