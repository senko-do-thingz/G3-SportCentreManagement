import { afterEach, describe, expect, it, vi } from 'vitest'
import { receptionApi } from './receptionApi'
import { backendError, installFakeFetch, json } from '../test/fakeBackend'
import { card, registration } from '../test/memberFixtures'
import { deskCard, deskPackage } from '../test/receptionFixtures'
import { tokenStorage } from './tokenStorage'

afterEach(() => vi.unstubAllGlobals())

function page<T>(rows: T[], index = 0, size = 100) {
  return { content: rows.slice(index * size, (index + 1) * size), number: index, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}

describe('FE-05 bounded complete pending queues', () => {
  it('loads large multi-page queues in order with four shared detail workers and per-member deduplication', async () => {
    const cards = Array.from({ length: 216 }, (_, i) => card({ id: i + 1, memberId: 2 + i % 36, status: 'PENDING_PAYMENT' }))
    const registrations = Array.from({ length: 216 }, (_, i) => registration({ id: i + 1, memberId: 2 + i % 36, status: 'PENDING_PAYMENT' }))
    let active = 0; let maximum = 0
    const { calls } = installFakeFetch(async ({ path }) => {
      const url = new URL(`http://test${path}`)
      if (url.pathname.endsWith('/pending')) {
        return json(200, page<ReturnType<typeof deskCard> | ReturnType<typeof deskPackage>>(url.pathname.includes('membership-cards') ? cards.map(deskCard) : registrations.map(deskPackage), Number(url.searchParams.get('page'))))
      }
      active += 1; maximum = Math.max(maximum, active)
      await new Promise((resolve) => setTimeout(resolve, 1))
      active -= 1
      const id = Number(url.pathname.split('/').pop())
      return json(200, url.pathname.includes('membership-cards') ? cards.filter((item) => item.memberId === id) : registrations.filter((item) => item.memberId === id))
    })
    expect(await receptionApi.pendingQueue()).toEqual({ cards, registrations })
    expect(maximum).toBe(4)
    expect(calls.filter((call) => call.path.includes('/member/'))).toHaveLength(72)
    expect(calls.filter((call) => call.path.includes('/pending?'))).toHaveLength(6)
  })

  it('cancels during pagination without issuing the next page or hydration', async () => {
    const controller = new AbortController()
    let finish!: (response: Response) => void
    const { calls, fetchMock } = installFakeFetch(() => new Promise<Response>((resolve) => { finish = resolve }))
    const pending = receptionApi.pendingCards({ signal: controller.signal })
    controller.abort()
    finish(json(200, page([deskCard(card()), deskCard(card({ id: 2 }))], 0, 1)))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(calls).toHaveLength(1)
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true)
  })

  it('stops queued hydration after cancellation and propagates the signal to every in-flight request', async () => {
    const rows = Array.from({ length: 12 }, (_, i) => card({ id: i + 1, memberId: i + 2, status: 'PENDING_PAYMENT' }))
    const controller = new AbortController()
    const finishes: ((response: Response) => void)[] = []
    const { calls, fetchMock } = installFakeFetch(({ path }) => path.includes('/pending?') ? json(200, page(rows.map(deskCard)))
      : new Promise<Response>((resolve) => { finishes.push(resolve) }))
    const pending = receptionApi.pendingCards({ signal: controller.signal })
    await vi.waitFor(() => expect(finishes).toHaveLength(4))
    controller.abort()
    finishes.forEach((finish) => finish(json(200, [])))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    expect(calls).toHaveLength(5)
    expect(fetchMock.mock.calls.every(([, init]) => init?.signal?.aborted)).toBe(true)
  })

  it('does not begin a cancelled load or continue it under another account', async () => {
    const controller = new AbortController(); controller.abort()
    const { calls } = installFakeFetch(() => json(200, page([])))
    await expect(receptionApi.pendingQueue({ signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' })
    expect(calls).toHaveLength(0)
    let finish!: (response: Response) => void
    const fake = installFakeFetch(() => new Promise<Response>((resolve) => { finish = resolve }))
    const pending = receptionApi.pendingCards()
    tokenStorage.markExternalSessionChange()
    finish(json(200, page([deskCard(card())])))
    await expect(pending).rejects.toMatchObject({ name: 'StaleSessionError' })
    expect(fake.calls).toHaveLength(1)
  })

  it('reports a missing detail as an error and retries without a stale or negative cache', async () => {
    const item = card({ status: 'PENDING_PAYMENT' })
    let missing = true
    const { calls } = installFakeFetch(({ path }) => json(200, path.includes('/pending?') ? page([deskCard(item)]) : missing ? [] : [item]))
    await expect(receptionApi.pendingCards()).rejects.toThrow('details are unavailable')
    missing = false
    expect(await receptionApi.pendingCards()).toEqual([item])
    expect(calls.filter((call) => call.path.includes('/member/'))).toHaveLength(2)
  })

  it('fails the whole load and aborts peer workers when one detail request fails', async () => {
    const rows = Array.from({ length: 12 }, (_, i) => card({ id: i + 1, memberId: i + 2, status: 'PENDING_PAYMENT' }))
    const finishes: ((response: Response) => void)[] = []
    const { calls, fetchMock } = installFakeFetch(({ path }) => path.includes('/pending?') ? json(200, page(rows.map(deskCard)))
      : new Promise<Response>((resolve) => { finishes.push(resolve) }))
    const pending = receptionApi.pendingCards()
    await vi.waitFor(() => expect(finishes).toHaveLength(4))
    finishes[0](backendError(403, 'Detail forbidden'))
    await expect(pending).rejects.toMatchObject({ status: 403 })
    finishes.slice(1).forEach((finish) => finish(json(200, [])))
    await Promise.resolve()
    expect(calls).toHaveLength(5)
    expect(fetchMock.mock.calls.every(([, init]) => init?.signal?.aborted)).toBe(true)
  })

  it.each([400, 403, 404, 500])('propagates detail HTTP %s and allows an explicit fresh retry', async (status) => {
    const item = card({ status: 'PENDING_PAYMENT' }); let failing = true
    installFakeFetch(({ path }) => path.includes('/pending?') ? json(200, page([deskCard(item)]))
      : failing ? backendError(status, 'Detail unavailable') : json(200, [item]))
    await expect(receptionApi.pendingCards()).rejects.toMatchObject({ status })
    failing = false
    expect(await receptionApi.pendingCards()).toEqual([item])
  })

  it.each(['empty-middle', 'changed-count', 'duplicate', 'wrong-page', 'oversized'])('rejects inconsistent paging: %s', async (kind) => {
    const rows = [deskCard(card({ id: 1 })), deskCard(card({ id: 2 }))]
    const { calls } = installFakeFetch(({ path }) => {
      const index = Number(new URL(`http://test${path}`).searchParams.get('page'))
      const answer = page(rows, index, 1)
      if (kind === 'oversized') return json(200, { ...answer, size: 101 })
      if (index === 1) {
        if (kind === 'empty-middle') answer.content = []
        if (kind === 'changed-count') answer.totalElements = 1
        if (kind === 'duplicate') answer.content = [rows[0]]
        if (kind === 'wrong-page') answer.number = 0
      }
      return json(200, answer)
    })
    await expect(receptionApi.pendingCards()).rejects.toThrow()
    expect(calls.some((call) => call.path.includes('/member/'))).toBe(false)
    expect(calls.length).toBeLessThanOrEqual(2)
  })

  it('terminates empty last pages without hydrating or inventing an item', async () => {
    const { calls } = installFakeFetch(() => json(200, page([])))
    expect(await receptionApi.pendingQueue()).toEqual({ cards: [], registrations: [] })
    expect(calls).toHaveLength(2)
  })
})
