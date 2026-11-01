/**
 * Pure provenance classification helper for Reception payments, invoices, and receipts.
 * Strictly guarantees truthful presentation of settlement provenance:
 * - VALID_SIMULATED: explicit simulated settlement label, warning banner, excluded from real cash.
 * - ACTUAL: truthful actual settlement presentation where supported by provider data.
 * - LEGACY_UNVERIFIED: explicit 'Legacy unverified' classification for successful payments with NULL/missing provenance.
 * - INCONSISTENT: explicit unverified/inconsistent classification for contradictory metadata.
 * - Non-successful statuses: preserves correct original status without false paid/verified claims.
 */

export type ProvenanceClassification =
  | 'ACTUAL'
  | 'VALID_SIMULATED'
  | 'LEGACY_UNVERIFIED'
  | 'INCONSISTENT'
  | 'REFUNDED'
  | 'PENDING'
  | 'FAILED'
  | 'CANCELLED'

export interface ProvenanceSubject {
  status?: string | null
  paymentStatus?: string | null
  settlementType?: string | null
  settlementProvider?: string | null
  simulatedTransactionId?: number | null
}

export interface ProvenancePresentation {
  classification: ProvenanceClassification
  statusBadgeLabel: string
  statusBadgeTone: 'success' | 'warning' | 'danger' | 'neutral' | 'info'
  provenanceLabel: string
  isVerifiedActual: boolean
  isSimulatedSuccess: boolean
  isLegacyUnverified: boolean
  isInconsistent: boolean
  canPrintReceipt: boolean
  receiptHeroTitle?: string
  receiptHeroDescription: string
  receiptWarningMessage?: string
  receiptWarningTone?: 'warning' | 'danger' | 'info'
}

