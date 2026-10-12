import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../api/tokenStorage'
import { backendError, installFakeFetch, json, MANAGER, MEMBER, type RecordedCall } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'

type Account = typeof MANAGER

/** Minimal stand-in for AuthenticationController and GET /users/me. */
function backendWith(accounts: Record<string, { password: string; profile: Account }>) {
  const tokens = new Map<string, Account>()
  let counter = 0
  const issue = (profile: Account) => {
    counter += 1
    const accessToken = `access-${counter}`
    tokens.set(accessToken, profile)
    return { accessToken, refreshToken: `refresh-${profile.id}-${counter}` }
  }
  return (call: RecordedCall) => {
    if (call.path === '/auth/login') {
      const { email, password } = call.body as { email: string; password: string }
      const account = accounts[email]
      return account && account.password === password
        ? json(200, issue(account.profile))
        : backendError(401, 'Invalid email or password')
    }
    if (call.path === '/users/me') {
      const token = call.authorization?.replace('Bearer ', '') ?? ''
      const profile = tokens.get(token)
      return profile ? json(200, profile) : backendError(401, 'Unauthorized')
    }
    return backendError(404, 'Resource not found')
  }
}

const ACCOUNTS = {
  'manager@sportify.com': { password: 'password', profile: MANAGER },
  'member@sportify.demo': { password: 'Demo123!', profile: MEMBER },
}

async function logIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText(/email address/i), email)
  await user.type(screen.getByLabelText(/^password/i), password)
  await user.click(screen.getByRole('button', { name: /log in/i }))
  return user
}

