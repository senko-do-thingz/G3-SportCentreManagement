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

describe('refresh timeout', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })

  afterEach(() => {
    vi.useRealTimers()
    removeLocks()
    vi.unstubAllGlobals()
  })

  /** Spies that find the timer created for the refresh request and check that it is cleared. */
  function watchRefreshTimer(timeoutMs: number) {
    const setSpy = vi.spyOn(globalThis, 'setTimeout')
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout')
    return {
      ids: () =>
        setSpy.mock.calls
          .map((call, index) => (call[1] === timeoutMs ? setSpy.mock.results[index]?.value : undefined))
          .filter((id) => id !== undefined),
      wasCleared: (id: unknown) => clearSpy.mock.calls.some((call) => call[0] === id),
    }
  }

  /**
   * Fetch double: GET /users/me accepts only access-2. The first hangCount calls to
   * POST /auth/refresh hang until their signal is aborted; later calls answer with a new pair.
   */
  function hangingBackend(hangCount = Number.POSITIVE_INFINITY) {
    const state = { hangRefresh: true, refreshCalls: 0, abortedRefreshes: 0 }
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = new URL(String(input)).pathname.replace('/api/v1', '')
      if (path === '/auth/refresh') {
        state.refreshCalls += 1
        if (!state.hangRefresh || state.refreshCalls > hangCount) {
          return Promise.resolve(json(200, { accessToken: 'access-2', refreshToken: 'refresh-2' }))
        }
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            state.abortedRefreshes += 1
            reject(new DOMException('The operation was aborted.', 'AbortError'))
          })
        })
      }
      const authorization = new Headers(init?.headers).get('Authorization')
      return Promise.resolve(
        authorization === 'Bearer access-2' ? json(200, { ok: true }) : backendError(401, 'Unauthorized'),
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    return state
  }

  it('aborts a refresh that never answers, keeps the session and releases the lock', async () => {
    installFakeLocks()
    const backend = hangingBackend()
    const tab = await openTab()
    const { REFRESH_TIMEOUT_MS } = await import('./client')
    tab.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const timer = watchRefreshTimer(REFRESH_TIMEOUT_MS)

    const pending = tab.api.get('/users/me').catch((caught: unknown) => caught)
    await vi.advanceTimersByTimeAsync(REFRESH_TIMEOUT_MS - 1)
    expect(backend.abortedRefreshes).toBe(0)
    await vi.advanceTimersByTimeAsync(1)

    const error = (await pending) as { status: number; message: string }
    expect(REFRESH_TIMEOUT_MS).toBe(15000)
    expect(backend.abortedRefreshes).toBe(1)
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/cannot reach the server/i)
    expect(window.localStorage.getItem('sportify.accessToken')).toBe('access-1')
    expect(window.localStorage.getItem('sportify.refreshToken')).toBe('refresh-1')
    expect(tab.expired).not.toHaveBeenCalled()
    expect(timer.ids()).toHaveLength(1)
    expect(timer.wasCleared(timer.ids()[0])).toBe(true)

    // The lock was released: a second attempt can refresh once the server answers again.
    backend.hangRefresh = false
    await expect(tab.api.get('/users/me')).resolves.toEqual({ ok: true })
    expect(backend.refreshCalls).toBe(2)
    expect(window.localStorage.getItem('sportify.refreshToken')).toBe('refresh-2')
  })

  it('lets a tab waiting for the lock continue after the other tab timed out', async () => {
    installFakeLocks()
    // Only the first refresh call (tab A) hangs; the second one (tab B) answers.
    const backend = hangingBackend(1)
    const tabA = await openTab()
    const tabB = await openTab()
    const { REFRESH_TIMEOUT_MS } = await import('./client')
    tabA.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })

    const pendingA = tabA.api.get('/users/me').catch((caught: unknown) => caught)
    const pendingB = tabB.api.get('/users/me')
    await vi.advanceTimersByTimeAsync(REFRESH_TIMEOUT_MS)

    // Tab A timed out and released the lock, so tab B could take it and refresh.
    expect(await pendingA).toMatchObject({ status: 0 })
    await expect(pendingB).resolves.toEqual({ ok: true })
    expect(backend.refreshCalls).toBe(2)
    expect(backend.abortedRefreshes).toBe(1)
    expect(tabA.expired).not.toHaveBeenCalled()
    expect(tabB.expired).not.toHaveBeenCalled()
    expect(window.localStorage.getItem('sportify.refreshToken')).toBe('refresh-2')
  })

  it('clears the timer when the refresh answers in time', async () => {
    const backend = hangingBackend()
    backend.hangRefresh = false
    const tab = await openTab()
    const { REFRESH_TIMEOUT_MS } = await import('./client')
    tab.tokenStorage.startSession({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    const timer = watchRefreshTimer(REFRESH_TIMEOUT_MS)

    await expect(tab.api.get('/users/me')).resolves.toEqual({ ok: true })
    expect(backend.refreshCalls).toBe(1)
    expect(timer.ids()).toHaveLength(1)
    expect(timer.wasCleared(timer.ids()[0])).toBe(true)
  })
})
