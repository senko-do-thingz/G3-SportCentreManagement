import { describe, expect, it } from 'vitest'
import { classifyProvenance } from './receptionProvenanceModel'

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

  it('classifies valid SIMULATED settlement truthfully', () => {
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

  it('handles REFUNDED status preserving refund receipt printability', () => {
    const res = classifyProvenance({
      status: 'REFUNDED',
      settlementType: 'ACTUAL',
    })
    expect(res.classification).toBe('REFUNDED')
    expect(res.statusBadgeLabel).toBe('Refunded')
    expect(res.canPrintReceipt).toBe(true)
    expect(res.receiptHeroDescription).toBe('This payment was refunded to the member.')
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
