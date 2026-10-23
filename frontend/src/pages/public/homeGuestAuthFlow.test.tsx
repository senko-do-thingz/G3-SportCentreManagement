import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'

const SPORTS = ['Football', 'Badminton', 'Basketball', 'Volleyball', 'Swimming', 'Tennis'].map((name, index) => ({ id: 41 + index, name, code: name.toUpperCase() }))
const PACKAGES = [
  { id: 81, code: 'FT-SELF', name: 'Football Independent', sportId: 41, sportName: 'Football', trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 500000, isActive: true },
  { id: 82, code: 'FT-COACH', name: 'Football Coached', sportId: 41, sportName: 'Football', trainingFormat: 'COACH_LED', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 900000, isActive: true },
  { id: 83, code: 'FT-SINGLE', name: 'Football Day Visit', sportId: 41, sportName: 'Football', trainingFormat: 'COACH_LED', durationDays: 1, sessionCount: 1, sessionMinutes: 60, priceAmount: 150000, isActive: true },
]
const TARGET = '/member/classes?sportId=41&level=BEGINNER&format=COACH_LED'

function catalogue(call: RecordedCall): Response {
  if (call.path === '/sports') return json(200, SPORTS)
  if (call.path === '/packages') return json(200, PACKAGES)
  if (call.path === '/packages/81') return json(200, PACKAGES[0])
  if (call.path === '/coaches/public') return json(200, [{ id: 21, fullName: 'Public Coach', headline: 'Football coach', specialties: 'Passing', yearsExperience: 3, sportIds: [41] }])
  if (call.path === '/facilities/public') return json(200, [{ id: 63, name: 'Football Field', facilityType: 'FIELD', sportId: 41, capacity: 22 }])
  if (call.path.startsWith('/membership-cards/')) return json(200, [])
  if (call.path.startsWith('/sessions?')) return json(200, { content: [], page: 0, size: Number(new URLSearchParams(call.path.split('?')[1]).get('size')), totalElements: 0, totalPages: 0 })
  if (call.path === '/packages/registrations/my') return json(200, [])
  return backendError(404, 'Not found')
}

function LocationProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return <><output aria-label="Current location">{location.pathname + location.search}</output><output aria-label="Route state">{JSON.stringify(location.state)}</output><button onClick={() => navigate(-1)}>Test browser Back</button></>
}

function app(path = '/', state?: unknown) {
  return render(<MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? `?${path.split('?')[1]}` : '', state }]}>
    <ToastProvider><AuthProvider><AppRoutes /><LocationProbe /></AuthProvider></ToastProvider>
  </MemoryRouter>)
}

async function filters(user: ReturnType<typeof userEvent.setup>) {
  for (const [label, option] of [['Sport', 'Football'], ['Level', 'Beginner'], ['Training', 'Coach-led']]) {
    await user.click(screen.getByRole('button', { name: new RegExp(`^${label}`) }))
    await user.click(await screen.findByRole('option', { name: option }))
  }
}

function expectFilters() {
  for (const [label, value] of [['Sport', 'Football'], ['Level', 'Beginner'], ['Training', 'Coach-led']]) {
    expect(screen.getByRole('button', { name: new RegExp(`^${label}`) })).toHaveTextContent(value)
  }
}

async function credentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email address/i), 'fixture@example.invalid')
  await user.type(screen.getByLabelText(/^password/i), 'Sportify123!')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

async function registration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/full name/i), 'Fixture Member')
  await user.type(screen.getByLabelText(/phone number/i), '0901234567')
  await user.type(screen.getByLabelText(/email address/i), 'fixture@example.invalid')
  await user.type(screen.getByLabelText(/^password/i), 'Sportify123!')
  await user.type(screen.getByLabelText(/confirm password/i), 'Sportify123!')
  await user.click(screen.getByRole('checkbox', { name: /terms of use/ }))
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

afterEach(() => vi.unstubAllGlobals())

