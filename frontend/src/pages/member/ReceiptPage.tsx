import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { paymentsApi, type Invoice } from '../../api/paymentsApi'
import { Button, Card, EmptyState, ErrorState, PageHeader, StatusBadge } from '../../components/ui'
import { buttonClassName } from '../../components/ui/buttonClassName'
import { Spinner } from '../../components/ui/Spinner'
import { formatDate, formatDateTime, formatMoney, humanizeCode } from '../../utils/format'
import { PaymentNav } from './PaymentNav'
import { methodLabel, parseIdParam, PAYMENT_PATHS } from './paymentsModel'
import { useMemberResource } from './useMemberResource'

const ITEM_TYPE_LABELS: Record<string, string> = {
  SPORT_PACKAGE: 'Sport package',
  MEMBERSHIP_CARD: 'Membership card',
  CLASS_DROP_IN: 'Single visit',
  PENALTY_FEE: 'Penalty fee',
}

/**
 * Receipt (Figma S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt): GET /invoices/{id} (BE-20).
 * One layout for the three kinds; the lines say what was bought. "Print receipt" calls
 * window.print(); the print stylesheet in styles/payments.css hides the navigation and the
 * buttons and fits the receipt on one A4 page.
 */
export function ReceiptPage() {
  const { invoiceId } = useParams()
  const id = parseIdParam(invoiceId)
  if (id === null) {
    return (
      <div className="page">
        <PageHeader eyebrow="Member / Payments" title="Receipt" />
        <EmptyState
          title="Receipt not found"
          description="The receipt link is not valid."
          action={
            <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>
              My payments
            </Link>
          }
        />
      </div>
    )
  }
  return <ReceiptView key={id} invoiceId={id} />
}

function ReceiptView({ invoiceId }: { invoiceId: number }) {
  const load = useCallback((signal: AbortSignal) => paymentsApi.invoice(invoiceId, { signal }), [invoiceId])
  const [invoice, reload] = useMemberResource(load)

  if (invoice.status !== 'ready') {
    return (
      <div className="page">
        <PageHeader eyebrow="Member / Payments" title="Receipt" />
        {invoice.status === 'loading' ? (
          <Spinner label="Loading the receipt" />
        ) : (
          <ErrorState title="We could not load the receipt" error={invoice.error} onRetry={reload} />
        )}
      </div>
    )
  }
  return <ReceiptContent invoice={invoice.data} />
}

function ReceiptContent({ invoice }: { invoice: Invoice }) {
  const lines = invoice.lines ?? []
  // Only the invoice decides who it was issued to; the signed in account is never used here.
  const issuedTo = [invoice.memberFullName, invoice.memberCode].filter(Boolean).join(' / ') || '-'
  const service = lines.length > 0 ? lines.map((line) => line.description).join(', ') : '-'
  const discount = Number(invoice.discountAmount ?? 0)
  const tax = Number(invoice.taxAmount ?? 0)

  return (
    <div className="page receipt-page">
      {invoice.paymentId ? <PaymentNav paymentId={invoice.paymentId} /> : null}
      <PageHeader
        eyebrow="Member / Payments"
        title={`Receipt ${invoice.invoiceNumber}`}
        description="Your confirmed payment record."
      />
      <Card className="receipt" aria-label={`Receipt ${invoice.invoiceNumber}`}>
        <div className="receipt__head">
          <p className="receipt__brand">SPORTIFY / PAYMENT RECEIPT</p>
          <p className="receipt__number">{invoice.invoiceNumber}</p>
        </div>
        <dl className="summary-list receipt__list">
          <div>
            <dt>Receipt / date</dt>
            <dd>
              {invoice.invoiceNumber} / {formatDate(invoice.issueDate)}
            </dd>
          </div>
          <div>
            <dt>Issued to</dt>
            <dd>{issuedTo}</dd>
          </div>
          <div>
            <dt>Service</dt>
            <dd>{service}</dd>
          </div>
          {lines.length > 1 && (
            <div className="receipt__lines-row">
              <dt>Items</dt>
              <dd>
                <ul className="receipt__lines" aria-label="Receipt items">
                  {lines.map((line) => (
                    <li key={line.id}>
                      <span>
                        {line.description}
                        {line.itemType ? ` (${ITEM_TYPE_LABELS[line.itemType] ?? humanizeCode(line.itemType)})` : ''}
                        {line.quantity && line.quantity > 1 ? ` x ${line.quantity}` : ''}
                      </span>
                      <span>{formatMoney(line.lineTotal)}</span>
                    </li>
                  ))}
                </ul>
              </dd>
            </div>
          )}
          <div>
            <dt>Base price</dt>
            <dd>{formatMoney(invoice.subtotalAmount ?? invoice.totalAmount)}</dd>
          </div>
          <div>
            <dt>Discount</dt>
            <dd>{discount > 0 ? `-${formatMoney(discount)}` : formatMoney(0)}</dd>
          </div>
          {tax > 0 && (
            <div>
              <dt>Tax</dt>
              <dd>{formatMoney(tax)}</dd>
            </div>
          )}
          <div className="summary-list__total">
            <dt>Paid total</dt>
            <dd data-testid="receipt-total">{formatMoney(invoice.totalAmount)}</dd>
          </div>
          <div>
            <dt>Payment</dt>
            <dd>
              {[invoice.paymentCode, invoice.paymentMethod ? methodLabel(invoice.paymentMethod) : null]
                .filter(Boolean)
                .join(' / ') || '-'}
            </dd>
          </div>
          {invoice.paymentTime && (
            <div>
              <dt>Paid at</dt>
              <dd>{formatDateTime(invoice.paymentTime)}</dd>
            </div>
          )}
          <div>
            <dt>Status</dt>
            <dd>
              <StatusBadge status={invoice.status} />
            </dd>
          </div>
        </dl>
        <p className="receipt__footer">Thank you for training with Sportify. Keep this receipt for your records.</p>
        <div className="receipt__actions no-print">
          <Button onClick={() => window.print()}>Print receipt</Button>
          <Link className={buttonClassName('secondary', 'md')} to={PAYMENT_PATHS.list}>
            Back to my payments
          </Link>
        </div>
      </Card>
    </div>
  )
}
