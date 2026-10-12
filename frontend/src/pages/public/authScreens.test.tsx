import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, type Location } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { AuthProvider } from '../../auth/AuthProvider'
import { STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
import { Checkbox } from '../../components/ui/Checkbox'
import { ToastProvider } from '../../components/ui/ToastProvider'
import { AppRoutes } from '../../routes/AppRoutes'
import { backendError, installFakeFetch, json, MANAGER, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

/**
 * FE-01: Log in (F1-05) and Sign up (F1-06) against a stand-in for AuthenticationController.
 * The fake copies the backend contract: 401 "Invalid email or password", 403 "Account is
 * disabled" for a locked or inactive account, 409 "Email is already registered", 400
 * "Validation Error" with a details map, and the {accessToken, refreshToken} pair on success.
 */

interface FakeAccount {
  password: string
  status: string
  profile: typeof MEMBER
}

function authBackend(options: { meFails?: boolean } = {}) {
  const accounts = new Map<string, FakeAccount>([
    ['member@sportify.demo', { password: 'Demo123!', status: 'ACTIVE', profile: MEMBER }],
    ['manager@sportify.com', { password: 'password', status: 'ACTIVE', profile: MANAGER }],
    ['locked@sportify.demo', { password: 'Demo123!', status: 'LOCKED', profile: { ...MEMBER, id: 9 } }],
  ])
  const tokens = new Map<string, typeof MEMBER>()
  let counter = 0
  const issue = (profile: typeof MEMBER) => {
    counter += 1
    tokens.set(`access-${counter}`, profile)
    return { accessToken: `access-${counter}`, refreshToken: `refresh-${counter}` }
  }

  return (call: RecordedCall) => {
    if (call.path === '/auth/login') {
      const { email, password } = call.body as { email: string; password: string }
      const account = accounts.get(email.toLowerCase())
      if (!account || account.password !== password) {
        return backendError(401, 'Invalid email or password')
      }
      if (account.status !== 'ACTIVE') {
        return backendError(403, 'Account is disabled')
      }
      return json(200, issue(account.profile))
    }
    if (call.path === '/auth/register') {
      const body = call.body as { fullName: string; email: string; password: string; phone?: string }
      const email = body.email.toLowerCase()
      if (accounts.has(email)) {
        return backendError(409, 'Email is already registered')
      }
      const profile = { id: 50 + accounts.size, email, fullName: body.fullName, phone: body.phone ?? null, role: 'MEMBER', status: 'ACTIVE' }
      accounts.set(email, { password: body.password, status: 'ACTIVE', profile: profile as typeof MEMBER })
      return json(200, issue(profile as typeof MEMBER))
    }
    if (call.path === '/users/me') {
      if (options.meFails) {
        return backendError(500, 'Internal server error')
      }
      const profile = tokens.get(call.authorization?.replace('Bearer ', '') ?? '')
      return profile ? json(200, profile) : backendError(401, 'Unauthorized')
    }
    return backendError(404, 'Resource not found')
  }
}

/** A response that only arrives when the test calls release(), to look at the form while sending. */
function deferredResponse() {
  let release: (response: Response) => void = () => undefined
  const promise = new Promise<Response>((resolve) => {
    release = resolve
  })
  return { promise, release }
}

async function fillRegisterForm(
  user: ReturnType<typeof userEvent.setup>,
  values: Partial<Record<'fullName' | 'phone' | 'email' | 'password' | 'confirm', string>> & { terms?: boolean } = {},
) {
  const filled = {
    fullName: 'Jamie Tran',
    phone: '0901234599',
    email: 'jamie@sportify.demo',
    password: 'Train2026',
    confirm: values.password ?? 'Train2026',
    terms: true,
    ...values,
  }
  if (filled.fullName) await user.type(await screen.findByLabelText(/full name/i), filled.fullName)
  if (filled.phone) await user.type(screen.getByLabelText(/phone number/i), filled.phone)
  if (filled.email) await user.type(screen.getByLabelText(/email address/i), filled.email)
  if (filled.password) await user.type(screen.getByLabelText(/^password/i), filled.password)
  if (filled.confirm) await user.type(screen.getByLabelText(/confirm password/i), filled.confirm)
  if (filled.terms) await user.click(screen.getByRole('checkbox', { name: /terms of use/i }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('F1-05 Log in', () => {
  it('shows and hides the password with the Show button', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    const password = await screen.findByLabelText(/^password/i)
    await user.type(password, 'Demo123!')
    expect(password).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Hide password' }))
    expect(password).toHaveAttribute('type', 'password')
  })

  it('submits with the Enter key', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(calls[0]).toMatchObject({ method: 'POST', path: '/auth/login', body: { email: 'member@sportify.demo', password: 'Demo123!' } })
  })

  it('disables the form while sending and sends only one request', async () => {
    const pending = deferredResponse()
    const backend = authBackend()
    const { calls } = installFakeFetch((call) => (call.path === '/auth/login' ? pending.promise : backend(call)))
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

    const submit = screen.getByRole('button', { name: /log in/i })
    expect(submit).toBeDisabled()
    expect(submit).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByLabelText(/email address/i)).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Show password' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Remember me' })).toBeDisabled()

    await user.click(submit)
    expect(calls.filter((call) => call.path === '/auth/login')).toHaveLength(1)

    pending.release(json(200, { accessToken: 'access-1', refreshToken: 'refresh-1' }))
    await waitFor(() => expect(calls.some((call) => call.path === '/users/me')).toBe(true))
  })

  it('without Remember me keeps the session for this tab only', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')
    await screen.findByRole('heading', { name: 'Member Dashboard' })

    expect(window.sessionStorage.getItem('sportify.accessToken')).toBe('access-1')
    expect(window.localStorage.getItem('sportify.accessToken')).toBeNull()
  })

  it('with Remember me keeps the session on this device', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!')
    await user.click(screen.getByRole('checkbox', { name: 'Remember me' }))
    await user.click(screen.getByRole('button', { name: /log in/i }))
    await screen.findByRole('heading', { name: 'Member Dashboard' })

    expect(window.localStorage.getItem('sportify.accessToken')).toBe('access-1')
    expect(window.sessionStorage.getItem('sportify.accessToken')).toBeNull()
  })

  it('shows a clear message for wrong credentials and keeps the email', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'wrong-pass{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password. Please try again.')
    expect(screen.getByLabelText(/email address/i)).toHaveValue('member@sportify.demo')
    expect(screen.getByRole('button', { name: /log in/i })).toBeEnabled()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('shows the locked or inactive account message for HTTP 403', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'locked@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This account is locked or inactive. Please contact the front desk to reactivate it.',
    )
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('shows a network message when the server cannot be reached', async () => {
    installFakeFetch(() => {
      throw new TypeError('Failed to fetch')
    })
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
    await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server')
  })

  it('Forgot password explains how to reset without leaving the page', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.click(await screen.findByRole('button', { name: 'Forgot password?' }))

    expect(screen.getByRole('status')).toHaveTextContent('ask the front desk to reset your password')
    expect(calls).toHaveLength(0)
  })

  it('moves the focus to the first invalid field', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo{Enter}')

    await waitFor(() => expect(screen.getByLabelText(/^password/i)).toHaveFocus())
    expect(screen.getByText('Password is required')).toBeInTheDocument()
  })

  it('links to the sign up page', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/login')

    await user.click(await screen.findByRole('link', { name: 'Create an account' }))

    expect(await screen.findByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
  })
})

