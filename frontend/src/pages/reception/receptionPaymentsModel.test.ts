import { describe, expect, it } from 'vitest'
import { ApiError, apiErrorFromBody } from '../../api/errors'
import { toRefundQuote, type Refund } from '../../api/receptionPaymentsApi'
import { card, registration } from '../../test/memberFixtures'
import {
  amountCheck,
  amountDifference,
  apiStatus,
  canConfirm,
  findPaidItem,
  initialConfirmForm,
  isAmountMismatch,
  isAmountToReview,
  memberRefundHistory,
  mismatchAmounts,
  paymentStatusLabel,
  paymentStatusTone,
  pendingTotal,
  readPaymentListParams,
  refundableRegistrations,
  refundAmount,
  refundDetailPath,
  refundOriginal,
  refundStage,
  refundStatusLabel,
  sortRefunds,
  toConfirmRequest,
  validateConfirmForm,
  validateRefundForm,
  validateRefundReference,
  validAmountDue,
  writePaymentListParams,
  type ConfirmForm,
} from './receptionPaymentsModel'

const DUE = 810000

const VALID_TRANSFER: ConfirmForm = {
  method: 'BANK_TRANSFER',
  reference: 'BANK-1041-20261002',
  amountReceived: '810.000',
  note: '',
  verified: true,
}

describe('FE-12 receptionPaymentsModel - payment list', () => {
  it('TC-FE12-001 list parameters default to PENDING, page 0 and ignore unknown values', () => {
    expect(readPaymentListParams(new URLSearchParams())).toEqual({ status: 'PENDING', query: '', page: 0 })
    expect(readPaymentListParams(new URLSearchParams('status=paid&q=%20REG-1041%20&page=2'))).toEqual({
      status: 'PENDING',
      query: 'REG-1041',
      page: 2,
    })
    expect(readPaymentListParams(new URLSearchParams('status=success&page=-1')).status).toBe('SUCCESS')
    expect(readPaymentListParams(new URLSearchParams('page=abc')).page).toBe(0)
    expect(writePaymentListParams({ status: 'PENDING', query: '', page: 0 }).toString()).toBe('')
    expect(writePaymentListParams({ status: 'ALL', query: 'Taylor', page: 1 }).toString()).toBe('status=ALL&q=Taylor&page=1')
  })

  it('TC-FE12-002 status helpers: ALL sends no status, SUCCESS reads Paid with the PAID tone', () => {
    expect(apiStatus('ALL')).toBe('')
    expect(apiStatus('FAILED')).toBe('FAILED')
    expect(paymentStatusLabel('SUCCESS')).toBe('Paid')
    expect(paymentStatusLabel('PENDING')).toBe('Pending payment')
    expect(paymentStatusTone('SUCCESS')).toBe('PAID')
    expect(paymentStatusTone('FAILED')).toBe('FAILED')
    expect(pendingTotal([
      { status: 'PENDING', amount: 810000 },
      { status: 'PENDING', amount: '500000' },
      { status: 'SUCCESS', amount: 300000 },
    ])).toBe(1310000)
  })
})

