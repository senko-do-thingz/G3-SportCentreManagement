import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ThemeProvider } from './ThemeProvider'
import { ThemeToggle } from './ThemeToggle'

describe('ThemeToggle', () => {
  it('names the next mode, switches the theme and keeps the class the screen passes', async () => {
    localStorage.setItem('sportify.themePreference', 'light')
    const user = userEvent.setup()
    render(<ThemeProvider><ThemeToggle className="auth-theme" /></ThemeProvider>)
    const toggle = screen.getByRole('button', { name: 'Switch to dark mode' })
    expect(toggle).toHaveClass('auth-theme')
    await user.click(toggle)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
  })
})
