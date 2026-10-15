import { afterEach, describe, expect, it, vi } from 'vitest'
import { receptionPaymentsApi, toRefundQuote } from './receptionPaymentsApi'
import { installFakeFetch, json } from '../test/fakeBackend'
import { initialConfirmForm, toConfirmRequest, validateConfirmForm } from '../pages/reception/receptionPaymentsModel'

afterEach(() => vi.unstubAllGlobals())

describe('FE-12 backend-compatible JSON transport', () => {
  it.each([
    ['CASH', '', '', null, null],
    ['CASH', ' CASH-TEST ', ' checked ', 'CASH-TEST', 'checked'],
    ['BANK_TRANSFER', ' BANK-TEST ', '', 'BANK-TEST', null],
    ['CREDIT_CARD', ' CARD-TEST ', ' checked ', 'CARD-TEST', 'checked'],
  ] as const)('serializes %s aliases accepted by ConfirmPaymentRequest', async (method, reference, note, sentReference, sentNote) => {
    const { calls, fetchMock } = installFakeFetch(() => json(200, { id: 41, code: 'PAY-TEST', status: 'SUCCESS' }))
    const form = { ...initialConfirmForm(method), reference, note, amountReceived: '100.25', verified: true }
    expect(validateConfirmForm(form, 100.25)).toEqual({})
    await receptionPaymentsApi.confirm(41, toConfirmRequest(form))
    expect(calls).toHaveLength(1)
    expect(calls[0]).toMatchObject({ method: 'PUT', path: '/payments/41/confirm', body: {
      paymentMethod: method, paymentReference: sentReference, amountReceived: 100.25, note: sentNote,
    } })
    // Assert the serialized wire body, not a mock of receptionPaymentsApi.confirm.
    expect(JSON.parse(fetchMock.mock.calls[0][1]?.body as string)).toEqual(calls[0].body)
    expect(Object.keys(calls[0].body as object).sort()).toEqual(['amountReceived', 'note', 'paymentMethod', 'paymentReference'])
  })

  it.each(['BANK_TRANSFER', 'CREDIT_CARD'])('blocks %s with missing or blank required reference', (method) => {
    for (const reference of ['', '   ']) {
      const form = { ...initialConfirmForm(method), reference, amountReceived: '100', verified: true }
      expect(validateConfirmForm(form, 100).reference).toBe('Reference code is required for this method')
    }
  })

  it('uses the actual member-refund array and sends completion without a body', async () => {
    const refund = { id: 7, refundCode: 'RF-TEST', status: 'APPROVED', memberId: 2 }
    const { calls, fetchMock } = installFakeFetch((call) => call.method === 'GET'
      ? json(200, [refund])
      : json(200, { ...refund, status: 'COMPLETED', cancelledBookings: 2 }))
    expect(await receptionPaymentsApi.memberRefunds(2)).toEqual([refund])
    expect(await receptionPaymentsApi.completeRefund(7)).toMatchObject({ status: 'COMPLETED', cancelledBookings: 2 })
    expect(calls.map(({ method, path, body }) => ({ method, path, body }))).toEqual([
      { method: 'GET', path: '/refunds/member/2', body: undefined },
      { method: 'PUT', path: '/refunds/7/complete', body: undefined },
    ])
    expect(fetchMock.mock.calls[1][1]?.body).toBeUndefined()
  })

  it('rejects a page-shaped member refund response instead of silently treating it as empty', async () => {
    installFakeFetch(() => json(200, { content: [] }))
    await expect(receptionPaymentsApi.memberRefunds(2)).rejects.toThrow()
  })

  it('reads the real BE-21 quote shape and uses paymentAmount rather than the suggestion as its cap', () => {
    expect(toRefundQuote({
      registrationId: 10, registrationCode: 'REG-TEST', packageName: 'Training', paymentId: 40,
      paymentCode: 'PAY-TEST', paymentMethod: 'CASH', paymentAmount: '150.50', paidAmount: '150.50',
      totalSessions: 3, remainingSessions: 1, upcomingBookings: 0, unusedSessions: 1, usedSessions: 2,
      suggestedAmount: '50.17',
    })).toMatchObject({ suggestedAmount: 50.17, maxRefundable: 150.5, serviceName: 'Training', eligible: true })
  })
})
