import { env, managerPreviewEnabled } from '../config/env'
import {
  ApiError,
  apiErrorFromBody,
  NETWORK_ERROR_MESSAGE,
  SESSION_EXPIRED_MESSAGE,
  StaleSessionError,
} from './errors'
import { tokenStorage } from './tokenStorage'
import { isAuthTokens } from './types'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** See RequestOptions.authFailure. */
export type AuthFailureMode = 'expire-session' | 'refresh-then-throw'

export type QueryValue = string | number | boolean | null | undefined
export type QueryParams = Record<string, QueryValue | QueryValue[]>

export interface RequestOptions {
  method?: HttpMethod
  /** Serialized as JSON unless it is FormData. */
  body?: unknown
  query?: QueryParams
  /** Send the Authorization header and refresh on 401. Defaults to true. */
  auth?: boolean
  /**
   * What a 401 on the retried request means. The first 401 always refreshes the token once (with
   * the shared single-flight refresh) and retries once.
   * - 'expire-session' (default): a 401 on the retry ends the session (tokens cleared, sign out).
   * - 'refresh-then-throw': a 401 on the retry is thrown as a normal ApiError(401) and the session
   *   is kept. For endpoints where 401 can describe the request body rather than the session, for
   *   example PUT /users/me/password with a wrong current password.
   * With both values a rejected refresh token still ends the session, and a network or server
   * error during the refresh is thrown without signing out.
   */
  authFailure?: AuthFailureMode
  signal?: AbortSignal
  headers?: Record<string, string>
}

export interface ApiDownload {
  blob: Blob
  contentDisposition: string | null
  contentType: string | null
}

const AUTH_PATH_PREFIX = '/auth/'

type SessionExpiredListener = () => void
const sessionExpiredListeners = new Set<SessionExpiredListener>()

/**
 * Registers a callback that runs when the refresh token is rejected and the user must log in again.
 * Returns an unsubscribe function.
 */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener)
  return () => {
    sessionExpiredListeners.delete(listener)
  }
}

function notifySessionExpired(): void {
  sessionExpiredListeners.forEach((listener) => listener())
}

export function buildUrl(path: string, query?: QueryParams): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = `${env.apiBaseUrl}${normalizedPath}`
  if (!query) {
    return url
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value]
    for (const item of values) {
      if (item !== undefined && item !== null && item !== '') {
        params.append(key, String(item))
      }
    }
  }
  const search = params.toString()
  return search ? `${url}?${search}` : url
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined
  }
  const text = await response.text()
  if (!text) {
    return undefined
  }
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    try {
      return JSON.parse(text)
    } catch {
      return undefined
    }
  }
  return text
}

/**
 * Refresh coordination.
 *
 * The backend rotates the refresh token on every POST /auth/refresh, and every browser tab shares
 * the tokens through localStorage. Two refresh calls with the same refresh token would therefore
 * make the second one fail and log the user out. Three layers prevent that:
 * 1. In one tab, parallel 401 responses share one refresh promise (refreshInFlight).
 * 2. Across tabs, the refresh runs inside a Web Locks API lock, so only one tab refreshes at a
 *    time. Inside the lock the tokens are read again, and a token already refreshed by another
 *    tab is reused without calling the backend.
 * 3. Without Web Locks (old browsers), a rejected refresh reads storage again: if another tab
 *    stored a newer refresh token in the meantime, its access token is used and the session stays.
 */
let refreshInFlight: { epoch: number; promise: Promise<string | null> } | null = null

const REFRESH_LOCK_NAME = 'sportify-auth-refresh'

/**
 * Maximum time in milliseconds for POST /auth/refresh. A refresh that takes longer is aborted and
 * handled like a network error (session kept, lock released). Normal requests have no timeout.
 */
export const REFRESH_TIMEOUT_MS = 15000

/** Refresh responses that mean the refresh token is no longer accepted (404: the user no longer exists). */
const REJECTED_REFRESH_STATUSES = new Set([400, 401, 403, 404])

interface LockManagerLike {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>
}

function lockManager(): LockManagerLike | null {
  if (typeof navigator === 'undefined') {
    return null
  }
  const locks = (navigator as Navigator & { locks?: LockManagerLike }).locks
  return locks && typeof locks.request === 'function' ? locks : null
}

