import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { AuthProvider } from '../../auth/AuthProvider'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'

/*
 * UI-05: guest authentication return navigation, re-verified after the Home, coach profile and
 * shared UI merges. Every Home action that needs a Member session must bring the guest back to
 * exactly that destination after Log in or Create account, and nothing else may change it.
 */

const SPORTS = ['Football', 'Badminton', 'Basketball', 'Volleyball', 'Swimming', 'Tennis'].map((name, index) => ({ id: 41 + index, name, code: name.toUpperCase() }))
const PACKAGES = [
  { id: 81, code: 'FT-SELF', name: 'Football Independent', sportId: 41, sportName: 'Football', trainingFormat: 'SELF_TRAINING', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 500000, isActive: true },
  { id: 82, code: 'FT-COACH', name: 'Football Coached', sportId: 41, sportName: 'Football', trainingFormat: 'COACH_LED', durationDays: 30, sessionCount: 8, sessionMinutes: 60, priceAmount: 900000, isActive: true },
  { id: 83, code: 'FT-SINGLE', name: 'Football Day Visit', sportId: 41, sportName: 'Football', trainingFormat: 'COACH_LED', durationDays: 1, sessionCount: 1, sessionMinutes: 60, priceAmount: 150000, isActive: true },
]
const TOKENS = { accessToken: 'fixture-access', refreshToken: 'fixture-refresh' }

function catalogue(call: RecordedCall): Response {
  if (call.path === '/sports') return json(200, SPORTS)
  if (call.path === '/packages') return json(200, PACKAGES)
  if (/^\/packages\/\d+$/.test(call.path)) return json(200, PACKAGES.find((pkg) => `/packages/${pkg.id}` === call.path) ?? PACKAGES[0])
  if (call.path.startsWith('/coaches/public')) return json(200, [{ id: 21, fullName: 'Public Coach', headline: 'Football coach', specialties: 'Passing', yearsExperience: 3, sportIds: [41] }])
  if (call.path === '/facilities/public') return json(200, [{ id: 63, name: 'Football Field', facilityType: 'FIELD', sportId: 41, capacity: 22 }])
  if (call.path.startsWith('/membership-cards/')) return json(200, [])
  if (call.path.startsWith('/sessions')) {
    const size = Number(new URLSearchParams(call.path.split('?')[1] ?? '').get('size') ?? 20) || 20
    return json(200, { content: [], page: 0, size, totalElements: 0, totalPages: 0 })
  }
  if (call.path.startsWith('/packages/registrations/my')) return json(200, [])
  if (call.path.startsWith('/bookings/my')) return json(200, [])
  if (call.path.startsWith('/payments/my')) return json(200, { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  return backendError(404, 'Not found')
}

function LocationProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return <>
    <output aria-label="Current location">{location.pathname + location.search}</output>
    <button onClick={() => navigate(-1)}>Test browser Back</button>
  </>
}

function app(path = '/', state?: unknown) {
  const [pathname, search] = path.split('?')
  return render(<MemoryRouter initialEntries={[{ pathname, search: search ? `?${search}` : '', state }]}>
    <ToastProvider><AuthProvider><AppRoutes /><LocationProbe /></AuthProvider></ToastProvider>
  </MemoryRouter>)
}

async function logIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email address/i), 'fixture@example.invalid')
  await user.type(screen.getByLabelText(/^password/i), 'Sportify123!')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

async function signUp(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/full name/i), 'Fixture Member')
  await user.type(screen.getByLabelText(/phone number/i), '0901234567')
  await user.type(screen.getByLabelText(/email address/i), 'fixture@example.invalid')
  await user.type(screen.getByLabelText(/^password/i), 'Sportify123!')
  await user.type(screen.getByLabelText(/confirm password/i), 'Sportify123!')
  await user.click(screen.getByRole('checkbox', { name: /terms of use/i }))
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

function memberBackend(extra?: (call: RecordedCall) => Response | undefined) {
  return installFakeFetch((call) => {
    const answer = extra?.(call)
    if (answer) return answer
    if (call.path === '/auth/login' || call.path === '/auth/register') return json(200, TOKENS)
    if (call.path === '/users/me') return json(200, MEMBER)
    return catalogue(call)
  })
}

const location = () => screen.getByLabelText('Current location').textContent

beforeEach(() => tokenStorage.clear())
afterEach(() => {
  vi.unstubAllGlobals()
  tokenStorage.clear()
})

/** Home actions that need a Member session, with the destination each one must resume. */
const HOME_ACTIONS: { name: string; link: RegExp | string; nth?: number; destination: string }[] = [
  { name: 'Membership offers in the hero strip', link: 'Membership offers', destination: '/member/membership/cards' },
  { name: 'View schedule in the sports section', link: 'View schedule', destination: '/member/schedule' },
  { name: 'View options on a package card', link: 'View options for Football Independent', destination: '/member/packages/81' },
  { name: 'View cards in the membership band', link: 'View cards', destination: '/member/membership/cards' },
]

