import { describe, expect, it } from 'vitest'
import { ApiError, StaleSessionError } from '../../api/errors'
import { payment, registration } from '../../test/memberFixtures'
import {
  accountNumberToCopy,
  amountToCopy,
  DEFAULT_PAYMENT_METHOD,
  discountPercent,
  filterAndSortPayments,
  isMethodAvailable,
  isPollingStatus,
  isSingleVisitRegistration,
  methodLabel,
  parseIdParam,
  PAYMENT_PATHS,
  paymentStatusLabel,
  paymentStatusToneCode,
  statusFilterFromParam,
  transferContentToCopy,
  validityRange,
} from './paymentsModel'
import { isRetryableError } from './usePaymentPolling'

describe('FE-11 paymentsModel', () => {
  it('TC-FE11-M01 only PENDING keeps polling', () => {
    expect(isPollingStatus('PENDING')).toBe(true)
    expect(isPollingStatus('SUCCESS')).toBe(false)
    expect(isPollingStatus('FAILED')).toBe(false)
    expect(isPollingStatus('REFUNDED')).toBe(false)
    expect(isPollingStatus(null)).toBe(false)
  })

  it('TC-FE11-M02 status labels and badge tones follow Figma (SUCCESS reads Paid, green)', () => {
    expect(paymentStatusLabel('SUCCESS')).toBe('Paid')
    expect(paymentStatusLabel('PENDING')).toBe('Pending')
    expect(paymentStatusLabel(undefined)).toBe('Unknown')
    expect(paymentStatusToneCode('SUCCESS')).toBe('PAID')
    expect(paymentStatusToneCode('OTHER')).toBe('OTHER')
    expect(paymentStatusToneCode(null)).toBeNull()
  })

  it('TC-FE11-M03 method labels', () => {
    expect(methodLabel('BANK_TRANSFER')).toBe('Bank transfer')
    expect(methodLabel('MOMO')).toBe('MoMo')
    expect(methodLabel(null)).toBe('Not chosen')
    expect(methodLabel('NEW_WALLET')).toBe('NEW_WALLET')
  })

  it('TC-FE11-M04 status filter from the URL accepts known statuses in any case only', () => {
    expect(statusFilterFromParam('pending')).toBe('PENDING')
    expect(statusFilterFromParam('SUCCESS')).toBe('SUCCESS')
    expect(statusFilterFromParam('paid')).toBe('')
    expect(statusFilterFromParam(null)).toBe('')
  })

  it('TC-FE11-M05 filter keeps the chosen status and sorts newest first (id breaks ties)', () => {
    const rows = [
      payment({ id: 1, status: 'SUCCESS', createdAt: '2026-10-01T09:00:00' }),
      payment({ id: 2, status: 'PENDING', createdAt: '2026-10-12T09:00:00' }),
      payment({ id: 3, status: 'PENDING', createdAt: '2026-10-12T09:00:00' }),
      payment({ id: 4, status: 'FAILED', createdAt: null }),
    ]
    expect(filterAndSortPayments(rows, '').map((row) => row.id)).toEqual([3, 2, 1, 4])
    expect(filterAndSortPayments(rows, 'PENDING').map((row) => row.id)).toEqual([3, 2])
    expect(filterAndSortPayments(rows, 'REFUNDED')).toEqual([])
    expect(rows.map((row) => row.id)).toEqual([1, 2, 3, 4])
  })

  it('TC-FE11-M06 copy values: account number without spaces, transfer content, whole amount', () => {
    expect(accountNumberToCopy('0000 1041 2460')).toBe('000010412460')
    expect(accountNumberToCopy(null)).toBe('')
    expect(transferContentToCopy(payment())).toBe('PAY-0052')
    expect(transferContentToCopy(payment({ bankTransferInstructions: null }))).toBe('PAY-0052')
    expect(amountToCopy(810000)).toBe('810000')
    expect(amountToCopy('810000.00')).toBe('810000')
    expect(amountToCopy('abc')).toBe('')
  })

  it('TC-FE11-M07 a one day registration is a single visit', () => {
    expect(isSingleVisitRegistration(registration({ startDate: '2026-10-05', endDate: '2026-10-05' }))).toBe(true)
    expect(isSingleVisitRegistration(registration({ startDate: '2026-10-01', endDate: '2026-10-30' }))).toBe(false)
    expect(isSingleVisitRegistration(registration({ startDate: null }))).toBe(false)
    expect(isSingleVisitRegistration(null)).toBe(false)
  })

  it('TC-FE11-M08 discount percent from the amounts', () => {
    expect(discountPercent(900000, 90000)).toBe(10)
    expect(discountPercent('600000', '30000')).toBe(5)
    expect(discountPercent(150000, 0)).toBeNull()
    expect(discountPercent(null, 1000)).toBeNull()
  })

  it('TC-FE11-M09 route ids and paths', () => {
    expect(parseIdParam('52')).toBe(52)
    expect(parseIdParam('0')).toBeNull()
    expect(parseIdParam('5.2')).toBeNull()
    expect(parseIdParam('abc')).toBeNull()
    expect(parseIdParam('1e3')).toBeNull()
    expect(parseIdParam(undefined)).toBeNull()
    expect(PAYMENT_PATHS.status(52)).toBe('/member/payments/52/status')
    expect(PAYMENT_PATHS.receipt(7)).toBe('/member/payments/receipts/7')
    expect(validityRange('2026-10-02', '2026-10-31')).toBe('02 Oct 2026 - 31 Oct 2026')
  })

  it('TC-FE11-M10 only bank transfer can be chosen online; it is the default', () => {
    expect(DEFAULT_PAYMENT_METHOD).toBe('BANK_TRANSFER')
    expect(isMethodAvailable('BANK_TRANSFER')).toBe(true)
    expect(isMethodAvailable('MOMO')).toBe(false)
    expect(isMethodAvailable('VNPAY')).toBe(false)
    expect(isMethodAvailable('ZALOPAY')).toBe(false)
  })

  it('TC-FE11-M11 polling retries only network errors and 5xx', () => {
    expect(isRetryableError(new ApiError(0, 'offline'))).toBe(true)
    expect(isRetryableError(new ApiError(500, 'Server error'))).toBe(true)
    expect(isRetryableError(new ApiError(503, 'Unavailable'))).toBe(true)
    expect(isRetryableError(new ApiError(403, 'Access denied'))).toBe(false)
    expect(isRetryableError(new ApiError(404, 'Not found'))).toBe(false)
    expect(isRetryableError(new StaleSessionError())).toBe(false)
    expect(isRetryableError(new Error('The server returned an unexpected response.'))).toBe(false)
  })
})
