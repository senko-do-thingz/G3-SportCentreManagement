import { useCallback, useEffect, useLayoutEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react'
import { applyTheme, getSystemTheme, normalizePreference, readPreference, resolveTheme, savePreference, subscribeSystemTheme, themeStorage, THEME_STORAGE_KEY, type ResolvedTheme, type ThemePreference } from './theme'
import { ThemeContext } from './themeContext'

/** One preference for the app, independent of accounts and stable across route transitions. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setChoice] = useState(readPreference)
  const system = useSyncExternalStore<ResolvedTheme>(subscribeSystemTheme, getSystemTheme, () => 'light')
  const resolved = resolveTheme(preference, system)

  useLayoutEffect(() => { applyTheme(preference, resolved) }, [preference, resolved])

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.storageArea && event.storageArea !== themeStorage()) return
      if (event.key === THEME_STORAGE_KEY || event.key === null) setChoice(normalizePreference(event.newValue))
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const setPreference = useCallback((choice: ThemePreference) => {
    setChoice(choice)
    savePreference(choice)
  }, [])
  const value = useMemo(() => ({ preference, resolved, setPreference }), [preference, resolved, setPreference])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
