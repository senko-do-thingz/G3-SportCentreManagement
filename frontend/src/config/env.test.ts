import { afterEach, describe, expect, it, vi } from 'vitest'
import { normalizeBaseUrl } from './env'

describe('API base URL configuration', () => {
  afterEach(() => vi.unstubAllEnvs())

  it.each([undefined, '', '  '])('uses the versioned default for %s', (value) => {
    expect(normalizeBaseUrl(value)).toBe('http://localhost:8080/api/v1')
  })

  it.each([
    ['http://localhost:8080', 'http://localhost:8080/api/v1'],
    [' http://127.0.0.1:8080/ ', 'http://127.0.0.1:8080/api/v1'],
    ['https://api.example.test/api/v1///', 'https://api.example.test/api/v1'],
    ['/api/v1/', '/api/v1'],
  ])('resolves %s with the API prefix exactly once', (value, expected) => {
    expect(normalizeBaseUrl(value)).toBe(expected)
  })

  it.each([
    'http://localhost:8080/api',
    'http://localhost:8080/api/v1/api/v1',
    'http://localhost:8080/auth/login',
    'http://localhost:8080?debug=1',
    'http://localhost:8080#fragment',
    'https://user:example@api.example.test/api/v1',
    'ftp://api.example.test/api/v1',
    'localhost:8080',
    '//api.example.test/api/v1',
  ])('rejects invalid configuration without echoing its value', (value) => {
    expect(() => normalizeBaseUrl(value)).toThrow('Invalid VITE_API_BASE_URL')
    try {
      normalizeBaseUrl(value)
    } catch (error) {
      expect((error as Error).message).not.toContain(value)
    }
  })

  it('honors the configured origin when constructing both authentication URLs', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
    vi.resetModules()
    const { buildUrl } = await import('../api/client')
    expect(buildUrl('/auth/login')).toBe('http://localhost:8080/api/v1/auth/login')
    expect(buildUrl('/users/me')).toBe('http://localhost:8080/api/v1/users/me')
  })
})
