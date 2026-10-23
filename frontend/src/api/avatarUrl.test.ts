import { describe, expect, it } from 'vitest'
import { env } from '../config/env'
import { avatarSrc } from './avatarUrl'

describe('avatarSrc', () => {
  it('keeps absolute http(s) links and resolves uploaded photos on the API', () => {
    expect(avatarSrc(' https://cdn.example.test/a.jpg ')).toBe('https://cdn.example.test/a.jpg')
    expect(avatarSrc('/api/v1/users/7/avatar?v=123')).toBe(`${env.apiBaseUrl}/users/7/avatar?v=123`)
    expect(avatarSrc('/api/v1/users/7/avatar')).toBe(`${env.apiBaseUrl}/users/7/avatar`)
    expect(avatarSrc('/images/coaches/khoa-nguyen.jpg')).toBe('/images/coaches/khoa-nguyen.jpg')
  })

  it('rejects anything else', () => {
    for (const value of ['', null, undefined, 'javascript:alert(1)', '/api/v1/users/7/avatar/../../secret', '/images/a.jpg', '/images/coaches/../secret.jpg', '/images/coaches/a.svg', 'data:image/png;base64,AAA']) {
      expect(avatarSrc(value)).toBeNull()
    }
  })
})
