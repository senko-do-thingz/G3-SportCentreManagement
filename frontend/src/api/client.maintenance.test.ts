import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { api, onSessionExpired } from './client'
import { StaleSessionError } from './errors'
import { tokenStorage } from './tokenStorage'

describe('FE-00 response and refresh ownership', () => {
  beforeEach(() => tokenStorage.startSession({ accessToken: 'old-access', refreshToken: 'old-refresh' }))
  afterEach(() => vi.unstubAllGlobals())

  it('drops a response whose body finishes after the account changes', async () => {
    let finish!: (body: string) => void
    const text = new Promise<string>((resolve) => { finish = resolve })
    const response = json(200, {})
    const reading = vi.spyOn(response, 'text').mockReturnValue(text)
    installFakeFetch(() => response)
    const pending = api.get('/users/me').catch((error: unknown) => error)
    await vi.waitFor(() => expect(reading).toHaveBeenCalledOnce())
    tokenStorage.startSession({ accessToken: 'new-access', refreshToken: 'new-refresh' })
    finish(JSON.stringify({ id: 1 }))
    expect(await pending).toBeInstanceOf(StaleSessionError)
    expect(tokenStorage.getAccessToken()).toBe('new-access')
  })

  it('does not retry an old operation with a new account after refresh', async () => {
    let finish!: (response: Response) => void
    const refresh = new Promise<Response>((resolve) => { finish = resolve })
    const { calls } = installFakeFetch((call) => call.path === '/auth/refresh'
      ? refresh : backendError(401, 'Unauthorized'))
    const pending = api.get('/users/me').catch((error: unknown) => error)
    await vi.waitFor(() => expect(calls.some((call) => call.path === '/auth/refresh')).toBe(true))
    tokenStorage.startSession({ accessToken: 'new-access', refreshToken: 'new-refresh' })
    finish(backendError(401, 'Unauthorized'))
    expect(await pending).toBeInstanceOf(StaleSessionError)
    expect(calls.map((call) => call.path)).toEqual(['/users/me', '/auth/refresh'])
    expect(tokenStorage.getAccessToken()).toBe('new-access')
  })

  it.each([0, 503, 200])('keeps a recoverable session when refresh fails (%s)', async (status) => {
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    try {
      const { calls } = installFakeFetch((call) => {
        if (call.path !== '/auth/refresh') return backendError(401, 'Unauthorized')
        if (status === 0) throw new TypeError('Failed to fetch')
        return status === 200 ? json(200, { accessToken: 42 }) : backendError(status, 'Unavailable')
      })
      await expect(api.get('/users/me')).rejects.toMatchObject({ status: status === 200 ? 502 : status })
      expect(tokenStorage.getRefreshToken()).toBe('old-refresh')
      expect(expired).not.toHaveBeenCalled()
      expect(calls.map((call) => call.path)).toEqual(['/users/me', '/auth/refresh'])
    } finally {
      unsubscribe()
    }
  })
})
