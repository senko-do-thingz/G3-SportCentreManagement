import { useState } from 'react'
import { paymentsApi, type Payment } from '../../api/paymentsApi'
import { getErrorMessage } from '../../api/errors'

export const HANDOFF_TOOLTIP = 'This payment has been submitted to Receptionist for verification and can no longer be cancelled. Please contact Receptionist for assistance.'

export function CancelPayment({ payment, onChanged }: { payment: Payment; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const reason = payment.submittedForVerificationAt ? HANDOFF_TOOLTIP
    : payment.status !== 'PENDING' ? `This payment is ${payment.status.toLowerCase()} and cannot be cancelled.`
    : payment.referenceCode || payment.settlementType ? 'Settlement is in progress. Please contact Receptionist.' : null
  async function cancel() {
    if (reason || busy || !window.confirm('Cancel this pending payment? Do not cancel if you have already transferred money.')) return
    setBusy(true); setError(null)
    try { await paymentsApi.cancel(payment.id); onChanged() }
    catch (caught) { setError(getErrorMessage(caught)); onChanged() }
    finally { setBusy(false) }
  }
  return <span title={reason ?? undefined} tabIndex={reason ? 0 : undefined}>
    <button type="button" disabled={!!reason || busy} aria-describedby={reason ? `cancel-reason-${payment.id}` : undefined}
      style={reason ? { color: '#777', opacity: 0.6, cursor: 'not-allowed' } : undefined} onClick={cancel}>Cancel Payment</button>
    {reason && <span id={`cancel-reason-${payment.id}`} className="sr-only">{reason}</span>}
    {error && <span role="alert">{error}</span>}
  </span>
}

export function SubmitPayment({ payment, onChanged }: { payment: Payment; onChanged: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  if (payment.status !== 'PENDING' || payment.submittedForVerificationAt) return null
  return <span>
    <button type="button" disabled={busy} onClick={async () => {
      setBusy(true); setError(null)
      try { await paymentsApi.submit(payment.id); onChanged() }
      catch (caught) { setError(getErrorMessage(caught)) }
      finally { setBusy(false) }
    }}>Submit to Receptionist for verification</button>
    {error && <span role="alert">{error}</span>}
  </span>
}
