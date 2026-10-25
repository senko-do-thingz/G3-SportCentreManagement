import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, StaleSessionError } from '../api/errors'
import { tokenStorage } from '../api/tokenStorage'
import type { Role } from '../api/types'
import { backendError, installFakeFetch, json, MANAGER } from '../test/fakeBackend'
import { renderApp } from '../test/renderApp'
import { AuthProvider } from './AuthProvider'
import { type AuthContextValue, LoginProfileError } from './authContext'
import { useAuth } from './useAuth'

const PAIR = { accessToken: 'test-access', refreshToken: 'test-refresh' }

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

async function submitLogin(remember = false) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText(/email address/i), 'person@example.test')
  await user.type(screen.getByLabelText(/^password/i), 'test-input')
  if (remember) {
    await user.click(screen.getByRole('checkbox', { name: 'Keep me logged in' }))
  }
  await user.click(screen.getByRole('button', { name: 'Log in' }))
  return user
}

afterEach(() => vi.unstubAllGlobals())

describe('FE-00 login integration', () => {
  it.each<[Role, string]>([
    ['MEMBER', 'Member Dashboard'],
    ['RECEPTIONIST', 'Member Search and Profile'],
    ['MANAGER', 'User Management'],
    ['COACH', 'My Teaching Schedule'],
  ])('loads a Bearer-authenticated profile and the %s layout', async (role, heading) => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, PAIR)
      if (call.path === '/users/me') return json(200, { ...MANAGER, role })
      return backendError(404, 'Not found')
    })
    renderApp('/login')
    await submitLogin()
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(calls[0]).toMatchObject({ method: 'POST', path: '/auth/login', authorization: null })
    expect(calls[1]).toMatchObject({ method: 'GET', path: '/users/me', authorization: 'Bearer test-access' })
    expect(tokenStorage.hasSession()).toBe(true)
  })

  it('reserves the credential message for a rejected login request', async () => {
    const { calls } = installFakeFetch(() => backendError(401, 'Unauthorized'))
    renderApp('/login')
    await submitLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password')
    expect(calls).toHaveLength(1)
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('reports a session change during login without calling it a network failure', async () => {
    const pending = deferred<Response>()
    installFakeFetch(() => pending.promise)
    renderApp('/login')
    await submitLogin()
    act(() => tokenStorage.markExternalSessionChange())
    await act(async () => pending.resolve(json(200, PAIR)))
    expect(await screen.findByRole('alert')).toHaveTextContent('The signed in account changed')
    expect(screen.getByRole('alert')).not.toHaveTextContent('Cannot reach the server')
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it.each([
    [401, 'Your session has expired'],
    [403, 'access to your account profile was denied'],
  ])('does not label a profile %s as bad credentials or a locked account', async (status, message) => {
    installFakeFetch((call) => call.path === '/auth/login'
      ? json(200, PAIR) : backendError(status as number, 'Internal diagnostic'))
    renderApp('/login')
    await submitLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText(/Incorrect email or password|locked or inactive|Internal diagnostic/)).not.toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it.each([0, 500, 503, 429])('keeps a recoverable session after a profile failure (%s)', async (status) => {
    let healthy = false
    installFakeFetch((call) => {
      if (call.path === '/auth/login') return json(200, PAIR)
      if (healthy) return json(200, MANAGER)
      if (status === 0) throw new TypeError('Private network diagnostic')
      return backendError(status, 'Private server diagnostic')
    })
    renderApp('/manager/finance')
    const user = await submitLogin(true)
    expect(await screen.findByRole('heading', { name: 'We could not load your account' })).toBeInTheDocument()
    expect(screen.getByText(/Your credentials were accepted/)).toBeInTheDocument()
    expect(screen.queryByText(/Private .* diagnostic|Incorrect email or password/)).not.toBeInTheDocument()
    expect(tokenStorage.getAccessToken()).toBe(PAIR.accessToken)
    expect(window.localStorage.getItem(tokenStorage.refreshKey)).toBe(PAIR.refreshToken)
    healthy = true
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('heading', { name: 'Financial Overview' })).toBeInTheDocument()
  })

  it('does not expose backend details or log credential material on a login service failure', async () => {
    const spies = [vi.spyOn(console, 'log'), vi.spyOn(console, 'warn'), vi.spyOn(console, 'error')]
    installFakeFetch(() => backendError(500, 'test-input test-access test-refresh private stack'))
    renderApp('/login')
    await submitLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent('The login service is unavailable')
    expect(screen.getByRole('alert')).not.toHaveTextContent(/test-input|test-access|test-refresh|private stack/)
    spies.forEach((spy) => expect(JSON.stringify(spy.mock.calls)).not.toMatch(/test-input|test-access|test-refresh/))
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('preserves status and field errors in a typed profile failure', () => {
    const failure = new LoginProfileError(new ApiError(403, 'Private detail', { profile: 'Unavailable' }))
    expect(failure).toBeInstanceOf(ApiError)
    expect(failure.status).toBe(403)
    expect(failure.fieldErrors).toEqual({ profile: 'Unavailable' })
    expect(failure.message).not.toContain('Private detail')
  })

  it.each([null, {}, { ...MANAGER, id: '1' }])('rejects an invalid profile without authenticating (%j)', async (body) => {
    installFakeFetch((call) => call.path === '/auth/login' ? json(200, PAIR) : json(200, body))
    renderApp('/login')
    await submitLogin()
    expect(await screen.findByRole('heading', { name: 'We could not load your account' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary', { name: /manager workspace/i })).not.toBeInTheDocument()
    expect(tokenStorage.hasSession()).toBe(true)
  })

  it('keeps retry diagnostics safe when the profile service fails again', async () => {
    installFakeFetch((call) => call.path === '/auth/login'
      ? json(200, PAIR) : backendError(503, 'Private retry diagnostic'))
    renderApp('/login')
    const user = await submitLogin()
    await user.click(await screen.findByRole('button', { name: 'Retry' }))
    expect(await screen.findByText(/Your credentials were accepted/)).toBeInTheDocument()
    expect(screen.queryByText('Private retry diagnostic')).not.toBeInTheDocument()
  })

  it.each([null, {}, { accessToken: 123, refreshToken: 'test-refresh' }, { accessToken: ' ', refreshToken: 'test-refresh' }])(
    'rejects malformed authentication JSON before storing a session (%j)', async (body) => {
      const { calls } = installFakeFetch(() => json(200, body))
      renderApp('/login')
      await submitLogin()
      expect(await screen.findByRole('alert')).toHaveTextContent('The login service is unavailable')
      expect(calls).toHaveLength(1)
      expect(tokenStorage.hasSession()).toBe(false)
    },
  )
})

describe('FE-00 session ownership', () => {
  function harness() {
    let auth!: AuthContextValue
    function Probe() {
      auth = useAuth()
      return <p>{auth.status}:{auth.user?.id}</p>
    }
    render(<AuthProvider><Probe /></AuthProvider>)
    return () => auth
  }

  it('does not restore a login completed after logout', async () => {
    const pending = deferred<Response>()
    installFakeFetch(() => pending.promise)
    const current = harness()
    let login!: Promise<unknown>
    act(() => { login = current().login({ email: 'person@example.test', password: 'test-input' }).catch((error: unknown) => error) })
    act(() => current().logout())
    await act(async () => { pending.resolve(json(200, PAIR)); await login })
    expect(await login).toBeInstanceOf(StaleSessionError)
    expect(current().status).toBe('anonymous')
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it.each([200, 503])('a late profile response (%s) cannot publish or clear a newer session', async (status) => {
    const pending = deferred<Response>()
    const { calls } = installFakeFetch((call) => call.path === '/auth/login' ? json(200, PAIR) : pending.promise)
    const current = harness()
    let login!: Promise<unknown>
    act(() => { login = current().login({ email: 'person@example.test', password: 'test-input' }).catch((error: unknown) => error) })
    await waitFor(() => expect(calls.some((call) => call.path === '/users/me')).toBe(true))
    act(() => {
      current().logout()
      tokenStorage.startSession({ accessToken: 'new-account', refreshToken: 'new-refresh' })
    })
    await act(async () => { pending.resolve(json(status, MANAGER)); await login })
    expect(await login).toBeInstanceOf(StaleSessionError)
    expect(tokenStorage.getAccessToken()).toBe('new-account')
    expect(current().user).toBeNull()
  })
})
