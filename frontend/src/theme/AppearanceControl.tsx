import { useId } from 'react'
import { normalizePreference } from './theme'
import { useTheme } from './useTheme'

/** The same native, keyboard-accessible three-way control in every application header. */
export function AppearanceControl() {
  const id = useId()
  const { preference, setPreference } = useTheme()
  return (
    <div className="appearance">
      <label className="appearance__label" htmlFor={id}>Appearance</label>
      <select className="appearance__select" id={id} value={preference} onChange={(event) => setPreference(normalizePreference(event.target.value))}>
        <option value="system">System</option>
        <option value="light">Light</option>
        <option value="dark">Dark</option>
      </select>
    </div>
  )
}
