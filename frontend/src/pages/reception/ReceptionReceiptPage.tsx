import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { receptionPaymentsApi } from '../../api/receptionPaymentsApi'
import { PaymentHero } from '../../components/PaymentHero'
import { Button, Card, EmptyState, ErrorState } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, humanizeCode } from '../../utils/format'
import { positiveId, receptionPaymentPath } from './receptionModel'
import { formatPaymentMoney as formatMoney, memberLine } from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

/**
 * Receipt (Figma S3-Receipt) at /reception/payments/:paymentId/receipt: the invoice of
 * GET /invoices/by-payment/{paymentId} (BE-18), ready to print. Back returns to the payment.
 * UI-31: only the invoice of this payment is shown, base price and discount only when the invoice
 * has them, and only a PAID or REFUNDED invoice can be printed.
 */
export function ReceptionReceiptPage() {
  const { paymentId } = useParams()
  const id = positiveId(paymentId)
  const load = useCallback(
    (signal: AbortSignal) =>
      id !== null ? receptionPaymentsApi.invoiceByPayment(id, { signal }) : Promise.reject(new Error('The receipt link is not valid.')),
    [id],
  )
  const [invoice, reload] = useResource(load)

  const back =
    id !== null ? (
      <Link className={buttonClassName('secondary', 'md')} to={receptionPaymentPath(id)}>
        Back
      </Link>
    ) : null

  if (id === null) {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Receipt" />
        <EmptyState title="Receipt not found" description="The receipt link is not valid." />
      </div>
    )
  }
  if (invoice.status === 'loading') {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Receipt" />
        <Spinner label="Loading the receipt" />
      </div>
    )
  }
  if (invoice.status === 'error') {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Receipt" aside={back} />
        <ErrorState title="We could not load the receipt" error={invoice.error} onRetry={reload} />
      </div>
    )
  }

  const data = invoice.data
  if (data.paymentId !== id) {
    return (
      <div className="page">
        <PaymentHero eyebrow={EYEBROW} title="Receipt" aside={back} />
        <EmptyState title="Receipt not found" description="The invoice returned belongs to another payment. Open the receipt from the payment again." />
      </div>
    )
  }
  const service = (data.lines ?? []).map((line) => line.description).filter(Boolean).join(', ')
  const discount = Number(data.discountAmount ?? 0)
  const hasSubtotal = data.subtotalAmount !== null && data.subtotalAmount !== undefined && String(data.subtotalAmount).trim() !== ''
  const hasDiscount = data.discountAmount !== null && data.discountAmount !== undefined && String(data.discountAmount).trim() !== ''
  const printable = data.status === 'PAID' || data.status === 'REFUNDED'
  const simulated = data.settlementType === 'SIMULATED'
  return (
    <div className="page">
      <PaymentHero eyebrow={EYEBROW} title={`Receipt ${data.invoiceNumber}`} description="Confirmed payment record." />
      <Card title="Sportify / Payment receipt" aria-label="Receipt" className={printable ? 'payment-receipt payment-print-receipt' : 'payment-receipt'}>
        {simulated && (
          <p className="notice notice--warning" role="status">
            SIMULATED SETTLEMENT - Completed via Sportify QR Simulator. No real bank transfer or cash collection occurred.
          </p>
        )}
        <dl className="summary-list payment-rows">
          <div>
            <dt>Receipt / date</dt>
            <dd>
              {data.invoiceNumber} / {formatDate(data.issueDate ?? data.paymentTime)}
            </dd>
          </div>
          <div>
            <dt>Issued to</dt>
            <dd>{memberLine(data.memberFullName, data.memberCode, data.memberId)}</dd>
          </div>
          {service && (
            <div>
              <dt>Service</dt>
              <dd>{service}</dd>
            </div>
          )}
          {hasSubtotal && (
            <div>
              <dt>Base price</dt>
              <dd>{formatMoney(data.subtotalAmount)}</dd>
            </div>
          )}
          {hasDiscount && (
            <div>
              <dt>Discount</dt>
              <dd>{discount > 0 ? `-${formatMoney(discount)}` : formatMoney(0)}</dd>
            </div>
          )}
          <div className="summary-list__total">
            <dt>Paid total</dt>
            <dd>{formatMoney(data.totalAmount)}</dd>
          </div>
          {simulated && (
            <div>
              <dt>Settlement provenance</dt>
              <dd>SIMULATED ({data.settlementProvider ?? 'SPORTIFY_SIMULATOR'})</dd>
            </div>
          )}
          <div>
            <dt>Status</dt>
            <dd>{data.status === 'PAID' ? (simulated ? 'Paid - Simulated' : 'Paid') : humanizeCode(data.status)}</dd>
          </div>
        </dl>
        {!printable && (
          <p className="notice notice--warning" role="alert">
            This invoice is {humanizeCode(data.status).toLowerCase()}, so it cannot be printed as a payment receipt.
          </p>
        )}
        <div className="reception-actions reception-print-hidden">
          {back}
          <Button onClick={() => window.print()} disabled={!printable}>Print receipt</Button>
        </div>
      </Card>
    </div>
  )
}
