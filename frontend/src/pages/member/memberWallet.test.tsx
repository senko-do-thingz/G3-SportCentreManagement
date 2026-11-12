import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

describe('My Wallet (retired)', () => {
  it('navigating to /member/wallet displays Page not found', async () => {
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      return json(200, {})
    })
    renderApp('/member/wallet')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'My Wallet' })).not.toBeInTheDocument()
  })
})
