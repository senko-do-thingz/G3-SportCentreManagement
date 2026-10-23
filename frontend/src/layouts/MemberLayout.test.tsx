/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { MemberHeader } from '../pages/reception/ReceptionMember'
import { installFakeFetch, json, MEMBER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

// Vitest disables CSS imports; load the actual sheets from the frontend test working directory.
const layoutStyles = readFileSync('src/styles/layouts.css', 'utf8')
const receptionStyles = readFileSync('src/styles/reception.css', 'utf8')

describe('member header style isolation', () => {
  let styles: HTMLStyleElement

  beforeEach(() => {
    // Match index.css import order to exercise the cross-feature selector collision.
    styles = document.createElement('style')
    styles.textContent = `${layoutStyles}\n${receptionStyles}`
    document.head.append(styles)
  })

  afterEach(() => {
    styles.remove()
    vi.unstubAllGlobals()
  })

  it('keeps member navigation below the right-aligned user bar with receptionist styles loaded', async () => {
    tokenStorage.startSession({ accessToken: 'test-layout-access', refreshToken: 'test-layout-refresh' })
    installFakeFetch((call) => json(200, call.path === '/users/me' ? MEMBER : []))
    renderApp('/member')

    const navigation = await screen.findByRole('navigation', { name: 'Member navigation' })
    const header = navigation.closest('header')!
    const logout = within(header).getByRole('button', { name: 'Log out' })
    const userBar = logout.closest('.member-header__bar')

    expect(getComputedStyle(header).display).toBe('block')
    expect(userBar).not.toBeNull()
    expect(getComputedStyle(userBar!).justifyContent).toBe('space-between')
    expect(userBar).not.toContainElement(navigation)
    expect(within(navigation).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).getByRole('link', { name: 'My Profile' })).toHaveAttribute('href', '/member/profile')
    expect(within(header).getByText(MEMBER.fullName)).toBeInTheDocument()
    expect(within(header).getByText('Member')).toBeInTheDocument()
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

  it('uses the refreshed Royal, Navy, and Gold member header palette in light and dark modes', () => {
    const root = document.documentElement
    const previousTheme = root.getAttribute('data-theme')
    const header = document.createElement('header')
    header.className = 'member-layout__header'
    document.body.append(header)

    try {
      root.removeAttribute('data-theme')
      expect(getComputedStyle(header).getPropertyValue('--member-header-navy').trim()).toBe('#0b1736')
      expect(getComputedStyle(header).getPropertyValue('--member-header-gold').trim()).toBe('#ffc83d')

      root.setAttribute('data-theme', 'dark')
      expect(getComputedStyle(header).getPropertyValue('--member-header-navy').trim()).toBe('#060b18')
      expect(getComputedStyle(header).getPropertyValue('--member-header-gold').trim()).toBe('#ffd65e')
    } finally {
      header.remove()
      if (previousTheme) root.setAttribute('data-theme', previousTheme)
      else root.removeAttribute('data-theme')
    }
  })
})