describe('FE-12 receptionPaymentsModel - verify and confirm', () => {
  it('TC-FE12-003 a transfer needs method, reference and the exact amount due', () => {
    expect(validateConfirmForm(VALID_TRANSFER, DUE)).toEqual({})
    const empty = initialConfirmForm('BANK_TRANSFER')
    expect(validateConfirmForm(empty, DUE)).toEqual({
      reference: 'Reference code is required for this method',
      amountReceived: 'Amount received is required',
    })
    expect(validateConfirmForm({ ...VALID_TRANSFER, amountReceived: '810000.50' }, DUE).amountReceived).toMatch(/whole number/)
    expect(validateConfirmForm({ ...VALID_TRANSFER, note: 'x'.repeat(501) }, DUE).note).toMatch(/at most 500/)
    expect(validateConfirmForm({ ...VALID_TRANSFER, reference: 'x'.repeat(101) }, DUE).reference).toMatch(/at most 100/)
    expect(validateConfirmForm({ ...VALID_TRANSFER, method: 'CHEQUE' }, DUE).method).toBe('Choose a payment method')
  })

  it('TC-FE12-004 cash needs no reference; Confirm also needs the checkbox', () => {
    const cash: ConfirmForm = { ...VALID_TRANSFER, method: 'CASH', reference: '' }
    expect(validateConfirmForm(cash, DUE)).toEqual({})
    expect(canConfirm(cash, DUE)).toBe(true)
    expect(canConfirm({ ...cash, verified: false }, DUE)).toBe(false)
    expect(canConfirm({ ...VALID_TRANSFER, reference: '  ' }, DUE)).toBe(false)
    expect(initialConfirmForm('MOMO').method).toBe('MOMO')
    expect(initialConfirmForm(null).method).toBe('BANK_TRANSFER')
  })

  it('TC-FE12-015 amount rule of the FE-05 desk sale: non-cash exact, cash equal or above', () => {
    const shortTransfer = { ...VALID_TRANSFER, amountReceived: '800000' }
    const overTransfer = { ...VALID_TRANSFER, amountReceived: '900000' }
    expect(validateConfirmForm(shortTransfer, DUE).amountReceived).toBe('Amount received is less than the amount payable')
    expect(validateConfirmForm(overTransfer, DUE).amountReceived).toBe('Amount received must equal the amount payable for this method')
    expect(canConfirm(shortTransfer, DUE)).toBe(false)
    expect(canConfirm(overTransfer, DUE)).toBe(false)
    expect(isAmountToReview(shortTransfer, DUE)).toBe(true)
    expect(isAmountToReview(overTransfer, DUE)).toBe(true)

    const cashOver: ConfirmForm = { ...VALID_TRANSFER, method: 'CASH', reference: '', amountReceived: '1.000.000' }
    expect(canConfirm(cashOver, DUE)).toBe(true)
    expect(isAmountToReview(cashOver, DUE)).toBe(false)
    expect(toConfirmRequest(cashOver).amountReceived).toBe(1000000)
    const cashShort = { ...cashOver, amountReceived: '800000' }
    expect(canConfirm(cashShort, DUE)).toBe(false)
    expect(isAmountToReview(cashShort, DUE)).toBe(true)
    expect(isAmountToReview({ ...VALID_TRANSFER }, DUE)).toBe(false)
  })

  it('TC-FE12-016 an amount due that is not a number blocks every action and never yields NaN', () => {
    expect(validAmountDue('810000.00')).toBe(810000)
    expect(validAmountDue(810000)).toBe(810000)
    expect(validAmountDue('abc')).toBeNull()
    expect(validAmountDue(null)).toBeNull()
    expect(validAmountDue(-1)).toBeNull()
    expect(validateConfirmForm(VALID_TRANSFER, null).amountReceived).toMatch(/amount due of this payment is not valid/)
    expect(canConfirm(VALID_TRANSFER, null)).toBe(false)
    expect(amountCheck(null, '810000')).toBe('empty')
    expect(amountDifference(null, '810000')).toBeNull()
    expect(isAmountToReview(VALID_TRANSFER, null)).toBe(false)
  })

  it('TC-FE12-005 the confirm body trims the text and sends no reference for empty cash', () => {
    expect(toConfirmRequest({ ...VALID_TRANSFER, reference: ' BANK-1 ', note: ' checked ' })).toEqual({
      paymentMethod: 'BANK_TRANSFER',
      paymentReference: 'BANK-1',
      amountReceived: 810000,
      note: 'checked',
    })
    expect(toConfirmRequest({ ...VALID_TRANSFER, method: 'CASH', reference: '' })).toEqual({
      paymentMethod: 'CASH',
      paymentReference: null,
      amountReceived: 810000,
      note: null,
    })
  })

  it('TC-FE12-006 amount check and difference against the amount due', () => {
    expect(amountCheck(810000, '')).toBe('empty')
    expect(amountCheck(810000, '810.000')).toBe('match')
    expect(amountCheck(810000, '800000')).toBe('short')
    expect(amountCheck(810000, '1,000,000')).toBe('over')
    expect(amountDifference(810000, '800000')).toBe(-10000)
    expect(amountDifference(810000, 'abc')).toBeNull()
  })

  it('TC-FE12-007 only a 409 with the PAYMENT_AMOUNT_MISMATCH code is a mismatch', () => {
    expect(isAmountMismatch(new ApiError(409, 'Amount does not match', {}, 'PAYMENT_AMOUNT_MISMATCH'))).toBe(true)
    expect(isAmountMismatch(new ApiError(409, 'PAYMENT_AMOUNT_MISMATCH: received 800000'))).toBe(false)
    expect(isAmountMismatch(new ApiError(409, 'The resource was modified by another request.'))).toBe(false)
    expect(isAmountMismatch(new ApiError(400, 'x', {}, 'PAYMENT_AMOUNT_MISMATCH'))).toBe(false)
    expect(isAmountMismatch(new Error('PAYMENT_AMOUNT_MISMATCH'))).toBe(false)
  })

  it('TC-FE12-008 mismatch amounts use the backend details when present, the typed amount otherwise', () => {
    expect(mismatchAmounts(810000, '800000')).toEqual({ amountDue: 810000, amountReceived: 800000, outstanding: 10000 })
    const error = apiErrorFromBody(409, {
      message: 'Amount received does not match',
      code: 'PAYMENT_AMOUNT_MISMATCH',
      details: { amountDue: 810000, amountReceived: '790000' },
    })
    expect(error.code).toBe('PAYMENT_AMOUNT_MISMATCH')
    expect(mismatchAmounts(810000, '800000', error)).toEqual({ amountDue: 810000, amountReceived: 790000, outstanding: 20000 })
    expect(mismatchAmounts(810000, '900000').outstanding).toBe(-90000)
  })

  it('TC-FE12-009 the paid item is the registration or card that carries the payment id', () => {
    const paidPackage = registration({ id: 11, paymentId: 41 })
    const paidCard = card({ id: 5, paymentId: 45 })
    expect(findPaidItem(41, [paidCard], [paidPackage])).toEqual({ kind: 'package', item: paidPackage })
    expect(findPaidItem(45, [paidCard], [paidPackage])).toEqual({ kind: 'card', item: paidCard })
    expect(findPaidItem(99, [paidCard], [paidPackage])).toBeNull()
  })
})

