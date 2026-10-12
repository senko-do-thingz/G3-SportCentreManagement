import { env } from '../config/env'
import {
  ApiError,
  apiErrorFromBody,
  NETWORK_ERROR_MESSAGE,
  SESSION_EXPIRED_MESSAGE,
} from './errors'
import { tokenStorage } from './tokenStorage'
import type { AuthTokens } from './types'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type QueryValue = string | number | boolean | null | undefined
export type QueryParams = Record<string, QueryValue | QueryValue[]>

export interface RequestOptions {
  method?: HttpMethod
  /** Serialized as JSON unless it is FormData. */
  body?: unknown
  query?: QueryParams
  /** Send the Authorization header and refresh on 401. Defaults to true. */
  auth?: boolean
  signal?: AbortSignal
  headers?: Record<string, string>
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
 * Shared promise so that several requests failing with 401 at the same time trigger
 * exactly one POST /auth/refresh call. The backend rotates the refresh token on every
 * refresh, so parallel refresh calls would invalidate each other.
 */
let refreshInFlight: { epoch: number; promise: Promise<string | null> } | null = null

/** Refresh responses that mean the refresh token itself is no longer accepted. */
const REJECTED_REFRESH_STATUSES = new Set([400, 401, 403])

async function performRefresh(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken()
  const epoch = tokenStorage.getEpoch()
  if (!refreshToken) {
    return null
  }
  let response: Response
  try {
    response = await fetch(buildUrl('/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    // The server is unreachable: keep the tokens so the user is not logged out by a network blip.
    throw new ApiError(0, NETWORK_ERROR_MESSAGE)
  }
  const data = await readBody(response)
  if (!response.ok) {
    if (REJECTED_REFRESH_STATUSES.has(response.status)) {
      return null
    }
    // A server error (5xx, 429) says nothing about the token: report it but keep the session.
    throw apiErrorFromBody(response.status, data)
  }
  const tokens = data as AuthTokens | undefined
  if (!tokens?.accessToken || !tokens.refreshToken) {
    return null
  }
  if (tokenStorage.getEpoch() !== epoch) {
    // The user logged out or logged in as someone else while the refresh was running.
    return null
  }
  tokenStorage.rotate(tokens)
  return tokens.accessToken
}

/** Returns the new access token, or null when the session cannot be refreshed. */
export function refreshAccessToken(): Promise<string | null> {
  const epoch = tokenStorage.getEpoch()
  // Join the running refresh only when it belongs to the current session.
  if (!refreshInFlight || refreshInFlight.epoch !== epoch) {
    const promise = performRefresh().finally(() => {
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
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const useAuth = options.auth !== false && !path.startsWith(AUTH_PATH_PREFIX)
  const accessToken = useAuth ? tokenStorage.getAccessToken() : null
  const epoch = tokenStorage.getEpoch()

  let response = await send(path, options, accessToken)

  if (response.status === 401 && useAuth && (accessToken || tokenStorage.getRefreshToken())) {
    // Another request may already have refreshed while this one was in flight: reuse that token.
    const storedAccessToken = tokenStorage.getAccessToken()
    const newAccessToken =
      storedAccessToken && storedAccessToken !== accessToken
        ? storedAccessToken
        : await refreshAccessToken()
    if (!newAccessToken) {
      throw expireSession(epoch)
    }
    response = await send(path, options, newAccessToken)
    if (response.status === 401) {
      throw expireSession(epoch)
    }
  }

  const data = await readBody(response)
  if (!response.ok) {
    throw apiErrorFromBody(response.status, data)
  }
  return data as T
}

export const api = {
  get<T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) {
    return apiRequest<T>(path, { ...options, method: 'GET' })
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
