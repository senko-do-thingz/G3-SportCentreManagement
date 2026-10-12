/**
 * Typed error thrown by the API client for every failed request.
 *
 * The backend error body is {timestamp, status, message, details}. For validation errors
 * (HTTP 400 "Validation Error") details is a map of field name to message, which is exposed
 * here as fieldErrors so forms can show the message under the matching input.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  /** True when the request never reached the server (offline, DNS, CORS, server down). */
  get isNetworkError(): boolean {
    return this.status === 0
  }

  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0
  }
}

export const NETWORK_ERROR_MESSAGE = 'Cannot reach the server. Check your connection and try again.'
export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please log in again.'

const DEFAULT_MESSAGES: Record<number, string> = {
  400: 'The request is invalid.',
  401: 'You need to log in to continue.',
  403: 'You do not have permission to do this.',
  404: 'The requested item was not found.',
  409: 'The request conflicts with existing data.',
  500: 'Something went wrong on the server. Please try again later.',
}

function defaultMessage(status: number): string {
  return DEFAULT_MESSAGES[status] ?? `Request failed with status ${status}.`
}

function toFieldErrors(details: unknown): Record<string, string> {
  if (!details || typeof details !== 'object' || Array.isArray(details)) {
    return {}
  }
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(details as Record<string, unknown>)) {
    if (typeof value === 'string') {
      result[key] = value
    }
  }
  return result
}

/** Builds an ApiError from a parsed response body (or null when the body was not JSON). */
export function apiErrorFromBody(status: number, body: unknown): ApiError {
  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>
    const message =
      typeof record.message === 'string' && record.message.trim().length > 0
        ? record.message
        : defaultMessage(status)
    return new ApiError(status, message, toFieldErrors(record.details))
  }
  return new ApiError(status, defaultMessage(status))
}

/** Returns a user friendly message for any thrown value. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}
