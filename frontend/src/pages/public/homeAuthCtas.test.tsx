import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { backendError, installFakeFetch, json, MEMBER, type RecordedCall } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'
import { THEME_STORAGE_KEY } from '../../theme/theme'

const request = { fullName: 'Taylor Test', phone: '0901234567', email: 'new-member@example.invalid', password: 'Sportify123!' }

function catalogue(call: RecordedCall) {
  if (['/sports', '/packages', '/membership-cards/tiers', '/coaches/public', '/facilities/public'].includes(call.path)) return json(200, [])
  return backendError(404, 'Not found')
}

async function fillRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/full name/i), request.fullName)
  await user.type(screen.getByLabelText(/phone number/i), request.phone)
  await user.type(screen.getByLabelText(/email address/i), request.email)
  await user.type(screen.getByLabelText(/^password/i), request.password)
  await user.type(screen.getByLabelText(/confirm password/i), request.password)
  await user.click(screen.getByRole('checkbox', { name: /terms of use/i }))
}

afterEach(() => { vi.unstubAllGlobals() })

describe('Home account navigation', () => {
  it('opens distinct login and guest registration forms and preserves their cross-links', async () => {
    const { calls } = installFakeFetch(catalogue)
    const user = userEvent.setup()
    renderApp('/')
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAccessibleDescription('Log in to your existing account')
    expect(within(screen.getByRole('banner')).getByRole('link', { name: 'Join now' })).toHaveAccessibleDescription('Create a new member account')
    await user.click(screen.getByRole('link', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(document.querySelector('.auth-shell--login')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Sign up' }))
    await screen.findByRole('heading', { name: 'Create your account' })
    expect(document.querySelector('.auth-shell--login')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Log in' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(document.querySelector('.auth-shell--login')).toBeInTheDocument()
    expect(calls.some((call) => call.path.startsWith('/auth/'))).toBe(false)
  })

  it('Join now supports real member-registration contract and preserves appearance after signup and logout', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/register') {
        expect(call.method).toBe('POST')
        expect(call.authorization).toBeNull()
        expect(call.body).toEqual(request)
        return json(200, { accessToken: 'new-member-access', refreshToken: 'new-member-refresh' })
      }
      if (call.path === '/users/me') return call.authorization === 'Bearer new-member-access' ? json(200, MEMBER) : backendError(401, 'Unauthorized')
      return catalogue(call)
    })
    const user = userEvent.setup()
    renderApp('/')
    await user.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: 'Join now' }))
    await screen.findByRole('heading', { name: 'Create your account' })
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    await fillRegistration(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await screen.findByRole('heading', { name: 'Member Dashboard' })
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    expect(calls.filter((call) => call.path === '/auth/register')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Log out' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('keeps registration validation and duplicate-account errors on the actual signup form', async () => {
    const { calls } = installFakeFetch((call) => call.path === '/auth/register' ? backendError(409, 'Email is already registered') : catalogue(call))
    const user = userEvent.setup()
    renderApp('/')
    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: 'Join now' }))
    await screen.findByRole('heading', { name: 'Create your account' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(calls.some((call) => call.path === '/auth/register')).toBe(false)
    expect(screen.getByLabelText(/full name/i)).toHaveAttribute('aria-invalid', 'true')
    await fillRegistration(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await screen.findByText('An account with this email already exists. Log in instead.')
    expect(screen.getByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Welcome back' })).not.toBeInTheDocument()
  })

  it('only moves signup to Login after successful creation followed by a failed profile request', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/register') { expect(call.body).toEqual(request); return json(200, { accessToken: 'created-access', refreshToken: 'created-refresh' }) }
      if (call.path === '/users/me') return backendError(503, 'Profile temporarily unavailable')
      return catalogue(call)
    })
    const user = userEvent.setup()
    renderApp('/')
    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: 'Join now' }))
    await screen.findByRole('heading', { name: 'Create your account' })
    await fillRegistration(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await screen.findByRole('heading', { name: 'Welcome back' })
    expect(screen.getByText('Your account was created, but we could not sign you in. Please log in.')).toBeInTheDocument()
    expect(screen.getByLabelText(/email address/i)).toHaveValue(request.email)
    expect(calls.filter((call) => call.path === '/auth/register')).toHaveLength(1)
    expect(calls.filter((call) => call.path === '/users/me')).toHaveLength(1)
  })

  it('offers session recovery instead of misleading guest signup during a retained-session failure', async () => {
    tokenStorage.startSession({ accessToken: 'saved-access', refreshToken: 'saved-refresh' })
    const { calls } = installFakeFetch((call) => call.path === '/users/me' ? backendError(503, 'Unavailable') : catalogue(call))
    const user = userEvent.setup()
    renderApp('/')
    const recover = await screen.findByRole('link', { name: 'Recover session' })
    expect(screen.queryByRole('link', { name: 'Join now' })).not.toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'))
    expect(calls.filter((call) => call.path === '/users/me')).toHaveLength(1)
    await user.click(recover)
    await screen.findByRole('heading', { name: 'We could not load your account' })
    expect(tokenStorage.hasSession()).toBe(true)
  })
})
