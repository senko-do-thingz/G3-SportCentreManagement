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
  /**
   * Machine readable error code when the body carries one ({code: "PAYMENT_AMOUNT_MISMATCH"}),
   * null otherwise. Screens match on it instead of on the message text.
   */
  readonly code: string | null
  /** The raw details value of the body, for errors whose details are not field messages. */
  readonly details: unknown

  constructor(
    status: number,
    message: string,
    fieldErrors: Record<string, string> = {},
    code: string | null = null,
    details: unknown = undefined,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
    this.code = code
    this.details = details
  }

  /** True when the request never reached the server (offline, DNS, CORS, server down). */
  get isNetworkError(): boolean {
    return this.status === 0
  }

  get hasFieldErrors(): boolean {
    return Object.keys(this.fieldErrors).length > 0
  }
}

export const SESSION_CHANGED_MESSAGE =
  'The signed in account changed while this request was running. Please try again.'

/**
 * Thrown by the API client when the session changed (logout, login, or another tab switched the
 * account) while a request was running. The answer belonged to the previous account, so it is
 * never returned to the caller and never retried with the new account's token.
 */
export class StaleSessionError extends ApiError {
  constructor() {
    super(0, SESSION_CHANGED_MESSAGE)
    this.name = 'StaleSessionError'
  }

  override get isNetworkError(): boolean {
    return false
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
    const code = typeof record.code === 'string' && record.code.trim().length > 0 ? record.code : null
    return new ApiError(status, message, toFieldErrors(record.details), code, record.details)
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

/**
 * Splits backend validation details between the fields a form renders and the rest.
 * fields: messages whose key matches a rendered input, shown under that input.
 * formMessage: the messages whose key has no input (for example "role"), joined, to show above
 * the form; null when every key matched. Without this, such errors would be lost and the form
 * would simply be enabled again with no message.
 */
export function splitFieldErrors(
  fieldErrors: Record<string, string>,
  renderedFields: readonly string[],
): { fields: Record<string, string>; formMessage: string | null } {
  const fields: Record<string, string> = {}
  const unmatched: string[] = []
  for (const [key, message] of Object.entries(fieldErrors)) {
    if (renderedFields.includes(key)) {
      fields[key] = message
    } else {
      unmatched.push(message)
    }
  }
  return { fields, formMessage: unmatched.length > 0 ? unmatched.join(' ') : null }
}
