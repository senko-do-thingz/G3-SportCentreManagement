/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { MemberHeader } from '../pages/reception/ReceptionMember'
import { installFakeFetch, json, MEMBER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

// Vitest disables CSS imports; load the actual sheets from the frontend test working directory.
const themeTokens = readFileSync('src/styles/tokens.css', 'utf8')
const layoutStyles = readFileSync('src/styles/layouts.css', 'utf8')
const receptionStyles = readFileSync('src/styles/reception.css', 'utf8')

describe('member header style isolation', () => {
  let styles: HTMLStyleElement
  let mobileMenuStyles: HTMLStyleElement | undefined

  beforeEach(() => {
    // Match index.css import order to exercise the cross-feature selector collision.
    styles = document.createElement('style')
    styles.textContent = `${themeTokens}\n${layoutStyles}\n${receptionStyles}`
    document.head.append(styles)
  })

  afterEach(() => {
    styles.remove()
    mobileMenuStyles?.remove()
    mobileMenuStyles = undefined
    tokenStorage.clear()
    vi.unstubAllGlobals()
  })

  it('keeps member navigation in the Home-style header with receptionist styles loaded', async () => {
    tokenStorage.startSession({ accessToken: 'test-layout-access', refreshToken: 'test-layout-refresh' })
    installFakeFetch((call) => json(200, call.path === '/users/me' ? MEMBER : []))
    renderApp('/member')

    const navigation = await screen.findByRole('navigation', { name: 'Member navigation' })
    const header = navigation.closest('header')!
    const logout = within(header).getByRole('button', { name: 'Log out' })
    const userBar = logout.closest('.workspace-header__bar')

    expect(getComputedStyle(header).display).toBe('block')
    expect(userBar).not.toBeNull()
    expect(getComputedStyle(userBar!).display).toBe('flex')
    expect(within(header).getByRole('button', { name: /Switch to .* mode/ })).toHaveClass('home-theme')
    expect(within(header).queryByLabelText('Appearance')).not.toBeInTheDocument()
    expect(userBar).toContainElement(navigation)
    expect(within(navigation).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).getByRole('link', { name: 'My Profile' })).toHaveAttribute('href', '/member/profile')
    expect(within(header).getByText(MEMBER.fullName)).toBeInTheDocument()
    expect(within(header).getByText('Member')).toBeInTheDocument()
  })

  it('opens and closes the mobile member menu with Escape and restores focus', async () => {
    const user = userEvent.setup()
    mobileMenuStyles = document.createElement('style')
    mobileMenuStyles.textContent = '.workspace-header__menu-toggle { display: inline-flex !important; }'
    document.head.append(mobileMenuStyles)
    tokenStorage.startSession({ accessToken: 'test-layout-access', refreshToken: 'test-layout-refresh' })
    installFakeFetch((call) => json(200, call.path === '/users/me' ? MEMBER : []))
    renderApp('/member')

    const toggle = await screen.findByRole('button', { name: 'Open member navigation' })
    const navigation = screen.getByRole('navigation', { name: 'Member navigation' })
    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(navigation).toHaveClass('workspace-header__nav--open')
    await user.keyboard('{Escape}')

    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(navigation).not.toHaveClass('workspace-header__nav--open')
    expect(toggle).toHaveFocus()
  })

  it('shares the Home navy and gold shell tokens in light and dark themes', () => {
    const root = document.documentElement
    const previousTheme = root.getAttribute('data-theme')
    const shell = document.createElement('div')
    shell.className = 'staff-layout'
    const header = document.createElement('header')
    header.className = 'workspace-header'
    const sidebar = document.createElement('aside')
    sidebar.className = 'sidebar'
    shell.append(header, sidebar)
    document.body.append(shell)

    try {
      root.removeAttribute('data-theme')
      expect(getComputedStyle(header).getPropertyValue('--workspace-navy').trim()).toBe('var(--color-hero)')
      expect(getComputedStyle(root).getPropertyValue('--color-hero').trim()).toBe('#0b1736')
      expect(getComputedStyle(sidebar).getPropertyValue('--workspace-gold').trim()).toBe('var(--color-hero-highlight)')

      root.setAttribute('data-theme', 'dark')
      expect(getComputedStyle(header).getPropertyValue('--workspace-navy').trim()).toBe('var(--color-hero)')
      expect(getComputedStyle(root).getPropertyValue('--color-hero').trim()).toBe('#060b18')
      expect(getComputedStyle(sidebar).getPropertyValue('--workspace-gold').trim()).toBe('var(--color-hero-highlight)')
    } finally {
      shell.remove()
      if (previousTheme) root.setAttribute('data-theme', previousTheme)
      else root.removeAttribute('data-theme')
    }
  })

  it('preserves the receptionist member summary flex layout', () => {
    const { container } = render(<MemberHeader member={{
      userId: MEMBER.id,
      memberCode: 'MEM-TEST',
      fullName: MEMBER.fullName,
      email: MEMBER.email,
      phone: MEMBER.phone,
      currentLevel: null,
      status: MEMBER.status,
    }} />)

    expect(getComputedStyle(container.firstElementChild!).display).toBe('flex')
    expect(screen.getByText(MEMBER.fullName)).toBeInTheDocument()
  })
})
