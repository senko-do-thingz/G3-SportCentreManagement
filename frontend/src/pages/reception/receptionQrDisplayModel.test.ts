import { describe, expect, it } from 'vitest'
import {
  classifyFinancialPresentation,
  mergePolledProjection,
  resolveBankSession,
} from './receptionQrDisplayModel'
import type { ReceptionActiveSession, ReceptionDeskProjection } from '../../api/demoQrApi'

describe('receptionQrDisplayModel', () => {
  describe('classifyFinancialPresentation (QR06-R1-01)', () => {
    it('handles null/undefined projection safely', () => {
      const res = classifyFinancialPresentation(null)
      expect(res.classification).toBe('INCONSISTENT')
      expect(res.statusBadgeLabel).toBe('Unavailable')
      expect(res.isTerminal).toBe(false)
      expect(res.isPending).toBe(false)
    })

    it('classifies valid SIMULATED payment with genuine transaction ID as Paid - Simulated', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'SIMULATED',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        simulatedTransactionId: 9001,
      })
      expect(res.classification).toBe('VALID_SIMULATED')
      expect(res.statusBadgeLabel).toBe('Paid - Simulated')
      expect(res.statusBadgeTone).toBe('success')
      expect(res.isSimulatedSuccess).toBe(true)
      expect(res.isTerminal).toBe(true)
      expect(res.isPending).toBe(false)
      expect(res.bannerTone).toBe('success')
      expect(res.bannerMessage).toContain('SIMULATED (SPORTIFY_SIMULATOR)')
      // Invariant: NEVER assert ungrounded entitlement activation
      expect(res.bannerMessage).not.toContain('Entitlement is active')
    })

    it('classifies ACTUAL payment with actual provider as Paid - Actual', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'ACTUAL',
        settlementProvider: 'DESK_CASH',
        simulatedTransactionId: null,
      })
      expect(res.classification).toBe('ACTUAL')
      expect(res.statusBadgeLabel).toBe('Paid - Actual')
      expect(res.statusBadgeTone).toBe('success')
      expect(res.isSimulatedSuccess).toBe(false)
      expect(res.isTerminal).toBe(true)
      expect(res.bannerMessage).toContain('actual payment provider')
    })

    it('classifies legacy payment with NULL provenance as Paid - Legacy unverified', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: null,
        settlementProvider: null,
        simulatedTransactionId: null,
      })
      expect(res.classification).toBe('LEGACY_UNVERIFIED')
      expect(res.statusBadgeLabel).toBe('Paid - Legacy unverified')
      expect(res.statusBadgeTone).toBe('neutral')
      expect(res.isSimulatedSuccess).toBe(false)
      expect(res.bannerMessage).toContain('Legacy unverified')
    })

    it('rejects SIMULATED type with NULL provider as Inconsistent provenance', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'SIMULATED',
        settlementProvider: null,
        simulatedTransactionId: 9001,
      })
      expect(res.classification).toBe('INCONSISTENT')
      expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
      expect(res.isSimulatedSuccess).toBe(false)
    })

    it('rejects ACTUAL type with SPORTIFY_SIMULATOR provider as Inconsistent provenance', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'ACTUAL',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        simulatedTransactionId: null,
      })
      expect(res.classification).toBe('INCONSISTENT')
      expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
      expect(res.isSimulatedSuccess).toBe(false)
    })

    it('rejects SIMULATED type with missing simulatedTransactionId as Inconsistent provenance', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'SIMULATED',
        settlementProvider: 'SPORTIFY_SIMULATOR',
        simulatedTransactionId: null,
      })
      expect(res.classification).toBe('INCONSISTENT')
      expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
      expect(res.isSimulatedSuccess).toBe(false)
    })

    it('rejects UNKNOWN settlement type as Inconsistent provenance', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'SUCCESS',
        settlementType: 'CRYPTO_TOKEN',
        settlementProvider: 'UNKNOWN_PROVIDER',
        simulatedTransactionId: null,
      })
      expect(res.classification).toBe('INCONSISTENT')
      expect(res.statusBadgeLabel).toBe('Paid - Inconsistent provenance')
      expect(res.isSimulatedSuccess).toBe(false)
    })

    it('classifies PENDING payment accurately without terminal flag', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'PENDING',
        settlementType: null,
        settlementProvider: null,
        simulatedTransactionId: null,
      })
      expect(res.statusBadgeLabel).toBe('Pending payment')
      expect(res.statusBadgeTone).toBe('warning')
      expect(res.isPending).toBe(true)
      expect(res.isTerminal).toBe(false)
    })

    it('classifies FAILED payment accurately as terminal', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'FAILED',
        settlementType: null,
        settlementProvider: null,
        simulatedTransactionId: null,
      })
      expect(res.statusBadgeLabel).toBe('Failed')
      expect(res.statusBadgeTone).toBe('danger')
      expect(res.isPending).toBe(false)
      expect(res.isTerminal).toBe(true)
      expect(res.bannerMessage).toContain('Desk QR is inactive')
    })

    it('classifies CANCELED payment accurately as terminal', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'CANCELED',
        settlementType: null,
        settlementProvider: null,
        simulatedTransactionId: null,
      })
      expect(res.statusBadgeLabel).toBe('Canceled')
      expect(res.statusBadgeTone).toBe('neutral')
      expect(res.isPending).toBe(false)
      expect(res.isTerminal).toBe(true)
      expect(res.bannerMessage).toContain('Desk QR is inactive')
    })

    it('classifies REFUNDED payment accurately as terminal', () => {
      const res = classifyFinancialPresentation({
        paymentStatus: 'REFUNDED',
        settlementType: null,
        settlementProvider: null,
        simulatedTransactionId: null,
      })
      expect(res.statusBadgeLabel).toBe('Refunded')
      expect(res.statusBadgeTone).toBe('neutral')
      expect(res.isPending).toBe(false)
      expect(res.isTerminal).toBe(true)
      expect(res.bannerMessage).toContain('Desk QR is inactive')
    })
  })

  describe('resolveBankSession (QR06-R1-03)', () => {
    const activePendingSession: ReceptionActiveSession = {
      sessionId: '00000000-0000-4000-8000-000000000001',
      bankCode: 'TECHCOMBANK_DEMO',
      bankLabel: 'Techcombank (demo)',
      state: 'PENDING',
      createdAt: '2026-10-30T14:00:00Z',
      expiresAt: '2026-10-30T14:10:00Z',
      serverTime: '2026-10-30T14:02:00Z',
    }

    it('locks to active member session bank when session is PENDING', () => {
      const res = resolveBankSession(activePendingSession, 'vietcombank')
      expect(res.isLockedToActiveSession).toBe(true)
      expect(res.effectiveBank).toBe('techcombank')
      expect(res.sessionBankLabel).toBe('Techcombank (demo)')
      expect(res.isSessionActive).toBe(true)
      expect(res.isSessionExpired).toBe(false)
      expect(res.statusNotice).toContain('Bank locked to customer active session')
    })

    it('uses preview bank with informational notice when no active session exists', () => {
      const res = resolveBankSession(null, 'bidv')
      expect(res.isLockedToActiveSession).toBe(false)
      expect(res.effectiveBank).toBe('bidv')
      expect(res.sessionBankLabel).toBe('No active session')
      expect(res.isSessionActive).toBe(false)
      expect(res.isSessionExpired).toBe(false)
      expect(res.statusNotice).toContain('Informational preview only')
    })

    it('uses preview bank with expired notice when session is EXPIRED', () => {
      const expiredSession: ReceptionActiveSession = {
        ...activePendingSession,
        state: 'EXPIRED',
      }
      const res = resolveBankSession(expiredSession, 'mb')
      expect(res.isLockedToActiveSession).toBe(false)
      expect(res.effectiveBank).toBe('mb')
      expect(res.isSessionActive).toBe(false)
      expect(res.isSessionExpired).toBe(true)
      expect(res.statusNotice).toContain('Active member simulation session has expired')
    })

    it('handles unsupported or unknown bank code safely', () => {
      const unknownBankSession: ReceptionActiveSession = {
        ...activePendingSession,
        bankCode: 'CUSTOM_UNKNOWN_BANK',
        bankLabel: 'Custom Bank',
      }
      const res = resolveBankSession(unknownBankSession, 'vietcombank')
      expect(res.isLockedToActiveSession).toBe(true)
      // Normalizes unknown bank to default demo bank
      expect(res.effectiveBank).toBe('vietcombank')
      expect(res.sessionBankLabel).toContain('Unknown bank')
    })
  })

  describe('mergePolledProjection (QR06-R1-04)', () => {
    const basePending: ReceptionDeskProjection = {
      paymentId: 101,
      paymentCode: 'PAY-101',
      amount: 500000,
      currency: 'VND',
      paymentStatus: 'PENDING',
      itemDescription: null,
      memberFullName: 'Test Member',
      memberCode: 'MEM-001',
      memberId: 1,
      settlementType: null,
      settlementProvider: null,
      simulatedTransactionId: null,
      activeSession: null,
    }

    const baseSettled: ReceptionDeskProjection = {
      ...basePending,
      paymentStatus: 'SUCCESS',
      settlementType: 'SIMULATED',
      settlementProvider: 'SPORTIFY_SIMULATOR',
      simulatedTransactionId: 1234,
    }

    it('returns incoming projection when current is null', () => {
      const result = mergePolledProjection(null, basePending)
      expect(result).toBe(basePending)
    })

    it('updates from PENDING to SUCCESS normally', () => {
      const result = mergePolledProjection(basePending, baseSettled)
      expect(result.paymentStatus).toBe('SUCCESS')
    })

    it('rejects stale PENDING response after terminal SUCCESS is reached', () => {
      const result = mergePolledProjection(baseSettled, basePending)
      // Must retain terminal SUCCESS
      expect(result.paymentStatus).toBe('SUCCESS')
      expect(result.simulatedTransactionId).toBe(1234)
    })

    it('rejects stale PENDING response after terminal FAILED is reached', () => {
      const baseFailed: ReceptionDeskProjection = {
        ...basePending,
        paymentStatus: 'FAILED',
      }
      const result = mergePolledProjection(baseFailed, basePending)
      expect(result.paymentStatus).toBe('FAILED')
    })
  })
})
