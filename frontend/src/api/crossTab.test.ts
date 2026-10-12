import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { backendError, installFakeFetch, json, type RecordedCall } from '../test/fakeBackend'

/**
 * Simulates two browser tabs: each tab gets its own instance of the client and token storage
 * modules (separate in-memory state) while both share the same window.localStorage, like real tabs.
 */
async function openTab() {
  vi.resetModules()
  const client = await import('./client')
  const storage = await import('./tokenStorage')
  const expired = vi.fn()
  client.onSessionExpired(expired)
  return { api: client.api, tokenStorage: storage.tokenStorage, expired }
}

/** Minimal Web Locks API: exclusive locks granted in request order. */
function installFakeLocks() {
  let tail: Promise<unknown> = Promise.resolve()
  const locks = {
    request<T>(_name: string, callback: () => Promise<T>): Promise<T> {
      const run = tail.then(() => callback())
      tail = run.catch(() => undefined)
      return run
    },
  }
  Object.defineProperty(navigator, 'locks', { value: locks, configurable: true })
}

function removeLocks() {
  Reflect.deleteProperty(navigator, 'locks')
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * Backend double that rotates refresh tokens like AuthenticationServiceImpl: a refresh token works
 * once, then it is rejected with 401.
 */
function rotatingBackend({ refreshDelays = [10, 10] }: { refreshDelays?: number[] } = {}) {
  const validAccess = new Set<string>()
  const validRefresh = new Set<string>(['refresh-1'])
  let issued = 1
  let refreshCalls = 0
  const handler = async (call: RecordedCall) => {
    if (call.path === '/auth/refresh') {
      const delayMs = refreshDelays[Math.min(refreshCalls, refreshDelays.length - 1)]
      refreshCalls += 1
      const { refreshToken } = call.body as { refreshToken: string }
      const accepted = validRefresh.delete(refreshToken)
      await delay(delayMs)
      if (!accepted) {
        return backendError(401, 'Invalid or expired token')
      }
      issued += 1
      validAccess.add(`access-${issued}`)
      validRefresh.add(`refresh-${issued}`)
      return json(200, { accessToken: `access-${issued}`, refreshToken: `refresh-${issued}` })
    }
    const token = call.authorization?.replace('Bearer ', '') ?? ''
    return validAccess.has(token) ? json(200, { ok: true }) : backendError(401, 'Unauthorized')
  }
  return { handler, refreshCount: () => refreshCalls, revokeAll: () => validRefresh.clear() }
}

describe('refresh across two tabs', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  afterEach(() => {
    removeLocks()
    vi.unstubAllGlobals()
  })

  it('with Web Locks: parallel 401 in two tabs makes exactly one refresh call and nobody is logged out', async () => {
    installFakeLocks()
    const backend = rotatingBackend()
    const { calls } = installFakeFetch(backend.handler)
    const tabA = await openTab()
    const tabB = await openTab()
    tabA.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })

    const [resultA, resultB] = await Promise.all([tabA.api.get('/users/me'), tabB.api.get('/users/me')])

    expect(resultA).toEqual({ ok: true })
    expect(resultB).toEqual({ ok: true })
    expect(calls.filter((call) => call.path === '/auth/refresh')).toHaveLength(1)
    expect(window.localStorage.getItem('sportify.accessToken')).toBe('access-2')
    expect(tabA.expired).not.toHaveBeenCalled()
    expect(tabB.expired).not.toHaveBeenCalled()
  })

  it('without Web Locks: the second refresh is rejected but the tab reuses the token stored by the other tab', async () => {
    removeLocks()
    // Tab A refreshes first; the second refresh with the same, now rotated, token is rejected later.
    const backend = rotatingBackend({ refreshDelays: [10, 30] })
    installFakeFetch(backend.handler)
    const tabA = await openTab()
    const tabB = await openTab()
    tabA.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })

    const [resultA, resultB] = await Promise.all([tabA.api.get('/users/me'), tabB.api.get('/users/me')])

    expect(resultA).toEqual({ ok: true })
    expect(resultB).toEqual({ ok: true })
    expect(backend.refreshCount()).toBe(2)
    expect(window.localStorage.getItem('sportify.refreshToken')).toBe('refresh-2')
    expect(tabA.expired).not.toHaveBeenCalled()
    expect(tabB.expired).not.toHaveBeenCalled()
  })

  it('a really rejected refresh token still logs out both tabs', async () => {
    installFakeLocks()
    const backend = rotatingBackend()
    backend.revokeAll()
    installFakeFetch(backend.handler)
    const tabA = await openTab()
    const tabB = await openTab()
    tabA.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })

    const results = await Promise.allSettled([tabA.api.get('/users/me'), tabB.api.get('/users/me')])

    expect(results.map((result) => result.status)).toEqual(['rejected', 'rejected'])
    expect(backend.refreshCount()).toBe(1)
    expect(window.localStorage.getItem('sportify.accessToken')).toBeNull()
    expect(window.localStorage.getItem('sportify.refreshToken')).toBeNull()
    expect(tabA.expired).toHaveBeenCalledTimes(1)
    expect(tabB.expired).toHaveBeenCalledTimes(1)
  })
})
