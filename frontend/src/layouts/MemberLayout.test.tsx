/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { MemberHeader } from '../pages/reception/ReceptionMember'
import { installFakeFetch, json, MEMBER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

const layoutStyles = readFileSync('src/styles/layouts.css', 'utf8')
const receptionStyles = readFileSync('src/styles/reception.css', 'utf8')
const workspaceStyles = readFileSync('src/styles/workspace.css', 'utf8')
const conceptStyles = readFileSync('src/layouts/memberConceptB.css', 'utf8')

describe('Member Concept B shell', () => {
  let styles: HTMLStyleElement
  let previousWidth: number | undefined
  let previousMatchMedia: typeof window.matchMedia | undefined

  beforeEach(() => {
    styles = document.createElement('style')
    styles.textContent = [layoutStyles, receptionStyles, workspaceStyles, conceptStyles].join('\n')
    document.head.append(styles)
  })

  afterEach(() => {
    styles.remove()
    vi.unstubAllGlobals()
    if (previousWidth !== undefined) Object.defineProperty(window, 'innerWidth', { configurable: true, value: previousWidth })
    if (previousMatchMedia) Object.defineProperty(window, 'matchMedia', { configurable: true, value: previousMatchMedia })
    else Reflect.deleteProperty(window, 'matchMedia')
    previousWidth = undefined
    previousMatchMedia = undefined
  })

  it('keeps the new Member shell isolated while receptionist styles are loaded', async () => {
    tokenStorage.startSession({ accessToken: 'test-layout-access', refreshToken: 'test-layout-refresh' })
    installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
    renderApp('/member')

    const navigation = await screen.findByRole('navigation', { name: 'Member navigation' })
    const header = document.querySelector('.workspace-header') as HTMLElement
    expect(header).toHaveClass('workspace-header--member', 'member-layout__header')
    expect(getComputedStyle(header).position).toBe('sticky')
    expect(document.querySelector('.member-concept__topbar')).not.toBeInTheDocument()
    expect(within(header).getByRole('button', { name: 'Log out' })).toBeInTheDocument()
    expect(within(header).getByRole('button', { name: 'Switch to dark mode' })).toBeInTheDocument()
    expect(within(navigation).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(within(navigation).queryByRole('heading', { name: 'Member workspace' })).not.toBeInTheDocument()
    expect(within(navigation).getByRole('link', { name: 'My profile' })).toHaveAttribute('href', '/member/profile')
    expect(within(navigation).getByRole('link', { name: 'My schedule' })).toHaveAttribute('href', '/member/schedule')
    expect(within(navigation).getByRole('link', { name: 'Training progress' })).toHaveAttribute('href', '/member/training')
    expect(screen.getByText(MEMBER.fullName)).toBeInTheDocument()
    expect(screen.getByText('Member')).toBeInTheDocument()
  })

  it('opens the mobile workspace drawer, moves focus inside, and closes on Escape', async () => {
    previousWidth = window.innerWidth
    previousMatchMedia = window.matchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 })
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: vi.fn(() => ({
      matches: false,
      media: '(min-width: 960px)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })) })
    tokenStorage.startSession({ accessToken: 'test-mobile-access', refreshToken: 'test-mobile-refresh' })
    installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
    const user = userEvent.setup()
    styles.remove()
    styles.textContent = '.workspace-header .workspace-header__menu-toggle { display: inline-flex; }'
    document.head.append(styles)
    renderApp('/member')

    const toggle = await screen.findByRole('button', { name: 'Open member navigation' })
    await user.click(toggle)
    const dashboard = screen.getByRole('link', { name: 'Dashboard' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(dashboard).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')

    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
    expect(document.body.style.overflow).toBe('')
    await user.click(toggle)
    await user.click(screen.getByRole('link', { name: 'My profile' }))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
  })

  it('opens the Member-only Spori AI drawer and routes a suggested question to the consent screen', async () => {
    tokenStorage.startSession({ accessToken: 'test-assistant-access', refreshToken: 'test-assistant-refresh' })
    const { calls } = installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
    const user = userEvent.setup()
    renderApp('/member')

    await user.click(await screen.findByRole('button', { name: 'Ask Spori AI' }))
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    expect(dialog).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Close Spori AI' })).toHaveFocus()
    await user.keyboard('{Shift>}{Tab}{/Shift}')
    expect(within(dialog).getByRole('link', { name: 'Open full assistant' })).toHaveFocus()
    await user.keyboard('{Tab}')
    expect(within(dialog).getByRole('button', { name: 'Close Spori AI' })).toHaveFocus()
    await user.click(within(dialog).getByRole('link', { name: /How should I warm up/ }))

    expect(await screen.findByRole('heading', { name: 'Center assistant' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Center question/)).toHaveValue('How should I warm up before practice?')
    expect(screen.getByLabelText('Enable automated center consultation')).not.toBeChecked()
    expect(calls.some(call => call.path.includes('/consultation'))).toBe(false)
    expect(screen.queryByRole('button', { name: 'Ask Spori AI' })).not.toBeInTheDocument()
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

  it('keeps the shared workspace header and theme control when switching appearance', async () => {
    const previousPreference = window.localStorage.getItem('sportify.themePreference')
    window.localStorage.setItem('sportify.themePreference', 'light')
    try {
      tokenStorage.startSession({ accessToken: 'test-theme-access', refreshToken: 'test-theme-refresh' })
      installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
      const user = userEvent.setup()
      renderApp('/member')
      const themeToggle = await screen.findByRole('button', { name: 'Switch to dark mode' })
      const header = themeToggle.closest('.workspace-header') as HTMLElement
      const initialBackground = getComputedStyle(header).backgroundImage

      await user.click(themeToggle)
      expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
      expect(header).toHaveClass('workspace-header--member')
      expect(getComputedStyle(header).backgroundImage).toBe(initialBackground)
      expect(within(header).getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    } finally {
      if (previousPreference === null) window.localStorage.removeItem('sportify.themePreference')
      else window.localStorage.setItem('sportify.themePreference', previousPreference)
    }
  })
})
