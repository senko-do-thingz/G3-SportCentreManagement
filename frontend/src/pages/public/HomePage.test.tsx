import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import type { Role } from '../../api/types'
import { backendError, installFakeFetch, json, MANAGER, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

const SPORTS = [{ id: 41, code: 'FOOTBALL', name: 'Football' }, { id: 52, code: 'BADMINTON', name: 'Badminton' }]
const PACKAGES = [{ id: 81, name: 'Football Independent', sportId: 41, sportName: 'Football', trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, priceAmount: 500000, isActive: true }]
const TIERS = [{ id: 1, name: 'Gold', discountPercentage: 5, isActive: true }]

function catalog(call: RecordedCall) {
  if (call.path === '/sports') return json(200, SPORTS)
  if (call.path === '/packages') return json(200, PACKAGES)
  if (call.path === '/membership-cards/tiers') return json(200, TIERS)
  return backendError(404, 'Not found')
}

function session(role: Role) {
  tokenStorage.startSession({ accessToken: 'home-access', refreshToken: 'home-refresh' })
  return { ...MEMBER, role }
}

afterEach(() => vi.unstubAllGlobals())

describe('public Home', () => {
  it('renders every reference section without authentication and only requests the public catalogue', async () => {
    const { calls } = installFakeFetch(catalog)
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport.*Build your skills/ })).toBeInTheDocument()
    for (const name of ['Find your next class', 'Choose your sport', 'Find a class that fits', 'Not sure where to start?', 'Learn from qualified coaches', 'Choose how you train', 'Membership card benefits']) {
      expect(screen.getByRole('heading', { name })).toBeInTheDocument()
    }
    expect(await screen.findByRole('link', { name: 'View options for Football Independent' })).toHaveAttribute('href', '/member/packages/81')
    await waitFor(() => expect(screen.getByText(/Gold: 5% off/)).toBeInTheDocument())
    expect(calls.map((call) => call.path).sort()).toEqual(['/membership-cards/tiers', '/packages', '/sports'])
    expect(calls.every((call) => call.method === 'GET' && call.authorization === null)).toBe(true)
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Age group')).toBeDisabled()
    expect(screen.getByText(/Example classes/)).toBeInTheDocument()
    expect(screen.getByText(/Illustrative profiles/)).toBeInTheDocument()
  })

  it.each(['MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH'] as const)('keeps %s on Home and exposes its workspace', async (role) => {
    const profile = session(role)
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? json(200, profile) : catalog(call))
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    const paths = { MEMBER: '/member', MANAGER: '/manager', RECEPTIONIST: '/reception', COACH: '/coach' }
    expect(await screen.findByRole('link', { name: 'My workspace' })).toHaveAttribute('href', paths[role])
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(calls.filter((call) => call.path !== '/users/me').every((call) => call.authorization === null)).toBe(true)
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('renders primary content immediately while the stored session is still restoring', async () => {
    session('MEMBER')
    let resolveProfile: (value: Response) => void = () => undefined
    installFakeFetch((call) => call.path === '/users/me' ? new Promise<Response>((resolve) => { resolveProfile = resolve }) : catalog(call))
    renderApp('/')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(screen.getByText('Restoring your session...')).toBeInTheDocument()
    await act(async () => resolveProfile(json(200, MEMBER)))
    expect(await screen.findByRole('link', { name: 'My workspace' })).toBeInTheDocument()
  })

  it('keeps Home visible after account restoration errors without discarding tokens', async () => {
    session('MEMBER')
    installFakeFetch((call) => call.path === '/users/me' ? backendError(503, 'Unavailable') : catalog(call))
    renderApp('/')
    await screen.findByRole('link', { name: 'Join now' })
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(true)
    expect(screen.queryByRole('heading', { name: 'We could not load your account' })).not.toBeInTheDocument()
  })

  it('shows recoverable catalogue errors while all main Home sections stay visible', async () => {
    let online = false
    installFakeFetch((call) => online ? catalog(call) : backendError(503, 'Unavailable'))
    const user = userEvent.setup()
    renderApp('/')
    const message = await screen.findByText('Current packages and prices are temporarily unavailable.')
    expect(screen.getByRole('heading', { name: 'Choose your sport' })).toBeInTheDocument()
    online = true
    await user.click(within(message.parentElement!).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'View options for Football Independent' })).toBeInTheDocument()
  })

  it('shows empty and malformed catalogues without presenting reference prices as current offers', async () => {
    installFakeFetch((call) => call.path === '/packages' ? json(200, []) : json(200, { unexpected: true }))
    renderApp('/')
    expect(await screen.findByText(/No packages are currently available/)).toBeInTheDocument()
    expect(await screen.findByText(/Sport filters are temporarily unavailable/)).toBeInTheDocument()
    expect(screen.queryByText('500,000 VND')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })

  it('rejects malformed catalogue records without crashing the public page', async () => {
    installFakeFetch(() => json(200, [null]))
    renderApp('/')
    expect(await screen.findByText(/Current packages and prices are temporarily unavailable/)).toBeInTheDocument()
    expect(await screen.findByText(/Sport filters are temporarily unavailable/)).toBeInTheDocument()
    expect(await screen.findByText(/Current card benefits are temporarily unavailable/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })

  it('features distinct training types from live packages and follows the existing zero-day single-visit rule', async () => {
    const coached = { ...PACKAGES[0], id: 82, name: 'Football Coached', trainingFormat: 'COACH_LED', priceAmount: 900000 }
    const single = { ...coached, id: 83, name: 'Football Day Visit', durationDays: 0, priceAmount: 150000 }
    installFakeFetch((call) => call.path === '/packages' ? json(200, [...PACKAGES, { ...PACKAGES[0], id: 85, name: 'Another self-training package' }, coached, single]) : catalog(call))
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'View options for Football Coached' })).toHaveAttribute('href', '/member/packages/82')
    expect(screen.getByRole('link', { name: 'View options for Football Day Visit' })).toHaveAttribute('href', '/member/packages/83')
    expect(screen.queryByRole('link', { name: 'View options for Another self-training package' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Football - Single visit' })).toBeInTheDocument()
  })

  it('preserves selected sport and level through the existing login return flow', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, { accessToken: 'logged-in', refreshToken: 'refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      if (call.path.startsWith('/sessions?')) return json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      return catalog(call)
    })
    const user = userEvent.setup()
    renderApp('/')
    await screen.findByRole('option', { name: 'Football' })
    await user.selectOptions(screen.getByLabelText('Sport'), '41')
    await user.selectOptions(screen.getByLabelText('Level'), 'BEGINNER')
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    await user.type(screen.getByLabelText(/email address/i), 'member@example.test')
    await user.type(screen.getByLabelText(/^password/i), 'Test-password1!')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Find Classes' })
    await waitFor(() => expect(calls.some((call) => call.path.startsWith('/sessions?') && call.path.includes('sportId=41') && call.path.includes('level=BEGINNER'))).toBe(true))
    expect(screen.getByRole('link', { name: 'Sportify Center home' })).toHaveAttribute('href', '/')
  })

  it('uses real catalogue IDs for sport exploration and package links', async () => {
    installFakeFetch(catalog)
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Explore Football' })).toHaveAttribute('href', '/member/classes?sportId=41')
    expect(screen.getByRole('link', { name: 'Explore Badminton' })).toHaveAttribute('href', '/member/classes?sportId=52')
    expect(screen.getByRole('link', { name: 'View options for Football Independent' })).toHaveAttribute('href', '/member/packages/81')
    expect(screen.getByRole('link', { name: 'View cards' })).toHaveAttribute('href', '/member/membership/cards')
    expect(screen.getByRole('link', { name: 'Join now' })).toHaveAttribute('href', '/register')
  })

  it('explains member-only actions to staff without navigating to a forbidden route', async () => {
    session('MANAGER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MANAGER) : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    await screen.findByRole('link', { name: 'My workspace' })
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    const dialog = screen.getByRole('dialog', { name: 'Member booking and purchases' })
    expect(within(dialog).getByRole('link', { name: 'Open my workspace' })).toHaveAttribute('href', '/manager')
    expect(screen.queryByRole('heading', { name: 'Access denied' })).not.toBeInTheDocument()
  })

  it.each(['Get recommendations', 'Contact reception', 'AI Assistant', 'View profile of Daniel Smith'])('opens an accessible availability dialog for %s and restores focus', async (name) => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const trigger = screen.getByRole('button', { name })
    await user.click(trigger)
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('toggles the mobile navigation with keyboard controls and Escape restores toggle focus', async () => {
    installFakeFetch(catalog)
    const user = userEvent.setup()
    renderApp('/')
    const toggle = screen.getByRole('button', { name: 'Menu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const nav = screen.getByRole('navigation', { name: 'Home navigation' })
    within(nav).getByRole('link', { name: 'Sports' }).focus()
    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
    await user.click(toggle)
    await user.click(within(nav).getByRole('link', { name: 'Sports' }))
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  it('keeps a cross-tab account switch on public Home', async () => {
    session('MANAGER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, call.authorization === 'Bearer member-access' ? MEMBER : MANAGER) : catalog(call))
    renderApp('/')
    await screen.findByText('Morgan Lee')
    const oldSession = tokenStorage.getSessionId()
    act(() => {
      localStorage.setItem('sportify.accessToken', 'member-access')
      localStorage.setItem('sportify.refreshToken', 'member-refresh')
      localStorage.setItem('sportify.sessionId', 'home-switch')
      window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: oldSession, newValue: 'home-switch' }))
    })
    await screen.findByText('Taylor Nguyen')
    expect(screen.getByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'My workspace' })).toHaveAttribute('href', '/member')
  })

  it('logs out with the existing session-clearing and login destination', async () => {
    session('MEMBER')
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : catalog(call))
    const user = userEvent.setup()
    renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Log out' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(tokenStorage.hasSession()).toBe(false)
    await user.click(screen.getByRole('link', { name: 'Sportify Center home' }))
    expect(await screen.findByRole('heading', { name: /Find your sport/ })).toBeInTheDocument()
  })
})
