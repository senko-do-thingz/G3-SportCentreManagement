import type { ReceptionDeskProjection } from '../../api/demoQrApi'
import {
  DEFAULT_DEMO_BANK,
  DEMO_BANKS,
  type DemoBank,
} from '../../components/qr/demoDetails'

export type FinancialProvenanceClassification =
  | 'VALID_SIMULATED'
  | 'ACTUAL'
  | 'LEGACY_UNVERIFIED'
  | 'INCONSISTENT'

export interface FinancialPresentation {
  classification: FinancialProvenanceClassification
  statusBadgeLabel: string
  statusBadgeTone: 'success' | 'warning' | 'danger' | 'neutral' | 'info'
  bannerTone?: 'success' | 'warning' | 'danger' | 'info'
  bannerMessage?: string
  provenanceLabel: string
  isSimulatedSuccess: boolean
  isTerminal: boolean
  isPending: boolean
}

export interface BankSessionResolution {
  effectiveBank: DemoBank
  isLockedToActiveSession: boolean
  sessionBankLabel: string
  isSessionActive: boolean
  isSessionExpired: boolean
  statusNotice?: string
}

/**
 * Pure classification of payment status and settlement provenance.
 * Strictly guarantees that 'Paid - Simulated' is displayed ONLY for genuine,
 * consistent simulated settlements matching the V35/V36 database invariants.
 * Never makes unverified assertions about entitlements or memberships.
 */
export function classifyFinancialPresentation(
  projection: Pick<
    ReceptionDeskProjection,
    'paymentStatus' | 'settlementType' | 'settlementProvider' | 'simulatedTransactionId'
  > | null | undefined,
): FinancialPresentation {
  if (!projection) {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Unavailable',
      statusBadgeTone: 'neutral',
      provenanceLabel: 'Unavailable',
      isSimulatedSuccess: false,
      isTerminal: false,
      isPending: false,
    }
  }

  const status = projection.paymentStatus

  if (status === 'PENDING') {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Pending payment',
      statusBadgeTone: 'warning',
      provenanceLabel: projection.settlementType ?? 'Pending settlement',
      isSimulatedSuccess: false,
      isTerminal: false,
      isPending: true,
    }
  }

  if (status === 'FAILED') {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Failed',
      statusBadgeTone: 'danger',
      bannerTone: 'danger',
      bannerMessage: 'Payment marked as failed. Desk QR is inactive.',
      provenanceLabel: projection.settlementType ?? 'Failed',
      isSimulatedSuccess: false,
      isTerminal: true,
      isPending: false,
    }
  }

  if (status === 'CANCELED') {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Canceled',
      statusBadgeTone: 'neutral',
      bannerTone: 'info',
      bannerMessage: 'Payment was canceled. Desk QR is inactive.',
      provenanceLabel: projection.settlementType ?? 'Canceled',
      isSimulatedSuccess: false,
      isTerminal: true,
      isPending: false,
    }
  }

  if (status === 'REFUNDED') {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Refunded',
      statusBadgeTone: 'neutral',
      bannerTone: 'info',
      bannerMessage: 'Payment was refunded. Desk QR is inactive.',
      provenanceLabel: projection.settlementType ?? 'Refunded',
      isSimulatedSuccess: false,
      isTerminal: true,
      isPending: false,
    }
  }

  if (status === 'SUCCESS') {
    const type = projection.settlementType
    const provider = projection.settlementProvider
    const txId = projection.simulatedTransactionId

    // Case 1: Valid Simulated Settlement
    // Strictly requires settlementType = 'SIMULATED', settlementProvider = 'SPORTIFY_SIMULATOR', and valid txId
    if (
      type === 'SIMULATED' &&
      provider === 'SPORTIFY_SIMULATOR' &&
      typeof txId === 'number' &&
      Number.isSafeInteger(txId) &&
      txId > 0
    ) {
      return {
        classification: 'VALID_SIMULATED',
        statusBadgeLabel: 'Paid - Simulated',
        statusBadgeTone: 'success',
        bannerTone: 'success',
        bannerMessage:
          'Simulated payment received. Provenance: SIMULATED (SPORTIFY_SIMULATOR).',
        provenanceLabel: 'SIMULATED (SPORTIFY_SIMULATOR)',
        isSimulatedSuccess: true,
        isTerminal: true,
        isPending: false,
      }
    }

    // Case 2: Actual Settlement
    if (
      type === 'ACTUAL' &&
      provider !== 'SPORTIFY_SIMULATOR' &&
      (txId === null || txId === undefined)
    ) {
      const providerText = provider ? ` (${provider})` : ''
      return {
        classification: 'ACTUAL',
        statusBadgeLabel: 'Paid - Actual',
        statusBadgeTone: 'success',
        bannerTone: 'info',
        bannerMessage: `Payment settled via actual payment provider${providerText}.`,
        provenanceLabel: `ACTUAL${providerText}`,
        isSimulatedSuccess: false,
        isTerminal: true,
        isPending: false,
      }
    }

    // Case 3: Legacy Unverified Settlement (null type, provider, and transaction)
    if (
      (!type || type.trim() === '') &&
      (!provider || provider.trim() === '') &&
      (txId === null || txId === undefined)
    ) {
      return {
        classification: 'LEGACY_UNVERIFIED',
        statusBadgeLabel: 'Paid - Legacy unverified',
        statusBadgeTone: 'neutral',
        bannerTone: 'warning',
        bannerMessage:
          'Payment recorded as SUCCESS without settlement provenance records (Legacy unverified).',
        provenanceLabel: 'Legacy unverified',
        isSimulatedSuccess: false,
        isTerminal: true,
        isPending: false,
      }
    }

    // Case 4: Inconsistent or Mismatched Provenance
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Paid - Inconsistent provenance',
      statusBadgeTone: 'warning',
      bannerTone: 'warning',
      bannerMessage:
        'Authoritative payment status is SUCCESS, but settlement provenance is inconsistent or unverified.',
      provenanceLabel: 'Inconsistent provenance',
      isSimulatedSuccess: false,
      isTerminal: true,
      isPending: false,
    }
  }

  // Any other status
  return {
    classification: 'INCONSISTENT',
    statusBadgeLabel: status || 'Unknown',
    statusBadgeTone: 'neutral',
    provenanceLabel: status || 'Unknown',
    isSimulatedSuccess: false,
    isTerminal: true,
    isPending: false,
  }
}

