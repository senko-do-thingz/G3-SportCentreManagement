import { afterEach, describe, expect, it, vi } from 'vitest'
import { receptionApi } from './receptionApi'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { card, registration } from '../test/memberFixtures'
import { deskCard, deskOverview, deskPackage } from '../test/receptionFixtures'
import { tokenStorage } from './tokenStorage'

afterEach(() => vi.unstubAllGlobals())

const profile = { userId: 2, fullName: 'Test Member', status: 'ACTIVE' }

describe('FE-05 real BE-05 transport contracts', () => {
  it('reads the canonical overview and obtains pricing, history and payment links from staff detail endpoints', async () => {
    const cards = [card(), card({ id: 9, status: 'PENDING_PAYMENT' }), card({ id: 8, status: 'EXPIRED' })]
    const registrations = [registration(), registration({ id: 9, status: 'PENDING_PAYMENT' })]
    installFakeFetch(({ path }) => {
      if (path === '/members/2/overview') return json(200, deskOverview(profile, cards, registrations))
      return json(200, path === '/membership-cards/member/2' ? cards : registrations)
    })
    expect(await receptionApi.memberOverview(2)).toMatchObject({ member: profile, cards, registrations })
  })

  it('reads empty overview collections without inventing a card or a package', async () => {
    installFakeFetch(({ path }) => json(200, path.endsWith('/overview') ? deskOverview(profile, [], []) : []))
    expect(await receptionApi.memberOverview(2)).toMatchObject({ member: profile, cards: [], registrations: [] })
  })

  it('retains canonical recent check-ins alongside full detail history', async () => {
    const recent = { id: 20, checkedInAt: '2026-10-17T10:00:00', result: 'DENIED', denialReason: 'No booking', note: null }
    installFakeFetch(({ path }) => json(200, path.endsWith('/overview')
      ? { ...deskOverview(profile, [], []), recentCheckIns: [recent] } : []))
    expect((await receptionApi.memberOverview(2)).recentCheckIns).toEqual([recent])
  })

  it('returns one-time creation credentials separately from the navigable member summary', async () => {
    const { calls } = installFakeFetch(() => json(201, { ...profile, memberCode: 'MEM-TEST', phone: '+84123456789', email: 'test@example.test', temporaryPassword: 'unit-test-only' }))
    const result = await receptionApi.createMember({ fullName: 'Test Member', phone: '+84123456789', email: 'test@example.test' })
    expect(result.member).not.toHaveProperty('temporaryPassword')
    expect(result.temporaryPassword).toBe('unit-test-only')
    expect(calls[0].body).toEqual({ fullName: 'Test Member', phone: '+84123456789', email: 'test@example.test' })
  })

  it.each([400, 403, 409, 500])('preserves HTTP %s errors during creation', async (status) => {
    installFakeFetch(() => backendError(status, 'Creation rejected'))
    await expect(receptionApi.createMember({ fullName: 'Test Member', phone: '12345678', email: 'test@example.test' })).rejects.toMatchObject({ status })
  })

  it('drops an overview response from an earlier authenticated session', async () => {
    let finish!: (response: Response) => void
    tokenStorage.startSession({ accessToken: 'old-test-access', refreshToken: 'old-test-refresh' })
    installFakeFetch(() => new Promise<Response>((resolve) => { finish = resolve }))
    const pending = receptionApi.memberOverview(2)
    tokenStorage.startSession({ accessToken: 'new-test-access', refreshToken: 'new-test-refresh' })
    finish(json(200, deskOverview(profile, [], [])))
    await expect(pending).rejects.toThrow(/session|account/i)
  })

  it.each([null, {}, { profile: null }, { ...deskOverview(profile, [], []), recentCheckIns: [null] }])(
    'rejects missing profiles and malformed overview responses', async (response) => {
      installFakeFetch(() => json(200, response))
      await expect(receptionApi.memberOverview(2)).rejects.toThrow('unexpected response')
    },
  )

  it('keeps a missing member as a real 404', async () => {
    const { calls } = installFakeFetch(() => backendError(404, 'Member not found'))
    await expect(receptionApi.memberOverview(999)).rejects.toMatchObject({ status: 404 })
    expect(calls).toHaveLength(1)
  })

  it('reads every pending Spring page and hydrates links once per member', async () => {
    const first = card({ id: 8, status: 'PENDING_PAYMENT' })
    const second = card({ id: 9, status: 'PENDING_PAYMENT', paymentId: null })
    const { calls } = installFakeFetch(({ path }) => {
      if (path === '/membership-cards/member/2') return json(200, [first, second])
      const page = new URL(`http://test${path}`).searchParams.get('page')
      return json(200, { content: [deskCard(page === '0' ? first : second)], number: Number(page), size: 1, totalElements: 2, totalPages: 2 })
    })
    expect(await receptionApi.pendingCards()).toEqual([first, second])
    expect(calls.filter(({ path }) => path === '/membership-cards/member/2')).toHaveLength(1)
  })

  it('does not offer a confirmation for an item that was cancelled after the queue loaded', async () => {
    const pending = card({ status: 'PENDING_PAYMENT' })
    installFakeFetch(({ path }) => path.startsWith('/membership-cards/pending')
      ? json(200, { content: [deskCard(pending)], number: 0, size: 20, totalElements: 1, totalPages: 1 })
      : json(200, [{ ...pending, status: 'CANCELLED' }]))
    expect(await receptionApi.pendingCards()).toEqual([])
  })

  it('hydrates the canonical pending package summary without inventing payment identifiers', async () => {
    const item = registration({ status: 'PENDING_PAYMENT', paymentId: null, paymentCode: null })
    installFakeFetch(({ path }) => path.startsWith('/packages/registrations/pending')
      ? json(200, { content: [deskPackage(item)], number: 0, size: 100, totalElements: 1, totalPages: 1 })
      : json(200, [item]))
    expect(await receptionApi.pendingRegistrations()).toEqual([item])
  })
})
