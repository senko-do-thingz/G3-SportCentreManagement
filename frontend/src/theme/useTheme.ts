import { useContext } from 'react'
import { ThemeContext } from './themeContext'

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('Appearance controls require ThemeProvider.')
  return context
}
