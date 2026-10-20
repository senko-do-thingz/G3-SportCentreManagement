/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { act, cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { AppearanceControl } from './AppearanceControl'
import { ThemeProvider } from './ThemeProvider'
import { normalizePreference, resolveTheme, THEME_STORAGE_KEY, type ThemePreference } from './theme'
import { useTheme } from './useTheme'

const bootstrap = readFileSync('public/appearance.js', 'utf8')

function systemMedia(initial = false, legacy = false) {
  let dark = initial
  const listeners = new Set<() => void>()
  const add = vi.fn((listener: () => void) => { listeners.add(listener) })
  const remove = vi.fn((listener: () => void) => { listeners.delete(listener) })
  const media = {
    get matches() { return dark },
    ...(legacy ? { addListener: add, removeListener: remove } : {
      addEventListener: vi.fn((_type: string, listener: () => void) => add(listener)),
      removeEventListener: vi.fn((_type: string, listener: () => void) => remove(listener)),
    }),
  }
  vi.stubGlobal('matchMedia', vi.fn(() => media))
  return { listeners, add, remove, change: (value: boolean) => act(() => { dark = value; listeners.forEach((listener) => listener()) }) }
}

function Probe() {
  const { preference, resolved } = useTheme()
  return <output data-testid="theme-result">{preference}:{resolved}</output>
}

function showTheme() {
  return render(<ThemeProvider><AppearanceControl /><Probe /></ThemeProvider>)
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  document.documentElement.removeAttribute('data-theme')
  document.documentElement.removeAttribute('data-theme-preference')
  document.documentElement.style.removeProperty('color-scheme')
  document.documentElement.style.removeProperty('background-color')
})