describe('UI-05 guest return navigation matrix', () => {
  it.each(HOME_ACTIONS)('$name returns to $destination after Log in', async ({ link, destination }) => {
    const { calls } = memberBackend()
    const user = userEvent.setup()
    app()
    await user.click(await screen.findByRole('link', { name: link }))
    const dialog = screen.getByRole('dialog', { name: 'Sign in to continue' })
    await user.click(within(dialog).getByRole('link', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    await logIn(user)
    await waitFor(() => expect(location()).toBe(destination))
    // Authentication only resumes navigation: no registration, purchase or booking is sent.
    expect(calls.filter((call) => call.method !== 'GET').map((call) => call.path)).toEqual(['/auth/login'])
  })

  it.each(HOME_ACTIONS)('$name returns to $destination after Create account', async ({ link, destination }) => {
    const { calls } = memberBackend()
    const user = userEvent.setup()
    app()
    await user.click(await screen.findByRole('link', { name: link }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Create account' }))
    await screen.findByRole('heading', { name: 'Create your account' })
    await signUp(user)
    await waitFor(() => expect(location()).toBe(destination))
    expect(calls.filter((call) => call.method !== 'GET').map((call) => call.path)).toEqual(['/auth/register'])
  })

  it('does not show the login form again on browser Back after a successful return', async () => {
    memberBackend()
    const user = userEvent.setup()
    app()
    await user.click(await screen.findByRole('link', { name: 'View schedule' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Log in' }))
    await logIn(user)
    await waitFor(() => expect(location()).toBe('/member/schedule'))
    await user.click(screen.getByRole('button', { name: 'Test browser Back' }))
    await waitFor(() => expect(location()).toBe('/'))
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
  })

  it.each([
    ['MANAGER', '/manager'],
    ['RECEPTIONIST', '/reception'],
    ['COACH', '/coach'],
  ] as const)('sends a %s who logs in from a Member destination to the staff home instead', async (role, home) => {
    installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, TOKENS)
      if (call.path === '/users/me') return json(200, { ...MEMBER, role })
      return catalogue(call)
    })
    const user = userEvent.setup()
    app('/login', { from: '/member/packages/81' })
    await logIn(user)
    await waitFor(() => expect(location()).toBe(home))
  })

  it.each([
    '//evil.invalid/member/packages',
    'https://evil.invalid/member/packages',
    '/member/%2e%2e/manager',
    '/memberx/packages',
    'member/packages',
  ])('ignores the unsafe return state %s and opens the Member home', async (from) => {
    memberBackend()
    const user = userEvent.setup()
    app('/login', { from })
    await logIn(user)
    await waitFor(() => expect(location()).toBe('/member'))
  })

  it('returns to the same page and query after the session expires and the member logs in again', async () => {
    tokenStorage.startSession(TOKENS, { persistent: true })
    let expired = false
    memberBackend((call) => {
      if (call.path === '/auth/refresh') return backendError(401, 'Refresh token expired')
      if (call.path.startsWith('/payments/my') && !expired) {
        expired = true
        return backendError(401, 'Access token expired')
      }
      return undefined
    })
    const user = userEvent.setup()
    app('/member/payments?status=SUCCESS')
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(location()).toBe('/login')
    await logIn(user)
    await waitFor(() => expect(location()).toBe('/member/payments?status=SUCCESS'))
  })

  it('keeps the return path when a signed-in Member opens Log in with a pending destination', async () => {
    tokenStorage.startSession(TOKENS, { persistent: true })
    memberBackend()
    app('/login', { from: '/member/schedule' })
    await waitFor(() => expect(location()).toBe('/member/schedule'))
  })
})

describe('UI-05 guests are never forced to sign in for public browsing', () => {
  it.each([
    ['/', /Find your sport/],
    ['/credits', /Photo credits/],
    ['/login', 'Welcome back'],
    ['/register', 'Create your account'],
  ] as const)('keeps a guest on %s without a session request or a write', async (path, heading) => {
    const { calls } = installFakeFetch(catalogue)
    app(path)
    await screen.findByRole('heading', { name: heading })
    expect(location()).toBe(path)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls.some((call) => call.path === '/users/me')).toBe(false)
    expect(calls.some((call) => call.authorization !== null)).toBe(false)
    expect(calls.filter((call) => call.method !== 'GET')).toEqual([])
  })
})

describe('UI-05 role guards still pass', () => {
  const AREAS = ['/member', '/reception', '/manager', '/coach'] as const
  const OWN = { MEMBER: '/member', RECEPTIONIST: '/reception', MANAGER: '/manager', COACH: '/coach' } as const

  it.each(AREAS)('sends a guest who opens %s to Log in with that return path', async (area) => {
    const user = userEvent.setup()
    memberBackend()
    app(area)
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(location()).toBe('/login')
    await logIn(user)
    // A Member resumes only inside the Member area; a staff area falls back to the Member home.
    await waitFor(() => expect(location()).toBe('/member'))
  })

  it.each(
    (Object.keys(OWN) as (keyof typeof OWN)[]).flatMap((role) => AREAS.map((area) => [role, area] as const)),
  )('%s opening %s sees its area only when it owns it', async (role, area) => {
    tokenStorage.startSession(TOKENS, { persistent: true })
    installFakeFetch((call) => (call.path === '/users/me' ? json(200, { ...MEMBER, role }) : catalogue(call)))
    app(area)
    if (OWN[role] === area) {
      // The role layout with its Log out action renders only after the profile has loaded.
      await screen.findByRole('button', { name: /log out/i })
      expect(screen.queryByText('Access denied')).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
    } else {
      await screen.findByText('Access denied')
    }
    // A wrong role keeps the requested address and never falls back to the login form.
    expect(location()).toBe(area)
  })
})

