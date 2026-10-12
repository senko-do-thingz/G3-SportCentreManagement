import { screen, within } from '@testing-library/react'
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
})