describe('appearance preference', () => {
  it.each([null, undefined, '', 'corrupt', 'DARK', '{}', 'system'])('normalizes unsupported preference %j to system', (value) => {
    expect(normalizePreference(value)).toBe('system')
  })

  it.each(['light', 'dark'] as const)('defaults to system with OS %s and does not persist a resolved color', (system) => {
    systemMedia(system === 'dark')
    showTheme()
    expect(screen.getByLabelText('Appearance')).toHaveValue('system')
    expect(screen.getByTestId('theme-result')).toHaveTextContent(`system:${system}`)
    expect(document.documentElement.dataset.theme).toBe(system)
    expect(document.documentElement.style.colorScheme).toBe(system)
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()
  })

  it('follows runtime OS changes in system mode and keeps explicit overrides stable', async () => {
    const media = systemMedia(false)
    const user = userEvent.setup()
    showTheme()
    media.change(true)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:dark')
    await user.selectOptions(screen.getByLabelText('Appearance'), 'light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    media.change(false)
    media.change(true)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('light:light')
    await user.selectOptions(screen.getByLabelText('Appearance'), 'dark')
    media.change(false)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('dark:dark')
    await user.selectOptions(screen.getByLabelText('Appearance'), 'system')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('system')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:light')
    media.change(true)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:dark')
  })

  it('persists an explicit preference through provider remount and ignores unrelated auth keys', async () => {
    systemMedia(false)
    localStorage.setItem('sportify.accessToken', 'test-access')
    const user = userEvent.setup()
    const first = showTheme()
    await user.selectOptions(screen.getByLabelText('Appearance'), 'dark')
    first.unmount()
    showTheme()
    expect(screen.getByTestId('theme-result')).toHaveTextContent('dark:dark')
    expect(localStorage.getItem('sportify.accessToken')).toBe('test-access')
  })

  it('shares one preference across route changes and multiple control instances', async () => {
    systemMedia(false)
    const user = userEvent.setup()
    render(<MemoryRouter><ThemeProvider><AppearanceControl /><Probe /><Routes>
      <Route path="/" element={<Link to="/next">Next screen</Link>} />
      <Route path="/next" element={<AppearanceControl />} />
    </Routes></ThemeProvider></MemoryRouter>)
    await user.selectOptions(screen.getByLabelText('Appearance'), 'dark')
    await user.click(screen.getByRole('link', { name: 'Next screen' }))
    expect(screen.getAllByLabelText('Appearance')).toHaveLength(2)
    screen.getAllByLabelText('Appearance').forEach((control) => expect(control).toHaveValue('dark'))
    await user.selectOptions(screen.getAllByLabelText('Appearance')[1], 'light')
    screen.getAllByLabelText('Appearance').forEach((control) => expect(control).toHaveValue('light'))
  })

  it('updates other tabs, handles removed/corrupt preferences and ignores unrelated storage events', () => {
    systemMedia(true)
    showTheme()
    const fire = (key: string | null, newValue: string | null, storageArea = localStorage) => act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue, storageArea })))
    fire(THEME_STORAGE_KEY, 'light')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('light:light')
    fire('sportify.sessionId', 'test-session')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('light:light')
    fire(THEME_STORAGE_KEY, 'dark', sessionStorage)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('light:light')
    fire(THEME_STORAGE_KEY, 'invalid')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:dark')
    fire(THEME_STORAGE_KEY, null)
    expect(screen.getByLabelText('Appearance')).toHaveValue('system')
    fire(THEME_STORAGE_KEY, 'light')
    fire(null, null)
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:dark')
  })

  it('handles corrupt and inaccessible localStorage while retaining an in-memory selection', async () => {
    systemMedia(true)
    localStorage.setItem(THEME_STORAGE_KEY, 'corrupt')
    const first = showTheme()
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:dark')
    first.unmount()
    vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError') })
    const user = userEvent.setup()
    showTheme()
    await user.selectOptions(screen.getByLabelText('Appearance'), 'light')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('light:light')
  })

  it('keeps an explicit selection when setItem is blocked', async () => {
    systemMedia(false)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota', 'QuotaExceededError') })
    const user = userEvent.setup()
    showTheme()
    await user.selectOptions(screen.getByLabelText('Appearance'), 'dark')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('dark:dark')
  })

  it.each(['missing', 'throwing'] as const)('supports explicit themes with %s matchMedia', async (kind) => {
    vi.stubGlobal('matchMedia', kind === 'missing' ? undefined : () => { throw new Error('Unavailable') })
    const user = userEvent.setup()
    showTheme()
    expect(screen.getByTestId('theme-result')).toHaveTextContent('system:light')
    await user.selectOptions(screen.getByLabelText('Appearance'), 'dark')
    expect(screen.getByTestId('theme-result')).toHaveTextContent('dark:dark')
  })

  it.each([false, true])('cleans up %s legacy media and storage listeners on unmount', (legacy) => {
    const media = systemMedia(false, legacy)
    const removeStorage = vi.spyOn(window, 'removeEventListener')
    const result = showTheme()
    expect(media.listeners.size).toBe(1)
    result.unmount()
    expect(media.listeners.size).toBe(0)
    expect(media.remove).toHaveBeenCalledTimes(1)
    expect(removeStorage).toHaveBeenCalledWith('storage', expect.any(Function))
  })

  it('has a visible label, three named choices and stable focus after selection', async () => {
    systemMedia()
    const user = userEvent.setup()
    showTheme()
    const select = screen.getByRole('combobox', { name: 'Appearance' })
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['System', 'Light', 'Dark'])
    await user.tab()
    expect(select).toHaveFocus()
    await user.selectOptions(select, 'dark')
    expect(screen.getByRole('option', { name: 'Dark', selected: true })).toBeInTheDocument()
    expect(select).toHaveFocus()
  })
})

describe('before-paint bootstrap parity', () => {
  it.each((['system', 'light', 'dark', 'corrupt', null] as const).flatMap((saved) => [false, true].map((dark) => [saved, dark] as const)))('matches runtime resolution for saved %s and OS dark=%s', (saved, dark) => {
    const preference = normalizePreference(saved)
    runInNewContext(bootstrap, { document, localStorage: { getItem: () => saved }, matchMedia: () => ({ matches: dark }) })
    expect(document.documentElement.dataset.theme).toBe(resolveTheme(preference, dark ? 'dark' : 'light'))
    expect(document.documentElement.dataset.themePreference).toBe(preference)
  })

  it('survives blocked storage and missing media queries before React loads', () => {
    runInNewContext(bootstrap, { document, localStorage: { getItem: () => { throw new Error('Blocked') } } })
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.dataset.themePreference).toBe('system')
  })

  it('runs as an external blocking head script before the application module', () => {
    const html = readFileSync('index.html', 'utf8')
    expect(html).toContain('<script src="/appearance.js"></script>')
    expect(html.indexOf('/appearance.js')).toBeLessThan(html.indexOf('</head>'))
    expect(html.indexOf('/appearance.js')).toBeLessThan(html.indexOf('/src/main.tsx'))
    expect((['system', 'light', 'dark'] satisfies ThemePreference[])).toHaveLength(3)
  })
})
