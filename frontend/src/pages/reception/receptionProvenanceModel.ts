/**
 * Pure provenance classification helper for Reception payments, invoices, and receipts.
 * Strictly guarantees truthful presentation of settlement provenance:
 * - VALID_SIMULATED: explicit simulated settlement label, warning banner, excluded from real cash.
 *   This includes Sportify simulator records with a transaction row, VietQR gateway sandbox records and
 *   local VietQR demo records (provider LOCAL_DEMO, environment SANDBOX).
 * - ACTUAL: truthful actual settlement presentation where supported by provider data.
 * - LEGACY_UNVERIFIED: explicit 'Legacy unverified' classification for records with NULL/missing provenance.
 * - INCONSISTENT: explicit unverified/inconsistent classification for contradictory metadata.
 * - REFUNDED: evaluates underlying settlement provenance; prevents unverified refunds from printing receipts.
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
  settlementEnvironment?: string | null
  simulatedTransactionId?: number | null
}

/** VietQR sandbox settlements: the gateway sandbox (GATEWAY) and the guarded local demo (LOCAL_DEMO). */
const VIETQR_SANDBOX_PROVIDERS = ['GATEWAY', 'LOCAL_DEMO']

function isVietQrSandbox(subject: ProvenanceSubject, provider: string, txId: number | null | undefined): boolean {
  return VIETQR_SANDBOX_PROVIDERS.includes(provider)
    && (subject.settlementEnvironment ?? '').trim().toUpperCase() === 'SANDBOX'
    && txId == null
}

function simulatedProvenanceLabel(provider: string): string {
  if (provider === 'GATEWAY') return 'SIMULATED (VIETQR SANDBOX)'
  if (provider === 'LOCAL_DEMO') return 'SIMULATED (VIETQR LOCAL DEMO)'
  return 'SIMULATED (SPORTIFY_SIMULATOR)'
}

