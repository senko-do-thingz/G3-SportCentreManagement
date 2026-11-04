import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'
import { localDemoPaymentApi } from './localDemoPaymentApi'

describe('localDemoPaymentApi', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('capability returns true when contract matches enabled local demo', async () => {
    vi.spyOn(api, 'get').mockResolvedValueOnce({
      contractVersion: 1,
      mode: 'LOCAL_DEMO',
      enabled: true,
    })

    const result = await localDemoPaymentApi.capability()
    expect(result).toBe(true)
    expect(api.get).toHaveBeenCalledWith('/payments/local-demo/capability', { signal: undefined })
  })

  it('capability returns false when contract is disabled', async () => {
    vi.spyOn(api, 'get').mockResolvedValueOnce({
      contractVersion: 1,
      mode: 'LOCAL_DEMO',
      enabled: false,
    })

    const result = await localDemoPaymentApi.capability()
    expect(result).toBe(false)
  })

  it('capability returns false on 404 or network error', async () => {
    vi.spyOn(api, 'get').mockRejectedValueOnce(new Error('Network error'))

    const result = await localDemoPaymentApi.capability()
    expect(result).toBe(false)
  })

  it('confirm calls demo-confirm endpoint and returns result', async () => {
    const mockResponse = {
      paymentId: 52,
      status: 'SUCCESS' as const,
      settlementType: 'SIMULATED' as const,
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 101,
      referenceCode: 'SIM-QR-52-TEST',
      invoiceId: 201,
      invoiceNumber: 'INV-2026-00201',
    }
    vi.spyOn(api, 'post').mockResolvedValueOnce(mockResponse)

    const result = await localDemoPaymentApi.confirm(52)
    expect(result).toEqual(mockResponse)
    expect(api.post).toHaveBeenCalledWith('/payments/52/demo-confirm', {})
  })

  it('confirm rejects invalid payment identifiers', async () => {
    await expect(localDemoPaymentApi.confirm(0)).rejects.toThrow('Invalid payment identifier')
    await expect(localDemoPaymentApi.confirm(-1)).rejects.toThrow('Invalid payment identifier')
  })
})
