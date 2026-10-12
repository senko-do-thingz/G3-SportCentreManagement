import { describe, expect, it } from 'vitest'
import { tokenStorage } from './tokenStorage'

describe('tokenStorage and Remember me', () => {
  it('keeps a remembered session in localStorage (default, as before FE-01)', () => {
    tokenStorage.startSession({ accessToken: 'a1', refreshToken: 'r1' })
    expect(window.localStorage.getItem('sportify.accessToken')).toBe('a1')
    expect(window.sessionStorage.getItem('sportify.accessToken')).toBeNull()
    expect(tokenStorage.isPersistent()).toBe(true)
  })

  it('keeps a session that is not remembered in sessionStorage only', () => {
    tokenStorage.startSession({ accessToken: 'a1', refreshToken: 'r1' }, { persistent: false })
    expect(window.sessionStorage.getItem('sportify.refreshToken')).toBe('r1')
    expect(window.localStorage.getItem('sportify.refreshToken')).toBeNull()
    expect(tokenStorage.getAccessToken()).toBe('a1')
    expect(tokenStorage.hasSession()).toBe(true)
    expect(tokenStorage.isPersistent()).toBe(false)
  })

  it('rotates the tokens in the area of the current session', () => {
    tokenStorage.startSession({ accessToken: 'a1', refreshToken: 'r1' }, { persistent: false })
    tokenStorage.rotate({ accessToken: 'a2', refreshToken: 'r2' })
    expect(window.sessionStorage.getItem('sportify.accessToken')).toBe('a2')
    expect(window.localStorage.getItem('sportify.accessToken')).toBeNull()
  })

  it("prefers this tab's own session over a remembered one written by another tab", () => {
    tokenStorage.startSession({ accessToken: 'tab-a', refreshToken: 'tab-r' }, { persistent: false })
    window.localStorage.setItem('sportify.accessToken', 'other-tab')
    expect(tokenStorage.getAccessToken()).toBe('tab-a')
  })

  it('a new login removes the session of the other area', () => {
    tokenStorage.startSession({ accessToken: 'old', refreshToken: 'old-r' })
    tokenStorage.startSession({ accessToken: 'new', refreshToken: 'new-r' }, { persistent: false })
    expect(window.localStorage.getItem('sportify.accessToken')).toBeNull()
    tokenStorage.startSession({ accessToken: 'newer', refreshToken: 'newer-r' })
    expect(window.sessionStorage.getItem('sportify.accessToken')).toBeNull()
    expect(tokenStorage.getAccessToken()).toBe('newer')
  })

  it('logout clears both areas and starts a new epoch', () => {
    tokenStorage.startSession({ accessToken: 'a1', refreshToken: 'r1' }, { persistent: false })
    const epoch = tokenStorage.getEpoch()
    tokenStorage.clear()
    expect(tokenStorage.hasSession()).toBe(false)
    expect(window.sessionStorage.length).toBe(0)
    expect(tokenStorage.getEpoch()).toBe(epoch + 1)
  })
})
