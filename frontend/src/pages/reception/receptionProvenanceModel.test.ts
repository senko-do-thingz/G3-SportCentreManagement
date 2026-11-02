import { describe, expect, it } from 'vitest'
import { classifyProvenance, reconcileProvenance } from './receptionProvenanceModel'

describe('receptionProvenanceModel - classifyProvenance', () => {
  it('handles null and undefined input safely', () => {
    const res = classifyProvenance(null)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.statusBadgeLabel).toBe('Unavailable')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.isVerifiedActual).toBe(false)

    const resUndef = classifyProvenance(undefined)
    expect(resUndef.classification).toBe('INCONSISTENT')
    expect(resUndef.canPrintReceipt).toBe(false)
  })

  it('classifies valid SIMULATED settlement with positive txId truthfully', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    })
    expect(res.classification).toBe('VALID_SIMULATED')
    expect(res.statusBadgeLabel).toBe('Paid - Simulated')
    expect(res.statusBadgeTone).toBe('success')
    expect(res.provenanceLabel).toBe('SIMULATED (SPORTIFY_SIMULATOR)')
    expect(res.isSimulatedSuccess).toBe(true)
    expect(res.isVerifiedActual).toBe(false)
    expect(res.canPrintReceipt).toBe(true)
    expect(res.receiptHeroDescription).toContain('Simulated payment record')
    expect(res.receiptWarningMessage).toContain('SIMULATED SETTLEMENT')
    expect(res.receiptWarningTone).toBe('warning')
  })

  it('rejects SIMULATED with undefined or null simulatedTransactionId (D03-04)', () => {
    const resUndef = classifyProvenance({
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: undefined,
    })
    expect(resUndef.classification).toBe('INCONSISTENT')
    expect(resUndef.canPrintReceipt).toBe(false)

    const resNull = classifyProvenance({
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: null,
    })
    expect(resNull.classification).toBe('INCONSISTENT')
    expect(resNull.canPrintReceipt).toBe(false)

    const resZero = classifyProvenance({
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 0,
    })
    expect(resZero.classification).toBe('INCONSISTENT')
    expect(resZero.canPrintReceipt).toBe(false)
  })

  it('classifies verified ACTUAL settlement truthfully', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: 'ACTUAL',
      settlementProvider: 'MANUAL_TRANSFER',
      simulatedTransactionId: null,
    })
    expect(res.classification).toBe('ACTUAL')
    expect(res.statusBadgeLabel).toBe('Paid')
    expect(res.statusBadgeTone).toBe('success')
    expect(res.provenanceLabel).toBe('ACTUAL (MANUAL_TRANSFER)')
    expect(res.isVerifiedActual).toBe(true)
    expect(res.isSimulatedSuccess).toBe(false)
    expect(res.canPrintReceipt).toBe(true)
    expect(res.receiptHeroDescription).toBe('Confirmed payment record.')
    expect(res.receiptWarningMessage).toBeUndefined()
  })

  it('classifies legacy payment with null settlementType as LEGACY_UNVERIFIED', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: null,
      settlementProvider: null,
      simulatedTransactionId: null,
    })
    expect(res.classification).toBe('LEGACY_UNVERIFIED')
    expect(res.statusBadgeLabel).toBe('Paid - Legacy unverified')
    expect(res.statusBadgeTone).toBe('neutral')
    expect(res.provenanceLabel).toBe('Legacy unverified')
    expect(res.isLegacyUnverified).toBe(true)
    expect(res.isVerifiedActual).toBe(false)
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptHeroDescription).toBe(
      'Historical payment record without verified settlement provenance.',
    )
    expect(res.receiptWarningMessage).toContain('LEGACY UNVERIFIED')
    expect(res.receiptWarningTone).toBe('warning')
  })

  it('classifies payment with UNKNOWN settlementType as LEGACY_UNVERIFIED', () => {
    const res = classifyProvenance({
      status: 'SUCCESS',
      settlementType: 'UNKNOWN',
      settlementProvider: null,
      simulatedTransactionId: null,
    })
    expect(res.classification).toBe('LEGACY_UNVERIFIED')
    expect(res.statusBadgeLabel).toBe('Paid - Legacy unverified')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.isLegacyUnverified).toBe(true)
    expect(res.receiptWarningMessage).toContain('LEGACY UNVERIFIED')
  })

  it('rejects SIMULATED with null provider as INCONSISTENT', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: null,
      simulatedTransactionId: 9001,
    })
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
    expect(res.statusBadgeTone).toBe('warning')
    expect(res.isInconsistent).toBe(true)
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('INCONSISTENT PROVENANCE')
  })

  it('rejects ACTUAL with SPORTIFY_SIMULATOR provider as INCONSISTENT', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: 'ACTUAL',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: null,
    })
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('rejects null settlementType with simulatedTransactionId as INCONSISTENT', () => {
    const res = classifyProvenance({
      status: 'PAID',
      settlementType: null,
      settlementProvider: null,
      simulatedTransactionId: 101,
    })
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('handles REFUNDED status with verified ACTUAL settlement allowing receipt printing', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    })
    expect(res.classification).toBe('REFUNDED')
    expect(res.statusBadgeLabel).toBe('Refunded')
    expect(res.provenanceLabel).toBe('ACTUAL (DESK_CASH)')
    expect(res.isVerifiedActual).toBe(true)
    expect(res.canPrintReceipt).toBe(true)
    expect(res.receiptHeroDescription).toBe('This payment was refunded to the member.')
  })

  it('handles REFUNDED status with verified SIMULATED settlement allowing receipt printing with warning', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9002,
    })
    expect(res.classification).toBe('REFUNDED')
    expect(res.statusBadgeLabel).toBe('Refunded')
    expect(res.provenanceLabel).toBe('SIMULATED (SPORTIFY_SIMULATOR)')
    expect(res.isSimulatedSuccess).toBe(true)
    expect(res.canPrintReceipt).toBe(true)
    expect(res.receiptWarningMessage).toContain('SIMULATED SETTLEMENT')
  })

  it('blocks receipt printing for REFUNDED status with NULL settlement provenance (D03-01)', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: null,
      settlementProvider: null,
    })
    expect(res.classification).toBe('LEGACY_UNVERIFIED')
    expect(res.statusBadgeLabel).toBe('Refunded - Legacy unverified')
    expect(res.provenanceLabel).toBe('Legacy unverified')
    expect(res.isLegacyUnverified).toBe(true)
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('LEGACY UNVERIFIED')
  })

  it('blocks receipt printing for REFUNDED status with UNKNOWN settlement provenance (D03-01)', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: 'UNKNOWN',
      settlementProvider: null,
    })
    expect(res.classification).toBe('LEGACY_UNVERIFIED')
    expect(res.statusBadgeLabel).toBe('Refunded - Legacy unverified')
    expect(res.provenanceLabel).toBe('Legacy unverified')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('LEGACY UNVERIFIED')
  })

  it('blocks receipt printing for REFUNDED status with contradictory provenance (D03-01)', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: 'ACTUAL',
      settlementProvider: 'SPORTIFY_SIMULATOR',
    })
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.statusBadgeLabel).toBe('Refunded - Inconsistent provenance')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('INCONSISTENT PROVENANCE')
  })

  it('handles PENDING status safely preventing receipt printing', () => {
    const res = classifyProvenance({
      status: 'PENDING',
    })
    expect(res.classification).toBe('PENDING')
    expect(res.statusBadgeLabel).toBe('Pending payment')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('handles FAILED status safely preventing receipt printing', () => {
    const res = classifyProvenance({
      status: 'FAILED',
    })
    expect(res.classification).toBe('FAILED')
    expect(res.statusBadgeLabel).toBe('Failed')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('handles CANCELLED status safely preventing receipt printing', () => {
    const res = classifyProvenance({
      status: 'CANCELLED',
    })
    expect(res.classification).toBe('CANCELLED')
    expect(res.statusBadgeLabel).toBe('Canceled')
    expect(res.canPrintReceipt).toBe(false)
  })
})

describe('receptionProvenanceModel - reconcileProvenance (D03-02)', () => {
  it('disables printing when invoice is absent or loading', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const res = reconcileProvenance(payment, null)
    expect(res.canPrintReceipt).toBe(false)
    expect(res.provenanceLabel).toBe('ACTUAL (DESK_CASH)')
  })

  it('rejects invoice with NULL provenance even if payment asserts ACTUAL (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const invoice = {
      status: 'PAID',
      settlementType: null,
      settlementProvider: null,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('disagree on settlement provenance')
  })

  it('rejects invoice with UNKNOWN provenance even if payment asserts ACTUAL (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'UNKNOWN',
      settlementProvider: null,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('rejects cross-source contradiction: invoice ACTUAL vs payment SIMULATED (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
  })

  it('rejects cross-source contradiction: invoice SIMULATED vs payment ACTUAL (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
  })

  it('rejects conflicting settlement providers within ACTUAL (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'ACTUAL',
      settlementProvider: 'MANUAL_TRANSFER',
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('conflicting settlement providers')
  })

  it('rejects conflicting simulatedTransactionId within SIMULATED (D03-02)', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9002,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('INCONSISTENT')
    expect(res.canPrintReceipt).toBe(false)
    expect(res.receiptWarningMessage).toContain('conflicting simulated transaction identifiers')
  })

  it('allows receipt printing for consistent verified ACTUAL records', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'ACTUAL',
      settlementProvider: 'DESK_CASH',
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('ACTUAL')
    expect(res.statusBadgeLabel).toBe('Paid')
    expect(res.provenanceLabel).toBe('ACTUAL (DESK_CASH)')
    expect(res.isVerifiedActual).toBe(true)
    expect(res.canPrintReceipt).toBe(true)
  })

  it('allows receipt printing for consistent verified SIMULATED records', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    }
    const invoice = {
      status: 'PAID',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 9001,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('VALID_SIMULATED')
    expect(res.statusBadgeLabel).toBe('Paid - Simulated')
    expect(res.provenanceLabel).toBe('SIMULATED (SPORTIFY_SIMULATOR)')
    expect(res.isSimulatedSuccess).toBe(true)
    expect(res.canPrintReceipt).toBe(true)
  })

  it('classifies consistent legacy NULL records as LEGACY_UNVERIFIED and blocks printing', () => {
    const payment = {
      status: 'SUCCESS',
      settlementType: null,
      settlementProvider: null,
    }
    const invoice = {
      status: 'PAID',
      settlementType: null,
      settlementProvider: null,
    }
    const res = reconcileProvenance(payment, invoice)
    expect(res.classification).toBe('LEGACY_UNVERIFIED')
    expect(res.statusBadgeLabel).toBe('Paid - Legacy unverified')
    expect(res.canPrintReceipt).toBe(false)
  })
})
