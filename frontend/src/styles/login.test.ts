import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * UI-06 Log in theme. The screen takes every color, font and focus style from the shared tokens (UI-00), so it
 * follows light and dark with no local palette. Rendered contrast, focus, alignment and layout are checked in a
 * real browser by scripts/ui06-login-check.py.
 */

const login = readFileSync('src/styles/login.css', 'utf8')
const layouts = readFileSync('src/styles/layouts.css', 'utf8')
const page = readFileSync('src/pages/public/LoginPage.tsx', 'utf8')

describe('UI-06 Log in theme', () => {
  it('keeps the Log in styles in login.css, loaded by the Log in page only', () => {
    expect(layouts).not.toContain('auth-shell--login')
    expect(page).toContain("import '../../styles/login.css'")
  })

  it('has no local palette: no --login-* variables and no dark-mode override block', () => {
    expect(login).not.toMatch(/--login-/)
    expect(login).not.toMatch(/data-theme/)
  })

  it('uses the Home brand tokens for the hero, highlight, display type, focus and primary action', () => {
    for (const token of ['--color-hero', '--color-hero-deep', '--color-hero-highlight', '--color-on-hero-muted', '--color-highlight',
      '--font-display', '--focus-ring', '--color-primary', '--color-on-primary']) {
      expect(login).toContain(`var(${token})`)
    }
  })

  it('loads the display font weights it uses', () => {
    expect(page).toContain("import '@fontsource/be-vietnam-pro/800.css'")
    expect(page).toContain("import '@fontsource/be-vietnam-pro/800-italic.css'")
  })

  it('squares the card, fields and button like Home and top-aligns the Remember me row', () => {
    expect(login).toMatch(/\.auth-shell\.auth-shell--login \{[^}]*border-radius: 0;/)
    expect(login).toMatch(/\.auth-shell--login \.input \{[^}]*border-radius: 0;/)
    expect(login).toMatch(/\.auth-shell--login \.auth-form \.btn--primary \{[^}]*border-radius: 0;/)
    expect(login).toMatch(/\.auth-shell--login \.auth-form__options \{[^}]*align-items: flex-start;/)
  })
})
