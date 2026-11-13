import { useId, useState } from 'react'
import { paymentsApi, type Payment } from '../../api/paymentsApi'
import { getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui'
import './paymentCancellation.css'

export const HANDOFF_TOOLTIP = 'This payment has been submitted to Receptionist for verification and can no longer be cancelled. Please contact Receptionist for assistance.'

/** Why a Member cannot cancel this payment, or null when cancellation is still allowed. The server re-checks all of it. */
function cancellationBlockedReason(payment: Payment): string | null {
  if (payment.submittedForVerificationAt) return HANDOFF_TOOLTIP
  if (payment.status !== 'PENDING') return `This payment is ${payment.status.toLowerCase()} and cannot be cancelled.`
  if (payment.referenceCode || payment.settlementType) return 'Settlement is in progress. Please contact Receptionist.'
  return null
}

/**
 * Cancel Payment stays visible for every payment. Once it is not allowed (handed to Receptionist, settled or no longer
 * pending) it is grey and disabled, and hovering or focusing it explains why. The backend enforces the same rule.
 */
export function CancelPayment({ payment, onChanged }: { payment: Payment; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const reasonId = useId()
  const reason = cancellationBlockedReason(payment)
  async function cancel() {
    if (reason || busy || !window.confirm('Cancel this pending payment? Do not cancel if you have already transferred money.')) return
    setBusy(true); setError(null)
    try { await paymentsApi.cancel(payment.id); onChanged() }
    catch (caught) { setError(getErrorMessage(caught)); onChanged() }
    finally { setBusy(false) }
  }
  return <span className={'payment-cancel' + (reason ? ' payment-cancel--blocked' : '')}
    title={reason ?? undefined} tabIndex={reason ? 0 : undefined} aria-describedby={reason ? reasonId : undefined}>
    <Button type="button" variant="secondary" size="sm" disabled={!!reason || busy} loading={busy}
      aria-describedby={reason ? reasonId : undefined} onClick={() => void cancel()}>Cancel Payment</Button>
    {reason && <span id={reasonId} role="tooltip" className="payment-cancel__reason">{reason}</span>}
    {error && <span role="alert" className="payment-cancel__error">{error}</span>}
  </span>
}

/**
 * The explicit Receptionist handoff: the Member confirms the order is ready for verification. After this the
 * payment can no longer be cancelled by the Member.
 */
export function SubmitPayment({ payment, onChanged }: { payment: Payment; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (payment.status !== 'PENDING' || payment.submittedForVerificationAt) return null
  async function submit() {
    if (busy || !window.confirm('Send this order to Receptionist for verification? After this you can no longer cancel it yourself.')) return
    setBusy(true); setError(null)
    try { await paymentsApi.submit(payment.id); onChanged() }
    catch (caught) { setError(getErrorMessage(caught)) }
    finally { setBusy(false) }
  }
  return <span className="payment-cancel">
    <Button type="button" size="sm" loading={busy} onClick={() => void submit()}>Submit to Receptionist for verification</Button>
    {error && <span role="alert" className="payment-cancel__error">{error}</span>}
  </span>
}
