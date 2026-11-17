import { useEffect, useState, type ReactNode } from 'react'
import QRCode from 'qrcode'
import { getErrorMessage } from '../../api/errors'
import { paymentsApi, type PayOsCheckout, type Payment } from '../../api/paymentsApi'
import { formatPaymentMoney } from '../../pages/member/paymentsModel'
import { Button, Card } from '../ui'
import { Spinner } from '../ui/Spinner'
import { buttonClassName } from '../ui/buttonClassName'

type CheckoutState =
  | { status: 'loading' }
  | { status: 'ready'; checkout: PayOsCheckout }
  | { status: 'error'; error: string }

export function PayOsPaymentCard({
  payment,
  manualFallback,
}: {
  payment: Payment
  manualFallback: ReactNode
}) {
  const [attempt, setAttempt] = useState(0)
  const [requestState, setRequestState] = useState<{ key: string; state: CheckoutState } | null>(null)
  const [qrState, setQrState] = useState<{ payload: string; image: string } | null>(null)
  const requestKey = `${payment.id}:${attempt}`
  const state = requestState?.key === requestKey ? requestState.state : { status: 'loading' as const }
  const qrPayload = state.status === 'ready' && state.checkout.enabled ? state.checkout.qrCode : null
  const qrImage = qrPayload && qrState?.payload === qrPayload ? qrState.image : null

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void paymentsApi.payOsCheckout(payment.id, { signal: controller.signal })
      .then((checkout) => {
        if (active) setRequestState({ key: requestKey, state: { status: 'ready', checkout } })
      })
      .catch((error: unknown) => {
        if (active && !controller.signal.aborted) {
          setRequestState({ key: requestKey, state: { status: 'error', error: getErrorMessage(error) } })
        }
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [payment.id, requestKey])

  useEffect(() => {
    let active = true
    if (!qrPayload) return () => { active = false }
    void QRCode.toDataURL(qrPayload, { width: 320, margin: 3, errorCorrectionLevel: 'M' })
      .then((image) => { if (active) setQrState({ payload: qrPayload, image }) })
      .catch(() => { if (active) setQrState({ payload: qrPayload, image: '' }) })
    return () => { active = false }
  }, [qrPayload])

  if (state.status === 'loading') {
    return <Card title="PayOS checkout" aria-label="PayOS checkout"><Spinner label="Preparing your PayOS QR payment" /></Card>
  }

  if (state.status === 'error') {
    return (
      <Card title="PayOS checkout unavailable" aria-label="PayOS checkout unavailable">
        <p className="notice notice--warning" role="alert">{state.error}</p>
        <Button variant="secondary" onClick={() => setAttempt((value) => value + 1)}>Try again</Button>
        <ManualFallback manualFallback={manualFallback} />
      </Card>
    )
  }

  if (!state.checkout.enabled) {
    return (
      <Card title="PayOS checkout unavailable" aria-label="PayOS checkout unavailable">
        <p className="notice notice--warning" role="status">{state.checkout.notice}</p>
        <p>PayOS must be configured before this page can create a PayOS payment QR.</p>
        <ManualFallback manualFallback={manualFallback} />
      </Card>
    )
  }

  return (
    <Card title="PayOS bank transfer" aria-label="PayOS bank transfer">
      <p>{state.checkout.notice}</p>
      {qrImage ? (
        <div className="payos-payment-card__qr-wrap">
          <div className="payos-payment-card__qr-frame">
            <img src={qrImage} alt={`PayOS payment QR for ${payment.code}`} />
          </div>
        </div>
      ) : state.checkout.qrCode ? (
        <p className="notice notice--warning" role="alert">The PayOS QR could not be rendered. Open the PayOS checkout page to view it.</p>
      ) : (
        <p className="notice notice--info">Open PayOS checkout to view the bank QR.</p>
      )}
      <dl className="summary-list">
        <div><dt>Amount</dt><dd>{formatPaymentMoney(state.checkout.amount)}</dd></div>
        <div><dt>Payment reference</dt><dd>{payment.code}</dd></div>
      </dl>
      {state.checkout.checkoutUrl && (
        <a className={buttonClassName('primary', 'md', true)} href={state.checkout.checkoutUrl} target="_blank" rel="noopener noreferrer">
          Continue to PayOS checkout
        </a>
      )}
      <p className="notice notice--info">
        PayOS sends confirmation to Sportify after the bank reports the transfer. This status page refreshes automatically; reception does not need to confirm PayOS payments.
      </p>
      <p className="payment-methods__hint">This is a real payment link. Scanning and paying transfers real money to the bank account connected to your PayOS channel.</p>
    </Card>
  )
}

function ManualFallback({ manualFallback }: { manualFallback: ReactNode }) {
  return (
    <div className="payos-payment-card__manual">
      <p className="payment-methods__hint">Manual transfer instructions are separate from PayOS and stay pending until reception verifies the received funds.</p>
      {manualFallback}
    </div>
  )
}
