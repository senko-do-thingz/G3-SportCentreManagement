import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { STRONG_PASSWORD_MESSAGE } from '../../auth/passwordRules'
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
    expect(tokenStorage.isPersistent()).toBe(false)
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
    expect(tokenStorage.isPersistent()).toBe(true)
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
    expect(tokenStorage.isPersistent()).toBe(true)
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
    expect(screen.getByText('Password strength: Strong')).toBeInTheDocument()
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
