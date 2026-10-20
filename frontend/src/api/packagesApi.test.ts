import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { packagesApi } from './packagesApi'
import { sport, sportPackage } from '../test/memberFixtures'

vi.mock('./client', () => ({ api: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

describe('DEF-001 catalogue response boundary', () => {
  for (const resource of ['sports', 'packages'] as const) {
    const valid = resource === 'sports' ? sport() : sportPackage()
    it.each([null, {}, 'invalid', [null], [{}], [42], [{ ...valid, id: 0 }], [{ ...valid, id: '1' }], [{ ...valid, name: null }], [valid, valid]])(`${resource} rejects malformed resource %j`, async (payload) => {
      vi.mocked(api.get).mockResolvedValueOnce(payload)
      await expect(packagesApi[resource]()).rejects.toThrow('response')
    })
    it(`${resource} preserves valid rows, empty lists and abort signals`, async () => {
      const signal = new AbortController().signal
      vi.mocked(api.get).mockResolvedValueOnce([valid]).mockResolvedValueOnce([])
      await expect(packagesApi[resource]({ signal })).resolves.toEqual([valid])
      expect(api.get).toHaveBeenCalledWith('/' + resource, { signal })
      await expect(packagesApi[resource]()).resolves.toEqual([])
    })
    it(`${resource} propagates network/timeout errors and permits a later successful request`, async () => {
      vi.mocked(api.get).mockRejectedValueOnce(new Error('Timed out')).mockResolvedValueOnce([valid])
      await expect(packagesApi[resource]()).rejects.toThrow('Timed out')
      await expect(packagesApi[resource]()).resolves.toEqual([valid])
    })
  }
  it.each(['code', 'sportId', 'sportName', 'trainingFormat', 'durationDays', 'sessionCount', 'sessionMinutes', 'priceAmount'])('rejects missing mandatory package %s', async (key) => {
    const row: Record<string, unknown> = { ...sportPackage() }
    delete row[key]
    vi.mocked(api.get).mockResolvedValueOnce([row])
    await expect(packagesApi.packages()).rejects.toThrow('response')
  })
  it.each([null, '', ' ', 'NaN', '0x10', -1, Infinity, {}, true])('does not replace corrupt price %j with zero', async (priceAmount) => {
    vi.mocked(api.get).mockResolvedValueOnce([{ ...sportPackage(), priceAmount }])
    await expect(packagesApi.packages()).rejects.toThrow('response')
  })
  it('accepts decimal prices, zero prices and nullable display metadata', async () => {
    const rows = [sportPackage({ priceAmount: '12.50', description: null, sportCode: null }), sportPackage({ id: 9, priceAmount: 0 })]
    vi.mocked(api.get).mockResolvedValueOnce(rows)
    await expect(packagesApi.packages()).resolves.toEqual(rows)
  })
  it('rejects an array masquerading as a training-format string', async () => {
    vi.mocked(api.get).mockResolvedValueOnce([{ ...sportPackage(), trainingFormat: ['COACH_LED'] }])
    await expect(packagesApi.packages()).rejects.toThrow('response')
  })

  for (const trainingFormat of ['SELF_TRAINING', 'COACH_LED']) {
    for (const field of ['sessionCount', 'sessionMinutes']) {
      it.each([null, undefined, 0, -1, 1.5, '8', true, [], {}, NaN, Infinity])(`DEF-001 contract review ${trainingFormat} rejects invalid ${field} %j`, async (invalid) => {
        const row = { ...sportPackage({ trainingFormat }), [field]: invalid }
        vi.mocked(api.get).mockResolvedValueOnce([row])
        await expect(packagesApi.packages()).rejects.toThrow('response')
      })
    }
  }
  it.each(['SELF_TRAINING', 'COACH_LED'])('DEF-001 contract review preserves finite session counts and 60/90 minutes for %s', async (trainingFormat) => {
    const rows = [sportPackage({ trainingFormat, sessionCount: 1, sessionMinutes: 60 }), sportPackage({ id: 9, trainingFormat, sessionCount: 1000, sessionMinutes: 90 })]
    vi.mocked(api.get).mockResolvedValueOnce(rows)
    await expect(packagesApi.packages()).resolves.toEqual(rows)
  })
})
