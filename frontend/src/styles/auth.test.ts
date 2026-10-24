import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * UI-06 and UI-07: Log in and Sign up share layout B through AuthShell. The Home hero photo fills the page with a
 * compact Home header and the form card on the right; every color, font and focus style comes from the shared
 * tokens (UI-00). Rendered contrast, focus, alignment and layout are checked in a real browser by
 * scripts/ui06-login-check.py (Log in) and scripts/ui07-signup-check.py (Sign up).
 */

const auth = readFileSync('src/styles/auth.css', 'utf8')
const layouts = readFileSync('src/styles/layouts.css', 'utf8')
const shell = readFileSync('src/pages/public/AuthShell.tsx', 'utf8')
const login = readFileSync('src/pages/public/LoginPage.tsx', 'utf8')
const register = readFileSync('src/pages/public/RegisterPage.tsx', 'utf8')

describe('Log in and Sign up theme (layout B)', () => {
  it('loads the shared stage styles and display fonts from AuthShell, so both screens always match', () => {
    expect(shell).toContain("import '../../styles/auth.css'")
    expect(shell).toContain("import '@fontsource/be-vietnam-pro/800.css'")
    expect(shell).toContain("import '@fontsource/be-vietnam-pro/800-italic.css'")
    expect(login).toContain('<AuthShell className="auth-shell--login"')
    expect(register).toContain('<AuthShell className="auth-shell--register"')
  })

  it('uses the bundled dunk photo, Back to Home and the shared sun and moon switch on both screens', () => {
    expect(shell).toContain("import authPhoto from '../../assets/auth/auth-dunk.jpg'")
    expect(shell).toContain('src={authPhoto} fallback={authPhoto}')
    expect(existsSync('src/assets/auth/auth-dunk.jpg')).toBe(true)
    expect(shell).toContain('<Link className="auth-stage__home" to="/">Back to Home</Link>')
    expect(shell).toContain('<ThemeToggle className="auth-theme" />')
    expect(shell).not.toContain('AppearanceControl')
  })

  it('retires the old split card and its unsourced photo', () => {
    expect(layouts).not.toMatch(/\.auth-shell\b|\.auth-hero\b|auth-panel__toolbar/)
    expect(existsSync('src/assets/auth-hero.jpg')).toBe(false)
  })

  it('has no local palette: no --login-* variables and no dark-mode override block', () => {
    expect(auth).not.toMatch(/--login-/)
    expect(auth).not.toMatch(/data-theme/)
  })

  it('uses the Home brand tokens for the hero, highlight, display type, focus and primary action', () => {
    for (const token of ['--color-hero', '--color-navy', '--color-hero-highlight', '--color-on-hero', '--color-on-hero-muted',
      '--color-highlight', '--font-display', '--focus-ring', '--color-primary', '--color-on-primary']) {
      expect(auth).toContain(`var(${token})`)
    }
  })

  it('squares fields and button, keeps the password checklist out of a box, aligns the Remember me row, widens the Sign up card and folds the photo on narrow screens', () => {
    expect(auth).toMatch(/\.auth-stage \.input \{[^}]*border-radius: 0;/)
    expect(auth).toMatch(/\.auth-stage \.auth-form \.btn--primary \{[^}]*border-radius: 0;/)
    expect(`${auth}\n${layouts}`).not.toMatch(/\.password-rules \{[^}]*(border|background):/)
    expect(auth).toMatch(/\.auth-stage \.auth-form__options \{[^}]*align-items: flex-start;/)
    expect(auth).toMatch(/\.auth-shell--register \.auth-stage__body \{[^}]*grid-template-columns: minmax\(0, 1fr\) 560px;/)
    expect(auth).toMatch(/@media \(max-width: 960px\) \{[\s\S]*?\.auth-stage__photo,\s*\.auth-stage::before \{[^}]*height: 360px;/)
  })
})
