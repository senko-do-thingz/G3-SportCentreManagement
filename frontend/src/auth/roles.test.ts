import { describe, expect, it } from 'vitest'
import { MEMBER, MANAGER } from '../test/fakeBackend'
import { loginReturnState, postLoginPath, safeReturnPath } from './roles'

describe('safe authentication continuation', () => {
  it.each([
    '/member', '/member?tab=overview', '/member/classes?sportId=41&level=BEGINNER&format=COACH_LED',
    '/member/packages/81', '/member/schedule?date=2026-10-23', '/member/membership/cards',
  ])('preserves the eligible Member destination %s', (path) => {
    expect(safeReturnPath(path)).toBe(path)
    expect(postLoginPath(MEMBER, path)).toBe(path)
  })

  it.each([
    null, undefined, 42, {}, [], true, '', 'member/classes', 'https://evil.invalid/member/classes',
    'http://evil.invalid', 'javascript:alert(1)', '//evil.invalid/member/classes', '///evil.invalid',
    '/\\evil.invalid', '/member/\\evil.invalid', '/member/../manager', '/member/./classes',
    '/member/%2e%2e/manager', '/member/%252e%252e/manager', '/member/%2f%2fevil.invalid',
    '/member/%5cclasses', '/member/%00', '/member/%zz', '/member/classes\n', '/member/classes\r',
    '/member/classes\t', '/member/classes\u007f', ' /member/classes', '/member/classes?bad=raw space',
  ])('rejects malformed or unsafe redirect value %j', (path) => {
    expect(safeReturnPath(path)).toBeNull()
    expect(postLoginPath(MEMBER, path)).toBe('/member')
  })

  it.each(['MANAGER', 'RECEPTIONIST', 'COACH'] as const)('keeps a %s in its authorized workspace', (role) => {
    const homes = { MANAGER: '/manager', RECEPTIONIST: '/reception', COACH: '/coach' }
    expect(postLoginPath({ ...MANAGER, role }, '/member/classes?sportId=41')).toBe(homes[role])
    expect(postLoginPath({ ...MANAGER, role }, `${homes[role]}/profile`)).toBe(`${homes[role]}/profile`)
  })

  it('rejects role-prefix lookalikes and unknown roles', () => {
    expect(postLoginPath(MEMBER, '/membership/cards')).toBe('/member')
    expect(postLoginPath(MEMBER, '/member-evil/classes')).toBe('/member')
    expect(postLoginPath(MEMBER, '/manager')).toBe('/member')
    expect(postLoginPath({ ...MEMBER, role: 'UNSUPPORTED' }, '/member/classes')).toBe('/403')
  })

  it('copies only validated return intent between forms', () => {
    expect(loginReturnState({ from: '/member/classes?sportId=41', email: 'fixture@example.invalid', password: 'never-copy' }))
      .toEqual({ from: '/member/classes?sportId=41' })
    expect(loginReturnState({ from: '//evil.invalid' })).toBeNull()
    expect(loginReturnState({ from: {} })).toBeNull()
    expect(loginReturnState(null)).toBeNull()
  })
})