function simulatedWarning(provider: string): string {
  if (provider === 'GATEWAY') return 'VIETQR SANDBOX - The local test transaction did not move real money.'
  if (provider === 'LOCAL_DEMO') return 'VIETQR LOCAL DEMO - Completed in local demo mode. No real money was moved.'
  return 'SIMULATED SETTLEMENT - Completed via Sportify QR Simulator. No real bank transfer or cash collection occurred.'
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

  // REFUNDED: Evaluate underlying settlement provenance. Unverified refunds must NOT print receipts.
  if (rawStatus === 'REFUNDED') {
    const type = (subject.settlementType ?? '').trim().toUpperCase()
    const provider = (subject.settlementProvider ?? '').trim().toUpperCase()
    const txId = subject.simulatedTransactionId
    const hasValidTxId = typeof txId === 'number' && Number.isSafeInteger(txId) && txId > 0

    // Refunded genuine ACTUAL settlement
    if (type === 'ACTUAL') {
      if (provider === 'SPORTIFY_SIMULATOR' || txId != null
          || (subject.settlementEnvironment ?? '').trim().toUpperCase() === 'SANDBOX') {
        return {
          classification: 'INCONSISTENT',
          statusBadgeLabel: 'Refunded - Inconsistent provenance',
          statusBadgeTone: 'warning',
          provenanceLabel: 'Inconsistent provenance',
          isVerifiedActual: false,
          isSimulatedSuccess: false,
          isLegacyUnverified: false,
          isInconsistent: true,
          canPrintReceipt: false,
          receiptHeroDescription: 'Payment was refunded, but settlement provenance is contradictory or unverified.',
          receiptWarningMessage:
            'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified. Not an authoritative proof of settlement.',
          receiptWarningTone: 'danger',
        }
      }
      const providerLabel = subject.settlementProvider ? `ACTUAL (${subject.settlementProvider})` : 'ACTUAL'
      return {
        classification: 'REFUNDED',
        statusBadgeLabel: 'Refunded',
        statusBadgeTone: 'neutral',
        provenanceLabel: providerLabel,
        isVerifiedActual: true,
        isSimulatedSuccess: false,
        isLegacyUnverified: false,
        isInconsistent: false,
        canPrintReceipt: true,
        receiptHeroDescription: 'This payment was refunded to the member.',
      }
    }

    // Refunded genuine SIMULATED settlement
    if (type === 'SIMULATED') {
      if ((provider === 'SPORTIFY_SIMULATOR' && hasValidTxId) || isVietQrSandbox(subject, provider, txId)) {
        return {
          classification: 'REFUNDED',
          statusBadgeLabel: 'Refunded',
          statusBadgeTone: 'neutral',
          provenanceLabel: simulatedProvenanceLabel(provider),
          isVerifiedActual: false,
          isSimulatedSuccess: true,
          isLegacyUnverified: false,
          isInconsistent: false,
          canPrintReceipt: true,
          receiptHeroDescription: 'This payment was refunded to the member.',
          receiptWarningMessage: simulatedWarning(provider),
          receiptWarningTone: 'warning',
        }
      }
      return {
        classification: 'INCONSISTENT',
        statusBadgeLabel: 'Refunded - Inconsistent provenance',
        statusBadgeTone: 'warning',
        provenanceLabel: 'Inconsistent provenance',
        isVerifiedActual: false,
        isSimulatedSuccess: false,
        isLegacyUnverified: false,
        isInconsistent: true,
        canPrintReceipt: false,
        receiptHeroDescription: 'Payment was refunded, but settlement provenance is contradictory or unverified.',
        receiptWarningMessage:
          'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified. Not an authoritative proof of settlement.',
        receiptWarningTone: 'danger',
      }
    }

    // Refunded LEGACY UNVERIFIED settlement (null, empty, or 'UNKNOWN')
    if (type === 'UNKNOWN' || type === '') {
      if (provider === 'SPORTIFY_SIMULATOR' || txId != null) {
        return {
          classification: 'INCONSISTENT',
          statusBadgeLabel: 'Refunded - Inconsistent provenance',
          statusBadgeTone: 'warning',
          provenanceLabel: 'Inconsistent provenance',
          isVerifiedActual: false,
          isSimulatedSuccess: false,
          isLegacyUnverified: false,
          isInconsistent: true,
          canPrintReceipt: false,
          receiptHeroDescription: 'Payment was refunded, but settlement provenance is contradictory or unverified.',
          receiptWarningMessage:
            'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified. Not an authoritative proof of settlement.',
          receiptWarningTone: 'danger',
        }
      }
      return {
        classification: 'LEGACY_UNVERIFIED',
        statusBadgeLabel: 'Refunded - Legacy unverified',
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

    // Any other contradictory settlementType on REFUNDED
    return {
      classification: 'INCONSISTENT',
      statusBadgeLabel: 'Refunded - Inconsistent provenance',
      statusBadgeTone: 'warning',
      provenanceLabel: 'Inconsistent provenance',
      isVerifiedActual: false,
      isSimulatedSuccess: false,
      isLegacyUnverified: false,
      isInconsistent: true,
      canPrintReceipt: false,
      receiptHeroDescription: 'Payment was refunded, but settlement provenance is contradictory or unverified.',
      receiptWarningMessage:
        'INCONSISTENT PROVENANCE - Settlement provenance is contradictory or unverified. Not an authoritative proof of settlement.',
      receiptWarningTone: 'danger',
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
    // Strictly requires settlementType = 'SIMULATED', provider = 'SPORTIFY_SIMULATOR', and valid positive txId
    if (type === 'SIMULATED') {
      if ((provider === 'SPORTIFY_SIMULATOR' && hasValidTxId) || isVietQrSandbox(subject, provider, txId)) {
        return {
          classification: 'VALID_SIMULATED',
          statusBadgeLabel: 'Paid - Simulated',
          statusBadgeTone: 'success',
          provenanceLabel: simulatedProvenanceLabel(provider),
          isVerifiedActual: false,
          isSimulatedSuccess: true,
          isLegacyUnverified: false,
          isInconsistent: false,
          canPrintReceipt: true,
          receiptHeroDescription: 'Simulated payment record - no real money was transferred.',
          receiptWarningMessage: simulatedWarning(provider),
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
      if (provider === 'SPORTIFY_SIMULATOR' || txId != null
          || (subject.settlementEnvironment ?? '').trim().toUpperCase() === 'SANDBOX') {
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

function buildInconsistentPresentation(
  isRefund: boolean,
  warningMessage: string,
): ProvenancePresentation {
  return {
    classification: 'INCONSISTENT',
    statusBadgeLabel: isRefund ? 'Refunded - Inconsistent provenance' : 'Paid - Inconsistent provenance',
    statusBadgeTone: 'warning',
    provenanceLabel: 'Inconsistent provenance',
    isVerifiedActual: false,
    isSimulatedSuccess: false,
    isLegacyUnverified: false,
    isInconsistent: true,
    canPrintReceipt: false,
    receiptHeroDescription: isRefund
      ? 'Payment was refunded, but settlement provenance is contradictory or unverified.'
      : 'Payment record with unverified or inconsistent settlement provenance.',
    receiptWarningMessage: warningMessage,
    receiptWarningTone: 'danger',
  }
}

/**
 * Reconciles provenance between a Payment and its corresponding Invoice.
 * Prevents cross-source provenance contamination:
 * - A missing provenance in an invoice is NEVER upgraded by payment fields.
 * - Any material contradiction between payment and invoice fails closed as INCONSISTENT.
 * - For REFUNDED records, compares underlying settlement type, provider, and simulated transaction ID (D03-05).
 * - If no ready invoice is available, canPrintReceipt is strictly false.
 */
export function reconcileProvenance(
  paymentSubject: ProvenanceSubject | null | undefined,
  invoiceSubject: ProvenanceSubject | null | undefined,
): ProvenancePresentation {
  // If invoice is not yet ready or absent, printing an invoice receipt is impossible
  if (!invoiceSubject) {
    const payPres = classifyProvenance(paymentSubject)
    return {
      ...payPres,
      canPrintReceipt: false,
    }
  }

  // When only payment is missing, rely on the invoice itself
  if (!paymentSubject) {
    return classifyProvenance(invoiceSubject)
  }

  const invoicePres = classifyProvenance(invoiceSubject)
  const paymentPres = classifyProvenance(paymentSubject)

  const invoiceStatus = (invoiceSubject.status ?? invoiceSubject.paymentStatus ?? '').trim().toUpperCase()
  const paymentStatus = (paymentSubject.status ?? paymentSubject.paymentStatus ?? '').trim().toUpperCase()
  const isRefund =
    invoiceStatus === 'REFUNDED' ||
    paymentStatus === 'REFUNDED' ||
    invoicePres.classification === 'REFUNDED' ||
    paymentPres.classification === 'REFUNDED'

  // Direct classification mismatch across sources (e.g. invoice NULL and payment ACTUAL, or ACTUAL vs SIMULATED)
  if (invoicePres.classification !== paymentPres.classification) {
    return buildInconsistentPresentation(
      isRefund,
      'INCONSISTENT PROVENANCE - Payment record and invoice disagree on settlement provenance. Not an authoritative proof of settlement.',
    )
  }

  // Cross-source reconciliation for REFUNDED records (D03-05)
  if (invoicePres.classification === 'REFUNDED') {
    const invType = (invoiceSubject.settlementType ?? '').trim().toUpperCase()
    const payType = (paymentSubject.settlementType ?? '').trim().toUpperCase()

    // Compare underlying settlement type (e.g. ACTUAL vs SIMULATED)
    if (invType !== payType) {
      return buildInconsistentPresentation(
        true,
        'INCONSISTENT PROVENANCE - Payment record and invoice disagree on settlement provenance. Not an authoritative proof of settlement.',
      )
    }

    if (invType === 'ACTUAL') {
      const invProv = (invoiceSubject.settlementProvider ?? '').trim().toUpperCase()
      const payProv = (paymentSubject.settlementProvider ?? '').trim().toUpperCase()
      // Under V35/V36 constraints, settlementProvider is nullable for ACTUAL settlements.
      // Both providers consistently absent is valid; a provider present on only one source or differing providers is inconsistent (D03-07).
      if (invProv !== payProv) {
        return buildInconsistentPresentation(
          true,
          'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting settlement providers.',
        )
      }
      return invoicePres
    }

    if (invType === 'SIMULATED') {
      const invProv = (invoiceSubject.settlementProvider ?? '').trim().toUpperCase()
      const payProv = (paymentSubject.settlementProvider ?? '').trim().toUpperCase()
      const invTx = invoiceSubject.simulatedTransactionId ?? null
      const payTx = paymentSubject.simulatedTransactionId ?? null
      if (!invProv || !payProv || invProv !== payProv) {
        return buildInconsistentPresentation(
          true,
          'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting settlement providers.',
        )
      }
      if (VIETQR_SANDBOX_PROVIDERS.includes(invProv)) {
        const invEnv = (invoiceSubject.settlementEnvironment ?? '').trim().toUpperCase()
        const payEnv = (paymentSubject.settlementEnvironment ?? '').trim().toUpperCase()
        if (invEnv !== 'SANDBOX' || payEnv !== 'SANDBOX' || invTx !== null || payTx !== null) {
          return buildInconsistentPresentation(
            true,
            'INCONSISTENT PROVENANCE - VietQR sandbox settlement details do not match.',
          )
        }
        return invoicePres
      }
      if (invTx === null || payTx === null || invTx !== payTx) {
        return buildInconsistentPresentation(
          true,
          'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting simulated transaction identifiers.',
        )
      }
      return invoicePres
    }

    return invoicePres
  }

  // Both have same classification for non-refunded: check detailed field consistency
  if (invoicePres.classification === 'ACTUAL') {
    const invProv = (invoiceSubject.settlementProvider ?? '').trim().toUpperCase()
    const payProv = (paymentSubject.settlementProvider ?? '').trim().toUpperCase()
    if (invProv && payProv && invProv !== payProv) {
      return buildInconsistentPresentation(
        isRefund,
        'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting settlement providers.',
      )
    }
    return invoicePres
  }

  if (invoicePres.classification === 'VALID_SIMULATED') {
    const invProvider = (invoiceSubject.settlementProvider ?? '').trim().toUpperCase()
    const payProvider = (paymentSubject.settlementProvider ?? '').trim().toUpperCase()
    if (invProvider !== payProvider) {
      return buildInconsistentPresentation(
        isRefund,
        'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting settlement providers.',
      )
    }
    if (VIETQR_SANDBOX_PROVIDERS.includes(invProvider)) {
      const invEnv = (invoiceSubject.settlementEnvironment ?? '').trim().toUpperCase()
      const payEnv = (paymentSubject.settlementEnvironment ?? '').trim().toUpperCase()
      if (invEnv !== 'SANDBOX' || payEnv !== 'SANDBOX'
          || invoiceSubject.simulatedTransactionId != null || paymentSubject.simulatedTransactionId != null) {
        return buildInconsistentPresentation(
          isRefund,
          'INCONSISTENT PROVENANCE - VietQR sandbox settlement details do not match.',
        )
      }
      return invoicePres
    }
    const invTx = invoiceSubject.simulatedTransactionId
    const payTx = paymentSubject.simulatedTransactionId
    if (invTx !== payTx) {
      return buildInconsistentPresentation(
        isRefund,
        'INCONSISTENT PROVENANCE - Payment and invoice specify conflicting simulated transaction identifiers.',
      )
    }
    return invoicePres
  }

  // For LEGACY_UNVERIFIED, PENDING, FAILED, CANCELLED, or INCONSISTENT
  return invoicePres
}
