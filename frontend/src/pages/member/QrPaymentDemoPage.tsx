import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { paymentsApi } from '../../api/paymentsApi'
import { PaymentHero } from '../../components/PaymentHero'
import { PaymentWorkspace } from '../../components/PaymentWorkspace'
import { DemoQrCard } from '../../components/qr/DemoQrCard'
import { demoPaymentAmountAvailable } from '../../components/qr/demoDetails'
import { Card, EmptyState, ErrorState, StatusBadge } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { qrPaymentDemoEnabled } from '../../config/env'
import { formatPaymentMoney, isSimulatedSettlement, parseIdParam, PAYMENT_PATHS, paymentStatusLabel, paymentStatusToneCode } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

/** Member guard and backend ownership are identical to the existing payment detail routes. */
export function QrPaymentDemoPage() {
  const { paymentId } = useParams()
  const id = parseIdParam(paymentId)
  if (!qrPaymentDemoEnabled()) return (
    <PaymentWorkspace>
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist or has been moved.</p>
      <Link to={PAYMENT_PATHS.list}>My payments</Link>
    </PaymentWorkspace>
  )
  if (id === null) return (
    <PaymentWorkspace>
      <PaymentHero eyebrow="Member / Simulation" title="QR Payment (Simulation)" />
      <EmptyState title="Payment not found" description="The demo payment link is not valid."
        action={<Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>My payments</Link>} />
    </PaymentWorkspace>
  )
  return <QrPaymentDemoView key={id} paymentId={id} />
}

function QrPaymentDemoView({ paymentId }: { paymentId: number }) {
  const load = useCallback((signal: AbortSignal) => paymentsApi.payment(paymentId, { signal }), [paymentId])
  const [payment, reload] = useMemberResource(load)
  const back = <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.summary(paymentId)}>Back to Payment Summary</Link>
  const valid = payment.status === 'ready' && payment.data.id === paymentId && demoPaymentAmountAvailable(payment.data.amount)
  return (
    <PaymentWorkspace>
      <PaymentHero eyebrow="Member / Simulation" title="QR Payment (Simulation)" description="Owner confirmation finalizes this payment as Paid - Simulated and activates the purchased entitlement. No real bank transfer or cash movement occurs." />
      {payment.status === 'loading' ? <Spinner label="Loading the payment demo" />
        : payment.status === 'error' ? <ErrorState title="We could not load the payment demo" error={payment.error} onRetry={reload} />
        : !valid ? <EmptyState title="Payment details unavailable" description="Open the correct payment again from My payments." />
        : payment.data.status === 'SUCCESS' && isSimulatedSettlement(payment.data) ? (
          <Card title="Simulated QR Payment Completed" aria-label="Simulated QR settlement result">
            <StatusBadge status={paymentStatusToneCode(payment.data.status)} label={paymentStatusLabel(payment.data.status, payment.data.settlementType)} />
            <p className="notice notice--success">Paid - Simulated. Your payment was settled via Sportify QR Simulator; no real bank transfer occurred.</p>
            <dl className="summary-list">
              <div><dt>Payment reference</dt><dd>{payment.data.code}</dd></div>
              <div><dt>Amount</dt><dd>{formatPaymentMoney(payment.data.amount)}</dd></div>
              <div><dt>Settlement provenance</dt><dd>SIMULATED ({payment.data.settlementProvider ?? 'SPORTIFY_SIMULATOR'})</dd></div>
              {payment.data.referenceCode && <div><dt>Simulated transaction</dt><dd>{payment.data.referenceCode}</dd></div>}
            </dl>
          </Card>
        )
        : payment.data.status !== 'PENDING' ? <EmptyState title="Demo unavailable for this payment" description="Open a pending payment to start a simulated QR payment session." />
        : <DemoQrCard payment={payment.data} />}
      <div>{back}</div>
    </PaymentWorkspace>
  )
}
