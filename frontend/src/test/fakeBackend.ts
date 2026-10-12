import { vi } from 'vitest'

export interface RecordedCall {
  method: string
  path: string
  authorization: string | null
  body: unknown
}

export type Handler = (call: RecordedCall) => Response | Promise<Response>

export const API = 'http://api.test/api/v1'

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function backendError(status: number, message: string, details?: unknown): Response {
  return json(status, { timestamp: '2026-10-12T10:00:00', status, message, ...(details ? { details } : {}) })
}

/** Replaces global fetch with a handler and records every call for assertions. */
export function installFakeFetch(handler: Handler) {
  const calls: RecordedCall[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input.toString())
    const headers = new Headers(init?.headers)
    const call: RecordedCall = {
      method: init?.method ?? 'GET',
      path: `${url.pathname.replace('/api/v1', '')}${url.search}`,
      authorization: headers.get('Authorization'),
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    calls.push(call)
    return handler(call)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

export const MANAGER = {
  id: 1,
  email: 'manager@sportify.com',
  fullName: 'Morgan Lee',
  phone: '0901234500',
  role: 'MANAGER',
  status: 'ACTIVE',
}

export const MEMBER = {
  id: 2,
  email: 'member@sportify.demo',
  fullName: 'Taylor Nguyen',
  phone: '0901234567',
  role: 'MEMBER',
  status: 'ACTIVE',
}