export function findDemoBankByCodeOrKey(value: unknown): DemoBank {
  if (typeof value !== 'string') return DEFAULT_DEMO_BANK
  if (Object.hasOwn(DEMO_BANKS, value)) return value as DemoBank
  const match = Object.entries(DEMO_BANKS).find(
    ([, bank]) => bank.code.toLowerCase() === value.toLowerCase(),
  )
  return match ? (match[0] as DemoBank) : DEFAULT_DEMO_BANK
}

/**
 * Resolves authoritative session bank vs desk preview bank.
 * When an active member session exists in PENDING state, the session bank is authoritative
 * and cannot be overridden by desk staff.
 * If no session exists or session is expired, desk preview bank is used with clear informational labeling.
 */
export function resolveBankSession(
  activeSession: ReceptionDeskProjection['activeSession'] | null | undefined,
  previewBank: DemoBank,
): BankSessionResolution {
  if (!activeSession) {
    return {
      effectiveBank: previewBank,
      isLockedToActiveSession: false,
      sessionBankLabel: 'No active session',
      isSessionActive: false,
      isSessionExpired: false,
      statusNotice:
        'Informational preview only - Does not change the Member session or settlement bank.',
    }
  }

  const isPending = activeSession.state === 'PENDING'
  const isExpired = activeSession.state === 'EXPIRED'

  if (isPending) {
    const effectiveBank = findDemoBankByCodeOrKey(activeSession.bankCode)
    const known = Object.values(DEMO_BANKS).some((b) => b.code === activeSession.bankCode)
    const label = known
      ? activeSession.bankLabel
      : `Unknown bank (${activeSession.bankCode})`

    return {
      effectiveBank,
      isLockedToActiveSession: true,
      sessionBankLabel: label,
      isSessionActive: true,
      isSessionExpired: false,
      statusNotice: `Bank locked to customer active session: ${label}.`,
    }
  }

  if (isExpired) {
    return {
      effectiveBank: previewBank,
      isLockedToActiveSession: false,
      sessionBankLabel: `${activeSession.bankLabel} (Expired)`,
      isSessionActive: false,
      isSessionExpired: true,
      statusNotice:
        'Active member simulation session has expired. The customer must restart from their Member portal.',
    }
  }

  return {
    effectiveBank: previewBank,
    isLockedToActiveSession: false,
    sessionBankLabel: `${activeSession.bankLabel} (${activeSession.state})`,
    isSessionActive: false,
    isSessionExpired: false,
    statusNotice: `Session state: ${activeSession.state}. Informational preview only.`,
  }
}

/**
 * Monotonically updates projection during polling to avoid stale race conditions.
 * Once a terminal payment state is reached, it will never be downgraded to PENDING.
 */
export function mergePolledProjection(
  current: ReceptionDeskProjection | null,
  incoming: ReceptionDeskProjection,
): ReceptionDeskProjection {
  if (!current) {
    return incoming
  }
  // If current state is terminal, ignore incoming stale PENDING responses
  if (current.paymentStatus !== 'PENDING' && incoming.paymentStatus === 'PENDING') {
    return current
  }
  return incoming
}