describe('F1-06 Sign up', () => {
  it('creates the account, logs in and opens the member dashboard', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user, { fullName: '  Jamie Tran ', phone: '0901 234 599', email: ' jamie@sportify.demo ' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
    expect(calls[0]).toMatchObject({
      method: 'POST',
      path: '/auth/register',
      authorization: null,
      body: { fullName: 'Jamie Tran', email: 'jamie@sportify.demo', password: 'Train2026', phone: '0901234599' },
    })
    expect(calls[1]).toMatchObject({ method: 'GET', path: '/users/me', authorization: 'Bearer access-1' })
    expect(screen.getByText('Jamie Tran')).toBeInTheDocument()
    // A new member is remembered on this device.
    expect(window.localStorage.getItem('sportify.accessToken')).toBe('access-1')
  })

  it('submits with the Enter key and disables the form while sending', async () => {
    const pending = deferredResponse()
    const backend = authBackend()
    const { calls } = installFakeFetch((call) => (call.path === '/auth/register' ? pending.promise : backend(call)))
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user, { terms: false })
    await user.click(screen.getByRole('checkbox', { name: /terms of use/i }))
    await user.type(screen.getByLabelText(/confirm password/i), '{Enter}')

    const submit = screen.getByRole('button', { name: /create account/i })
    expect(submit).toBeDisabled()
    expect(submit).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByLabelText(/full name/i)).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: /terms of use/i })).toBeDisabled()
    await user.click(submit)
    expect(calls.filter((call) => call.path === '/auth/register')).toHaveLength(1)

    pending.release(json(200, { accessToken: 'access-1', refreshToken: 'refresh-1' }))
    await waitFor(() => expect(calls.some((call) => call.path === '/users/me')).toBe(true))
  })

  it('shows "Email is already registered" under the email field for a duplicate email', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user, { email: 'member@sportify.demo' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    const email = screen.getByLabelText(/email address/i)
    expect(await screen.findByText('Email is already registered')).toBeInTheDocument()
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription('Email is already registered')
    await waitFor(() => expect(email).toHaveFocus())
    expect(tokenStorage.hasSession()).toBe(false)
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
  })

  it('blocks a weak password with the backend StrongPassword message and sends nothing', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user, { password: 'onlyletters' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByText(STRONG_PASSWORD_MESSAGE)).toBeInTheDocument()
    expect(screen.getByLabelText(/^password/i)).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toHaveLength(0)
  })

  it('shows backend validation details under each field', async () => {
    installFakeFetch(() =>
      backendError(400, 'Validation Error', {
        password: STRONG_PASSWORD_MESSAGE,
        phone: 'Phone must be 8-15 digits with optional leading +',
      }),
    )
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(STRONG_PASSWORD_MESSAGE)).toBeInTheDocument()
    expect(screen.getByLabelText(/phone number/i)).toHaveAccessibleDescription(
      'Phone must be 8-15 digits with optional leading +',
    )
    expect(screen.queryByText('Validation Error')).not.toBeInTheDocument()
  })

  it('updates the password rules and the strength label while typing', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')
    const rule = (id: string) => document.querySelector(`[data-rule="${id}"]`)

    const password = await screen.findByLabelText(/^password/i)
    expect(rule('length')).toHaveAttribute('data-met', 'false')

    await user.type(password, 'abc')
    expect(rule('letter')).toHaveAttribute('data-met', 'true')
    expect(rule('digit')).toHaveAttribute('data-met', 'false')
    expect(screen.getByText('Password strength: Weak')).toBeInTheDocument()

    await user.type(password, '12345')
    expect(rule('length')).toHaveAttribute('data-met', 'true')
    expect(rule('digit')).toHaveAttribute('data-met', 'true')
    expect(screen.getByText('Meets requirements')).toBeInTheDocument()
    expect(within(rule('length') as HTMLElement).getByText('- met')).toBeInTheDocument()
  })

  it('shows required messages, mismatch and terms errors without calling the backend', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await user.click(await screen.findByRole('button', { name: 'Create account' }))
    for (const message of ['Full name is required', 'Phone is required', 'Email is required', 'Password is required', 'Please confirm your password', 'You must accept the terms to create an account']) {
      expect(screen.getByText(message)).toBeInTheDocument()
    }
    await waitFor(() => expect(screen.getByLabelText(/full name/i)).toHaveFocus())

    await user.type(screen.getByLabelText(/^password/i), 'Train2026')
    await user.type(screen.getByLabelText(/confirm password/i), 'Train2027')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('confirms when the two passwords match', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await user.type(await screen.findByLabelText(/^password/i), 'Train2026')
    await user.type(screen.getByLabelText(/confirm password/i), 'Train2026')

    expect(screen.getByText('Passwords match')).toBeInTheDocument()
  })

  it('rejects an invalid phone number with the backend message', async () => {
    const { calls } = installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user, { phone: '12ab' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByText('Phone must be 8-15 digits with optional leading +')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('sends the user to log in when the account was created but the sign in failed', async () => {
    const { calls } = installFakeFetch(authBackend({ meFails: true }))
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your account was created, but we could not sign you in.')
    expect(screen.getByLabelText(/email address/i)).toHaveValue('jamie@sportify.demo')
    expect(calls.filter((call) => call.path === '/auth/register')).toHaveLength(1)
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('shows a network message above the form when the server cannot be reached', async () => {
    installFakeFetch(() => {
      throw new TypeError('Failed to fetch')
    })
    const user = userEvent.setup()
    renderApp('/register')

    await fillRegisterForm(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(/Cannot reach the server/)).toBeInTheDocument()
    expect(screen.getByLabelText(/full name/i)).toHaveValue('Jamie Tran')
  })

  it('sends a signed in member away from the sign up page', async () => {
    window.localStorage.setItem('sportify.accessToken', 'access-0')
    window.localStorage.setItem('sportify.refreshToken', 'refresh-0')
    installFakeFetch((call) => (call.path === '/users/me' ? json(200, MEMBER) : backendError(404, 'Resource not found')))
    renderApp('/register')

    expect(await screen.findByRole('heading', { name: 'Member Dashboard' })).toBeInTheDocument()
  })

  it('links back to the log in page', async () => {
    installFakeFetch(authBackend())
    const user = userEvent.setup()
    renderApp('/register')

    await user.click(await screen.findByRole('link', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })
})

describe('FE-01 review fixes (round 2)', () => {
  const SIGNED_OUT_ELSEWHERE = 'You were signed out because another account signed in or out in this browser.'

  /** Signs this tab in as a member with Remember me, as if it had logged in earlier. */
  async function rememberedMemberTab() {
    tokenStorage.startSession({ accessToken: 'access-0', refreshToken: 'refresh-0' })
    installFakeFetch((call) => (call.path === '/users/me' ? json(200, MEMBER) : backendError(404, 'Resource not found')))
    renderApp('/member')
    await screen.findByRole('heading', { name: 'Member Dashboard' })
    return tokenStorage.getSessionId()
  }

  describe('R2 one account per browser', () => {
    it('a login without Remember me in another tab signs this tab out with a notice', async () => {
      const oldSession = await rememberedMemberTab()

      // The other tab's startSession({ persistent: false }) clears the shared localStorage.
      window.localStorage.clear()
      act(() => {
        window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: oldSession, newValue: null }))
      })

      expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent(SIGNED_OUT_ELSEWHERE)
      expect(tokenStorage.hasSession()).toBe(false)
    })

    it('a login or logout in another tab also ends the session this tab keeps for itself', async () => {
      tokenStorage.startSession({ accessToken: 'access-0', refreshToken: 'refresh-0' }, { persistent: false })
      installFakeFetch((call) => (call.path === '/users/me' ? json(200, MEMBER) : backendError(404, 'Resource not found')))
      renderApp('/member')
      await screen.findByRole('heading', { name: 'Member Dashboard' })

      act(() => {
        window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: 'x', newValue: null }))
      })

      expect(await screen.findByText(SIGNED_OUT_ELSEWHERE)).toBeInTheDocument()
      expect(window.sessionStorage.getItem('sportify.accessToken')).toBeNull()
    })

    it('a logout in this tab does not show the notice', async () => {
      await rememberedMemberTab()
      const user = userEvent.setup()

      await user.click(screen.getByRole('button', { name: 'Log out' }))

      expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
      expect(screen.queryByText(SIGNED_OUT_ELSEWHERE)).not.toBeInTheDocument()
    })

    it('the notice disappears after the next successful login', async () => {
      const oldSession = await rememberedMemberTab()
      window.localStorage.clear()
      act(() => {
        window.dispatchEvent(new StorageEvent('storage', { key: 'sportify.sessionId', oldValue: oldSession, newValue: null }))
      })
      await screen.findByText(SIGNED_OUT_ELSEWHERE)
      installFakeFetch(authBackend())
      const user = userEvent.setup()

      await user.type(screen.getByLabelText(/email address/i), 'member@sportify.demo')
      await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')
      await screen.findByRole('heading', { name: 'Member Dashboard' })
      await user.click(screen.getByRole('button', { name: 'Log out' }))

      expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
      expect(screen.queryByText(SIGNED_OUT_ELSEWHERE)).not.toBeInTheDocument()
    })
  })

  describe('R3 sample account box', () => {
    it('is hidden by default', async () => {
      installFakeFetch(authBackend())
      renderApp('/login')

      await screen.findByRole('heading', { name: 'Welcome back' })
      expect(screen.queryByText(/sample account/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/manager@sportify\.com/)).not.toBeInTheDocument()
    })

    it('stays hidden when values are set but the flag is not "true"', async () => {
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_EMAIL', 'demo.member@sportify.demo')
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_PASSWORD', 'Demo2026')
      vi.stubEnv('VITE_SHOW_SAMPLE_ACCOUNT', 'yes')
      installFakeFetch(authBackend())
      renderApp('/login')

      await screen.findByRole('heading', { name: 'Welcome back' })
      expect(screen.queryByText(/sample account/i)).not.toBeInTheDocument()
    })

    it('shows the configured account when VITE_SHOW_SAMPLE_ACCOUNT is "true"', async () => {
      vi.stubEnv('VITE_SHOW_SAMPLE_ACCOUNT', 'true')
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_EMAIL', 'demo.member@sportify.demo')
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_PASSWORD', 'Demo2026')
      installFakeFetch(authBackend())
      renderApp('/login')

      expect(await screen.findByText('Sample account: demo.member@sportify.demo - Demo2026')).toBeInTheDocument()
    })

    it('stays hidden when the flag is on but a value is empty', async () => {
      vi.stubEnv('VITE_SHOW_SAMPLE_ACCOUNT', 'true')
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_EMAIL', 'demo.member@sportify.demo')
      vi.stubEnv('VITE_SAMPLE_ACCOUNT_PASSWORD', '')
      installFakeFetch(authBackend())
      renderApp('/login')

      await screen.findByRole('heading', { name: 'Welcome back' })
      expect(screen.queryByText(/sample account/i)).not.toBeInTheDocument()
    })
  })

  describe('R4 Passwords do not match follows both fields', () => {
    async function mismatchShown() {
      installFakeFetch(authBackend())
      const user = userEvent.setup()
      renderApp('/register')
      await user.type(await screen.findByLabelText(/^password/i), 'Train2026')
      await user.type(screen.getByLabelText(/confirm password/i), 'Train2027')
      await user.click(screen.getByRole('button', { name: 'Create account' }))
      expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
      return user
    }

    it('clears the error when the Password field is changed to match', async () => {
      const user = await mismatchShown()
      const password = screen.getByLabelText(/^password/i)

      await user.clear(password)
      await user.type(password, 'Train2027')

      expect(screen.queryByText('Passwords do not match')).not.toBeInTheDocument()
      expect(screen.getByText('Passwords match')).toBeInTheDocument()
      expect(screen.getByLabelText(/confirm password/i)).not.toHaveAttribute('aria-invalid')
    })

    it('clears the error when the Confirm password field is fixed instead', async () => {
      const user = await mismatchShown()
      const confirm = screen.getByLabelText(/confirm password/i)

      await user.clear(confirm)
      await user.type(confirm, 'Train2026')

      expect(screen.queryByText('Passwords do not match')).not.toBeInTheDocument()
      expect(screen.getByText('Passwords match')).toBeInTheDocument()
    })

    it('keeps the error while the two values still differ', async () => {
      const user = await mismatchShown()

      await user.type(screen.getByLabelText(/^password/i), '9')

      expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    })
  })

  describe('R5 backend errors for keys without an input', () => {
    it('Sign up shows them above the form and keeps the matched ones under their field', async () => {
      installFakeFetch(() =>
        backendError(400, 'Validation Error', { role: 'Role is not allowed', phone: 'Phone must be 8-15 digits with optional leading +' }),
      )
      const user = userEvent.setup()
      renderApp('/register')

      await fillRegisterForm(user)
      await user.click(screen.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByText('Role is not allowed')).toHaveClass('notice--danger')
      expect(screen.getByLabelText(/phone number/i)).toHaveAccessibleDescription('Phone must be 8-15 digits with optional leading +')
      expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
    })

    it('Log in shows them above the form', async () => {
      installFakeFetch(() => backendError(400, 'Validation Error', { role: 'Role is not allowed' }))
      const user = userEvent.setup()
      renderApp('/login')

      await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
      await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

      expect(await screen.findByText('Role is not allowed')).toHaveClass('notice--danger')
      expect(screen.getByRole('button', { name: /log in/i })).toBeEnabled()
    })
  })

  describe('R6 only "Email is already registered" goes under Email', () => {
    it('shows another 409 above the form, not under Email', async () => {
      installFakeFetch((call) =>
        call.path === '/auth/register' ? backendError(409, 'The request conflicts with existing data') : backendError(404, 'x'),
      )
      const user = userEvent.setup()
      renderApp('/register')

      await fillRegisterForm(user)
      await user.click(screen.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByText('The request conflicts with existing data')).toHaveClass('notice--danger')
      expect(screen.getByLabelText(/email address/i)).not.toHaveAttribute('aria-invalid')
    })

    it('keeps "Email is already registered" under Email', async () => {
      installFakeFetch(authBackend())
      const user = userEvent.setup()
      renderApp('/register')

      await fillRegisterForm(user, { email: 'member@sportify.demo' })
      await user.click(screen.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByText('Email is already registered')).toHaveClass('field__error')
      expect(screen.queryByRole('alert', { name: /conflicts/ })).not.toBeInTheDocument()
    })
  })

  describe('R8 focus after a server error', () => {
    it('Log in focuses the field named by a backend validation error', async () => {
      installFakeFetch(() => backendError(400, 'Validation Error', { email: 'Email is invalid' }))
      const user = userEvent.setup()
      renderApp('/login')

      await user.type(await screen.findByLabelText(/email address/i), 'member@sportify.demo')
      await user.type(screen.getByLabelText(/^password/i), 'Demo123!{Enter}')

      await screen.findByText('Email is invalid')
      await waitFor(() => expect(screen.getByLabelText(/email address/i)).toHaveFocus())
    })
  })

  describe('R9 Log in prefill is read once', () => {
    it('removes the email and notice from history so a reload does not show them again', async () => {
      installFakeFetch(authBackend())
      let current: Location | null = null
      function LocationProbe() {
        current = useLocation()
        return null
      }
      const tree = (entry: Partial<Location>) => (
        <MemoryRouter initialEntries={[entry]}>
          <ToastProvider>
            <AuthProvider>
              <AppRoutes />
              <LocationProbe />
            </AuthProvider>
          </ToastProvider>
        </MemoryRouter>
      )
      const notice = 'Your account was created, but we could not sign you in. Please log in.'
      const first = render(tree({ pathname: '/login', state: { email: 'jamie@sportify.demo', notice } }))

      expect(await screen.findByText(notice)).toBeInTheDocument()
      expect(screen.getByLabelText(/email address/i)).toHaveValue('jamie@sportify.demo')
      await waitFor(() => expect((current as Location | null)?.state).toBeNull())

      // Reload: the browser restores the same history entry, now without the prefill.
      const reloaded = (current as Location | null) ?? { pathname: '/login', state: null }
      first.unmount()
      render(tree({ pathname: reloaded.pathname, state: reloaded.state }))

      await screen.findByRole('heading', { name: 'Welcome back' })
      expect(screen.queryByText(notice)).not.toBeInTheDocument()
      expect(screen.getByLabelText(/email address/i)).toHaveValue('')
    })

    it('keeps the requested page in history when it removes the prefill', async () => {
      installFakeFetch(authBackend())
      let current: Location | null = null
      function LocationProbe() {
        current = useLocation()
        return null
      }
      render(
        <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/member/packages', notice: 'Hello' } }]}>
          <ToastProvider>
            <AuthProvider>
              <AppRoutes />
              <LocationProbe />
            </AuthProvider>
          </ToastProvider>
        </MemoryRouter>,
      )

      await screen.findByText('Hello')
      await waitFor(() => expect((current as Location | null)?.state).toEqual({ from: '/member/packages' }))
    })
  })

  describe('R11 accessible descriptions', () => {
    it('links the password field to the rules list through a generated id', async () => {
      installFakeFetch(authBackend())
      renderApp('/register')

      const password = await screen.findByLabelText(/^password/i)
      const rulesId = password.getAttribute('aria-describedby') ?? ''
      expect(rulesId).not.toBe('password-rules')
      const rules = document.getElementById(rulesId)
      expect(rules).toHaveClass('password-rules')
    })

    it('Checkbox keeps the caller describedby together with its error id', () => {
      render(
        <>
          <p id="terms-note">Read the terms first.</p>
          <Checkbox label="I agree" aria-describedby="terms-note" error="You must accept the terms" />
        </>,
      )

      const box = screen.getByRole('checkbox', { name: 'I agree' })
      const ids = (box.getAttribute('aria-describedby') ?? '').split(' ')
      expect(ids).toContain('terms-note')
      expect(ids).toHaveLength(2)
      expect(box).toHaveAccessibleDescription('You must accept the terms Read the terms first.')
    })
  })
})
