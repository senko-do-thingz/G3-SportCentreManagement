import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { managerCardTiersApi } from './managerCardTiersApi'
import { managerPackagesApi } from './managerPackagesApi'
import { previewResponse } from './mock/managerPreview'
import { tokenStorage } from './tokenStorage'
import { installFakeFetch, json } from '../test/fakeBackend'

beforeEach(() => {
  vi.stubEnv('VITE_MANAGER_PREVIEW', 'false')
  tokenStorage.startSession({ accessToken: 'manager-test', refreshToken: 'refresh-test' })
})
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('FE-06 released catalog contracts', () => {
  it('reports malformed list responses instead of rendering a successful empty catalog', async () => {
    installFakeFetch(() => json(200, { content: [] }))
    await expect(managerPackagesApi.list({})).rejects.toThrow('unexpected response')
    await expect(managerCardTiersApi.list()).rejects.toThrow('unexpected response')
  })
  it('uses authenticated manager APIs with preview mode explicitly disabled', async () => {
    const backend = installFakeFetch((call) => json(200, call.method === 'GET' ? [] : { id: 4 }))
    await managerPackagesApi.list({ active: 'true' })
    await managerCardTiersApi.list()
    await managerPackagesApi.update(4, { name: 'Updated package', durationDays: 30, sessionCount: 8, sessionMinutes: 60, price: 550000, active: true })
    await managerCardTiersApi.update(3, { price: 650000, durationMonths: 12, discountPercentage: 10, description: 'Updated tier', active: true })

    expect(backend.calls.map(({ method, path }) => [method, path])).toEqual([
      ['GET', '/manager/packages?active=true'],
      ['GET', '/manager/card-tiers'],
      ['PUT', '/packages/4'],
      ['PUT', '/membership-cards/tiers/3'],
    ])
    expect(backend.calls.every((call) => call.authorization === 'Bearer manager-test')).toBe(true)
  })
  it('maps preview package wire fields back to the published response fields', async () => {
    const response = await previewResponse('/packages/1', { method: 'PUT', body: { name: 'Updated preview package', durationDays: 30, sessionCount: 8, sessionMinutes: 60, price: 550000.25, active: false } }, 'preview-MANAGER')
    expect(await response?.json()).toMatchObject({ name: 'Updated preview package', priceAmount: 550000.25, isActive: false })
    const inactive = await previewResponse('/manager/packages', { query: { active: 'false' } }, 'preview-MANAGER')
    expect(await inactive?.json()).toEqual(expect.arrayContaining([expect.objectContaining({ id: 1, isActive: false })]))
  })
  it('maps preview card active status and keeps server tier-price rejection visible', async () => {
    const invalid = await previewResponse('/membership-cards/tiers/3', { method: 'PUT', body: { price: 200000, durationMonths: 12, discountPercentage: 10, description: '', active: true } }, 'preview-MANAGER')
    expect(invalid?.status).toBe(400)
    const response = await previewResponse('/membership-cards/tiers/3', { method: 'PUT', body: { price: 650000, durationMonths: 12, discountPercentage: 10, description: '', active: false } }, 'preview-MANAGER')
    expect(await response?.json()).toMatchObject({ price: 650000, description: null, isActive: false, tierRank: 2 })
  })
})