export function classifyProvenance(
  subject: ProvenanceSubject | null | undefined,
): ProvenancePresentation {
  if (!subject) {
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Unavailable',
      statusBadgeTone: 'neutral',
      provenanceLabel: 'Unavailable',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: true,
      canPrintReceipt: false,
      receiptHeroDescription: 'Unavailable payment record.',
    }
  }

  const rawStatus = (subject.status ?? subject.paymentStatus ?? '').trim().toUpperCase()

  if (rawStatus === 'REFUNDED') {
    return {
      classification: 'REFUNDED',
      statusBadgeLabel: 'Refunded',
      statusBadgeTone: 'neutral',
      provenanceLabel: subject.settlementType?.trim() || 'Refunded',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: false,
      canPrintReceipt: true,
      receiptHeroDescription: 'This payment was refunded to the member.',
    }
  }

  if (rawStatus === 'PENDING' || rawStatus === 'ISSUED') {
    return {
      classification: 'PENDING',
      statusBadgeLabel: 'Pending payment',
      statusBadgeTone: 'warning',
      provenanceLabel: subject.settlementType?.trim() || 'Pending settlement',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: false,
      canPrintReceipt: false,
      receiptHeroDescription: 'This invoice is pending payment.',
    }
  }

  if (rawStatus === 'FAILED') {
    return {
      classification: 'FAILED',
      statusBadgeLabel: 'Failed',
      statusBadgeTone: 'danger',
      provenanceLabel: subject.settlementType?.trim() || 'Failed',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: false,
      canPrintReceipt: false,
      receiptHeroDescription: 'Payment has failed.',
    }
  }

  if (rawStatus === 'CANCELLED') {
    return {
      classification: 'CANCELLED',
      statusBadgeLabel: 'Canceled',
      statusBadgeTone: 'neutral',
      provenanceLabel: subject.settlementType?.trim() || 'Canceled',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: false,
      canPrintReceipt: false,
      receiptHeroDescription: 'Payment was canceled.',
    }
  }

  if (rawStatus === 'PAID' || rawStatus === 'SUCCESS') {
    const type = (subject.settlementType ?? '').trim().toUpperCase()
    const provider = (subject.settlementProvider ?? '').trim().toUpperCase()
    const txId = subject.simulatedTransactionId
    const hasValidTxId = typeof txId === 'number' && Number.isSafeInteger(txId) && txId > 0

    // Case 1: Valid Simulated Settlement
    // Strictly requires settlementType = 'SIMULATED', provider = 'SPORTIFY_SIMULATOR', and valid txId (if provided)
    if (type === 'SIMULATED') {
      if (provider === 'SPORTIFY_SIMULATOR' && (txId === undefined || hasValidTxId)) {
        return {
          classification: 'VALID_SIMULATED',
          statusBadgeLabel: 'Paid - Simulated',
          statusBadgeTone: 'success',
          provenanceLabel: 'SIMULATED (SPORTIFY_SIMULATOR)',
          isVerifiedActual: false,
          isSimulatedSuccess: true,
          isLegacyUnverified: false,
          isInconsistent: false,
          canPrintReceipt: true,
          receiptHeroDescription: 'Simulated payment record - no real money was transferred.',
          receiptWarningMessage:
            'SIMULATED SETTLEMENT - Completed via Sportify QR Simulator. No real bank transfer or cash collection occurred.',
          receiptWarningTone: 'warning',
        }
      }
      return {
        classification: 'INCONSISTENT',
        statusBadgeLabel: 'Paid - Inconsistent provenance',
        statusBadgeTone: 'warning',
        provenanceLabel: 'Inconsistent provenance',
        isVerifiedActual: false,
        isSimulatedSuccess: false,
        isLegacyUnverified: false,
        isInconsistent: true,
        canPrintReceipt: false,
        receiptHeroDescription: 'Payment record with unverified or inconsistent settlement provenance.',
        receiptWarningMessage:
          'INCONSISTENT PROVENANCE - Settlement provenance is unverified or contradictory. Not an authoritative proof of settlement.',
        receiptWarningTone: 'danger',
      }
    }

    // Case 2: Actual Settlement
    // Strictly requires settlementType = 'ACTUAL' and NO simulated provider or transaction ID
    if (type === 'ACTUAL') {
      if (provider === 'SPORTIFY_SIMULATOR' || txId != null) {
        return {
          classification: 'INCONSISTENT',
          statusBadgeLabel: 'Paid - Inconsistent provenance',
          statusBadgeTone: 'warning',
          provenanceLabel: 'Inconsistent provenance',
          isVerifiedActual: false,
          isSimulatedSuccess: false,
          isLegacyUnverified: false,
          isInconsistent: true,
          canPrintReceipt: false,
          receiptHeroDescription: 'Payment record with unverified or inconsistent settlement provenance.',
          receiptWarningMessage:
            'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified.',
          receiptWarningTone: 'danger',
        }
      }
      const providerLabel = subject.settlementProvider ? `ACTUAL (${subject.settlementProvider})` : 'ACTUAL'
      return {
        classification: 'ACTUAL',
        statusBadgeLabel: 'Paid',
        statusBadgeTone: 'success',
        provenanceLabel: providerLabel,
        isVerifiedActual: true,
        isSimulatedSuccess: false,
        isLegacyUnverified: false,
        isInconsistent: false,
        canPrintReceipt: true,
        receiptHeroDescription: 'Confirmed payment record.',
      }
    }

    // Case 3: Legacy Unverified Settlement (null, empty, or 'UNKNOWN' type)
    if (type === 'UNKNOWN' || type === '') {
      if (provider === 'SPORTIFY_SIMULATOR' || txId != null) {
        return {
          classification: 'INCONSISTENT',
          statusBadgeLabel: 'Paid - Inconsistent provenance',
          statusBadgeTone: 'warning',
          provenanceLabel: 'Inconsistent provenance',
          isVerifiedActual: false,
          isSimulatedSuccess: false,
          isLegacyUnverified: false,
          isInconsistent: true,
          canPrintReceipt: false,
          receiptHeroDescription: 'Payment record with unverified or inconsistent settlement provenance.',
          receiptWarningMessage:
            'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified.',
          receiptWarningTone: 'danger',
        }
      }
      return {
        classification: 'LEGACY_UNVERIFIED',
        statusBadgeLabel: 'Paid - Legacy unverified',
        statusBadgeTone: 'neutral',
        provenanceLabel: 'Legacy unverified',
        isVerifiedActual: false,
        isSimulatedSuccess: false,
        isLegacyUnverified: true,
        isInconsistent: false,
        canPrintReceipt: false,
        receiptHeroDescription: 'Historical payment record without verified settlement provenance.',
        receiptWarningMessage:
          'LEGACY UNVERIFIED - Historical payment record without verified settlement provenance. Not an authoritative proof of settlement.',
        receiptWarningTone: 'warning',
      }
    }

    // Case 4: Any other contradictory settlementType
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Paid - Inconsistent provenance',
      statusBadgeTone: 'warning',
      provenanceLabel: 'Inconsistent provenance',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: true,
      canPrintReceipt: false,
      receiptHeroDescription: 'Payment record with unverified or inconsistent settlement provenance.',
      receiptWarningMessage:
        'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified.',
      receiptWarningTone: 'danger',
    }
  }

  // Any other status
  return {
    classification: 'INCONSISTENT',
    statusBadgeLabel: rawStatus || 'Unknown',
    statusBadgeTone: 'neutral',
    provenanceLabel: rawStatus || 'Unknown',
    isVerifiedActual: false,
    isSimulatedSuccess: false,
    isLegacyUnverified: false,
    isInconsistent: true,
    canPrintReceipt: false,
    receiptHeroDescription: 'Payment record with unverified status.',
  }
}