/** Runs the callback while holding the cross tab refresh lock, or directly when locks are unsupported. */
function withRefreshLock<T>(callback: () => Promise<T>): Promise<T> {
  const locks = lockManager()
  return locks ? locks.request(REFRESH_LOCK_NAME, callback) : callback()
}

async function performRefresh(failedAccessToken: string | null): Promise<string | null> {
  const epoch = tokenStorage.getEpoch()
  return withRefreshLock(async () => {
    if (tokenStorage.getEpoch() !== epoch) {
      // The user logged out or logged in again while this tab was waiting for the lock.
      return null
    }
    // Another tab (or an earlier request) may have refreshed while this one was waiting.
    const storedAccessToken = tokenStorage.getAccessToken()
    if (storedAccessToken && storedAccessToken !== failedAccessToken) {
      return storedAccessToken
    }
    const refreshToken = tokenStorage.getRefreshToken()
    if (!refreshToken) {
      return null
    }

    // The request runs while this tab holds the cross tab lock, so it must not wait forever:
    // other tabs queue behind it. The timer covers the response headers and the body.
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), REFRESH_TIMEOUT_MS)
    let response: Response
    let data: unknown
    try {
      response = await fetch(buildUrl('/auth/refresh'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ refreshToken }),
        signal: controller.signal,
      })
      data = await readBody(response)
    } catch {
      // Unreachable server or timeout: keep the tokens so the user is not logged out by a network
      // blip. Throwing also ends the lock callback, so the lock is released for other tabs.
      throw new ApiError(0, NETWORK_ERROR_MESSAGE)
    } finally {
      clearTimeout(timer)
    }

    if (!response.ok) {
      if (!REJECTED_REFRESH_STATUSES.has(response.status)) {
        // A server error (5xx, 429) says nothing about the token: report it but keep the session.
        throw apiErrorFromBody(response.status, data)
      }
      // Rejected. If another tab rotated the tokens meanwhile, the session is still valid.
      const currentRefreshToken = tokenStorage.getRefreshToken()
      const currentAccessToken = tokenStorage.getAccessToken()
      if (currentRefreshToken && currentRefreshToken !== refreshToken && currentAccessToken) {
        return currentAccessToken
      }
      // End the session while still holding the lock, so a tab waiting for it sees empty storage
      // instead of sending the same rejected refresh token again.
      if (tokenStorage.getEpoch() === epoch) {
        tokenStorage.clear()
        notifySessionExpired()
      }
      return null
    }

    if (!isAuthTokens(data)) {
      throw new ApiError(502, 'The authentication service returned an invalid response. Please try again later.')
    }
    if (tokenStorage.getEpoch() !== epoch) {
      // The user logged out or logged in as someone else while the refresh was running.
      return null
    }
    tokenStorage.rotate(data)
    return data.accessToken
  })
}

/**
 * Returns a usable access token after a 401, or null when the session cannot be refreshed.
 * failedAccessToken is the token that was rejected; a different stored token is reused as is.
 */
export function refreshAccessToken(
  failedAccessToken: string | null = tokenStorage.getAccessToken(),
): Promise<string | null> {
  const epoch = tokenStorage.getEpoch()
  // Join the running refresh only when it belongs to the current session.
  if (!refreshInFlight || refreshInFlight.epoch !== epoch) {
    const promise = performRefresh(failedAccessToken).finally(() => {
      if (refreshInFlight?.promise === promise) {
        refreshInFlight = null
      }
    })
    refreshInFlight = { epoch, promise }
  }
  return refreshInFlight.promise
}

/**
 * Ends the session that the failed request belonged to. Does nothing to the storage when a newer
 * session already replaced it, so a late failure cannot log out a user who just logged in.
 */
function expireSession(epoch: number): ApiError {
  if (tokenStorage.getEpoch() === epoch) {
    tokenStorage.clear()
    notifySessionExpired()
  }
  return new ApiError(401, SESSION_EXPIRED_MESSAGE)
}

