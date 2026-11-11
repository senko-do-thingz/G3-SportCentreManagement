import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from '../../api/tokenStorage'
import { installFakeFetch, json, MEMBER } from '../../test/fakeBackend'
import { renderApp } from '../../test/renderApp'

afterEach(() => vi.unstubAllGlobals())

describe('Wallet Top-Up (retired)', () => {
  it('member and reception top-up routes display Page not found', async () => {
    tokenStorage.startSession({ accessToken: 'member-access', refreshToken: 'member-refresh' })
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, MEMBER)
      return json(200, {})
    })
    renderApp('/member/wallet/topup')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('reception top-up queue route displays Page not found', async () => {
    tokenStorage.startSession({ accessToken: 'reception-access', refreshToken: 'reception-refresh' })
    installFakeFetch((call) => {
      if (call.path === '/users/me') return json(200, { ...MEMBER, role: 'RECEPTIONIST' })
      return json(200, {})
    })
    renderApp('/reception/wallet/topups')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