describe('auth flow', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('manager login lands on the staff layout', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/login')

    await logIn('manager@sportify.com', 'password')

    expect(await screen.findByRole('heading', { name: 'User Management' })).toBeInTheDocument()
    const sidebar = screen.getByRole('complementary', { name: /manager workspace/i })
    expect(within(sidebar).getByRole('link', { name: 'Users' })).toHaveAttribute('aria-current', 'page')
    expect(within(sidebar).getByRole('link', { name: 'Revenue reports' })).toBeInTheDocument()
    expect(screen.getByText('Morgan Lee')).toBeInTheDocument()
    expect(tokenStorage.getAccessToken()).toBe('access-1')
  })

  it('member login lands on the member layout', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/login')

    await logIn('member@sportify.demo', 'Demo123!')

    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: /member navigation/i })
    expect(within(nav).getByRole('link', { name: 'Sport Packages' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
  })

  it('shows the backend message when the password is wrong', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/login')

    await logIn('manager@sportify.com', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('a member opening a manager page sees the 403 page', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/login')
    await logIn('member@sportify.demo', 'Demo123!')
    await screen.findByRole('heading', { name: 'Member Dashboard' })

    renderApp('/manager/users')

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
    expect(screen.getByText(/not available for the Member role/i)).toBeInTheDocument()
  })

  it('a manager opening a member page sees the 403 page', async () => {
    tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    installFakeFetch((call) => (call.path === '/users/me' ? json(200, MANAGER) : backendError(404, 'x')))

    renderApp('/member/packages')

    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
  })

  it('sends anonymous users to login and back to the requested page after login', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/member/packages')

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    await logIn('member@sportify.demo', 'Demo123!')

    expect(await screen.findByRole('heading', { name: 'Sport Packages' })).toBeInTheDocument()
  })

  it('ignores a requested page that belongs to another role', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/manager/finance')

    await logIn('member@sportify.demo', 'Demo123!')

    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
  })

  it('restores the session after reload and refreshes an expired access token silently', async () => {
    tokenStorage.startSession({ accessToken: 'expired', refreshToken: 'refresh-ok' })
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/refresh') {
        return json(200, { accessToken: 'fresh', refreshToken: 'refresh-new' })
      }
      if (call.path === '/users/me') {
        return call.authorization === 'Bearer fresh' ? json(200, MANAGER) : backendError(401, 'Unauthorized')
      }
      return backendError(404, 'x')
    })

    renderApp('/manager')

    expect(await screen.findByRole('heading', { name: 'User Management' })).toBeInTheDocument()
    expect(calls.map((call) => call.path)).toEqual(['/users/me', '/auth/refresh', '/users/me'])
    expect(tokenStorage.getRefreshToken()).toBe('refresh-new')
  })

  it('logs out when the refresh token is expired', async () => {
    tokenStorage.startSession({ accessToken: 'expired', refreshToken: 'expired-refresh' })
    installFakeFetch((call) =>
      call.path === '/auth/refresh'
        ? backendError(401, 'Invalid or expired token')
        : backendError(401, 'Unauthorized'),
    )

    renderApp('/manager')

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('Log out clears the tokens and returns to the login page', async () => {
    installFakeFetch(backendWith(ACCOUNTS))
    renderApp('/login')
    const user = await logIn('manager@sportify.com', 'password')
    await screen.findByRole('heading', { name: 'User Management' })

    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('shows the 404 page for an unknown address', async () => {
    renderApp('/does-not-exist')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('redirects / to the login page for anonymous users', async () => {
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })
  it('keeps the tokens and shows Retry when the backend is unreachable on reload', async () => {
    tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))

    renderApp('/manager')

    expect(await screen.findByRole('heading', { name: 'We could not load your account' })).toBeInTheDocument()
    expect(screen.getByText(/cannot reach the server/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
    expect(tokenStorage.getAccessToken()).toBe('access-1')
    expect(tokenStorage.getRefreshToken()).toBe('refresh-1')
  })

  it('Retry restores the session once the backend is back', async () => {
    tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    let online = false
    installFakeFetch((call) => {
      if (!online) {
        return backendError(503, 'Service unavailable')
      }
      return call.path === '/users/me' ? json(200, MANAGER) : backendError(404, 'x')
    })
    const user = userEvent.setup()
    renderApp('/manager/finance')

    await screen.findByRole('heading', { name: 'We could not load your account' })
    expect(screen.getByText('Service unavailable')).toBeInTheDocument()
    online = true
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('heading', { name: 'Financial Overview' })).toBeInTheDocument()
  })

  it('a server error during the refresh on reload keeps the session and shows Retry', async () => {
    tokenStorage.startSession({ accessToken: 'expired', refreshToken: 'refresh-ok' })
    installFakeFetch((call) =>
      call.path === '/auth/refresh' ? backendError(500, 'Internal server error') : backendError(401, 'Unauthorized'),
    )

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'We could not load your account' })).toBeInTheDocument()
    expect(tokenStorage.getRefreshToken()).toBe('refresh-ok')
  })

  it('a rejected refresh on reload still goes to the login page', async () => {
    tokenStorage.startSession({ accessToken: 'expired', refreshToken: 'revoked' })
    installFakeFetch((call) =>
      call.path === '/auth/refresh' ? backendError(404, 'User not found') : backendError(401, 'Unauthorized'),
    )

    renderApp('/member')

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('Log out on the Retry screen ends the session', async () => {
    tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const user = userEvent.setup()
    renderApp('/manager')

    await screen.findByRole('heading', { name: 'We could not load your account' })
    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })
  describe('changes made in another tab (storage events)', () => {
    function fireStorage(key: string, oldValue: string | null, newValue: string | null) {
      act(() => {
        window.dispatchEvent(new StorageEvent('storage', { key, oldValue, newValue }))
      })
    }

    async function signedInAsManager() {
      tokenStorage.startSession({ accessToken: 'manager-access', refreshToken: 'manager-refresh' })
      const profiles: Record<string, typeof MANAGER> = { 'manager-access': MANAGER, 'member-access': MEMBER }
      const delays: Record<string, number> = {}
      const { calls } = installFakeFetch(async (call) => {
        const token = call.authorization?.replace('Bearer ', '') ?? ''
        await new Promise((resolve) => setTimeout(resolve, delays[token] ?? 0))
        return profiles[token] ? json(200, profiles[token]) : backendError(401, 'Unauthorized')
      })
      renderApp('/manager')
      await screen.findByRole('heading', { name: 'User Management' })
      const meCalls = () => calls.filter((call) => call.path === '/users/me').length
      return { meCalls, delays }
    }

    it('ignores token rotation done by a refresh in another tab', async () => {
      const { meCalls } = await signedInAsManager()
      expect(meCalls()).toBe(1)

      window.localStorage.setItem('sportify.accessToken', 'manager-access-2')
      fireStorage('sportify.accessToken', 'manager-access', 'manager-access-2')
      fireStorage('sportify.refreshToken', 'manager-refresh', 'manager-refresh-2')
      await new Promise((resolve) => setTimeout(resolve, 20))

      expect(meCalls()).toBe(1)
      expect(screen.getByRole('heading', { name: 'User Management' })).toBeInTheDocument()
    })

    it('follows a login as another user in another tab', async () => {
      const { meCalls } = await signedInAsManager()
      const oldSession = tokenStorage.getSessionId()

      window.localStorage.setItem('sportify.accessToken', 'member-access')
      window.localStorage.setItem('sportify.refreshToken', 'member-refresh')
      window.localStorage.setItem('sportify.sessionId', 'session-from-other-tab')
      fireStorage('sportify.sessionId', oldSession, 'session-from-other-tab')

      // This tab was on a manager page; the member who is now signed in sees the 403 page.
      expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
      expect(meCalls()).toBe(2)
    })

    it('follows a logout in another tab', async () => {
      await signedInAsManager()
      const oldSession = tokenStorage.getSessionId()

      window.localStorage.clear()
      fireStorage('sportify.sessionId', oldSession, null)

      expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    })

    it('a slow profile answer from an older change cannot overwrite a newer one', async () => {
      const { delays } = await signedInAsManager()
      const oldSession = tokenStorage.getSessionId()

      // First change: a member logs in elsewhere, and its profile answer is slow.
      delays['member-access'] = 60
      window.localStorage.setItem('sportify.accessToken', 'member-access')
      window.localStorage.setItem('sportify.sessionId', 'member-session')
      fireStorage('sportify.sessionId', oldSession, 'member-session')
      // Second change right after: the manager logs in again, fast answer.
      window.localStorage.setItem('sportify.accessToken', 'manager-access')
      window.localStorage.setItem('sportify.sessionId', 'manager-session')
      fireStorage('sportify.sessionId', 'member-session', 'manager-session')

      await new Promise((resolve) => setTimeout(resolve, 100))
      expect(screen.getByRole('heading', { name: 'User Management' })).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Access denied' })).not.toBeInTheDocument()
    })
  })
  describe('log in form checks', () => {
    it('shows required messages and sends no request for an empty form', async () => {
      const { calls } = installFakeFetch(backendWith(ACCOUNTS))
      const user = userEvent.setup()
      renderApp('/login')

      await user.click(await screen.findByRole('button', { name: /log in/i }))

      expect(screen.getByText('Email is required')).toBeInTheDocument()
      expect(screen.getByText('Password is required')).toBeInTheDocument()
      expect(calls).toHaveLength(0)
    })

    it('rejects a malformed email before calling the backend', async () => {
      const { calls } = installFakeFetch(backendWith(ACCOUNTS))
      renderApp('/login')

      await logIn('manager.sportify.com', 'password')

      expect(screen.getByText('Email is invalid')).toBeInTheDocument()
      expect(screen.getByLabelText(/email address/i)).toHaveAttribute('aria-invalid', 'true')
      expect(calls).toHaveLength(0)
    })

    it('clears the message of a field when the user types in it', async () => {
      installFakeFetch(backendWith(ACCOUNTS))
      const user = userEvent.setup()
      renderApp('/login')
      await user.click(await screen.findByRole('button', { name: /log in/i }))

      await user.type(screen.getByLabelText(/email address/i), 'm')

      expect(screen.queryByText('Email is required')).not.toBeInTheDocument()
      expect(screen.getByText('Password is required')).toBeInTheDocument()
    })

    it('shows backend field errors over the client checks', async () => {
      installFakeFetch(() => backendError(400, 'Validation Error', { email: 'Email must be at most 255 characters' }))
      renderApp('/login')

      await logIn('someone@sportify.com', 'secret')

      expect(await screen.findByText('Email must be at most 255 characters')).toBeInTheDocument()
    })
  })
})
