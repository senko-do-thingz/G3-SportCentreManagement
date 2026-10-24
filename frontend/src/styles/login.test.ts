import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/*
 * UI-06 Log in theme, layout B. The Home hero photo fills the page with a compact Home header and the form card on
 * the right; every color, font and focus style comes from the shared tokens (UI-00). Rendered contrast, focus,
 * alignment and layout are checked in a real browser by scripts/ui06-login-check.py.
 */

const login = readFileSync('src/styles/login.css', 'utf8')
const layouts = readFileSync('src/styles/layouts.css', 'utf8')
const page = readFileSync('src/pages/public/LoginPage.tsx', 'utf8')
const shell = readFileSync('src/pages/public/AuthShell.tsx', 'utf8')

describe('UI-06 Log in theme', () => {
  it('keeps the Log in styles in login.css, loaded by the Log in page only', () => {
    expect(layouts).not.toContain('auth-shell--login')
    expect(page).toContain("import '../../styles/login.css'")
  })

  it('uses the stage layout with the Home hero photo, Back to Home and the sun and moon switch', () => {
    expect(page).toMatch(/<AuthShell className="auth-shell--login" layout="stage"/)
    expect(shell).toContain('src={HOME_PHOTOS.hero} fallback={homeHero}')
    expect(shell).toContain('<Link className="auth-stage__home" to="/">Back to Home</Link>')
    expect(shell).toContain('<ThemeToggle className="auth-theme" />')
  })

  it('has no local palette: no --login-* variables and no dark-mode override block', () => {
    expect(login).not.toMatch(/--login-/)
    expect(login).not.toMatch(/data-theme/)
  })

  it('uses the Home brand tokens for the hero, highlight, display type, focus and primary action', () => {
    for (const token of ['--color-hero', '--color-navy', '--color-hero-highlight', '--color-on-hero', '--color-on-hero-muted',
      '--color-highlight', '--font-display', '--focus-ring', '--color-primary', '--color-on-primary']) {
      expect(login).toContain(`var(${token})`)
    }
  })

  it('loads the display font weights it uses', () => {
    expect(page).toContain("import '@fontsource/be-vietnam-pro/800.css'")
    expect(page).toContain("import '@fontsource/be-vietnam-pro/800-italic.css'")
  })

  it('squares the fields and button like Home, top-aligns the Remember me row and folds the photo into a band on narrow screens', () => {
    expect(login).toMatch(/\.auth-shell--login \.input \{[^}]*border-radius: 0;/)
    expect(login).toMatch(/\.auth-shell--login \.auth-form \.btn--primary \{[^}]*border-radius: 0;/)
    expect(login).toMatch(/\.auth-shell--login \.auth-form__options \{[^}]*align-items: flex-start;/)
    expect(login).toMatch(/@media \(max-width: 960px\) \{[\s\S]*?\.auth-stage__photo,\s*\.auth-stage::before \{[^}]*height: 360px;/)
  })
})
