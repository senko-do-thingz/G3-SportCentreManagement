import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { api, buildUrl, onSessionExpired } from './client'
import { ApiError, StaleSessionError } from './errors'
import { tokenStorage } from './tokenStorage'

describe('api client', () => {
  beforeEach(() => {
    tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('builds URLs from VITE_API_BASE_URL and skips empty query values', () => {
    expect(buildUrl('/members', { q: 'taylor', page: 0, role: '', status: undefined, ids: [1, 2] })).toBe(
      'http://api.test/api/v1/members?q=taylor&page=0&ids=1&ids=2',
    )
  })

  it('sends the access token as a Bearer header and parses JSON', async () => {
    const { calls } = installFakeFetch(() => json(200, { id: 7 }))
    await expect(api.get<{ id: number }>('/users/me')).resolves.toEqual({ id: 7 })
    expect(calls[0].authorization).toBe('Bearer access-1')
  })

  it('does not send the access token to /auth endpoints', async () => {
    const { calls } = installFakeFetch(() => json(200, { accessToken: 'a', refreshToken: 'r' }))
    await api.post('/auth/login', { email: 'a@b.c', password: 'x' })
    expect(calls[0].authorization).toBeNull()
  })

  it('refreshes once on 401, retries the request and stores the rotated tokens', async () => {
    const { calls } = installFakeFetch((call) => {
      if (call.path === '/auth/refresh') {
        return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
      }
      return call.authorization === 'Bearer access-2' ? json(200, { ok: true }) : backendError(401, 'Unauthorized')
    })

    await expect(api.get('/users/me')).resolves.toEqual({ ok: true })

    expect(calls.map((call) => call.path)).toEqual(['/users/me', '/auth/refresh', '/users/me'])
    expect(calls[1].body).toEqual({ refreshToken: 'refresh-1' })
    expect(tokenStorage.getAccessToken()).toBe('access-2')
    expect(tokenStorage.getRefreshToken()).toBe('refresh-2')
  })

  it('uses a single refresh call for parallel requests that fail with 401', async () => {
    let refreshCount = 0
    installFakeFetch(async (call) => {
      if (call.path === '/auth/refresh') {
        refreshCount += 1
        await new Promise((resolve) => setTimeout(resolve, 10))
        return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
      }
      return call.authorization === 'Bearer access-2' ? json(200, { path: call.path }) : backendError(401, 'Unauthorized')
    })

    const results = await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')])

    expect(results).toEqual([{ path: '/a' }, { path: '/b' }, { path: '/c' }])
    expect(refreshCount).toBe(1)
  })

  it('logs out when the refresh token is rejected', async () => {
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    installFakeFetch((call) =>
      call.path === '/auth/refresh'
        ? backendError(401, 'Invalid or expired token')
        : backendError(401, 'Unauthorized'),
    )

    const error = await api.get('/users/me').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).message).toMatch(/session has expired/i)
    expect(expired).toHaveBeenCalledTimes(1)
    expect(tokenStorage.getAccessToken()).toBeNull()
    expect(tokenStorage.getRefreshToken()).toBeNull()
    unsubscribe()
  })

  it('retries only once: a second 401 after refresh also ends the session', async () => {
    const { calls } = installFakeFetch((call) =>
      call.path === '/auth/refresh'
        ? json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
        : backendError(401, 'Unauthorized'),
    )

    await expect(api.get('/users/me')).rejects.toMatchObject({ status: 401 })
    expect(calls).toHaveLength(3)
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('does not refresh for a 403 response', async () => {
    const { calls } = installFakeFetch(() => backendError(403, 'Access Denied'))
    await expect(api.get('/manager/users')).rejects.toMatchObject({ status: 403, message: 'Access Denied' })
    expect(calls).toHaveLength(1)
    expect(tokenStorage.getAccessToken()).toBe('access-1')
  })

  it('maps validation details to fieldErrors', async () => {
    installFakeFetch(() =>
      backendError(400, 'Validation Error', { email: 'Email is invalid', password: 'Password is required' }),
    )
    const error = (await api.post('/auth/register', {}).catch((caught: unknown) => caught)) as ApiError
    expect(error.status).toBe(400)
    expect(error.message).toBe('Validation Error')
    expect(error.fieldErrors).toEqual({ email: 'Email is invalid', password: 'Password is required' })
    expect(error.hasFieldErrors).toBe(true)
  })

  it('turns a network failure into an ApiError with status 0 and keeps the tokens', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const error = (await api.get('/users/me').catch((caught: unknown) => caught)) as ApiError
    expect(error.status).toBe(0)
    expect(error.isNetworkError).toBe(true)
    expect(tokenStorage.getAccessToken()).toBe('access-1')
  })

  it('returns undefined for 204 No Content', async () => {
    installFakeFetch(() => new Response(null, { status: 204 }))
    await expect(api.delete('/bookings/1')).resolves.toBeUndefined()
  })
  it('does not bring tokens back when the user logs out while a refresh is running', async () => {
    let releaseRefresh: () => void = () => undefined
    installFakeFetch(async (call) => {
      if (call.path === '/auth/refresh') {
        await new Promise<void>((resolve) => {
          releaseRefresh = resolve
        })
        return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
      }
      // The retry would succeed with the refreshed token, so a bug would keep the user signed in.
      return call.authorization === 'Bearer access-2' ? json(200, { ok: true }) : backendError(401, 'Unauthorized')
    })

    const pending = api.get('/users/me').catch((caught: unknown) => caught)
    await new Promise((resolve) => setTimeout(resolve, 0))
    tokenStorage.clear()
    releaseRefresh()

    await expect(pending).resolves.toMatchObject({ status: 401 })
    expect(tokenStorage.hasSession()).toBe(false)
  })

  it('does not clear a newer session when an old request fails late', async () => {
    let releaseRefresh: () => void = () => undefined
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    installFakeFetch(async (call) => {
      if (call.path === '/auth/refresh') {
        await new Promise<void>((resolve) => {
          releaseRefresh = resolve
        })
        return backendError(401, 'Invalid or expired token')
      }
      return backendError(401, 'Unauthorized')
    })

    const pending = api.get('/users/me').catch((caught: unknown) => caught)
    await new Promise((resolve) => setTimeout(resolve, 0))
    tokenStorage.startSession({ accessToken: 'new-user-access', refreshToken: 'new-user-refresh' })
    releaseRefresh()

    // FE-01 round 3 (N1): the old request fails without returning anything; depending on timing
    // it ends as a 401 of the old session or as StaleSessionError. Either way it is an ApiError.
    await expect(pending).resolves.toBeInstanceOf(ApiError)
    expect(tokenStorage.getAccessToken()).toBe('new-user-access')
    expect(expired).not.toHaveBeenCalled()
    unsubscribe()
  })

  it('drops the answer of a request started before another tab switched the account (N1)', async () => {
    let release: () => void = () => undefined
    installFakeFetch(async () => {
      await new Promise<void>((resolve) => {
        release = resolve
      })
      return json(200, { owner: 'previous account' })
    })

    const pending = api.get('/members/me/cards').catch((caught: unknown) => caught)
    await new Promise((resolve) => setTimeout(resolve, 0))
    // Another tab logged in as someone else: the storage listener marks the change.
    tokenStorage.markExternalSessionChange()
    release()

    const result = await pending
    expect(result).toBeInstanceOf(StaleSessionError)
    expect(result).not.toMatchObject({ owner: 'previous account' })
  })

  it('never retries an old 401 with the token of the new account (N1)', async () => {
    let release: () => void = () => undefined
    const { calls } = installFakeFetch(async (call) => {
      if (call.authorization === 'Bearer access-1') {
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return backendError(401, 'Unauthorized')
      }
      return json(200, { owner: 'new account' })
    })

    const pending = api.get('/members/me/cards').catch((caught: unknown) => caught)
    await new Promise((resolve) => setTimeout(resolve, 0))
    tokenStorage.startSession({ accessToken: 'new-access', refreshToken: 'new-refresh' })
    release()

    expect(await pending).toBeInstanceOf(StaleSessionError)
    expect(calls.filter((call) => call.authorization === 'Bearer new-access')).toHaveLength(0)
  })

  it('keeps the session when the refresh endpoint fails with a server error', async () => {
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    installFakeFetch((call) =>
      call.path === '/auth/refresh' ? backendError(500, 'Internal server error') : backendError(401, 'Unauthorized'),
    )

    await expect(api.get('/users/me')).rejects.toMatchObject({ status: 500, message: 'Internal server error' })
    expect(tokenStorage.getRefreshToken()).toBe('refresh-1')
    expect(expired).not.toHaveBeenCalled()
    unsubscribe()
  })

  it('rethrows the abort reason when the caller cancels the request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => Promise.reject(init?.signal?.reason)),
    )
    const controller = new AbortController()
    controller.abort('route changed')
    await expect(api.get('/users/me', { signal: controller.signal })).rejects.toBe('route changed')
  })

  describe("FE-02 authFailure 'refresh-then-throw' (PUT /users/me/password)", () => {
    const body = { currentPassword: 'x', newPassword: 'y' }
    const options = { authFailure: 'refresh-then-throw' } as const

    it('TC-FE02-105 an expired access token is refreshed and the retry succeeds', async () => {
      const { calls } = installFakeFetch((call) => {
        if (call.path === '/auth/refresh') {
          return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
        }
        return call.authorization === 'Bearer access-2' ? new Response(null, { status: 204 }) : backendError(401, 'Unauthorized')
      })

      await expect(api.put('/users/me/password', body, options)).resolves.toBeUndefined()

      expect(calls.map((call) => call.path)).toEqual(['/users/me/password', '/auth/refresh', '/users/me/password'])
      expect(calls[2].authorization).toBe('Bearer access-2')
      expect(tokenStorage.getAccessToken()).toBe('access-2')
      expect(tokenStorage.getRefreshToken()).toBe('refresh-2')
    })

    it('TC-FE02-106 a 401 on the retry is thrown as ApiError(401) after exactly one refresh, without sign out', async () => {
      const expired = vi.fn()
      const unsubscribe = onSessionExpired(expired)
      const { calls } = installFakeFetch((call) => {
        if (call.path === '/auth/refresh') {
          return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
        }
        return backendError(401, 'Unauthorized')
      })

      const error = await api.put('/users/me/password', body, options).catch((caught: unknown) => caught)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(401)
      expect(calls.map((call) => call.path)).toEqual(['/users/me/password', '/auth/refresh', '/users/me/password'])
      expect(tokenStorage.getAccessToken()).toBe('access-2')
      expect(tokenStorage.getRefreshToken()).toBe('refresh-2')
      expect(expired).not.toHaveBeenCalled()
      unsubscribe()
    })

    it('TC-FE02-107 a rejected refresh token still ends the session', async () => {
      const expired = vi.fn()
      const unsubscribe = onSessionExpired(expired)
      installFakeFetch((call) =>
        call.path === '/auth/refresh' ? backendError(401, 'Invalid refresh token') : backendError(401, 'Unauthorized'),
      )

      const error = await api.put('/users/me/password', body, options).catch((caught: unknown) => caught)

      expect((error as ApiError).status).toBe(401)
      expect(tokenStorage.getAccessToken()).toBeNull()
      expect(tokenStorage.getRefreshToken()).toBeNull()
      expect(expired).toHaveBeenCalledTimes(1)
      unsubscribe()
    })

    it('TC-FE02-108 a server error during the refresh is thrown without sign out', async () => {
      const expired = vi.fn()
      const unsubscribe = onSessionExpired(expired)
      installFakeFetch((call) =>
        call.path === '/auth/refresh' ? backendError(503, 'Service unavailable') : backendError(401, 'Unauthorized'),
      )

      const error = await api.put('/users/me/password', body, options).catch((caught: unknown) => caught)

      expect((error as ApiError).status).toBe(503)
      expect(tokenStorage.getAccessToken()).toBe('access-1')
      expect(tokenStorage.getRefreshToken()).toBe('refresh-1')
      expect(expired).not.toHaveBeenCalled()
      unsubscribe()
    })

    it('TC-FE02-109 a password change and a normal request in parallel share one refresh call', async () => {
      let refreshCount = 0
      installFakeFetch(async (call) => {
        if (call.path === '/auth/refresh') {
          refreshCount += 1
          await new Promise((resolve) => setTimeout(resolve, 10))
          return json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
        }
        if (call.authorization !== 'Bearer access-2') {
          return backendError(401, 'Unauthorized')
        }
        return call.path === '/users/me/password' ? new Response(null, { status: 204 }) : json(200, { ok: true })
      })

      const [password, profile] = await Promise.all([api.put('/users/me/password', body, options), api.get('/users/me')])

      expect(password).toBeUndefined()
      expect(profile).toEqual({ ok: true })
      expect(refreshCount).toBe(1)
    })

    it('TC-FE02-112 without the option a 401 on the retry still ends the session (default unchanged)', async () => {
      const expired = vi.fn()
      const unsubscribe = onSessionExpired(expired)
      installFakeFetch((call) =>
        call.path === '/auth/refresh'
          ? json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' })
          : backendError(401, 'Unauthorized'),
      )

      const error = await api.put('/users/me/password', body).catch((caught: unknown) => caught)

      expect((error as ApiError).status).toBe(401)
      expect(tokenStorage.getAccessToken()).toBeNull()
      expect(expired).toHaveBeenCalledTimes(1)
      unsubscribe()
    })
  })
})
