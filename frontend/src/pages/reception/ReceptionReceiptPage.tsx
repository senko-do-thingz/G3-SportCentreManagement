import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { receptionPaymentsApi } from '../../api/receptionPaymentsApi'
import { Button, Card, EmptyState, ErrorState, PageHeader } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { useResource } from '../../hooks/useResource'
import { formatDate, formatMoney, humanizeCode } from '../../utils/format'
import { positiveId, receptionPaymentPath } from './receptionModel'
import { memberLine } from './receptionPaymentsModel'

const EYEBROW = 'Receptionist / Payments and reports'

/**
 * Receipt (Figma S3-Receipt) at /reception/payments/:paymentId/receipt: the invoice of
 * GET /invoices/by-payment/{paymentId} (BE-18), ready to print. Back returns to the payment.
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
        <PageHeader eyebrow={EYEBROW} title="Receipt" />
        <EmptyState title="Receipt not found" description="The receipt link is not valid." />
      </div>
    )
  }
  if (invoice.status === 'loading') {
    return (
      <div className="page">
        <PageHeader eyebrow={EYEBROW} title="Receipt" />
        <Spinner label="Loading the receipt" />
      </div>
    )
  }
  if (invoice.status === 'error') {
    return (
      <div className="page">
        <PageHeader eyebrow={EYEBROW} title="Receipt" actions={back} />
        <ErrorState title="We could not load the receipt" error={invoice.error} onRetry={reload} />
      </div>
    )
  }

  const data = invoice.data
  const service = (data.lines ?? []).map((line) => line.description).filter(Boolean).join(', ')
  const discount = Number(data.discountAmount ?? 0)
  return (
    <div className="page">
      <PageHeader eyebrow={EYEBROW} title={`Receipt ${data.invoiceNumber}`} description="Confirmed payment record." />
      <Card title="Sportify / Payment receipt" aria-label="Receipt" className="payment-receipt">
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
          <div>
            <dt>Service</dt>
            <dd>{service || '-'}</dd>
          </div>
          <div>
            <dt>Base price</dt>
            <dd>{formatMoney(data.subtotalAmount ?? data.totalAmount)}</dd>
          </div>
          <div>
            <dt>Discount</dt>
            <dd>{formatMoney(discount)}</dd>
          </div>
          <div className="summary-list__total">
            <dt>Paid total</dt>
            <dd>{formatMoney(data.totalAmount)}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{data.status === 'PAID' ? 'Paid' : humanizeCode(data.status)}</dd>
          </div>
        </dl>
        <div className="reception-actions reception-print-hidden">
          {back}
          <Button onClick={() => window.print()}>Print receipt</Button>
        </div>
      </Card>
    </div>
  )
}