async function send(path: string, options: RequestOptions, accessToken: string | null): Promise<Response> {
  if (import.meta.env.DEV && managerPreviewEnabled()) {
    const { previewResponse } = await import('./mock/managerPreview')
    const preview = await previewResponse(path, options, accessToken)
    if (preview) return preview
  }
  const { method = 'GET', body, query, signal, headers = {} } = options
  const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers }
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (accessToken) {
    finalHeaders.Authorization = `Bearer ${accessToken}`
  }
  try {
    return await fetch(buildUrl(path, query), { method, headers: finalHeaders, body: payload, signal })
  } catch (error) {
    // Cancelled by the caller (AbortController): rethrow as is, whatever the abort reason is.
    if (signal?.aborted) {
      throw error
    }
    throw new ApiError(0, NETWORK_ERROR_MESSAGE)
  }
}

/**
 * Sends a request to the Sportify API and returns the parsed JSON body.
 *
 * On 401 for an authenticated request the client refreshes the access token once and retries
 * the original request once. If the refresh token is rejected, tokens are cleared, session expired
 * listeners run (the auth context logs the user out) and an ApiError with status 401 is thrown.
 * A network error or a server error during the refresh is thrown without logging out.
 * A 403 is never refreshed: it means the user is signed in but lacks the permission.
 * With authFailure 'refresh-then-throw' a 401 on the retry is thrown as a normal ApiError and the
 * session is kept; a rejected refresh token still ends the session.
 * When the session epoch changes while an authenticated request runs, the request fails with
 * StaleSessionError instead of returning the previous account's answer.
 */
async function successfulResponse(path: string, options: RequestOptions): Promise<{ response: Response; useAuth: boolean; epoch: number }> {
  const useAuth = options.auth !== false && !path.startsWith(AUTH_PATH_PREFIX)
  const accessToken = useAuth ? tokenStorage.getAccessToken() : null
  const epoch = tokenStorage.getEpoch()

  let response = await send(path, options, accessToken)

  // The session changed while the request was running (logout, login, or another tab switched
  // the account). The answer belongs to the previous account: drop it, and never retry it with
  // the token of the new account.
  if (useAuth && tokenStorage.getEpoch() !== epoch) {
    throw new StaleSessionError()
  }

  if (response.status === 401 && useAuth && (accessToken || tokenStorage.getRefreshToken())) {
    // Another request may already have refreshed while this one was in flight: reuse that token.
    const storedAccessToken = tokenStorage.getAccessToken()
    const newAccessToken =
      storedAccessToken && storedAccessToken !== accessToken
        ? storedAccessToken
        : await refreshAccessToken(accessToken)
    if (!newAccessToken) {
      throw expireSession(epoch)
    }
    if (tokenStorage.getEpoch() !== epoch) {
      throw new StaleSessionError()
    }
    response = await send(path, options, newAccessToken)
    if (tokenStorage.getEpoch() !== epoch) {
      throw new StaleSessionError()
    }
    if (response.status === 401 && options.authFailure !== 'refresh-then-throw') {
      throw expireSession(epoch)
    }
    // With 'refresh-then-throw' a 401 here falls through and is thrown below as a normal
    // ApiError(401): the token is fresh, so the 401 is about the request, not the session.
  }

  if (!response.ok) {
    const data = await readBody(response)
    if (useAuth && tokenStorage.getEpoch() !== epoch) {
      throw new StaleSessionError()
    }
    throw apiErrorFromBody(response.status, data)
  }
  return { response, useAuth, epoch }
}

/** Sends an authenticated API request and parses its successful JSON response. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { response, useAuth, epoch } = await successfulResponse(path, options)
  const data = await readBody(response)
  if (useAuth && tokenStorage.getEpoch() !== epoch) {
    throw new StaleSessionError()
  }
  return data as T
}

/** Sends an authenticated API request and returns its successful file response. */
export async function apiDownloadRequest(path: string, options: RequestOptions = {}): Promise<ApiDownload> {
  const { response, useAuth, epoch } = await successfulResponse(path, options)
  const blob = await response.blob()
  if (useAuth && tokenStorage.getEpoch() !== epoch) {
    throw new StaleSessionError()
  }
  return {
    blob,
    contentDisposition: response.headers.get('content-disposition'),
    contentType: response.headers.get('content-type'),
  }
}

export const api = {
  get<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'GET' })
  },
  getDownload(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiDownloadRequest(path, { ...options, method: 'GET' })
  },
  post<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'POST', body })
  },
  put<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'PUT', body })
  },
  patch<T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'PATCH', body })
  },
  delete<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'DELETE' })
  },
}
