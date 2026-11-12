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
    expect(within(navigation).getByRole('link', { name: 'Schedules & classes' })).toHaveAttribute('href', '/member/schedules')
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

  it('keeps a consented Spori conversation in the floating panel', async () => {
    tokenStorage.startSession({ accessToken: 'test-assistant-access', refreshToken: 'test-assistant-refresh' })
    const { calls } = installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : call.path.includes('/consultation') ? {
      mode: 'KNOWLEDGE_BASED', status: 'ANSWERED', answer: "Hello! I'm Spori.", historyStored: false, sources: [], language: 'en',
    } : []))
    const user = userEvent.setup()
    renderApp('/member')

    const launcher = await screen.findByRole('button', { name: 'Ask Spori AI' })
    expect(within(launcher).getByText('Spori AI')).toBeInTheDocument()
    expect(launcher.querySelector('.member-concept__assistant-mark svg')).toBeInTheDocument()
    await user.click(launcher)
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    expect(dialog).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Close Spori AI' })).toHaveFocus()
    await user.type(within(dialog).getByRole('textbox', { name: 'Ask Spori a question' }), 'Hello')
    await user.click(within(dialog).getByRole('button', { name: 'Send' }))

    expect(await within(dialog).findByText("Hello! I'm Spori.")).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Center assistant' })).not.toBeInTheDocument()
    expect(calls.find(call => call.path.includes('/consultation'))?.body).toEqual({ question: 'Hello', optIn: true })
    expect(within(dialog).getByRole('link', { name: 'Open full assistant' })).toBeInTheDocument()
  })

  it('fills a suggested question in place on a narrow screen and expands only explicitly', async () => {
    previousWidth = window.innerWidth
    previousMatchMedia = window.matchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 })
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: vi.fn(() => ({
      matches: false,
      media: '(min-width: 960px)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })) })
    tokenStorage.startSession({ accessToken: 'test-narrow-assistant-access', refreshToken: 'test-narrow-assistant-refresh' })
    installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
    const user = userEvent.setup()
    renderApp('/member')

    await user.click(await screen.findByRole('button', { name: 'Ask Spori AI' }))
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    const prompt = within(dialog).getByRole('button', { name: /How should I warm up/ })
    await user.click(prompt)
    expect(within(dialog).getByRole('textbox', { name: 'Ask Spori a question' })).toHaveValue('How should I warm up before practice?')
    expect(screen.queryByRole('heading', { name: 'Center assistant' })).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('link', { name: 'Open full assistant' }))
    expect(await screen.findByRole('heading', { name: 'Center assistant' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Your question/ })).toHaveValue('')
  })

  it('does not send an empty floating question', async () => {
    tokenStorage.startSession({ accessToken: 'test-assistant-access', refreshToken: 'test-assistant-refresh' })
    const { calls } = installFakeFetch(call => json(200, call.path === '/users/me' ? MEMBER : []))
    const user = userEvent.setup()
    renderApp('/member')

    const launcher = await screen.findByRole('button', { name: 'Ask Spori AI' })
    expect(within(launcher).getByText('Spori AI')).toBeInTheDocument()
    expect(launcher.querySelector('.member-concept__assistant-mark svg')).toBeInTheDocument()
    await user.click(launcher)
    const dialog = screen.getByRole('dialog', { name: 'Spori AI' })
    await user.type(within(dialog).getByRole('textbox', { name: 'Ask Spori a question' }), '   ')
    expect(within(dialog).getByRole('button', { name: 'Send' })).toBeDisabled()
    expect(calls.some(call => call.path.includes('/consultation'))).toBe(false)
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
