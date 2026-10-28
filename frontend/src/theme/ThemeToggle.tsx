import { useTheme } from './useTheme'

/**
 * Light and dark switch shared by Home, authentication and payment workspace headers.
 * The icon shows the current mode (sun for light, moon for dark); the label names the action.
 */
export function ThemeToggle({ className = 'theme-toggle' }: { className?: string }) {
  const { resolved, setPreference } = useTheme()
  const next = resolved === 'dark' ? 'light' : 'dark'
  return (
    <button type="button" className={className} aria-label={`Switch to ${next} mode`} title={`${resolved === 'dark' ? 'Dark' : 'Light'} mode. Switch to ${next} mode`} onClick={() => setPreference(next)}>
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" focusable="false">
        {resolved === 'dark'
          ? <path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z" />
          : <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></>}
      </svg>
    </button>
  )
}