const REFUND: Refund = {
  id: 7,
  refundCode: 'RF-1040',
  packageRegistrationId: 11,
  registrationCode: 'REG-1040',
  memberId: 2,
  memberCode: 'MB-0246',
  memberFullName: 'Taylor Nguyen',
  amountRequested: 150000,
  amountApproved: null,
  reason: 'Cancelled before 24-hour deadline',
  status: 'PENDING',
  createdAt: '2026-10-03T10:00:00',
}

describe('FE-12 receptionPaymentsModel - refunds', () => {
  it('TC-FE12-010 refund amount is required, whole, above 0 and within the refundable balance', () => {
    expect(validateRefundForm({ amount: '150.000', reason: 'Cancelled' }, 150000)).toEqual({})
    expect(validateRefundForm({ amount: '', reason: '' }, 150000)).toEqual({
      amount: 'Refund amount is required',
      reason: 'Reason is required',
    })
    expect(validateRefundForm({ amount: '150001', reason: 'x' }, 150000).amount).toBe(
      'Refund amount is more than the refundable balance',
    )
    expect(validateRefundForm({ amount: '0', reason: 'x' }, 150000).amount).toMatch(/above 0/)
    expect(validateRefundForm({ amount: '1', reason: 'x'.repeat(501) }, 150000).reason).toMatch(/at most 500/)
  })

  it('TC-FE12-011 refund status maps to the Figma states and labels', () => {
    expect(refundStage('PENDING')).toBe('submitted')
    expect(refundStage('APPROVED')).toBe('approved')
    expect(refundStage('COMPLETED')).toBe('done')
    expect(refundStage('REJECTED')).toBe('rejected')
    expect(refundStage('UNKNOWN')).toBe('other')
    expect(refundStatusLabel('PENDING')).toBe('Pending approval')
    expect(refundStatusLabel('APPROVED')).toBe('Approved - not yet refunded')
    expect(refundOriginal({ ...REFUND, invoiceNumber: 'INV-1040' })).toBe('INV-1040')
    expect(refundOriginal({ ...REFUND, paymentCode: 'PAY-0040' })).toBe('PAY-0040')
    expect(refundOriginal(REFUND)).toBe('REG-1040')
    expect(refundAmount({ amountApproved: 120000, amountRequested: 150000 })).toBe(120000)
    expect(refundAmount(REFUND)).toBe(150000)
    expect(refundDetailPath(7, 2)).toBe('/reception/refunds/7?memberId=2')
    expect(refundDetailPath(7)).toBe('/reception/refunds/7')
  })

  it('TC-FE12-012 reference rules, refundable registrations and newest-first order', () => {
    expect(validateRefundReference('  ')).toBe('Refund transaction reference is required')
    expect(validateRefundReference('x'.repeat(101))).toMatch(/at most 100/)
    expect(validateRefundReference('REF-1040-20261003')).toBeNull()
    const rows = [
      registration({ id: 1, status: 'ACTIVE', paymentId: 41 }),
      registration({ id: 2, status: 'PENDING_PAYMENT', paymentId: 42 }),
      registration({ id: 3, status: 'EXPIRED', paymentId: 43 }),
      registration({ id: 4, status: 'ACTIVE', paymentId: null }),
    ]
    expect(refundableRegistrations(rows).map((row) => row.id)).toEqual([1, 3])
    const older = { ...REFUND, id: 1, createdAt: '2026-10-01T10:00:00' }
    expect(sortRefunds([older, REFUND]).map((row) => row.id)).toEqual([7, 1])
    const approved = { ...REFUND, id: 3, status: 'APPROVED' }
    expect(memberRefundHistory([REFUND, older, approved]).map((row) => row.id)).toEqual([3])
  })

  it('TC-FE12-013 the refund quote reader fills defaults and refuses a quote without an amount', () => {
    const quote = toRefundQuote({ registrationId: 11, packageName: 'Football - 30 days', suggestedAmount: '150000.00' })
    expect(quote.suggestedAmount).toBe(150000)
    expect(quote.maxRefundable).toBe(150000)
    expect(quote.eligible).toBe(true)
    expect(quote.serviceName).toBe('Football - 30 days')
    expect(toRefundQuote({ registrationId: 11, suggestedAmount: 100, maxRefundable: 200, eligible: false }).eligible).toBe(false)
    expect(() => toRefundQuote({ registrationId: 11 })).toThrow()
    expect(() => toRefundQuote({ suggestedAmount: 100 })).toThrow()
  })

  it('TC-FE12-014 ApiError keeps the code and raw details of the body', () => {
    const error = apiErrorFromBody(400, { message: 'Validation Error', details: { paymentReference: 'Reference is required' } })
    expect(error.code).toBeNull()
    expect(error.fieldErrors).toEqual({ paymentReference: 'Reference is required' })
    expect(error.details).toEqual({ paymentReference: 'Reference is required' })
  })
})