describe('Home guest authentication gate', () => {
  it('keeps public discovery, FAQ, coach information, themes and filters public with GET-only startup', async () => {
    const { calls } = installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    await screen.findByRole('heading', { name: 'Public Coach' })
    expect(calls.map((call) => call.path).sort()).toEqual(['/coaches/public', '/facilities/public', '/membership-cards/tiers', '/packages', '/sports'])
    await filters(user)
    await user.click(screen.getByRole('button', { name: 'Next sports' }))
    await user.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    await user.click(document.querySelector('summary')!)
    await user.click(screen.getByRole('button', { name: 'View profile of Public Coach' }))
    expect(screen.getByRole('dialog')).not.toHaveAccessibleName('Sign in to continue')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expectFilters()
    expect(calls.every((call) => call.method === 'GET' && call.authorization === null)).toBe(true)
  })

  it.each([
    ...SPORTS.map((sport) => [`Explore ${sport.name}`, /current classes/]),
    ['View schedule', /member schedule/], ['Membership offers', /membership card options/], ['View cards', /membership card options/],
    ...PACKAGES.map((pkg) => [`View options for ${pkg.name}`, /review package options/]),
  ] as [string, RegExp][])('gates %s on Home with the shared contextual dialog', async (name, description) => {
    const { calls } = installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    const trigger = await screen.findByRole('link', { name })
    await user.click(trigger)
    const dialog = screen.getByRole('dialog', { name: 'Sign in to continue' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAccessibleDescription(description)
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/$/)
    expect(within(dialog).getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login')
    expect(within(dialog).getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/register')
    expect(calls.every((call) => call.method === 'GET' && call.authorization === null)).toBe(true)
    await user.click(within(dialog).getByRole('button', { name: 'Continue browsing' }))
    expect(trigger).toHaveFocus()
  })

  it.each(['Continue browsing', 'Close dialog', 'Escape', 'backdrop'])('dismisses with %s while preserving filters, focus and scroll', async (method) => {
    installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    await filters(user)
    Object.defineProperty(window, 'scrollY', { value: 820, configurable: true })
    const trigger = screen.getByRole('button', { name: 'Find classes' })
    await user.click(trigger)
    const dialog = screen.getByRole('dialog', { name: 'Sign in to continue' })
    expect(document.body.style.overflow).toBe('hidden')
    await user.click(within(dialog).getByText(/current classes/))
    expect(dialog).toBeInTheDocument()
    if (method === 'Escape') await user.keyboard('{Escape}')
    else if (method === 'backdrop') fireEvent.mouseDown(dialog.parentElement!)
    else await user.click(within(dialog).getByRole('button', { name: method }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expectFilters()
    expect(window.scrollY).toBe(820)
    expect(document.body.style.overflow).not.toBe('hidden')
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
  })

  it('contains keyboard focus in both directions and restores the keyboard opener', async () => {
    installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    const trigger = screen.getByRole('button', { name: 'Find classes' })
    trigger.focus()
    await user.keyboard('{Enter}')
    const dialog = screen.getByRole('dialog', { name: 'Sign in to continue' })
    const close = within(dialog).getByRole('button', { name: 'Close dialog' })
    expect(close).toHaveFocus()
    await user.tab({ shift: true })
    expect(within(dialog).getByRole('button', { name: 'Continue browsing' })).toHaveFocus()
    await user.tab()
    expect(close).toHaveFocus()
    await user.tab()
    expect(within(dialog).getByRole('link', { name: 'Log in' })).toHaveFocus()
    within(screen.getByRole('banner')).getByRole('link', { name: 'Log in' }).focus()
    expect(close).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(trigger).toHaveFocus()
  })

  it.each([['Log in', '/login', 'Welcome back'], ['Create account', '/register', 'Create your account']])('hands %s a safe filtered intent and a distinct usable form', async (action, route, title) => {
    installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    await filters(user)
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: action }))
    await screen.findByRole('heading', { name: title })
    expect(screen.getByLabelText('Current location')).toHaveTextContent(route)
    expect(JSON.parse(screen.getByLabelText('Route state').textContent!)).toEqual({ from: TARGET })
    expect(document.body.style.overflow).not.toBe('hidden')
  })

  it('retains all filters through a failed login and successful Member retry without booking', async () => {
    let attempts = 0
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/login') return ++attempts === 1 ? backendError(401, 'Invalid email or password') : json(200, { accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      return catalogue(call)
    })
    const user = userEvent.setup()
    app()
    await filters(user)
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Log in' }))
    await credentials(user)
    await screen.findByText('Invalid email or password. Please try again.')
    expect(JSON.parse(screen.getByLabelText('Route state').textContent!)).toEqual({ from: TARGET })
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Find Classes' })
    expect(screen.getByLabelText('Current location')).toHaveTextContent(TARGET)
    await waitFor(() => expect(calls.some((call) => call.path.startsWith('/sessions?') && ['sportId=41', 'level=BEGINNER', 'format=COACH_LED'].every((filter) => call.path.includes(filter)))).toBe(true))
    expect(calls.filter((call) => call.method !== 'GET').map((call) => call.path)).toEqual(['/auth/login', '/auth/login'])
  })

  it('restores public finder choices when authentication is canceled with browser Back', async () => {
    installFakeFetch(catalogue)
    const user = userEvent.setup()
    app()
    await filters(user)
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    await user.click(screen.getByRole('button', { name: 'Test browser Back' }))
    await screen.findByRole('heading', { name: /Find your sport/ })
    expectFilters()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('preserves the selected package across both form cross-links and successful signup without purchasing', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/register') return json(200, { accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      return catalogue(call)
    })
    const user = userEvent.setup()
    app()
    await user.click(await screen.findByRole('link', { name: 'View options for Football Independent' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Create account' }))
    await user.click(screen.getByRole('link', { name: 'Log in' }))
    await user.click(screen.getByRole('link', { name: 'Create an account' }))
    expect(JSON.parse(screen.getByLabelText('Route state').textContent!)).toEqual({ from: '/member/packages/81' })
    await registration(user)
    await waitFor(() => expect(screen.getByLabelText('Current location')).toHaveTextContent('/member/packages/81'))
    await screen.findByRole('button', { name: /Confirm registration/ })
    expect(screen.queryByRole('heading', { name: 'We could not load the package' })).not.toBeInTheDocument()
    expect(calls.filter((call) => call.method !== 'GET').map((call) => call.path)).toEqual(['/auth/register'])
  })

  it('keeps safe intent and creation notice when signup succeeds but automatic profile loading fails', async () => {
    installFakeFetch((call) => {
      if (call.path === '/auth/register') return json(200, { accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
      if (call.path === '/users/me') return backendError(503, 'Unavailable')
      return catalogue(call)
    })
    const user = userEvent.setup()
    app('/register', { from: TARGET })
    await registration(user)
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(screen.getByText('Your account was created, but we could not sign you in. Please log in.')).toBeInTheDocument()
    expect(screen.getByLabelText(/email address/i)).toHaveValue('fixture@example.invalid')
    await waitFor(() => expect(JSON.parse(screen.getByLabelText('Route state').textContent!)).toEqual({ from: TARGET }))
  })

  it('keeps direct protected deep links on the normal guard and resumes their query', async () => {
    installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, { accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
      if (call.path === '/users/me') return json(200, MEMBER)
      return catalogue(call)
    })
    const user = userEvent.setup()
    app(TARGET)
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await credentials(user)
    await screen.findByRole('heading', { name: 'Find Classes' })
    expect(screen.getByLabelText('Current location')).toHaveTextContent(TARGET)
  })

  it.each(['/login', '/register'])('rejects unsafe return state on %s and while switching forms', async (route) => {
    installFakeFetch(catalogue)
    const user = userEvent.setup()
    app(route, { from: '//evil.invalid/member/classes' })
    const crossLink = screen.getByRole('link', { name: route === '/login' ? 'Create an account' : 'Log in' })
    await user.click(crossLink)
    expect(screen.getByLabelText('Route state')).toHaveTextContent('null')
  })

  it.each(['MANAGER', 'RECEPTIONIST', 'COACH'] as const)('retains the %s Member-only notice without protected browse requests', async (role) => {
    tokenStorage.startSession({ accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? json(200, { ...MEMBER, role }) : catalogue(call))
    const user = userEvent.setup()
    app()
    await screen.findByRole('link', { name: 'My workspace' })
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    const dialog = screen.getByRole('dialog', { name: 'Member booking and purchases' })
    expect(within(dialog).getByRole('link', { name: 'Open my workspace' })).toHaveAttribute('href', { MANAGER: '/manager', RECEPTIONIST: '/reception', COACH: '/coach' }[role])
    expect(screen.queryByRole('dialog', { name: 'Sign in to continue' })).not.toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/sessions'))).toBe(false)
  })

  it('waits during restoration and lets the restored Member navigate without a guest prompt', async () => {
    tokenStorage.startSession({ accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
    let resolveProfile: (value: Response) => void = () => undefined
    installFakeFetch((call) => call.path === '/users/me' ? new Promise<Response>((resolve) => { resolveProfile = resolve }) : catalogue(call))
    const user = userEvent.setup()
    app()
    expect(screen.getByRole('button', { name: 'Find classes' })).toBeDisabled()
    await user.click(screen.getByRole('link', { name: 'View cards' }))
    expect(screen.getByLabelText('Current location')).toHaveTextContent(/^\/$/)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await act(async () => resolveProfile(json(200, MEMBER)))
    await screen.findByRole('link', { name: 'My workspace' })
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    await screen.findByRole('heading', { name: 'Find Classes' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('uses retained-session recovery instead of prompting an errored session to create an account', async () => {
    tokenStorage.startSession({ accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
    installFakeFetch((call) => call.path === '/users/me' ? backendError(503, 'Unavailable') : catalogue(call))
    const user = userEvent.setup()
    app()
    await screen.findByRole('link', { name: 'Recover session' })
    await user.click(screen.getByRole('link', { name: 'View cards' }))
    await screen.findByRole('heading', { name: 'We could not load your account' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(true)
  })

  it('clears an open guest prompt when another tab signs in and does not reopen it after sign-out', async () => {
    installFakeFetch((call) => call.path === '/users/me' ? json(200, MEMBER) : catalogue(call))
    const user = userEvent.setup()
    app()
    await user.click(screen.getByRole('button', { name: 'Find classes' }))
    expect(screen.getByRole('dialog', { name: 'Sign in to continue' })).toBeInTheDocument()
    act(() => {
      localStorage.setItem('sportify.accessToken', 'fixture-access')
      localStorage.setItem('sportify.refreshToken', 'fixture-refresh')
      localStorage.setItem('sportify.sessionId', 'fixture-other-tab')
      window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: null, newValue: 'fixture-other-tab' }))
    })
    await screen.findByRole('link', { name: 'My workspace' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    act(() => {
      localStorage.clear()
      window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: 'fixture-other-tab', newValue: null }))
    })
    await screen.findByRole('link', { name: 'Log in' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
