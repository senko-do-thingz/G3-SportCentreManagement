import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { paymentsApi } from '../../api/paymentsApi'
import { PaymentHero } from '../../components/PaymentHero'
import { PaymentWorkspace } from '../../components/PaymentWorkspace'
import { DemoQrCard } from '../../components/qr/DemoQrCard'
import { demoPaymentAmountAvailable } from '../../components/qr/demoDetails'
import { EmptyState, ErrorState } from '../../components/ui'
import { Spinner } from '../../components/ui/Spinner'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { qrPaymentDemoEnabled } from '../../config/env'
import { NotFoundPage } from '../public/NotFoundPage'
import { parseIdParam, PAYMENT_PATHS } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

/** Member guard and backend ownership are identical to the existing payment detail routes. */
export function QrPaymentDemoPage() {
  const { paymentId } = useParams()
  const id = parseIdParam(paymentId)
  if (!qrPaymentDemoEnabled()) return <NotFoundPage />
  if (id === null) return (
    <PaymentWorkspace>
      <PaymentHero eyebrow="Member / Demo" title="QR Payment (Demo)" />
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
      <PaymentHero eyebrow="Member / Demo" title="QR Payment (Demo)" description="A non-payable interface preview. Your actual payment is not changed." />
      {payment.status === 'loading' ? <Spinner label="Loading the payment demo" />
        : payment.status === 'error' ? <ErrorState title="We could not load the payment demo" error={payment.error} onRetry={reload} />
        : !valid ? <EmptyState title="Payment details unavailable" description="Open the correct payment again from My payments." />
        : payment.data.status !== 'PENDING' ? <EmptyState title="Demo unavailable for this payment" description="Open a pending payment to preview the QR demo. Its actual status has not changed." />
        : <DemoQrCard payment={payment.data} />}
      <div>{back}</div>
    </PaymentWorkspace>
  )
}
